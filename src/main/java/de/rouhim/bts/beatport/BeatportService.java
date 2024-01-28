package de.rouhim.bts.beatport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.rouhim.bts.scheduler.SchedulerService;
import de.rouhim.bts.settings.Settings;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.pmw.tinylog.Logger;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;


public class BeatportService {
    public static final String beatportGenrePlaylistParsed = "beatportGenrePlaylistParsed";
    public static final String beatportUrlObtained = "beatportUrlObtained";
    private static final int TIMEOUT_MILLIS = 10000;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public BeatportService(IMqttClient mqttClient) {
        try {
            mqttClient.subscribe(SchedulerService.parsingScheduled, (topic, msg) -> {
                Logger.info("Message on topic {} received", topic);

                List<String> beatportUrls = Settings.readStringList(Settings.EnvValue.BEATPORT_URLS);
                Logger.info("Found {} urls", beatportUrls.size());

                // Send a beatport url obtained message
                beatportUrls.forEach(sendBeatportUrlObtainedMessage(mqttClient));
            });
            // Subscribe to beatportUrlObtained
            mqttClient.subscribe(beatportUrlObtained, (topic, msg) -> {
                Logger.info("Message on topic {} received", topic);

                String beatportUrl = new String(msg.getPayload(), StandardCharsets.UTF_8);
                Logger.info("Parsing: {}", beatportUrl);

                // Parse the beatport url
                Document jsoupDoc = toJsoupDoc(beatportUrl);
                BeatportPlaylist beatportPlaylist = new BeatportPlaylist(
                        beatportUrl,
                        getPlaylistTitle(jsoupDoc),
                        getTracks(jsoupDoc));

                // Send the beatport playlist parsed message
                sendBeatportGenrePlaylistParsedMessage(mqttClient, beatportPlaylist);
            });
        } catch (MqttException e) {
            Logger.error(e, e.getMessage());
        }
    }

    private static Consumer<String> sendBeatportUrlObtainedMessage(IMqttClient mqttClient) {
        return url -> {
            try {
                MqttMessage message = new MqttMessage(url.getBytes());
                message.setQos(1);

                mqttClient.publish(beatportUrlObtained, message);
            } catch (MqttException e) {
                Logger.error(e, e.getMessage());
            }
        };
    }

    private void sendBeatportGenrePlaylistParsedMessage(IMqttClient mqttClient, BeatportPlaylist beatportPlaylist) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(beatportPlaylist);

            MqttMessage message = new MqttMessage(bytes);
            message.setQos(1);

            mqttClient.publish(beatportGenrePlaylistParsed, message);
        } catch (MqttException | JsonProcessingException e) {
            Logger.error(e, e.getMessage());
        }
    }

    private Document toJsoupDoc(String beatportUrl) {
        try {
            Logger.info("Parsing: " + beatportUrl);
            URI beatportUri = URI.create(beatportUrl);
            return Jsoup.parse(beatportUri.toURL(), TIMEOUT_MILLIS);
        } catch (IOException e) {
            Logger.error(e, "Failed to parse: " + beatportUrl);
            throw new RuntimeException(e);
        }
    }

    private String getPlaylistTitle(Document doc) {
        Element titleElement = doc.select("div.TitleControls-style__PreText-sc-80310707-1.dHuTUq").first();
        return titleElement.text() + " - Beatport Top 100";
    }


    public List<BeatportTrack> getTracks(Document doc) {
        return doc.select("div[data-testid=tracks-list-item]")
                .stream()
                .map(this::toTrack)
                .toList();
    }

    private BeatportTrack toTrack(Element trackElement) {
        return new BeatportTrack(
                toTrackArtists(trackElement),
                getTrackTitle(trackElement)
        );
    }

    private List<String> toTrackArtists(Element trackElement) {
        return trackElement
                .select("div.ArtistNames-sc-dff2fb58-0.demTCQ a")
                .stream()
                .map(Element::text)
                .toList();
    }

    private static String getTrackTitle(Element trackElement) {
        return trackElement
                .select("span.TracksList-style__TrackName-sc-aa5f840e-0.ktpGSg")
                .text();
    }
}
