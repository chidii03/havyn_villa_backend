package com.havyn.payments.provider;

import java.math.BigDecimal;

public record RefundRequest(String providerRef, BigDecimal amount, String reason) {
}
