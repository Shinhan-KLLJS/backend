package com.shinhan.klljs.global.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHasherTest {

    @Test
    void sha256_isDeterministicAndThirtyTwoBytes() {
        byte[] first = TokenHasher.sha256("same-input");
        byte[] second = TokenHasher.sha256("same-input");

        assertThat(first).hasSize(32);
        assertThat(first).isEqualTo(second);
    }

    @Test
    void sha256_differsForDifferentInput() {
        byte[] a = TokenHasher.sha256("input-a");
        byte[] b = TokenHasher.sha256("input-b");

        assertThat(a).isNotEqualTo(b);
    }
}
