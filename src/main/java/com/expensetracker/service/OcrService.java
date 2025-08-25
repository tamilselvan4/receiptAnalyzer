package com.expensetracker.service;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.commons.io.FileUtils;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.opencv_java;
import org.springframework.stereotype.Service;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    public static void main(String[] args) {
        OcrService ocrService = new OcrService();
        String result = ocrService.extractText("/Users/tamilselvans/Downloads/invoice.png", UUID.randomUUID().toString(), "png", "/Users/tamilselvans/M.E/project/uploads/");
        System.out.println("Extracted Text:");
        System.out.println(result);
    }
}
