package com.electronics.store.dto.response;

/**
 * Health check response payload.
 *
 * @param status  always "UP" when the application is healthy
 * @param version application version from build metadata
 * @param message human-readable status message
 */
public record HealthResponse(
        String status,
        String version,
        String message
) {}
