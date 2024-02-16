package de.rouhim.beatporttospotify.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import static de.rouhim.beatporttospotify.config.KafkaTopicConfig.KAFKA_TOPIC_BEATPORT_PARSING_SCHEDULED;

@Service
public class SchedulerService {
    private final Logger logger = LoggerFactory.getLogger(SchedulerService.class);

    private final KafkaTemplate<String, String> kafkaStringMessage;

    public SchedulerService(KafkaTemplate<String, String> kafkaStringMessage) {
        this.kafkaStringMessage = kafkaStringMessage;
    }

    @Scheduled(fixedRate = 60000)
    public void runNightlyTask() {
        logger.info("Sending beatport parsing scheduled message");
        kafkaStringMessage.send(KAFKA_TOPIC_BEATPORT_PARSING_SCHEDULED, null);
    }

}
