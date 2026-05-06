package io.temporal.demos.durablemoney.account;

import java.util.UUID;

record AccountResultMessage(UUID transferId, UUID accountId, CommandType type, boolean success, String errorMessage) {}
