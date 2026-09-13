package com.electronics.store.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/** Documentation metadata only; SecurityConfig remains the authorization authority. */
@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI backendOpenApi() {
        Components components = new Components()
                // apiKey sends the complete value verbatim, so users can paste "Bearer <JWT>".
                .addSecuritySchemes("BearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("Authorization")
                        .description("Bearer JWT authentication. Paste the complete value: Bearer <JWT>. "
                                + "Obtain data.accessToken from register/login. Do not enter a raw token. "
                                + "AUTHENTICATED accepts USER or ADMIN; ROLE_ADMIN requires ADMIN."))
                .addSchemas("ErrorResponse", new ObjectSchema()
                        .description("ApiResponse error envelope. Null data is omitted; validation reports the first field error.")
                        .addProperty("success", new BooleanSchema().example(false))
                        .addProperty("message", new StringSchema().example("Invalid request body or unsupported field value"))
                        .addProperty("timestamp", new StringSchema()
                                .description("Server local date-time, without a UTC offset")
                                .example("2026-09-12T10:00:00"))
                        .required(List.of("success", "message", "timestamp")));
        Map.of(
                "BadRequest", "Invalid body, field validation, query/path type, or business rule (400). See operation constraints.",
                "Unauthorized", "Missing, invalid or expired JWT; invalid login credentials or inactive account (401). "
                        + "A malformed Bearer token also fails on public APIs except VNPay callbacks.",
                "Forbidden", "ROLE_ADMIN required, foreign cart item, or review purchase/delivery requirement not met (403).",
                "NotFound", "Resource not found; owned order/review lookup also returns 404 for another user's resource.",
                "Conflict", "Duplicate email, catalog name, product specification name, or product review (409).",
                "InternalError", "Unexpected server or persistence error (500); no internal details are returned."
        ).forEach((name, description) -> components.addResponses(name, new ApiResponse()
                .description(description).content(new Content().addMediaType("application/json", new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"))))));
        return new OpenAPI().components(components).info(new Info()
                .title("Electronics Store Backend API").version("0.0.1-SNAPSHOT")
                .description("Backend MVP: catalog, accounts, cart, checkout, orders, COD, VNPay sandbox, wishlist and reviews. "
                        + "PUBLIC: no JWT required. AUTHENTICATED: active USER or ADMIN with Bearer JWT. "
                        + "ROLE_ADMIN: ADMIN only. Ownership checks still apply to personal APIs, including admins. "
                        + "Use Authorize with the complete Bearer <JWT> header value. "
                        + "Responses use ApiResponse<T>; VNPay IPN returns its own RspCode/Message object. "
                        + "Money uses decimal values; VNPay uses VND. Dates are server local date-times unless stated. "
                        + "See docs/api-spec.md and README.md for setup, lifecycle and limitations."));
    }
}
