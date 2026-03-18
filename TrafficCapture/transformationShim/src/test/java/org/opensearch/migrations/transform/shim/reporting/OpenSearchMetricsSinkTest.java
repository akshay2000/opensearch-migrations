package org.opensearch.migrations.transform.shim.reporting;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenSearchMetricsSinkTest {

    private ValidationDocument minimalDoc() {
        return new ValidationDocument(
            "2025-03-17T10:00:00Z", "test-id",
            null, null, null, null,
            null, null, null, null, null, null, null, null
        );
    }

    @Test
    void indexNameFormat() {
        try (var sink = new OpenSearchMetricsSink(
                "http://localhost:9200", "shim-metrics", 100, 60000, null, null, false)) {
            String indexName = sink.generateIndexName();
            String expected = "shim-metrics-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
            assertEquals(expected, indexName);
        }
    }

    @Test
    void indexNameCustomPrefix() {
        try (var sink = new OpenSearchMetricsSink(
                "http://localhost:9200", "my-prefix", 100, 60000, null, null, false)) {
            String indexName = sink.generateIndexName();
            assertTrue(indexName.startsWith("my-prefix-"));
        }
    }

    @Test
    void submitNeverThrows() {
        // Sink with unreachable URI — submit should not throw
        var sink = new OpenSearchMetricsSink(
            "http://localhost:1", "test", 1, 60000, null, null, false);
        assertDoesNotThrow(() -> sink.submit(minimalDoc()));
        sink.close();
    }

    @Test
    void submitMultipleNeverThrows() {
        var sink = new OpenSearchMetricsSink(
            "http://localhost:1", "test", 2, 60000, null, null, false);
        // Submit more than bulkSize to trigger a flush attempt against unreachable host
        assertDoesNotThrow(() -> {
            sink.submit(minimalDoc());
            sink.submit(minimalDoc());
            sink.submit(minimalDoc());
        });
        sink.close();
    }

    @Test
    void closeFlushesAndShutdown() {
        var sink = new OpenSearchMetricsSink(
            "http://localhost:1", "test", 100, 60000, null, null, false);
        sink.submit(minimalDoc());
        // close() should flush remaining and not throw
        assertDoesNotThrow(sink::close);
    }
}
