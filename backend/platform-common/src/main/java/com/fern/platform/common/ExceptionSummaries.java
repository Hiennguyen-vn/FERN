package com.fern.platform.common;

import java.util.ArrayList;
import java.util.List;

public final class ExceptionSummaries {
    private static final String UNEXPECTED_ERROR_MESSAGE = "An unexpected error occurred";
    private static final int MAX_CHAIN_DEPTH = 4;

    private ExceptionSummaries() {
    }

    public static String unexpectedErrorMessage() {
        return UNEXPECTED_ERROR_MESSAGE;
    }

    public static String safeSummary(Throwable throwable) {
        if (throwable == null) {
            return "UnknownException";
        }
        List<String> chain = new ArrayList<>();
        Throwable current = throwable;
        while (current != null && chain.size() < MAX_CHAIN_DEPTH) {
            String simpleName = current.getClass().getSimpleName();
            String summary = simpleName == null || simpleName.isBlank() ? current.getClass().getName() : simpleName;
            if (chain.isEmpty() || !chain.getLast().equals(summary)) {
                chain.add(summary);
            }
            current = current.getCause();
        }
        return String.join(" -> ", chain);
    }
}
