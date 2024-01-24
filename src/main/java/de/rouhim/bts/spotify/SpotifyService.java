package de.rouhim.bts.spotify;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.rouhim.bts.beatport.BeatportPlaylist;
import de.rouhim.bts.beatport.BeatportTrack;
import de.rouhim.bts.image.CoverImageGenerator;
import de.rouhim.bts.settings.Settings;
import org.apache.hc.client5.http.utils.Base64;
import org.apache.hc.core5.http.ParseException;
import org.jsoup.internal.StringUtil;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.SpotifyHttpManager;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import se.michaelthelin.spotify.model_objects.specification.Playlist;
import se.michaelthelin.spotify.model_objects.specification.PlaylistSimplified;
import se.michaelthelin.spotify.model_objects.specification.Track;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

public class SpotifyService {
    private static final String clientId = Settings.readString(Settings.EnvValue.SPOTIFY_CLIENT_ID);
    private static final String clientSecret = Settings.readString(Settings.EnvValue.SPOTIFY_CLIENT_SECRET);
    private static final URI redirectUri = SpotifyHttpManager.makeUri("https://example.org/");
    private SpotifyApi spotifyApi;

    public static String readInput(String enterMessage) {
        Scanner scanner = new Scanner(System.in);
        System.out.println(enterMessage);
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

        System.out.println("Visit: " + authUrl.toString());

        String code = readInput("Enter code:");

        AuthorizationCodeCredentials authorizationCodeCredentials = spotifyApi.authorizationCode(code)
                .build().execute();

        // Set access and refresh token for further "spotifyApi" object usage
        spotifyApi.setAccessToken(authorizationCodeCredentials.getAccessToken());
        spotifyApi.setRefreshToken(authorizationCodeCredentials.getRefreshToken());

        Settings.savePersistentValue(Settings.PersistentValue.REFRESH_TOKEN, authorizationCodeCredentials.getRefreshToken());

        //System.out.println("Expires in: " + authorizationCodeCredentials.getExpiresIn());
    }

    private void refreshToken() throws IOException, SpotifyWebApiException, ParseException {
        AuthorizationCodeCredentials authorizationRefreshCodeCredentials = spotifyApi.authorizationCodeRefresh().build().execute();
        spotifyApi.setAccessToken(authorizationRefreshCodeCredentials.getAccessToken());
        spotifyApi.setRefreshToken(authorizationRefreshCodeCredentials.getRefreshToken());

        //System.out.println("Expires in: " + authorizationRefreshCodeCredentials.getExpiresIn());
    }

    public void updatePlaylist(BeatportPlaylist beatportPlaylist) throws Exception {
        String playlistTitle = beatportPlaylist.title();
        String sourceUrl = beatportPlaylist.url();

        System.out.println("Try to find playlist: " + playlistTitle);
        Optional<String> playlistId = findPlaylist(playlistTitle);

        if (playlistId.isEmpty()) {
            System.out.println("No playlist found, creating:" + sourceUrl);
            playlistId = createPlaylist(playlistTitle, sourceUrl);

            if (playlistId.isPresent() && Settings.readBool(Settings.EnvValue.GENERATE_COVER_IMAGE)) {
                System.out.println("Set cover image to spotify playlist");
                setCoverImageToPlaylist(playlistTitle, playlistId.get());
            }
        }

        if (playlistId.isPresent()) {
            System.out.println("Found spotify playlist");
            Playlist playlist = spotifyApi.getPlaylist(playlistId.get()).build().execute();

            System.out.println("Deleting tracks from spotify playlist");
            clearPlayList(playlist);

            System.out.println("Adding tracks to spotify playlist");
            addTracksToPlaylist(playlist, beatportPlaylist.tracks());
        } else {
            System.out.println("Could not create a playlist for: " + sourceUrl);
        }
    }

    private void setCoverImageToPlaylist(String playlistTitle, String playlistId) throws IOException, SpotifyWebApiException, ParseException {
        // Base64 encoded JPEG image data, maximum payload size is 256 KB.
        String rawPlaylistTitle = playlistTitle.replace(" - Beatport Top 100", "").trim();
        byte[] bytes = CoverImageGenerator.generateImage(rawPlaylistTitle);
        String encodedImage = new String(Base64.encodeBase64(bytes), StandardCharsets.UTF_8);
        spotifyApi.uploadCustomPlaylistCoverImage(playlistId)
                .image_data(encodedImage)
                .build().execute();
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
            System.out.println("no tracks to delete");
        }
    }

    private void addTracksToPlaylist(Playlist playlist, List<BeatportTrack> beatportTracks) throws Exception {
        List<String> spotifyUris = new ArrayList<>();

        for (BeatportTrack beatportTrack : beatportTracks) {
            String searchQuery = SpotifyQueryBuilder.build(beatportTrack);
            Optional<String> maybeCachedSpotifyUri = SpotifyTrackCache.get(searchQuery);

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

        System.out.println("Added " + spotifyUris.size() + " tracks to spotify playlist.");
    }

    private Optional<Track> matchSpotifyTrack(String searchQuery) {
        Optional<Track> matched = Optional.empty();

        try {
            Track[] spotifyTracks = spotifyApi.searchTracks(searchQuery).build().execute().getItems();

            if (spotifyTracks.length > 0) {
                matched = Optional.of(spotifyTracks[0]);
            } else {
                System.out.println("no match for: " + searchQuery);
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

                System.out.println("finished parsing\n");
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}