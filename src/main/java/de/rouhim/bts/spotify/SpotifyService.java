package de.rouhim.bts.spotify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.rouhim.bts.beatport.BeatportPlaylist;
import de.rouhim.bts.beatport.BeatportService;
import de.rouhim.bts.beatport.BeatportTrack;
import de.rouhim.bts.image.CoverImageGeneratorService;
import de.rouhim.bts.settings.Settings;
import org.apache.hc.core5.http.ParseException;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.jsoup.internal.StringUtil;
import org.pmw.tinylog.Logger;
import redis.clients.jedis.Jedis;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.SpotifyHttpManager;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import se.michaelthelin.spotify.model_objects.specification.Playlist;
import se.michaelthelin.spotify.model_objects.specification.PlaylistSimplified;
import se.michaelthelin.spotify.model_objects.specification.Track;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

public class SpotifyService {
    private static final String clientId = Settings.readString(Settings.EnvValue.SPOTIFY_CLIENT_ID);
    private static final String clientSecret = Settings.readString(Settings.EnvValue.SPOTIFY_CLIENT_SECRET);
    private static final URI redirectUri = SpotifyHttpManager.makeUri("https://example.org/");
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final IMqttClient mqttClient;
    private final Jedis redisClient;
    private SpotifyApi spotifyApi;
    public static String spotifyPlaylistCreated = "spotifyPlaylistCreated";

    public SpotifyService(IMqttClient mqttClient, Jedis redisClient) {
        this.mqttClient = mqttClient;
        this.redisClient = redisClient;

        try {
            initialize();
        } catch (Exception e) {
            Logger.error(e, e.getMessage());
            throw new RuntimeException(e);
        }

        try {
            mqttClient.subscribe(BeatportService.beatportGenreParsed, (topic, msg) -> {
                BeatportPlaylist beatportPlaylist = objectMapper.readValue(msg.getPayload(), BeatportPlaylist.class);
                updatePlaylist(beatportPlaylist);
            });
            mqttClient.subscribe(CoverImageGeneratorService.coverImageGenerated, (topic, msg) -> {
                JsonObject jsonObject = objectMapper.readValue(msg.getPayload(), JsonObject.class);
                String playlistId = jsonObject.get("playlistId").getAsString();
                String base64CoverImage = jsonObject.get("base64CoverImage").getAsString();
                updatePlaylistCoverImage(playlistId, base64CoverImage);
            });
        } catch (MqttException e) {
            Logger.error(e, e.getMessage());
        }
    }

    private void updatePlaylistCoverImage(String playlistId, String base64CoverImage) {
        try {
            spotifyApi.uploadCustomPlaylistCoverImage(playlistId)
                    .image_data(base64CoverImage)
                    .build().execute();
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            Logger.error(e, e.getMessage());
        }
    }

    public static String readInput(String enterMessage) {
        Scanner scanner = new Scanner(System.in);
        Logger.info(enterMessage);
        return scanner.next();
    }

    public void initialize() throws IOException, ParseException, SpotifyWebApiException {
        if (spotifyApi == null) {
            spotifyApi = new SpotifyApi.Builder()
                    .setClientId(clientId)
                    .setClientSecret(clientSecret)
                    .setRedirectUri(redirectUri)
                    .build();

            String accessToken = Settings.readString(Settings.EnvValue.ACCESS_TOKEN);
            String refreshToken = Settings.readPersistentValue(Settings.PersistentValue.REFRESH_TOKEN);

            if (StringUtil.isBlank(accessToken) || StringUtil.isBlank(refreshToken)) {
                authorizeApi();
            } else {
                spotifyApi.setAccessToken(accessToken);
                spotifyApi.setRefreshToken(refreshToken);
                refreshToken();
            }
        }
    }

    private void authorizeApi() throws IOException, ParseException, SpotifyWebApiException {
        URI authUrl = spotifyApi.authorizationCodeUri()
                .scope("playlist-modify-public playlist-modify-private ugc-image-upload")
                .build().execute();

        Logger.info("Visit: " + authUrl.toString());

        String code = readInput("Enter code:");

        AuthorizationCodeCredentials authorizationCodeCredentials = spotifyApi.authorizationCode(code)
                .build()
                .execute();

        // Set access and refresh token for further "spotifyApi" object usage
        spotifyApi.setAccessToken(authorizationCodeCredentials.getAccessToken());
        spotifyApi.setRefreshToken(authorizationCodeCredentials.getRefreshToken());

        Settings.savePersistentValue(Settings.PersistentValue.REFRESH_TOKEN, authorizationCodeCredentials.getRefreshToken());
    }

    private void refreshToken() throws IOException, SpotifyWebApiException, ParseException {
        AuthorizationCodeCredentials authorizationRefreshCodeCredentials = spotifyApi.authorizationCodeRefresh().build().execute();
        spotifyApi.setAccessToken(authorizationRefreshCodeCredentials.getAccessToken());
        spotifyApi.setRefreshToken(authorizationRefreshCodeCredentials.getRefreshToken());
    }

