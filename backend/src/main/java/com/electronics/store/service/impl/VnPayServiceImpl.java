package com.electronics.store.service.impl;

import com.electronics.store.config.VnPayProperties;
import com.electronics.store.dto.response.*;
import com.electronics.store.entity.*;
import com.electronics.store.exception.ForbiddenOperationException;
import com.electronics.store.exception.ResourceNotFoundException;
import com.electronics.store.exception.VnPayCallbackException;
import com.electronics.store.repository.OrderRepository;
import com.electronics.store.repository.PaymentRepository;
import com.electronics.store.repository.UserRepository;
import com.electronics.store.service.VnPayService;
import com.electronics.store.util.VnPaySigner;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VnPayServiceImpl implements VnPayService {
    private static final Logger log = LoggerFactory.getLogger(VnPayServiceImpl.class);
    private static final ZoneId GATEWAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter GATEWAY_TIME = DateTimeFormatter.ofPattern("uuuuMMddHHmmss")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final Set<String> FAILURE_CODES = Set.of("09", "10", "11", "12", "13", "24", "51", "65", "75", "79", "99");
    private final VnPayProperties properties;
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final UserRepository users;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public VnPayCreatePaymentResponse createPaymentUrl(Long orderId, String clientIp) {
        OrderEntity order = orders.findByIdAndUserIdForUpdate(orderId, currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        properties.requireConfigured();
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.DELIVERED) {
            throw new IllegalArgumentException("Order is not eligible for VNPAY payment");
        }
        PaymentEntity payment = payments.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for order: " + orderId));
        if (payment.getMethod() != PaymentMethod.VNPAY || order.getPaymentMethod() != PaymentMethod.VNPAY
                || payment.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalArgumentException("Only a PENDING VNPAY payment can create a payment URL");
        }
        if (payment.getAmount().compareTo(order.getTotalAmount()) != 0) {
            throw new IllegalArgumentException("Payment amount does not match order");
        }
        String amount = VnPaySigner.amount(order.getTotalAmount());
        LocalDateTime now = LocalDateTime.now(GATEWAY_ZONE);
        if (payment.getGatewayReference() != null) {
            if (payment.getGatewayPaymentUrl() == null || payment.getGatewayExpiresAt() == null
                    || !now.isBefore(payment.getGatewayExpiresAt())) {
                throw new IllegalArgumentException("VNPAY payment URL has expired; a new attempt is not supported");
            }
            return new VnPayCreatePaymentResponse(orderId, payment.getId(), payment.getGatewayPaymentUrl());
        }
        String reference = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expires = now.plusMinutes(properties.getExpiryMinutes()).withNano(0);
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", properties.getVersion());
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", properties.getTmnCode());
        params.put("vnp_Amount", amount);
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", reference);
        params.put("vnp_OrderInfo", "Thanh toan don hang " + orderId);
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", properties.getReturnUrl());
        params.put("vnp_IpAddr", clientIp);
        params.put("vnp_CreateDate", now.format(GATEWAY_TIME));
        params.put("vnp_ExpireDate", expires.format(GATEWAY_TIME));
        String url = properties.getPaymentUrl() + "?" + VnPaySigner.canonicalize(params)
                + "&vnp_SecureHash=" + VnPaySigner.sign(params, properties.getHashSecret());
        payment.setGatewayReference(reference);
        payment.setGatewayPaymentUrl(url);
        payment.setGatewayExpiresAt(expires);
        payments.saveAndFlush(payment);
        return new VnPayCreatePaymentResponse(orderId, payment.getId(), url);
    }

    @Override
    @Transactional(readOnly = true)
    public VnPayReturnResponse inspectReturn(MultiValueMap<String, String> parameters) {
        Map<String, String> fields = verifiedFields(parameters);
        Long orderId = referenceOrderId(fields);
        PaymentEntity payment = payments.findByOrderId(orderId)
                .orElseThrow(() -> invalid("01", "Order not found"));
        validatePayment(fields, payment, payment.getOrder());
        return new VnPayReturnResponse(orderId, payment.getId(), payment.getStatus(),
                fields.get("vnp_ResponseCode"), fields.get("vnp_TransactionStatus"));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public VnPayIpnResponse processIpn(MultiValueMap<String, String> parameters) {
        Map<String, String> fields = verifiedFields(parameters);
        Long orderId = referenceOrderId(fields);
        OrderEntity order = orders.findByIdForUpdate(orderId).orElseThrow(() -> invalid("01", "Order not found"));
        PaymentEntity payment = payments.findByOrderId(orderId).orElseThrow(() -> invalid("01", "Order not found"));
        validatePayment(fields, payment, order);
        if (payment.getStatus() != PaymentStatus.PENDING || order.getStatus() == OrderStatus.CANCELLED) {
            if (order.getStatus() == OrderStatus.CANCELLED && "00".equals(fields.get("vnp_ResponseCode"))
                    && "00".equals(fields.get("vnp_TransactionStatus"))) {
                log.warn("VNPAY success arrived after cancellation for order {}, payment {}; reconciliation required",
                        orderId, payment.getId());
            }
            return new VnPayIpnResponse("02", "Order already confirmed");
        }
        String response = fields.get("vnp_ResponseCode");
        String status = fields.get("vnp_TransactionStatus");
        if ("00".equals(response) && "00".equals(status)) {
            payment.setStatus(PaymentStatus.PAID);
            payment.setTransactionCode(fields.get("vnp_TransactionNo"));
            payment.setPaidAt(paymentTime(fields.get("vnp_PayDate")));
        } else if (FAILURE_CODES.contains(response) && "02".equals(status)) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setTransactionCode(fields.get("vnp_TransactionNo"));
        } else {
            // Unfinished, suspicious, reversed or contradictory results need reconciliation, not a guessed terminal state.
            throw invalid("99", "Payment result requires reconciliation");
        }
        payments.saveAndFlush(payment);
        return new VnPayIpnResponse("00", "Confirm Success");
    }

    private Map<String, String> verifiedFields(MultiValueMap<String, String> parameters) {
        properties.requireConfigured();
        Map<String, String> fields = new TreeMap<>();
        for (var entry : parameters.entrySet()) {
            if (!entry.getKey().startsWith("vnp_")) continue;
            if (entry.getValue().size() != 1 || !entry.getKey().matches("vnp_[A-Za-z0-9_]+")) {
                throw invalid("97", "Invalid signature parameters");
            }
            fields.put(entry.getKey(), entry.getValue().get(0));
        }
        if (!VnPaySigner.verify(fields, properties.getHashSecret())) throw invalid("97", "Invalid signature");
        if (!properties.getTmnCode().equals(fields.get("vnp_TmnCode"))) throw invalid("01", "Merchant not found");
        if (!matches(fields, "vnp_TxnRef", "[A-Za-z0-9]{1,100}")) throw invalid("01", "Order not found");
        if (!matches(fields, "vnp_Amount", "[0-9]{1,12}")) throw invalid("04", "Invalid amount");
        if (!matches(fields, "vnp_ResponseCode", "[0-9]{2}")
                || !matches(fields, "vnp_TransactionStatus", "[0-9]{2}")
                || !matches(fields, "vnp_TransactionNo", "[0-9]{1,15}")
                || !matches(fields, "vnp_BankCode", "[A-Za-z0-9]{3,20}")
                || !matches(fields, "vnp_OrderInfo", "[\\x20-\\x7E]{1,255}")) {
            throw invalid("99", "Invalid callback data");
        }
        // Apply the same validation to Return and IPN, including notifications for terminal payments.
        if ("00".equals(fields.get("vnp_ResponseCode")) && "00".equals(fields.get("vnp_TransactionStatus"))
                && fields.get("vnp_TransactionNo").matches("0+")) {
            throw invalid("99", "Invalid transaction number");
        }
        if (fields.containsKey("vnp_PayDate") && !fields.get("vnp_PayDate").isEmpty()) paymentTime(fields.get("vnp_PayDate"));
        return fields;
    }

    private void validatePayment(Map<String, String> fields, PaymentEntity payment, OrderEntity order) {
        if (payment.getMethod() != PaymentMethod.VNPAY || order.getPaymentMethod() != PaymentMethod.VNPAY
                || !fields.get("vnp_TxnRef").equals(payment.getGatewayReference()) || payment.getGatewayPaymentUrl() == null) {
            throw invalid("01", "Order not found");
        }
        if (payment.getAmount().compareTo(order.getTotalAmount()) != 0
                || !new java.math.BigInteger(fields.get("vnp_Amount")).toString().equals(VnPaySigner.amount(payment.getAmount()))) {
            throw invalid("04", "Invalid amount");
        }
    }

    private Long referenceOrderId(Map<String, String> fields) {
        return payments.findOrderIdByGatewayReference(fields.get("vnp_TxnRef"))
                .orElseThrow(() -> invalid("01", "Order not found"));
    }

    private LocalDateTime paymentTime(String value) {
        if (value == null || value.isEmpty()) return LocalDateTime.now(GATEWAY_ZONE);
        // A strict date formatter still accepts signed/extended years; PAY requires exactly 14 digits.
        if (!value.matches("[0-9]{14}")) throw invalid("99", "Invalid payment timestamp");
        try {
            return LocalDateTime.parse(value, GATEWAY_TIME);
        } catch (DateTimeParseException ex) {
            throw invalid("99", "Invalid payment timestamp");
        }
    }

    private boolean matches(Map<String, String> fields, String key, String pattern) {
        return fields.get(key) != null && fields.get(key).matches(pattern);
    }

    private VnPayCallbackException invalid(String code, String message) {
        return new VnPayCallbackException(code, message);
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new ForbiddenOperationException("Authentication is required");
        }
        return users.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found")).getId();
    }
}
