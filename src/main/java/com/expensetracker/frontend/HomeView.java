package com.expensetracker.frontend;

import com.expensetracker.config.ExpenseCategoryCatalog;
import com.expensetracker.model.Expense;
import com.expensetracker.model.User;
import com.expensetracker.service.ExpenseService;
import com.expensetracker.service.UserService;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.annotation.UIScope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Route("")
@UIScope
@Component
public class HomeView extends AppLayout implements AfterNavigationObserver {

    private static final long DEFAULT_USER_ID = 1L;
    private static final DecimalFormat AMOUNT_FORMAT = new DecimalFormat("0.00");

    private final ExpenseService expenseService;
    private final ExpenseCategoryCatalog expenseCategoryCatalog;
    private final UserService userService;

    private YearMonth calendarMonth = YearMonth.now();
    private final List<Expense> homeExpenses = new ArrayList<>();
    private ListDataProvider<Expense> homeDataProvider;
    private H1 totalExpenseLabel;
    private H1 calendarMonthTitle;
    private Span calendarMonthTotal;
    private Div calendarGridContainer;
    private final Tab homeTab;
    private final Tab calendarTab;
    private final Tab reportTab;
    private final Tab profileTab;
    private final Tabs tabs;
    private final VerticalLayout contentArea;

    public HomeView(ExpenseService expenseService,
                    ExpenseCategoryCatalog expenseCategoryCatalog,
                    UserService userService) {
        this.expenseService = expenseService;
        this.expenseCategoryCatalog = expenseCategoryCatalog;
        this.userService = userService;

        homeTab = new Tab(new Icon(VaadinIcon.HOME), new Paragraph("Home"));
        calendarTab = new Tab(new Icon(VaadinIcon.CALENDAR), new Paragraph("Calendar"));
        reportTab = new Tab(new Icon(VaadinIcon.CHART), new Paragraph("Report"));
        profileTab = new Tab(new Icon(VaadinIcon.USER), new Paragraph("Profile"));
        tabs = new Tabs(homeTab, calendarTab, reportTab, profileTab);
        tabs.setOrientation(Tabs.Orientation.VERTICAL);
        tabs.getStyle()
                .set("background", "#f8fafc")
                .set("padding", "1rem")
                .set("minWidth", "160px");

        H1 title = new H1("Receipt Analyzer");
        title.getStyle().set("margin", "0").set("color", "#0f172a");
        Button addExpenseButton = new Button("Add Expense", new Icon(VaadinIcon.PLUS), event -> getUI().ifPresent(ui -> ui.navigate("/add")));
        addExpenseButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addExpenseButton.getStyle().set("background", "#0f766e").set("color", "white");

        HorizontalLayout topBar = new HorizontalLayout(title, addExpenseButton);
        topBar.setWidthFull();
        topBar.setHeight("72px");
        topBar.setAlignItems(FlexComponent.Alignment.CENTER);
        topBar.setJustifyContentMode(HorizontalLayout.JustifyContentMode.BETWEEN);
        topBar.setPadding(true);
        topBar.getStyle()
                .set("background", "linear-gradient(90deg, #dbeafe 0%, #e0f2fe 100%)")
                .set("borderBottom", "1px solid #bfdbfe");

        contentArea = new VerticalLayout();
        contentArea.setSizeFull();
        contentArea.setPadding(true);
        contentArea.setSpacing(true);
        contentArea.getStyle().set("background", "#f8fbff");
        refreshSelectedTabContent();

        tabs.addSelectedChangeListener(event -> {
            refreshSelectedTabContent();
        });

        HorizontalLayout mainLayout = new HorizontalLayout(tabs, contentArea);
        mainLayout.setSizeFull();
        mainLayout.expand(contentArea);

        VerticalLayout root = new VerticalLayout(topBar, mainLayout);
        root.setSizeFull();
        root.setPadding(false);
        root.setSpacing(false);
        setContent(root);
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        refreshSelectedTabContent();
    }

    private VerticalLayout getHomeTabContent() {
        homeExpenses.clear();
        homeExpenses.addAll(getCurrentMonthExpenses());
        homeDataProvider = new ListDataProvider<>(homeExpenses);

        totalExpenseLabel = new H1();
        totalExpenseLabel.getStyle().set("margin", "0").set("color", "#0f172a");
        refreshTotalExpenseLabel();

        Paragraph helper = new Paragraph("Showing only expenses from the current month. Seller, invoice, amount, date, and category stay in the main grid. Full invoice details open on double click.");
        helper.getStyle().set("margin", "0").set("color", "#64748b");

        Grid<Expense> grid = new Grid<>(Expense.class, false);
        grid.setDataProvider(homeDataProvider);
        grid.setWidthFull();
        grid.setHeight("calc(100vh - 280px)");

        grid.addColumn(expense -> safeText(expense.getName()))
                .setHeader("Seller")
                .setAutoWidth(true)
                .setSortable(true);
        grid.addColumn(expense -> safeText(expense.getInvoiceNumber()))
                .setHeader("Invoice #")
                .setAutoWidth(true)
                .setSortable(true);
        grid.addColumn(expense -> formatAmount(expense.getAmount()))
                .setHeader("Amount")
                .setAutoWidth(true)
                .setSortable(true);
        grid.addColumn(expense -> expense.getDate() != null ? expense.getDate().toString() : "-")
                .setHeader("Date")
                .setAutoWidth(true)
                .setSortable(true);
        grid.addColumn(expense -> safeText(expense.getCategory()))
                .setHeader("Category")
                .setAutoWidth(true)
                .setSortable(true);

        TextField sellerFilter = createFilterField("Seller");
        TextField invoiceFilter = createFilterField("Invoice #");
        DatePicker dateFilter = new DatePicker("Date");
        dateFilter.setWidth("180px");
        TextField categoryFilter = createFilterField("Category");

        sellerFilter.addValueChangeListener(event -> applyFilters(sellerFilter, invoiceFilter, dateFilter, categoryFilter));
        invoiceFilter.addValueChangeListener(event -> applyFilters(sellerFilter, invoiceFilter, dateFilter, categoryFilter));
        dateFilter.addValueChangeListener(event -> applyFilters(sellerFilter, invoiceFilter, dateFilter, categoryFilter));
        categoryFilter.addValueChangeListener(event -> applyFilters(sellerFilter, invoiceFilter, dateFilter, categoryFilter));

        HorizontalLayout filterRow = new HorizontalLayout(sellerFilter, invoiceFilter, dateFilter, categoryFilter);
        filterRow.setWidthFull();
        filterRow.setSpacing(true);
        filterRow.getStyle().set("flexWrap", "wrap");

        grid.addItemDoubleClickListener(event -> openExpenseEditor(event.getItem()));

        VerticalLayout card = new VerticalLayout(totalExpenseLabel, helper, filterRow, grid);
        card.setWidthFull();
        card.setPadding(true);
        card.setSpacing(true);
        card.getStyle()
                .set("background", "white")
                .set("borderRadius", "24px")
                .set("boxShadow", "0 18px 45px rgba(15, 23, 42, 0.08)");
        return card;
    }

