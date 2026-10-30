package ma.enset.bank.transaction.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ma.enset.bank.transaction.dto.TransactionResponse;
import ma.enset.bank.transaction.service.TransactionService;
import ma.enset.bank.transaction.service.exception.AccountServiceUnavailableException;
import ma.enset.bank.transaction.service.exception.TransactionNotFoundException;
import ma.enset.bank.transaction.service.exception.TransactionRuleException;
import ma.enset.bank.transaction.service.exception.TransactionRuleException.Violation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    TransactionService service;

    UUID id = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();

    String body(String amount) {
        return "{\"accountId\":\"" + accountId + "\",\"amount\":" + amount
                + ",\"currency\":\"EUR\",\"merchantCategory\":\"GROCERY\",\"country\":\"MA\"}";
    }

    TransactionResponse sample() {
        return new TransactionResponse(id, accountId, new BigDecimal("250.00"), "EUR", "GROCERY", "MA",
                Instant.parse("2026-01-15T10:30:00Z"));
    }

    private void postExpecting(int expected) throws Exception {
        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(body("250")))
                .andExpect(status().is(expected));
    }

    @Test
    void recordReturns201() throws Exception {
        when(service.record(any())).thenReturn(sample());
        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(body("250")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.amount").value(250.00));
    }

    @Test
    void missingFieldsReturn400() throws Exception {
        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void businessViolationsAreMappedToHttpStatuses() throws Exception {
        record Case(Violation violation, int status) { }
        for (Case c : List.of(new Case(Violation.INVALID_AMOUNT, 400), new Case(Violation.AMOUNT_LIMIT_EXCEEDED, 422),
                new Case(Violation.ACCOUNT_NOT_FOUND, 404), new Case(Violation.ACCOUNT_NOT_ACTIVE, 409),
                new Case(Violation.CURRENCY_MISMATCH, 422), new Case(Violation.INSUFFICIENT_FUNDS, 422))) {
            doThrow(new TransactionRuleException(c.violation(), "refused")).when(service).record(any());
            mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(body("250")))
                    .andExpect(status().is(c.status()))
                    .andExpect(jsonPath("$.violation").value(c.violation().name()));
        }
    }

    @Test
    void accountServiceFailureReturns503() throws Exception {
        when(service.record(any())).thenThrow(new AccountServiceUnavailableException("down", null));
        postExpecting(503);
    }

    @Test
    void getUnknownTransactionReturns404() throws Exception {
        when(service.get(id)).thenThrow(new TransactionNotFoundException(id));
        mvc.perform(get("/api/transactions/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void listAndListByAccountReturnTransactions() throws Exception {
        when(service.list()).thenReturn(List.of(sample()));
        when(service.listByAccount(accountId)).thenReturn(List.of(sample()));
        mvc.perform(get("/api/transactions")).andExpect(status().isOk()).andExpect(jsonPath("$[0].country").value("MA"));
        mvc.perform(get("/api/transactions/account/" + accountId)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountId").value(accountId.toString()));
    }
}