package org.ecphx;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class ImageDisplayer {

    public static void showImage(BufferedImage image, String title) {
        JFrame frame = new JFrame(title);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JLabel label = new JLabel(new ImageIcon(image));
        frame.getContentPane().add(label, BorderLayout.CENTER);

        frame.pack();
        frame.setLocationRelativeTo(null); // Center on screen
        frame.setVisible(true);
    }
}
