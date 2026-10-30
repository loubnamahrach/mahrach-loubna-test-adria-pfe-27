package ma.enset.bank.transaction.client;

import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

public class AccountClientConfig {

    @Bean
    ErrorDecoder accountErrorDecoder() {
        return new AccountErrorDecoder();
    }
}