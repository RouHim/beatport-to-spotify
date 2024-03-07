package de.rouhim.beatporttospotify.beatport;

import de.rouhim.beatporttospotify.scheduler.SchedulerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

import static de.rouhim.beatporttospotify.config.KafkaTopicConfig.KAFKA_TOPIC_BEATPORT_GENRE_PLAYLIST_URL_OBTAINED;
import static de.rouhim.beatporttospotify.config.KafkaTopicConfig.KAFKA_TOPIC_BEATPORT_PARSING_SCHEDULED;

@Service
public class BeatPortConfigService {
    private final Logger logger = LoggerFactory.getLogger(SchedulerService.class);

    private final KafkaTemplate<String, String> kafkaStringMessage;

    public BeatPortConfigService(KafkaTemplate<String, String> kafkaStringMessage) {
        this.kafkaStringMessage = kafkaStringMessage;
    }

    @KafkaListener(topics = KAFKA_TOPIC_BEATPORT_PARSING_SCHEDULED)
    public void consume() {
        logger.info("Consumed message from topic: " + KAFKA_TOPIC_BEATPORT_PARSING_SCHEDULED);

        List<String> playlists = readBeatportGenrePlaylistFromConfig();
        logger.info("Found " + playlists.size() + " playlists");

        playlists.forEach(playlist -> {
            kafkaStringMessage.send(KAFKA_TOPIC_BEATPORT_GENRE_PLAYLIST_URL_OBTAINED, playlist);
        });
    }

    private List<String> readBeatportGenrePlaylistFromConfig() {
        var env = System.getenv();
        String[] beatportGenrePlaylistUrls = env.get("BEATPORT_URLS").split(",");
        return Arrays.asList(beatportGenrePlaylistUrls);
    }
}
