
package com.abhay.payment.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    @Test
    void shouldCreateMoneySuccessfully() {

        Money money = Money.of("100.50", "INR");

        assertEquals("100.50", money.amount().toPlainString());
        assertEquals("INR", money.currency().getCurrencyCode());
    }

    @Test
    void shouldAddMoneySuccessfully() {

        Money first = Money.of("100.50", "INR");
        Money second = Money.of("200.25", "INR");

        Money result = first.add(second);

        assertEquals(Money.of("300.75", "INR"), result);
    }

    @Test
    void shouldSubtractMoneySuccessfully() {

        Money first = Money.of("500.00", "INR");
        Money second = Money.of("200.00", "INR");

        Money result = first.subtract(second);

        assertEquals(Money.of("300.00", "INR"), result);
    }

    @Test
    void shouldRejectCurrencyMismatch() {

        Money inr = Money.of("100", "INR");
        Money usd = Money.of("100", "USD");

        assertThrows(
                IllegalArgumentException.class,
                () -> inr.add(usd)
        );
    }

    @Test
    void shouldRejectExcessPrecision() {

        assertThrows(
                IllegalArgumentException.class,
                () -> Money.of("100.555", "INR")
        );
    }

    @Test
    void shouldSupportValueBasedEquality() {

        Money first = Money.of("100.0", "INR");
        Money second = Money.of("100.00", "INR");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void shouldIdentifyPositiveAmount() {

        Money money = Money.of("100", "INR");

        assertTrue(money.isPositive());
        assertFalse(money.isZero());
    }

    @Test
    void shouldIdentifyZeroAmount() {

        Money money = Money.of("0", "INR");

        assertTrue(money.isZero());
        assertFalse(money.isPositive());
    }

    @Test
    void shouldNotAllowNegativeAmountToBeIdentifiedAsPositive() {

        Money money = Money.of("-100", "INR");

        assertTrue(money.isNegative());
        assertFalse(money.isPositive());
    }
}
