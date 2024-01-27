package de.rouhim.bts;

import de.rouhim.bts.beatport.BeatportService;
import de.rouhim.bts.image.CoverImageGeneratorService;
import de.rouhim.bts.scheduler.SchedulerService;
import de.rouhim.bts.spotify.SpotifyService;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import redis.clients.jedis.Jedis;

import java.util.UUID;

public class Main {

    public static void main(String[] args) throws MqttException {
        // Create jedis client

        try (IMqttClient mqttClient = new MqttClient("tcp://localhost:1883", MqttAsyncClient.generateClientId());
             Jedis jedis = new Jedis("localhost", 6379)) {
            mqttClient.connect(getMqttConnectOptions());

            SchedulerService schedulerService = new SchedulerService(mqttClient);
            BeatportService beatportService = new BeatportService(mqttClient);
            SpotifyService spotifyService = new SpotifyService(mqttClient, jedis);
            CoverImageGeneratorService coverImageGeneratorService = new CoverImageGeneratorService(mqttClient);

            // Hold the main thread open, until gracefully terminated
            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    private static MqttConnectOptions getMqttConnectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true); // reconnect on connection loss
        options.setCleanSession(true); // non-durable subscriptions
        options.setConnectionTimeout(10);
        return options;
    }
}