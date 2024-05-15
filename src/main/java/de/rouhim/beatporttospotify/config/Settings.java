package de.rouhim.beatporttospotify.config;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class Settings {

    // Writes data to file
    public static void savePersistentValue(PersistentValue key, String value) {
        try {
            File dataDir = new File("./data");
            dataDir.mkdirs();
            File file = new File(dataDir, key.name());
            FileUtils.writeStringToFile(file, value, "UTF-8");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Reads data from file
    public static String readPersistentValue(PersistentValue key) {
        try {
            File dataDir = new File("./data");
            dataDir.mkdirs();
            File file = new File(dataDir, key.name());
            if (!file.exists()) {
                return "";
            }
            return FileUtils.readFileToString(file, "UTF-8");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Reads the given env value as a int
    public static int readInt(EnvValue envValue) {
        String value = System.getenv(envValue.name());
        return Integer.parseInt(value);
    }

    // Reads the given env value as a String
    public static String readString(EnvValue envValue) {
        return System.getenv(envValue.name());
    }

    // Reads the given env value as a bool
    public static boolean readBool(EnvValue envValue) {
        String value = System.getenv(envValue.name());
        return Boolean.parseBoolean(value);
    }

    // Reads the given env value as a String list
    public static List<String> readStringList(EnvValue envValue) {
        String value = System.getenv(envValue.name());
        return Arrays.asList(value.split(","));
    }

    /**
     * Deletes the persistent value
     */
    public static void deletePersistentValue(PersistentValue persistentValue) {
        try {
            File dataDir = new File("./data");
            dataDir.mkdirs();
            File file = new File(dataDir, persistentValue.name());
            file.delete();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public enum EnvValue {
        ACCESS_TOKEN,
        BEATPORT_URLS,
        SCHEDULE_RATE_MINUTES,
        GENERATE_COVER_IMAGE,
        SPOTIFY_CLIENT_ID,
        SPOTIFY_CLIENT_SECRET
    }

    public enum PersistentValue {
        REFRESH_TOKEN,
    }
}
