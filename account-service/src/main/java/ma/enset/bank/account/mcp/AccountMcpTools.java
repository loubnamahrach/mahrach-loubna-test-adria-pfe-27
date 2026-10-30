package ma.enset.bank.account.mcp;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import ma.enset.bank.account.domain.AccountStatus;
import ma.enset.bank.account.dto.AccountResponse;
import ma.enset.bank.account.dto.CreateAccountRequest;
import ma.enset.bank.account.service.AccountService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * Outils MCP de gestion des comptes. Chaque outil delegue a AccountService.
 */
@Component
public class AccountMcpTools {

    private final AccountService service;

    public AccountMcpTools(AccountService service) {
        this.service = service;
    }

    @McpTool(name = "list_accounts", description = "List all bank accounts with their status, currency and balance")
    public List<AccountResponse> listAccounts() {
        return service.list();
    }

    @McpTool(name = "get_account", description = "Get one bank account by its identifier")
    public AccountResponse getAccount(@McpToolParam(description = "Account UUID") UUID id) {
        return service.get(id);
    }

    @McpTool(name = "get_account_balance", description = "Get the current balance of a bank account")
    public BigDecimal getAccountBalance(@McpToolParam(description = "Account UUID") UUID id) {
        return service.getBalance(id);
    }

    @McpTool(name = "create_account", description = "Create a new ACTIVE bank account")
    public AccountResponse createAccount(
            @McpToolParam(description = "Customer reference") String customerReference,
            @McpToolParam(description = "ISO 4217 currency code in upper case, for example EUR") String currency,
            @McpToolParam(description = "Initial balance, zero or positive, at most 2 decimals") BigDecimal initialBalance) {
        return service.create(new CreateAccountRequest(customerReference, currency, initialBalance));
    }

    @McpTool(name = "change_account_status", description = "Set the status of an account to ACTIVE or BLOCKED")
    public AccountResponse changeAccountStatus(
            @McpToolParam(description = "Account UUID") UUID id,
            @McpToolParam(description = "ACTIVE or BLOCKED") AccountStatus status) {
        return service.changeStatus(id, status);
    }

    @McpTool(name = "block_account", description = "Block an account so that it refuses debits and credits")
    public AccountResponse blockAccount(@McpToolParam(description = "Account UUID") UUID id) {
        return service.block(id);
    }

    @McpTool(name = "unblock_account", description = "Unblock an account")
    public AccountResponse unblockAccount(@McpToolParam(description = "Account UUID") UUID id) {
        return service.unblock(id);
    }

    @McpTool(name = "debit_account", description = "Debit an active account; refused if the balance is insufficient")
    public AccountResponse debitAccount(
            @McpToolParam(description = "Account UUID") UUID id,
            @McpToolParam(description = "Strictly positive amount, at most 2 decimals") BigDecimal amount) {
        return service.debit(id, amount);
    }

    @McpTool(name = "credit_account", description = "Credit an active account")
    public AccountResponse creditAccount(
            @McpToolParam(description = "Account UUID") UUID id,
            @McpToolParam(description = "Strictly positive amount, at most 2 decimals") BigDecimal amount) {
        return service.credit(id, amount);
    }
}