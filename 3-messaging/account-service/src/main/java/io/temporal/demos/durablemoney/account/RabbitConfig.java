package io.temporal.demos.durablemoney.account;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
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
    Queue accountCommandsQueue() {
        return QueueBuilder.durable("account.commands")
            .withArgument("x-dead-letter-exchange", "money.dlx")
            .withArgument("x-dead-letter-routing-key", "account.commands.dlq")
            .build();
    }

    @Bean
    Queue accountCommandsDlq() {
        return QueueBuilder.durable("account.commands.dlq").build();
    }

    @Bean
    Binding accountCommandsBinding(Queue accountCommandsQueue, DirectExchange moneyExchange) {
        return BindingBuilder.bind(accountCommandsQueue).to(moneyExchange).with("account.commands");
    }

    @Bean
    Binding accountCommandsDlqBinding(Queue accountCommandsDlq, DirectExchange moneyDlx) {
        return BindingBuilder.bind(accountCommandsDlq).to(moneyDlx).with("account.commands.dlq");
    }

    @Bean
    MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
