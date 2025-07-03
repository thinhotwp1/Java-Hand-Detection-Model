package org.ecphx.process;

import org.ecphx.Main;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

public class ImageDrawer {

    public static void drawDetections(
            BufferedImage image,
            List<float[]> decodedBoxes,
            float[][] rawBoxArray,
            List<Integer> keepIndices,
            PreprocessInfo info
    ) {
        Graphics2D g = image.createGraphics();
        g.setStroke(new BasicStroke(2.0f));
        g.setColor(Color.RED);

        for (int i : keepIndices) {
            // Draw bounding box
            float[] box = decodedBoxes.get(i);
            int x = Math.round(box[0]);
            int y = Math.round(box[1]);
            int width = Math.round(box[2] - box[0]);
            int height = Math.round(box[3] - box[1]);
            g.drawRect(x, y, width, height);

            // Draw keypoints
            float[] raw = rawBoxArray[i];
            for (int j = 0; j < 7; j++) {
                float rawX = raw[4 + j * 2];
                float rawY = raw[4 + j * 2 + 1];
                float[] point = Main.convertPoint(rawX, rawY, info);
                int px = Math.round(point[0]);
                int py = Math.round(point[1]);

                g.fillOval(px - 2, py - 2, 5, 5); // small circle
            }
        }

        g.dispose();
    }
}
