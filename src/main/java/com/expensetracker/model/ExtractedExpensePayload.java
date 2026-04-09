package com.expensetracker.model;

import com.expensetracker.util.FlexibleDateDeserializer;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtractedExpensePayload {

    private String name;
    private BigDecimal amount;
    private BigDecimal tax;
    private String currency;
    @JsonDeserialize(using = FlexibleDateDeserializer.class)
    private LocalDate date;
    @JsonProperty("invoice_number")
    private String invoiceNumber;
    @JsonProperty("due_date")
    @JsonDeserialize(using = FlexibleDateDeserializer.class)
    private LocalDate dueDate;
    private String category;
    private String comment;
    @JsonProperty("seller_address")
    private String sellerAddress;
    @JsonProperty("client_name")
    private String clientName;
    @JsonProperty("client_address")
    private String clientAddress;
    private BigDecimal discount;
    @JsonProperty("payment_method")
    private String paymentMethod;
    @JsonProperty("bank_name")
    private String bankName;
    @JsonProperty("account_number")
    private String accountNumber;
    @JsonProperty("line_items")
    private List<ExtractedLineItemPayload> lineItems = new ArrayList<>();
    private Map<String, Double> confidence = new HashMap<>();
    @JsonProperty("raw_ocr_text")
    private String rawOcrText;
    @JsonProperty("extra_fields")
    private Map<String, Object> extraFields = new HashMap<>();
    private ClassificationResult classification;
    @JsonProperty("risk_prior")
    private RiskPriorPayload riskPrior;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getTax() {
        return tax;
    }

    public void setTax(BigDecimal tax) {
        this.tax = tax;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getSellerAddress() {
        return sellerAddress;
    }

    public void setSellerAddress(String sellerAddress) {
        this.sellerAddress = sellerAddress;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getClientAddress() {
        return clientAddress;
    }

    public void setClientAddress(String clientAddress) {
        this.clientAddress = clientAddress;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public void setDiscount(BigDecimal discount) {
        this.discount = discount;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public List<ExtractedLineItemPayload> getLineItems() {
        return lineItems;
    }

    public void setLineItems(List<ExtractedLineItemPayload> lineItems) {
        this.lineItems = lineItems == null ? new ArrayList<>() : new ArrayList<>(lineItems);
    }

    public Map<String, Double> getConfidence() {
        return confidence;
    }

    public void setConfidence(Map<String, Double> confidence) {
        this.confidence = confidence;
    }

    public String getRawOcrText() {
        return rawOcrText;
    }

    public void setRawOcrText(String rawOcrText) {
        this.rawOcrText = rawOcrText;
    }

    public Map<String, Object> getExtraFields() {
        return extraFields;
    }

    public void setExtraFields(Map<String, Object> extraFields) {
        this.extraFields = extraFields;
    }

    public ClassificationResult getClassification() {
        return classification;
    }

    public void setClassification(ClassificationResult classification) {
        this.classification = classification;
    }

    public RiskPriorPayload getRiskPrior() {
        return riskPrior;
    }

    public void setRiskPrior(RiskPriorPayload riskPrior) {
        this.riskPrior = riskPrior;
    }
}
