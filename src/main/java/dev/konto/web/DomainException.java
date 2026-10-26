package dev.konto.web;

import org.springframework.http.HttpStatus;

public class DomainException extends RuntimeException {

    private final HttpStatus status;

    private DomainException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static DomainException notFound(String what, Object id) {
        return new DomainException(HttpStatus.NOT_FOUND, what + " " + id + " not found");
    }

    public static DomainException insufficientFunds(Long accountId) {
        return new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds in account " + accountId);
    }

    public static DomainException conflict(String message) {
        return new DomainException(HttpStatus.CONFLICT, message);
    }

    public static DomainException invalid(String message) {
        return new DomainException(HttpStatus.BAD_REQUEST, message);
    }
}
