package ma.enset.bank.transaction;

import ma.enset.bank.transaction.config.BankTransactionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
@EnableConfigurationProperties(BankTransactionProperties.class)
public class TransactionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionServiceApplication.class, args);
    }
}