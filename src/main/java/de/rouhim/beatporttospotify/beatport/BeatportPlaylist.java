package de.rouhim.beatporttospotify.beatport;

import java.util.List;

public record BeatportPlaylist(String url, String title, List<BeatportTrack> tracks) {
}