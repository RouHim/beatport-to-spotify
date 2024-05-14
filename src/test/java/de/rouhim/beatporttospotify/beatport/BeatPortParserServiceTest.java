package de.rouhim.beatporttospotify.beatport;

import org.junit.jupiter.api.Test;

import static de.rouhim.beatporttospotify.beatport.BeatPortParserService.SUFFIX_BEATPORT_TOP_100;
import static org.assertj.core.api.Assertions.assertThat;

class BeatPortParserServiceTest {

    @Test
    void parse() {
        // GIVEN is a beatport url
        String url = "https://www.beatport.com/genre/hard-dance-hardcore/8/top-100";

        // WHEN parsing the url
        BeatportPlaylist parse = new BeatPortParserService(null).parse(url);

        // THEN the correct title should be parsed
        assertThat(parse.title()).isEqualTo("Hard Dance / Hardcore / Neo Rave" + SUFFIX_BEATPORT_TOP_100);
    }
}