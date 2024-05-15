package de.rouhim.beatporttospotify.image;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.rouhim.beatporttospotify.spotify.SpotifyPlaylistDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;

import static de.rouhim.beatporttospotify.config.KafkaTopicConfig.KAFKA_TOPIC_COVER_IMAGE_GENERATED;
import static de.rouhim.beatporttospotify.config.KafkaTopicConfig.KAFKA_TOPIC_SPOTIFY_PLAYLIST_CREATED;

@Service
public class CoverImageService {
    private static final String FONT_FILE = "/Montserrat-Regular.ttf";
    private static final String FONT_NAME = "Montserrat Regular";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        try {
            URL resource = CoverImageService.class.getResource(FONT_FILE);
            InputStream fontDataStream = resource.openStream();

            GraphicsEnvironment
                    .getLocalGraphicsEnvironment()
                    .registerFont(Font.createFont(Font.TRUETYPE_FONT, fontDataStream)
                            .deriveFont(48f));
        } catch (Exception e) {
            throw new RuntimeException("Could not load font file: " + FONT_FILE, e);
        }
    }

    private final Logger logger = LoggerFactory.getLogger(CoverImageService.class);
    private final KafkaTemplate<String, String> kafkaStringMessage;

    @KafkaListener(topics = KAFKA_TOPIC_SPOTIFY_PLAYLIST_CREATED)
    public void consumePlaylistCreated(String playlistJson) throws IOException {
        logger.info("Consumed message from topic: " + KAFKA_TOPIC_SPOTIFY_PLAYLIST_CREATED);

        var spotifyPlaylist = objectMapper.readValue(playlistJson, SpotifyPlaylistDto.class);

        byte[] coverImage = generateImage(spotifyPlaylist.title());

        // Send KAFKA_TOPIC_COVER_IMAGE_GENERATED message
        String messagePayload = objectMapper.writeValueAsString(
                new CoverImage(spotifyPlaylist.id(), coverImage)
        );
        kafkaStringMessage.send(KAFKA_TOPIC_COVER_IMAGE_GENERATED, messagePayload);
    }

    public CoverImageService(KafkaTemplate<String, String> kafkaStringMessage) {
        this.kafkaStringMessage = kafkaStringMessage;
    }

    public static byte[] generateImage(String textToWriteOnImage) throws IOException {
        BufferedImage image = ImageIO.read(URI.create("https://source.unsplash.com/collection/9535011/500x500").toURL());
        Color avgColor = getAverageColorOfImage(image);
        Color fontColor = determineFontColor(avgColor);
        drawText(image, textToWriteOnImage, fontColor);
        drawBorder(image, avgColor);
        return compress(image);
    }

    private static byte[] compress(BufferedImage image) throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        ImageOutputStream outputStream = ImageIO.createImageOutputStream(compressed);

        ImageWriter jpgWriter = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam jpgWriteParam = jpgWriter.getDefaultWriteParam();

        jpgWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        jpgWriteParam.setCompressionQuality((float) 0.8);
        jpgWriter.setOutput(outputStream);
        jpgWriter.write(null, new IIOImage(image, null, null), jpgWriteParam);
        jpgWriter.dispose();

        return compressed.toByteArray();
    }

    private static void drawBorder(BufferedImage image, Color avgColor) {
        Graphics2D g = (Graphics2D) image.getGraphics();
        g.setStroke(new BasicStroke(3));
        g.setColor(new Color(
                invertChannel(avgColor.getRed()),
                invertChannel(avgColor.getGreen()),
                invertChannel(avgColor.getBlue()))
        );
        g.drawRect(10, 10, image.getWidth() - 20, image.getHeight() - 20);
    }

    private static Color determineFontColor(Color avgColor) {
        int avgSumColor = (int) ((avgColor.getRed() + avgColor.getGreen() + avgColor.getBlue()) / 3.f);
        int avgSumColorInverted = invertChannel(avgSumColor);
        return avgSumColorInverted > 128 ? Color.WHITE : Color.BLACK;
    }

    private static int invertChannel(int color) {
        return (color * -1) + 255;
    }

    private static Color getAverageColorOfImage(BufferedImage image) {
        long sumRed = 0;
        long sumGreen = 0;
        long sumBlue = 0;
        double cycleCount = 0;

        //just process the lower half because the is the font rendered
        int yStart = image.getHeight() - (image.getHeight() / 4);
        for (int y = yStart; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int clr = image.getRGB(x, y);
                sumRed += (clr & 0x00ff0000) >> 16;
                sumGreen += (clr & 0x0000ff00) >> 8;
                sumBlue += clr & 0x000000ff;
                cycleCount++;
            }
        }

        double avgRed = sumRed / cycleCount;
        double avgGreen = sumGreen / cycleCount;
        double avgBlue = sumBlue / cycleCount;
        return new Color((int) avgRed, (int) avgGreen, (int) avgBlue);
    }

    private static void drawText(BufferedImage image, String text, Color color) {
        Graphics graphics = image.getGraphics();
        int initialFontSize = 90;
        Font font = new Font(FONT_NAME, Font.PLAIN, initialFontSize);
        graphics.setFont(font);
        graphics.setColor(color);
        int textWidth = graphics.getFontMetrics().stringWidth(text);

        while (textWidth > 460) {
            initialFontSize--;
            font = new Font(FONT_NAME, Font.PLAIN, initialFontSize);
            graphics.setFont(font);
            graphics.setColor(color);
            textWidth = graphics.getFontMetrics().stringWidth(text);
        }

        int x = 250 - (textWidth / 2);
        int y = 460;
        graphics.drawString(text, x, y);

        graphics.dispose();
    }
}
