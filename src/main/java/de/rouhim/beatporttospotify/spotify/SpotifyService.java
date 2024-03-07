package de.rouhim.beatporttospotify.spotify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.rouhim.beatporttospotify.beatport.BeatportPlaylist;
import de.rouhim.beatporttospotify.beatport.BeatportTrack;
import de.rouhim.beatporttospotify.config.Settings;
import de.rouhim.beatporttospotify.scheduler.SchedulerService;
import org.apache.hc.client5.http.utils.Base64;
import org.apache.hc.core5.http.ParseException;
import org.jsoup.internal.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.SpotifyHttpManager;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import se.michaelthelin.spotify.model_objects.specification.Playlist;
import se.michaelthelin.spotify.model_objects.specification.PlaylistSimplified;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static de.rouhim.beatporttospotify.config.KafkaTopicConfig.*;

@Service
public class SpotifyService {
    private static final String clientId = Settings.readString(Settings.EnvValue.SPOTIFY_CLIENT_ID);
    private static final String clientSecret = Settings.readString(Settings.EnvValue.SPOTIFY_CLIENT_SECRET);
    private static final URI redirectUri = SpotifyHttpManager.makeUri("https://example.org/");
    private SpotifyApi spotifyApi;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final Logger logger = LoggerFactory.getLogger(SchedulerService.class);

    private final KafkaTemplate<String, String> kafkaStringMessage;


    public SpotifyService(KafkaTemplate<String, String> kafkaStringMessage) {
        this.kafkaStringMessage = kafkaStringMessage;
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

    @KafkaListener(topics = KAFKA_TOPIC_COVER_IMAGE_GENERATED)
    public void consumeCoverImageGenerated(String coverImagePairJson) throws IOException, SpotifyWebApiException, ParseException {
        logger.info("Consumed message from topic: " + KAFKA_TOPIC_COVER_IMAGE_GENERATED);

        var coverImagePair = objectMapper.readValue(coverImagePairJson, Map.Entry.class);
        String playlistId = (String) coverImagePair.getKey();
        byte[] coverImage = (byte[]) coverImagePair.getValue();

        // Base64 encoded JPEG image data, maximum payload size is 256 KB.
        String encodedImage = new String(Base64.encodeBase64(coverImage), StandardCharsets.UTF_8);
        spotifyApi.uploadCustomPlaylistCoverImage(playlistId)
                .image_data(encodedImage)
                .build().execute();
    }

    public String readInput(String enterMessage) {
        Scanner scanner = new Scanner(System.in);
        logger.info(enterMessage);
        return scanner.next();
    }

    public void initialize() throws IOException, SpotifyWebApiException, ParseException {
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

    private void authorizeApi() throws IOException, SpotifyWebApiException, ParseException {
        URI authUrl = spotifyApi.authorizationCodeUri()
                .scope("playlist-modify-public playlist-modify-private ugc-image-upload")
                .build().execute();

        logger.info("Visit: " + authUrl.toString());

        String code = readInput("Enter code:");

        AuthorizationCodeCredentials authorizationCodeCredentials = spotifyApi.authorizationCode(code)
                .build().execute();

        // Set access and refresh token for further "spotifyApi" object usage
        spotifyApi.setAccessToken(authorizationCodeCredentials.getAccessToken());
        spotifyApi.setRefreshToken(authorizationCodeCredentials.getRefreshToken());

        Settings.savePersistentValue(Settings.PersistentValue.REFRESH_TOKEN, authorizationCodeCredentials.getRefreshToken());

        //logger.info("Expires in: " + authorizationCodeCredentials.getExpiresIn());
    }

    private void refreshToken() throws IOException, SpotifyWebApiException, ParseException {
        AuthorizationCodeCredentials authorizationRefreshCodeCredentials = spotifyApi.authorizationCodeRefresh().build().execute();
        spotifyApi.setAccessToken(authorizationRefreshCodeCredentials.getAccessToken());
        spotifyApi.setRefreshToken(authorizationRefreshCodeCredentials.getRefreshToken());

        //logger.info("Expires in: " + authorizationRefreshCodeCredentials.getExpiresIn());
    }

    public void updatePlaylist(BeatportPlaylist beatportPlaylist) throws Exception {
        String playlistTitle = beatportPlaylist.title();
        String sourceUrl = beatportPlaylist.url();

        logger.info("Try to find playlist: " + playlistTitle);
        Optional<String> playlistId = findPlaylist(playlistTitle);

        if (playlistId.isEmpty()) {
            logger.info("No playlist found, creating:" + sourceUrl);
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

            String playlistDtoJson = createPlaylistDto(playlist.getId(), playlistTitle);
            kafkaStringMessage.send(KAFKA_TOPIC_SPOTIFY_PLAYLIST_UPDATED, playlistDtoJson);
        } else {
            logger.error("Could not create a playlist for: " + sourceUrl);
        }
    }

    private static String createPlaylistDto(String playlistId, String playlistTitle) throws JsonProcessingException {
        String playlistDtoJson = objectMapper.writeValueAsString(new SpotifyPlaylistDto(
                playlistId,
                playlistTitle.replace(" - Beatport Top 100", "").trim()
        ));
        return playlistDtoJson;
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
            Optional<String> maybeCachedSpotifyUri =

            if (maybeCachedSpotifyUri.isPresent()) {
                spotifyUris.add(maybeCachedSpotifyUri.get());
            } else {
                Optional<Track> maybeMatchedSpotifyUri = matchSpotifyTrack(searchQuery);
                if (maybeMatchedSpotifyUri.isPresent()) {
                    String matchedSpotifyUri = maybeMatchedSpotifyUri.get().getUri();
                    spotifyUris.add(matchedSpotifyUri);
                    SpotifyTrackCache.put(searchQuery, matchedSpotifyUri);
                }
            }
        }

        JsonArray itemsToAdd = new JsonArray();
        spotifyUris.forEach(itemsToAdd::add);

        spotifyApi.addItemsToPlaylist(playlist.getId(), itemsToAdd).build().execute();

        logger.info("Added " + spotifyUris.size() + " tracks to spotify playlist.");
    }

    private Optional<Track> matchSpotifyTrack(String searchQuery) {
        Optional<Track> matched = Optional.empty();

        try {
            Track[] spotifyTracks = spotifyApi.searchTracks(searchQuery).build().execute().getItems();

            if (spotifyTracks.length > 0) {
                matched = Optional.of(spotifyTracks[0]);
            } else {
                logger.info("no match for: " + searchQuery);
            }

        } catch (IOException | SpotifyWebApiException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

        return matched;
    }

    public void save(List<BeatportPlaylist> beatportPlaylists) {
        for (BeatportPlaylist beatportPlaylist : beatportPlaylists) {
            try {

                updatePlaylist(beatportPlaylist);

                logger.info("finished parsing\n");
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
