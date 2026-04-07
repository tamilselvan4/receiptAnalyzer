package com.expensetracker.service;

import com.expensetracker.config.InvoiceProcessingProperties;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.opencv_java;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);
    private static final boolean OPENCV_AVAILABLE;

    static {
        boolean loaded;
        try {
            Loader.load(opencv_java.class);
            loaded = true;
        } catch (Throwable throwable) {
            loaded = false;
        }
        OPENCV_AVAILABLE = loaded;
    }

    private final InvoiceProcessingProperties properties;

    public OcrService(InvoiceProcessingProperties properties) {
        this.properties = properties;
    }

    public String extractText(String path, String documentId, String extension) {
        Path workspace = null;
        try {
            workspace = Files.createTempDirectory("ocr-" + documentId + "-");
            Path preprocessedImage = preprocess(path, workspace, documentId, extension);
            String text = doOcr(preprocessedImage.toFile());
            persistArtifactsIfEnabled(preprocessedImage, documentId, extension, text);
            return text;
        } catch (Exception exception) {
            log.warn("OCR failed for {}: {}", path, exception.getMessage());
            return "";
        } finally {
            deleteDirectoryQuietly(workspace);
        }
    }

    private String doOcr(File file) throws TesseractException {
        Path dataPath = resolveTesseractDataPath();
        if (dataPath == null) {
            log.warn("Skipping OCR because no valid tessdata directory was found. Configure invoice.processing.ocr.tesseract-data-path or TESSDATA_PREFIX.");
            return "";
        }

        String language = properties.getOcr().getLanguage();
        Path trainedDataFile = dataPath.resolve(language + ".traineddata");
        if (!Files.isRegularFile(trainedDataFile)) {
            log.warn("Skipping OCR because language data file is missing: {}", trainedDataFile);
            return "";
        }

        ITesseract tesseract = new Tesseract();
        tesseract.setDatapath(dataPath.toString());
        tesseract.setLanguage(language);
        tesseract.setPageSegMode(properties.getOcr().getPageSegMode());
        tesseract.setOcrEngineMode(properties.getOcr().getEngineMode());
        return tesseract.doOCR(file);
    }

    private Path preprocess(String sourcePath, Path workspace, String documentId, String extension) throws Exception {
        String normalizedExtension = normalizeExtension(extension);
        Path outputPath = workspace.resolve(documentId + normalizedExtension);
        if (!OPENCV_AVAILABLE) {
            Files.copy(Path.of(sourcePath), outputPath, StandardCopyOption.REPLACE_EXISTING);
            log.debug("OpenCV unavailable; using source image without preprocessing");
            return outputPath;
        }

        Mat image = Imgcodecs.imread(sourcePath);
        if (image.empty()) {
            Files.copy(Path.of(sourcePath), outputPath, StandardCopyOption.REPLACE_EXISTING);
            log.warn("Could not read image at {}; OCR will use the original file", sourcePath);
            return outputPath;
        }

        Mat gray = new Mat();
        Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);

        Mat thresholded = new Mat();
        Imgproc.threshold(gray, thresholded, 150, 255, Imgproc.THRESH_BINARY);
        Imgcodecs.imwrite(outputPath.toString(), thresholded);
        return outputPath;
    }

    private void persistArtifactsIfEnabled(Path processedImage, String documentId, String extension, String text) {
        if (!properties.getOcr().isPersistArtifacts()) {
            return;
        }
        try {
            Path basePath = Path.of(properties.getStorage().getUploadPath());
            Path filesDir = basePath.resolve("files");
            Path textDir = basePath.resolve("txt");
            Files.createDirectories(filesDir);
            Files.createDirectories(textDir);

            String normalizedExtension = normalizeExtension(extension);
            Files.copy(processedImage, filesDir.resolve(documentId + normalizedExtension), StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(textDir.resolve(documentId + ".txt"), text == null ? "" : text, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            log.warn("Failed to persist OCR artifacts: {}", exception.getMessage());
        }
    }

    private void deleteDirectoryQuietly(Path directory) {
        if (directory == null) {
            return;
        }
        try {
            Files.walk(directory)
                    .sorted((left, right) -> right.compareTo(left))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                            log.debug("Unable to delete temporary OCR path {}", path);
                        }
                    });
        } catch (Exception ignored) {
            log.debug("Unable to delete OCR workspace {}", directory);
        }
    }

    private String normalizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return ".png";
        }
        return extension.startsWith(".") ? extension : "." + extension;
    }

    private Path resolveTesseractDataPath() {
        String configuredPath = properties.getOcr().getTesseractDataPath();
        if (configuredPath != null && !configuredPath.isBlank()) {
            Path configured = Path.of(configuredPath);
            if (Files.isDirectory(configured)) {
                return configured;
            }
            log.warn("Configured tessdata path does not exist or is not a directory: {}", configuredPath);
        }

        String envPath = System.getenv("TESSDATA_PREFIX");
        if (envPath != null && !envPath.isBlank()) {
            Path env = Path.of(envPath);
            if (Files.isDirectory(env)) {
                return env;
            }
            log.warn("TESSDATA_PREFIX does not point to a valid directory: {}", envPath);
        }

        for (String candidatePath : List.of(
                "/opt/homebrew/share/tessdata",
                "/usr/local/share/tessdata",
                "/usr/share/tessdata",
                "/usr/share/tesseract-ocr/4.00/tessdata",
                "/usr/share/tesseract-ocr/5/tessdata"
        )) {
            Path candidate = Path.of(candidatePath);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }

        return null;
    }
}
