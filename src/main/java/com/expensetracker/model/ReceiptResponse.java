package com.expensetracker.model;

import com.expensetracker.util.FlexibleDateDeserializer;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.time.LocalDate;

public class ReceiptResponse {

    private String Company;
    private String Address;
    private String Amount;
    @JsonDeserialize(using = FlexibleDateDeserializer.class)
    private LocalDate Date;
    private String Items;

    public String getAddress() {
        return Address;
    }

    public void setAddress(String address) {
        Address = address;
    }

    public String getAmount() {
        return Amount;
    }

    public void setAmount(String amount) {
        Amount = amount;
    }

    public String getCompany() {
        return Company;
    }

    public void setCompany(String company) {
        Company = company;
    }

    public LocalDate getDate() {
        return Date;
    }

    public void setDate(LocalDate date) {
        Date = date;
    }

    public String getItems() {
        return Items;
    }

    public void setItems(String items) {
        Items = items;
    }
}

