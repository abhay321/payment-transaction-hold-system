
package com.abhay.payment.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionStatusTest {

    @Test
    void shouldIdentifyTerminalStatuses() {
        assertThat(TransactionStatus.CAPTURED.isTerminal()).isTrue();
        assertThat(TransactionStatus.RELEASED.isTerminal()).isTrue();
        assertThat(TransactionStatus.EXPIRED.isTerminal()).isTrue();
    }

    @Test
    void shouldIdentifyNonTerminalStatuses() {
        assertThat(TransactionStatus.CREATED.isTerminal()).isFalse();
        assertThat(TransactionStatus.HELD.isTerminal()).isFalse();
        assertThat(TransactionStatus.PARTIALLY_CAPTURED.isTerminal()).isFalse();
        assertThat(TransactionStatus.PARTIALLY_RELEASED.isTerminal()).isFalse();
    }
}
