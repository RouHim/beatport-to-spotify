package de.rouhim.beatporttospotify.beatport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.rouhim.beatporttospotify.scheduler.SchedulerService;
import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static de.rouhim.beatporttospotify.config.KafkaTopicConfig.KAFKA_TOPIC_BEATPORT_GENRE_PLAYLIST_URL_OBTAINED;
import static de.rouhim.beatporttospotify.config.KafkaTopicConfig.KAFKA_TOPIC_BEATPORT_PARSING_SCHEDULED;

@Service
public class BeatPortParserService {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    public static final String SUFFIX_BEATPORT_TOP_100 = " - Beatport Top 100";
    private final Logger logger = LoggerFactory.getLogger(SchedulerService.class);
    private final KafkaTemplate<String, String> kafkaStringMessage;

    public BeatPortParserService(KafkaTemplate<String, String> kafkaStringMessage) {
        this.kafkaStringMessage = kafkaStringMessage;
    }

    private static String getTrackTitle(Element trackElement) {
        return trackElement.select("span.TracksList-style__TrackName-sc-aa5f840e-0.ktpGSg").text();
    }

    @KafkaListener(topics = KAFKA_TOPIC_BEATPORT_GENRE_PLAYLIST_URL_OBTAINED)
    public void consume(String playlistUrl) throws JsonProcessingException {
        logger.info(
                "Consumed message from topic: %s with url: %s"
                        .formatted(
                                KAFKA_TOPIC_BEATPORT_GENRE_PLAYLIST_URL_OBTAINED,
                                playlistUrl
                        )
        );

        BeatportPlaylist beatportPlaylist = parse(playlistUrl);

        // Serialize to json string
        var beatportPlaylistJson = objectMapper.writeValueAsString(beatportPlaylist);


        // Send message to KAFKA_TOPIC_BEATPORT_GENRE_PLAYLIST_PARSED
        kafkaStringMessage.send(KAFKA_TOPIC_BEATPORT_PARSING_SCHEDULED, beatportPlaylistJson);
    }

    public List<BeatportTrack> getTracks(String beatportUrl) {
        try {
            URI beatportUri = URI.create(beatportUrl);
            Document doc = Jsoup.parse(IOUtils.toString(beatportUri, StandardCharsets.UTF_8));

            System.out.println("Parsing Tracks from: " + beatportUrl);

            // Select div with the following tag: data-testid="tracks-list-item"
            List<BeatportTrack> beatportTracks = doc
                    .select("div[data-testid=tracks-list-item]")
                    .stream()
                    .map(this::toTrack)
                    .toList();

            System.out.println("Found " + beatportTracks.size() + " tracks");

            return beatportTracks;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private BeatportTrack toTrack(Element trackElement) {
        return new BeatportTrack(toTrackArtists(trackElement), getTrackTitle(trackElement));
    }

    private List<String> toTrackArtists(Element trackElement) {
        return trackElement.select("div[class^=ArtistNames a")
                .stream()
                .map(Element::text)
                .toList();
    }

    public BeatportPlaylist parse(String playlistUrl) {
        return new BeatportPlaylist(playlistUrl, getPlaylistTitle(playlistUrl), getTracks(playlistUrl));
    }

    private String getPlaylistTitle(String url) {
        try {
            URI beatportUri = URI.create(url);
            Document doc = Jsoup.parse(IOUtils.toString(beatportUri, StandardCharsets.UTF_8));
            Element titleElement = doc.select("div[class^=TitleControls-style__PreText]").first();
            return titleElement.text().trim() + SUFFIX_BEATPORT_TOP_100;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
