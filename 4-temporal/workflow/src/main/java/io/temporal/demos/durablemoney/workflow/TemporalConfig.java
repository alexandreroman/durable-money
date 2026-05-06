package io.temporal.demos.durablemoney.workflow;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
class TemporalConfig {

    static final String TASK_QUEUE = "MONEY_TRANSFER";

    @Bean
    WorkflowServiceStubs workflowServiceStubs(
            @Value("${temporal.host:localhost}") String host) {
        return WorkflowServiceStubs.newServiceStubs(
            WorkflowServiceStubsOptions.newBuilder()
                .setTarget(host + ":7233")
                .build());
    }

    @Bean
    WorkflowClient workflowClient(WorkflowServiceStubs stubs) {
        return WorkflowClient.newInstance(stubs);
    }

    @Bean
    WorkerFactory workerFactory(WorkflowClient client) {
        return WorkerFactory.newInstance(client);
    }

    @Bean
    Worker transferWorker(WorkerFactory factory, AccountActivitiesImpl activities) {
        var worker = factory.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(TransferWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        return worker;
    }

    @Bean
    RestClient accountRestClient(
            @Value("${account.service.url:http://localhost:8081}") String baseUrl) {
        var settings = HttpClientSettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(5))
                .withReadTimeout(Duration.ofSeconds(10));
        var requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }
}
