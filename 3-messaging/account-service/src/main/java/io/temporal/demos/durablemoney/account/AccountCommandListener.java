package io.temporal.demos.durablemoney.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
class AccountCommandListener {

    private static final Logger log = LoggerFactory.getLogger(AccountCommandListener.class);

    private final AccountService accountService;
    private final RabbitTemplate rabbitTemplate;

    AccountCommandListener(AccountService accountService, RabbitTemplate rabbitTemplate) {
        this.accountService = accountService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = "account.commands")
    void handleCommand(AccountCommandMessage cmd) {
        AccountResultMessage result;
        try {
            if ("DEBIT".equals(cmd.type())) {
                accountService.debit(cmd.accountId(), cmd.amount());
            } else {
                accountService.credit(cmd.accountId(), cmd.amount());
            }
            result = new AccountResultMessage(cmd.transferId(), cmd.accountId(), cmd.type(), true, null);
            log.info("Processed {} for transfer {}", cmd.type(), cmd.transferId());
        } catch (Exception e) {
            log.warn("Failed to process {} for transfer {}: {}", cmd.type(), cmd.transferId(), e.getMessage());
            result = new AccountResultMessage(cmd.transferId(), cmd.accountId(), cmd.type(), false, e.getMessage());
        }
        rabbitTemplate.convertAndSend("money.exchange", "transfer.results", result);
    }
}
