package com.kad.jet.finance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class PaystackAmountConverter {

    private PaystackAmountConverter() {
    }

    static BigDecimal normalizeNairaAmount(BigDecimal amountInNaira) {
        if (amountInNaira == null) {
            throw new IllegalArgumentException("Payment amount is required");
        }

        try {
            BigDecimal normalizedAmount =
                    amountInNaira.setScale(2, RoundingMode.UNNECESSARY);
            normalizedAmount.movePointRight(2).longValueExact();
            return normalizedAmount;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Payment amount must be exactly representable in Kobo", exception);
        }
    }

    static long toKobo(BigDecimal amountInNaira) {
        return normalizeNairaAmount(amountInNaira)
                .movePointRight(2)
                .longValueExact();
    }

    static BigDecimal toNaira(Object amountInKobo) {
        if (!(amountInKobo instanceof Number number)) {
            throw new IllegalArgumentException(
                    "Paystack amount must be a numeric Kobo value");
        }

        try {
            long kobo = new BigDecimal(number.toString()).longValueExact();
            return BigDecimal.valueOf(kobo, 2);
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Paystack amount must be an in-range integer Kobo value",
                    exception);
        }
    }
}
