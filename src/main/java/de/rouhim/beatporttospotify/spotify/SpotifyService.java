package de.rouhim.beatporttospotify.spotify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.rouhim.beatporttospotify.beatport.BeatportPlaylist;
import de.rouhim.beatporttospotify.beatport.BeatportTrack;
import de.rouhim.beatporttospotify.config.Settings;
import de.rouhim.beatporttospotify.image.CoverImage;
import de.rouhim.beatporttospotify.image.CoverImageService;
import jakarta.annotation.Nonnull;
import jakarta.annotation.PostConstruct;
import org.apache.hc.client5.http.utils.Base64;
import org.apache.hc.core5.http.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.SpotifyHttpManager;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import se.michaelthelin.spotify.model_objects.specification.Image;
import se.michaelthelin.spotify.model_objects.specification.Playlist;
import se.michaelthelin.spotify.model_objects.specification.PlaylistSimplified;
import se.michaelthelin.spotify.model_objects.specification.Track;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static de.rouhim.beatporttospotify.config.KafkaTopicConfig.KAFKA_TOPIC_BEATPORT_GENRE_PLAYLIST_PARSED;
import static de.rouhim.beatporttospotify.config.KafkaTopicConfig.KAFKA_TOPIC_COVER_IMAGE_GENERATED;
import static de.rouhim.beatporttospotify.config.KafkaTopicConfig.KAFKA_TOPIC_SPOTIFY_PLAYLIST_CREATED;
import static de.rouhim.beatporttospotify.config.KafkaTopicConfig.KAFKA_TOPIC_SPOTIFY_PLAYLIST_UPDATED;

@Service
public class SpotifyService {
    private static final String clientId = Settings.readString(Settings.EnvValue.SPOTIFY_CLIENT_ID).orElseThrow();
    private static final String clientSecret = Settings.readString(Settings.EnvValue.SPOTIFY_CLIENT_SECRET).orElseThrow();
    private static final URI redirectUri = SpotifyHttpManager.makeUri("https://example.org/");
    public static final String CACHE_NAME_SPOTIFY_URI = "spotify-uri";
    private SpotifyApi spotifyApi;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final Logger logger = LoggerFactory.getLogger(SpotifyService.class);

    private final KafkaTemplate<String, String> kafkaStringMessage;
    private final CacheManager cacheManager;
    private Cache spotifyUriCache;

    public SpotifyService(KafkaTemplate<String, String> kafkaStringMessage, CacheManager cacheManager) {
        this.kafkaStringMessage = kafkaStringMessage;
        this.cacheManager = cacheManager;
    }

    @PostConstruct
    public void init() throws IOException, ParseException, SpotifyWebApiException {
        spotifyUriCache = cacheManager.getCache(CACHE_NAME_SPOTIFY_URI);
        initialize();
    }

    @KafkaListener(topics = KAFKA_TOPIC_BEATPORT_GENRE_PLAYLIST_PARSED)
    public void consumePlaylistParsed(String beatportPlaylist) throws Exception {
        logger.info(
                "Consumed message from topic: %s with playlist: %s"
                        .formatted(
                                KAFKA_TOPIC_BEATPORT_GENRE_PLAYLIST_PARSED,
                                beatportPlaylist
                        )
        );

        BeatportPlaylist parsedBeatportPlaylist = objectMapper.readValue(beatportPlaylist, BeatportPlaylist.class);

        updatePlaylist(parsedBeatportPlaylist);
    }

    public void initialize() throws IOException, SpotifyWebApiException, ParseException {
        if (spotifyApi != null) {
            return;
        }

        spotifyApi = new SpotifyApi.Builder()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setRedirectUri(redirectUri)
                .build();

        Optional<String> authCode = Settings.readString(Settings.EnvValue.SPOTIFY_AUTH_CODE);
        Optional<String> accessToken = Settings.readPersistentValue(Settings.PersistentValue.ACCESS_TOKEN);
        Optional<String> refreshToken = Settings.readPersistentValue(Settings.PersistentValue.REFRESH_TOKEN);

        // If nothing is set, request manual authorization
        if (accessToken.isEmpty() && refreshToken.isEmpty() && authCode.isEmpty()) {
            requestManualAuthorization();
        }

        // If only auth code is set, request access token and refresh token
        if (accessToken.isEmpty() && refreshToken.isEmpty() && authCode.isPresent()) {
            try {
                requestAccessToken(authCode.get());
            } catch (Exception e) {
                logger.error(e.getMessage());
                requestManualAuthorization();
            }
        }

        // If only access token is set, request refresh token
        if (accessToken.isPresent() && refreshToken.isPresent()) {
            requestRefreshToken(accessToken.get(), refreshToken.get());
        }

        // Test if access token is valid
        try {
            logger.info("Testing access token validity");
            spotifyApi.getCurrentUsersProfile().build().execute();
            logger.info("Access token is valid");
        } catch (SpotifyWebApiException e) {
            logger.error(e.getMessage(), e);
            requestManualAuthorization();
        }
    }

