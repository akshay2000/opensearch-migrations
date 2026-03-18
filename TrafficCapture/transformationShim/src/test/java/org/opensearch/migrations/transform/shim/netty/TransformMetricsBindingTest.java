package org.opensearch.migrations.transform.shim.netty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the _metrics side-channel binding used by transforms.
 */
class TransformMetricsBindingTest {

    @Test
    void collectTransformMetrics_validEntries() {
        var source = new LinkedHashMap<String, Object>();
        source.put("warn-offset", 1);
        source.put("handler", "edismax");
        var accumulator = new LinkedHashMap<String, Object>();

        MultiTargetRoutingHandler.collectTransformMetrics(source, accumulator);

        assertEquals(1, accumulator.get("warn-offset"));
        assertEquals("edismax", accumulator.get("handler"));
    }

    @Test
    void collectTransformMetrics_ignoresNonStringNonNumberValues() {
        var source = new LinkedHashMap<String, Object>();
        source.put("valid", 42);
        source.put("invalid-list", List.of(1, 2));
        source.put("invalid-map", Map.of("a", "b"));
        var accumulator = new LinkedHashMap<String, Object>();

        MultiTargetRoutingHandler.collectTransformMetrics(source, accumulator);

        assertEquals(1, accumulator.size());
        assertEquals(42, accumulator.get("valid"));
    }

    @Test
    void collectTransformMetrics_emptySource() {
        var accumulator = new LinkedHashMap<String, Object>();
        MultiTargetRoutingHandler.collectTransformMetrics(new LinkedHashMap<>(), accumulator);
        assertTrue(accumulator.isEmpty());
    }

    @Test
    void collectTransformMetrics_nullSource() {
        var accumulator = new LinkedHashMap<String, Object>();
        MultiTargetRoutingHandler.collectTransformMetrics(null, accumulator);
        assertTrue(accumulator.isEmpty());
    }

    @Test
    void collectTransformMetrics_responseOverridesRequest() {
        var accumulator = new LinkedHashMap<String, Object>();
        var reqMetrics = new LinkedHashMap<String, Object>();
        reqMetrics.put("key", "fromRequest");
        MultiTargetRoutingHandler.collectTransformMetrics(reqMetrics, accumulator);

        var respMetrics = new LinkedHashMap<String, Object>();
        respMetrics.put("key", "fromResponse");
        MultiTargetRoutingHandler.collectTransformMetrics(respMetrics, accumulator);

        assertEquals("fromResponse", accumulator.get("key"));
    }
}
