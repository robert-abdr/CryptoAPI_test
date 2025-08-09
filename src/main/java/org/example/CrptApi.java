package org.example;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.Map.entry;

public class CrptApi {
    private static final String API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/commissioning/contract/create";
    private final static String TOKEN = "12TEST34TOKEN";
    private final static Map<String, String> HEADER_CONTENT_TYPE_MAP = Map.of(
            "MANUAL", "application/json",
            "XML", "application/xml",
            "CSV", "text/csv"
    );

    private final static Map<String, String> PRODUCT_GROUP_MAP = Map.of(
            "1", "clothes",
            "2", "shoes",
            "3", "tobacco",
            "4", "perfumery",
            "5", "tires",
            "6", "electronics",
            "7", "pharma",
            "8", "milk",
            "9", "bicycle",
            "10", "wheelchairs");

    private static final Map<String, String> TYPE_MAP = Map.ofEntries(
            entry("Документ агрегации. json", "AGGREGATION_DOCUMENT"),
            entry("Агрегация. csv", "AGGREGATION_DOCUMENT_CSV"),
            entry("Агрегация. xml", "AGGREGATION_DOCUMENT_XML"),
            entry("Дезагрегация. json", "DISAGGREGATION_DOCUMENT"),
            entry("Дезагрегация. csv", "DISAGGREGATION_DOCUMENT_CSV"),
            entry("Дезагрегация. xml", "DISAGGREGATION_DOCUMENT_XML"),
            entry("Переагрегация. json", "REAGGREGATION_DOCUMENT"),
            entry("Переагрегация. csv", "REAGGREGATION_DOCUMENT_CSV"),
            entry("Переагрегация. xml", "REAGGREGATION_DOCUMENT_XML"),
            entry("Ввод в оборот. Производство РФ. json", "LP_INTRODUCE_GOODS"),
            entry("Отгрузка. json", "LP_SHIP_GOODS"),
            entry("Отгрузка. csv", "LP_SHIP_GOODS_CSV"),
            entry("Отгрузка. xml", "LP_SHIP_GOODS_XML"),
            entry("Ввод в оборот. Производство РФ. csv", "LP_INTRODUCE_GOODS_CSV"),
            entry("Ввод в оборот. Производство РФ. xml", "LP_INTRODUCE_GOODS_XML"),
            entry("Приемка. json", "LP_ACCEPT_GOODS"));
    //Документ по API очень кривой, все типы не буду класть.
    //Это как пример использования для расширяемости

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Lock lock = new ReentrantLock();
    private final long requestIntervalMillis;
    private final int requestLimit;
    private int requestCount = 0;
    private long lastRequestTime = System.currentTimeMillis();

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newHttpClient();
        this.requestIntervalMillis = timeUnit.toMillis(3);
        this.requestLimit = requestLimit;
    }

    public void createDocument(Document document, String signature) throws IOException, InterruptedException {
        checkLimit();

        String encodedSignature = encodeToBase64(signature);
        String encodedDocument = encodeToBase64(document.productDocument);
        Map<String, String> requestBody = prepareRequestBody(document, encodedDocument, encodedSignature);

        sendDocumentCreationRequest(document, requestBody);
    }

    private String encodeToBase64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes());
    }

    private Map<String, String> prepareRequestBody(Document document, String encodedDocument,
                                                   String encodedSignature) {
        return Map.of(
                "document_format", document.documentFormat,
                "product_document", encodedDocument,
                "product_group", document.productGroup,
                "signature", encodedSignature,
                "type", document.type
        );
    }

    private void sendDocumentCreationRequest(Document document, Map<String, String> requestBody)
            throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(requestBody);
        String pg = "?pg=" + document.productGroup;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + pg))
                .header("Content-Type", HEADER_CONTENT_TYPE_MAP.get(document.documentFormat))
                .header("Authorization", "Bearer " + TOKEN)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Document create ERROR: " + response.body());
        }
    }

    private void checkLimit() {
        lock.lock();
        {
            try {
                long currentTime = System.currentTimeMillis();

                if (currentTime - lastRequestTime > requestIntervalMillis) {
                    requestCount = 0;
                    lastRequestTime = currentTime;
                }

                if (requestCount >= requestLimit) {
                    long sleepTime = requestIntervalMillis - (currentTime - lastRequestTime);
                    if (sleepTime > 0) {
                        Thread.sleep(sleepTime);
                    }
                    requestCount = 0;
                    lastRequestTime = System.currentTimeMillis();
                }
                requestCount++;
            } catch (InterruptedException e) {
                throw new RuntimeException("Thread sleep was failed", e);
            } finally {
                lock.unlock();
            }
        }

    }

    public static class Document {
        @JsonProperty("document_format")
        private final String documentFormat;
        @JsonProperty("product_document")
        private final String productDocument;
        @JsonProperty("product_group")
        private final String productGroup;
        @JsonProperty("type")
        private final String type;

        public Document(String documentFormat, String productDocument, String productGroup, String type) {
            this.documentFormat = checkDocumentFormat(documentFormat);
            this.productDocument = checkProductDocument(productDocument);
            this.productGroup = handleProductGroup(productGroup);
            this.type = handleDocumentType(type);
        }

        private String checkDocumentFormat(String documentFormat) {
            List<String> allowedFormats = List.of("MANUAL", "XML", "CSV");
            String upperCaseDocumentFormat = documentFormat.toUpperCase();
            if (allowedFormats.contains(upperCaseDocumentFormat)) {
                return upperCaseDocumentFormat;
            }
            throw new IllegalArgumentException("Format must be MANUAL/XML/CSV");

        }

        private String checkProductDocument(String productDocument) {
            if (productDocument == null || productDocument.isBlank()) {
                throw new IllegalArgumentException("Product document must be not null or blank");
            }
            return productDocument;

        }

        private String handleProductGroup(String productGroup) {
            if (productGroup == null || productGroup.isBlank()) {
                throw new IllegalArgumentException("Product group must be not null or blank");
            }

            String productGroupName = PRODUCT_GROUP_MAP.get(productGroup);

            if (productGroupName == null) {
                throw new IllegalArgumentException("The product code must consist of numbers 1-10" +
                        " in accordance with your product group");
            }
            return productGroupName;
        }

        private String handleDocumentType(String documentType) {

            if (documentType == null || documentType.isBlank()) {
                throw new IllegalArgumentException("Document type can not be null or blank");
            }

            String type = TYPE_MAP.get(documentType);
            if (type == null) {
                throw new IllegalArgumentException("TypeCode can not found, your type must correspond to the " +
                        "documentation and be in the format " +
                        "\"Ввод в оборот. Производство РФ. json\", \"Переагрегация. xml\", \"Агрегация. csv\"");
            }
            return type;
        }
    }
}