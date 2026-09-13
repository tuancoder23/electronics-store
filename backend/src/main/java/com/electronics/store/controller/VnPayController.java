package com.electronics.store.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

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

    @Operation(summary = "Create VNPay payment URL",
            description = "AUTHENTICATED: Bearer JWT; USER or ADMIN; personal ownership applies. Owned order, PENDING VNPAY payment, matching amount, valid sandbox configuration, non-CANCELLED/non-DELIVERED order required. Reuses URL while valid. Expired URL/terminal payment 400; new attempts unsupported. No body.",
            tags = {"VNPay"},
            security = @SecurityRequirement(name = "BearerAuth"),
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @PostMapping("/create/{orderId}")
    public ResponseEntity<ApiResponse<VnPayCreatePaymentResponse>> create(@io.swagger.v3.oas.annotations.Parameter(description = "Order identifier; personal APIs require ownership (foreign order returns 404).") @PathVariable Long orderId,
                                                                         HttpServletRequest request) {
        // No forwarded-header trust is configured: use the actual connection address.
        return ResponseEntity.ok(ApiResponse.ok("VNPAY payment URL created",
                service.createPaymentUrl(orderId, request.getRemoteAddr())));
    }

    @Operation(summary = "Verify VNPay browser return",
            description = "PUBLIC: no JWT or role required. Signed callback; JWT ignored. Verifies signature/merchant/reference/amount/fields. Read-only, never marks PAID. Returns persisted paymentStatus separately from gateway result. Invalid callback/configuration 400. Preserve every gateway vnp_ field; edits invalidate signature.",
            tags = {"VNPay"},
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @io.swagger.v3.oas.annotations.Parameters({
            @io.swagger.v3.oas.annotations.Parameter(name = "vnp_TmnCode", in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
                    required = true, description = "Merchant code; must match configured sandbox merchant.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")),
            @io.swagger.v3.oas.annotations.Parameter(name = "vnp_TxnRef", in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
                    required = true, description = "Persisted payment gateway reference, 1..100 alphanumeric characters.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")),
            @io.swagger.v3.oas.annotations.Parameter(name = "vnp_Amount", in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
                    required = true, description = "Exact VND amount multiplied by 100, 1..12 digits.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")),
            @io.swagger.v3.oas.annotations.Parameter(name = "vnp_ResponseCode", in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
                    required = true, description = "Two-digit gateway result; 00 indicates success with matching transaction status.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")),
            @io.swagger.v3.oas.annotations.Parameter(name = "vnp_TransactionStatus", in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
                    required = true, description = "Two-digit transaction status; 00 success, 02 recognized failure.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")),
            @io.swagger.v3.oas.annotations.Parameter(name = "vnp_TransactionNo", in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
                    required = true, description = "1..15 digits; successful payment must not be all zero.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")),
            @io.swagger.v3.oas.annotations.Parameter(name = "vnp_BankCode", in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
                    required = true, description = "3..20 alphanumeric characters.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")),
            @io.swagger.v3.oas.annotations.Parameter(name = "vnp_OrderInfo", in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
                    required = true, description = "1..255 printable ASCII characters.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")),
            @io.swagger.v3.oas.annotations.Parameter(name = "vnp_PayDate", in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
                    required = false, description = "Optional valid timestamp yyyyMMddHHmmss in Asia/Ho_Chi_Minh.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")),
            @io.swagger.v3.oas.annotations.Parameter(name = "vnp_SecureHash", in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
                    required = true, description = "Gateway HMAC-SHA512 signature over all signed vnp_ fields.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")),
            @io.swagger.v3.oas.annotations.Parameter(name = "vnp_SecureHashType", in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
                    required = false, description = "Optional signature metadata; excluded from signed fields.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")),
            @io.swagger.v3.oas.annotations.Parameter(name = "vnp_CardType", in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
                    required = false, description = "Optional gateway field. Preserve if present because it participates in signature.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string"))
    })
    @GetMapping("/return")
    public ResponseEntity<ApiResponse<VnPayReturnResponse>> paymentReturn(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestParam MultiValueMap<String, String> parameters) {
        try {
            return ResponseEntity.ok(ApiResponse.ok("VNPAY result verified",
                    service.inspectReturn(parameters)));
        } catch (VnPayCallbackException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        }
    }

    @Operation(summary = "Process VNPay notification",
            description = "PUBLIC: no JWT or role required. Signed callback; JWT ignored. Records PAID/FAILED. HTTP 200 bare RspCode/Message: 00 processed; 01 missing reference/merchant; 02 already terminal; 04 amount mismatch; 97 invalid signature; 99 invalid data/configuration/unrecognized result/processing failure. Duplicate does not apply twice. Late success after cancellation needs manual reconciliation. Preserve every gateway vnp_ field.",
            tags = {"VNPay"},
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true)
            })
    @io.swagger.v3.oas.annotations.Parameters({
            @io.swagger.v3.oas.annotations.Parameter(name = "vnp_TmnCode", in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
                    required = true, description = "Merchant code; must match configured sandbox merchant.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")),
            @io.swagger.v3.oas.annotations.Parameter(name = "vnp_TxnRef", in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
                    required = true, description = "Persisted payment gateway reference, 1..100 alphanumeric characters.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")),
            @io.swagger.v3.oas.annotations.Parameter(name = "vnp_Amount", in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
                    required = true, description = "Exact VND amount multiplied by 100, 1..12 digits.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")),
            @io.swagger.v3.oas.annotations.Parameter(name = "vnp_ResponseCode", in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
                    required = true, description = "Two-digit gateway result; 00 indicates success with matching transaction status.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")),
            @io.swagger.v3.oas.annotations.Parameter(name = "vnp_TransactionStatus", in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
                    required = true, description = "Two-digit transaction status; 00 success, 02 recognized failure.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")),
            @io.swagger.v3.oas.annotations.Parameter(name = "vnp_TransactionNo", in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
                    required = true, description = "1..15 digits; successful payment must not be all zero.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")),
            @io.swagger.v3.oas.annotations.Parameter(name = "vnp_BankCode", in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
                    required = true, description = "3..20 alphanumeric characters.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")),
            @io.swagger.v3.oas.annotations.Parameter(name = "vnp_OrderInfo", in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
                    required = true, description = "1..255 printable ASCII characters.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")),
            @io.swagger.v3.oas.annotations.Parameter(name = "vnp_PayDate", in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
                    required = false, description = "Optional valid timestamp yyyyMMddHHmmss in Asia/Ho_Chi_Minh.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")),
            @io.swagger.v3.oas.annotations.Parameter(name = "vnp_SecureHash", in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
                    required = true, description = "Gateway HMAC-SHA512 signature over all signed vnp_ fields.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")),
            @io.swagger.v3.oas.annotations.Parameter(name = "vnp_SecureHashType", in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
                    required = false, description = "Optional signature metadata; excluded from signed fields.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string")),
            @io.swagger.v3.oas.annotations.Parameter(name = "vnp_CardType", in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
                    required = false, description = "Optional gateway field. Preserve if present because it participates in signature.", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string"))
    })
    @GetMapping("/ipn")
    public VnPayIpnResponse ipn(@io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestParam MultiValueMap<String, String> parameters) {
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
