package com.expensetracker.frontend;

import com.expensetracker.config.ExpenseCategoryCatalog;
import com.expensetracker.model.Expense;
import com.expensetracker.model.ExpenseLineItem;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoiceEditorFormTest {

    private final ExpenseCategoryCatalog catalog = new ExpenseCategoryCatalog();

    @Test
    void loadsCustomCategoryAsOtherAndWritesItBack() throws Exception {
        InvoiceEditorForm form = new InvoiceEditorForm(catalog);
        Expense expense = new Expense();
        expense.setCategory("Conference Fee");

        form.setExpense(expense);

        ComboBox<String> categoryField = comboBox(form, "categoryField");
        TextField customCategoryField = textField(form, "customCategoryField");

        assertEquals(ExpenseCategoryCatalog.OTHER, categoryField.getValue());
        assertEquals("Conference Fee", customCategoryField.getValue());
        assertTrue(customCategoryField.isVisible());

        Expense updated = new Expense();
        form.writeToExpense(updated);
        assertEquals("Conference Fee", updated.getCategory());
    }

    @Test
    void selectingStandardCategoryHidesCustomField() throws Exception {
        InvoiceEditorForm form = new InvoiceEditorForm(catalog);
        ComboBox<String> categoryField = comboBox(form, "categoryField");
        TextField customCategoryField = textField(form, "customCategoryField");

        categoryField.setValue(ExpenseCategoryCatalog.OTHER);
        customCategoryField.setValue("Temporary");
        categoryField.setValue("Meals");

        assertFalse(customCategoryField.isVisible());
        assertEquals("", customCategoryField.getValue());
    }

    @Test
    void showsLineItemTotalSummaryBelowGrid() throws Exception {
        InvoiceEditorForm form = new InvoiceEditorForm(catalog);
        Expense expense = new Expense();
        expense.setCurrency("usd");
        expense.setLineItems(List.of(lineItem("Item A", "12.50"), lineItem("Item B", "7.25")));

        form.setExpense(expense);

        Span lineItemsTotalLabel = span(form, "lineItemsTotalLabel");
        assertEquals("Line Item Total: USD 19.75", lineItemsTotalLabel.getText());
    }

    @Test
    void notifiesChangeListenerWhenTotalsChange() throws Exception {
        InvoiceEditorForm form = new InvoiceEditorForm(catalog);
        AtomicInteger counter = new AtomicInteger();
        form.setChangeListener(counter::incrementAndGet);

        BigDecimalField amountField = bigDecimalField(form, "amountField");
        amountField.setValue(BigDecimal.valueOf(12.50));

        assertEquals(1, counter.get());
    }

    @SuppressWarnings("unchecked")
    private ComboBox<String> comboBox(InvoiceEditorForm form, String fieldName) throws Exception {
        Field field = InvoiceEditorForm.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (ComboBox<String>) field.get(form);
    }

    private TextField textField(InvoiceEditorForm form, String fieldName) throws Exception {
        Field field = InvoiceEditorForm.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (TextField) field.get(form);
    }

    private BigDecimalField bigDecimalField(InvoiceEditorForm form, String fieldName) throws Exception {
        Field field = InvoiceEditorForm.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (BigDecimalField) field.get(form);
    }

    private Span span(InvoiceEditorForm form, String fieldName) throws Exception {
        Field field = InvoiceEditorForm.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Span) field.get(form);
    }

    private ExpenseLineItem lineItem(String description, String totalPrice) {
        ExpenseLineItem item = new ExpenseLineItem();
        item.setDescription(description);
        item.setTotalPrice(new BigDecimal(totalPrice));
        return item;
    }
}
