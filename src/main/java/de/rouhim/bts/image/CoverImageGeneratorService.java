package de.rouhim.bts.image;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import de.rouhim.bts.settings.Settings;
import de.rouhim.bts.spotify.SpotifyService;
import org.apache.hc.client5.http.utils.Base64;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.pmw.tinylog.Logger;

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
import java.nio.charset.StandardCharsets;

public class CoverImageGeneratorService {

    private static final String FONT_FILE = "/Montserrat-Regular.ttf";
    private static final String FONT_NAME = "Montserrat Regular";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    public static final String coverImageGenerated = "coverImageGenerated";

    static {
        try {
            InputStream fontDataStream = CoverImageGeneratorService.class.getResourceAsStream(FONT_FILE);

            GraphicsEnvironment
                    .getLocalGraphicsEnvironment()
                    .registerFont(Font.createFont(Font.TRUETYPE_FONT, fontDataStream)
                            .deriveFont(48f));
        } catch (Exception e) {
            Logger.error(e, e.getMessage());
        }
    }

    public CoverImageGeneratorService(IMqttClient mqttClient) {
        try {
            mqttClient.subscribe(SpotifyService.spotifyPlaylistCreated, (topic, msg) -> {
                if (Settings.readBool(Settings.EnvValue.GENERATE_COVER_IMAGE)) {
                    return;
                }

                JsonObject beatportPlaylist = objectMapper.readValue(msg.getPayload(), JsonObject.class);
                String playlistId = beatportPlaylist.get("playlistId").getAsString();
                String playlistTitle = beatportPlaylist.get("playlistTitle").getAsString();

                String base64CoverImage = generateCoverImage(playlistTitle);

                sendCoverImageGeneratedMessage(mqttClient, playlistId, base64CoverImage);
            });
        } catch (MqttException e) {
            Logger.error(e, e.getMessage());
        }
    }

    private static void sendCoverImageGeneratedMessage(IMqttClient mqttClient, String playlistId, String base64CoverImage) throws JsonProcessingException, MqttException {
        JsonObject payload = new JsonObject();
        payload.addProperty("playlistId", playlistId);
        payload.addProperty("base64CoverImage", base64CoverImage);

        byte[] bytes = objectMapper.writeValueAsBytes(payload);

        MqttMessage msg = new MqttMessage(bytes);
        msg.setQos(2);
        msg.setRetained(true);

        mqttClient.publish(coverImageGenerated, msg);
    }

    public String generateCoverImage(String playlistTitle) throws IOException {
        // Base64 encoded JPEG image data, maximum payload size is 256 KB.
        String rawPlaylistTitle = playlistTitle.replace(" - Beatport Top 100", "").trim();
        byte[] bytes = generateImage(rawPlaylistTitle);
        return new String(Base64.encodeBase64(bytes), StandardCharsets.UTF_8);
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

        // Only use the lower quarter of the image to determine the average color
        // Because there is where the text will be written
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

    /**
     * Draws the given text on the given image
     *
     * @param image draw the text on
     * @param text  to draw
     * @param color of the text
     */
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