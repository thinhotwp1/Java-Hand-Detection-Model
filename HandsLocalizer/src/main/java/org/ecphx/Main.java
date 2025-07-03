package org.ecphx;

import ai.djl.inference.Predictor;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import ai.djl.translate.Batchifier;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Main {

    private static final String resPath = "src/main/resources/";
    private static final String imgPath = "imgs/";
    private static final String modelsPath = "models/";
    public static float[] flatBoxes;
    public static float[] flatScores;

    public static void main(String[] args) {
        try {
            Image original = ImageFactory.getInstance().fromFile(Paths.get(resPath + imgPath + "g.jpg"));

            // Resize to 128x128 to match model input
            //original.getTransform().resize(128, 128);

            // Load model with ONNX Runtime engine
            Criteria<Image, NDList> criteria = Criteria.builder()
                    .setTypes(Image.class, NDList.class)
                    .optModelPath(Paths.get(resPath + modelsPath + "hand_detector.onnx"))
                    .optEngine("OnnxRuntime") // Use ONNX Runtime
                    .optTranslator(new Translator<Image, NDList>() {

                        @Override
                        public NDList processInput(TranslatorContext ctx, Image input) {
                            // Step 1: Crop e resize con padding come in Unity
                            BufferedImage original = (BufferedImage) input.getWrappedImage();

                            int srcW = original.getWidth();
                            int srcH = original.getHeight();
                            int targetSize = 192;

                            float scale = Math.min((float) targetSize / srcW, (float) targetSize / srcH);
                            int resizedW = Math.round(srcW * scale);
                            int resizedH = Math.round(srcH * scale);

                            // Resize mantenendo il rapporto d’aspetto
                            Image resized = input.resize(resizedW, resizedH, false);

                            // Step 2: Creare immagine 192x192 vuota (nera) e disegnare la resized centrata
                            BufferedImage padded = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_RGB);
                            Graphics2D g = padded.createGraphics();

                            int offsetX = (targetSize - resizedW) / 2;
                            int offsetY = (targetSize - resizedH) / 2;

                            g.drawImage((BufferedImage) resized.getWrappedImage(), offsetX, offsetY, null);
                            g.dispose();

                            // Step 3: Normalizza i pixel (float32 RGB [0,1]) in formato NHWC
                            float[] data = new float[targetSize * targetSize * 3];
                            int idx = 0;

                            for (int y = 0; y < targetSize; y++) {
                                for (int x = 0; x < targetSize; x++) {
                                    int pixel = padded.getRGB(x, y);
                                    int r = (pixel >> 16) & 0xff;
                                    int gVal = (pixel >> 8) & 0xff;
                                    int b = pixel & 0xff;

                                    data[idx++] = r / 255f;
                                    data[idx++] = gVal / 255f;
                                    data[idx++] = b / 255f;
                                }
                            }

                            NDManager manager = ctx.getNDManager();
                            NDArray inputTensor = manager.create(data, new Shape(1, targetSize, targetSize, 3));

                            return new NDList(inputTensor);
                        }


                        @Override
                        public NDList processOutput(TranslatorContext ctx, NDList list) {
                            System.out.println("list: " + Arrays.toString(list.getShapes()));
                            // list.get(0): [1, 2016, 18]
                            // list.get(1): [1, 2016, 1]

                            float[] fullBoxes = list.get(0).toFloatArray();   // [1 × 2016 × 18] = 36288
                            float[] fullScores = list.get(1).toFloatArray();  // [1 × 2016 × 1] = 2016

                            Main.flatBoxes = fullBoxes;
                            Main.flatScores = fullScores;

                            return list;
                        }




                        @Override
                        public Batchifier getBatchifier() {
                            return null;
                        }
                    })
                    .build();


            try (ZooModel<Image, NDList> model = ModelZoo.loadModel(criteria);
                 Predictor<Image, NDList> predictor = model.newPredictor()) {

                NDList result = predictor.predict(original);

                // ✅ Estrai i dati prima che il result venga rilasciato
                float[] flatBoxes = Main.flatBoxes; // result.get(0).toFloatArray();  // [1, 2016, 18]
                float[] flatScores = Main.flatScores; // result.get(1).toFloatArray(); // [1, 2016, 1]

                int numBoxes = flatScores.length;
                System.out.println("🔍 Num Boxes : " + numBoxes);

                // Ricostruisci la struttura [2016][18]
                float[][] rawBoxArray = new float[numBoxes][18];
                for (int i = 0; i < numBoxes; i++) {
                    System.arraycopy(flatBoxes, i * 18, rawBoxArray[i], 0, 18);
                    System.out.printf("rawBox[0]=%.3f, rawBox[1]=%.3f%n", rawBoxArray[i][0], rawBoxArray[i][1]);
                }

                // Carica gli anchor
                float[][] anchorData = AnchorLoader.load(resPath + modelsPath + "anchors.csv");
                System.out.println("🔍 Loaded anchors: " + anchorData.length);
                for (int i = 0; i < 5; i++) {
                    System.out.printf("Anchor %d: [%.4f, %.4f, %.4f, %.4f]%n",
                            i, anchorData[i][0], anchorData[i][1], anchorData[i][2], anchorData[i][3]);
                }


                // Decode & filtra
                List<float[]> decodedBoxes = new ArrayList<>();
                List<Float> finalScores = new ArrayList<>();
                float scoreThreshold = 0.5000f;

                for (int i = 0; i < numBoxes; i++) {
                    float score = flatScores[i];
                    if (score > scoreThreshold) {
                        float[] decoded = BoxDecoder.decode(rawBoxArray[i], anchorData[i]);
                        decodedBoxes.add(decoded);
                        finalScores.add(score);
                    }
                }

                System.out.println("🔍 Boxes over threshold: " + decodedBoxes.size());

                // Non-Max Suppression
                float iouThreshold = 0.1f;
                List<Integer> keepIndices = NonMaxSuppression.apply(decodedBoxes, finalScores, iouThreshold);

                for (int i : keepIndices) {
                    float[] box = decodedBoxes.get(i);
                    float score = finalScores.get(i);
                    System.out.printf("▶ Final Detection - Score: %.2f → [xMin=%.2f, yMin=%.2f, xMax=%.2f, yMax=%.2f]%n",
                            score, box[0], box[1], box[2], box[3]);

                    // Stampa i keypoint
                    KeypointPrinter.print(rawBoxArray[i]);
                }



                // Mostra immagine
                BufferedImage bimg = (BufferedImage) original.getWrappedImage();
                ImageDisplayer.showImage(bimg, "Detected Hands");

            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
