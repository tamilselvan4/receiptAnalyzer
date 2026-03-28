package com.expensetracker.frontend;

import com.expensetracker.model.Expense;
import com.expensetracker.service.ExpenseService;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.HeaderRow;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.annotation.UIScope;
import com.vaadin.flow.data.provider.ListDataProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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
import java.text.DecimalFormat;

@Route("")
@UIScope
@Component
public class HomeView extends AppLayout {

//    @Autowired
    private final ExpenseService expenseService;
    private YearMonth calendarMonth = YearMonth.now();
    private static final DecimalFormat AMOUNT_FORMAT = new DecimalFormat("0.00");

    public HomeView(ExpenseService expenseService) {

        this.expenseService = expenseService;

        Tab homeTab = new Tab(new Icon(VaadinIcon.HOME), new Paragraph("Home"));
        Tab calendarTab = new Tab(new Icon(VaadinIcon.CALENDAR), new Paragraph("Calendar"));
        Tab reportTab = new Tab(new Icon(VaadinIcon.CHART), new Paragraph("Report"));
        Tab profileTab = new Tab(new Icon(VaadinIcon.USER), new Paragraph("Profile"));
        Tabs tabs = new Tabs(homeTab, calendarTab, reportTab, profileTab);
        tabs.setOrientation(Tabs.Orientation.VERTICAL);
        tabs.getStyle().set("background", "#f8f9fa").set("padding", "1rem").set("minWidth", "160px");

        H1 title = new H1("Receipt Analyzer");
        title.getStyle().set("margin", "0").set("color", "#007bff");
        Button addExpenseButton = new Button("Add Expense", new Icon(VaadinIcon.PLUS), e -> getUI().ifPresent(ui -> ui.navigate("/add")));
        addExpenseButton.getStyle().set("background", "#007bff").set("color", "white");
        HorizontalLayout topBar = new HorizontalLayout(title, addExpenseButton);
        topBar.setWidthFull();
        topBar.setHeight("64px");
        topBar.setJustifyContentMode(HorizontalLayout.JustifyContentMode.BETWEEN);
        topBar.setAlignItems(Alignment.CENTER);
        topBar.setPadding(true);
        topBar.getStyle().set("background", "#91bafa").set("color", "white");

        VerticalLayout contentArea = new VerticalLayout();
        contentArea.setSizeFull();
        contentArea.setPadding(true);
        contentArea.setSpacing(true);
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
        mainLayout.setHeight("calc(100vh - 64px)"); // Fill viewport minus top bar
        mainLayout.getStyle().set("overflow", "hidden"); // Prevent scrolling

        tabs.setHeightFull();
        contentArea.setSizeFull();

        VerticalLayout rootLayout = new VerticalLayout(topBar, mainLayout);
        rootLayout.setSizeFull();
        rootLayout.setPadding(false);
        rootLayout.setSpacing(false);
        rootLayout.getStyle().set("overflow", "hidden"); // Prevent scrolling
        setContent(rootLayout);
    }

