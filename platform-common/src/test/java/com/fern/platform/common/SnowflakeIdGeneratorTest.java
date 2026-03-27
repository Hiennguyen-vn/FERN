package com.fern.platform.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class SnowflakeIdGeneratorTest {
    private static final long EPOCH = 1_735_689_600_000L;

    @Test
    void shouldGenerateMonotonicIds() {
        AtomicLong clock = new AtomicLong(EPOCH + 100);
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(EPOCH, 7, 5, clock::get);

        long first = generator.nextId();
        clock.incrementAndGet();
        long second = generator.nextId();

        assertThat(second).isGreaterThan(first);
        assertThat(generator.extractNodeId(first)).isEqualTo(7L);
        assertThat(generator.extractTimestamp(second)).isEqualTo(EPOCH + 101);
    }

    @Test
    void shouldNotDuplicateWithinSameMillisecond() {
        AtomicLong clock = new AtomicLong(EPOCH + 200);
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(EPOCH, 3, 5, clock::get);

        long first = generator.nextId();
        long second = generator.nextId();

        assertThat(second).isGreaterThan(first);
        assertThat(generator.extractSequence(first)).isEqualTo(0L);
        assertThat(generator.extractSequence(second)).isEqualTo(1L);
    }

    @Test
    void shouldRollOverSequenceIntoNextMillisecond() {
        AtomicLong clock = new AtomicLong(EPOCH + 300);
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(EPOCH, 1, 5, new AdvancingTimeSupplier(clock, 4_097));

        long lastSameMillis = -1L;
        for (int index = 0; index < 4_096; index++) {
            lastSameMillis = generator.nextId();
        }
        clock.incrementAndGet();
        long next = generator.nextId();

        assertThat(generator.extractTimestamp(lastSameMillis)).isEqualTo(EPOCH + 300);
        assertThat(generator.extractSequence(lastSameMillis)).isEqualTo(4_095L);
        assertThat(generator.extractTimestamp(next)).isEqualTo(EPOCH + 301);
        assertThat(generator.extractSequence(next)).isEqualTo(0L);
    }

    @Test
    void shouldTolerateSmallClockRollbackWithinThreshold() {
        AtomicLong clock = new AtomicLong(EPOCH + 400);
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(EPOCH, 8, 5, clock::get);

        long first = generator.nextId();
        clock.addAndGet(-2);
        long second = generator.nextId();

        assertThat(second).isGreaterThan(first);
        assertThat(generator.extractTimestamp(second)).isEqualTo(generator.extractTimestamp(first));
    }

    @Test
    void shouldFailFastWhenClockRollbackExceedsThreshold() {
        AtomicLong clock = new AtomicLong(EPOCH + 500);
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(EPOCH, 9, 1, clock::get);
        generator.nextId();

        clock.addAndGet(-5);

        assertThatThrownBy(generator::nextId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Clock moved backwards");
    }

    private static final class AdvancingTimeSupplier implements java.util.function.LongSupplier {
        private final AtomicLong clock;
        private final int rolloverIndex;
        private final AtomicInteger calls = new AtomicInteger();

        private AdvancingTimeSupplier(AtomicLong clock, int rolloverIndex) {
            this.clock = clock;
            this.rolloverIndex = rolloverIndex;
        }

        @Override
        public long getAsLong() {
            int call = calls.getAndIncrement();
            if (call > rolloverIndex) {
                clock.compareAndSet(EPOCH + 300, EPOCH + 301);
                return clock.get();
            }
            return clock.get();
        }
    }
}
