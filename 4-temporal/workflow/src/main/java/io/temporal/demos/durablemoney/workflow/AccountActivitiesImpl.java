package io.temporal.demos.durablemoney.workflow;

import io.temporal.failure.ApplicationFailure;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Component
@ActivityImpl(workers = "transfer")
class AccountActivitiesImpl implements AccountActivities {
    private static final String INSUFFICIENT_FUNDS_TYPE = "INSUFFICIENT_FUNDS";

    private final RestClient restClient;

    AccountActivitiesImpl(@Value("${account.service.url}") String baseUrl,
                          RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.clone().baseUrl(baseUrl).build();
    }

    @Override
    public DebitOutput debitAccount(DebitInput input) {
        try {
            var view = restClient.post()
                    .uri("/accounts/{id}/debit", input.accountId())
                    .body(new DebitCreditRequest(input.transferId(), input.amount()))
                    .retrieve()
                    .body(AccountView.class);
            Objects.requireNonNull(view, "Empty response body from account-service");
            return new DebitOutput(view.balance());
        } catch (RestClientResponseException e) {
            // 422 means the account-service rejected the debit due to insufficient funds.
            // Surface it as non-retryable so the workflow compensates immediately instead of
            // burning retries on a deterministic failure.
            if (e.getStatusCode().value() == HttpStatus.UNPROCESSABLE_CONTENT.value()) {
                throw ApplicationFailure.newNonRetryableFailure("Insufficient funds", INSUFFICIENT_FUNDS_TYPE);
            }
            throw e;
        }
    }

    @Override
    public CreditOutput creditAccount(CreditInput input) {
        var view = restClient.post()
                .uri("/accounts/{id}/credit", input.accountId())
                .body(new DebitCreditRequest(input.transferId(), input.amount()))
                .retrieve()
                .body(AccountView.class);
        Objects.requireNonNull(view, "Empty response body from account-service");
        return new CreditOutput(view.balance());
    }

    @Override
    public ReverseDebitOutput reverseDebit(ReverseDebitInput input) {
        // Compensating action: distinct endpoint so the account-service records the operation
        // under a different idempotency key than the original debit.
        var view = restClient.post()
                .uri("/accounts/{id}/reverse-debit", input.accountId())
                .body(new DebitCreditRequest(input.transferId(), input.amount()))
                .retrieve()
                .body(AccountView.class);
        Objects.requireNonNull(view, "Empty response body from account-service");
        return new ReverseDebitOutput(view.balance());
    }

    private record DebitCreditRequest(UUID transferId, BigDecimal amount) {
    }

    private record AccountView(UUID id, BigDecimal balance) {
    }
}
