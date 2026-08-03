package com.havyn.payments.provider;

import java.math.BigDecimal;

public record WebhookEvent(WebhookEventType type, String providerRef, BigDecimal amount, String currency, String rawBody) {
}
