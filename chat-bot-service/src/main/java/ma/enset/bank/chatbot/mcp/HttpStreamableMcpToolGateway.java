package ma.enset.bank.chatbot.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ma.enset.bank.chatbot.dto.McpToolInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HttpStreamableMcpToolGateway implements McpToolGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpStreamableMcpToolGateway.class);

    private static final Set<String> ACCOUNT_TOOLS = Set.of(
            "list_accounts", "get_account", "get_account_balance",
            "create_account", "change_account_status", "block_account",
            "unblock_account", "debit_account", "credit_account");

    private final String accountServiceUrl;
    private final String transactionServiceUrl;

    public HttpStreamableMcpToolGateway(
            @Value("${mcp.servers.account-service-url:http://localhost:8081}") String accountServiceUrl,
            @Value("${mcp.servers.transaction-service-url:http://localhost:8082}") String transactionServiceUrl) {
        this.accountServiceUrl = accountServiceUrl;
        this.transactionServiceUrl = transactionServiceUrl;
    }

    @Override
    public List<McpToolInfo> listAvailableTools() {
        List<McpToolInfo> tools = new ArrayList<>();
        tools.addAll(fetchToolsFromServer("account-service", accountServiceUrl));
        tools.addAll(fetchToolsFromServer("transaction-service", transactionServiceUrl));
        return tools;
    }

    @Override
    public McpInvocationResult invokeTool(String toolName, Map<String, Object> arguments) {
        String baseUrl = ACCOUNT_TOOLS.contains(toolName) ? accountServiceUrl : transactionServiceUrl;
        try (McpSyncClient client = McpClient.sync(
                HttpClientStreamableHttpTransport.builder(baseUrl).endpoint("/mcp").build()).build()) {
            client.initialize();
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(toolName, arguments));
            String text = result.content().stream()
                    .filter(McpSchema.TextContent.class::isInstance)
                    .map(McpSchema.TextContent.class::cast)
                    .map(McpSchema.TextContent::text)
                    .findFirst()
                    .orElse("");
            return new McpInvocationResult(Boolean.TRUE.equals(result.isError()), text);
        } catch (Exception e) {
            log.warn("MCP tool invocation failed for {} on {}: {}", toolName, baseUrl, e.getMessage());
            return new McpInvocationResult(true, "MCP server communication error: " + e.getMessage());
        }
    }

    private List<McpToolInfo> fetchToolsFromServer(String serverName, String baseUrl) {
        try (McpSyncClient client = McpClient.sync(
                HttpClientStreamableHttpTransport.builder(baseUrl).endpoint("/mcp").build()).build()) {
            client.initialize();
            return client.listTools().tools().stream()
                    .map(t -> new McpToolInfo(serverName, t.name(), t.description()))
                    .toList();
        } catch (Exception e) {
            log.warn("Could not list MCP tools from {} ({}): {}", serverName, baseUrl, e.getMessage());
            return List.of();
        }
    }
}
