package org.opensearch.migrations.transform.shim.reporting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * POJO for the --reporting-config YAML file.
 */
public class ReportingConfig {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    @JsonProperty("enabled")
    private boolean enabled = true;

    @JsonProperty("include_request_body")
    private boolean includeRequestBody = false;

    @JsonProperty("sink")
    private SinkConfig sink;

    public boolean isEnabled() { return enabled; }
    public boolean isIncludeRequestBody() { return includeRequestBody; }
    public SinkConfig getSink() { return sink; }

    public static class SinkConfig {
        @JsonProperty("type")
        private String type = "opensearch";

        @JsonProperty("opensearch")
        private OpenSearchSinkConfig opensearch;

        public String getType() { return type; }
        public OpenSearchSinkConfig getOpensearch() { return opensearch; }
    }

    public static class OpenSearchSinkConfig {
        @JsonProperty("uri")
        private String uri;

        @JsonProperty("index_prefix")
        private String indexPrefix = "shim-metrics";

        @JsonProperty("bulk_size")
        private int bulkSize = 100;

        @JsonProperty("flush_interval_ms")
        private long flushIntervalMs = 5000;

        @JsonProperty("auth")
        private AuthConfig auth;

        public String getUri() { return uri; }
        public String getIndexPrefix() { return indexPrefix; }
        public int getBulkSize() { return bulkSize; }
        public long getFlushIntervalMs() { return flushIntervalMs; }
        public AuthConfig getAuth() { return auth; }
    }

    public static class AuthConfig {
        @JsonProperty("username")
        private String username;

        @JsonProperty("password")
        private String password;

        @JsonProperty("tls")
        private TlsConfig tls;

        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public TlsConfig getTls() { return tls; }
    }

    public static class TlsConfig {
        @JsonProperty("insecure")
        private boolean insecure = false;

        @JsonProperty("trust_store_path")
        private String trustStorePath;

        public boolean isInsecure() { return insecure; }
        public String getTrustStorePath() { return trustStorePath; }
    }

    /**
     * Parse a YAML config file and validate required fields.
     * @throws IllegalArgumentException if required fields are missing
     * @throws IOException if the file cannot be read or parsed
     */
    public static ReportingConfig parse(Path configPath) throws IOException {
        String yaml = Files.readString(configPath);
        ReportingConfig config = YAML_MAPPER.readValue(yaml, ReportingConfig.class);
        validate(config);
        return config;
    }

    private static void validate(ReportingConfig config) {
        if (config.sink == null) {
            throw new IllegalArgumentException("reporting config: 'sink' section is required");
        }
        if ("opensearch".equals(config.sink.type)) {
            if (config.sink.opensearch == null || config.sink.opensearch.uri == null
                    || config.sink.opensearch.uri.isBlank()) {
                throw new IllegalArgumentException(
                        "reporting config: 'sink.opensearch.uri' is required when sink type is 'opensearch'");
            }
        }
    }
}
