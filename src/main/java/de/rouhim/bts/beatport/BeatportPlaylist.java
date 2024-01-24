package de.rouhim.bts.beatport;


import java.util.List;

public record BeatportPlaylist(String url, String title, List<BeatportTrack> tracks) {
}