package com.electronics.store.controller;

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
