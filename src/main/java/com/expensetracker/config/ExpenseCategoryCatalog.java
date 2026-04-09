package com.expensetracker.config;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class ExpenseCategoryCatalog {

    public static final String OTHER = "Other";

    private static final List<String> AVAILABLE_CATEGORIES = List.of(
            "General",
            "Meals",
            "Travel",
            "Office Supplies",
            "Electronics",
            "Furniture",
            "Utilities",
            "Fuel",
            "Medical",
            "Internet",
            "Rent",
            "Maintenance",
            "Subscriptions",
            "Entertainment",
            "Services",
            "Education",
            OTHER
    );

    private static final Map<String, List<String>> CATEGORY_KEYWORDS = new LinkedHashMap<>();
    private static final Map<String, String> CANONICAL_TO_EXPANDED = Map.of(
            "General", "General",
            "Meals", "Meals",
            "Travel", "Travel",
            "Office Supplies", "Office Supplies",
            "Electronics", "Electronics",
            "Furniture", "Furniture",
            "Services", "Services"
    );

    static {
        CATEGORY_KEYWORDS.put("Meals", List.of(
                "restaurant", "cafe", "coffee", "food", "lunch", "dinner", "pizza", "dining", "kitchen", "meal", "swiggy", "zomato"
        ));
        CATEGORY_KEYWORDS.put("Travel", List.of(
                "taxi", "cab", "flight", "train", "hotel", "booking", "ticket", "uber", "lyft", "airport", "bus", "travel"
        ));
        CATEGORY_KEYWORDS.put("Office Supplies", List.of(
                "paper", "printer", "stationery", "notebook", "pen", "toner", "office", "folder", "stapler", "ink"
        ));
        CATEGORY_KEYWORDS.put("Electronics", List.of(
                "laptop", "phone", "charger", "monitor", "usb", "adapter", "camera", "device", "keyboard", "mouse", "electronic"
        ));
        CATEGORY_KEYWORDS.put("Furniture", List.of(
                "chair", "desk", "table", "shelf", "cabinet", "furniture", "sofa", "drawer"
        ));
        CATEGORY_KEYWORDS.put("Utilities", List.of(
                "electricity", "water", "utility", "bill payment", "utility bill", "power bill"
        ));
        CATEGORY_KEYWORDS.put("Fuel", List.of(
                "petrol", "diesel", "fuel", "gas station", "refuelling", "refueling"
        ));
        CATEGORY_KEYWORDS.put("Medical", List.of(
                "pharmacy", "hospital", "clinic", "medicine", "diagnostic", "medical", "doctor", "tablet", "lab"
        ));
        CATEGORY_KEYWORDS.put("Internet", List.of(
                "wifi", "broadband", "internet", "fiber", "data plan", "isp", "router"
        ));
        CATEGORY_KEYWORDS.put("Rent", List.of(
                "rent", "lease", "tenant", "property", "landlord", "rental"
        ));
        CATEGORY_KEYWORDS.put("Maintenance", List.of(
                "repair", "service charge", "maintenance", "technician", "servicing", "upkeep"
        ));
        CATEGORY_KEYWORDS.put("Subscriptions", List.of(
                "monthly plan", "subscription", "renewal", "membership", "saas", "license", "plan fee"
        ));
        CATEGORY_KEYWORDS.put("Entertainment", List.of(
                "movie", "event", "streaming", "gaming", "show", "concert", "netflix", "spotify"
        ));
        CATEGORY_KEYWORDS.put("Services", List.of(
                "consulting", "professional service", "support", "freelance", "consultancy", "implementation"
        ));
        CATEGORY_KEYWORDS.put("Education", List.of(
                "tuition", "course", "training", "exam", "certification", "books", "education", "class", "workshop"
        ));
        CATEGORY_KEYWORDS.put("General", List.of());
    }

    public List<String> getAvailableCategories() {
        return AVAILABLE_CATEGORIES;
    }

    public boolean isOtherCategory(String value) {
        return OTHER.equalsIgnoreCase(normalize(value));
    }

    public boolean isStandardCategory(String value) {
        String normalized = normalize(value);
        return AVAILABLE_CATEGORIES.stream()
                .filter(category -> !OTHER.equals(category))
                .map(this::normalize)
                .anyMatch(category -> category.equals(normalized));
    }

    public String resolveStoredCategory(String selectedCategory, String customCategory) {
        String normalizedSelection = trimToNull(selectedCategory);
        if (normalizedSelection == null) {
            return trimToNull(customCategory);
        }
        if (isOtherCategory(normalizedSelection)) {
            String customValue = trimToNull(customCategory);
            return customValue == null ? OTHER : customValue;
        }
        return normalizedSelection;
    }

    public Map<String, List<String>> getCategoryKeywords() {
        return CATEGORY_KEYWORDS;
    }

    public String mapCanonicalCategory(String category) {
        String normalized = trimToNull(category);
        if (normalized == null) {
            return "General";
        }
        return CANONICAL_TO_EXPANDED.getOrDefault(normalized, normalized);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalize(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? "" : trimmed.toLowerCase(Locale.ENGLISH);
    }
}
