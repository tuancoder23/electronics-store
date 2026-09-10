package com.electronics.store.util;

import com.electronics.store.config.VnPayProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VnPaySignerTest {
    private static final String TEST_SECRET = "test-only-vnpay-secret";

    @Test
    void signingMatchesIndependentDotNetHmacVectorAndFormEncoding() {
        Map<String, String> fields = new HashMap<>(Map.of(
                "vnp_TxnRef", "abc123", "vnp_Amount", "2399000000",
                "vnp_ReturnUrl", "https://example.test/return?a=1&b=2",
                "vnp_OrderInfo", "Thanh toan A+B & C", "vnp_BankCode", "",
                "vnp_SecureHashType", "ignored", "userId", "123"));
        assertThat(VnPaySigner.canonicalize(fields)).isEqualTo("vnp_Amount=2399000000"
                + "&vnp_OrderInfo=Thanh+toan+A%2BB+%26+C"
                + "&vnp_ReturnUrl=https%3A%2F%2Fexample.test%2Freturn%3Fa%3D1%26b%3D2&vnp_TxnRef=abc123");
        // Expected bytes computed separately with System.Security.Cryptography.HMACSHA512.
        String expected = "0b957dfd14da09b143621fa0703ea927e0528508f4fa74f68f66365e2111481e00"
                + "db143179ce8b89b3e6ba69d980e8d74135387926713b75ff5f820c3016552f";
        assertThat(VnPaySigner.sign(fields, TEST_SECRET)).isEqualTo(expected);
        fields.put("vnp_SecureHash", expected.toUpperCase());
        assertThat(VnPaySigner.verify(fields, TEST_SECRET)).isTrue();
        fields.put("vnp_Amount", "1");
        assertThat(VnPaySigner.verify(fields, TEST_SECRET)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-hex", "1234"})
    void malformedSignaturesAreRejected(String hash) {
        assertThat(VnPaySigner.verify(Map.of("vnp_SecureHash", hash), TEST_SECRET)).isFalse();
        assertThat(VnPaySigner.verify(Map.of(), TEST_SECRET)).isFalse();
    }

    @Test
    void amountConversionIsExactAndDoesNotModifyVndAmount() {
        BigDecimal amount = new BigDecimal("23990000.00");
        assertThat(VnPaySigner.amount(amount)).isEqualTo("2399000000");
        assertThat(amount).isEqualByComparingTo("23990000");
        assertThat(VnPaySigner.amount(new BigDecimal("123.45"))).isEqualTo("12345");
        assertThat(VnPaySigner.amount(new BigDecimal("9999999999.99"))).isEqualTo("999999999999");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.001", "10000000000"})
    void unsupportedAmountsAreRejectedWithoutRounding(String amount) {
        assertThrows(IllegalArgumentException.class, () -> VnPaySigner.amount(new BigDecimal(amount)));
    }

    @Test
    void sandboxConfigurationRejectsMissingCredentialsProductionAndNonHttpsIpn() {
        VnPayProperties config = new VnPayProperties();
        assertThrows(IllegalArgumentException.class, config::requireConfigured);
        config.setEnabled(true);
        assertThrows(IllegalArgumentException.class, config::requireConfigured);
        config.setTmnCode("TESTONLY");
        config.setHashSecret(TEST_SECRET);
        config.setPaymentUrl(VnPayProperties.SANDBOX_URL);
        config.setReturnUrl("http://localhost:8080/api/payments/vnpay/return");
        config.setIpnUrl("https://merchant.example/api/payments/vnpay/ipn");
        config.requireConfigured();
        config.setPaymentUrl("https://pay.vnpay.vn/vpcpay.html");
        assertThrows(IllegalArgumentException.class, config::requireConfigured);
        config.setPaymentUrl(VnPayProperties.SANDBOX_URL);
        config.setIpnUrl("http://merchant.example/ipn");
        assertThrows(IllegalArgumentException.class, config::requireConfigured);
        config.setIpnUrl("https://merchant.example/ipn");
        config.setVersion("2.0.1");
        assertThrows(IllegalArgumentException.class, config::requireConfigured);
    }
}
