package com.havyn.payments.provider.paystack;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code havyn.payments.paystack.*}. Paystack signs webhooks with this same secret key — there is no separate webhook secret (see .env.example's note). */
@ConfigurationProperties(prefix = "havyn.payments.paystack")
public class PaystackProperties {

    private String secretKey = "";
    private String baseUrl = "https://api.paystack.co";
    private String callbackUrl = "";
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(15);

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

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}
