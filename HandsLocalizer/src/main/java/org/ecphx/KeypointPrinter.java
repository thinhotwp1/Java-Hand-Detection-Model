package org.ecphx;

public class KeypointPrinter {

    public static void print(float[] box18) {
        System.out.println("Keypoints:");
        for (int kp = 0; kp < 7; kp++) {
            float kpX = box18[4 + kp * 2];
            float kpY = box18[4 + kp * 2 + 1];
            System.out.printf("  Keypoint %d: (%.3f, %.3f)%n", kp, kpX, kpY);
        }
    }
}
