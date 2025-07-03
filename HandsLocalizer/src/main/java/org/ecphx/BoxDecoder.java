package org.ecphx;

public class BoxDecoder {

    public static float[] decode(float[] rawBox, float[] anchor) {
        int modelInputSize = 192;

        float x_center = (rawBox[0] + anchor[0]) * modelInputSize;
        float y_center = (rawBox[1] + anchor[1]) * modelInputSize;

        float width = rawBox[2] * modelInputSize;
        float height = rawBox[3] * modelInputSize;

        float x_min = x_center - width / 2f;
        float y_min = y_center - height / 2f;
        float x_max = x_center + width / 2f;
        float y_max = y_center + height / 2f;

        return new float[]{x_min, y_min, x_max, y_max};
    }
}
