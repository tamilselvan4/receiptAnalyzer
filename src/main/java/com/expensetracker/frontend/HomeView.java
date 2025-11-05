package com.expensetracker.frontend;

import com.expensetracker.model.Expense;
import com.expensetracker.service.ExpenseService;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.annotation.UIScope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Route("")
@UIScope
@Component
public class HomeView extends AppLayout {

//    @Autowired
    private final ExpenseService expenseService;

    public HomeView(ExpenseService expenseService) {

        this.expenseService = expenseService;

        Tab homeTab = new Tab(new Icon(VaadinIcon.HOME), new Paragraph("Home"));
        Tab calendarTab = new Tab(new Icon(VaadinIcon.CALENDAR), new Paragraph("Calendar"));
        Tab reportTab = new Tab(new Icon(VaadinIcon.CHART), new Paragraph("Report"));
        Tab profileTab = new Tab(new Icon(VaadinIcon.USER), new Paragraph("Profile"));
        Tabs tabs = new Tabs(homeTab, calendarTab, reportTab, profileTab);
        tabs.setOrientation(Tabs.Orientation.VERTICAL);
        tabs.getStyle().set("background", "#f8f9fa").set("padding", "1rem").set("minWidth", "160px");

        H1 title = new H1("Expense Tracker");
        title.getStyle().set("margin", "0").set("color", "#007bff");
        Button addExpenseButton = new Button("Add Expense", new Icon(VaadinIcon.PLUS), e -> getUI().ifPresent(ui -> ui.navigate("/add")));
        addExpenseButton.getStyle().set("background", "#007bff").set("color", "white");
        HorizontalLayout topBar = new HorizontalLayout(title, addExpenseButton);
        topBar.setWidthFull();
        topBar.setHeight("64px");
        topBar.setJustifyContentMode(HorizontalLayout.JustifyContentMode.BETWEEN);
        topBar.setAlignItems(Alignment.CENTER); // Vertically center
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

        BigDecimal totalExpense = getExpenses().stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        H1 totalExpenseLabel = new H1("Total Expense: $" + totalExpense);
        totalExpenseLabel.getStyle().set("color", "#007bff");

        Grid<Expense> expenseGrid = new Grid<>(Expense.class, false);
        expenseGrid.addColumn(Expense::getName).setHeader("Name").setAutoWidth(true);
        expenseGrid.addColumn(Expense::getAmount).setHeader("Amount ($)").setAutoWidth(true);
        expenseGrid.addColumn(Expense::getTax).setHeader("Tax ($)").setAutoWidth(true);
        expenseGrid.addColumn(exp -> exp.getDate().toString()).setHeader("Date").setAutoWidth(true);
        expenseGrid.addColumn(Expense::getCategory).setHeader("Category").setAutoWidth(true);
        expenseGrid.addColumn(Expense::getComment).setHeader("Description").setAutoWidth(true);
        expenseGrid.setItems(getExpenses());
        expenseGrid.setWidthFull();

        // Make grid scrollable only, with fixed height
        Div gridContainer = new Div(expenseGrid);
        gridContainer.setWidthFull();
        gridContainer.setHeight("calc(100vh - 64px - 120px)"); // Adjust height as needed
        gridContainer.getStyle().set("overflow", "auto").set("background", "#f8f9fa");

        card.setHeightFull();
        card.add(totalExpenseLabel, gridContainer);
        card.setFlexGrow(1, gridContainer);

        return card;
    }

    private VerticalLayout getCalendarContent() {
        VerticalLayout card = new VerticalLayout(new Paragraph("Calendar data goes here"));
        card.getStyle().set("background", "white").set("borderRadius", "8px").set("boxShadow", "0 2px 8px rgba(0,0,0,0.05)").set("padding", "2rem");
        card.setHeightFull();
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
}