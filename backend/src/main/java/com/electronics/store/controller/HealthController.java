package com.electronics.store.controller;

import io.swagger.v3.oas.annotations.Operation;

import com.electronics.store.dto.response.ApiResponse;
import com.electronics.store.dto.response.HealthResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health check controller.
 * Provides a simple endpoint to verify the API is running.
 *
 * <p>GET /api/health — returns HTTP 200 with a JSON body.</p>
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private static final String VERSION = "0.0.1-SNAPSHOT";

    @Operation(summary = "Check API health",
            description = "PUBLIC: no JWT or role required. Returns UP and application version. No body or parameters. Liveness only, not dependency readiness.",
            tags = {"Health"},
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<HealthResponse>> health() {
        HealthResponse payload = new HealthResponse(
                "UP",
                VERSION,
                "Electronics Store API is running"
        );
        return ResponseEntity.ok(ApiResponse.ok("Health check passed", payload));
    }
}
