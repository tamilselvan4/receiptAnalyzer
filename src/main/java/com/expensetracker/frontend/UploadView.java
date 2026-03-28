package com.expensetracker.frontend;

import com.expensetracker.model.Expense;
import com.expensetracker.model.ReceiptResponse;
import com.expensetracker.model.User;
import com.expensetracker.model.Validation;
import com.expensetracker.service.*;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
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
import com.vaadin.flow.spring.annotation.UIScope;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Route("/add")
@UIScope
@Component
public class UploadView extends VerticalLayout {

    @Value("${expense.file.upload.path}")
    private String expenseFileUploadPath;

    private final Image imagePreview;
    private final MemoryBuffer buffer;
    private final Upload upload;
    private final Checkbox useExternalAICheckBox = new Checkbox();

    private final TextField nameField;
    private final DatePicker dateField;
    private final TextField amountField;
    private final TextField taxField;
    private final TextField categoryField;
    private final TextArea commentField;

    private final TextField isAnomaly;
    private final TextField anomalyReason;

    Expense expense = new Expense();
    ReceiptResponse receipt = new ReceiptResponse();
    Validation validation = new Validation();

    VerticalLayout validationLayout = new VerticalLayout();

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private UserService userService;

    private final OcrService ocrService = new OcrService();
    private final AiParserService aiParserService = new AiParserService();
    private final LocalAiExtractionService localService = new LocalAiExtractionService();
    private final AgenticRagService agenticRagService = new AgenticRagService();

    VerticalLayout dataView = new VerticalLayout();

    public UploadView() {

        setSizeFull();
        setPadding(false);
        setSpacing(false);
        getStyle()
                .set("background", "#f4f6fb")
                .set("overflow", "hidden");

        H1 header = new H1("Add Expense");
        header.getStyle().set("color", "#007bff").set("margin", "0 0 0 0");

        VerticalLayout formLayout = new VerticalLayout();
        formLayout.setWidth("40%");
        formLayout.setPadding(true);
        formLayout.setSpacing(true);
        formLayout.getStyle()
                .set("background", "white")
                .set("borderRadius", "12px")
                .set("boxShadow", "0 2px 12px rgba(0,0,0,0.08)");
//                .set("margin", "2rem 2rem 2rem 0")
//                .set("padding", "2rem 4rem 0 4rem");

        nameField = new TextField("Name");
        dateField = new DatePicker("Date");
        HorizontalLayout firstRow = new HorizontalLayout();
        firstRow.add(nameField, dateField);
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
            syncExpenseFromFields();
            if (expense.getAmount() == null) {
                Notification.show("Please enter the Expense Amount", 2000, Notification.Position.TOP_CENTER);
                return;
            }

            if (expense.getUser() == null) {
                User user = new User();
                user.setId(1L);
                expense.setUser(user);
            }

            expenseService.saveExpense(expense);
            getUI().ifPresent(ui -> ui.getPage().setLocation("/"));
            Notification.show("Expense submitted!", 2000, Notification.Position.TOP_CENTER);
        });

        dataView.add(firstRow, amountField, taxField, categoryField, commentField, upload);

        isAnomaly = new TextField("Is Anomaly");
        anomalyReason = new TextField("Anomaly Reason");
        validationLayout.add(isAnomaly, anomalyReason);
        validationLayout.setVisible(false);

        formLayout.add(header, dataView, validationLayout, uploadBtn);

        VerticalLayout viewerLayout = new VerticalLayout();
        viewerLayout.setWidth("60%");
        viewerLayout.setAlignItems(Alignment.CENTER);
        viewerLayout.setJustifyContentMode(JustifyContentMode.CENTER);
        viewerLayout.getStyle()
                .set("background", "white")
