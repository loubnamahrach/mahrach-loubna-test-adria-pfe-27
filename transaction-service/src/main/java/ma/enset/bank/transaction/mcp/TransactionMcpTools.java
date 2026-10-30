package ma.enset.bank.transaction.mcp;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import ma.enset.bank.transaction.dto.CreateTransactionRequest;
import ma.enset.bank.transaction.dto.TransactionResponse;
import ma.enset.bank.transaction.dto.TransferRequest;
import ma.enset.bank.transaction.dto.TransferResponse;
import ma.enset.bank.transaction.service.TransactionService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * Outils MCP de gestion des transactions et virements. Appellent exclusivement TransactionService.
 */
@Component
public class TransactionMcpTools {

    private final TransactionService service;

    public TransactionMcpTools(TransactionService service) {
        this.service = service;
    }

    @McpTool(name = "list_transactions", description = "List all recorded bank transactions")
    public List<TransactionResponse> listTransactions() {
        return service.list();
    }

    @McpTool(name = "get_transaction", description = "Get a bank transaction by its UUID")
    public TransactionResponse getTransaction(@McpToolParam(description = "Transaction UUID") UUID id) {
        return service.get(id);
    }

    @McpTool(name = "list_transactions_by_account", description = "List all transactions of a given bank account")
    public List<TransactionResponse> listTransactionsByAccount(
            @McpToolParam(description = "Account UUID") UUID accountId) {
        return service.listByAccount(accountId);
    }

    @McpTool(name = "record_transaction", description = "Record a new debit transaction on an active account after checking all 9 business rules")
    public TransactionResponse recordTransaction(
            @McpToolParam(description = "Target account UUID") UUID accountId,
            @McpToolParam(description = "Strictly positive amount, at most bank.transaction.max-amount") BigDecimal amount,
            @McpToolParam(description = "ISO 4217 currency code matching the account") String currency,
            @McpToolParam(description = "Merchant category, e.g. GROCERY or ECOMMERCE") String merchantCategory,
            @McpToolParam(description = "ISO country code, e.g. MA or FR") String country) {
        return service.record(new CreateTransactionRequest(accountId, amount, currency, merchantCategory, country));
    }

    @McpTool(name = "transfer_between_accounts", description = "Execute a transfer between two active accounts with Saga compensation and idempotency key")
    public TransferResponse transferBetweenAccounts(
            @McpToolParam(description = "Source account UUID") UUID sourceAccountId,
            @McpToolParam(description = "Target account UUID") UUID targetAccountId,
            @McpToolParam(description = "Strictly positive amount") BigDecimal amount,
            @McpToolParam(description = "ISO 4217 currency code") String currency,
            @McpToolParam(description = "Unique idempotency key for safe retries") String idempotencyKey) {
        return service.transfer(new TransferRequest(sourceAccountId, targetAccountId, amount, currency, idempotencyKey));
    }
}