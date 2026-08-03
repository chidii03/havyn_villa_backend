package com.havyn.payments.provider.paystack;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code havyn.payments.paystack.*}. Paystack signs webhooks with this same secret key — there is no separate webhook secret (see .env.example's note). */
@ConfigurationProperties(prefix = "havyn.payments.paystack")
public class PaystackProperties {

    private String secretKey = "";
    private String baseUrl = "https://api.paystack.co";
    private String callbackUrl = "";

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }
}
