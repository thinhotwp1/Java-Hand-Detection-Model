package org.ecphx.process;

public class PreprocessInfo {
    public final float scale;
    public final int offsetX;
    public final int offsetY;
    public final int originalWidth;
    public final int originalHeight;

    public PreprocessInfo(float scale, int offsetX, int offsetY, int originalWidth, int originalHeight) {
        this.scale = scale;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.originalWidth = originalWidth;
        this.originalHeight = originalHeight;
    }
}
