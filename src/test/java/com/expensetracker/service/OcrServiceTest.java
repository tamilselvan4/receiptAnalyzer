package com.expensetracker.service;

import com.expensetracker.config.InvoiceProcessingProperties;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OcrServiceTest {

    @Test
    void returnsEmptyWhenTessdataIsUnavailable() throws Exception {
        InvoiceProcessingProperties properties = new InvoiceProcessingProperties();
        properties.getOcr().setTesseractDataPath("/path/that/does/not/exist");

        OcrService service = new OcrService(properties);

        Path imagePath = Files.createTempFile("ocr-test", ".png");
        try {
            BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
            ImageIO.write(image, "png", imagePath.toFile());

            String text = service.extractText(imagePath.toString(), "doc-1", ".png");

            assertEquals("", text);
        } finally {
            Files.deleteIfExists(imagePath);
        }
    }
}
