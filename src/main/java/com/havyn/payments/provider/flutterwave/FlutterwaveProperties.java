package com.havyn.payments.provider.flutterwave;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Server-only Flutterwave Standard configuration. */
@ConfigurationProperties(prefix = "havyn.payments.flutterwave")
public class FlutterwaveProperties {

    private String secretKey = "";
    private String webhookHash = "";
    private String baseUrl = "https://api.flutterwave.com/v3";
    private String redirectUrl = "";

    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getWebhookHash() { return webhookHash; }
    public void setWebhookHash(String webhookHash) { this.webhookHash = webhookHash; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getRedirectUrl() { return redirectUrl; }
    public void setRedirectUrl(String redirectUrl) { this.redirectUrl = redirectUrl; }
}