    private void refreshSelectedTabContent() {
        if (contentArea == null) {
            return;
        }

        contentArea.removeAll();
        Tab selected = tabs.getSelectedTab();
        if (selected == calendarTab) {
            contentArea.add(getCalendarContent());
        } else if (selected == reportTab) {
            contentArea.add(getReportTabContent());
        } else if (selected == profileTab) {
            contentArea.add(getProfileTabContent());
        } else {
            contentArea.add(getHomeTabContent());
        }
    }

    private VerticalLayout getCalendarContent() {
        calendarMonthTitle = new H1();
        calendarMonthTitle.getStyle().set("margin", "0").set("color", "#0f172a");

        calendarMonthTotal = new Span();
        calendarMonthTotal.getStyle()
                .set("fontSize", "0.9rem")
                .set("fontWeight", "700")
                .set("color", "#0f766e")
                .set("background", "#ccfbf1")
                .set("padding", "6px 12px")
                .set("borderRadius", "999px");

        Button prevButton = new Button("Prev", new Icon(VaadinIcon.ANGLE_LEFT));
        Button nextButton = new Button("Next", new Icon(VaadinIcon.ANGLE_RIGHT));
        prevButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        nextButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        ComboBox<Month> monthSelect = new ComboBox<>();
        monthSelect.setItems(Month.values());
        monthSelect.setItemLabelGenerator(month -> month.getDisplayName(TextStyle.FULL, Locale.ENGLISH));
        monthSelect.setValue(calendarMonth.getMonth());
        monthSelect.setWidth("180px");

        ComboBox<Integer> yearSelect = new ComboBox<>();
        List<Integer> years = new ArrayList<>();
        int currentYear = YearMonth.now().getYear();
        for (int year = currentYear - 5; year <= currentYear + 5; year++) {
            years.add(year);
        }
        yearSelect.setItems(years);
        yearSelect.setValue(calendarMonth.getYear());
        yearSelect.setWidth("120px");

        VerticalLayout heading = new VerticalLayout(calendarMonthTitle, calendarMonthTotal);
        heading.setPadding(false);
        heading.setSpacing(false);

        HorizontalLayout navigation = new HorizontalLayout(heading, monthSelect, yearSelect, prevButton, nextButton);
        navigation.setWidthFull();
        navigation.setAlignItems(FlexComponent.Alignment.CENTER);
        navigation.expand(heading);

        calendarGridContainer = new Div();
        calendarGridContainer.setWidthFull();
        calendarGridContainer.getStyle().set("height", "calc(100vh - 270px)");

        prevButton.addClickListener(event -> {
            calendarMonth = calendarMonth.minusMonths(1);
            monthSelect.setValue(calendarMonth.getMonth());
            yearSelect.setValue(calendarMonth.getYear());
            refreshCalendar();
        });
        nextButton.addClickListener(event -> {
            calendarMonth = calendarMonth.plusMonths(1);
            monthSelect.setValue(calendarMonth.getMonth());
            yearSelect.setValue(calendarMonth.getYear());
            refreshCalendar();
        });
        monthSelect.addValueChangeListener(event -> {
            if (event.getValue() != null) {
                calendarMonth = YearMonth.of(calendarMonth.getYear(), event.getValue());
                refreshCalendar();
            }
        });
        yearSelect.addValueChangeListener(event -> {
            if (event.getValue() != null) {
                calendarMonth = YearMonth.of(event.getValue(), calendarMonth.getMonth());
                refreshCalendar();
            }
        });

        refreshCalendar();

        VerticalLayout card = new VerticalLayout(navigation, calendarGridContainer);
        card.setWidthFull();
        card.setHeightFull();
        card.setPadding(true);
        card.setSpacing(true);
        card.getStyle()
                .set("background", "white")
                .set("borderRadius", "24px")
                .set("boxShadow", "0 18px 45px rgba(15, 23, 42, 0.08)");
        return card;
    }

