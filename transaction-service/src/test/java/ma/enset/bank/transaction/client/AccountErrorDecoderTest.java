package ma.enset.bank.transaction.client;

import static org.assertj.core.api.Assertions.assertThat;

import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import ma.enset.bank.transaction.service.exception.AccountServiceUnavailableException;
import ma.enset.bank.transaction.service.exception.TransactionRuleException;
import ma.enset.bank.transaction.service.exception.TransactionRuleException.Violation;
import org.junit.jupiter.api.Test;

class AccountErrorDecoderTest {

    AccountErrorDecoder decoder = new AccountErrorDecoder();

    private Exception decode(int status) {
        Map<String, Collection<String>> headers = new HashMap<>();
        Request request = Request.create(Request.HttpMethod.GET, "http://account", headers, null, StandardCharsets.UTF_8, null);
        return decoder.decode("AccountClient#getAccount", Response.builder().status(status).request(request).headers(headers).build());
    }

    @Test
    void mapsBusinessStatuses() {
        assertThat(((TransactionRuleException) decode(404)).getViolation()).isEqualTo(Violation.ACCOUNT_NOT_FOUND);
        assertThat(((TransactionRuleException) decode(409)).getViolation()).isEqualTo(Violation.ACCOUNT_NOT_ACTIVE);
        assertThat(((TransactionRuleException) decode(422)).getViolation()).isEqualTo(Violation.INSUFFICIENT_FUNDS);
    }

    @Test
    void treatsAnyOtherStatusAsUnavailable() {
        assertThat(decode(500)).isInstanceOf(AccountServiceUnavailableException.class);
        assertThat(decode(503)).isInstanceOf(AccountServiceUnavailableException.class);
    }
}