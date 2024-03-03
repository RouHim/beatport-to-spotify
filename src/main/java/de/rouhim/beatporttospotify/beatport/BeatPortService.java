package de.rouhim.beatporttospotify.beatport;

import de.rouhim.beatporttospotify.scheduler.SchedulerService;
import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static de.rouhim.beatporttospotify.config.KafkaTopicConfig.KAFKA_TOPIC_BEATPORT_PARSING_SCHEDULED;

@Service
public class BeatPortService {
    private final Logger logger = LoggerFactory.getLogger(SchedulerService.class);

    @KafkaListener(topics = KAFKA_TOPIC_BEATPORT_PARSING_SCHEDULED)
    public void consume() {
        logger.info("Consumed message from topic: " + KAFKA_TOPIC_BEATPORT_PARSING_SCHEDULED);
    }


    private static String getTrackTitle(Element trackElement) {
        return trackElement.select("span.TracksList-style__TrackName-sc-aa5f840e-0.ktpGSg").text();
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
        return trackElement.select("div.ArtistNames-sc-dff2fb58-0.demTCQ a")
                .stream()
                .map(Element::text)
                .toList();
    }

    public List<BeatportPlaylist> parse(List<String> beatportUrls) {
        return beatportUrls.stream()
                .map(url -> new BeatportPlaylist(url, getPlaylistTitle(url), getTracks(url)))
                .toList();
    }

    private String getPlaylistTitle(String url) {
        try {
            URI beatportUri = URI.create(url);
            Document doc = Jsoup.parse(IOUtils.toString(beatportUri, StandardCharsets.UTF_8));
            Element titleElement = doc.select("div.TitleControls-style__PreText-sc-80310707-1.dHuTUq").first();
            return titleElement.text() + " - Beatport Top 100";
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
