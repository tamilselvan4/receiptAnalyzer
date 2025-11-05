package com.expensetracker.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public class FlexibleDateDeserializer extends JsonDeserializer<LocalDate> {

    private static final List<String> DATE_PATTERNS = List.of(
            "dd/MM/yyyy",
            "dd-MM-yyyy",
            "dd.MM.yyyy",
            "dd/MM/yy",
            "yyyy-MM-dd",
            "yyyy/MM/dd"
    );

    @Override
    public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String dateText = p.getText().trim();

        for (String pattern : DATE_PATTERNS) {
            try {
                return LocalDate.parse(dateText, DateTimeFormatter.ofPattern(pattern));
            } catch (DateTimeParseException ignored) {}
        }

        throw new IOException("Unrecognized date format: " + dateText);
    }
}

