package de.rouhim.bts.imaging;

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
import java.net.URL;

public class CoverImageGenerator {

    private static final String FONT_FILE = "/Montserrat-Regular.ttf";
    private static final String FONT_NAME = "Montserrat Regular";

    static {
        try {
            InputStream fontDataStream = CoverImageGenerator.class.getResourceAsStream(FONT_FILE);

            GraphicsEnvironment
                    .getLocalGraphicsEnvironment()
                    .registerFont(Font.createFont(Font.TRUETYPE_FONT, fontDataStream)
                            .deriveFont(48f));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static byte[] generateImage(final String imageKeywords, String textToWriteOnImage) throws IOException {
        BufferedImage image = ImageIO.read(new URL("https://source.unsplash.com/collection/9535011/500x500"));
        Color avgColor = getAverageColorOfImage(image);
        Color fontColor = determineFontColor(image, avgColor);
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
                invertColor(avgColor.getRed()),
                invertColor(avgColor.getGreen()),
                invertColor(avgColor.getBlue()))
        );
        g.drawRect(10, 10, image.getWidth() - 20, image.getHeight() - 20);
    }

    private static Color determineFontColor(BufferedImage image, Color avgColor) {
        int avgSumColor = (int) ((avgColor.getRed() + avgColor.getGreen() + avgColor.getBlue()) / 3.f);
        int avgSumColorInverted = invertColor(avgSumColor);
        return avgSumColorInverted > 128 ? Color.WHITE : Color.BLACK;
    }

    private static int invertColor(int color) {
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