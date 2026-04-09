package com.expensetracker.frontend;

import com.expensetracker.config.ExpenseCategoryCatalog;
import com.expensetracker.model.Expense;
import com.expensetracker.model.User;
import com.expensetracker.service.ExpenseService;
import com.expensetracker.service.UserService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasText;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HomeViewTest {

    private final ExpenseCategoryCatalog catalog = new ExpenseCategoryCatalog();

    @Test
    void profileTabShowsUserIdentityAndMetrics() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setName("Jane Doe");
        user.setEmail("jane@example.com");

        List<Expense> expenses = List.of(
                expense(11L, "Flight Desk", BigDecimal.valueOf(120.50), LocalDate.now(), "Travel", null, false),
                expense(8L, "Cafe Central", BigDecimal.valueOf(49.50), YearMonth.now().minusMonths(1).atDay(18), "Meals", null, true)
        );

        VerticalLayout profile = renderProfile(expenses, Optional.of(user));
        String text = extractText(profile);

        assertTrue(text.contains("Jane Doe"));
        assertTrue(text.contains("jane@example.com"));
        assertTrue(text.contains("170.00"));
        assertTrue(text.contains("120.50"));
        assertTrue(text.contains("Expenses Logged"));
        assertTrue(text.contains("2"));
        assertTrue(text.contains("Needs Review"));
        assertTrue(text.contains("1"));
    }

    @Test
    void profileTabShowsFallbackWhenUserMissing() throws Exception {
        VerticalLayout profile = renderProfile(List.of(), Optional.empty());
        String text = extractText(profile);

        assertTrue(text.contains("Demo User"));
        assertTrue(text.contains("No email configured"));
        assertTrue(text.contains("No categories yet"));
        assertTrue(text.contains("No expenses uploaded yet"));
        assertTrue(text.contains("0.00"));
    }

    @Test
    void profileTabLimitsRecentActivityAndOrdersNewestFirst() throws Exception {
        List<Expense> expenses = List.of(
                expense(3L, "Same Day Lower", BigDecimal.valueOf(19), LocalDate.of(2025, 4, 8), "Travel", null, false),
                expense(7L, "Same Day Higher", BigDecimal.valueOf(25), LocalDate.of(2025, 4, 8), "Travel", null, false),
                expense(6L, "Second Newest", BigDecimal.valueOf(45), LocalDate.of(2025, 4, 7), "Meals", null, false),
                expense(5L, "Third Newest", BigDecimal.valueOf(50), LocalDate.of(2025, 4, 6), "Software", null, false),
                expense(4L, "Fourth Newest", BigDecimal.valueOf(15), LocalDate.of(2025, 4, 5), "Travel", null, false),
                expense(1L, "Oldest Seller", BigDecimal.valueOf(10), LocalDate.of(2025, 4, 4), "Office", null, false)
        );

        VerticalLayout profile = renderProfile(expenses, Optional.of(demoUser()));
        List<Component> rows = findComponentsWithClass(profile, "profile-recent-activity-row");

        assertEquals(5, rows.size());
        assertTrue(extractText(rows.get(0)).contains("Same Day Higher"));
        assertTrue(extractText(rows.get(1)).contains("Same Day Lower"));
        assertFalse(extractText(profile).contains("Oldest Seller"));
    }

    @Test
    void profileTabBuildsCategoryBreakdownFromCategoryOrPrediction() throws Exception {
        List<Expense> expenses = List.of(
                expense(1L, "Model Filled", BigDecimal.valueOf(30), LocalDate.now(), null, "AI Travel", false),
                expense(2L, "No Labels", BigDecimal.valueOf(12), LocalDate.now().minusDays(1), null, null, false),
                expense(3L, "Meals Vendor", BigDecimal.valueOf(8), LocalDate.now().minusDays(2), "Meals", "Ignored Prediction", false)
        );

        VerticalLayout profile = renderProfile(expenses, Optional.of(demoUser()));
        String text = extractText(profile);

        assertTrue(text.contains("AI Travel"));
        assertTrue(text.contains("Uncategorized"));
        assertTrue(text.contains("Meals"));
    }

    @Test
    void afterNavigationRefreshesHomeTabWithLatestExpenses() {
        ExpenseService expenseService = mock(ExpenseService.class);
        UserService userService = mock(UserService.class);

        List<Expense> firstLoad = List.of(
                expense(1L, "Initial Seller", BigDecimal.valueOf(10), LocalDate.now(), "Travel", null, false)
        );
        List<Expense> refreshedLoad = List.of(
                expense(1L, "Initial Seller", BigDecimal.valueOf(10), LocalDate.now(), "Travel", null, false),
                expense(2L, "Fresh Seller", BigDecimal.valueOf(22), LocalDate.now(), "Meals", null, false)
        );

        when(expenseService.getExpenseByUserId(anyLong())).thenReturn(firstLoad, refreshedLoad, refreshedLoad);
        when(userService.findUser(1L)).thenReturn(Optional.of(demoUser()));

        HomeView view = new HomeView(expenseService, catalog, userService);
        assertTrue(extractText(view).contains("Current Month Expense: 10.00"));

        view.afterNavigation(null);

        String text = extractText(view);
        assertTrue(text.contains("Current Month Expense: 32.00"));
    }

    private VerticalLayout renderProfile(List<Expense> expenses, Optional<User> user) throws Exception {
        ExpenseService expenseService = mock(ExpenseService.class);
        UserService userService = mock(UserService.class);

        when(expenseService.getExpenseByUserId(1L)).thenReturn(expenses);
        when(userService.findUser(1L)).thenReturn(user);

        HomeView view = new HomeView(expenseService, catalog, userService);
        Method method = HomeView.class.getDeclaredMethod("getProfileTabContent");
        method.setAccessible(true);
        return (VerticalLayout) method.invoke(view);
    }

    private User demoUser() {
        User user = new User();
        user.setId(1L);
        user.setName("Demo User");
        user.setEmail("demo@example.com");
        return user;
    }

    private Expense expense(Long id,
                            String seller,
                            BigDecimal amount,
                            LocalDate date,
                            String category,
                            String predictedCategory,
                            boolean reviewRequired) {
        Expense expense = new Expense();
        expense.setId(id);
        expense.setName(seller);
        expense.setAmount(amount);
        expense.setDate(date);
        expense.setCategory(category);
        expense.setPredictedCategory(predictedCategory);
        expense.setReviewRequired(reviewRequired);
        return expense;
    }

    private String extractText(Component component) {
        List<String> texts = new ArrayList<>();
        collectText(component, texts);
        return String.join("\n", texts);
    }

    private void collectText(Component component, List<String> texts) {
        if (component instanceof HasText hasText) {
            String text = hasText.getText();
            if (text != null && !text.isBlank()) {
                texts.add(text);
            }
        }
        component.getChildren().forEach(child -> collectText(child, texts));
    }

    private List<Component> findComponentsWithClass(Component root, String className) {
        List<Component> matches = new ArrayList<>();
        collectComponentsWithClass(root, className, matches);
        return matches;
    }

    private void collectComponentsWithClass(Component component, String className, List<Component> matches) {
        if (component.getClassNames().contains(className)) {
            matches.add(component);
        }
        component.getChildren().forEach(child -> collectComponentsWithClass(child, className, matches));
    }
}
