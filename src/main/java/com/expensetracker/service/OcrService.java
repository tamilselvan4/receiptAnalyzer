package com.expensetracker.service;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.opencv_java;
import org.springframework.stereotype.Service;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;

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

    public String extractText(String path) {

        preprocess(path);

        ITesseract tesseract = new Tesseract();

        tesseract.setDatapath("/opt/homebrew/share/tessdata");
        tesseract.setLanguage("eng");
        tesseract.setPageSegMode(1); // Automatic page segmentation
        tesseract.setOcrEngineMode(1); // Neural nets LSTM engine

        try {
            File file = new File("/Users/tamilselvans/Downloads/temp.png");
            return tesseract.doOCR(file);
        } catch (TesseractException e) {
            System.err.println("Error during OCR: " + e.getMessage());
        }
        return "";
    }

    public void preprocess(String path) {

        Mat img = Imgcodecs.imread(path);

        if (img.empty()) {
            System.err.println("⚠️ Could not read the image at: " + path);
            return;
        }

        Mat gray = new Mat();
        Imgproc.cvtColor(img, gray, Imgproc.COLOR_BGR2GRAY);

        Mat thresh = new Mat();
        Imgproc.threshold(gray, thresh, 150, 255, Imgproc.THRESH_BINARY);

        String outPath = "/Users/tamilselvans/Downloads/temp.png";
        Imgcodecs.imwrite(outPath, thresh);

        System.out.println("✅ Preprocessed image saved at: " + outPath);
    }

    public static void main(String[] args) {
        OcrService ocrService = new OcrService();
        String result = ocrService.extractText("/Users/tamilselvans/Downloads/invoice.png");
        System.out.println("Extracted Text:");
        System.out.println(result);
    }
}
