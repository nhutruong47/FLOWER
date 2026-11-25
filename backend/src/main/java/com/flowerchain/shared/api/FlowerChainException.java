package com.flowerchain.shared.api;

import org.springframework.http.HttpStatus;

public class FlowerChainException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;
    private final String title;

    public FlowerChainException(HttpStatus status, String errorCode, String title, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.title = title;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getTitle() {
        return title;
    }

    public static FlowerChainException badRequest(String errorCode, String message) {
        return new FlowerChainException(HttpStatus.BAD_REQUEST, errorCode, "Invalid request", message);
    }

    public static FlowerChainException unauthorized(String errorCode, String message) {
        return new FlowerChainException(HttpStatus.UNAUTHORIZED, errorCode, "Unauthorized", message);
    }

    public static FlowerChainException forbidden(String errorCode, String message) {
        return new FlowerChainException(HttpStatus.FORBIDDEN, errorCode, "Access denied", message);
    }

    public static FlowerChainException notFound(String message) {
        return new FlowerChainException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource not found", message);
    }

    public static FlowerChainException conflict(String errorCode, String title, String message) {
        return new FlowerChainException(HttpStatus.CONFLICT, errorCode, title, message);
    }

    public static FlowerChainException unprocessable(String errorCode, String title, String message) {
        return new FlowerChainException(HttpStatus.UNPROCESSABLE_ENTITY, errorCode, title, message);
    }
}
