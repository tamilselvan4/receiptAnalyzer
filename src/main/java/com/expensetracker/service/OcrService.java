package com.expensetracker.service;

import net.sourceforge.tess4j.*;
import org.apache.commons.io.FileUtils;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.opencv_java;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Service;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Service
public class OcrService {

    static {
        Loader.load(opencv_java.class);
    }

    public static String extractText(File file) {
        ITesseract tesseract = new Tesseract();
        tesseract.setDatapath("/Users/tamilselvans/M.E/project/tess4j/Tess4J");
        tesseract.setLanguage("eng");

        try {
            return tesseract.doOCR(file);
        } catch (TesseractException e) {
            throw new RuntimeException("Error during OCR", e);
        }
    }

    public String extractText(String path, String uuid, String ext, String expUploadPath) {

        preprocess(path, uuid, ext, expUploadPath);

        ITesseract tesseract = new Tesseract();

        tesseract.setDatapath("/opt/homebrew/share/tessdata");
        tesseract.setLanguage("eng");
        tesseract.setPageSegMode(1); // Automatic page segmentation
        tesseract.setOcrEngineMode(1); // Neural nets LSTM engine

        try {
//            File file = new File("/Users/tamilselvans/Downloads/temp.png");
            File file = new File(expUploadPath + "files/" + uuid + "." + ext);
            String txt = tesseract.doOCR(file);

            File txtFile = new File(expUploadPath + "txt/", uuid + ".txt");
            try {
                FileUtils.writeStringToFile(txtFile, txt, StandardCharsets.UTF_8);
            } catch (IOException e) {
                System.out.println(e);
            }

            return txt;
        } catch (TesseractException e) {
            System.err.println("Error during OCR: " + e.getMessage());
        }
        return "";
    }

    public void preprocess(String path, String uuid, String ext, String expUploadPath) {

        Mat img = Imgcodecs.imread(path);

        if (img.empty()) {
            System.err.println("⚠️ Could not read the image at: " + path);
            return;
        }

        Mat gray = new Mat();
        Imgproc.cvtColor(img, gray, Imgproc.COLOR_BGR2GRAY);

        Mat thresh = new Mat();
        Imgproc.threshold(gray, thresh, 150, 255, Imgproc.THRESH_BINARY);

//        String outPath = "/Users/tamilselvans/Downloads/temp.png";
        String outPath = expUploadPath + "files/" + uuid + "." + ext;
        Imgcodecs.imwrite(outPath, thresh);

        System.out.println("✅ Preprocessed image saved at: " + outPath);
    }

    public static String extractTextWithBoxes(String imagePath) {
        try {
            File file = new File(imagePath);
            BufferedImage img = ImageIO.read(file);  // convert file to BufferedImage

            ITesseract tesseract = new Tesseract();
            tesseract.setDatapath("/opt/homebrew/share/tessdata");
            tesseract.setLanguage("eng");
            tesseract.setPageSegMode(1); // Automatic page segmentation
            tesseract.setOcrEngineMode(1); // LSTM engine

            // Get word-level OCR results
            List<Word> words = tesseract.getWords(img, ITessAPI.TessPageIteratorLevel.RIL_WORD);

            JSONArray textArray = new JSONArray();
            JSONArray boxesArray = new JSONArray();

            for (Word word : words) {
                textArray.put(word.getText());

                int x0 = word.getBoundingBox().x;
                int y0 = word.getBoundingBox().y;
                int x1 = x0 + word.getBoundingBox().width;
                int y1 = y0 + word.getBoundingBox().height;

                JSONArray box = new JSONArray();
                box.put(x0);
                box.put(y0);
                box.put(x1);
                box.put(y1);
                boxesArray.put(box);
            }

            JSONObject result = new JSONObject();
            result.put("text", textArray);
            result.put("boxes", boxesArray);

            // Optional: save to a file
            // Files.write(Paths.get("output.json"), result.toString(2).getBytes(StandardCharsets.UTF_8));

            System.out.println(result);
            return result.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return "{}";
        }
    }

//    public static void main(String[] args) {
//        OcrService ocrService = new OcrService();
//        String result = ocrService.extractText("/Users/tamilselvans/Downloads/invoice.png", UUID.randomUUID().toString(), "png", "/Users/tamilselvans/M.E/project/uploads/");
//        System.out.println("Extracted Text:");
//        System.out.println(result);
//    }

    public static void main(String[] args) throws Exception {
//        File file = new File("/Users/tamilselvans/Downloads/invoice.png");
//        BufferedImage img = ImageIO.read(file);  // convert file to BufferedImage
//
//        ITesseract tesseract = new Tesseract();
//        tesseract.setDatapath("/opt/homebrew/share/tessdata");
//        tesseract.setLanguage("eng");
//
//        // Use BufferedImage + level to get word list
//        List<Word> words = tesseract.getWords(img, ITessAPI.TessPageIteratorLevel.RIL_WORD);
//
//        for (Word word : words) {
//            String text = word.getText();
//            int x0 = word.getBoundingBox().x;
//            int y0 = word.getBoundingBox().y;
//            int x1 = x0 + word.getBoundingBox().width;
//            int y1 = y0 + word.getBoundingBox().height;
//
//            System.out.println(text + " -> [" + x0 + "," + y0 + "," + x1 + "," + y1 + "]");
//        }
        extractTextWithBoxes("/Users/tamilselvans/Downloads/invoice.png");
    }
}
