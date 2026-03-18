package org.opensearch.migrations.transform.shim.reporting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ReportingConfigTest {

    @TempDir
    Path tempDir;

    private Path writeYaml(String content) throws IOException {
        Path file = tempDir.resolve("metrics.yaml");
        Files.writeString(file, content);
        return file;
    }

    @Test
    void parseValidFullConfig() throws Exception {
        var config = ReportingConfig.parse(writeYaml("""
            enabled: true
            include_request_body: true
            sink:
              type: opensearch
              opensearch:
                uri: https://reporting:9200
                index_prefix: my-metrics
                bulk_size: 50
                flush_interval_ms: 3000
                auth:
                  username: admin
                  password: secret
                  tls:
                    insecure: true
            """));
        assertTrue(config.isEnabled());
        assertTrue(config.isIncludeRequestBody());
        assertEquals("opensearch", config.getSink().getType());
        assertEquals("https://reporting:9200", config.getSink().getOpensearch().getUri());
        assertEquals("my-metrics", config.getSink().getOpensearch().getIndexPrefix());
        assertEquals(50, config.getSink().getOpensearch().getBulkSize());
        assertEquals(3000, config.getSink().getOpensearch().getFlushIntervalMs());
        assertEquals("admin", config.getSink().getOpensearch().getAuth().getUsername());
        assertTrue(config.getSink().getOpensearch().getAuth().getTls().isInsecure());
    }

    @Test
    void defaultValuesApplied() throws Exception {
        var config = ReportingConfig.parse(writeYaml("""
            sink:
              type: opensearch
              opensearch:
                uri: http://localhost:9200
            """));
        assertTrue(config.isEnabled());
        assertFalse(config.isIncludeRequestBody());
        assertEquals("shim-metrics", config.getSink().getOpensearch().getIndexPrefix());
        assertEquals(100, config.getSink().getOpensearch().getBulkSize());
        assertEquals(5000, config.getSink().getOpensearch().getFlushIntervalMs());
    }

    @Test
    void malformedYamlThrowsDescriptiveError() {
        assertThrows(Exception.class, () -> ReportingConfig.parse(writeYaml("{{invalid yaml")));
    }

    @Test
    void missingRequiredUriThrows() {
        assertThrows(IllegalArgumentException.class, () -> ReportingConfig.parse(writeYaml("""
            sink:
              type: opensearch
              opensearch:
                index_prefix: test
            """)));
    }

    @Test
    void missingSinkSectionThrows() {
        assertThrows(IllegalArgumentException.class, () -> ReportingConfig.parse(writeYaml("""
            enabled: true
            """)));
    }
}
