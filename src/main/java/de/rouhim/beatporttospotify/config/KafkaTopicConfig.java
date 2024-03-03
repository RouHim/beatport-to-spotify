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

/**
 * beatport.parsing.scheduled
 * ->
 *
 */
@Configuration
public class KafkaTopicConfig {
    public static final String KAFKA_TOPIC_BEATPORT_PARSING_SCHEDULED = "beatport.parsing.scheduled";
    public static final String KAFKA_TOPIC_BEATPORT_GENRE_PLAYLIST_URL_OBTAINED = "beatport.genre.playlist.url.obtained";
    public static final String KAFKA_TOPIC_BEATPORT_GENRE_PLAYLIST_PARSED = "beatport.genre.playlist.parsed";

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
        return TopicBuilder.name(KAFKA_TOPIC_BEATPORT_PARSING_SCHEDULED).build();
    }

    @Bean
    public NewTopic topic2() {
        return TopicBuilder.name(KAFKA_TOPIC_BEATPORT_GENRE_PLAYLIST_URL_OBTAINED).build();
    }

    @Bean
    public NewTopic topic3() {
        return TopicBuilder.name(KAFKA_TOPIC_BEATPORT_GENRE_PLAYLIST_PARSED).build();
    }
}