    private VerticalLayout getHomeTabContent() {
        VerticalLayout card = new VerticalLayout();
        card.getStyle().set("background", "white").set("borderRadius", "8px").set("boxShadow", "0 2px 8px rgba(0,0,0,0.05)").set("padding", "2rem");
        card.setWidthFull();
        card.setHeightFull();

        List<Expense> expenses = getExpenses();
        BigDecimal totalExpense = expenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        H1 totalExpenseLabel = new H1("Total Expense: " + formatAmount(totalExpense));
        totalExpenseLabel.getStyle().set("color", "#007bff");

        Grid<Expense> expenseGrid = new Grid<>(Expense.class, false);
        Grid.Column<Expense> nameCol = expenseGrid.addColumn(exp -> safeText(exp.getName()))
                .setHeader("Name").setAutoWidth(true).setSortable(true);
        Grid.Column<Expense> amountCol = expenseGrid.addColumn(exp -> formatAmount(exp.getAmount()))
                .setHeader("Amount").setAutoWidth(true).setSortable(true);
        Grid.Column<Expense> taxCol = expenseGrid.addColumn(exp -> formatAmount(exp.getTax()))
                .setHeader("Tax").setAutoWidth(true).setSortable(true);
        Grid.Column<Expense> dateCol = expenseGrid.addColumn(exp -> exp.getDate() != null ? exp.getDate().toString() : "-")
                .setHeader("Date").setAutoWidth(true).setSortable(true);
        Grid.Column<Expense> categoryCol = expenseGrid.addColumn(exp -> safeText(exp.getCategory()))
                .setHeader("Category").setAutoWidth(true).setSortable(true);
        Grid.Column<Expense> commentCol = expenseGrid.addColumn(exp -> safeText(exp.getComment()))
                .setHeader("Description").setAutoWidth(true);

        amountCol.setComparator((a, b) -> compareBigDecimal(a.getAmount(), b.getAmount()));
        taxCol.setComparator((a, b) -> compareBigDecimal(a.getTax(), b.getTax()));
        dateCol.setComparator((a, b) -> compareLocalDate(a.getDate(), b.getDate()));

        ListDataProvider<Expense> dataProvider = new ListDataProvider<>(expenses);
        expenseGrid.setDataProvider(dataProvider);

        HeaderRow filterRow = expenseGrid.appendHeaderRow();
        TextField nameFilter = new TextField();
        nameFilter.setPlaceholder("Filter");
        nameFilter.setClearButtonVisible(true);
        nameFilter.setWidthFull();
        filterRow.getCell(nameCol).setComponent(nameFilter);

        TextField categoryFilter = new TextField();
        categoryFilter.setPlaceholder("Filter");
        categoryFilter.setClearButtonVisible(true);
        categoryFilter.setWidthFull();
        filterRow.getCell(categoryCol).setComponent(categoryFilter);

        DatePicker dateFilter = new DatePicker();
        dateFilter.setPlaceholder("Date");
        dateFilter.setClearButtonVisible(true);
        dateFilter.setWidthFull();
        filterRow.getCell(dateCol).setComponent(dateFilter);

        nameFilter.addValueChangeListener(e -> applyFilters(dataProvider, nameFilter, categoryFilter, dateFilter));
        categoryFilter.addValueChangeListener(e -> applyFilters(dataProvider, nameFilter, categoryFilter, dateFilter));
        dateFilter.addValueChangeListener(e -> applyFilters(dataProvider, nameFilter, categoryFilter, dateFilter));

        expenseGrid.addItemDoubleClickListener(e -> openEditDialog(e.getItem(), dataProvider));
        expenseGrid.setWidthFull();
        expenseGrid.setHeightFull();

        // Make grid scrollable only, with fixed height
        Div gridContainer = new Div(expenseGrid);
        gridContainer.setWidthFull();
        gridContainer.setHeightFull();
        gridContainer.getStyle().set("overflow", "hidden").set("background", "#f8f9fa");

        card.setHeightFull();
        card.add(totalExpenseLabel, gridContainer);
        card.setFlexGrow(1, gridContainer);

        return card;
    }

    private VerticalLayout getCalendarContent() {
        VerticalLayout card = new VerticalLayout();
        card.getStyle().set("background", "white").set("borderRadius", "8px").set("boxShadow", "0 2px 8px rgba(0,0,0,0.05)").set("padding", "2rem");
        card.setHeightFull();

        H1 monthTitle = new H1();
        monthTitle.getStyle().set("color", "#007bff").set("margin", "0");

        Span monthTotal = new Span();
        monthTotal.getStyle()
                .set("fontSize", "0.9rem")
                .set("fontWeight", "600")
                .set("color", "#0f5132")
                .set("background", "#e7f5ec")
                .set("padding", "4px 10px")
                .set("borderRadius", "8px");

        Button prevBtn = new Button("Prev", new Icon(VaadinIcon.ANGLE_LEFT));
        Button nextBtn = new Button("Next", new Icon(VaadinIcon.ANGLE_RIGHT));
        prevBtn.getStyle().set("background", "#f8f9fa");
        nextBtn.getStyle().set("background", "#f8f9fa");

        ComboBox<Month> monthSelect = new ComboBox<>();
        monthSelect.setItems(Month.values());
        monthSelect.setItemLabelGenerator(m -> m.getDisplayName(TextStyle.FULL, Locale.ENGLISH));
        monthSelect.setValue(calendarMonth.getMonth());
        monthSelect.setWidth("170px");

        int currentYear = YearMonth.now().getYear();
        ComboBox<Integer> yearSelect = new ComboBox<>();
        List<Integer> years = new ArrayList<>();
        for (int y = currentYear - 5; y <= currentYear + 5; y++) {
            years.add(y);
        }
        yearSelect.setItems(years);
        yearSelect.setValue(calendarMonth.getYear());
        yearSelect.setWidth("110px");

        VerticalLayout titleBlock = new VerticalLayout(monthTitle, monthTotal);
        titleBlock.setSpacing(false);
        titleBlock.setPadding(false);

        HorizontalLayout nav = new HorizontalLayout(titleBlock, monthSelect, yearSelect, prevBtn, nextBtn);
        nav.setAlignItems(Alignment.CENTER);
        nav.setWidthFull();
        nav.expand(titleBlock);

        Div gridContainer = new Div();
        gridContainer.setWidthFull();
        gridContainer.getStyle()
                .set("overflow", "hidden")
                .set("paddingBottom", "4px")
                .set("height", "calc(100vh - 64px - 210px)");

        renderCalendar(calendarMonth, monthTitle, monthTotal, gridContainer, getExpenses());

        prevBtn.addClickListener(e -> {
            calendarMonth = calendarMonth.minusMonths(1);
            monthSelect.setValue(calendarMonth.getMonth());
            yearSelect.setValue(calendarMonth.getYear());
            renderCalendar(calendarMonth, monthTitle, monthTotal, gridContainer, getExpenses());
        });
        nextBtn.addClickListener(e -> {
            calendarMonth = calendarMonth.plusMonths(1);
            monthSelect.setValue(calendarMonth.getMonth());
            yearSelect.setValue(calendarMonth.getYear());
            renderCalendar(calendarMonth, monthTitle, monthTotal, gridContainer, getExpenses());
        });

        monthSelect.addValueChangeListener(e -> {
            Month selected = e.getValue();
            if (selected == null) {
                return;
            }
            calendarMonth = YearMonth.of(calendarMonth.getYear(), selected);
            renderCalendar(calendarMonth, monthTitle, monthTotal, gridContainer, getExpenses());
        });

        yearSelect.addValueChangeListener(e -> {
            Integer selected = e.getValue();
            if (selected == null) {
                return;
            }
            calendarMonth = YearMonth.of(selected, calendarMonth.getMonth());
            renderCalendar(calendarMonth, monthTitle, monthTotal, gridContainer, getExpenses());
        });

        card.add(nav, gridContainer);
        return card;
    }

