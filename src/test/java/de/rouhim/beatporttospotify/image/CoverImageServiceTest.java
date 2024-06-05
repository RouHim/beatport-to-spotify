package de.rouhim.beatporttospotify.image;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CoverImageServiceTest {
    @Test
    void consumePlaylistCreated() {
        byte[] imageData = CoverImageService.generateImage("test");
        assertNotNull(imageData);
    }
}