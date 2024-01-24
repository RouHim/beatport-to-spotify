package de.rouhim.bts.domain;


import java.util.List;

public record BeatportPlaylist(String url, String title, List<BeatportTrack> tracks) {

}