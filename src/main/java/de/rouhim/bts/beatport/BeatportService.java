package de.rouhim.bts.beatport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.rouhim.bts.scheduler.SchedulerService;
import de.rouhim.bts.settings.Settings;
import de.rouhim.bts.utils.Pair;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.pmw.tinylog.Logger;

import java.io.IOException;
import java.net.URI;
import java.util.List;


public class BeatportService {
    public static final String beatportGenreParsed = "beatportGenreParsed";
    private static final int TIMEOUT_MILLIS = 10000;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public BeatportService(IMqttClient mqttClient) {
        try {
            mqttClient.subscribe(SchedulerService.parsingScheduled, (topic, msg) -> {
                List<String> beatportUrls = Settings.readStringList(Settings.EnvValue.BEATPORT_URLS);
                Logger.info("Found %s urls%n", beatportUrls.size());

                List<BeatportPlaylist> playlists = parse(beatportUrls);
                playlists.forEach(beatportPlaylist -> sendMessage(mqttClient, beatportPlaylist));
            });
        } catch (MqttException e) {
            Logger.error(e, e.getMessage());
        }
    }

    private void sendMessage(IMqttClient mqttClient, BeatportPlaylist beatportPlaylist) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(beatportPlaylist);

            MqttMessage msg = new MqttMessage(bytes);
            msg.setQos(2);
            msg.setRetained(true);

            mqttClient.publish(beatportGenreParsed, msg);
        } catch (MqttException | JsonProcessingException e) {
            Logger.error(e, e.getMessage());
        }
    }

    private List<BeatportPlaylist> parse(List<String> beatportUrls) {
        return beatportUrls
                .parallelStream()
                .map(url -> Pair.of(url, toJsoupDoc(url)))
                .map(pair -> new BeatportPlaylist(
                        pair.first(),
                        getPlaylistTitle(pair.second()),
                        getTracks(pair.second())
                ))
                .toList();
    }

    private Document toJsoupDoc(String beatportUrl) {
        try {
            Logger.info("Parsing: " + beatportUrl);
            URI beatportUri = URI.create(beatportUrl);
            return Jsoup.parse(beatportUri.toURL(), TIMEOUT_MILLIS);
        } catch (IOException e) {
            Logger.error(e, e.getMessage());
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
