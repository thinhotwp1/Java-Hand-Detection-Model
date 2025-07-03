package org.ecphx;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class ImageSaver {

    public static void drawDetections(BufferedImage image, float[][] rawBoxes, float[][] anchors, float[] scores, float threshold, String outputPath) {
        Graphics2D g = image.createGraphics();
        g.setStroke(new BasicStroke(2));
        g.setColor(Color.RED);

        for (int i = 0; i < scores.length; i++) {
            if (scores[i] < threshold) continue;

            float[] decodedBox = BoxDecoder.decode(rawBoxes[i], anchors[i]);

            int imgWidth = image.getWidth();
            int imgHeight = image.getHeight();

            int xMin = (int) (decodedBox[0] * imgWidth);
            int yMin = (int) (decodedBox[1] * imgHeight);
            int xMax = (int) (decodedBox[2] * imgWidth);
            int yMax = (int) (decodedBox[3] * imgHeight);

            g.drawRect(xMin, yMin, xMax - xMin, yMax - yMin);

            // Draw keypoints
            g.setColor(Color.GREEN);
            float[] box18 = rawBoxes[i];
            for (int kp = 0; kp < 7; kp++) {
                float kpX = box18[4 + kp * 2] * imgWidth;
                float kpY = box18[4 + kp * 2 + 1] * imgHeight;
                g.fillOval((int) kpX - 3, (int) kpY - 3, 6, 6);
            }

            g.setColor(Color.RED); // reset for next box
        }

        g.dispose();

        try {
            ImageIO.write(image, "png", new File(outputPath));
            System.out.println("✅ Saved output image to: " + outputPath);
        } catch (Exception e) {
            System.err.println("❌ Failed to save output image: " + e.getMessage());
        }
    }
}
