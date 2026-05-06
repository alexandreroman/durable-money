package io.temporal.demos.durablemoney.workflow;

import java.util.UUID;

public record TransferResult(UUID transferId, String status, String errorMessage) {}
