package com.expensetracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "invoice.processing")
public class InvoiceProcessingProperties {

    private final LocalAi localAi = new LocalAi();
    private final LmStudio lmStudio = new LmStudio();
    private final ExternalFallback externalFallback = new ExternalFallback();
    private final Ocr ocr = new Ocr();
    private final Storage storage = new Storage();
    private final ValidationRules validation = new ValidationRules();

    public LocalAi getLocalAi() {
        return localAi;
    }

    public LmStudio getLmStudio() {
        return lmStudio;
    }

    public ExternalFallback getExternalFallback() {
        return externalFallback;
    }

    public Ocr getOcr() {
        return ocr;
    }

    public Storage getStorage() {
        return storage;
    }

    public ValidationRules getValidation() {
        return validation;
    }

    public static class LocalAi {
        private String url = "http://localhost:5050/extract-image";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    public static class LmStudio {
        private String url = "http://localhost:1234/v1/chat/completions";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    public static class ExternalFallback {
        private boolean enabled = true;
        private String geminiApiKey;
        private String model = "gemini-2.5-flash";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getGeminiApiKey() {
            return geminiApiKey;
        }

        public void setGeminiApiKey(String geminiApiKey) {
            this.geminiApiKey = geminiApiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    public static class Ocr {
        private String tesseractDataPath;
        private String language = "eng";
        private int pageSegMode = 1;
        private int engineMode = 1;
        private boolean persistArtifacts;

        public String getTesseractDataPath() {
            return tesseractDataPath;
        }

        public void setTesseractDataPath(String tesseractDataPath) {
            this.tesseractDataPath = tesseractDataPath;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public int getPageSegMode() {
            return pageSegMode;
        }

        public void setPageSegMode(int pageSegMode) {
            this.pageSegMode = pageSegMode;
        }

        public int getEngineMode() {
            return engineMode;
        }

        public void setEngineMode(int engineMode) {
            this.engineMode = engineMode;
        }

        public boolean isPersistArtifacts() {
            return persistArtifacts;
        }

        public void setPersistArtifacts(boolean persistArtifacts) {
            this.persistArtifacts = persistArtifacts;
        }
    }

    public static class Storage {
        private String uploadPath = System.getProperty("java.io.tmpdir") + "/expensetracker";

        public String getUploadPath() {
            return uploadPath;
        }

        public void setUploadPath(String uploadPath) {
            this.uploadPath = uploadPath;
        }
    }

    public static class ValidationRules {
        private BigDecimal highAmountThreshold = BigDecimal.valueOf(100000);
        private BigDecimal lineItemTolerance = BigDecimal.valueOf(2.00);

        public BigDecimal getHighAmountThreshold() {
            return highAmountThreshold;
        }

        public void setHighAmountThreshold(BigDecimal highAmountThreshold) {
            this.highAmountThreshold = highAmountThreshold;
        }

        public BigDecimal getLineItemTolerance() {
            return lineItemTolerance;
        }

        public void setLineItemTolerance(BigDecimal lineItemTolerance) {
            this.lineItemTolerance = lineItemTolerance;
        }
    }
}
