package ma.enset.bank.account.mcp;

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
import java.util.Optional;
import java.util.UUID;
import ma.enset.bank.account.domain.Account;
import ma.enset.bank.account.domain.AccountStatus;
import ma.enset.bank.account.repository.AccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * Teste le serveur MCP reel (transport HTTP streamable) avec un vrai client MCP.
 * La base est remplacee par un repository simule : seule la persistance est factice.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"})
@Import(AccountMcpToolsTest.NoopTransactions.class)
class AccountMcpToolsTest {

    @TestConfiguration
    static class NoopTransactions {
        @Bean
        PlatformTransactionManager transactionManager() {
            return new AbstractPlatformTransactionManager() {
                @Override
                protected Object doGetTransaction() {
                    return new Object();
                }

                @Override
                protected void doBegin(Object transaction, TransactionDefinition definition) {
                }

                @Override
                protected void doCommit(DefaultTransactionStatus status) {
                }

                @Override
                protected void doRollback(DefaultTransactionStatus status) {
                }
            };
        }
    }

    @LocalServerPort
    int port;

    @MockitoBean
    AccountRepository repository;

    McpSyncClient client;
    UUID id = UUID.randomUUID();
    Account account = new Account("CUST-1", "EUR", new BigDecimal("100.00"));

    @BeforeEach
    void connect() {
        client = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .endpoint("/mcp").build()).build();
        client.initialize();
        when(repository.findById(any())).thenReturn(Optional.of(account));
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(account));
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
    void exposesAllAccountTools() {
        List<String> names = client.listTools().tools().stream().map(McpSchema.Tool::name).toList();
        assertThat(names).containsExactlyInAnyOrder("list_accounts", "get_account", "get_account_balance",
                "create_account", "change_account_status", "block_account", "unblock_account",
                "debit_account", "credit_account");
    }

    @Test
    void readsBalanceThroughService() {
        McpSchema.CallToolResult result = call("get_account_balance", Map.of("id", id.toString()));
        assertThat(result.isError()).isFalse();
        assertThat(text(result)).contains("100");
    }

    @Test
    void debitsThroughService() {
        McpSchema.CallToolResult result = call("debit_account", Map.of("id", id.toString(), "amount", 30));
        assertThat(result.isError()).isFalse();
        assertThat(text(result)).contains("70.00");
    }

    @Test
    void blocksThenRefusesDebit() {
        call("block_account", Map.of("id", id.toString()));
        assertThat(account.getStatus()).isEqualTo(AccountStatus.BLOCKED);
        McpSchema.CallToolResult result = call("debit_account", Map.of("id", id.toString(), "amount", 10));
        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("blocked");
    }

    @Test
    void businessRuleViolationIsReportedAsToolError() {
        McpSchema.CallToolResult result = call("debit_account", Map.of("id", id.toString(), "amount", 5000));
        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("Insufficient funds");
        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void createsAccountThroughService() {
        when(repository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));
        McpSchema.CallToolResult result = call("create_account",
                Map.of("customerReference", "CUST-9", "currency", "EUR", "initialBalance", 50));
        assertThat(result.isError()).isFalse();
        assertThat(text(result)).contains("ACTIVE").contains("50.00");
    }
}