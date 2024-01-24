package de.rouhim.bts;

import de.rouhim.bts.beatport.BeatPortService;
import de.rouhim.bts.domain.BeatportPlaylist;
import de.rouhim.bts.spotify.SpotifyService;
import de.rouhim.bts.utils.Settings;
import de.rouhim.bts.utils.SpotifyTrackCache;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main {

    public static void main(String[] args) {
        int period = Settings.readInt(Settings.EnvValue.SCHEDULE_RATE_MINUTES);
        System.out.printf(" * Schedule period: %dm%n", period);
        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(Main::startCycle,
                        0, period, TimeUnit.MINUTES);
    }

    private static void startCycle() {
        try {
            System.out.println("==============");
            LocalTime start = LocalTime.now();
            System.out.println("started cycle @ " + start);

            List<String> beatportUrls = Settings.readStringList(Settings.EnvValue.BEATPORT_URLS);
            System.out.printf("Found %s urls%n", beatportUrls.size());
            BeatPortService beatPortService = new BeatPortService();
            List<BeatportPlaylist> beatportPlaylists = beatPortService.parse(beatportUrls);


            SpotifyService spotifyService = new SpotifyService();
            spotifyService.initialize();
            spotifyService.save(beatportPlaylists);

            SpotifyTrackCache.persistCache();

            LocalTime end = LocalTime.now();
            long runtime = start.until(end, ChronoUnit.SECONDS);
            System.out.printf("finished cycle @ %s - runtime: %ss%n", end, runtime);
            System.out.println("==============");
            System.out.println();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}