//                .set("borderRadius", "12px")
                .set("boxShadow", "0 2px 12px rgba(0,0,0,0.08)")
                .set("margin", "0 0 0 0");

        imagePreview = new Image();
        imagePreview.getStyle()
                .set("objectFit", "contain")
                .set("border", "1px solid #eee")
                .set("borderRadius", "8px");

        useExternalAICheckBox.setLabel("Use API");
        useExternalAICheckBox.setValue(true);

        viewerLayout.add(imagePreview, upload, useExternalAICheckBox);

        HorizontalLayout mainLayout = new HorizontalLayout(viewerLayout, formLayout);
        mainLayout.setSizeFull();
        mainLayout.setSpacing(true);

        add(mainLayout);
    }

    private void showImagePreview() {
        String mimeType = buffer.getFileData().getMimeType();
        String uuid = "1_" + UUID.randomUUID();
        System.out.println("Path: " + expenseFileUploadPath);
        if (mimeType.startsWith("image/")) {
            try {
                byte[] bytes = buffer.getInputStream().readAllBytes();

                String base64 = java.util.Base64.getEncoder().encodeToString(bytes);
                imagePreview.setSrc("data:" + mimeType + ";base64," + base64);
                imagePreview.setWidth("100%");
                imagePreview.setHeight("100%");
                upload.setVisible(false);

                String fileName = buffer.getFileName();
                int dotIndex = fileName.lastIndexOf('.');
                String extension = (dotIndex > 0) ? fileName.substring(fileName.lastIndexOf('.') + 1) : "";

                File tempFile = File.createTempFile(uuid, extension);
                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    fos.write(bytes);
                }

                if (!useExternalAICheckBox.getValue()) {
                    String jsonResult = localService.localTextExtraction(tempFile.getAbsolutePath());

                    ObjectMapper mapper = new ObjectMapper();
                    mapper.registerModule(new JavaTimeModule());
                    mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
                    receipt = mapper.readValue(jsonResult, ReceiptResponse.class);
                    setReceiptData(receipt);
                } else {

                    JSONObject jsonResult = agenticRagService.analyzeReceipt(fileName, bytes);
                    System.out.println(jsonResult.toString(2));

                    JSONObject extractedData = jsonResult.getJSONObject("structured_data");
                    JSONObject validationData = jsonResult.getJSONObject("validation");

                    ObjectMapper mapper = new ObjectMapper();
                    mapper.registerModule(new JavaTimeModule());
                    expense = mapper.readValue(extractedData.toString(), Expense.class);
                    validation = mapper.readValue(validationData.toString(), Validation.class);

                    setExpenseData(expense);
                    setValidationData(validation);

                    expense.setUser(userService.getUser(1L));
                    expense.setFileName(uuid + "." + extension);

                    /*String extractedText = ocrService.extractText(tempFile.getAbsolutePath(), uuid, extension, expenseFileUploadPath);

                    System.out.println("**********************************");
                    System.out.println(extractedText);
                    System.out.println("**********************************");

                    String jsonResult = aiParserService.extractInvoiceDataUsingGenAI(extractedText);
                    //String jsonResult = aiParserService.extractInvoiceDataUsingLocalModel(extractedText);
                    //String jsonResult = localService.localTextExtraction(tempFile.getAbsolutePath());

                    System.out.println(jsonResult);

                    ObjectMapper mapper = new ObjectMapper();
                    mapper.registerModule(new JavaTimeModule());
                    expense = mapper.readValue(jsonResult, Expense.class);

                    setExpenseData(expense);

                    expense.setUser(userService.getUser(1L));
                    expense.setFileName(uuid + "." + extension);*/

                }


            } catch (IOException ex) {
                System.out.println("Exception: " + ex);
                Notification.show("Failed to preview image.", 2000, Notification.Position.TOP_CENTER);
            }
        } else {
            imagePreview.setSrc("https://cdn-icons-png.flaticon.com/512/337/337946.png");
        }
    }

    private void setValidationData(Validation validation) {
        setTextField(isAnomaly, String.valueOf(validation.isAnomaly()));
        setTextField(anomalyReason, validation.getReason());
        /*isAnomaly.setValue(String.valueOf(validation.isAnomaly()));
        anomalyReason.setValue(validation.getReason());*/

        validationLayout.setVisible(true);
    }

    private void setReceiptData(ReceiptResponse expense) {
        setTextField(nameField, expense.getCompany());
        setDateField(dateField, expense.getDate());
        setTextField(amountField, String.valueOf(expense.getAmount()));
        taxField.setLabel("Location");
        setTextField(taxField, expense.getAddress());
        /*nameField.setValue(expense.getCompany());
        dateField.setValue(expense.getDate());
        amountField.setValue(expense.getAmount());
        taxField.setLabel("Location");
        taxField.setValue(expense.getAddress());*/
    }

    private void setExpenseData(Expense expense) {
        setTextField(nameField, expense.getName());
        setDateField(dateField, expense.getDate());
        setTextField(amountField, String.valueOf(expense.getAmount()));
        setTextField(taxField, String.valueOf(expense.getTax()));
        setTextField(categoryField, expense.getCategory());
        setTextArea(commentField, expense.getComment());

        /*nameField.setValue(expense.getName());
        dateField.setValue(expense.getDate());
        amountField.setValue(String.valueOf(expense.getAmount()));
        taxField.setValue(String.valueOf(expense.getTax()));
        categoryField.setValue(expense.getCategory());
        commentField.setValue(expense.getComment());*/

    }

    private void setTextArea(TextArea commentField, String comment) {
        commentField.setValue(comment != null ? comment : "");
    }

    private void setDateField(DatePicker dateField, LocalDate date) {
        dateField.setValue(date != null ? date : LocalDate.now());
    }

    private void setTextField(TextField field, String value) {
        field.setValue(value != null ? value : "");
    }

    private void syncExpenseFromFields() {
        if (expense == null) {
            expense = new Expense();
        }
        expense.setName(nameField.getValue());
        expense.setCategory(categoryField.getValue());
        expense.setComment(commentField.getValue());
        expense.setDate(dateField.getValue());

        try {
            String amt = amountField.getValue() != null ? amountField.getValue().trim() : "";
            expense.setAmount(amt.isEmpty() ? null : new BigDecimal(amt));
        } catch (Exception ignored) {
            expense.setAmount(null);
        }

        try {
            String tx = taxField.getValue() != null ? taxField.getValue().trim() : "";
            expense.setTax(tx.isEmpty() ? null : new BigDecimal(tx));
        } catch (Exception ignored) {
            expense.setTax(null);
        }
    }

}
