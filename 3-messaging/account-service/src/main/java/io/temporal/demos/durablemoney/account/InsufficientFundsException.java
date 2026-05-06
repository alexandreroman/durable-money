package io.temporal.demos.durablemoney.account;

class InsufficientFundsException extends RuntimeException {
    InsufficientFundsException(String message) {
        super(message);
    }
}
