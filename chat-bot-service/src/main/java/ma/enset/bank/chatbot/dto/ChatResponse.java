package ma.enset.bank.chatbot.dto;

public record ChatResponse(
        String sessionId,
        String reply,
        String selectedMcpTool,
        boolean requiresConfirmation,
        String confirmationId,
        String mcpRawResult) {
}
