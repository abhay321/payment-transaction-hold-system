
package com.abhay.payment.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Immutable representation of a monetary amount.
 *
 * Money is a Value Object:
 * - It has no unique identity.
 * - Equality is based on amount and currency.
 * - It cannot be modified after creation.
 */
public final class Money implements Comparable<Money> {

    private final BigDecimal amount;
    private final Currency currency;

    private Money(BigDecimal amount, Currency currency) {
        this.amount = validateAmount(amount, currency);
        this.currency = Objects.requireNonNull(
                currency,
                "Currency cannot be null"
        );
    }

    /**
     * Creates Money from a decimal string.
     */
    public static Money of(String amount, String currencyCode) {

        Objects.requireNonNull(amount, "Amount cannot be null");
        Objects.requireNonNull(
                currencyCode,
                "Currency code cannot be null"
        );

        Currency currency = Currency.getInstance(currencyCode);

        return of(new BigDecimal(amount), currency);
    }

    /**
     * Creates Money from BigDecimal and Currency.
     */
    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    private static BigDecimal validateAmount(
            BigDecimal amount,
            Currency currency
    ) {

        Objects.requireNonNull(amount, "Amount cannot be null");
        Objects.requireNonNull(currency, "Currency cannot be null");

        if (amount.scale() > currency.getDefaultFractionDigits()) {
            throw new IllegalArgumentException(
                    "Amount exceeds supported currency precision"
            );
        }

        return amount.setScale(
                currency.getDefaultFractionDigits(),
                RoundingMode.UNNECESSARY
        );
    }

    public BigDecimal amount() {
        return amount;
    }

    /** JavaBean accessor for JSON API serialization. */
    public BigDecimal getAmount() { return amount; }

    /** Stable ISO currency code for JSON API serialization. */
    public String getCurrencyCode() { return currency.getCurrencyCode(); }

    public Currency currency() {
        return currency;
    }

    public Money add(Money other) {

        validateCurrency(other);

        return new Money(
                amount.add(other.amount),
                currency
        );
    }

    public Money subtract(Money other) {

        validateCurrency(other);

        return new Money(
                amount.subtract(other.amount),
                currency
        );
    }

    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isNegative() {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }

    public boolean isGreaterThan(Money other) {
        validateCurrency(other);
        return amount.compareTo(other.amount) > 0;
    }

    public boolean isGreaterThanOrEqualTo(Money other) {
        validateCurrency(other);
        return amount.compareTo(other.amount) >= 0;
    }

    private void validateCurrency(Money other) {

        Objects.requireNonNull(other, "Money cannot be null");

        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: "
                            + currency.getCurrencyCode()
                            + " vs "
                            + other.currency.getCurrencyCode()
            );
        }
    }

    @Override
    public int compareTo(Money other) {
        validateCurrency(other);
        return amount.compareTo(other.amount);
    }

    @Override
    public boolean equals(Object object) {

        if (this == object) {
            return true;
        }

        if (!(object instanceof Money other)) {
            return false;
        }

        return amount.compareTo(other.amount) == 0
                && currency.equals(other.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros(), currency);
    }

    @Override
    public String toString() {
        return currency.getCurrencyCode() + " " + amount;
    }
}
