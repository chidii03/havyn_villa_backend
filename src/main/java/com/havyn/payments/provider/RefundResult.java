package com.havyn.payments.provider;

public record RefundResult(String providerRefundRef, boolean succeeded) {
}