    private void requestAccessToken(String authCode) throws IOException, SpotifyWebApiException, ParseException {
        AuthorizationCodeCredentials authorizationCodeCredentials = spotifyApi.authorizationCode(authCode).build().execute();

        // Set access and refresh token for further "spotifyApi" object usage
        spotifyApi.setAccessToken(authorizationCodeCredentials.getAccessToken());
        spotifyApi.setRefreshToken(authorizationCodeCredentials.getRefreshToken());

        Settings.savePersistentValue(Settings.PersistentValue.ACCESS_TOKEN, authorizationCodeCredentials.getAccessToken());
        Settings.savePersistentValue(Settings.PersistentValue.REFRESH_TOKEN, authorizationCodeCredentials.getRefreshToken());
    }

    private void requestRefreshToken(String accessToken, String refreshToken) throws IOException, SpotifyWebApiException, ParseException {
        spotifyApi.setAccessToken(accessToken);
        spotifyApi.setRefreshToken(refreshToken);
        AuthorizationCodeCredentials authorizationCodeCredentials = spotifyApi.authorizationCodeRefresh().build().execute();

        // Set access and refresh token for further "spotifyApi" object usage
        spotifyApi.setAccessToken(authorizationCodeCredentials.getAccessToken());
        spotifyApi.setRefreshToken(authorizationCodeCredentials.getRefreshToken());

        Settings.savePersistentValue(Settings.PersistentValue.ACCESS_TOKEN, authorizationCodeCredentials.getAccessToken());
        Settings.savePersistentValue(Settings.PersistentValue.REFRESH_TOKEN, authorizationCodeCredentials.getRefreshToken());
    }

    private void requestManualAuthorization() throws IOException, SpotifyWebApiException, ParseException {
        logger.info("Requesting manual authorization");

        // Remove access and refresh token
        Settings.deletePersistentValue(Settings.PersistentValue.ACCESS_TOKEN);
        Settings.deletePersistentValue(Settings.PersistentValue.REFRESH_TOKEN);

        URI authUrl = spotifyApi.authorizationCodeUri()
                .scope("playlist-modify-public playlist-modify-private ugc-image-upload")
                .build().execute();

        logger.info("Visit: {}", authUrl.toString());
        logger.info("Then enter the retrieved code to ACCESS_TOKEN and restart");

        System.exit(0);
    }

    public void updatePlaylist(BeatportPlaylist beatportPlaylist) throws Exception {
        authCodeRefresh();

        String playlistTitle = beatportPlaylist.title();
        String sourceUrl = beatportPlaylist.url();

        logger.info("Try to find playlist: {}", playlistTitle);
        Optional<String> playlistId = findPlaylist(playlistTitle);

        if (playlistId.isEmpty()) {
            logger.info("No playlist found, creating:{}", sourceUrl);
            playlistId = createPlaylist(playlistTitle, sourceUrl);


            if (playlistId.isPresent()) {
                kafkaStringMessage.send(
                        KAFKA_TOPIC_SPOTIFY_PLAYLIST_CREATED,
                        createPlaylistDto(playlistId.get(), playlistTitle)
                );
            }
        }

        if (playlistId.isPresent()) {
            logger.info("Found spotify playlist");
            Playlist playlist = spotifyApi.getPlaylist(playlistId.get()).build().execute();

            logger.info("Deleting tracks from spotify playlist");
            clearPlayList(playlist);

            logger.info("Adding tracks to spotify playlist");
            addTracksToPlaylist(playlist, beatportPlaylist.tracks());

            // Check if the playlist has a valid cover image
            checkCoverImage(playlist, playlistTitle);
        } else {
            logger.error("Could not create a playlist for: {}", sourceUrl);
        }

        logger.info("Finished updating playlist: {}", playlistTitle);
    }

    private void authCodeRefresh() {
        try {
            spotifyApi.setRefreshToken(Settings.readPersistentValue(Settings.PersistentValue.REFRESH_TOKEN).orElseThrow());

            AuthorizationCodeCredentials authorizationCodeCredentials = spotifyApi.authorizationCodeRefresh()
                    .build()
                    .execute();

            // Set access and refresh token for further "spotifyApi" object usage
            String accessToken = authorizationCodeCredentials.getAccessToken();
            String refreshToken = authorizationCodeCredentials.getRefreshToken();

            spotifyApi.setAccessToken(accessToken);
            spotifyApi.setRefreshToken(refreshToken);

            Settings.savePersistentValue(Settings.PersistentValue.ACCESS_TOKEN, accessToken);
            Settings.savePersistentValue(Settings.PersistentValue.REFRESH_TOKEN, refreshToken);
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            logger.error("Could not refresh access token: {}", e.getMessage(), e);
        }
    }