    private VerticalLayout getReportTabContent() {
        List<Expense> expenses = getExpenses();
        VerticalLayout card = new VerticalLayout();
        card.setWidthFull();
        card.setHeightFull();
        card.setPadding(true);
        card.setSpacing(true);
        card.getStyle()
                .set("background", "white")
                .set("borderRadius", "24px")
                .set("boxShadow", "0 18px 45px rgba(15, 23, 42, 0.08)")
                .set("padding", "24px");

        Paragraph heading = new Paragraph("AI report: category intelligence, anomaly risk distribution, and the current high-risk review queue.");
        heading.getStyle().set("margin", "0").set("color", "#64748b");

        HorizontalLayout summaryRow = new HorizontalLayout(
                buildMetricCard("Processed", String.valueOf(expenses.size()), "#e0f2fe", "#0c4a6e"),
                buildMetricCard("High Risk", String.valueOf(countByRiskBand(expenses, "HIGH")), "#fee2e2", "#991b1b"),
                buildMetricCard("Needs Review", String.valueOf(countReviewRequired(expenses)), "#fef3c7", "#92400e"),
                buildMetricCard("Avg Score", averageRiskScore(expenses), "#dcfce7", "#166534")
        );
        summaryRow.setWidthFull();
        summaryRow.expand(summaryRow.getComponentAt(0), summaryRow.getComponentAt(1), summaryRow.getComponentAt(2), summaryRow.getComponentAt(3));

        HorizontalLayout distributionRow = new HorizontalLayout(
                buildDistributionCard("Category Distribution", buildCategoryDistribution(expenses), "#0f766e"),
                buildDistributionCard("Risk Band Distribution", buildRiskBandDistribution(expenses), "#b45309")
        );
        distributionRow.setWidthFull();
        distributionRow.expand(distributionRow.getComponentAt(0), distributionRow.getComponentAt(1));

        Grid<Expense> highRiskGrid = new Grid<>(Expense.class, false);
        highRiskGrid.setWidthFull();
        highRiskGrid.setAllRowsVisible(true);
        highRiskGrid.addColumn(Expense::getName).setHeader("Seller").setAutoWidth(true);
        highRiskGrid.addColumn(Expense::getInvoiceNumber).setHeader("Invoice #").setAutoWidth(true);
        highRiskGrid.addColumn(expense -> safeText(expense.getPredictedCategory())).setHeader("Predicted Category").setAutoWidth(true);
        highRiskGrid.addColumn(expense -> safeText(expense.getRiskBand())).setHeader("Risk").setAutoWidth(true);
        highRiskGrid.addColumn(expense -> formatAmount(expense.getAmount())).setHeader("Amount").setAutoWidth(true);
        highRiskGrid.addColumn(expense -> expense.getRiskReasonSummary() == null ? "-" : expense.getRiskReasonSummary()).setHeader("Reason").setFlexGrow(1);
        highRiskGrid.setItems(expenses.stream()
                .filter(expense -> "HIGH".equalsIgnoreCase(expense.getRiskBand()))
                .sorted(Comparator.comparing(Expense::getRiskScore, Comparator.nullsLast(BigDecimal::compareTo)).reversed())
                .limit(10)
                .toList());

        Grid<MetricRow> categoryAverageGrid = new Grid<>(MetricRow.class, false);
        categoryAverageGrid.setWidthFull();
        categoryAverageGrid.setAllRowsVisible(true);
        categoryAverageGrid.addColumn(MetricRow::label).setHeader("Category");
        categoryAverageGrid.addColumn(MetricRow::value).setHeader("Average Amount");
        categoryAverageGrid.setItems(buildAverageAmountByCategory(expenses));

        Grid<MetricRow> signalGrid = new Grid<>(MetricRow.class, false);
        signalGrid.setWidthFull();
        signalGrid.setAllRowsVisible(true);
        signalGrid.addColumn(MetricRow::label).setHeader("Signal");
        signalGrid.addColumn(MetricRow::value).setHeader("Count");
        signalGrid.setItems(buildSignalCounts(expenses));

        card.add(
                heading,
                summaryRow,
                distributionRow,
                createReportSection("High-Risk Queue", highRiskGrid),
                createReportSection("Average Amount by Category", categoryAverageGrid),
                createReportSection("Most Frequent Risk Signals", signalGrid)
        );
        return card;
    }

    private VerticalLayout getProfileTabContent() {
        Optional<User> user = findCurrentUser();
        List<Expense> expenses = getExpenses();
        String profileName = resolveProfileName(user);
        String profileEmail = resolveProfileEmail(user);

        HorizontalLayout metricRow = new HorizontalLayout(
                buildProfileMetricCard("Total Spend", formatAmount(totalSpend(expenses)), "Across every stored invoice", "#eff6ff", "#1d4ed8"),
                buildProfileMetricCard("This Month", formatAmount(currentMonthSpend(expenses)), "Current month activity", "#ecfeff", "#0f766e"),
                buildProfileMetricCard("Expenses Logged", String.valueOf(expenses.size()), "Documents tracked in the workspace", "#f8fafc", "#0f172a"),
                buildProfileMetricCard("Needs Review", String.valueOf(reviewQueueCount(expenses)), "Flagged for manual attention", "#fff7ed", "#c2410c")
        );
        metricRow.setWidthFull();
        metricRow.setPadding(false);
        metricRow.setSpacing(true);
        metricRow.getStyle().set("flexWrap", "wrap");
        metricRow.getChildren().forEach(component -> metricRow.expand(component));

        Div categorySection = buildProfileSection(
                "Category Breakdown",
                "Top categories based on saved expenses and AI fallbacks.",
                buildCategoryBreakdownContent(buildTopCategories(expenses, 5), expenses.size())
        );
        categorySection.addClassName("profile-category-breakdown");

        Div recentActivitySection = buildProfileSection(
                "Recent Activity",
                "Latest expense entries across the tracker.",
                buildRecentActivityContent(buildRecentExpenses(expenses, 5))
        );
        recentActivitySection.addClassName("profile-recent-activity");

        HorizontalLayout insightRow = new HorizontalLayout(categorySection, recentActivitySection);
        insightRow.setWidthFull();
        insightRow.setPadding(false);
        insightRow.setSpacing(true);
        insightRow.getStyle().set("flexWrap", "wrap");
        insightRow.expand(categorySection, recentActivitySection);

        VerticalLayout content = new VerticalLayout(
                buildProfileHero(profileName, profileEmail, user, latestExpenseDate(expenses), topCategory(expenses), reviewQueueCount(expenses)),
                metricRow,
                insightRow
        );
        content.setWidthFull();
        content.setHeightFull();
        content.setPadding(false);
        content.setSpacing(true);
        content.getStyle().set("gap", "20px");
        return content;
    }

