package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "expenses")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column
    private String name;

    @Column(name = "invoice_number", length = 32)
    private String invoiceNumber;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(precision = 19, scale = 4)
    private BigDecimal tax;

    @Column(precision = 19, scale = 4)
    private BigDecimal discount;

    @Column(length = 3)
    private String currency;

    @Column
    private LocalDate date;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column
    private String category;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String comment;

    @Lob
    @Column(name = "seller_address", columnDefinition = "TEXT")
    private String sellerAddress;

    @Column(name = "client_name")
    private String clientName;

    @Lob
    @Column(name = "client_address", columnDefinition = "TEXT")
    private String clientAddress;

    @Column(name = "payment_method", length = 100)
    private String paymentMethod;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "account_number", length = 64)
    private String accountNumber;

    @Column(name = "file_name", length = 70)
    private String fileName;

    @Column(name = "predicted_category")
    private String predictedCategory;

    @Column(name = "classification_confidence", precision = 19, scale = 4)
    private BigDecimal classificationConfidence;

    @Column(name = "classification_model_version", length = 100)
    private String classificationModelVersion;

    @Lob
    @Column(name = "classification_alternatives_json", columnDefinition = "TEXT")
    private String classificationAlternativesJson;

    @Column(name = "risk_score", precision = 19, scale = 4)
    private BigDecimal riskScore;

    @Column(name = "risk_band", length = 16)
    private String riskBand;

    @Column(name = "risk_model_version", length = 100)
    private String riskModelVersion;

    @Column(name = "review_required")
    private Boolean reviewRequired;

    @Lob
    @Column(name = "risk_signals_json", columnDefinition = "TEXT")
    private String riskSignalsJson;

    @Lob
    @Column(name = "risk_reason_summary", columnDefinition = "TEXT")
    private String riskReasonSummary;

    @Column(name = "risk_source", length = 32)
    private String riskSource;

    @JsonManagedReference
    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineIndex ASC")
    private List<ExpenseLineItem> lineItems = new ArrayList<>();

    public Expense() {
    }

    public Expense(User user,
                   String name,
                   BigDecimal amount,
                   BigDecimal tax,
                   String currency,
                   LocalDate date,
                   String category,
                   String comment,
                   String fileName) {
        this.user = user;
        this.name = name;
        this.amount = amount;
        this.tax = tax;
        this.currency = currency;
        this.date = date;
        this.category = category;
        this.comment = comment;
        this.fileName = fileName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
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

    public BigDecimal getDiscount() {
        return discount;
    }

    public void setDiscount(BigDecimal discount) {
        this.discount = discount;
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

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getPredictedCategory() {
        return predictedCategory;
    }

    public void setPredictedCategory(String predictedCategory) {
        this.predictedCategory = predictedCategory;
    }

    public BigDecimal getClassificationConfidence() {
        return classificationConfidence;
    }

    public void setClassificationConfidence(BigDecimal classificationConfidence) {
        this.classificationConfidence = classificationConfidence;
    }

    public String getClassificationModelVersion() {
        return classificationModelVersion;
    }

    public void setClassificationModelVersion(String classificationModelVersion) {
        this.classificationModelVersion = classificationModelVersion;
    }

    public String getClassificationAlternativesJson() {
        return classificationAlternativesJson;
    }

    public void setClassificationAlternativesJson(String classificationAlternativesJson) {
        this.classificationAlternativesJson = classificationAlternativesJson;
    }

    public BigDecimal getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(BigDecimal riskScore) {
        this.riskScore = riskScore;
    }

    public String getRiskBand() {
        return riskBand;
    }

    public void setRiskBand(String riskBand) {
        this.riskBand = riskBand;
    }

    public String getRiskModelVersion() {
        return riskModelVersion;
    }

    public void setRiskModelVersion(String riskModelVersion) {
        this.riskModelVersion = riskModelVersion;
    }

    public Boolean getReviewRequired() {
        return reviewRequired;
    }

    public void setReviewRequired(Boolean reviewRequired) {
        this.reviewRequired = reviewRequired;
    }

    public String getRiskSignalsJson() {
        return riskSignalsJson;
    }

    public void setRiskSignalsJson(String riskSignalsJson) {
        this.riskSignalsJson = riskSignalsJson;
    }

    public String getRiskReasonSummary() {
        return riskReasonSummary;
    }

    public void setRiskReasonSummary(String riskReasonSummary) {
        this.riskReasonSummary = riskReasonSummary;
    }

    public String getRiskSource() {
        return riskSource;
    }

    public void setRiskSource(String riskSource) {
        this.riskSource = riskSource;
    }

    public List<ExpenseLineItem> getLineItems() {
        return lineItems;
    }

    public void setLineItems(List<ExpenseLineItem> lineItems) {
        List<ExpenseLineItem> snapshot = lineItems == null ? List.of() : new ArrayList<>(lineItems);
        this.lineItems.clear();
        snapshot.forEach(this::addLineItem);
    }

    public void addLineItem(ExpenseLineItem lineItem) {
        if (lineItem == null) {
            return;
        }
        lineItem.setExpense(this);
        if (lineItem.getLineIndex() == null) {
            lineItem.setLineIndex(this.lineItems.size());
        }
        this.lineItems.add(lineItem);
    }

    public void removeLineItem(ExpenseLineItem lineItem) {
        if (lineItem == null) {
            return;
        }
        this.lineItems.remove(lineItem);
        lineItem.setExpense(null);
    }
}
