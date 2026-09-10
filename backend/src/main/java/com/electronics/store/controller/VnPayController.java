package com.electronics.store.controller;

import com.electronics.store.dto.response.*;
import com.electronics.store.exception.VnPayCallbackException;
import com.electronics.store.service.VnPayService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments/vnpay")
@RequiredArgsConstructor
public class VnPayController {
    private static final Logger log = LoggerFactory.getLogger(VnPayController.class);
    private final VnPayService service;

    @PostMapping("/create/{orderId}")
    public ResponseEntity<ApiResponse<VnPayCreatePaymentResponse>> create(@PathVariable Long orderId,
                                                                         HttpServletRequest request) {
        // No forwarded-header trust is configured: use the actual connection address.
        return ResponseEntity.ok(ApiResponse.ok("VNPAY payment URL created",
                service.createPaymentUrl(orderId, request.getRemoteAddr())));
    }

    @GetMapping("/return")
    public ResponseEntity<? extends ApiResponse<?>> paymentReturn(@RequestParam MultiValueMap<String, String> parameters) {
        try {
            return ResponseEntity.ok(ApiResponse.ok("VNPAY result verified",
                    service.inspectReturn(parameters)));
        } catch (VnPayCallbackException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/ipn")
    public VnPayIpnResponse ipn(@RequestParam MultiValueMap<String, String> parameters) {
        // Catch outside the transactional proxy, including flush/commit errors, so VNPAY can retry.
        try {
            return service.processIpn(parameters);
        } catch (VnPayCallbackException ex) {
            return new VnPayIpnResponse(ex.getResponseCode(), ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("VNPAY IPN was not committed ({})", ex.getClass().getSimpleName());
            return new VnPayIpnResponse("99", "Unable to process notification");
        }
    }
}
