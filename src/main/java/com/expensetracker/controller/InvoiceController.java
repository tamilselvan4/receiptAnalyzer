package com.expensetracker.controller;

import com.expensetracker.model.Invoice;
//import com.expensetracker.repository.InvoiceRepository;
import com.expensetracker.repository.InvoiceRepository;
import com.expensetracker.service.LocalAiExtractionService;
import com.expensetracker.service.OcrService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final OcrService ocrService;
    private final LocalAiExtractionService localService;
    private final InvoiceRepository invoiceRepository;

    public InvoiceController(OcrService ocrService, LocalAiExtractionService localService, InvoiceRepository invoiceRepository) {
        this.ocrService = ocrService;
        this.localService = localService;
        this.invoiceRepository = invoiceRepository;
    }

//    @PostMapping("/upload")
//    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) throws IOException {
//        File tempFile = File.createTempFile("upload", file.getOriginalFilename());
//        file.transferTo(tempFile);
//
//        String extractedText = ocrService.extractText(tempFile);
//
//        Invoice invoice = new Invoice();
//        invoice.setFileName(file.getOriginalFilename());
//        invoice.setFileData(file.getBytes());
//        invoice.setExtractedText(extractedText);
////        invoiceRepository.save(invoice);
//
//        return ResponseEntity.ok(extractedText);
//    }
    @PostMapping(value = "/upload", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<String> uploadFile(@RequestBody byte[] fileBytes) throws IOException {
        File tempFile = File.createTempFile("upload", ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(fileBytes);
        }

        String extractedText = "";
        try {
            //extractedText = ocrService.extractText(tempFile.getAbsolutePath(), UUID.randomUUID().toString(), "png", "/Users/tamilselvans/M.E/project/uploads/");
            extractedText = localService.localTextExtraction(tempFile.getAbsolutePath());
        } finally {
            if (tempFile.exists()) {
                System.out.println("tempFile deleted: " + tempFile.delete());
            }
        }

        Invoice invoice = new Invoice();
        invoice.setFileName("uploaded-file");
        invoice.setFileData(fileBytes);
        invoice.setExtractedText(extractedText);
//        invoiceRepository.save(invoice);

        return ResponseEntity.ok(extractedText);
    }

}

