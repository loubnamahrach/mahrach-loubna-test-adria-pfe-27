package ma.enset.bank.chatbot.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ma.enset.bank.chatbot.dto.ChatRequest;
import ma.enset.bank.chatbot.dto.ChatResponse;
import ma.enset.bank.chatbot.dto.McpToolInfo;
import ma.enset.bank.chatbot.mcp.McpToolGateway;
import ma.enset.bank.chatbot.mcp.McpToolGateway.McpInvocationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BankingAiAgentService {

    private static final Logger log = LoggerFactory.getLogger(BankingAiAgentService.class);

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?<!\\w)(\\d+(?:[.,]\\d{1,2})?)\\s*(EUR|USD|MAD|GBP)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern CURRENCY_PATTERN = Pattern.compile("\\b(EUR|USD|MAD|GBP)\\b", Pattern.CASE_INSENSITIVE);

    public record PendingOperation(
            String confirmationId,
            String sessionId,
            String toolName,
            Map<String, Object> arguments,
            String description) {
    }

    private final McpToolGateway mcpGateway;
    private final ChatModel chatModel;
    private final Map<String, PendingOperation> pendingConfirmations = new ConcurrentHashMap<>();
    private final Map<String, String> lastConfirmationBySession = new ConcurrentHashMap<>();

    public BankingAiAgentService(McpToolGateway mcpGateway) {
        this(mcpGateway, null);
    }

    @Autowired
    public BankingAiAgentService(McpToolGateway mcpGateway, @Autowired(required = false) ChatModel chatModel) {
        this.mcpGateway = mcpGateway;
        this.chatModel = chatModel;
    }

    public List<McpToolInfo> listTools() {
        return mcpGateway.listAvailableTools();
    }

    public ChatResponse chat(ChatRequest request) {
        String sessionId = (request.sessionId() == null || request.sessionId().isBlank())
                ? "default-session"
                : request.sessionId().trim();
        String msg = request.message().trim();
        String lower = msg.toLowerCase(Locale.ROOT);

        if (lower.startsWith("oui") || lower.startsWith("confirm") || lower.startsWith("valide")) {
            String confId = extractConfirmationId(msg, sessionId);
            if (confId != null && pendingConfirmations.containsKey(confId)) {
                return confirm(confId);
            }
        }
        if (lower.startsWith("non") || lower.startsWith("annul")) {
            String confId = lastConfirmationBySession.remove(sessionId);
            if (confId != null) {
                pendingConfirmations.remove(confId);
                return new ChatResponse(sessionId, "L'opération sensible en attente a été annulée.",
                        null, false, null, null);
            }
        }

        List<UUID> uuids = extractUuids(msg);

        if (lower.contains("virement") || lower.contains("transf")) {
            BigDecimal amount = extractAmount(msg);
            String currency = extractCurrency(msg, "EUR");
            if (uuids.size() < 2 || amount == null) {
                return new ChatResponse(sessionId,
                        "Pour effectuer un virement, veuillez préciser le montant, la devise, l'UUID du compte source et l'UUID du compte destinataire.",
                        null, false, null, null);
            }
            UUID sourceId = uuids.get(0);
            UUID targetId = uuids.get(1);
            return registerSensitiveOperation(sessionId, "transfer_between_accounts",
                    Map.of("sourceAccountId", sourceId.toString(),
                            "targetAccountId", targetId.toString(),
                            "amount", amount,
                            "currency", currency,
                            "idempotencyKey", "AI-" + UUID.randomUUID()),
                    String.format("virement de %s %s du compte %s vers le compte %s",
                            amount, currency, sourceId, targetId));
        }

        if (lower.contains("enregistr") || lower.contains("effectue une transaction") || lower.contains("nouvelle transaction")
                || (lower.contains("transaction") && (lower.contains("de ") || lower.contains("pour ")))) {
            if (!lower.contains("affiche") && !lower.contains("liste") && !lower.contains("consulte") && !lower.contains("quelles")) {
                BigDecimal amount = extractAmount(msg);
                String currency = extractCurrency(msg, "EUR");
                if (uuids.isEmpty() || amount == null) {
                    return new ChatResponse(sessionId,
                            "Pour enregistrer une transaction, veuillez indiquer l'UUID du compte ainsi que le montant et la devise.",
                            null, false, null, null);
                }
                UUID accountId = uuids.get(0);
                return registerSensitiveOperation(sessionId, "record_transaction",
                        Map.of("accountId", accountId.toString(),
                                "amount", amount,
                                "currency", currency,
                                "merchantCategory", "AI_ASSISTANT",
                                "country", "MA"),
                        String.format("enregistrement d'une transaction de %s %s sur le compte %s",
                                amount, currency, accountId));
            }
        }

        if (lower.contains("débloque") || lower.contains("debloque") || lower.contains("unblock")) {
            if (uuids.isEmpty()) {
                return new ChatResponse(sessionId, "Veuillez préciser l'UUID du compte à débloquer.",
                        null, false, null, null);
            }
            return executeReadOrSafeTool(sessionId, "unblock_account", Map.of("id", uuids.get(0).toString()),
                    "Le compte " + uuids.get(0) + " a été débloqué.");
        }
        if (lower.contains("bloque") || lower.contains("block")) {
            if (uuids.isEmpty()) {
                return new ChatResponse(sessionId, "Veuillez préciser l'UUID du compte à bloquer.",
                        null, false, null, null);
            }
            return registerSensitiveOperation(sessionId, "block_account",
                    Map.of("id", uuids.get(0).toString()),
                    "blocage du compte " + uuids.get(0));
        }

        if (lower.contains("transaction") || lower.contains("historique")) {
            if (!uuids.isEmpty()) {
                return executeReadOrSafeTool(sessionId, "list_transactions_by_account",
                        Map.of("accountId", uuids.get(0).toString()),
                        "Voici les transactions du compte " + uuids.get(0) + " :");
            }
            return executeReadOrSafeTool(sessionId, "list_transactions", Map.of(),
                    "Voici la liste des transactions enregistrées :");
        }

        if (lower.contains("solde") || lower.contains("balance")) {
            if (uuids.isEmpty()) {
                return new ChatResponse(sessionId,
                        "Veuillez préciser l'identifiant (UUID) du compte dont vous souhaitez connaître le solde.",
                        null, false, null, null);
            }
            return executeReadOrSafeTool(sessionId, "get_account_balance",
                    Map.of("id", uuids.get(0).toString()),
                    "Solde actuel du compte " + uuids.get(0) + " :");
        }

        if (lower.contains("compte") || lower.contains("account")) {
            if (!uuids.isEmpty()) {
                return executeReadOrSafeTool(sessionId, "get_account",
                        Map.of("id", uuids.get(0).toString()),
                        "Détails du compte " + uuids.get(0) + " :");
            }
            return executeReadOrSafeTool(sessionId, "list_accounts", Map.of(),
                    "Voici la liste de vos comptes bancaires :");
        }

        String llmReply = askOllamaIfAvailable(msg);
        return new ChatResponse(sessionId, llmReply, null, false, null, null);
    }

    public ChatResponse confirm(String confirmationId) {
        PendingOperation op = pendingConfirmations.remove(confirmationId);
        if (op == null) {
            return new ChatResponse("default-session",
                    "Aucune opération en attente trouvée pour l'identifiant de confirmation : " + confirmationId,
                    null, false, null, null);
        }
        lastConfirmationBySession.remove(op.sessionId());
        McpInvocationResult result = mcpGateway.invokeTool(op.toolName(), op.arguments());
        if (result.isError()) {
            return new ChatResponse(op.sessionId(),
                    "L'opération (" + op.description() + ") a été refusée par les règles métier : " + result.content(),
                    op.toolName(), false, null, result.content());
        }
        return new ChatResponse(op.sessionId(),
                "Opération confirmée et exécutée avec succès (" + op.description() + ") : " + result.content(),
                op.toolName(), false, null, result.content());
    }

    private ChatResponse registerSensitiveOperation(String sessionId, String toolName,
                                                    Map<String, Object> args, String description) {
        String confirmationId = UUID.randomUUID().toString().substring(0, 8);
        PendingOperation pending = new PendingOperation(confirmationId, sessionId, toolName, args, description);
        pendingConfirmations.put(confirmationId, pending);
        lastConfirmationBySession.put(sessionId, confirmationId);
        String prompt = String.format(
                "CONFIRMATION REQUISE : Vous demandez l'exécution de l'opération sensible suivante : %s (outil MCP : %s). "
                        + "Pour valider, répondez 'OUI' ou appelez POST /api/chat/confirm/%s.",
                description, toolName, confirmationId);
        return new ChatResponse(sessionId, prompt, toolName, true, confirmationId, null);
    }

    private ChatResponse executeReadOrSafeTool(String sessionId, String toolName,
                                               Map<String, Object> args, String prefix) {
        McpInvocationResult result = mcpGateway.invokeTool(toolName, args);
        if (result.isError()) {
            return new ChatResponse(sessionId,
                    "Erreur lors de l'exécution de l'outil MCP " + toolName + " : " + result.content(),
                    toolName, false, null, result.content());
        }
        return new ChatResponse(sessionId, prefix + " " + result.content(), toolName, false, null, result.content());
    }

    private String askOllamaIfAvailable(String userMessage) {
        if (chatModel != null) {
            try {
                return chatModel.call("Tu es un assistant bancaire sécurisé ADRIA. Réponds brièvement en français à : " + userMessage);
            } catch (Exception e) {
                log.debug("Ollama local model not reachable, using fallback message: {}", e.getMessage());
            }
        }
        return "Je suis votre assistant bancaire IA (connecté aux serveurs MCP Account-Service et Transaction-Service). "
                + "Vous pouvez me demander : 'Quels sont mes comptes ?', 'Quel est le solde du compte <UUID> ?', "
                + "'Affiche les transactions du compte <UUID>', 'Enregistre une transaction de 250 EUR pour le compte <UUID>' "
                + "ou 'Effectue un virement de 100 EUR du compte <UUID1> vers le compte <UUID2>'.";
    }

    private static List<UUID> extractUuids(String text) {
        List<UUID> list = new ArrayList<>();
        Matcher m = UUID_PATTERN.matcher(text);
        while (m.find()) {
            list.add(UUID.fromString(m.group()));
        }
        return list;
    }

    private static BigDecimal extractAmount(String text) {
        String withoutUuids = UUID_PATTERN.matcher(text).replaceAll(" ");
        Matcher m = AMOUNT_PATTERN.matcher(withoutUuids);
        while (m.find()) {
            try {
                BigDecimal val = new BigDecimal(m.group(1).replace(',', '.'));
                if (val.signum() > 0) {
                    return val;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private static String extractCurrency(String text, String defaultCurrency) {
        Matcher m = CURRENCY_PATTERN.matcher(text);
        return m.find() ? m.group(1).toUpperCase(Locale.ROOT) : defaultCurrency;
    }

    private String extractConfirmationId(String text, String sessionId) {
        String[] tokens = text.trim().split("\\s+");
        if (tokens.length >= 2 && pendingConfirmations.containsKey(tokens[1])) {
            return tokens[1];
        }
        return lastConfirmationBySession.get(sessionId);
    }
}
