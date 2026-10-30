package ma.enset.bank.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.config.enabled=false"
})
class GatewayRoutesTest {

    @Autowired
    RouteDefinitionLocator locator;

    @Test
    void loadsRoutesForAccountTransactionAndChatBotServices() {
        List<String> routeIds = locator.getRouteDefinitions()
                .map(RouteDefinition::getId)
                .collectList()
                .block();
        assertThat(routeIds).contains(
                "account-service-rest",
                "account-service-graphql",
                "transaction-service-rest",
                "transaction-service-graphql",
                "chat-bot-service-rest");
    }
}