package de.rouhim.bts.spotify;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.SpotifyHttpManager;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import com.wrapper.spotify.model_objects.specification.Playlist;
import com.wrapper.spotify.model_objects.specification.PlaylistSimplified;
import com.wrapper.spotify.model_objects.specification.Track;
import de.rouhim.bts.Main;
import de.rouhim.bts.domain.BeatportPlaylist;
import de.rouhim.bts.domain.BeatportTrack;
import de.rouhim.bts.imaging.CoverImageGenerator;
import de.rouhim.bts.utils.QueryBuilder;
import de.rouhim.bts.utils.Settings;
import de.rouhim.bts.utils.SpotifyTrackCache;
import de.rouhim.bts.utils.Utils;
import org.apache.commons.codec.binary.Base64;
import org.jsoup.internal.StringUtil;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class SpotifyService {
    private static final String clientId = "1acc388cd9384d21a1d61fb90c4dca89";
    private static final String clientSecret = "469897456daa4f7587ede94f421f60b8";
    private static final URI redirectUri = SpotifyHttpManager.makeUri("https://www.it-lobby.de/");
    private SpotifyApi spotifyApi;

    public void initialize() throws IOException, SpotifyWebApiException {
        //https://github.com/thelinmichael/spotify-web-api-java

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

    private void authorizeApi() throws IOException, SpotifyWebApiException {
        URI authUrl = spotifyApi.authorizationCodeUri()
                .scope("playlist-modify-public playlist-modify-private ugc-image-upload")
                .build().execute();

        System.out.println("Visit: " + authUrl.toString());

        String code = Utils.readInput("Enter code:");

        AuthorizationCodeCredentials authorizationCodeCredentials = spotifyApi.authorizationCode(code)
                .build().execute();

        // Set access and refresh token for further "spotifyApi" object usage
        spotifyApi.setAccessToken(authorizationCodeCredentials.getAccessToken());
        spotifyApi.setRefreshToken(authorizationCodeCredentials.getRefreshToken());

        Settings.savePersistentValue(Settings.PersistentValue.REFRESH_TOKEN, authorizationCodeCredentials.getRefreshToken());

        //System.out.println("Expires in: " + authorizationCodeCredentials.getExpiresIn());
    }

    private void refreshToken() throws IOException, SpotifyWebApiException {
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
        }

        if (playlistId.isPresent()) {
            System.out.println("Found spotify playlist");
            Playlist playlist = spotifyApi.getPlaylist(playlistId.get()).build().execute();

            System.out.println("Deleting tracks from spotify playlist");
            clearPlayList(playlist);

            System.out.println("Adding tracks to spotify playlist");
            addTracksToPlaylist(playlist, beatportPlaylist.tracks());

            if (Settings.readBool(Settings.EnvValue.GENERATE_COVER_IMAGE)) {
                System.out.println("Set cover image to spotify playlist");
                setCoverImageToPlaylist(playlist, playlistTitle);
            }
        } else {
            System.out.println("Could not create a playlist for: " + sourceUrl);
        }
    }

    private void setCoverImageToPlaylist(Playlist playlist, String playlistTitle) throws IOException, SpotifyWebApiException {
        // Base64 encoded JPEG image data, maximum payload size is 256 KB.
        String rawPlaylistTitle = playlistTitle
                .replace(" - Beatport Top 100", "");
        String coverSearchQuery = rawPlaylistTitle
                .replace("house", "")
                .replace("House", "")
                .replace("Bass", "")
                .replace("bass", "")
                .replace("Dance", "")
                .replace("dance", "")
                .replace("Room", "")
                .replace("room", "")
                .replace("&", "")
                .replace("'", "")
                .replace("/", "")
                .replace("  ", " ")
                .trim();
        String imageKeywords = String.join("+", coverSearchQuery.split(" ")) + "+club"
                .replace("++", "+");
        byte[] bytes = CoverImageGenerator.generateImage(imageKeywords, rawPlaylistTitle);
        String encodedImage = new String(Base64.encodeBase64(bytes), StandardCharsets.UTF_8);
        spotifyApi.uploadCustomPlaylistCoverImage(playlist.getId())
                .image_data(encodedImage)
                .build().execute();
    }

    private Optional<String> createPlaylist(String playlistTitle, String sourceUrl) throws IOException, SpotifyWebApiException {
        String currentUserId = spotifyApi.getCurrentUsersProfile().build().execute().getId();
        Playlist createdPlaylist = spotifyApi.createPlaylist(currentUserId, playlistTitle)
                .description(sourceUrl)
                .collaborative(false)
                .public_(true)
                .build()
                .execute();
        return Optional.ofNullable(createdPlaylist.getId());
    }

    private Optional<String> findPlaylist(String playlistTitle) throws IOException, SpotifyWebApiException {
        PlaylistSimplified[] currentPlaylists = spotifyApi.getListOfCurrentUsersPlaylists()
                .limit(50)
                .build().execute().getItems();

        return Arrays.stream(currentPlaylists)
                .filter(playlist -> playlist.getName().equals(playlistTitle))
                .map(PlaylistSimplified::getId)
                .findFirst();
    }

    private void clearPlayList(Playlist playlist) throws IOException, SpotifyWebApiException {
        JsonArray tracksToDelete = new JsonArray(100);
        Arrays.stream(playlist.getTracks().getItems())
                .map(track -> track.getTrack().getUri())
                .collect(Collectors.toList())
                .forEach(track -> {
                    JsonObject element = new JsonObject();
                    element.addProperty("uri", track);
                    tracksToDelete.add(element);
                });
        if (tracksToDelete.size() > 0) {
            spotifyApi.removeTracksFromPlaylist(playlist.getId(), tracksToDelete).build().execute();
        } else {
            System.out.println("no tracks to delete");
        }
    }

    private void addTracksToPlaylist(Playlist playlist, List<BeatportTrack> beatportTracks) throws Exception {
        List<String> spotifyUris = new ArrayList<>();

        for (BeatportTrack beatportTrack : beatportTracks) {
            // TODO: Use library to search by artist and trackname not fuzzy query
            String searchQuery = QueryBuilder.build(beatportTrack);
            Optional<String> maybeSpotifyUri = SpotifyTrackCache.get(searchQuery);

            if (maybeSpotifyUri.isPresent()) {
                spotifyUris.add(maybeSpotifyUri.get());
            } else {
                Optional<Track> maybeMatchedSpotifyUri = matchSpotifyTrack(searchQuery);
                if (maybeMatchedSpotifyUri.isPresent()) {
                    String matchedSpotifyUri = maybeMatchedSpotifyUri.get().getUri();
                    spotifyUris.add(matchedSpotifyUri);
                    SpotifyTrackCache.put(searchQuery, matchedSpotifyUri);
                }
            }
        }

        String[] matchedTracksUris = spotifyUris.toArray(new String[0]);

        //Separate into two calls, because adding all in one request doesnt work. (message to long)
        int length = matchedTracksUris.length;
        int mid = length / 2;
        String[] a = Arrays.copyOfRange(matchedTracksUris, 0, mid);
        String[] b = Arrays.copyOfRange(matchedTracksUris, mid, length);

        spotifyApi.addTracksToPlaylist(playlist.getId(), a).build().execute();
        Thread.sleep(5000);
        spotifyApi.addTracksToPlaylist(playlist.getId(), b).build().execute();

        System.out.println("added " + length + " tracks to spotify playlist.");
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