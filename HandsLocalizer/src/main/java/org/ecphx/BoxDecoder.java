package org.ecphx;

public class BoxDecoder {

    public static float[] decode(float[] rawBox, float[] anchor) {
        // Anchors: x_center, y_center, width=1, height=1
        // Output box: [dx_center, dy_center, w, h] (in normalized units)

        float x_center = rawBox[0] + anchor[0];
        float y_center = rawBox[1] + anchor[1];

        float width = rawBox[2];   // ❌ NO exp()
        float height = rawBox[3];

        float x_min = x_center - width / 2f;
        float y_min = y_center - height / 2f;
        float x_max = x_center + width / 2f;
        float y_max = y_center + height / 2f;

        return new float[]{x_min, y_min, x_max, y_max};
    }
}