    private void checkCoverImage(Playlist playlist, String playlistTitle) throws IOException, SpotifyWebApiException, ParseException {
        Image[] playlistCoverImages = spotifyApi.getPlaylistCoverImage(playlist.getId()).build().execute();
        if (isValidCoverImage(playlistCoverImages)) {
            logger.info("Valid cover image found for playlist: {}", playlistTitle);
        } else {
            logger.info("No valid cover image found for playlist: {}", playlistTitle);
            byte[] imageData = CoverImageService.generateImage(playlistTitle);
            kafkaStringMessage.send(
                    KAFKA_TOPIC_SPOTIFY_PLAYLIST_UPDATED,
                    objectMapper.writeValueAsString(new CoverImage(playlist.getId(), imageData))
            );
        }
    }

    private static boolean isValidCoverImage(Image[] playlistCoverImages) {
        if (playlistCoverImages.length == 0) {
            return false;
        }

        String firstUrl = playlistCoverImages[0].getUrl();
        if (firstUrl.startsWith("https://mosaic")) {
            return false;
        }

        return firstUrl.startsWith("https://image");
    }

    @KafkaListener(topics = KAFKA_TOPIC_COVER_IMAGE_GENERATED)
    public void consumeCoverImageGenerated(String coverImagePairJson) {
        logger.info("Consumed message from topic: " + KAFKA_TOPIC_COVER_IMAGE_GENERATED);

        try {
            CoverImage coverImagePair = objectMapper.readValue(coverImagePairJson, CoverImage.class);
            String playlistId = coverImagePair.identifier();
            byte[] coverImage = coverImagePair.imageData();

            logger.info("Uploading cover image for playlist: {}", playlistId);

            String encodedImage = Base64.encodeBase64String(coverImage);
            spotifyApi.uploadCustomPlaylistCoverImage(playlistId)
                    .image_data(encodedImage)
                    .build()
                    .execute();
            logger.info("Cover image uploaded for playlist: {}", playlistId);
        } catch (Exception e) {
            logger.error("Could not upload cover image: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private static String createPlaylistDto(String playlistId, String playlistTitle) throws JsonProcessingException {
        return objectMapper.writeValueAsString(new SpotifyPlaylistDto(
                playlistId,
                playlistTitle.replace(" - Beatport Top 100", "").trim()
        ));
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
                .build()
                .execute()
                .getItems();

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
            logger.info("no tracks to delete");
        }
    }

    private void addTracksToPlaylist(Playlist playlist, List<BeatportTrack> beatportTracks) throws Exception {
        List<String> spotifyUris = new ArrayList<>();

        for (BeatportTrack beatportTrack : beatportTracks) {
            String searchQuery = "%s %s".formatted(
                    String.join(" ", beatportTrack.artists()),
                    beatportTrack.title()
            );

            // Read from redis cache if available
            Optional<String> maybeCachedSpotifyUri = getMaybeCachedSpotifyUri(searchQuery);

            if (maybeCachedSpotifyUri.isPresent()) {
                spotifyUris.add(maybeCachedSpotifyUri.get());
            } else {
                Optional<Track> maybeMatchedSpotifyUri = matchSpotifyTrack(searchQuery);
                if (maybeMatchedSpotifyUri.isPresent()) {
                    String matchedSpotifyUri = maybeMatchedSpotifyUri.get().getUri();
                    spotifyUris.add(matchedSpotifyUri);
                    putSpotifyUriToCache(searchQuery, matchedSpotifyUri);
                }
            }
        }

        JsonArray itemsToAdd = new JsonArray();
        spotifyUris.forEach(itemsToAdd::add);

        spotifyApi.addItemsToPlaylist(playlist.getId(), itemsToAdd).build().execute();

        logger.info("Added {} tracks to spotify playlist.", spotifyUris.size());
    }

    @SuppressWarnings("DataFlowIssue")
    private void putSpotifyUriToCache(String searchQuery, String matchedSpotifyUri) {
        spotifyUriCache.put(searchQuery, matchedSpotifyUri);
    }

    @SuppressWarnings("DataFlowIssue")
    private Optional<String> getMaybeCachedSpotifyUri(@Nonnull String searchQuery) {
        String value = spotifyUriCache.get(searchQuery, String.class);
        return Optional.ofNullable(value);
    }

    private Optional<Track> matchSpotifyTrack(String searchQuery) throws IOException, ParseException, SpotifyWebApiException {
        Optional<Track> matched = Optional.empty();

        Track[] spotifyTracks = spotifyApi.searchTracks(searchQuery).build().execute().getItems();

        if (spotifyTracks.length > 0) {
            matched = Optional.of(spotifyTracks[0]);
        } else {
            logger.info("no match for: {}", searchQuery);
        }

        return matched;
    }

    public void save(List<BeatportPlaylist> beatportPlaylists) throws Exception {
        for (BeatportPlaylist beatportPlaylist : beatportPlaylists) {
            updatePlaylist(beatportPlaylist);

            try {
                logger.info("finished parsing\n");
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
