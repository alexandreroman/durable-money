package io.temporal.demos.durablemoney.transfer;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class RabbitConfig {
    @Bean
    DirectExchange moneyExchange() {
        return new DirectExchange("money.exchange", true, false);
    }

    @Bean
    DirectExchange moneyDlx() {
        return new DirectExchange("money.dlx", true, false);
    }

    @Bean
    Queue transferResultsQueue() {
        return QueueBuilder.durable("transfer.results")
            .withArgument("x-dead-letter-exchange", "money.dlx")
            .withArgument("x-dead-letter-routing-key", "transfer.results.dlq")
            .build();
    }

    @Bean
    Queue transferResultsDlq() {
        return QueueBuilder.durable("transfer.results.dlq").build();
    }

    @Bean
    Binding transferResultsBinding(Queue transferResultsQueue, DirectExchange moneyExchange) {
        return BindingBuilder.bind(transferResultsQueue).to(moneyExchange).with("transfer.results");
    }

    @Bean
    Binding transferResultsDlqBinding(Queue transferResultsDlq, DirectExchange moneyDlx) {
        return BindingBuilder.bind(transferResultsDlq).to(moneyDlx).with("transfer.results.dlq");
    }

    @Bean
    MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
