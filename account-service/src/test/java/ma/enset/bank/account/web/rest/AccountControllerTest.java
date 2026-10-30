package ma.enset.bank.account.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import ma.enset.bank.account.domain.AccountStatus;
import ma.enset.bank.account.dto.AccountResponse;
import ma.enset.bank.account.service.AccountService;
import ma.enset.bank.account.service.exception.AccountBlockedException;
import ma.enset.bank.account.service.exception.AccountNotFoundException;
import ma.enset.bank.account.service.exception.InsufficientFundsException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({AccountController.class, InternalAccountController.class})
class AccountControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AccountService service;

    UUID id = UUID.randomUUID();

    private AccountResponse sample() {
        return new AccountResponse(id, "CUST-1", AccountStatus.ACTIVE, "EUR", new BigDecimal("100.00"));
    }

    @Test
    void createReturns201() throws Exception {
        when(service.create(any())).thenReturn(sample());
        mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerReference\":\"CUST-1\",\"currency\":\"EUR\",\"initialBalance\":100}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.balance").value(100.00));
    }

    @Test
    void createWithInvalidBodyReturns400() throws Exception {
        mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerReference\":\"\",\"currency\":\"euro\",\"initialBalance\":-5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listReturnsAccounts() throws Exception {
        when(service.list()).thenReturn(List.of(sample()));
        mvc.perform(get("/api/accounts")).andExpect(status().isOk()).andExpect(jsonPath("$[0].currency").value("EUR"));
    }

    @Test
    void unknownAccountReturns404() throws Exception {
        when(service.get(id)).thenThrow(new AccountNotFoundException(id));
        mvc.perform(get("/api/accounts/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void balanceIsReturned() throws Exception {
        when(service.getBalance(id)).thenReturn(new BigDecimal("42.00"));
        mvc.perform(get("/api/accounts/" + id + "/balance")).andExpect(status().isOk()).andExpect(jsonPath("$").value(42.00));
    }

    @Test
    void blockAndUnblockReturn200() throws Exception {
        when(service.block(id)).thenReturn(sample());
        when(service.unblock(id)).thenReturn(sample());
        mvc.perform(post("/api/accounts/" + id + "/block")).andExpect(status().isOk());
        mvc.perform(post("/api/accounts/" + id + "/unblock")).andExpect(status().isOk());
    }

    @Test
    void statusChangeAcceptsKnownStatusOnly() throws Exception {
        when(service.changeStatus(id, AccountStatus.BLOCKED)).thenReturn(sample());
        mvc.perform(patch("/api/accounts/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"BLOCKED\"}")).andExpect(status().isOk());
        mvc.perform(patch("/api/accounts/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CLOSED\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void internalDebitMapsBusinessErrors() throws Exception {
        when(service.debit(any(), any())).thenThrow(new InsufficientFundsException(id));
        mvc.perform(post("/internal/accounts/" + id + "/debit").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":500}")).andExpect(status().isUnprocessableEntity());
        when(service.credit(any(), any())).thenThrow(new AccountBlockedException(id));
        mvc.perform(post("/internal/accounts/" + id + "/credit").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":5}")).andExpect(status().isConflict());
    }

    @Test
    void internalDebitSucceeds() throws Exception {
        when(service.debit(any(), any())).thenReturn(sample());
        mvc.perform(post("/internal/accounts/" + id + "/debit").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":10}")).andExpect(status().isOk());
    }
}