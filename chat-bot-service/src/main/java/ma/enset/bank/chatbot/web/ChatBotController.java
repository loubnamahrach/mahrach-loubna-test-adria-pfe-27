package ma.enset.bank.chatbot.web;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import ma.enset.bank.chatbot.dto.ChatRequest;
import ma.enset.bank.chatbot.dto.ChatResponse;
import ma.enset.bank.chatbot.dto.McpToolInfo;
import ma.enset.bank.chatbot.service.BankingAiAgentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatBotController {

    private final BankingAiAgentService agentService;

    public ChatBotController(BankingAiAgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/ask")
    public ChatResponse ask(@Valid @RequestBody ChatRequest request) {
        return agentService.chat(request);
    }

    @PostMapping("/confirm/{confirmationId}")
    public ChatResponse confirm(@PathVariable String confirmationId) {
        return agentService.confirm(confirmationId);
    }

    @GetMapping("/tools")
    public List<McpToolInfo> listMcpTools() {
        return agentService.listTools();
    }

    @PostMapping("/telegram/webhook")
    public Map<String, Object> telegramWebhook(@RequestBody Map<String, Object> update) {
        Object msgObj = update.get("message");
        String chatId = "telegram-default";
        String text = "";
        if (msgObj instanceof Map<?, ?> msgMap) {
            Object t = msgMap.get("text");
            if (t != null) {
                text = t.toString();
            }
            Object chatObj = msgMap.get("chat");
            if (chatObj instanceof Map<?, ?> chatMap && chatMap.get("id") != null) {
                chatId = "tg-" + chatMap.get("id");
            }
        }
        ChatResponse response = agentService.chat(new ChatRequest(chatId, text.isBlank() ? "aide" : text));
        return Map.of(
                "method", "sendMessage",
                "chat_id", chatId,
                "text", response.reply(),
                "requiresConfirmation", response.requiresConfirmation());
    }

    @PostMapping("/discord/webhook")
    public Map<String, Object> discordWebhook(@RequestBody Map<String, Object> payload) {
        String userId = payload.getOrDefault("userId", "discord-user").toString();
        String content = payload.getOrDefault("content", "aide").toString();
        ChatResponse response = agentService.chat(new ChatRequest("discord-" + userId, content));
        return Map.of(
                "content", response.reply(),
                "selectedMcpTool", response.selectedMcpTool() == null ? "" : response.selectedMcpTool(),
                "requiresConfirmation", response.requiresConfirmation());
    }
}
