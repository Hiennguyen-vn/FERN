package com.fern.platform.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Optional hook invoked from {@link FernGlobalExceptionHandler} for unhandled exceptions only
 * (after mapped handlers such as {@code ConflictException}).
 */
@FunctionalInterface
public interface UnhandledExceptionListener {
    void onUnhandled(HttpServletRequest request, Exception exception);
}
