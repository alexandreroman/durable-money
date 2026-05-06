package io.temporal.demos.durablemoney.transfer;

import java.util.UUID;

record AccountResultMessage(UUID transferId, UUID accountId, String type, boolean success, String errorMessage) {}
