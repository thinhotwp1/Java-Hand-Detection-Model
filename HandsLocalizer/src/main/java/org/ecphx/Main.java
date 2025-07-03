
package org.ecphx;

import ai.djl.inference.Predictor;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.Batchifier;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import org.ecphx.process.ImageDrawer;
import org.ecphx.process.PreprocessInfo;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Main {

    private static final String resPath = "src/main/resources/";
    private static final String imgPath = "imgs/";
    private static final String modelsPath = "models/";
    private static final String outputPath = "output/";

    public static float[] flatBoxes;
    public static float[] flatScores;
    public static PreprocessInfo preprocessInfo;

    public static void main(String[] args) {
        try {
            Image inputImage = loadImage("a.jpg");
            Criteria<Image, NDList> criteria = buildCriteria();

            try (ZooModel<Image, NDList> model = ModelZoo.loadModel(criteria);
                 Predictor<Image, NDList> predictor = model.newPredictor()) {

                NDList result = predictor.predict(inputImage);
                processResult(result, inputImage);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Image loadImage(String fileName) throws Exception {
        return ImageFactory.getInstance().fromFile(Paths.get(resPath + imgPath + fileName));
    }

    private static Criteria<Image, NDList> buildCriteria() {
        return Criteria.builder()
                .setTypes(Image.class, NDList.class)
                .optModelPath(Paths.get(resPath + modelsPath + "hand_detector.onnx"))
                .optEngine("OnnxRuntime")
                .optTranslator(new Translator<Image, NDList>() {

                    @Override
                    public NDList processInput(TranslatorContext ctx, Image input) {
                        BufferedImage original = (BufferedImage) input.getWrappedImage();
                        int srcW = original.getWidth();
                        int srcH = original.getHeight();
                        int targetSize = 192;

                        float scale = Math.min((float) targetSize / srcW, (float) targetSize / srcH);
                        int resizedW = Math.round(srcW * scale);
                        int resizedH = Math.round(srcH * scale);

                        Image resized = input.resize(resizedW, resizedH, false);
                        BufferedImage padded = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_RGB);
                        Graphics2D g = padded.createGraphics();
                        int offsetX = (targetSize - resizedW) / 2;
                        int offsetY = (targetSize - resizedH) / 2;
                        g.drawImage((BufferedImage) resized.getWrappedImage(), offsetX, offsetY, null);
                        g.dispose();

                        float[] data = new float[targetSize * targetSize * 3];
                        int idx = 0;
                        for (int y = 0; y < targetSize; y++) {
                            for (int x = 0; x < targetSize; x++) {
                                int pixel = padded.getRGB(x, y);
                                data[idx++] = ((pixel >> 16) & 0xff) / 255f;
                                data[idx++] = ((pixel >> 8) & 0xff) / 255f;
                                data[idx++] = (pixel & 0xff) / 255f;
                            }
                        }

                        NDManager manager = ctx.getNDManager();
                        NDArray inputTensor = manager.create(data, new Shape(1, targetSize, targetSize, 3));
                        preprocessInfo = new PreprocessInfo(scale, offsetX, offsetY, srcW, srcH);
                        return new NDList(inputTensor);
                    }

                    @Override
                    public NDList processOutput(TranslatorContext ctx, NDList list) {
                        System.out.println("list: " + Arrays.toString(list.getShapes()));
                        flatBoxes = list.get(0).toFloatArray();
                        flatScores = list.get(1).toFloatArray();
                        return list;
                    }

                    @Override
                    public Batchifier getBatchifier() {
                        return null;
                    }
                })
                .build();
    }

    private static void processResult(NDList result, Image original) throws Exception {
        float[] boxes = flatBoxes;
        float[] scores = flatScores;
        int numBoxes = scores.length;

        float[][] rawBoxArray = new float[numBoxes][18];

        System.out.println("Raw box values:");
        for (int i = 0; i < numBoxes; i++) {
            System.arraycopy(boxes, i * 18, rawBoxArray[i], 0, 18);
            System.out.println(Arrays.toString(rawBoxArray[i]));
        }

        float[][] anchors = AnchorLoader.load(resPath + modelsPath + "anchors.csv");
        List<float[]> decodedBoxes = new ArrayList<>();
        List<Float> finalScores = new ArrayList<>();
        float threshold = 0.5f;

        for (int i = 0; i < numBoxes; i++) {
            if (scores[i] > threshold) {
                System.out.println("Anchor for this box: " + Arrays.toString(anchors[i]));
                float[] decoded = BoxDecoder.decode(rawBoxArray[i], anchors[i]);
                float[] converted = convertBoxToOriginal(decoded, preprocessInfo);
                decodedBoxes.add(converted);
                finalScores.add(scores[i]);
            }
        }

        List<Integer> keep = NonMaxSuppression.apply(decodedBoxes, finalScores, 0.1f);

        try (FileWriter fw = new FileWriter(outputPath + "result.json")) {
            fw.write("[\n");
            for (int i = 0; i < keep.size(); i++) {
                int index = keep.get(i);
                float[] box = decodedBoxes.get(index);
                float score = finalScores.get(index);
                fw.write(String.format("  {\"score\": %.2f, \"xMin\": %.2f, \"yMin\": %.2f, \"xMax\": %.2f, \"yMax\": %.2f}%s\n",
                        score, box[0], box[1], box[2], box[3], (i < keep.size() - 1 ? "," : "")));

                System.out.printf("▶ Final Detection - Score: %.2f → [xMin=%.2f, yMin=%.2f, xMax=%.2f, yMax=%.2f]%n",
                        score, box[0], box[1], box[2], box[3]);


                System.out.println("Keypoints (Scaled to original image):");
                for (int j = 0; j < 7; j++) {
                    float x = rawBoxArray[index][4 + j * 2];
                    float y = rawBoxArray[index][4 + j * 2 + 1];

                    float anchorX = anchors[index][0];
                    float anchorY = anchors[index][1];
                    float anchorW = anchors[index][2];
                    float anchorH = anchors[index][3];

                    float[] modelPt = decodeKeypoint(x, y, anchorX, anchorY, anchorW, anchorH);

                    float[] imgPt = convertPoint(modelPt[0], modelPt[1], preprocessInfo);

                    System.out.printf("  Raw keypoint %d: (%.3f, %.3f)%n", j, x, y);
                    System.out.printf("  Keypoint %d: (%.3f, %.3f)%n", j, imgPt[0], imgPt[1]);
                }

            }
            fw.write("]\n");
        }

        BufferedImage bimg = (BufferedImage) original.getWrappedImage();
        ImageDrawer.drawDetections(bimg, decodedBoxes, rawBoxArray, keep, preprocessInfo);

        ImageDisplayer.showImage(bimg, "Detected Hands");
        ImageIO.write(bimg, "png", new File("output/result.png"));

    }

    public static float[] convertBoxToOriginal(float[] box, PreprocessInfo info) {
        float xMin = (box[0] - info.offsetX) / info.scale;
        float yMin = (box[1] - info.offsetY) / info.scale;
        float xMax = (box[2] - info.offsetX) / info.scale;
        float yMax = (box[3] - info.offsetY) / info.scale;

        // Clamp lại về trong kích thước ảnh
        xMin = Math.max(0, Math.min(xMin, info.originalWidth));
        yMin = Math.max(0, Math.min(yMin, info.originalHeight));
        xMax = Math.max(0, Math.min(xMax, info.originalWidth));
        yMax = Math.max(0, Math.min(yMax, info.originalHeight));

        return new float[]{xMin, yMin, xMax, yMax};
    }

    public static float[] decodeKeypoint(float rawX, float rawY, float anchorX, float anchorY, float anchorW, float anchorH) {
        float modelX = anchorX + rawX * anchorW;
        float modelY = anchorY + rawY * anchorH;

        float modelInputSize = 192.0f;
        return new float[]{
                modelX * modelInputSize,
                modelY * modelInputSize
        };
    }




    public static float[] convertPoint(float x, float y, PreprocessInfo info) {
        float newX = (x - info.offsetX) / info.scale;
        float newY = (y - info.offsetY) / info.scale;
        return new float[]{newX, newY};
    }

}
