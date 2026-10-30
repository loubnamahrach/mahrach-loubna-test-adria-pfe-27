package ma.enset.bank.transaction.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.enset.bank.transaction.client.AccountClient;
import ma.enset.bank.transaction.client.AccountView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Teste de bout en bout le serveur MCP de Transaction-Service via un client MCP HTTP Streamable.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TransactionMcpToolsTest {

    @LocalServerPort
    int port;

    @MockitoBean
    AccountClient accountClient;

    McpSyncClient client;
    UUID sourceId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();

    @BeforeEach
    void connect() {
        client = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .endpoint("/mcp").build()).build();
        client.initialize();
    }

    @AfterEach
    void disconnect() {
        client.close();
    }

    private McpSchema.CallToolResult call(String tool, Map<String, Object> args) {
        return client.callTool(new McpSchema.CallToolRequest(tool, args));
    }

    private String text(McpSchema.CallToolResult result) {
        return ((McpSchema.TextContent) result.content().get(0)).text();
    }

    @Test
    void exposesAllTransactionMcpTools() {
        List<String> names = client.listTools().tools().stream().map(McpSchema.Tool::name).toList();
        assertThat(names).containsExactlyInAnyOrder(
                "list_transactions",
                "get_transaction",
                "list_transactions_by_account",
                "record_transaction",
                "transfer_between_accounts");
    }

    @Test
    void recordsTransactionAndListsByAccountThroughMcp() {
        when(accountClient.getAccount(sourceId))
                .thenReturn(new AccountView(sourceId, "CLI-1", "ACTIVE", "EUR", new BigDecimal("2000.00")));
        McpSchema.CallToolResult res = call("record_transaction", Map.of(
                "accountId", sourceId.toString(),
                "amount", 250,
                "currency", "EUR",
                "merchantCategory", "ECOMMERCE",
                "country", "MA"));
        assertThat(res.isError()).isFalse();
        assertThat(text(res)).contains("250.00").contains("ECOMMERCE");

        McpSchema.CallToolResult list = call("list_transactions_by_account", Map.of("accountId", sourceId.toString()));
        assertThat(list.isError()).isFalse();
        assertThat(text(list)).contains("ECOMMERCE");
    }

    @Test
    void rejectsTransactionExceedingMaxAmountThroughMcp() {
        McpSchema.CallToolResult res = call("record_transaction", Map.of(
                "accountId", sourceId.toString(),
                "amount", 1500,
                "currency", "EUR",
                "merchantCategory", "LUXURY",
                "country", "MA"));
        assertThat(res.isError()).isTrue();
        assertThat(text(res)).contains("maximum");
    }

    @Test
    void executesTransferThroughMcp() {
        when(accountClient.getAccount(sourceId))
                .thenReturn(new AccountView(sourceId, "CLI-1", "ACTIVE", "EUR", new BigDecimal("2000.00")));
        when(accountClient.getAccount(targetId))
                .thenReturn(new AccountView(targetId, "CLI-2", "ACTIVE", "EUR", new BigDecimal("500.00")));
        McpSchema.CallToolResult res = call("transfer_between_accounts", Map.of(
                "sourceAccountId", sourceId.toString(),
                "targetAccountId", targetId.toString(),
                "amount", 100,
                "currency", "EUR",
                "idempotencyKey", "MCP-TRANSFER-1"));
        assertThat(res.isError()).isFalse();
        assertThat(text(res)).contains("COMPLETED");
    }
}