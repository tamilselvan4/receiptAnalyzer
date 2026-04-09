package com.expensetracker.frontend;

import com.expensetracker.config.ExpenseCategoryCatalog;
import com.expensetracker.model.ClassificationResult;
import com.expensetracker.model.Expense;
import com.expensetracker.model.ExpenseExtractionResult;
import com.expensetracker.model.RiskAssessment;
import com.expensetracker.model.User;
import com.expensetracker.model.Validation;
import com.expensetracker.service.ExpenseClassificationService;
import com.expensetracker.service.ExpenseExtractionCoordinator;
import com.expensetracker.service.ExpenseService;
import com.expensetracker.service.InvoiceValidationService;
import com.expensetracker.service.RiskAssessmentService;
import com.expensetracker.service.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.annotation.UIScope;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.UUID;

@Route("/add")
@UIScope
@Component
public class UploadView extends VerticalLayout implements AfterNavigationObserver {

    private final Image imagePreview = new Image();
    private final MemoryBuffer buffer = new MemoryBuffer();
    private final Upload upload = new Upload(buffer);
    private final Checkbox useExternalAICheckBox = new Checkbox("Allow API fallback", true);
    private final InvoiceEditorForm invoiceEditorForm;

    private final ExpenseService expenseService;
    private final UserService userService;
    private final ExpenseExtractionCoordinator extractionCoordinator;
    private final ExpenseClassificationService classificationService;
    private final InvoiceValidationService validationService;
    private final RiskAssessmentService riskAssessmentService;

    private Expense expense = new Expense();
    private Validation validation = new Validation();
    private ClassificationResult classificationResult;
    private RiskAssessment riskAssessment;
    private String rawOcrText;

    public UploadView(ExpenseService expenseService,
                      UserService userService,
                      ExpenseExtractionCoordinator extractionCoordinator,
                      ExpenseCategoryCatalog expenseCategoryCatalog,
                      ExpenseClassificationService classificationService,
                      InvoiceValidationService validationService,
                      RiskAssessmentService riskAssessmentService) {
        this.expenseService = expenseService;
        this.userService = userService;
        this.extractionCoordinator = extractionCoordinator;
        this.classificationService = classificationService;
        this.validationService = validationService;
        this.riskAssessmentService = riskAssessmentService;
        this.invoiceEditorForm = new InvoiceEditorForm(expenseCategoryCatalog);

        configureLayout();
        configureUpload();
        invoiceEditorForm.setChangeListener(this::refreshDerivedSignals);

        invoiceEditorForm.setExpense(expense);
        invoiceEditorForm.setValidation(null);

        add(buildHeader(), buildMainLayout());
    }

    private void configureLayout() {
        setSizeFull();
        setPadding(true);
        setSpacing(true);
        getStyle()
                .set("background", "linear-gradient(180deg, #f8fbff 0%, #eef2ff 100%)")
                .set("overflow", "auto");

        imagePreview.setWidthFull();
        imagePreview.setHeight("520px");
        imagePreview.getStyle()
                .set("objectFit", "contain")
                .set("borderRadius", "20px")
                .set("background", "#f8fafc")
                .set("border", "1px solid #dbeafe");
    }

    private void configureUpload() {
        upload.setAcceptedFileTypes("image/png", "image/jpeg", "application/pdf");
        upload.setMaxFileSize(5 * 1024 * 1024);
        upload.setWidthFull();
        upload.getStyle()
                .set("border", "2px dashed #93c5fd")
                .set("borderRadius", "18px")
                .set("background", "#eff6ff")
                .set("padding", "18px");

        upload.addSucceededListener(event -> {
            renderPreview();
            Notification.show("File uploaded", 2000, Notification.Position.TOP_CENTER);
        });
    }

    private HorizontalLayout buildHeader() {
        H1 title = new H1("Invoice Details");
        title.getStyle()
                .set("margin", "0")
                .set("fontSize", "2rem")
                .set("color", "#0f172a");

        Paragraph subtitle = new Paragraph("Upload a receipt or invoice, review the extracted header fields and line items, then save the final record.");
        subtitle.getStyle()
                .set("margin", "4px 0 0 0")
                .set("color", "#475569");

        VerticalLayout copy = new VerticalLayout(title, subtitle);
        copy.setPadding(false);
        copy.setSpacing(false);

        HorizontalLayout header = new HorizontalLayout(copy);
        header.setWidthFull();
        header.setPadding(false);
        header.setSpacing(false);
        return header;
    }

    private HorizontalLayout buildMainLayout() {
        VerticalLayout previewColumn = buildPreviewColumn();
        VerticalLayout editorColumn = buildEditorColumn();

        HorizontalLayout mainLayout = new HorizontalLayout(previewColumn, editorColumn);
        mainLayout.setWidthFull();
        mainLayout.setAlignItems(Alignment.START);
        mainLayout.expand(editorColumn);
        return mainLayout;
    }

    private VerticalLayout buildPreviewColumn() {
        Span previewLabel = new Span("Preview");
        previewLabel.getStyle()
                .set("fontWeight", "700")
                .set("fontSize", "0.95rem")
                .set("color", "#0f172a");

        Paragraph previewHint = new Paragraph("Drag a document here or browse a file. The local model runs first, with the existing API fallback available if needed.");
        previewHint.getStyle()
                .set("margin", "0")
                .set("color", "#64748b");

        Button resetButton = new Button("Reset Form", new Icon(VaadinIcon.REFRESH));
        resetButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        resetButton.addClickListener(event -> resetState());

        VerticalLayout card = new VerticalLayout(previewLabel, previewHint, upload, useExternalAICheckBox, imagePreview, resetButton);
        card.setWidth("430px");
        card.setPadding(true);
        card.setSpacing(true);
        card.getStyle()
                .set("background", "white")
                .set("borderRadius", "24px")
                .set("boxShadow", "0 18px 45px rgba(15, 23, 42, 0.08)");

        return card;
    }

