package com.expensetracker.frontend;

import com.expensetracker.config.ExpenseCategoryCatalog;
import com.expensetracker.model.ClassificationAlternative;
import com.expensetracker.model.ClassificationResult;
import com.expensetracker.model.Expense;
import com.expensetracker.model.ExpenseLineItem;
import com.expensetracker.model.RiskAssessment;
import com.expensetracker.model.Validation;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.data.provider.ListDataProvider;
import org.json.JSONArray;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class InvoiceEditorForm extends VerticalLayout {

    private final TextField sellerNameField = new TextField("Seller");
    private final TextField invoiceNumberField = new TextField("Invoice Number");
    private final DatePicker invoiceDateField = new DatePicker("Invoice Date");
    private final DatePicker dueDateField = new DatePicker("Due Date");
    private final ComboBox<String> categoryField = new ComboBox<>("Category");
    private final TextField customCategoryField = new TextField("Custom Category");

    private final BigDecimalField amountField = new BigDecimalField("Amount");
    private final BigDecimalField taxField = new BigDecimalField("Tax");
    private final BigDecimalField discountField = new BigDecimalField("Discount");
    private final TextField currencyField = new TextField("Currency");

    private final TextArea sellerAddressField = new TextArea("Seller Address");
    private final TextField clientNameField = new TextField("Client Name");
    private final TextArea clientAddressField = new TextArea("Client Address");

    private final TextField paymentMethodField = new TextField("Payment Method");
    private final TextField bankNameField = new TextField("Bank Name");
    private final TextField accountNumberField = new TextField("Account Number");

    private final TextArea commentField = new TextArea("Notes");

    private final Span validationBadge = new Span("Validation Pending");
    private final Paragraph validationReason = new Paragraph("Upload an invoice to see validation output.");
    private final Span classificationBadge = new Span("Model unavailable");
    private final Span classificationConfidence = new Span("Confidence: -");
    private final Span classificationAlternatives = new Span("Alternatives: -");
    private final Span riskBadge = new Span("LOW");
    private final Span reviewRecommendation = new Span("Rules-only assessment used");
    private final Span modelSummary = new Span("Model: -");
    private final ProgressBar riskScoreBar = new ProgressBar(0.0, 1.0, 0.0);
    private final VerticalLayout signalList = new VerticalLayout();
    private final Span lineItemsTotalLabel = new Span("Line Item Total: INR 0.00");

    private final List<ExpenseLineItem> editableLineItems = new ArrayList<>();
    private final ListDataProvider<ExpenseLineItem> lineItemProvider = new ListDataProvider<>(editableLineItems);
    private final Grid<ExpenseLineItem> lineItemsGrid = new Grid<>(ExpenseLineItem.class, false);
    private final ExpenseCategoryCatalog expenseCategoryCatalog;
    private Runnable changeListener = () -> {};
    private boolean suppressChangeEvents;

    public InvoiceEditorForm(ExpenseCategoryCatalog expenseCategoryCatalog) {
        this.expenseCategoryCatalog = expenseCategoryCatalog;
        setWidthFull();
        setPadding(false);
        setSpacing(true);

        configureFields();
        configureLineItemsGrid();

        add(
                createCard("Summary", createSummaryLayout()),
                createCard("Totals", createTotalsLayout()),
                createCard("Parties", createPartiesLayout()),
                createCard("Payment", createPaymentLayout()),
                createCard("Notes", commentField),
                createLineItemsCard(),
                createAiReviewCard(),
                createValidationCard()
        );
    }

    public void setExpense(Expense expense) {
        Expense value = expense == null ? new Expense() : expense;
        suppressChangeEvents = true;

        sellerNameField.setValue(orEmpty(value.getName()));
        invoiceNumberField.setValue(orEmpty(value.getInvoiceNumber()));
        invoiceDateField.setValue(value.getDate());
        dueDateField.setValue(value.getDueDate());
        applyStoredCategory(value.getCategory());

        amountField.setValue(value.getAmount());
        taxField.setValue(value.getTax());
        discountField.setValue(value.getDiscount());
        currencyField.setValue(orEmpty(value.getCurrency()));

        sellerAddressField.setValue(orEmpty(value.getSellerAddress()));
        clientNameField.setValue(orEmpty(value.getClientName()));
        clientAddressField.setValue(orEmpty(value.getClientAddress()));

        paymentMethodField.setValue(orEmpty(value.getPaymentMethod()));
        bankNameField.setValue(orEmpty(value.getBankName()));
        accountNumberField.setValue(orEmpty(value.getAccountNumber()));
        commentField.setValue(orEmpty(value.getComment()));

        editableLineItems.clear();
        if (value.getLineItems() != null) {
            for (ExpenseLineItem item : value.getLineItems()) {
                editableLineItems.add(copyLineItem(item));
            }
        }
        lineItemProvider.refreshAll();
        updateLineItemsSummary();
        suppressChangeEvents = false;
        setAssessment(fromExpenseClassification(value), fromExpenseRisk(value));
    }

    public void setChangeListener(Runnable changeListener) {
        this.changeListener = changeListener == null ? () -> {} : changeListener;
    }

    public void writeToExpense(Expense expense) {
        expense.setName(blankToNull(sellerNameField.getValue()));
        expense.setInvoiceNumber(blankToNull(invoiceNumberField.getValue()));
        expense.setDate(invoiceDateField.getValue());
        expense.setDueDate(dueDateField.getValue());
        expense.setCategory(expenseCategoryCatalog.resolveStoredCategory(categoryField.getValue(), customCategoryField.getValue()));

        expense.setAmount(amountField.getValue());
        expense.setTax(taxField.getValue());
        expense.setDiscount(discountField.getValue());
        expense.setCurrency(normalizeCurrency(currencyField.getValue()));

        expense.setSellerAddress(blankToNull(sellerAddressField.getValue()));
        expense.setClientName(blankToNull(clientNameField.getValue()));
        expense.setClientAddress(blankToNull(clientAddressField.getValue()));

        expense.setPaymentMethod(blankToNull(paymentMethodField.getValue()));
        expense.setBankName(blankToNull(bankNameField.getValue()));
        expense.setAccountNumber(blankToNull(accountNumberField.getValue()));
        expense.setComment(blankToNull(commentField.getValue()));

        List<ExpenseLineItem> normalizedLineItems = new ArrayList<>();
        for (int index = 0; index < editableLineItems.size(); index++) {
            ExpenseLineItem lineItem = editableLineItems.get(index);
            if (lineItem == null || blankToNull(lineItem.getDescription()) == null) {
                continue;
            }
            ExpenseLineItem copy = copyLineItem(lineItem);
            copy.setLineIndex(index);
            normalizedLineItems.add(copy);
        }
        expense.setLineItems(normalizedLineItems);
    }

    public void setValidation(Validation validation) {
        if (validation == null) {
            validationBadge.setText("Validation Pending");
            validationBadge.getStyle()
                    .set("background", "#eef2ff")
                    .set("color", "#1d4ed8");
            validationReason.setText("Upload an invoice to see validation output.");
            return;
        }

        boolean anomaly = validation.isAnomaly();
        validationBadge.setText(anomaly ? "Needs Review" : "Validated");
        validationBadge.getStyle()
                .set("background", anomaly ? "#fee2e2" : "#dcfce7")
                .set("color", anomaly ? "#b91c1c" : "#166534");
        validationReason.setText(orEmpty(validation.getReason()));
    }

    public void setAssessment(ClassificationResult classificationResult, RiskAssessment riskAssessment) {
        applyClassification(classificationResult);
        applyRiskAssessment(riskAssessment);
    }

    private void configureFields() {
        sellerNameField.setWidthFull();
        invoiceNumberField.setWidthFull();
        invoiceDateField.setWidthFull();
        dueDateField.setWidthFull();
        categoryField.setWidthFull();
        categoryField.setItems(expenseCategoryCatalog.getAvailableCategories());
        categoryField.setAllowCustomValue(false);
        categoryField.setClearButtonVisible(true);
        customCategoryField.setWidthFull();
        customCategoryField.setVisible(false);
        customCategoryField.setPlaceholder("Enter custom category");
        categoryField.addValueChangeListener(event -> {
            toggleCustomCategoryField(event.getValue());
            notifyChange();
        });
        customCategoryField.addValueChangeListener(event -> notifyChange());
        sellerNameField.addValueChangeListener(event -> notifyChange());
        invoiceNumberField.addValueChangeListener(event -> notifyChange());
        invoiceDateField.addValueChangeListener(event -> notifyChange());
        dueDateField.addValueChangeListener(event -> notifyChange());
        amountField.setWidthFull();
        amountField.addValueChangeListener(event -> notifyChange());
        taxField.setWidthFull();
        taxField.addValueChangeListener(event -> notifyChange());
        discountField.setWidthFull();
        discountField.addValueChangeListener(event -> notifyChange());
        currencyField.setWidthFull();
        currencyField.setMaxLength(3);
        currencyField.addValueChangeListener(event -> {
            updateLineItemsSummary();
            notifyChange();
        });
        sellerAddressField.setWidthFull();
        sellerAddressField.setMinHeight("120px");
        clientNameField.setWidthFull();
        clientAddressField.setWidthFull();
        clientAddressField.setMinHeight("120px");
        paymentMethodField.setWidthFull();
        bankNameField.setWidthFull();
        accountNumberField.setWidthFull();
        commentField.setWidthFull();
        commentField.setMinHeight("120px");
        commentField.addValueChangeListener(event -> notifyChange());

        validationBadge.getStyle()
                .set("fontWeight", "700")
                .set("padding", "6px 12px")
                .set("borderRadius", "999px")
                .set("display", "inline-block")
                .set("background", "#eef2ff")
                .set("color", "#1d4ed8");
        validationReason.getStyle()
                .set("margin", "0")
                .set("color", "#475569");
        classificationBadge.getStyle()
                .set("fontWeight", "700")
                .set("padding", "6px 12px")
                .set("borderRadius", "999px")
                .set("display", "inline-block")
                .set("background", "#e0f2fe")
                .set("color", "#0369a1");
        riskBadge.getStyle()
                .set("fontWeight", "700")
                .set("padding", "6px 12px")
                .set("borderRadius", "999px")
                .set("display", "inline-block");
        reviewRecommendation.getStyle().set("fontWeight", "600").set("color", "#475569");
        classificationConfidence.getStyle().set("color", "#475569");
        classificationAlternatives.getStyle().set("color", "#64748b");
        modelSummary.getStyle().set("color", "#64748b");
        signalList.setPadding(false);
        signalList.setSpacing(false);
        signalList.setAlignItems(FlexComponent.Alignment.START);
        riskScoreBar.setWidthFull();
        lineItemsTotalLabel.getStyle()
                .set("marginTop", "6px")
                .set("fontWeight", "700")
                .set("color", "#0f172a")
                .set("display", "inline-block")
                .set("background", "#f8fafc")
                .set("border", "1px solid #e2e8f0")
                .set("borderRadius", "999px")
                .set("padding", "8px 12px");
    }

    private void configureLineItemsGrid() {
        lineItemsGrid.setDataProvider(lineItemProvider);
        lineItemsGrid.setAllRowsVisible(true);
        lineItemsGrid.setWidthFull();
        lineItemsGrid.addColumn(item -> resolveDisplayIndex(item) + 1)
                .setHeader("#")
                .setFlexGrow(0)
                .setWidth("70px");
        lineItemsGrid.addComponentColumn(item -> buildLineItemDescriptionField(item))
                .setHeader("Description")
                .setAutoWidth(true)
                .setFlexGrow(1);
        lineItemsGrid.addComponentColumn(item -> buildLineItemQuantityField(item))
                .setHeader("Quantity")
                .setAutoWidth(true)
                .setFlexGrow(0);
        lineItemsGrid.addComponentColumn(item -> buildLineItemTotalField(item))
                .setHeader("Total Price")
                .setAutoWidth(true)
                .setFlexGrow(0);
        lineItemsGrid.addComponentColumn(item -> buildDeleteLineItemButton(item))
                .setHeader("")
                .setFlexGrow(0)
                .setWidth("90px");
    }

    private Component createSummaryLayout() {
        FormLayout formLayout = createTwoColumnForm();
        formLayout.add(sellerNameField, invoiceNumberField, invoiceDateField, dueDateField, categoryField, customCategoryField);
        formLayout.setColspan(customCategoryField, 2);
        return formLayout;
    }

    private Component createTotalsLayout() {
        FormLayout formLayout = createTwoColumnForm();
        formLayout.add(amountField, taxField, discountField, currencyField);
        return formLayout;
    }

    private Component createPartiesLayout() {
        FormLayout formLayout = createTwoColumnForm();
        formLayout.add(sellerAddressField, clientNameField, clientAddressField);
        formLayout.setColspan(sellerAddressField, 2);
        formLayout.setColspan(clientAddressField, 2);
        return formLayout;
    }

    private Component createPaymentLayout() {
        FormLayout formLayout = createTwoColumnForm();
        formLayout.add(paymentMethodField, bankNameField, accountNumberField);
        formLayout.setColspan(accountNumberField, 2);
        return formLayout;
    }

    private Component createLineItemsCard() {
        Button addLineItemButton = new Button("Add Line Item", new Icon(VaadinIcon.PLUS));
        addLineItemButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addLineItemButton.addClickListener(event -> {
            ExpenseLineItem item = new ExpenseLineItem();
            item.setLineIndex(editableLineItems.size());
            item.setQuantity(BigDecimal.ONE);
            editableLineItems.add(item);
            lineItemProvider.refreshAll();
            updateLineItemsSummary();
            notifyChange();
        });

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);
        content.add(addLineItemButton, lineItemsGrid, lineItemsTotalLabel);
        return createCard("Line Items", content);
    }

    private Component createAiReviewCard() {
        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);

        HorizontalLayout topRow = new HorizontalLayout(classificationBadge, riskBadge);
        topRow.setSpacing(true);
        topRow.setAlignItems(FlexComponent.Alignment.CENTER);

        content.add(
                topRow,
                classificationConfidence,
                classificationAlternatives,
                reviewRecommendation,
                modelSummary,
                riskScoreBar,
                signalList
        );
        return createCard("AI Review", content);
    }

    private Component createValidationCard() {
        VerticalLayout content = new VerticalLayout(validationBadge, validationReason);
        content.setPadding(false);
        content.setSpacing(false);
        content.setAlignItems(FlexComponent.Alignment.START);
        return createCard("Validation", content);
    }

    private Div createCard(String title, Component content) {
        Div card = new Div();
        card.getStyle()
                .set("background", "white")
                .set("borderRadius", "18px")
                .set("boxShadow", "0 14px 40px rgba(15, 23, 42, 0.08)")
                .set("padding", "20px 22px")
                .set("width", "100%");

        Span titleLabel = new Span(title);
        titleLabel.getStyle()
                .set("display", "block")
                .set("fontSize", "0.95rem")
                .set("fontWeight", "700")
                .set("letterSpacing", "0.02em")
                .set("color", "#0f172a")
                .set("marginBottom", "16px");

        card.add(titleLabel, content);
        return card;
    }

    private FormLayout createTwoColumnForm() {
        FormLayout formLayout = new FormLayout();
        formLayout.setWidthFull();
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("780px", 2)
        );
        return formLayout;
    }

    private Component buildLineItemDescriptionField(ExpenseLineItem item) {
        TextField field = new TextField();
        field.setWidthFull();
        field.setPlaceholder("Description");
        field.setValue(orEmpty(item.getDescription()));
        field.addValueChangeListener(event -> {
            item.setDescription(blankToNull(event.getValue()));
            notifyChange();
        });
        return field;
    }

    private Component buildLineItemQuantityField(ExpenseLineItem item) {
        BigDecimalField field = new BigDecimalField();
        field.setWidthFull();
        field.setValue(item.getQuantity());
        field.addValueChangeListener(event -> {
            item.setQuantity(event.getValue());
            updateLineItemsSummary();
            notifyChange();
        });
        return field;
    }

    private Component buildLineItemTotalField(ExpenseLineItem item) {
        BigDecimalField field = new BigDecimalField();
        field.setWidthFull();
        field.setValue(item.getTotalPrice());
        field.addValueChangeListener(event -> {
            item.setTotalPrice(event.getValue());
            updateLineItemsSummary();
            notifyChange();
        });
        return field;
    }

    private Component buildDeleteLineItemButton(ExpenseLineItem item) {
        Button button = new Button(new Icon(VaadinIcon.TRASH));
        button.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        button.addClickListener(event -> {
            editableLineItems.remove(item);
            normalizeLineIndexes();
            lineItemProvider.refreshAll();
            updateLineItemsSummary();
            notifyChange();
        });
        return new HorizontalLayout(button);
    }

    private ExpenseLineItem copyLineItem(ExpenseLineItem source) {
        ExpenseLineItem copy = new ExpenseLineItem();
        copy.setId(source.getId());
        copy.setLineIndex(source.getLineIndex());
        copy.setDescription(source.getDescription());
        copy.setQuantity(source.getQuantity());
        copy.setTotalPrice(source.getTotalPrice());
        return copy;
    }

    private void normalizeLineIndexes() {
        for (int index = 0; index < editableLineItems.size(); index++) {
            editableLineItems.get(index).setLineIndex(index);
        }
    }

    private int resolveDisplayIndex(ExpenseLineItem item) {
        Integer lineIndex = item.getLineIndex();
        return lineIndex == null ? editableLineItems.indexOf(item) : lineIndex;
    }

    private String normalizeCurrency(String value) {
        String trimmed = blankToNull(value);
        return trimmed == null ? "INR" : trimmed.toUpperCase();
    }

    private void applyClassification(ClassificationResult classificationResult) {
        if (classificationResult == null || blankToNull(classificationResult.getPredictedCategory()) == null) {
            classificationBadge.setText("Model unavailable");
            classificationConfidence.setText("Confidence: -");
            classificationAlternatives.setText("Alternatives: -");
            return;
        }

        classificationBadge.setText("Suggested Category: " + classificationResult.getPredictedCategory());
        classificationConfidence.setText("Confidence: " + formatPercentage(classificationResult.getConfidence()));
        classificationAlternatives.setText("Alternatives: " + summarizeAlternatives(classificationResult.getAlternatives()));
    }

    private void applyRiskAssessment(RiskAssessment riskAssessment) {
        signalList.removeAll();
        if (riskAssessment == null) {
            riskBadge.setText("LOW");
            riskBadge.getStyle().set("background", "#dcfce7").set("color", "#166534");
            reviewRecommendation.setText("Rules-only assessment used");
            modelSummary.setText("Model: -");
            riskScoreBar.setValue(0.0);
            signalList.add(buildSignal("No AI risk signals available yet."));
            return;
        }

        String band = orEmpty(riskAssessment.getRiskBand());
        riskBadge.setText("Risk: " + band);
        if ("HIGH".equalsIgnoreCase(band)) {
            riskBadge.getStyle().set("background", "#fee2e2").set("color", "#b91c1c");
        } else if ("MEDIUM".equalsIgnoreCase(band)) {
            riskBadge.getStyle().set("background", "#fef3c7").set("color", "#b45309");
        } else {
            riskBadge.getStyle().set("background", "#dcfce7").set("color", "#166534");
        }

        reviewRecommendation.setText(riskAssessment.isReviewRequired()
                ? "Manual review recommended"
                : "Low-risk automated assessment");
        modelSummary.setText("Model: " + orEmpty(riskAssessment.getModelVersion()) + " | Source: " + orEmpty(riskAssessment.getSource()));
        riskScoreBar.setValue(riskAssessment.getRiskScore() == null ? 0.0 : riskAssessment.getRiskScore().doubleValue());

        List<String> signals = riskAssessment.getSignals();
        if (signals == null || signals.isEmpty()) {
            signalList.add(buildSignal(orEmpty(riskAssessment.getReasonSummary())));
            return;
        }
        signals.stream().limit(3).forEach(signal -> signalList.add(buildSignal(signal)));
    }

    private Span buildSignal(String signal) {
        Span signalBadge = new Span(orEmpty(signal));
        signalBadge.getStyle()
                .set("background", "#f8fafc")
                .set("border", "1px solid #e2e8f0")
                .set("borderRadius", "999px")
                .set("padding", "6px 10px")
                .set("color", "#334155")
                .set("marginTop", "6px");
        return signalBadge;
    }

    private String summarizeAlternatives(List<ClassificationAlternative> alternatives) {
        if (alternatives == null || alternatives.isEmpty()) {
            return "-";
        }
        List<String> labels = new ArrayList<>();
        for (ClassificationAlternative alternative : alternatives) {
            if (alternative == null || blankToNull(alternative.getLabel()) == null) {
                continue;
            }
            labels.add(alternative.getLabel() + " (" + formatPercentage(alternative.getConfidence()) + ")");
            if (labels.size() == 3) {
                break;
            }
        }
        return labels.isEmpty() ? "-" : String.join(", ", labels);
    }

    private String formatPercentage(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP) + "%";
    }

    private ClassificationResult fromExpenseClassification(Expense expense) {
        if (expense == null || blankToNull(expense.getPredictedCategory()) == null) {
            return null;
        }
        ClassificationResult result = new ClassificationResult();
        result.setPredictedCategory(expense.getPredictedCategory());
        result.setConfidence(expense.getClassificationConfidence());
        result.setModelVersion(expense.getClassificationModelVersion());
        result.setAlternatives(parseAlternatives(expense.getClassificationAlternativesJson()));
        result.setSource("persisted");
        return result;
    }

    private RiskAssessment fromExpenseRisk(Expense expense) {
        if (expense == null || expense.getRiskScore() == null) {
            return null;
        }
        RiskAssessment assessment = new RiskAssessment();
        assessment.setRiskScore(expense.getRiskScore());
        assessment.setRiskBand(expense.getRiskBand());
        assessment.setReviewRequired(Boolean.TRUE.equals(expense.getReviewRequired()));
        assessment.setSignals(parseSignals(expense.getRiskSignalsJson()));
        assessment.setReasonSummary(expense.getRiskReasonSummary());
        assessment.setModelVersion(expense.getRiskModelVersion());
        assessment.setSource(expense.getRiskSource());
        return assessment;
    }

    private List<String> parseSignals(String json) {
        List<String> signals = new ArrayList<>();
        if (blankToNull(json) == null) {
            return signals;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int index = 0; index < array.length(); index++) {
                signals.add(array.optString(index));
            }
        } catch (Exception ignored) {
            signals.add(json);
        }
        return signals;
    }

    private List<ClassificationAlternative> parseAlternatives(String json) {
        List<ClassificationAlternative> alternatives = new ArrayList<>();
        if (blankToNull(json) == null) {
            return alternatives;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int index = 0; index < array.length(); index++) {
                String label = array.getJSONObject(index).optString("label", null);
                BigDecimal confidence = array.getJSONObject(index).has("confidence")
                        ? BigDecimal.valueOf(array.getJSONObject(index).optDouble("confidence"))
                        : null;
                alternatives.add(new ClassificationAlternative(label, confidence));
            }
        } catch (Exception ignored) {
            return alternatives;
        }
        return alternatives;
    }

    private void applyStoredCategory(String category) {
        String normalized = blankToNull(category);
        if (normalized == null) {
            categoryField.clear();
            customCategoryField.clear();
            customCategoryField.setVisible(false);
            return;
        }
        if (expenseCategoryCatalog.isStandardCategory(normalized)) {
            categoryField.setValue(normalized);
            customCategoryField.clear();
            customCategoryField.setVisible(false);
            return;
        }
        categoryField.setValue(ExpenseCategoryCatalog.OTHER);
        customCategoryField.setValue(normalized);
        customCategoryField.setVisible(true);
    }

    private void toggleCustomCategoryField(String selectedCategory) {
        boolean custom = expenseCategoryCatalog.isOtherCategory(selectedCategory);
        customCategoryField.setVisible(custom);
        if (!custom) {
            customCategoryField.clear();
        }
    }

    private void notifyChange() {
        if (suppressChangeEvents) {
            return;
        }
        changeListener.run();
    }

    private void updateLineItemsSummary() {
        BigDecimal total = BigDecimal.ZERO;
        for (ExpenseLineItem item : editableLineItems) {
            if (item == null || item.getTotalPrice() == null) {
                continue;
            }
            total = total.add(item.getTotalPrice());
        }
        lineItemsTotalLabel.setText("Line Item Total: " + normalizeCurrency(currencyField.getValue()) + " "
                + total.setScale(2, RoundingMode.HALF_UP));
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
