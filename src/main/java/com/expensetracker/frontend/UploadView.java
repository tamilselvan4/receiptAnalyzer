package com.expensetracker.frontend;

import com.expensetracker.model.Expense;
import com.expensetracker.service.AiParserService;
import com.expensetracker.service.ExpenseService;
import com.expensetracker.service.OcrService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.Route;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

@Route("/add")
public class UploadView extends VerticalLayout {

    private final Image imagePreview;
    private final MemoryBuffer buffer;
    private final Upload upload;

    private final TextField nameField;
    private final DatePicker dateField;
    private final TextField amountField;
    private final TextField taxField;
    private final TextField categoryField;
    private final TextArea commentField;

    Expense expense;

    @Autowired
    private ExpenseService expenseService;

//    private final ExpenseService expenseService = new ExpenseService();
    private final OcrService ocrService = new OcrService();
    private final AiParserService aiParserService = new AiParserService();

    public UploadView() {

        setSizeFull();
        setPadding(false);
        setSpacing(false);
        getStyle()
                .set("background", "#f4f6fb")
                .set("overflow", "hidden");

        H1 header = new H1("Add Expense");
        header.getStyle().set("color", "#007bff").set("margin", "0 0 2rem 0");

        VerticalLayout formLayout = new VerticalLayout();
        formLayout.setWidth("40%");
        formLayout.setPadding(true);
        formLayout.setSpacing(true);
        formLayout.getStyle()
                .set("background", "white")
                .set("borderRadius", "12px")
                .set("boxShadow", "0 2px 12px rgba(0,0,0,0.08)")
                .set("margin", "2rem 2rem 2rem 0")
                .set("padding", "2rem 4rem 0 4rem");

        nameField = new TextField("Name");
        dateField = new DatePicker("Date");
        amountField = new TextField("Amount");
        taxField = new TextField("Tax");
        categoryField = new TextField("Category");
        commentField = new TextArea("Comment");
        commentField.setWidthFull();

        buffer = new MemoryBuffer();
        upload = new Upload(buffer);
        upload.setAcceptedFileTypes("image/png", "image/jpeg", "application/pdf");
        upload.setMaxFileSize(5 * 1024 * 1024);
        upload.getStyle()
                .set("border", "2px dashed #88b5fc")
                .set("borderRadius", "8px")
                .set("background", "#f8f9fa")
                .set("padding", "1rem");
        upload.setWidthFull();
        upload.setHeightFull();

        Button uploadBtn = new Button("Add", new Icon(VaadinIcon.PLUS));
        uploadBtn.getStyle().set("background", "#007bff").set("color", "white").set("marginTop", "2rem");
        uploadBtn.setWidthFull();

        upload.addSucceededListener(event -> {
            showImagePreview();
            Notification.show("File uploaded!", 2000, Notification.Position.TOP_CENTER);
        });

        uploadBtn.addClickListener(e -> {
            if (buffer.getInputStream() == null) {
                Notification.show("Please upload a file first.", 2000, Notification.Position.TOP_CENTER);
                return;
            }

            expenseService.saveExpense(expense);
            Notification.show("Expense submitted!", 2000, Notification.Position.TOP_CENTER);
        });

        formLayout.add(header, nameField, dateField, amountField, taxField, categoryField, commentField, upload, uploadBtn);

        VerticalLayout viewerLayout = new VerticalLayout();
        viewerLayout.setWidth("60%");
        viewerLayout.setAlignItems(Alignment.CENTER);
        viewerLayout.setJustifyContentMode(JustifyContentMode.CENTER);
        viewerLayout.getStyle()
                .set("background", "white")
                .set("borderRadius", "12px")
                .set("boxShadow", "0 2px 12px rgba(0,0,0,0.08)")
                .set("margin", "2rem 0 2rem 2rem");

        imagePreview = new Image();
        imagePreview.getStyle()
                .set("objectFit", "contain")
                .set("border", "1px solid #eee")
                .set("borderRadius", "8px");

        viewerLayout.add(imagePreview, upload);

        HorizontalLayout mainLayout = new HorizontalLayout(viewerLayout, formLayout);
        mainLayout.setSizeFull();
        mainLayout.setSpacing(true);

        add(mainLayout);
    }

    private void showImagePreview() {
        String mimeType = buffer.getFileData().getMimeType();
        if (mimeType.startsWith("image/")) {
            try {
                byte[] bytes = buffer.getInputStream().readAllBytes();

                String base64 = java.util.Base64.getEncoder().encodeToString(bytes);
                imagePreview.setSrc("data:" + mimeType + ";base64," + base64);
                imagePreview.setWidth("100%");
                imagePreview.setHeight("100%");
                upload.setVisible(false);

                File tempFile = File.createTempFile("upload", ".png"); // or ".jpg" depending on input
                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    fos.write(bytes);
                }

                String extractedText = ocrService.extractText(tempFile.getAbsolutePath());

                /*String jsonResult = "{\n" +
                        "  \"name\": \"Groceries\",\n" +
                        "  \"amount\": 1500.75,\n" +
                        "  \"tax\": 75.50,\n" +
                        "  \"currency\": \"INR\",\n" +
                        "  \"date\": \"2024-06-10\",\n" +
                        "  \"category\": \"Food\",\n" +
                        "  \"comment\": \"Weekly shopping at supermarket\"\n" +
                        "}";*/

                String jsonResult = aiParserService.extractInvoiceDataUsingGenAI(extractedText);
//                String jsonResult = aiParserService.extractInvoiceDataUsingLocalMethod(extractedText);

                ObjectMapper mapper = new ObjectMapper();
                mapper.registerModule(new JavaTimeModule());
                expense = mapper.readValue(jsonResult, Expense.class);

                setExpenseData(expense);

                tempFile.deleteOnExit();

            } catch (IOException ex) {
                System.out.println("Exception: " + ex);
                Notification.show("Failed to preview image.", 2000, Notification.Position.TOP_CENTER);
            }
        } else {
            imagePreview.setSrc("https://cdn-icons-png.flaticon.com/512/337/337946.png");
        }
    }

    private void setExpenseData(Expense expense) {
        nameField.setValue(expense.getName());
        dateField.setValue(expense.getDate());
        amountField.setValue(String.valueOf(expense.getAmount()));
        taxField.setValue(String.valueOf(expense.getTax()));
        categoryField.setValue(expense.getCategory());
        commentField.setValue(expense.getComment());

    }
}
