package io.temporal.demos.durablemoney.workflow;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

@Component
class AccountActivitiesImpl implements AccountActivities {

    private final RestClient restClient;

    AccountActivitiesImpl(RestClient accountRestClient) {
        this.restClient = accountRestClient;
    }

    record DebitCreditRequest(BigDecimal amount, UUID transferId) {}

    @Override
    public void debitAccount(UUID accountId, BigDecimal amount, UUID transferId) {
        restClient.post()
            .uri("/accounts/{id}/debit", accountId)
            .body(new DebitCreditRequest(amount, transferId))
            .retrieve()
            .toBodilessEntity();
    }

    @Override
    public void creditAccount(UUID accountId, BigDecimal amount, UUID transferId) {
        restClient.post()
            .uri("/accounts/{id}/credit", accountId)
            .body(new DebitCreditRequest(amount, transferId))
            .retrieve()
            .toBodilessEntity();
    }

    @Override
    public void reverseDebit(UUID accountId, BigDecimal amount, UUID transferId) {
        // Compensating action: credit the source account back to reverse a prior debit
        restClient.post()
            .uri("/accounts/{id}/credit", accountId)
            .body(new DebitCreditRequest(amount, transferId))
            .retrieve()
            .toBodilessEntity();
    }
}
