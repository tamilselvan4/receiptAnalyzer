package com.expensetracker.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpenseCategoryCatalogTest {

    private final ExpenseCategoryCatalog catalog = new ExpenseCategoryCatalog();

    @Test
    void exposesStandardCategoriesAndOther() {
        assertTrue(catalog.getAvailableCategories().contains("Meals"));
        assertTrue(catalog.getAvailableCategories().contains(ExpenseCategoryCatalog.OTHER));
        assertTrue(catalog.isStandardCategory("Travel"));
        assertFalse(catalog.isStandardCategory("Custom Expense"));
    }

    @Test
    void resolvesOtherToCustomValue() {
        assertEquals("Conference Fee", catalog.resolveStoredCategory(ExpenseCategoryCatalog.OTHER, "Conference Fee"));
        assertEquals("Meals", catalog.resolveStoredCategory("Meals", "Ignored"));
        assertEquals(ExpenseCategoryCatalog.OTHER, catalog.resolveStoredCategory(ExpenseCategoryCatalog.OTHER, ""));
    }
}
