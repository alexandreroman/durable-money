package io.temporal.demos.durablemoney.transfer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

@Component
class AccountClient {
    private final RestClient restClient;

    AccountClient(@Value("${account.service.url}") String baseUrl,
                  RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.clone().baseUrl(baseUrl).build();
    }

    void prepareDebit(UUID accountId, BigDecimal amount, String xid) {
        restClient.post()
                .uri("/accounts/{id}/debit/prepare", accountId)
                .body(new PrepareRequest(amount, xid))
                .retrieve()
                .toBodilessEntity();
    }

    void prepareCredit(UUID accountId, BigDecimal amount, String xid) {
        restClient.post()
                .uri("/accounts/{id}/credit/prepare", accountId)
                .body(new PrepareRequest(amount, xid))
                .retrieve()
                .toBodilessEntity();
    }

    void commit(String xid) {
        restClient.post()
                .uri("/xa/{xid}/commit", xid)
                .retrieve()
                .toBodilessEntity();
    }

    void rollback(String xid) {
        restClient.post()
                .uri("/xa/{xid}/rollback", xid)
                .retrieve()
                .toBodilessEntity();
    }

    private record PrepareRequest(BigDecimal amount, String xid) {
    }
}
