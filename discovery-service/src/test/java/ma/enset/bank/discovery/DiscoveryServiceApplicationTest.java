package ma.enset.bank.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DiscoveryServiceApplicationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void eurekaServerStartsAndExposesAppsEndpoint() {
        ResponseEntity<String> response = rest.getForEntity("/eureka/apps", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}