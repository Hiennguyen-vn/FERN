package com.fern.platform.common;

/**
 * Shared defaults for list/query endpoints to prevent unbounded result sets.
 *
 * <p>Usage in controllers or services:
 * <pre>{@code
 * int safeLimit = ListQueryDefaults.clampLimit(requestedLimit);
 * long safeOffset = ListQueryDefaults.offsetFrom(page, safeLimit);
 * }</pre>
 *
 * <p>This is a non-breaking addition — existing endpoints that don't use these
 * methods continue to work. Controllers can adopt gradually.
 */
public final class ListQueryDefaults {

    /** Default number of items returned if no limit is specified. */
    public static final int DEFAULT_LIMIT = 200;

    /** Absolute maximum to prevent OOM from a single query. */
    public static final int MAX_LIMIT = 1000;

    private ListQueryDefaults() {
    }

    /**
     * Clamps the requested limit to a safe range.
     *
     * @param requestedLimit the limit from the query parameter, may be null
     * @return a value between 1 and {@link #MAX_LIMIT}
     */
    public static int clampLimit(Integer requestedLimit) {
        if (requestedLimit == null || requestedLimit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requestedLimit, MAX_LIMIT);
    }

    /**
     * Calculates the offset for a given page and page size.
     *
     * @param page zero-based page number, defaults to 0 if null or negative
     * @param size number of items per page (already clamped)
     * @return the SQL offset value
     */
    public static long offsetFrom(Integer page, int size) {
        int safePage = (page == null || page < 0) ? 0 : page;
        return (long) safePage * size;
    }
}
