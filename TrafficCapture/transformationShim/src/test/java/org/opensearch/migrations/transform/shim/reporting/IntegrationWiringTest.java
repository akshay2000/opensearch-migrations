package org.opensearch.migrations.transform.shim.reporting;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.opensearch.migrations.transform.shim.ShimProxy;
import org.opensearch.migrations.transform.shim.validation.Target;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that the MetricsCollector wiring through ShimProxy works correctly.
 */
class IntegrationWiringTest {

    @Test
    void shimProxyAcceptsNullMetricsCollector() {
        // Should not throw — metrics disabled
        var targets = Map.of("solr", new Target("solr", URI.create("http://localhost:8983"),
            null, null, null));
        assertDoesNotThrow(() -> new ShimProxy(
            0, targets, "solr", Set.of("solr"), List.of(),
            null, false, Duration.ofSeconds(5), 1024, null));
    }

    @Test
    void shimProxyAcceptsMetricsCollector() {
        var sink = new MetricsCollectorTest.CapturingSink();
        var collector = new MetricsCollector(sink, false);
        var targets = Map.of("solr", new Target("solr", URI.create("http://localhost:8983"),
            null, null, null));
        assertDoesNotThrow(() -> new ShimProxy(
            0, targets, "solr", Set.of("solr"), List.of(),
            null, false, Duration.ofSeconds(5), 1024, collector));
    }

    @Test
    void metricsCollectorExceptionDoesNotPropagate() {
        var throwingSink = new MetricsSink() {
            @Override public void submit(ValidationDocument d) { throw new RuntimeException("boom"); }
            @Override public void flush() {}
            @Override public void close() {}
        };
        var collector = new MetricsCollector(throwingSink, false);
        // Calling collect should not throw even when sink throws
        assertDoesNotThrow(() ->
            collector.collect(Map.of("method", "GET", "URI", "/solr/c/select", "headers", Map.of()),
                null, Map.of(), new java.util.LinkedHashMap<>()));
    }
}
