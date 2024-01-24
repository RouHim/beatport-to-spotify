package de.rouhim.bts.beatport;

import de.rouhim.bts.domain.BeatportPlaylist;
import de.rouhim.bts.domain.BeatportTrack;
import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class BeatPortService {

    private static String getTrackTitle(Element trackElement) {
        return trackElement.select("span.TracksList-style__TrackName-sc-aa5f840e-0.ktpGSg").text();
    }

    public List<BeatportTrack> getTracks(String beatportUrl) {
        try {
            URI beatportUri = URI.create(beatportUrl);
            Document doc = Jsoup.parse(IOUtils.toString(beatportUri, StandardCharsets.UTF_8));

            System.out.println("Parsing Tracks from: " + beatportUrl);

            // Select div with the following tag: data-testid="tracks-list-item"
            List<BeatportTrack> beatportTracks = doc
                    .select("div[data-testid=tracks-list-item]")
                    .stream()
                    .map(this::toTrack)
                    .toList();

            System.out.println("Found " + beatportTracks.size() + " tracks");

            return beatportTracks;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private BeatportTrack toTrack(Element trackElement) {
        return new BeatportTrack(toTrackArtists(trackElement), getTrackTitle(trackElement));
    }

    private List<String> toTrackArtists(Element trackElement) {
        return trackElement.select("div.ArtistNames-sc-dff2fb58-0.demTCQ a")
                .stream()
                .map(Element::text)
                .toList();
    }

    public List<BeatportPlaylist> parse(List<String> beatportUrls) {
        return beatportUrls.stream()
                .map(url -> new BeatportPlaylist(url, getPlaylistTitle(url), getTracks(url)))
                .toList();
    }

    private String getPlaylistTitle(String url) {
        try {
            URI beatportUri = URI.create(url);
            Document doc = Jsoup.parse(IOUtils.toString(beatportUri, StandardCharsets.UTF_8));
            Element titleElement = doc.select("div.TitleControls-style__PreText-sc-80310707-1.dHuTUq").first();
            return titleElement.text() + " - Beatport Top 100";
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}