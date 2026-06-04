package com.aidecision.agentic.tool;

import com.aidecision.agentic.config.OrchestratorProperties;
import com.aidecision.agentic.dto.ToolExecuteRequest;
import com.aidecision.agentic.dto.ToolRunResponse;
import com.aidecision.agentic.entity.OrchestratorTool;
import com.aidecision.agentic.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * Invokes a registered tool over HTTP at its {@code endpointUrl}
 * (e.g. {@code POST /agent/tools/data_acquisition/1.1.0/execute}) instead of calling the
 * in-process {@link AgentTool} bean. This is what lets the executor treat tools as decoupled,
 * URL-addressable services. Relative endpoint URLs are resolved against {@code tool-base-url}
 * (this app on localhost by default).
 */
@Service
public class HttpToolInvoker {

    private static final Logger log = LoggerFactory.getLogger(HttpToolInvoker.class);

    private final ToolRegistryService toolRegistry;
    private final RestClient http;
    private final OrchestratorProperties props;
    private final String opsToken;
    private final String fallbackBaseUrl;

    public HttpToolInvoker(
            ToolRegistryService toolRegistry,
            OrchestratorProperties props,
            @Value("${app.ops-token:}") String opsToken,
            @Value("${server.port:8788}") String serverPort,
            @Value("${app.orchestrator.tool-http-connect-timeout-ms:10000}") int connectTimeoutMs,
            @Value("${app.orchestrator.tool-http-read-timeout-ms:150000}") int readTimeoutMs) {
        this.toolRegistry = toolRegistry;
        this.props = props;
        this.opsToken = opsToken == null ? "" : opsToken.trim();
        this.fallbackBaseUrl = "http://localhost:" + serverPort;

        // Dedicated client with finite timeouts: a tool call that never returns must surface as a
        // failed step rather than block the executor thread forever.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    /** Calls the tool's execute endpoint and maps the HTTP response back to a {@link ToolResult}. */
    public ToolResult invoke(String toolName, ToolExecutionContext ctx, Map<String, Object> params) {
        OrchestratorTool row = toolRegistry.enabledToolsByName().get(toolName);
        if (row == null) {
            return ToolResult.fail("Unknown or disabled tool: " + toolName);
        }
        String endpoint = row.getEndpointUrl();
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = BuiltinToolCatalog.executeUrl(toolName, row.getVersion());
        }
        String url = baseUrl() + endpoint;

        ToolExecuteRequest body = new ToolExecuteRequest(
                params == null ? Map.of() : params,
                ctx.question(),
                ctx.runId(),
                ctx.stepKey(),
                ctx.priorOutputsByStepKey());

        try {
            ToolRunResponse resp = http.post()
                    .uri(url)
                    .headers(h -> {
                        h.setContentType(MediaType.APPLICATION_JSON);
                        if (!opsToken.isEmpty()) {
                            h.setBearerAuth(opsToken);
                        }
                    })
                    .body(body)
                    .retrieve()
                    .body(ToolRunResponse.class);

            if (resp == null) {
                return ToolResult.fail("Empty response from tool endpoint: " + url);
            }
            return new ToolResult(
                    resp.success(),
                    resp.output() == null ? Map.of() : resp.output(),
                    resp.error(),
                    resp.asyncPending());
        } catch (Exception e) {
            log.warn("HTTP tool call failed tool={} url={}: {}",
                    toolName, url, LogSanitizer.message(e.getMessage()));
            return ToolResult.fail("Tool HTTP call failed (" + url + "): " + e.getMessage());
        }
    }

    private String baseUrl() {
        String configured = props.getToolBaseUrl();
        String base = (configured == null || configured.isBlank()) ? fallbackBaseUrl : configured.trim();
        return base.replaceAll("/+$", "");
    }
}