    private VerticalLayout buildEditorColumn() {
        Button saveButton = new Button("Save Expense", new Icon(VaadinIcon.CHECK));
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.getStyle()
                .set("background", "#0f766e")
                .set("color", "white");
        saveButton.addClickListener(event -> saveExpense());

        VerticalLayout editorColumn = new VerticalLayout(invoiceEditorForm, saveButton);
        editorColumn.setWidthFull();
        editorColumn.setPadding(false);
        editorColumn.setSpacing(true);
        editorColumn.setAlignItems(Alignment.STRETCH);
        return editorColumn;
    }

    private void renderPreview() {
        String mimeType = buffer.getFileData().getMimeType();
        String fileName = buffer.getFileName();
        String extension = resolveExtension(fileName);

        try {
            User currentUser = userService.getUser(1L);
            byte[] bytes = buffer.getInputStream().readAllBytes();
            if (mimeType != null && mimeType.startsWith("image/")) {
                String base64 = Base64.getEncoder().encodeToString(bytes);
                imagePreview.setSrc("data:" + mimeType + ";base64," + base64);
            } else {
                imagePreview.setSrc("https://cdn-icons-png.flaticon.com/512/337/337946.png");
            }

            ExpenseExtractionResult result = extractionCoordinator.extract(fileName, bytes, useExternalAICheckBox.getValue(), currentUser.getId());
            expense = result.getExpense();
            validation = result.getValidation();
            classificationResult = result.getClassificationResult();
            riskAssessment = result.getRiskAssessment();
            rawOcrText = result.getRawOcrText();

            expense.setUser(currentUser);
            expense.setFileName("1_" + UUID.randomUUID() + "." + extension);

            invoiceEditorForm.setExpense(expense);
            invoiceEditorForm.setAssessment(classificationResult, riskAssessment);
            invoiceEditorForm.setValidation(validation);
        } catch (Exception exception) {
            Notification.show("Failed to process uploaded file", 3000, Notification.Position.TOP_CENTER);
        }
    }

    private void saveExpense() {
        invoiceEditorForm.writeToExpense(expense);
        refreshDerivedSignals();
        if (expense.getAmount() == null) {
            Notification.show("Amount is required before saving", 2500, Notification.Position.TOP_CENTER);
            return;
        }

        if (expense.getUser() == null) {
            User user = userService.getUser(1L);
            expense.setUser(user);
        }

        expenseService.saveExpense(expense);
        Notification.show("Expense submitted", 2000, Notification.Position.TOP_CENTER);
        getUI().ifPresent(ui -> ui.navigate(""));
    }

    private void resetState() {
        expense = new Expense();
        validation = new Validation();
        classificationResult = null;
        riskAssessment = null;
        rawOcrText = null;
        invoiceEditorForm.setExpense(expense);
        invoiceEditorForm.setAssessment(null, null);
        invoiceEditorForm.setValidation(null);
        imagePreview.setSrc("");
        upload.clearFileList();
    }

    private void refreshDerivedSignals() {
        if (!hasReviewableContent()) {
            return;
        }
        invoiceEditorForm.writeToExpense(expense);
        classificationResult = classificationService.classify(expense, null, rawOcrText);
        validation = validationService.validate(expense);
        Long userId = expense.getUser() == null ? null : expense.getUser().getId();
        riskAssessment = riskAssessmentService.assess(expense, validation, classificationResult, null, null, userId);
        applyDerivedAssessment();
        invoiceEditorForm.setAssessment(classificationResult, riskAssessment);
        invoiceEditorForm.setValidation(validation);
    }

    private boolean hasReviewableContent() {
        return expense.getAmount() != null
                || (expense.getName() != null && !expense.getName().isBlank())
                || (expense.getInvoiceNumber() != null && !expense.getInvoiceNumber().isBlank())
                || (expense.getLineItems() != null && !expense.getLineItems().isEmpty())
                || rawOcrText != null;
    }

    private void applyDerivedAssessment() {
        if (classificationResult != null) {
            expense.setPredictedCategory(classificationResult.getPredictedCategory());
            expense.setClassificationConfidence(classificationResult.getConfidence());
            expense.setClassificationModelVersion(classificationResult.getModelVersion());
            expense.setClassificationAlternativesJson(serializeAlternatives());
        }
        if (riskAssessment != null) {
            expense.setRiskScore(riskAssessment.getRiskScore());
            expense.setRiskBand(riskAssessment.getRiskBand());
            expense.setRiskModelVersion(riskAssessment.getModelVersion());
            expense.setReviewRequired(riskAssessment.isReviewRequired());
            expense.setRiskSignalsJson(new JSONArray(riskAssessment.getSignals() == null ? java.util.List.of() : riskAssessment.getSignals()).toString());
            expense.setRiskReasonSummary(riskAssessment.getReasonSummary());
            expense.setRiskSource(riskAssessment.getSource());
        }
    }

    private String serializeAlternatives() {
        JSONArray array = new JSONArray();
        if (classificationResult.getAlternatives() == null) {
            return array.toString();
        }
        classificationResult.getAlternatives().forEach(alternative -> array.put(new JSONObject()
                .put("label", alternative.getLabel())
                .put("confidence", alternative.getConfidence())));
        return array.toString();
    }

    private String resolveExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "png";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        resetState();
    }
}
