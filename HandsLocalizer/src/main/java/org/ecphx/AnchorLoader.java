package org.ecphx;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AnchorLoader {

    public static float[][] load(String path) throws IOException {
        List<float[]> anchors = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                // String[] parts = line.trim().split("\\s+"); // space as separator
                String[] parts = line.trim().split("[,\\s]+"); // comma as separator
                if (parts.length == 4) {
                    float[] anchor = new float[4];
                    for (int i = 0; i < 4; i++) {
                        anchor[i] = Float.parseFloat(parts[i]);
                    }
                    anchors.add(anchor);
                }
            }

        }
        return anchors.toArray(new float[0][0]);
    }
}
