package ma.enset.bank.chatbot.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import ma.enset.bank.chatbot.dto.ChatResponse;
import ma.enset.bank.chatbot.dto.McpToolInfo;
import ma.enset.bank.chatbot.service.BankingAiAgentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChatBotController.class)
class ChatBotControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    BankingAiAgentService agentService;

    @Test
    void askEndpointReturnsAgentReply() throws Exception {
        when(agentService.chat(any())).thenReturn(
                new ChatResponse("s1", "Voici vos comptes", "list_accounts", false, null, "[]"));
        mvc.perform(post("/api/chat/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"s1\",\"message\":\"Quels sont mes comptes ?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedMcpTool").value("list_accounts"));
    }

    @Test
    void telegramAndDiscordWebhooksInvokeAgent() throws Exception {
        when(agentService.chat(any())).thenReturn(
                new ChatResponse("tg-123", "Solde: 1650 EUR", "get_account_balance", false, null, "1650"));

        mvc.perform(post("/api/chat/telegram/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":{\"chat\":{\"id\":123},\"text\":\"Quel est mon solde ?\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Solde: 1650 EUR"));

        mvc.perform(post("/api/chat/discord/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"u1\",\"content\":\"Quel est mon solde ?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Solde: 1650 EUR"));
    }

    @Test
    void toolsEndpointListsDiscoveredMcpTools() throws Exception {
        when(agentService.listTools()).thenReturn(
                List.of(new McpToolInfo("account-service", "list_accounts", "List all bank accounts")));
        mvc.perform(get("/api/chat/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("list_accounts"));
    }
}
