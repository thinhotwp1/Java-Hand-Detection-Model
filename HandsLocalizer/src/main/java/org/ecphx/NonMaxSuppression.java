package org.ecphx;

import java.util.ArrayList;
import java.util.List;

public class NonMaxSuppression {

    public static List<Integer> apply(List<float[]> boxes, List<Float> scores, float iouThreshold) {
        List<Integer> keep = new ArrayList<>();
        boolean[] removed = new boolean[boxes.size()];

        for (int i = 0; i < boxes.size(); i++) {
            if (removed[i]) continue;

            keep.add(i);
            float[] boxA = boxes.get(i);

            for (int j = i + 1; j < boxes.size(); j++) {
                if (removed[j]) continue;

                float[] boxB = boxes.get(j);
                if (computeIoU(boxA, boxB) > iouThreshold) {
                    removed[j] = true;
                }
            }
        }

        return keep;
    }

    private static float computeIoU(float[] a, float[] b) {
        float xA = Math.max(a[0], b[0]);
        float yA = Math.max(a[1], b[1]);
        float xB = Math.min(a[2], b[2]);
        float yB = Math.min(a[3], b[3]);

        float interArea = Math.max(0, xB - xA) * Math.max(0, yB - yA);
        float boxAArea = (a[2] - a[0]) * (a[3] - a[1]);
        float boxBArea = (b[2] - b[0]) * (b[3] - b[1]);

        return interArea / (boxAArea + boxBArea - interArea);
    }
}