    private VerticalLayout getReportTabContent() {
        VerticalLayout card = new VerticalLayout(new Paragraph("Report data goes here"));
        card.getStyle().set("background", "white").set("borderRadius", "8px").set("boxShadow", "0 2px 8px rgba(0,0,0,0.05)").set("padding", "2rem");
        card.setHeightFull();
        return card;
    }

    private VerticalLayout getProfileTabContent() {
        VerticalLayout card = new VerticalLayout(new Paragraph("Profile data goes here"));
        card.getStyle().set("background", "white").set("borderRadius", "8px").set("boxShadow", "0 2px 8px rgba(0,0,0,0.05)").set("padding", "2rem");
        card.setHeightFull();
        return card;
    }

    private List<Expense> getExpenses() {
        return expenseService.getExpenseByUserId(1L);
//        return List.of(
                /*new Expense(
                        "Groceries",
                        new BigDecimal("5.00"),
                        new BigDecimal("0.50"),
                        "INR",
                        LocalDate.of(2024, 6, 10),
                        "Food",
                        "Weekly shopping"
                ),
                new Expense(
                        "Internet Bill",
                        new BigDecimal("75.0"),
                        new BigDecimal("7.5"),
                        "INR",
                        LocalDate.of(2024, 6, 15),
                        "Utilities",
                        "Monthly payment"
                ),
                new Expense(
                        "Electricity Bill",
                        new BigDecimal("120.0"),
                        new BigDecimal("12.0"),
                        "INR",
                        LocalDate.of(2024, 6, 20),
                        "Utilities",
                        "June electricity payment"
                )*/
//        );
    }

    private Div buildCalendarGrid(YearMonth month, Map<LocalDate, List<Expense>> byDate, Map<LocalDate, BigDecimal> totals) {
        Div wrapper = new Div();
        wrapper.getStyle()
                .set("display", "flex")
                .set("flexDirection", "column")
                .set("gap", "6px")
                .set("height", "100%");

        Div headerRow = new Div();
        headerRow.getStyle()
                .set("display", "grid")
                .set("gridTemplateColumns", "repeat(7, 1fr)")
                .set("gap", "8px")
                .set("width", "100%");

        for (DayOfWeek day : DayOfWeek.values()) {
            Span header = new Span(day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            header.getStyle().set("fontWeight", "600").set("color", "#6c757d");
            Div headerCell = new Div(header);
            headerCell.getStyle().set("padding", "0 8px");
            headerRow.add(headerCell);
        }

        Div grid = new Div();
        int weeks = calculateWeeksInMonth(month);
        grid.getStyle()
                .set("display", "grid")
                .set("gridTemplateColumns", "repeat(7, 1fr)")
                .set("gridTemplateRows", "repeat(" + weeks + ", minmax(90px, 1fr))")
                .set("gap", "8px")
                .set("alignItems", "stretch")
                .set("width", "100%")
                .set("height", "100%");

        LocalDate firstOfMonth = month.atDay(1);
        int firstDayIndex = firstOfMonth.getDayOfWeek().getValue(); // 1=Mon .. 7=Sun

        for (int i = 1; i < firstDayIndex; i++) {
            Div blank = new Div();
            blank.getStyle()
                    .set("minHeight", "90px")
                    .set("height", "100%")
                    .set("border", "1px solid transparent")
                    .set("boxSizing", "border-box");
            grid.add(blank);
        }

        int daysInMonth = month.lengthOfMonth();
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = month.atDay(day);
            BigDecimal total = totals.getOrDefault(date, BigDecimal.ZERO);
            List<Expense> items = byDate.getOrDefault(date, List.of());
            grid.add(buildDateCell(date, total, items));
        }

        wrapper.add(headerRow, grid);
        return wrapper;
    }

