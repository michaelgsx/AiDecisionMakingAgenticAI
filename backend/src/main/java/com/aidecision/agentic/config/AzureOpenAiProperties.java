package com.aidecision.agentic.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.azure-openai")
public class AzureOpenAiProperties {

    private String endpoint = "";
    private String apiKey = "";
    private String chatDeployment = "";
    private String chatApiVersion = "";
    private String apiVersion = "2024-02-01";
    private boolean skipChat = false;

    public static final String DEFAULT_CHAT_API_VERSION = "2024-08-01-preview";

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint == null ? "" : endpoint.trim(); }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey == null ? "" : apiKey; }

    public String getChatDeployment() { return chatDeployment; }
    public void setChatDeployment(String chatDeployment) { this.chatDeployment = chatDeployment == null ? "" : chatDeployment.trim(); }

    public String getChatApiVersion() { return chatApiVersion; }
    public void setChatApiVersion(String chatApiVersion) { this.chatApiVersion = chatApiVersion == null ? "" : chatApiVersion.trim(); }

    public String getEffectiveChatApiVersion() {
        return chatApiVersion.isBlank() ? DEFAULT_CHAT_API_VERSION : chatApiVersion;
    }

    public String getApiVersion() { return apiVersion; }
    public void setApiVersion(String apiVersion) { this.apiVersion = apiVersion == null ? "2024-02-01" : apiVersion.trim(); }

    public boolean isSkipChat() { return skipChat; }
    public void setSkipChat(boolean skipChat) { this.skipChat = skipChat; }

    public boolean chatConfigured() {
        return !skipChat && !endpoint.isBlank() && !apiKey.isBlank() && !chatDeployment.isBlank();
    }
}
