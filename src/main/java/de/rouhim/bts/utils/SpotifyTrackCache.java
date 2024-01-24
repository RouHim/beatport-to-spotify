package de.rouhim.bts.utils;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class SpotifyTrackCache {
    private static final File localStorageFile = new File("data", "spotify_track_cache");

    /*
    Key describes the search query, the value is the spotify track uri as string
     */
    private static final Map<String, String> spotifyTrackCache = new ConcurrentHashMap<>();

    static {
        try {
            if (localStorageFile.exists()) {
                System.out.println("Loading cache from file");
                List<String> lines = FileUtils.readLines(localStorageFile, StandardCharsets.UTF_8);
                for (String line : lines) {
                    String[] lineSplit = line.split("=");
                    spotifyTrackCache.put(lineSplit[0], lineSplit[1]);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void persistCache() {
        try {
            List<String> lines = new ArrayList<>();
            for (Map.Entry<String, String> entry : spotifyTrackCache.entrySet()) {
                lines.add(String.format("%s=%s", entry.getKey(), entry.getValue()));
            }
            FileUtils.writeLines(localStorageFile, StandardCharsets.UTF_8.toString(), lines);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Optional<String> get(String searchQuery) {
        return Optional.ofNullable(spotifyTrackCache.get(searchQuery));
    }

    public static void put(String searchQuery, String spotifyUri) {
        spotifyTrackCache.put(searchQuery, spotifyUri);
    }
}
