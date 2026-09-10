package com.electronics.store.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
@ConfigurationProperties(prefix = "app.vnpay")
@Getter
@Setter
public class VnPayProperties {
    public static final String SANDBOX_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    private boolean enabled;
    private String tmnCode = "";
    private String hashSecret = "";
    private String paymentUrl = "";
    private String returnUrl = "";
    // Registered with VNPAY, not sent as a payment query parameter.
    private String ipnUrl = "";
    private String version = "2.1.0";
    private int expiryMinutes = 15;

    public void requireConfigured() {
        if (!enabled) throw new IllegalArgumentException("VNPAY sandbox is disabled");
        if (tmnCode == null || !tmnCode.matches("[A-Za-z0-9]{8}")
                || hashSecret == null || hashSecret.isBlank()
                || !SANDBOX_URL.equals(paymentUrl) || !"2.1.0".equals(version)
                || expiryMinutes < 1 || expiryMinutes > 60
                || !validCallbackUrl(returnUrl, false) || !validCallbackUrl(ipnUrl, true)) {
            // Never include configuration values in the exception or a generated toString.
            throw new IllegalArgumentException("VNPAY sandbox configuration is incomplete or invalid");
        }
    }

    private boolean validCallbackUrl(String value, boolean requireHttps) {
        try {
            if (value == null || value.length() > 255) return false;
            URI uri = URI.create(value);
            return uri.getHost() != null && uri.getUserInfo() == null && uri.getFragment() == null
                    && ("https".equals(uri.getScheme()) || (!requireHttps && "http".equals(uri.getScheme())
                    && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()))));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
