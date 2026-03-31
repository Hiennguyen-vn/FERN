package com.fern.platform.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ListQueryDefaultsTest {

    @Test
    void clampLimit_nullReturnsDefault() {
        assertThat(ListQueryDefaults.clampLimit(null)).isEqualTo(ListQueryDefaults.DEFAULT_LIMIT);
    }

    @Test
    void clampLimit_zeroReturnsDefault() {
        assertThat(ListQueryDefaults.clampLimit(0)).isEqualTo(ListQueryDefaults.DEFAULT_LIMIT);
    }

    @Test
    void clampLimit_negativeReturnsDefault() {
        assertThat(ListQueryDefaults.clampLimit(-5)).isEqualTo(ListQueryDefaults.DEFAULT_LIMIT);
    }

    @Test
    void clampLimit_withinRangeReturnsRequested() {
        assertThat(ListQueryDefaults.clampLimit(50)).isEqualTo(50);
    }

    @Test
    void clampLimit_atMaxReturnsMax() {
        assertThat(ListQueryDefaults.clampLimit(ListQueryDefaults.MAX_LIMIT)).isEqualTo(ListQueryDefaults.MAX_LIMIT);
    }

    @Test
    void clampLimit_overMaxClampsToMax() {
        assertThat(ListQueryDefaults.clampLimit(5000)).isEqualTo(ListQueryDefaults.MAX_LIMIT);
    }

    @ParameterizedTest
    @CsvSource({
            ", 20, 0",       // null page → offset 0
            "-1, 20, 0",     // negative page → offset 0
            "0, 20, 0",      // page 0 → offset 0
            "1, 20, 20",     // page 1 → offset 20
            "5, 50, 250"     // page 5, size 50 → offset 250
    })
    void offsetFrom_calculatesCorrectly(Integer page, int size, long expected) {
        assertThat(ListQueryDefaults.offsetFrom(page, size)).isEqualTo(expected);
    }
}