    private void renderCalendar(YearMonth month, H1 title, Span monthTotal, Div container, List<Expense> expenses) {
        title.setText(month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.getYear());
        monthTotal.setText("Month Total: ₹ " + formatAmount(getMonthlyTotal(month, expenses)));
        Map<LocalDate, List<Expense>> byDate = groupExpensesByDate(expenses);
        Map<LocalDate, BigDecimal> totals = sumByDate(byDate);
        container.removeAll();
        container.add(buildCalendarGrid(month, byDate, totals));
    }

    private BigDecimal getMonthlyTotal(YearMonth month, List<Expense> expenses) {
        return expenses.stream()
                .filter(e -> e.getDate() != null)
                .filter(e -> YearMonth.from(e.getDate()).equals(month))
                .map(Expense::getAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Div buildDateCell(LocalDate date, BigDecimal total, List<Expense> items) {
        Span dateLabel = new Span(String.valueOf(date.getDayOfMonth()));
        dateLabel.getStyle().set("fontWeight", "600").set("color", "#343a40");

        Span amountLabel = new Span("Total");
        amountLabel.getStyle().set("fontSize", "0.7rem").set("color", "#6c757d");

        Span amountValue = new Span("₹ " + formatAmount(total));
        amountValue.getStyle()
                .set("fontSize", "0.9rem")
                .set("fontWeight", "600")
                .set("color", "#198754")
                .set("background", "#e7f5ec")
                .set("padding", "2px 6px")
                .set("borderRadius", "6px")
                .set("display", "inline-block");

        Div cell = new Div(dateLabel, amountLabel, amountValue);
        cell.getStyle()
                .set("border", "1px solid #e9ecef")
                .set("borderRadius", "8px")
                .set("padding", "8px")
                .set("minHeight", "90px")
                .set("height", "100%")
                .set("width", "100%")
                .set("boxSizing", "border-box")
                .set("display", "flex")
                .set("flexDirection", "column")
                .set("gap", "6px")
                .set("background", items.isEmpty() ? "#f8f9fa" : "#ffffff")
                .set("cursor", items.isEmpty() ? "default" : "pointer");

        if (!items.isEmpty()) {
            cell.addClickListener(e -> openExpenseDialog(date, items));
        }

        return cell;
    }

    private void openExpenseDialog(LocalDate date, List<Expense> items) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Expenses on " + date);

        VerticalLayout list = new VerticalLayout();
        list.setPadding(false);
        list.setSpacing(true);

        items.stream()
                .sorted(Comparator.comparing(Expense::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .forEach(exp -> {
                    String name = exp.getName() != null ? exp.getName() : "Expense";
                    String amount = formatAmount(exp.getAmount());
                    String category = exp.getCategory() != null ? exp.getCategory() : "-";

                    Span row = new Span(name + " • ₹ " + amount + " • " + category);
                    row.getStyle().set("color", "#495057");
                    list.add(row);
                });

        dialog.add(list);
        dialog.setWidth("420px");
        dialog.open();
    }

    private Map<LocalDate, List<Expense>> groupExpensesByDate(List<Expense> expenses) {
        Map<LocalDate, List<Expense>> map = new HashMap<>();
        for (Expense expense : expenses) {
            LocalDate date = expense.getDate();
            if (date == null) {
                continue;
            }
            map.computeIfAbsent(date, k -> new ArrayList<>()).add(expense);
        }
        return map;
    }

    private Map<LocalDate, BigDecimal> sumByDate(Map<LocalDate, List<Expense>> grouped) {
        Map<LocalDate, BigDecimal> totals = new HashMap<>();
        for (Map.Entry<LocalDate, List<Expense>> entry : grouped.entrySet()) {
            BigDecimal total = entry.getValue().stream()
                    .map(Expense::getAmount)
                    .filter(a -> a != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            totals.put(entry.getKey(), total);
        }
        return totals;
    }

    private int calculateWeeksInMonth(YearMonth month) {
        LocalDate firstOfMonth = month.atDay(1);
        int firstDayIndex = firstOfMonth.getDayOfWeek().getValue(); // 1=Mon .. 7=Sun
        int daysInMonth = month.lengthOfMonth();
        int slots = (firstDayIndex - 1) + daysInMonth;
        return (int) Math.ceil(slots / 7.0);
    }

    private void applyFilters(ListDataProvider<Expense> dataProvider, TextField nameFilter, TextField categoryFilter, DatePicker dateFilter) {
        String nameValue = nameFilter.getValue() != null ? nameFilter.getValue().trim().toLowerCase() : "";
        String categoryValue = categoryFilter.getValue() != null ? categoryFilter.getValue().trim().toLowerCase() : "";
        LocalDate dateValue = dateFilter.getValue();

        dataProvider.setFilter(expense -> {
            String name = expense.getName() != null ? expense.getName().toLowerCase() : "";
            String category = expense.getCategory() != null ? expense.getCategory().toLowerCase() : "";
            LocalDate date = expense.getDate();

            boolean nameMatch = nameValue.isEmpty() || name.contains(nameValue);
            boolean categoryMatch = categoryValue.isEmpty() || category.contains(categoryValue);
            boolean dateMatch = dateValue == null || (date != null && date.equals(dateValue));

            return nameMatch && categoryMatch && dateMatch;
        });
    }

    private void openEditDialog(Expense expense, ListDataProvider<Expense> dataProvider) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Edit Expense");

        TextField nameField = new TextField("Name");
        BigDecimalField amountField = new BigDecimalField("Amount");
        BigDecimalField taxField = new BigDecimalField("Tax");
        DatePicker dateField = new DatePicker("Date");
        TextField categoryField = new TextField("Category");
        TextField commentField = new TextField("Description");

        nameField.setValue(expense.getName() != null ? expense.getName() : "");
        amountField.setValue(expense.getAmount());
        taxField.setValue(expense.getTax());
        dateField.setValue(expense.getDate());
        categoryField.setValue(expense.getCategory() != null ? expense.getCategory() : "");
        commentField.setValue(expense.getComment() != null ? expense.getComment() : "");

        Button save = new Button("Save");
        Button cancel = new Button("Cancel");
        save.setEnabled(false);

        Runnable markDirty = () -> save.setEnabled(true);
        nameField.addValueChangeListener(e -> markDirty.run());
        amountField.addValueChangeListener(e -> markDirty.run());
        taxField.addValueChangeListener(e -> markDirty.run());
        dateField.addValueChangeListener(e -> markDirty.run());
        categoryField.addValueChangeListener(e -> markDirty.run());
        commentField.addValueChangeListener(e -> markDirty.run());

        save.addClickListener(e -> {
            expense.setName(nameField.getValue());
            expense.setAmount(amountField.getValue());
            expense.setTax(taxField.getValue());
            expense.setDate(dateField.getValue());
            expense.setCategory(categoryField.getValue());
            expense.setComment(commentField.getValue());

            expenseService.saveExpense(expense);
            dataProvider.refreshItem(expense);
            dialog.close();
        });
        cancel.addClickListener(e -> dialog.close());

        HorizontalLayout actions = new HorizontalLayout(save, cancel);
        actions.setPadding(false);
        actions.setSpacing(true);

        VerticalLayout form = new VerticalLayout(nameField, amountField, taxField, dateField, categoryField, commentField, actions);
        form.setPadding(false);
        form.setSpacing(true);

        dialog.add(form);
        dialog.setWidth("420px");
        dialog.open();
    }

    private String formatAmount(BigDecimal value) {
        if (value == null) {
            return "0.00";
        }
        return AMOUNT_FORMAT.format(value);
    }

    private String safeText(String value) {
        return value != null ? value : "-";
    }

    private int compareBigDecimal(BigDecimal a, BigDecimal b) {
        BigDecimal left = a != null ? a : BigDecimal.ZERO;
        BigDecimal right = b != null ? b : BigDecimal.ZERO;
        return left.compareTo(right);
    }

    private int compareLocalDate(LocalDate a, LocalDate b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        return a.compareTo(b);
    }
}
