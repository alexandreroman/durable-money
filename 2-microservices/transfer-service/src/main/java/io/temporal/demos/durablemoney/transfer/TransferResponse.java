package io.temporal.demos.durablemoney.transfer;

import java.util.UUID;

record TransferResponse(UUID transferId, String status, String message) {}
