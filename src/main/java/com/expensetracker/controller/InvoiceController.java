package com.expensetracker.controller;

import com.expensetracker.model.ExpenseExtractionResult;
import com.expensetracker.model.Invoice;
import com.expensetracker.repository.InvoiceRepository;
import com.expensetracker.service.ExpenseExtractionCoordinator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final ExpenseExtractionCoordinator extractionCoordinator;
    private final InvoiceRepository invoiceRepository;
    private final ObjectMapper objectMapper;

    public InvoiceController(ExpenseExtractionCoordinator extractionCoordinator, InvoiceRepository invoiceRepository) {
        this.extractionCoordinator = extractionCoordinator;
        this.invoiceRepository = invoiceRepository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @PostMapping(value = "/upload", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<String> uploadFile(@RequestBody byte[] fileBytes) throws IOException {
        ExpenseExtractionResult result = extractionCoordinator.extract("uploaded-file.png", fileBytes, true);
        String extractedText = objectMapper.writeValueAsString(result.getExpense());

        Invoice invoice = new Invoice();
        invoice.setFileName("uploaded-file");
        invoice.setFileData(fileBytes);
        invoice.setExtractedText(extractedText);
        invoiceRepository.save(invoice);

        return ResponseEntity.ok(extractedText);
    }

}
