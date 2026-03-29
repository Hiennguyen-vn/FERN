package com.fern.platform.common;

import java.time.Instant;
import java.util.Objects;
import java.util.function.LongSupplier;

public final class SnowflakeIdGenerator {
    public static final long DEFAULT_EPOCH_MILLIS = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();
    public static final long DEFAULT_MAX_CLOCK_ROLLBACK_MILLIS = 5L;

    private static final int NODE_ID_BITS = 10;
    private static final int SEQUENCE_BITS = 12;
    private static final long MAX_NODE_ID = (1L << NODE_ID_BITS) - 1;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
    private static final int TIMESTAMP_SHIFT = NODE_ID_BITS + SEQUENCE_BITS;
    private static final int NODE_ID_SHIFT = SEQUENCE_BITS;

    private final long epochMillis;
    private final long nodeId;
    private final long maxClockRollbackMillis;
    private final LongSupplier timeSupplier;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(long nodeId) {
        this(DEFAULT_EPOCH_MILLIS, nodeId, DEFAULT_MAX_CLOCK_ROLLBACK_MILLIS, System::currentTimeMillis);
    }

    public SnowflakeIdGenerator(long epochMillis, long nodeId, long maxClockRollbackMillis) {
        this(epochMillis, nodeId, maxClockRollbackMillis, System::currentTimeMillis);
    }

    SnowflakeIdGenerator(long epochMillis, long nodeId, long maxClockRollbackMillis, LongSupplier timeSupplier) {
        if (nodeId < 0 || nodeId > MAX_NODE_ID) {
            throw new IllegalArgumentException("nodeId must be between 0 and " + MAX_NODE_ID);
        }
        if (maxClockRollbackMillis < 0) {
            throw new IllegalArgumentException("maxClockRollbackMillis must be >= 0");
        }
        this.epochMillis = epochMillis;
        this.nodeId = nodeId;
        this.maxClockRollbackMillis = maxClockRollbackMillis;
        this.timeSupplier = Objects.requireNonNull(timeSupplier, "timeSupplier must not be null");
    }

    public synchronized long nextId() {
        long timestamp = currentTimestamp();
        if (timestamp < lastTimestamp) {
            long rollback = lastTimestamp - timestamp;
            if (rollback > maxClockRollbackMillis) {
                throw new IllegalStateException("Clock moved backwards by " + rollback + "ms");
            }
            timestamp = lastTimestamp;
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                timestamp = waitForNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;
        return ((timestamp - epochMillis) << TIMESTAMP_SHIFT)
                | (nodeId << NODE_ID_SHIFT)
                | sequence;
    }

    long extractTimestamp(long id) {
        return (id >>> TIMESTAMP_SHIFT) + epochMillis;
    }

    long extractNodeId(long id) {
        return (id >>> NODE_ID_SHIFT) & MAX_NODE_ID;
    }

    long extractSequence(long id) {
        return id & MAX_SEQUENCE;
    }

    private long waitForNextMillis(long previousTimestamp) {
        long timestamp = currentTimestamp();
        while (timestamp <= previousTimestamp) {
            timestamp = currentTimestamp();
        }
        return timestamp;
    }

    private long currentTimestamp() {
        long timestamp = timeSupplier.getAsLong();
        if (timestamp < epochMillis) {
            throw new IllegalStateException("Current time is before configured epoch");
        }
        return timestamp;
    }
}
