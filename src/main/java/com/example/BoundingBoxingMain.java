package com.example;

import com.google.gson.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.List;
import java.util.ArrayList;

public class BoundingBoxingMain {

    public static class BoundingBox {
        public String label;
        public double confidence;
        public double x1, y1, x2, y2;

        public BoundingBox(String label, double confidence, double x1, double y1, double x2, double y2) {
            this.label = label;
            this.confidence = confidence;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }
    }

    public static void main(String[] args) {
        String inputImagePath = "imgs/b.jpg";
        String jsonPath = "input/boxes.json";
        String outputImagePath = "output/result.jpg";

        try {
            // Load image
            BufferedImage image = ImageIO.read(new File(inputImagePath));

            // Load bounding boxes
            List<BoundingBox> boxes = loadBoundingBoxesFromJson(jsonPath);

            // Draw bounding boxes
            drawBoxes(image, boxes);

            // Save result
            File output = new File(outputImagePath);
            output.getParentFile().mkdirs(); // ensure output folder exists
            ImageIO.write(image, "png", output);

            System.out.println("Saved image with boxes to: " + outputImagePath);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static List<BoundingBox> loadBoundingBoxesFromJson(String filePath) {
        List<BoundingBox> boxes = new ArrayList<>();
        try (FileReader reader = new FileReader(filePath)) {
            JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
            for (JsonElement el : array) {
                JsonObject obj = el.getAsJsonObject();
                String label = obj.get("label").getAsString();
                double confidence = obj.get("confidence").getAsDouble();
                JsonArray bbox = obj.get("bbox").getAsJsonArray();
                double x1 = bbox.get(0).getAsDouble();
                double y1 = bbox.get(1).getAsDouble();
                double x2 = bbox.get(2).getAsDouble();
                double y2 = bbox.get(3).getAsDouble();

                boxes.add(new BoundingBox(label, confidence, x1, y1, x2, y2));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return boxes;
    }

    private static void drawBoxes(BufferedImage image, List<BoundingBox> boxes) {
        Graphics2D g = image.createGraphics();
        g.setStroke(new BasicStroke(2));
        for (BoundingBox box : boxes) {
            // Draw rectangle
            g.setColor(Color.RED);
            int x = (int) box.x1;
            int y = (int) box.y1;
            int width = (int) (box.x2 - box.x1);
            int height = (int) (box.y2 - box.y1);
            g.drawRect(x, y, width, height);

            // Draw label
            String text = String.format("%s: %.2f", box.label, box.confidence);
            g.setFont(new Font("Arial", Font.BOLD, 14));
            FontMetrics fm = g.getFontMetrics();
            int textWidth = fm.stringWidth(text);
            int textHeight = fm.getHeight();

            g.setColor(new Color(255, 255, 255, 200)); // white semi-transparent
            g.fillRect(x, y - textHeight, textWidth, textHeight);

            g.setColor(Color.BLACK);
            g.drawString(text, x, y - 5);
        }
        g.dispose();
    }
}
