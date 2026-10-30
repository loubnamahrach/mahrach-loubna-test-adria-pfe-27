package ma.enset.bank.chatbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;
import ma.enset.bank.chatbot.dto.ChatRequest;
import ma.enset.bank.chatbot.dto.ChatResponse;
import ma.enset.bank.chatbot.mcp.McpToolGateway;
import ma.enset.bank.chatbot.mcp.McpToolGateway.McpInvocationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BankingAiAgentServiceTest {

    @Mock
    McpToolGateway mcpGateway;

    BankingAiAgentService agent;
    UUID accountX = UUID.randomUUID();
    UUID accountY = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        agent = new BankingAiAgentService(mcpGateway);
    }

    @Test
    void listsAccountsImmediatelyViaMcpTool() {
        when(mcpGateway.invokeTool(eq("list_accounts"), anyMap()))
                .thenReturn(new McpInvocationResult(false, "[{\"customerReference\":\"CLI-01\",\"balance\":1650.00}]"));

        ChatResponse res = agent.chat(new ChatRequest("s1", "Quels sont mes comptes ?"));

        assertThat(res.requiresConfirmation()).isFalse();
        assertThat(res.selectedMcpTool()).isEqualTo("list_accounts");
        assertThat(res.reply()).contains("1650.00");
    }

    @Test
    void queriesAccountBalanceViaMcpTool() {
        when(mcpGateway.invokeTool("get_account_balance", Map.of("id", accountX.toString())))
                .thenReturn(new McpInvocationResult(false, "1650.00"));

        ChatResponse res = agent.chat(new ChatRequest("s1", "Quel est le solde du compte " + accountX + " ?"));

        assertThat(res.selectedMcpTool()).isEqualTo("get_account_balance");
        assertThat(res.reply()).contains("1650.00");
    }

    @Test
    void queriesTransactionsByAccountViaMcpTool() {
        when(mcpGateway.invokeTool("list_transactions_by_account", Map.of("accountId", accountX.toString())))
                .thenReturn(new McpInvocationResult(false, "[{\"amount\":250.00,\"currency\":\"EUR\"}]"));

        ChatResponse res = agent.chat(new ChatRequest("s1", "Affiche les transactions du compte " + accountX));

        assertThat(res.selectedMcpTool()).isEqualTo("list_transactions_by_account");
        assertThat(res.reply()).contains("250.00");
    }

    @Test
    void requiresExplicitConfirmationBeforeRecordingTransactionThenExecutesMcpTool() {
        ChatResponse step1 = agent.chat(new ChatRequest("s1",
                "Enregistre une transaction de 250 EUR pour le compte " + accountX));

        assertThat(step1.requiresConfirmation()).isTrue();
        assertThat(step1.selectedMcpTool()).isEqualTo("record_transaction");
        assertThat(step1.confirmationId()).isNotBlank();
        verify(mcpGateway, never()).invokeTool(eq("record_transaction"), anyMap());

        when(mcpGateway.invokeTool(eq("record_transaction"), anyMap()))
                .thenReturn(new McpInvocationResult(false, "{\"id\":\"tx-1\",\"amount\":250.00}"));

        ChatResponse step2 = agent.chat(new ChatRequest("s1", "OUI"));

        assertThat(step2.requiresConfirmation()).isFalse();
        assertThat(step2.selectedMcpTool()).isEqualTo("record_transaction");
        assertThat(step2.reply()).contains("confirmée et exécutée");
        verify(mcpGateway).invokeTool(eq("record_transaction"), anyMap());
    }

    @Test
    void requiresExplicitConfirmationBeforeTransferAndPropagatesBusinessRuleError() {
        ChatResponse step1 = agent.chat(new ChatRequest("s2",
                "Effectue un virement de 100 EUR du compte " + accountX + " vers le compte " + accountY));

        assertThat(step1.requiresConfirmation()).isTrue();
        assertThat(step1.selectedMcpTool()).isEqualTo("transfer_between_accounts");
        verify(mcpGateway, never()).invokeTool(eq("transfer_between_accounts"), anyMap());

        when(mcpGateway.invokeTool(eq("transfer_between_accounts"), anyMap()))
                .thenReturn(new McpInvocationResult(true, "Insufficient funds"));

        ChatResponse step2 = agent.confirm(step1.confirmationId());
        assertThat(step2.reply()).contains("refusée par les règles métier").contains("Insufficient funds");
    }

    @Test
    void asksForClarificationWhenParametersAreMissing() {
        ChatResponse res = agent.chat(new ChatRequest("s3", "Effectue un virement de 100 EUR"));
        assertThat(res.requiresConfirmation()).isFalse();
        assertThat(res.reply()).contains("veuillez préciser");
    }
}
