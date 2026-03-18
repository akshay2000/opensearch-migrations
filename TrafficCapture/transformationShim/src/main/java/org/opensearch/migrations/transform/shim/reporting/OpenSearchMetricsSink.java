package org.opensearch.migrations.transform.shim.reporting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bulk-indexes ValidationDocuments into a reporting OpenSearch cluster.
 * Uses a synchronized buffer + background flush thread.
 */
public class OpenSearchMetricsSink implements MetricsSink {

    private static final Logger log = LoggerFactory.getLogger(OpenSearchMetricsSink.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final String reportingClusterUri;
    private final String indexPrefix;
    private final int bulkSize;
    private final HttpClient httpClient;
    private final List<ValidationDocument> buffer = new ArrayList<>();
    private final ScheduledExecutorService scheduler;
    private final String authHeader;

    public OpenSearchMetricsSink(String reportingClusterUri, String indexPrefix,
                                  int bulkSize, long flushIntervalMs,
                                  String username, String password, boolean insecureTls) {
        this.reportingClusterUri = reportingClusterUri.endsWith("/")
                ? reportingClusterUri.substring(0, reportingClusterUri.length() - 1)
                : reportingClusterUri;
        this.indexPrefix = indexPrefix;
        this.bulkSize = bulkSize;

        if (username != null && password != null) {
            this.authHeader = "Basic " + Base64.getEncoder()
                    .encodeToString((username + ":" + password).getBytes());
        } else {
            this.authHeader = null;
        }

        var builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10));
        // Note: for insecure TLS, a custom SSLContext would be needed in production.
        // For now we rely on the default trust store or the JVM's javax.net.ssl settings.
        this.httpClient = builder.build();

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "metrics-sink-flush");
            t.setDaemon(true);
            return t;
        });
        this.scheduler.scheduleAtFixedRate(this::flush, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void submit(ValidationDocument document) {
        try {
            synchronized (buffer) {
                buffer.add(document);
                if (buffer.size() >= bulkSize) {
                    List<ValidationDocument> batch = new ArrayList<>(buffer);
                    buffer.clear();
                    scheduler.execute(() -> sendBulk(batch));
                }
            }
        } catch (Exception e) {
            log.error("Error in MetricsSink.submit()", e);
        }
    }

    @Override
    public void flush() {
        try {
            List<ValidationDocument> batch;
            synchronized (buffer) {
                if (buffer.isEmpty()) return;
                batch = new ArrayList<>(buffer);
                buffer.clear();
            }
            sendBulk(batch);
        } catch (Exception e) {
            log.error("Error in MetricsSink.flush()", e);
        }
    }

    @Override
    public void close() {
        flush();
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void sendBulk(List<ValidationDocument> batch) {
        try {
            String indexName = generateIndexName();
            StringBuilder ndjson = new StringBuilder();
            for (ValidationDocument doc : batch) {
                ndjson.append("{\"index\":{\"_index\":\"").append(indexName).append("\"}}\n");
                ndjson.append(MAPPER.writeValueAsString(doc)).append("\n");
            }

            var requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(reportingClusterUri + "/_bulk"))
                    .header("Content-Type", "application/x-ndjson")
                    .POST(HttpRequest.BodyPublishers.ofString(ndjson.toString()))
                    .timeout(Duration.ofSeconds(30));

            if (authHeader != null) {
                requestBuilder.header("Authorization", authHeader);
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 300) {
                log.warn("Bulk index returned status {}: {}", response.statusCode(),
                        truncate(response.body(), 500));
            } else {
                checkPartialFailures(response.body(), batch.size());
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize ValidationDocument batch", e);
        } catch (Exception e) {
            log.error("Failed to send bulk index request to reporting cluster", e);
        }
    }

    private void checkPartialFailures(String responseBody, int totalDocs) {
        try {
            var tree = MAPPER.readTree(responseBody);
            if (tree.has("errors") && tree.get("errors").asBoolean()) {
                int failedCount = 0;
                var items = tree.get("items");
                if (items != null && items.isArray()) {
                    for (var item : items) {
                        var index = item.get("index");
                        if (index != null && index.has("error")) {
                            failedCount++;
                        }
                    }
                }
                log.warn("Bulk index had {} failures out of {} documents", failedCount, totalDocs);
            }
        } catch (Exception e) {
            log.debug("Could not parse bulk response for failure check", e);
        }
    }

    String generateIndexName() {
        return indexPrefix + "-" + LocalDate.now().format(DATE_FORMAT);
    }

    private static String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
