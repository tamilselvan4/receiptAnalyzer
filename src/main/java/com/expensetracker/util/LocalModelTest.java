package com.expensetracker.util;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class LocalModelTest {
    public static void main(String[] args) {
        try {
            // URL of your API endpoint
            URL url = new URL("http://localhost:8000/predict");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            // Set request method to POST
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");

            // JSON payload
            String jsonInputString = "{\"text\": \"Your invoice details here\"}";

            // Write JSON to request body
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Read the response
            int code = conn.getResponseCode();
            Scanner scanner;
            if (code == 200) {
                scanner = new Scanner(conn.getInputStream(), "UTF-8");
            } else {
                scanner = new Scanner(conn.getErrorStream(), "UTF-8");
            }
            String response = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
            scanner.close();

            // Output the response
            System.out.println("Response Code: " + code);
            System.out.println("Response Body: " + response);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}