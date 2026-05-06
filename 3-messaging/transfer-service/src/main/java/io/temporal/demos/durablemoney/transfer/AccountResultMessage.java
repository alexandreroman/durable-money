package io.temporal.demos.durablemoney.transfer;

import java.util.UUID;

record AccountResultMessage(UUID transferId, UUID accountId, CommandType type, boolean success, String errorMessage) {}
