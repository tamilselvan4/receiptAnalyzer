package com.expensetracker.frontend;

import com.expensetracker.model.Expense;
import com.expensetracker.service.ExpenseService;
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
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.annotation.UIScope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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

@Route("")
@UIScope
@Component
public class HomeView extends AppLayout {

    private static final long DEFAULT_USER_ID = 1L;
    private static final DecimalFormat AMOUNT_FORMAT = new DecimalFormat("0.00");

    private final ExpenseService expenseService;

    private YearMonth calendarMonth = YearMonth.now();
    private final List<Expense> homeExpenses = new ArrayList<>();
    private ListDataProvider<Expense> homeDataProvider;
    private H1 totalExpenseLabel;
    private H1 calendarMonthTitle;
    private Span calendarMonthTotal;
    private Div calendarGridContainer;

    public HomeView(ExpenseService expenseService) {
        this.expenseService = expenseService;

        Tab homeTab = new Tab(new Icon(VaadinIcon.HOME), new Paragraph("Home"));
        Tab calendarTab = new Tab(new Icon(VaadinIcon.CALENDAR), new Paragraph("Calendar"));
        Tab reportTab = new Tab(new Icon(VaadinIcon.CHART), new Paragraph("Report"));
        Tab profileTab = new Tab(new Icon(VaadinIcon.USER), new Paragraph("Profile"));
        Tabs tabs = new Tabs(homeTab, calendarTab, reportTab, profileTab);
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

        VerticalLayout contentArea = new VerticalLayout();
        contentArea.setSizeFull();
        contentArea.setPadding(true);
        contentArea.setSpacing(true);
        contentArea.getStyle().set("background", "#f8fbff");
        contentArea.add(getHomeTabContent());

        tabs.addSelectedChangeListener(event -> {
            contentArea.removeAll();
            Tab selected = event.getSelectedTab();
            if (selected == homeTab) {
                contentArea.add(getHomeTabContent());
            } else if (selected == calendarTab) {
                contentArea.add(getCalendarContent());
            } else if (selected == reportTab) {
                contentArea.add(getReportTabContent());
            } else if (selected == profileTab) {
                contentArea.add(getProfileTabContent());
            }
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

    private VerticalLayout getHomeTabContent() {
        homeExpenses.clear();
        homeExpenses.addAll(getExpenses());
        homeDataProvider = new ListDataProvider<>(homeExpenses);

        totalExpenseLabel = new H1();
        totalExpenseLabel.getStyle().set("margin", "0").set("color", "#0f172a");
        refreshTotalExpenseLabel();

        Paragraph helper = new Paragraph("Seller, invoice, client, amount, date, and category stay in the main grid. Full invoice details open on double click.");
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
        grid.addColumn(expense -> safeText(expense.getClientName()))
                .setHeader("Client")
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
        TextField clientFilter = createFilterField("Client");
        DatePicker dateFilter = new DatePicker("Date");
        dateFilter.setWidth("180px");
        TextField categoryFilter = createFilterField("Category");

        sellerFilter.addValueChangeListener(event -> applyFilters(sellerFilter, invoiceFilter, clientFilter, dateFilter, categoryFilter));
        invoiceFilter.addValueChangeListener(event -> applyFilters(sellerFilter, invoiceFilter, clientFilter, dateFilter, categoryFilter));
        clientFilter.addValueChangeListener(event -> applyFilters(sellerFilter, invoiceFilter, clientFilter, dateFilter, categoryFilter));
        dateFilter.addValueChangeListener(event -> applyFilters(sellerFilter, invoiceFilter, clientFilter, dateFilter, categoryFilter));
        categoryFilter.addValueChangeListener(event -> applyFilters(sellerFilter, invoiceFilter, clientFilter, dateFilter, categoryFilter));

        HorizontalLayout filterRow = new HorizontalLayout(sellerFilter, invoiceFilter, clientFilter, dateFilter, categoryFilter);
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
        VerticalLayout card = new VerticalLayout(new Paragraph("Report view is unchanged in this iteration."));
        card.setWidthFull();
        card.setHeightFull();
        card.getStyle()
                .set("background", "white")
                .set("borderRadius", "24px")
                .set("boxShadow", "0 18px 45px rgba(15, 23, 42, 0.08)")
                .set("padding", "24px");
        return card;
    }

    private VerticalLayout getProfileTabContent() {
        VerticalLayout card = new VerticalLayout(new Paragraph("Profile view is unchanged in this iteration."));
        card.setWidthFull();
        card.setHeightFull();
        card.getStyle()
                .set("background", "white")
                .set("borderRadius", "24px")
                .set("boxShadow", "0 18px 45px rgba(15, 23, 42, 0.08)")
                .set("padding", "24px");
        return card;
    }

    private TextField createFilterField(String label) {
        TextField field = new TextField(label);
        field.setClearButtonVisible(true);
        field.setWidth("200px");
        return field;
    }

    private void applyFilters(TextField sellerFilter,
                              TextField invoiceFilter,
                              TextField clientFilter,
                              DatePicker dateFilter,
                              TextField categoryFilter) {
        if (homeDataProvider == null) {
            return;
        }

        String sellerValue = normalizedFilter(sellerFilter.getValue());
        String invoiceValue = normalizedFilter(invoiceFilter.getValue());
        String clientValue = normalizedFilter(clientFilter.getValue());
        String categoryValue = normalizedFilter(categoryFilter.getValue());
        LocalDate dateValue = dateFilter.getValue();

        homeDataProvider.setFilter(expense -> {
            boolean sellerMatch = sellerValue.isEmpty() || safeLower(expense.getName()).contains(sellerValue);
            boolean invoiceMatch = invoiceValue.isEmpty() || safeLower(expense.getInvoiceNumber()).contains(invoiceValue);
            boolean clientMatch = clientValue.isEmpty() || safeLower(expense.getClientName()).contains(clientValue);
            boolean categoryMatch = categoryValue.isEmpty() || safeLower(expense.getCategory()).contains(categoryValue);
            boolean dateMatch = dateValue == null || (expense.getDate() != null && expense.getDate().equals(dateValue));
            return sellerMatch && invoiceMatch && clientMatch && categoryMatch && dateMatch;
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

        InvoiceEditorForm editorForm = new InvoiceEditorForm();
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
        boolean replaced = false;
        for (int index = 0; index < homeExpenses.size(); index++) {
            Expense current = homeExpenses.get(index);
            if (current.getId() != null && current.getId().equals(savedExpense.getId())) {
                homeExpenses.set(index, savedExpense);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
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
        totalExpenseLabel.setText("Total Expense: " + formatAmount(totalExpense));
    }

    private List<Expense> getExpenses() {
        return expenseService.getExpenseByUserId(DEFAULT_USER_ID);
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

    private String safeText(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ENGLISH);
    }

    private String normalizedFilter(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ENGLISH);
    }
}
