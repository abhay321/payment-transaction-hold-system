
package com.abhay.payment.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    @Test
    void shouldCreateMoneySuccessfully() {
        Money money = Money.of("100.50", "INR");

        assertEquals("100.50", money.amount().toPlainString());
        assertEquals("INR", money.currency().getCurrencyCode());
    }

    @Test
    void shouldCreateMoneyFromBigDecimal() {
        Money money = Money.of(
                new BigDecimal("250.50"),
                Currency.getInstance("INR")
        );

        assertEquals(new BigDecimal("250.50"), money.amount());
    }

    @Test
    void shouldRejectNullAmountString() {
        assertThrows(
                NullPointerException.class,
                () -> Money.of(null, "INR")
        );
    }

    @Test
    void shouldRejectNullCurrencyCode() {
        assertThrows(
                NullPointerException.class,
                () -> Money.of("100", null)
        );
    }

    @Test
    void shouldRejectInvalidCurrencyCode() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Money.of("100", "INVALID")
        );
    }

    @Test
    void shouldRejectNullBigDecimal() {
        assertThrows(
                NullPointerException.class,
                () -> Money.of(null, Currency.getInstance("INR"))
        );
    }

    @Test
    void shouldRejectNullCurrency() {
        assertThrows(
                NullPointerException.class,
                () -> Money.of(new BigDecimal("100"), null)
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
    void shouldReturnAmountSuccessfully() {
        Money money = Money.of("100.50", "INR");

        assertEquals(
                new BigDecimal("100.50"),
                money.amount()
        );
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
    void shouldRejectCurrencyMismatchDuringArithmetic() {
        Money inr = Money.of("100", "INR");
        Money usd = Money.of("100", "USD");

        assertThrows(
                IllegalArgumentException.class,
                () -> inr.add(usd)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> inr.subtract(usd)
        );
    }

    @Test
    void shouldIdentifyPositiveAmount() {
        Money money = Money.of("100", "INR");

        assertTrue(money.isPositive());
        assertFalse(money.isZero());
        assertFalse(money.isNegative());
    }

    @Test
    void shouldIdentifyZeroAmount() {
        Money money = Money.of("0", "INR");

        assertTrue(money.isZero());
        assertFalse(money.isPositive());
        assertFalse(money.isNegative());
    }

    @Test
    void shouldIdentifyNegativeAmount() {
        Money money = Money.of("-100", "INR");

        assertTrue(money.isNegative());
        assertFalse(money.isPositive());
        assertFalse(money.isZero());
    }

    @Test
    void shouldCompareMoneyUsingGreaterThan() {
        Money amount = Money.of("100", "INR");

        assertTrue(amount.isGreaterThan(Money.of("90", "INR")));
        assertFalse(amount.isGreaterThan(Money.of("100", "INR")));
        assertFalse(amount.isGreaterThan(Money.of("110", "INR")));
    }

    @Test
    void shouldCompareMoneyUsingGreaterThanOrEqualTo() {
        Money amount = Money.of("100", "INR");

        assertTrue(amount.isGreaterThanOrEqualTo(Money.of("100", "INR")));
        assertTrue(amount.isGreaterThanOrEqualTo(Money.of("90", "INR")));
        assertFalse(amount.isGreaterThanOrEqualTo(Money.of("110", "INR")));
    }

    @Test
    void shouldCompareMoneyUsingCompareTo() {
        Money amount = Money.of("100", "INR");

        assertTrue(amount.compareTo(Money.of("90", "INR")) > 0);
        assertEquals(0, amount.compareTo(Money.of("100", "INR")));
        assertTrue(amount.compareTo(Money.of("110", "INR")) < 0);
    }

    @Test
    void shouldRejectCurrencyMismatchDuringComparison() {
        Money inr = Money.of("100", "INR");
        Money usd = Money.of("100", "USD");

        assertThrows(
                IllegalArgumentException.class,
                () -> inr.isGreaterThan(usd)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> inr.isGreaterThanOrEqualTo(usd)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> inr.compareTo(usd)
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
    void shouldHandleMoneyEqualityEdgeCases() {
        Money money = Money.of("100.00", "INR");

        assertEquals(money, money);
        assertNotEquals(money, null);
        assertNotEquals(money, "100.00");

        assertNotEquals(
                money,
                Money.of("200.00", "INR")
        );

        assertNotEquals(
                money,
                Money.of("100.00", "USD")
        );
    }

    @Test
    void shouldGenerateConsistentHashCode() {
        Money first = Money.of("100.0", "INR");
        Money second = Money.of("100.00", "INR");

        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void shouldReturnReadableStringRepresentation() {
        Money money = Money.of("100.50", "INR");

        assertEquals("INR 100.50", money.toString());
    }
}
