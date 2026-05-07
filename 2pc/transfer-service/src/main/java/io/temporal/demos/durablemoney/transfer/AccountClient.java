package io.temporal.demos.durablemoney.transfer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

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
        callPrepare("/accounts/{id}/debit/prepare", accountId, amount, xid);
    }

    void prepareCredit(UUID accountId, BigDecimal amount, String xid) {
        callPrepare("/accounts/{id}/credit/prepare", accountId, amount, xid);
    }

    void commit(String xid) {
        restClient.post().uri("/xa/{xid}/commit", xid).retrieve().toBodilessEntity();
    }

    void rollback(String xid) {
        restClient.post().uri("/xa/{xid}/rollback", xid).retrieve().toBodilessEntity();
    }

    private void callPrepare(String uriTemplate, UUID accountId, BigDecimal amount, String xid) {
        try {
            restClient.post()
                    .uri(uriTemplate, accountId)
                    .body(new PrepareRequest(amount, xid))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            if (status.is4xxClientError()) {
                throw new TransferCoordinator.BusinessException(status.value(),
                        extractDetail(e, "prepare failed: " + status));
            }
            throw e;
        }
    }

    private static String extractDetail(RestClientResponseException e, String fallback) {
        var body = e.getResponseBodyAsString();
        return (body == null || body.isBlank()) ? fallback : body;
    }

    private record PrepareRequest(BigDecimal amount, String xid) {
    }
}
