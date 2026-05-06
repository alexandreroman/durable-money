package io.temporal.demos.durablemoney.workflow;

import io.temporal.spring.boot.ActivityImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@ActivityImpl(workers = "transfer")
class AccountActivitiesImpl implements AccountActivities {
    private final RestClient restClient;

    AccountActivitiesImpl(@Value("${account.service.url}") String baseUrl,
                          RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.clone().baseUrl(baseUrl).build();
    }

    @Override
    public DebitOutput debitAccount(DebitInput input) {
        var view = restClient.post()
                .uri("/accounts/{id}/debit", input.accountId())
                .body(new DebitCreditRequest(input.amount(), input.transferId()))
                .retrieve()
                .body(AccountView.class);
        return new DebitOutput(view.balance());
    }

    @Override
    public CreditOutput creditAccount(CreditInput input) {
        var view = restClient.post()
                .uri("/accounts/{id}/credit", input.accountId())
                .body(new DebitCreditRequest(input.amount(), input.transferId()))
                .retrieve()
                .body(AccountView.class);
        return new CreditOutput(view.balance());
    }

    @Override
    public ReverseDebitOutput reverseDebit(ReverseDebitInput input) {
        // Compensating action: credit the source account back to reverse a prior debit.
        var view = restClient.post()
                .uri("/accounts/{id}/credit", input.accountId())
                .body(new DebitCreditRequest(input.amount(), input.transferId()))
                .retrieve()
                .body(AccountView.class);
        return new ReverseDebitOutput(view.balance());
    }

    private record DebitCreditRequest(BigDecimal amount, UUID transferId) {
    }

    private record AccountView(UUID id, BigDecimal balance) {
    }
}
