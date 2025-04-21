package com.example.config;

import com.example.CommonClient;
import com.example.utils.ConfigToolCallBackProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.server.transport.WebFluxSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerTransportProvider;

import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;

@Configuration
public class McpServerConfig {

    // STDIO transport
    @Bean
    @ConditionalOnProperty(prefix = "transport", name = "mode", havingValue = "stdio")
    public StdioServerTransportProvider stdioServerTransportProvider() {
        return new StdioServerTransportProvider();
    }

    // SSE transport
    @Bean
    @ConditionalOnProperty(prefix = "transport", name = "mode", havingValue = "sse")
    public WebFluxSseServerTransportProvider sseServerTransportProvider() {
        return new WebFluxSseServerTransportProvider(new ObjectMapper(), "/mcp/message");
    }

    // Router function for SSE transport used by Spring WebFlux to start an HTTP
    // server.
    @Bean
    @ConditionalOnProperty(prefix = "transport", name = "mode", havingValue = "sse")
    public RouterFunction<?> mcpRouterFunction(WebFluxSseServerTransportProvider transportProvider) {
        return transportProvider.getRouterFunction();
    }

    @Bean
    public CommonClient commonClient() {
        return new CommonClient();
    }

    @Bean
    public McpSyncServer mcpServer(McpServerTransportProvider transportProvider, CommonClient commonClient) { // @formatter:off

        // Configure server capabilities with resource support
        var capabilities = McpSchema.ServerCapabilities.builder()
                .tools(true) // Tool support with list changes notifications
                .logging() // Logging support
                .build();
        ConfigToolCallBackProvider configToolCallBackProvider = ConfigToolCallBackProvider.builder().build();

        // Create the server with both tool and resource capabilities
        McpSyncServer server = McpServer.sync(transportProvider)
                .serverInfo("MCP Common Server", "1.0.0")
                .capabilities(capabilities)
                .tools(McpToolUtils.toSyncToolSpecifications( configToolCallBackProvider.getToolCallbacks())) // Add @Tools
//                .tools(McpToolUtils.toSyncToolSpecifications(ToolCallbacks.from(commonClient))) // Add @Tools
                .build();

        return server; // @formatter:on
    } // @formatter:on

}
