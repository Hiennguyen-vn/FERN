package com.fern.posservice.service;

public class DownstreamServiceUnavailableException extends RuntimeException {
    public DownstreamServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
