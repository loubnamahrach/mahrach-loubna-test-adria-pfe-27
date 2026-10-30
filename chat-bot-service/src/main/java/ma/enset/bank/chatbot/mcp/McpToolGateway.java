package ma.enset.bank.chatbot.mcp;

import java.util.List;
import java.util.Map;
import ma.enset.bank.chatbot.dto.McpToolInfo;

public interface McpToolGateway {

    List<McpToolInfo> listAvailableTools();

    McpInvocationResult invokeTool(String toolName, Map<String, Object> arguments);

    record McpInvocationResult(boolean isError, String content) {
    }
}
