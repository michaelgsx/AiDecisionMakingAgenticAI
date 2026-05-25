package com.aidecision.agentic.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rag-api")
public class RagApiProperties {

    private String baseUrl = "";
    private String opsToken = "";
    private String assessPath = "/rag/assess";

    public String getBaseUrl() { return baseUrl == null ? "" : baseUrl.trim(); }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getOpsToken() { return opsToken == null ? "" : opsToken; }
    public void setOpsToken(String opsToken) { this.opsToken = opsToken; }

    public String getAssessPath() { return assessPath == null ? "/rag/assess" : assessPath; }

    public boolean configured() {
        return !getBaseUrl().isBlank();
    }
}
