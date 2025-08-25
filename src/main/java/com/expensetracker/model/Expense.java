package com.expensetracker.model;

import jakarta.persistence.Entity;

import java.time.LocalDate;

import java.math.BigDecimal;
import jakarta.persistence.*;

@Entity
@Table(name = "expenses")
public class Expense {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column()
    private String name;

    @Column(precision = 4, scale = 2)
    private BigDecimal amount;

    @Column(precision = 4, scale = 2)
    private BigDecimal tax;

    @Column()
    private String currency;

    @Column()
    private LocalDate date;

    @Column()
    private String category;

    private String comment;

    @Column(name = "file_name", length = 70)
    private String fileName;

    public Expense() {

    }

    public Expense(User user, String name, BigDecimal amount, BigDecimal tax, String currency, LocalDate date, String category, String comment, String fileName) {
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

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

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

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
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

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
}