    public void updatePlaylist(BeatportPlaylist beatportPlaylist) throws Exception {
        String playlistTitle = beatportPlaylist.title();
        String sourceUrl = beatportPlaylist.url();

        Logger.info("Try to find playlist: " + playlistTitle);
        Optional<String> playlistId = findPlaylist(playlistTitle);

        if (playlistId.isEmpty()) {
            Logger.info("No playlist found, creating:" + sourceUrl);
            playlistId = createPlaylist(playlistTitle, sourceUrl);

            // If playlist was created, send a message to the mqtt broker
            playlistId.ifPresent(s -> sendPlaylistCreatedMessage(playlistTitle, s));
        }

        if (playlistId.isPresent()) {
            Logger.info("Found spotify playlist");
            Playlist playlist = spotifyApi.getPlaylist(playlistId.get()).build().execute();

            Logger.info("Deleting tracks from spotify playlist");
            clearPlayList(playlist);

            Logger.info("Adding tracks to spotify playlist");
            addTracksToPlaylist(playlist, beatportPlaylist.tracks());
        } else {
            Logger.info("Could not create a playlist for: " + sourceUrl);
        }
    }

    private void sendPlaylistCreatedMessage(String playlistTitle, String playlistId) {
        try {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("playlistId", playlistId);
            jsonObject.addProperty("playlistTitle", playlistTitle);

            byte[] bytes = objectMapper.writeValueAsBytes(jsonObject);

            MqttMessage msg = new MqttMessage(bytes);
            msg.setQos(2);
            msg.setRetained(true);

            mqttClient.publish(spotifyPlaylistCreated, msg);
        } catch (MqttException | JsonProcessingException e) {
            Logger.error(e, e.getMessage());
        }
    }

    private Optional<String> createPlaylist(String playlistTitle, String sourceUrl) throws IOException, SpotifyWebApiException, ParseException {
        String currentUserId = spotifyApi.getCurrentUsersProfile().build().execute().getId();
        Playlist createdPlaylist = spotifyApi.createPlaylist(currentUserId, playlistTitle)
                .description(sourceUrl)
                .collaborative(false)
                .public_(true)
                .build()
                .execute();
        return Optional.ofNullable(createdPlaylist.getId());
    }

    private Optional<String> findPlaylist(String playlistTitle) throws IOException, SpotifyWebApiException, ParseException {
        PlaylistSimplified[] currentPlaylists = spotifyApi.getListOfCurrentUsersPlaylists()
                .limit(50)
                .build().execute().getItems();

        return Arrays.stream(currentPlaylists)
                .filter(playlist -> playlist.getName().equals(playlistTitle))
                .map(PlaylistSimplified::getId)
                .findFirst();
    }

    private void clearPlayList(Playlist playlist) throws IOException, SpotifyWebApiException, ParseException {
        JsonArray tracksToDelete = new JsonArray(100);
        Arrays.stream(playlist.getTracks().getItems())
                .map(track -> track.getTrack().getUri())
                .toList()
                .forEach(track -> {
                    JsonObject element = new JsonObject();
                    element.addProperty("uri", track);
                    tracksToDelete.add(element);
                });
        if (!tracksToDelete.isEmpty()) {
            spotifyApi.removeItemsFromPlaylist(playlist.getId(), tracksToDelete).build().execute();
        } else {
            Logger.info("no tracks to delete");
        }
    }

    private void addTracksToPlaylist(Playlist playlist, List<BeatportTrack> beatportTracks) throws Exception {
        List<String> spotifyUris = new ArrayList<>();

        for (BeatportTrack beatportTrack : beatportTracks) {
            String searchQuery = SpotifyQueryBuilder.build(beatportTrack);

            // Check redis client cache for spotify uri
            String maybeCachedSpotifyUri = redisClient.get(searchQuery);

            // TODO: do we get really null if there is nothing, debug it!
            if (maybeCachedSpotifyUri != null) {
                spotifyUris.add(maybeCachedSpotifyUri);
            } else {
                Optional<Track> maybeMatchedSpotifyUri = matchSpotifyTrack(searchQuery);
                if (maybeMatchedSpotifyUri.isPresent()) {
                    String matchedSpotifyUri = maybeMatchedSpotifyUri.get().getUri();
                    spotifyUris.add(matchedSpotifyUri);
                    redisClient.set(searchQuery, matchedSpotifyUri);
                }
            }
        }

        JsonArray itemsToAdd = new JsonArray();
        spotifyUris.forEach(itemsToAdd::add);

        spotifyApi.addItemsToPlaylist(playlist.getId(), itemsToAdd).build().execute();

        Logger.info("Added " + spotifyUris.size() + " tracks to spotify playlist.");
    }

    private Optional<Track> matchSpotifyTrack(String searchQuery) {
        Optional<Track> matched = Optional.empty();

        try {
            Track[] spotifyTracks = spotifyApi.searchTracks(searchQuery).build().execute().getItems();

            if (spotifyTracks.length > 0) {
                matched = Optional.of(spotifyTracks[0]);
            } else {
                Logger.info("no match for: " + searchQuery);
            }

        } catch (IOException | SpotifyWebApiException e) {
            Logger.error(e, e.getMessage());
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

        return matched;
    }

    public void save(List<BeatportPlaylist> beatportPlaylists) {
        for (BeatportPlaylist beatportPlaylist : beatportPlaylists) {
            try {

                updatePlaylist(beatportPlaylist);

                Logger.info("finished parsing\n");
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}