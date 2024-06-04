package de.rouhim.beatporttospotify.config;


import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaTopicConfig {
    public static final String KAFKA_TOPIC_BEATPORT_PARSING_SCHEDULED = "beatport.parsing.scheduled";
    public static final String KAFKA_TOPIC_BEATPORT_GENRE_PLAYLIST_URL_OBTAINED = "beatport.genre.playlist.url.obtained";
    public static final String KAFKA_TOPIC_BEATPORT_GENRE_PLAYLIST_PARSED = "beatport.genre.playlist.parsed";
    public static final String KAFKA_TOPIC_SPOTIFY_PLAYLIST_CREATED = "spotify.playlist.created";
    public static final String KAFKA_TOPIC_SPOTIFY_PLAYLIST_UPDATED = "spotify.playlist.updated";
    public static final String KAFKA_TOPIC_COVER_IMAGE_GENERATED = "cover.image.generated";


    @Value(value = "${spring.kafka.bootstrap-servers}")
    private String bootstrapAddress;

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapAddress);
        return new KafkaAdmin(configs);
    }

    @Bean
    public NewTopic topic1() {
        return TopicBuilder
                .name(KAFKA_TOPIC_BEATPORT_PARSING_SCHEDULED)
                .build();
    }

    @Bean
    public NewTopic topic2() {
        return TopicBuilder
                .name(KAFKA_TOPIC_BEATPORT_GENRE_PLAYLIST_URL_OBTAINED)
                .build();
    }

    @Bean
    public NewTopic topic3() {
        return TopicBuilder
                .name(KAFKA_TOPIC_BEATPORT_GENRE_PLAYLIST_PARSED)
                .build();
    }

    @Bean
    public NewTopic topic4() {
        return TopicBuilder
                .name(KAFKA_TOPIC_SPOTIFY_PLAYLIST_CREATED)
                .build();
    }

    @Bean
    public NewTopic topic5() {
        return TopicBuilder
                .name(KAFKA_TOPIC_SPOTIFY_PLAYLIST_UPDATED)
                .build();
    }

    @Bean
    public NewTopic topic6() {
        return TopicBuilder
                .name(KAFKA_TOPIC_COVER_IMAGE_GENERATED)
                .build();
    }
}
