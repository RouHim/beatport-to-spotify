package de.rouhim.bts.scheduler;

import de.rouhim.bts.settings.Settings;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.pmw.tinylog.Logger;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SchedulerService {
    public static String parsingScheduled = "parsingScheduled";

    public SchedulerService(IMqttClient mqttClient) {
        int period = Settings.readInt(Settings.EnvValue.SCHEDULE_RATE_MINUTES);
        Logger.info(" * Schedule period: %dm%n", period);
        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        scheduledExecutorService.scheduleAtFixedRate(
                () -> tick(mqttClient),
                0,
                period,
                TimeUnit.MINUTES
        );
    }

    private void tick(IMqttClient mqttClient) {
        try {
            MqttMessage msg = new MqttMessage();
            msg.setQos(2);
            msg.setRetained(true);

            mqttClient.publish(parsingScheduled, msg);
        } catch (MqttException e) {
            Logger.error(e, e.getMessage());
        }
    }
}
