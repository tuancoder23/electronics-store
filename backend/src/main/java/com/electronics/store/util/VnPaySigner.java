package com.electronics.store.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class VnPaySigner {
    private VnPaySigner() { }

    // PAY 2.1.0: alphabetical keys, form URL encoding, no empty or signature fields.
    public static String canonicalize(Map<String, String> parameters) {
        return new TreeMap<>(parameters).entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("vnp_"))
                .filter(entry -> !entry.getKey().equals("vnp_SecureHash") && !entry.getKey().equals("vnp_SecureHashType"))
                .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    public static String sign(Map<String, String> parameters, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(canonicalize(parameters).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("VNPAY signing is unavailable");
        }
    }

    public static boolean verify(Map<String, String> parameters, String secret) {
        String supplied = parameters.get("vnp_SecureHash");
        if (supplied == null || !supplied.matches("[0-9a-fA-F]{128}")) return false;
        return MessageDigest.isEqual(HexFormat.of().parseHex(sign(parameters, secret)), HexFormat.of().parseHex(supplied));
    }

    public static String amount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("VNPAY amount must be positive");
        try {
            String value = amount.movePointRight(2).toBigIntegerExact().toString();
            if (value.length() > 12) throw new IllegalArgumentException("VNPAY amount exceeds the gateway limit");
            return value;
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("VNPAY amount has unsupported precision");
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
