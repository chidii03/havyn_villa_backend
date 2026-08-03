package com.havyn.payments.provider;

import com.havyn.common.error.BadRequestException;

public class InvalidWebhookSignatureException extends BadRequestException {

    public InvalidWebhookSignatureException() {
        super("INVALID_WEBHOOK_SIGNATURE", "Webhook signature verification failed");
    }
}