    private TextField createFilterField(String label) {
        TextField field = new TextField(label);
        field.setClearButtonVisible(true);
        field.setWidth("200px");
        return field;
    }

    private void applyFilters(TextField sellerFilter,
                              TextField invoiceFilter,
                              DatePicker dateFilter,
                              TextField categoryFilter) {
        if (homeDataProvider == null) {
            return;
        }

        String sellerValue = normalizedFilter(sellerFilter.getValue());
        String invoiceValue = normalizedFilter(invoiceFilter.getValue());
        String categoryValue = normalizedFilter(categoryFilter.getValue());
        LocalDate dateValue = dateFilter.getValue();

        homeDataProvider.setFilter(expense -> {
            boolean sellerMatch = sellerValue.isEmpty() || safeLower(expense.getName()).contains(sellerValue);
            boolean invoiceMatch = invoiceValue.isEmpty() || safeLower(expense.getInvoiceNumber()).contains(invoiceValue);
            boolean categoryMatch = categoryValue.isEmpty() || safeLower(expense.getCategory()).contains(categoryValue);
            boolean dateMatch = dateValue == null || (expense.getDate() != null && expense.getDate().equals(dateValue));
            return sellerMatch && invoiceMatch && categoryMatch && dateMatch;
        });
    }

    private void openExpenseEditor(Expense summaryExpense) {
        Expense detailedExpense = summaryExpense.getId() == null
                ? summaryExpense
                : expenseService.getExpenseWithLineItems(summaryExpense.getId(), DEFAULT_USER_ID).orElse(summaryExpense);

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Invoice Details");
        dialog.setWidth("1100px");
        dialog.setMaxWidth("95vw");
        dialog.setHeight("90vh");

        InvoiceEditorForm editorForm = new InvoiceEditorForm(expenseCategoryCatalog);
        editorForm.setExpense(detailedExpense);
        editorForm.setValidation(null);

        Button saveButton = new Button("Save Changes", new Icon(VaadinIcon.CHECK));
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.getStyle().set("background", "#0f766e").set("color", "white");
        saveButton.addClickListener(event -> {
            editorForm.writeToExpense(detailedExpense);
            Expense saved = expenseService.saveExpense(detailedExpense);
            replaceExpenseInCollections(saved);
            refreshTotalExpenseLabel();
            refreshCalendar();
            dialog.close();
        });

        Button closeButton = new Button("Close", event -> dialog.close());

        HorizontalLayout actions = new HorizontalLayout(saveButton, closeButton);
        actions.setWidthFull();
        actions.setJustifyContentMode(HorizontalLayout.JustifyContentMode.END);

        VerticalLayout content = new VerticalLayout(editorForm, actions);
        content.setSizeFull();
        content.setPadding(false);
        content.setSpacing(true);

        dialog.add(content);
        dialog.open();
    }

    private void refreshCalendar() {
        if (calendarGridContainer == null || calendarMonthTitle == null || calendarMonthTotal == null) {
            return;
        }
        List<Expense> expenses = getExpenses();
        calendarMonthTitle.setText(calendarMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + calendarMonth.getYear());
        calendarMonthTotal.setText("Month Total: " + formatAmount(getMonthlyTotal(calendarMonth, expenses)));
        calendarGridContainer.removeAll();
        calendarGridContainer.add(buildCalendarGrid(calendarMonth, groupExpensesByDate(expenses), sumByDate(groupExpensesByDate(expenses))));
    }

    private Div buildCalendarGrid(YearMonth month, Map<LocalDate, List<Expense>> groupedExpenses, Map<LocalDate, BigDecimal> totals) {
        Div wrapper = new Div();
        wrapper.getStyle()
                .set("display", "flex")
                .set("flexDirection", "column")
                .set("gap", "8px")
                .set("height", "100%");

        Div headerRow = new Div();
        headerRow.getStyle()
                .set("display", "grid")
                .set("gridTemplateColumns", "repeat(7, 1fr)")
                .set("gap", "8px");

        for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
            Span label = new Span(dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            label.getStyle().set("fontWeight", "700").set("color", "#64748b");
            Div cell = new Div(label);
            cell.getStyle().set("padding", "0 6px");
            headerRow.add(cell);
        }

        Div grid = new Div();
        grid.getStyle()
                .set("display", "grid")
                .set("gridTemplateColumns", "repeat(7, 1fr)")
                .set("gridTemplateRows", "repeat(" + calculateWeeksInMonth(month) + ", minmax(100px, 1fr))")
                .set("gap", "8px")
                .set("height", "100%");

        LocalDate firstOfMonth = month.atDay(1);
        int firstDayIndex = firstOfMonth.getDayOfWeek().getValue();
        for (int index = 1; index < firstDayIndex; index++) {
            Div blank = new Div();
            blank.getStyle().set("border", "1px solid transparent");
            grid.add(blank);
        }

        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate date = month.atDay(day);
            List<Expense> expenses = groupedExpenses.getOrDefault(date, List.of());
            BigDecimal total = totals.getOrDefault(date, BigDecimal.ZERO);
            grid.add(buildDateCell(date, total, expenses));
        }

