package com.rootcause.foshol.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Uuid7Test {

    @Test
    void createProducesUniqueVersion7Ids() {
        var first = Uuid7.create();
        var second = Uuid7.create();
        assertThat(first).isNotEqualTo(second);
        assertThat(first.version()).isEqualTo(7);
        assertThat(second.version()).isEqualTo(7);
    }
}