        wrapper.add(headerRow, grid);
        return wrapper;
    }

    private Div buildDateCell(LocalDate date, BigDecimal total, List<Expense> expenses) {
        Span dateLabel = new Span(String.valueOf(date.getDayOfMonth()));
        dateLabel.getStyle().set("fontWeight", "700").set("color", "#0f172a");

        Span totalLabel = new Span(formatAmount(total));
        totalLabel.getStyle()
                .set("fontWeight", "700")
                .set("color", "#0f766e")
                .set("background", "#ccfbf1")
                .set("padding", "4px 8px")
                .set("borderRadius", "999px");

        Span countLabel = new Span(expenses.isEmpty() ? "No invoices" : expenses.size() + " invoice(s)");
        countLabel.getStyle().set("fontSize", "0.8rem").set("color", "#64748b");

        Div cell = new Div(dateLabel, totalLabel, countLabel);
        cell.getStyle()
                .set("border", "1px solid #dbeafe")
                .set("borderRadius", "18px")
                .set("padding", "12px")
                .set("background", expenses.isEmpty() ? "#f8fafc" : "white")
                .set("cursor", expenses.isEmpty() ? "default" : "pointer")
                .set("display", "flex")
                .set("flexDirection", "column")
                .set("gap", "8px");

        if (!expenses.isEmpty()) {
            cell.addClickListener(event -> openDayExpensesDialog(date, expenses));
        }
        return cell;
    }

    private void openDayExpensesDialog(LocalDate date, List<Expense> expenses) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Invoices on " + date);
        dialog.setWidth("760px");

        VerticalLayout list = new VerticalLayout();
        list.setPadding(false);
        list.setSpacing(true);

        expenses.stream()
                .sorted(Comparator.comparing(Expense::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .forEach(expense -> {
                    Span seller = new Span(safeText(expense.getName()));
                    seller.getStyle().set("fontWeight", "700").set("color", "#0f172a");
                    Span details = new Span(
                            "Invoice " + safeText(expense.getInvoiceNumber())
                                    + " | Client " + safeText(expense.getClientName())
                                    + " | " + formatAmount(expense.getAmount())
                                    + " | " + safeText(expense.getCategory())
                    );
                    details.getStyle().set("fontSize", "0.9rem").set("color", "#64748b");

                    VerticalLayout summary = new VerticalLayout(seller, details);
                    summary.setPadding(false);
                    summary.setSpacing(false);

                    Button openButton = new Button("Open", new Icon(VaadinIcon.EXTERNAL_LINK));
                    openButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
                    openButton.addClickListener(event -> {
                        dialog.close();
                        openExpenseEditor(expense);
                    });

                    HorizontalLayout row = new HorizontalLayout(summary, openButton);
                    row.setWidthFull();
                    row.setAlignItems(FlexComponent.Alignment.CENTER);
                    row.setJustifyContentMode(HorizontalLayout.JustifyContentMode.BETWEEN);
                    row.getStyle()
                            .set("padding", "12px 0")
                            .set("borderBottom", "1px solid #e2e8f0");
                    list.add(row);
                });

        dialog.add(list);
        dialog.open();
    }

    private void replaceExpenseInCollections(Expense savedExpense) {
        homeExpenses.removeIf(current -> current.getId() != null && current.getId().equals(savedExpense.getId()));
        if (isInCurrentMonth(savedExpense)) {
            homeExpenses.add(savedExpense);
        }
        if (homeDataProvider != null) {
            homeDataProvider.refreshAll();
        }
    }

    private void refreshTotalExpenseLabel() {
        if (totalExpenseLabel == null) {
            return;
        }
        BigDecimal totalExpense = homeExpenses.stream()
                .map(Expense::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        totalExpenseLabel.setText("Current Month Expense: " + formatAmount(totalExpense));
    }

    private List<Expense> getExpenses() {
        List<Expense> expenses = expenseService.getExpenseByUserId(DEFAULT_USER_ID);
        return expenses == null ? List.of() : expenses;
    }

    private Optional<User> findCurrentUser() {
        return userService.findUser(DEFAULT_USER_ID);
    }

    private String resolveProfileName(Optional<User> user) {
        return user.map(User::getName)
                .filter(this::hasText)
                .orElse("Demo User");
    }

    private String resolveProfileEmail(Optional<User> user) {
        return user.map(User::getEmail)
                .filter(this::hasText)
                .orElse("No email configured");
    }

    private String initialsFor(String name) {
        if (!hasText(name)) {
            return "DU";
        }

        String[] tokens = name.trim().split("\\s+");
        if (tokens.length == 1) {
            String compact = tokens[0].toUpperCase(Locale.ENGLISH);
            return compact.substring(0, Math.min(2, compact.length()));
        }

        StringBuilder initials = new StringBuilder();
        for (String token : tokens) {
            if (!token.isBlank()) {
                initials.append(Character.toUpperCase(token.charAt(0)));
            }
            if (initials.length() == 2) {
                break;
            }
        }
        return initials.isEmpty() ? "DU" : initials.toString();
    }

    private LocalDate latestExpenseDate(List<Expense> expenses) {
        return expenses.stream()
                .map(Expense::getDate)
                .filter(date -> date != null)
                .max(LocalDate::compareTo)
                .orElse(null);
    }

    private String topCategory(List<Expense> expenses) {
        return buildTopCategories(expenses, 1).stream()
                .findFirst()
                .map(MetricRow::label)
                .orElse("None yet");
    }

    private BigDecimal totalSpend(List<Expense> expenses) {
        return expenses.stream()
                .map(Expense::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal currentMonthSpend(List<Expense> expenses) {
        return getMonthlyTotal(YearMonth.now(), expenses);
    }

    private long reviewQueueCount(List<Expense> expenses) {
        return expenses.stream()
                .filter(expense -> Boolean.TRUE.equals(expense.getReviewRequired()))
                .count();
    }

    private List<MetricRow> buildTopCategories(List<Expense> expenses, int limit) {
        Map<String, Long> counts = new HashMap<>();
        for (Expense expense : expenses) {
            String key = resolveExpenseCategory(expense);
            counts.put(key, counts.getOrDefault(key, 0L) + 1);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(entry -> new MetricRow(entry.getKey(), String.valueOf(entry.getValue())))
                .toList();
    }

    private List<Expense> buildRecentExpenses(List<Expense> expenses, int limit) {
        Comparator<Expense> recentComparator = Comparator
                .comparing(Expense::getDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Expense::getId, Comparator.nullsLast(Comparator.reverseOrder()));
        return expenses.stream()
                .sorted(recentComparator)
                .limit(limit)
                .toList();
    }

    private Div buildProfileHero(String profileName,
                                 String profileEmail,
                                 Optional<User> user,
                                 LocalDate lastExpenseDate,
                                 String topCategory,
                                 long reviewQueueCount) {
        Div avatar = new Div(new Span(initialsFor(profileName)));
        avatar.addClassName("profile-avatar");
        avatar.getStyle()
                .set("width", "84px")
                .set("height", "84px")
                .set("borderRadius", "50%")
                .set("display", "flex")
                .set("alignItems", "center")
                .set("justifyContent", "center")
                .set("fontSize", "1.8rem")
                .set("fontWeight", "700")
                .set("color", "#0f172a")
                .set("background", "rgba(255, 255, 255, 0.78)")
                .set("boxShadow", "inset 0 0 0 1px rgba(255, 255, 255, 0.5)");

        H1 name = new H1(profileName);
        name.getStyle()
                .set("margin", "0")
                .set("fontSize", "2rem")
                .set("color", "#0f172a");

        Paragraph email = new Paragraph(profileEmail);
        email.getStyle()
                .set("margin", "0")
                .set("fontSize", "1rem")
                .set("color", "#334155");

        Span meta = new Span(user.map(currentUser -> "User ID #" + currentUser.getId())
                .orElse("User ID #" + DEFAULT_USER_ID + " - Demo profile"));
        meta.getStyle()
                .set("fontSize", "0.9rem")
                .set("color", "#475569");

        VerticalLayout copy = new VerticalLayout(name, email, meta);
        copy.setPadding(false);
        copy.setSpacing(false);
        copy.getStyle().set("gap", "6px");

        HorizontalLayout identity = new HorizontalLayout(avatar, copy);
        identity.setPadding(false);
        identity.setSpacing(true);
        identity.setAlignItems(FlexComponent.Alignment.CENTER);

        HorizontalLayout chips = new HorizontalLayout(
                buildStatusChip("Last expense: " + (lastExpenseDate == null ? "No activity" : lastExpenseDate)),
                buildStatusChip("Top category: " + topCategory),
                buildStatusChip("Review queue: " + reviewQueueCount)
        );
        chips.setPadding(false);
        chips.setSpacing(true);
        chips.getStyle()
                .set("flexWrap", "wrap")
                .set("justifyContent", "flex-end");

        HorizontalLayout heroContent = new HorizontalLayout(identity, chips);
        heroContent.setWidthFull();
        heroContent.setPadding(false);
        heroContent.setSpacing(true);
        heroContent.setAlignItems(FlexComponent.Alignment.CENTER);
        heroContent.setJustifyContentMode(HorizontalLayout.JustifyContentMode.BETWEEN);
        heroContent.getStyle().set("flexWrap", "wrap");

        Div hero = new Div(heroContent);
        hero.addClassName("profile-hero");
        hero.getStyle()
                .set("background", "linear-gradient(135deg, #dbeafe 0%, #ccfbf1 100%)")
                .set("borderRadius", "24px")
                .set("boxShadow", "0 18px 45px rgba(15, 23, 42, 0.08)")
                .set("padding", "28px")
                .set("border", "1px solid rgba(191, 219, 254, 0.9)");
        return hero;
    }

    private Div buildProfileMetricCard(String label, String value, String hint, String background, String accentColor) {
        Span labelSpan = new Span(label);
        labelSpan.getStyle()
                .set("fontSize", "0.88rem")
                .set("fontWeight", "600")
                .set("color", "#475569");

        H1 valueHeading = new H1(value);
        valueHeading.getStyle()
                .set("margin", "0")
                .set("fontSize", "1.9rem")
                .set("color", accentColor);

        Paragraph hintLine = new Paragraph(hint);
        hintLine.getStyle()
                .set("margin", "0")
                .set("fontSize", "0.92rem")
                .set("color", "#64748b");

        Div card = new Div(labelSpan, valueHeading, hintLine);
        card.addClassName("profile-metric-card");
        card.getStyle()
                .set("background", background)
                .set("borderRadius", "22px")
                .set("padding", "20px")
                .set("minWidth", "200px")
                .set("width", "100%")
                .set("flex", "1 1 220px")
                .set("display", "flex")
                .set("flexDirection", "column")
                .set("gap", "10px")
                .set("boxShadow", "0 12px 30px rgba(15, 23, 42, 0.05)");
        return card;
    }

    private Span buildStatusChip(String text) {
        Span chip = new Span(text);
        chip.getStyle()
                .set("padding", "8px 14px")
                .set("borderRadius", "999px")
                .set("background", "rgba(255, 255, 255, 0.76)")
                .set("border", "1px solid rgba(148, 163, 184, 0.25)")
                .set("fontSize", "0.9rem")
                .set("fontWeight", "600")
                .set("color", "#0f172a");
        return chip;
    }

    private Div buildProfileSection(String title, String subtitle, com.vaadin.flow.component.Component content) {
        Span titleSpan = new Span(title);
        titleSpan.getStyle()
                .set("fontWeight", "700")
                .set("fontSize", "1rem")
                .set("color", "#0f172a");

        Paragraph subtitleLine = new Paragraph(subtitle);
        subtitleLine.getStyle()
                .set("margin", "0")
                .set("fontSize", "0.92rem")
                .set("color", "#64748b");

        content.getElement().getStyle().set("width", "100%");

        Div section = new Div(titleSpan, subtitleLine, content);
        section.getStyle()
                .set("background", "white")
                .set("borderRadius", "24px")
                .set("boxShadow", "0 18px 45px rgba(15, 23, 42, 0.08)")
                .set("padding", "24px")
                .set("minWidth", "320px")
                .set("width", "100%")
                .set("flex", "1 1 340px")
                .set("display", "flex")
                .set("flexDirection", "column")
                .set("gap", "16px");
        return section;
    }

    private com.vaadin.flow.component.Component buildCategoryBreakdownContent(List<MetricRow> rows, int totalExpenses) {
        if (rows.isEmpty()) {
            Paragraph emptyState = new Paragraph("No categories yet");
            emptyState.getStyle()
                    .set("margin", "0")
                    .set("color", "#64748b");
            return emptyState;
        }

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);

        for (MetricRow row : rows) {
            long count = Long.parseLong(row.value());
            int percentage = totalExpenses == 0 ? 0 : (int) Math.round((count * 100.0) / totalExpenses);

            Span label = new Span(row.label());
            label.getStyle()
                    .set("fontWeight", "600")
                    .set("color", "#0f172a");

            Span countLabel = new Span(count + " expense" + (count == 1 ? "" : "s") + " - " + percentage + "%");
            countLabel.getStyle()
                    .set("fontSize", "0.88rem")
                    .set("color", "#64748b");

            HorizontalLayout heading = new HorizontalLayout(label, countLabel);
            heading.setWidthFull();
            heading.setPadding(false);
            heading.setSpacing(true);
            heading.setJustifyContentMode(HorizontalLayout.JustifyContentMode.BETWEEN);

            Div barTrack = new Div();
            barTrack.getStyle()
                    .set("width", "100%")
                    .set("height", "10px")
                    .set("borderRadius", "999px")
                    .set("background", "#e2e8f0")
                    .set("overflow", "hidden");

            Div barFill = new Div();
            barFill.getStyle()
                    .set("width", Math.max(percentage, 8) + "%")
                    .set("height", "100%")
                    .set("background", "linear-gradient(90deg, #0ea5e9 0%, #14b8a6 100%)");
            barTrack.add(barFill);

            VerticalLayout rowLayout = new VerticalLayout(heading, barTrack);
            rowLayout.addClassName("profile-category-row");
            rowLayout.setPadding(false);
            rowLayout.setSpacing(false);
            rowLayout.getStyle()
                    .set("gap", "10px")
                    .set("padding", "12px 0")
                    .set("borderBottom", "1px solid #e2e8f0");
            content.add(rowLayout);
        }

        return content;
    }

    private com.vaadin.flow.component.Component buildRecentActivityContent(List<Expense> recentExpenses) {
        if (recentExpenses.isEmpty()) {
            Paragraph emptyState = new Paragraph("No expenses uploaded yet");
            emptyState.getStyle()
                    .set("margin", "0")
                    .set("color", "#64748b");
            return emptyState;
        }

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);

        for (Expense expense : recentExpenses) {
            Span seller = new Span(safeText(expense.getName()));
            seller.getStyle()
                    .set("fontWeight", "700")
                    .set("color", "#0f172a");

            Span date = new Span(expense.getDate() == null ? "No date" : expense.getDate().toString());
            date.getStyle()
                    .set("fontSize", "0.85rem")
                    .set("color", "#64748b");

            HorizontalLayout header = new HorizontalLayout(seller, date);
            header.setWidthFull();
            header.setPadding(false);
            header.setSpacing(true);
            header.setJustifyContentMode(HorizontalLayout.JustifyContentMode.BETWEEN);

            Span category = new Span(resolveExpenseCategory(expense));
            category.getStyle()
                    .set("fontSize", "0.9rem")
                    .set("color", "#475569");

            Span amount = new Span(formatAmount(expense.getAmount()));
            amount.getStyle()
                    .set("fontWeight", "700")
                    .set("color", "#0f766e");

            HorizontalLayout footer = new HorizontalLayout(category, amount);
            footer.setWidthFull();
            footer.setPadding(false);
            footer.setSpacing(true);
            footer.setJustifyContentMode(HorizontalLayout.JustifyContentMode.BETWEEN);

            Div row = new Div(header, footer);
            row.addClassName("profile-recent-activity-row");
            row.getStyle()
                    .set("border", "1px solid #e2e8f0")
                    .set("borderRadius", "18px")
                    .set("padding", "14px 16px")
                    .set("background", "#f8fbff")
                    .set("display", "flex")
                    .set("flexDirection", "column")
                    .set("gap", "10px");
            content.add(row);
        }

        return content;
    }

    private List<Expense> getCurrentMonthExpenses() {
        YearMonth currentMonth = YearMonth.now();
        return getExpenses().stream()
                .filter(this::isInCurrentMonth)
                .filter(expense -> expense.getDate() != null && YearMonth.from(expense.getDate()).equals(currentMonth))
                .toList();
    }

    private boolean isInCurrentMonth(Expense expense) {
        return expense != null
                && expense.getDate() != null
                && YearMonth.from(expense.getDate()).equals(YearMonth.now());
    }

    private BigDecimal getMonthlyTotal(YearMonth month, List<Expense> expenses) {
        return expenses.stream()
                .filter(expense -> expense.getDate() != null)
                .filter(expense -> YearMonth.from(expense.getDate()).equals(month))
                .map(Expense::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<LocalDate, List<Expense>> groupExpensesByDate(List<Expense> expenses) {
        Map<LocalDate, List<Expense>> grouped = new HashMap<>();
        for (Expense expense : expenses) {
            if (expense.getDate() == null) {
                continue;
            }
            grouped.computeIfAbsent(expense.getDate(), key -> new ArrayList<>()).add(expense);
        }
        return grouped;
    }

    private Map<LocalDate, BigDecimal> sumByDate(Map<LocalDate, List<Expense>> grouped) {
        Map<LocalDate, BigDecimal> totals = new HashMap<>();
        for (Map.Entry<LocalDate, List<Expense>> entry : grouped.entrySet()) {
            BigDecimal total = entry.getValue().stream()
                    .map(Expense::getAmount)
                    .filter(amount -> amount != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            totals.put(entry.getKey(), total);
        }
        return totals;
    }

    private int calculateWeeksInMonth(YearMonth month) {
        LocalDate firstOfMonth = month.atDay(1);
        int firstDayIndex = firstOfMonth.getDayOfWeek().getValue();
        int totalSlots = (firstDayIndex - 1) + month.lengthOfMonth();
        return (int) Math.ceil(totalSlots / 7.0);
    }

    private String formatAmount(BigDecimal amount) {
        return AMOUNT_FORMAT.format(amount == null ? BigDecimal.ZERO : amount);
    }

    private String resolveExpenseCategory(Expense expense) {
        if (expense == null) {
            return "Uncategorized";
        }
        if (hasText(expense.getCategory())) {
            return expense.getCategory().trim();
        }
        if (hasText(expense.getPredictedCategory())) {
            return expense.getPredictedCategory().trim();
        }
        return "Uncategorized";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ENGLISH);
    }

    private String normalizedFilter(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ENGLISH);
    }

    private Div buildMetricCard(String label, String value, String background, String color) {
        Span labelSpan = new Span(label);
        labelSpan.getStyle().set("color", "#475569").set("fontSize", "0.9rem");
        H1 valueSpan = new H1(value);
        valueSpan.getStyle().set("margin", "6px 0 0 0").set("color", color).set("fontSize", "1.7rem");

        Div card = new Div(labelSpan, valueSpan);
        card.getStyle()
                .set("background", background)
                .set("borderRadius", "18px")
                .set("padding", "16px")
                .set("width", "100%");
        return card;
    }

    private Div buildDistributionCard(String title, List<MetricRow> rows, String accentColor) {
        Span titleSpan = new Span(title);
        titleSpan.getStyle().set("fontWeight", "700").set("color", "#0f172a");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);
        for (MetricRow row : rows) {
            HorizontalLayout line = new HorizontalLayout(new Span(row.label()), new Span(row.value()));
            line.setWidthFull();
            line.setJustifyContentMode(HorizontalLayout.JustifyContentMode.BETWEEN);
            line.getStyle().set("borderBottom", "1px solid #e2e8f0").set("paddingBottom", "8px");
            content.add(line);
        }

        Div card = new Div(titleSpan, content);
        card.getStyle()
                .set("border", "1px solid #e2e8f0")
                .set("borderLeft", "5px solid " + accentColor)
                .set("borderRadius", "18px")
                .set("padding", "16px")
                .set("width", "100%");
        return card;
    }

    private VerticalLayout createReportSection(String title, com.vaadin.flow.component.Component content) {
        Span titleSpan = new Span(title);
        titleSpan.getStyle().set("fontWeight", "700").set("color", "#0f172a");

        VerticalLayout section = new VerticalLayout(titleSpan, content);
        section.setWidthFull();
        section.setPadding(false);
        section.setSpacing(true);
        return section;
    }

    private int countByRiskBand(List<Expense> expenses, String band) {
        return (int) expenses.stream()
                .filter(expense -> band.equalsIgnoreCase(safeText(expense.getRiskBand())))
                .count();
    }

    private int countReviewRequired(List<Expense> expenses) {
        return (int) expenses.stream()
                .filter(expense -> Boolean.TRUE.equals(expense.getReviewRequired()))
                .count();
    }

    private String averageRiskScore(List<Expense> expenses) {
        BigDecimal total = expenses.stream()
                .map(Expense::getRiskScore)
                .filter(score -> score != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long count = expenses.stream().map(Expense::getRiskScore).filter(score -> score != null).count();
        if (count == 0) {
            return "0.00";
        }
        return total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP).toPlainString();
    }

    private List<MetricRow> buildCategoryDistribution(List<Expense> expenses) {
        Map<String, Long> counts = new HashMap<>();
        for (Expense expense : expenses) {
            String key = safeText(expense.getPredictedCategory());
            counts.put(key, counts.getOrDefault(key, 0L) + 1);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> new MetricRow(entry.getKey(), String.valueOf(entry.getValue())))
                .toList();
    }

    private List<MetricRow> buildRiskBandDistribution(List<Expense> expenses) {
        Map<String, Long> counts = new HashMap<>();
        for (Expense expense : expenses) {
            String key = safeText(expense.getRiskBand());
            counts.put(key, counts.getOrDefault(key, 0L) + 1);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new MetricRow(entry.getKey(), String.valueOf(entry.getValue())))
                .toList();
    }

    private List<MetricRow> buildAverageAmountByCategory(List<Expense> expenses) {
        Map<String, BigDecimal> totals = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        for (Expense expense : expenses) {
            if (expense.getAmount() == null) {
                continue;
            }
            String key = safeText(expense.getPredictedCategory());
            totals.put(key, totals.getOrDefault(key, BigDecimal.ZERO).add(expense.getAmount()));
            counts.put(key, counts.getOrDefault(key, 0) + 1);
        }
        return totals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(entry -> {
                    int count = counts.getOrDefault(entry.getKey(), 1);
                    BigDecimal average = entry.getValue().divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
                    return new MetricRow(entry.getKey(), average.toPlainString());
                })
                .toList();
    }

    private List<MetricRow> buildSignalCounts(List<Expense> expenses) {
        Map<String, Integer> counts = new HashMap<>();
        for (Expense expense : expenses) {
            if (expense.getRiskSignalsJson() == null || expense.getRiskSignalsJson().isBlank()) {
                continue;
            }
            String normalized = expense.getRiskSignalsJson()
                    .replace("[", "")
                    .replace("]", "")
                    .replace("\"", "");
            for (String signal : normalized.split(",")) {
                String trimmed = signal.trim();
                if (!trimmed.isEmpty()) {
                    counts.put(trimmed, counts.getOrDefault(trimmed, 0) + 1);
                }
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .map(entry -> new MetricRow(entry.getKey(), String.valueOf(entry.getValue())))
                .toList();
    }

    private record MetricRow(String label, String value) {
    }
}
