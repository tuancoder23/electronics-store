package com.electronics.store.documentation;

import com.electronics.store.entity.Role;
import com.electronics.store.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Real HTTP server, production documentation/resource configuration and real JWT filter. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.additional-location=file:src/main/resources/application.yml",
        "spring.datasource.url=jdbc:h2:mem:api_docs;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=documentation_test_key_at_least_32_bytes_long",
        "app.vnpay.enabled=false"
})
class OpenApiIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;
    @Autowired RequestMappingHandlerMapping mappings;
    @Autowired UserRepository users;

    @Test
    void swaggerAndOpenApiResourcesLoadWithoutAuthentication() throws Exception {
        for (String path : new String[]{"/swagger-ui.html", "/swagger-ui/index.html", "/swagger-ui/swagger-ui.css",
                "/swagger-ui/swagger-ui-bundle.js", "/swagger-ui/swagger-ui-standalone-preset.js",
                "/swagger-ui/swagger-initializer.js", "/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/swagger-config"}) {
            var response = http.getForEntity(path, String.class);
            assertThat(response.getStatusCode()).as(path).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).as(path).isNotBlank();
        }
        assertThat(http.getForObject("/swagger-ui/index.html", String.class)).contains("Swagger UI", "swagger-ui-bundle.js");
        JsonNode config = mapper.readTree(http.getForObject("/v3/api-docs/swagger-config", String.class));
        assertThat(config.path("url").asText()).isEqualTo("/v3/api-docs");
        assertThat(config.path("persistAuthorization").asBoolean()).isFalse();
    }

    @Test
    void everyActualApiHasAccurateAccessAndDocumentedSuccessSchema() throws Exception {
        JsonNode doc = documentation();
        Set<String> expected = new HashSet<>();
        mappings.getHandlerMethods().forEach((mapping, handler) -> {
            if (!handler.getBeanType().getPackageName().equals("com.electronics.store.controller")) return;
            for (String path : mapping.getPatternValues()) {
                for (var method : mapping.getMethodsCondition().getMethods()) expected.add(method.name().toLowerCase() + " " + path);
            }
        });
        Set<String> actual = new HashSet<>();
        doc.path("paths").fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(entry -> {
            String method = entry.getKey(), url = path.getKey();
            JsonNode op = entry.getValue();
            actual.add(method + " " + url);
            assertThat(op.path("summary").asText()).isNotBlank();
            assertThat(op.path("tags").size()).isPositive();
            boolean pub = url.equals("/api/health") || url.startsWith("/api/auth/")
                    || (method.equals("get") && (url.startsWith("/api/products") || url.startsWith("/api/categories")
                    || url.startsWith("/api/brands") || url.startsWith("/api/product-images")
                    || url.startsWith("/api/product-specifications") || url.endsWith("/vnpay/return") || url.endsWith("/vnpay/ipn")));
            String access = pub ? "PUBLIC" : url.startsWith("/api/admin/") ? "ROLE_ADMIN" : "AUTHENTICATED";
            assertThat(op.path("description").asText()).as(method + " " + url).startsWith(access + ":");
            if (pub) assertThat(op.path("security").size()).isZero();
            else assertThat(op.path("security").get(0).has("BearerAuth")).isTrue();
            boolean created = method.equals("post") && !url.startsWith("/api/wishlist")
                    && !url.startsWith("/api/payments/") && !url.endsWith("/login");
            JsonNode success = op.path("responses").path(created ? "201" : "200");
            assertThat(success.path("content").size()).as("Success schema: " + url).isPositive();
            success.path("content").forEach(media -> assertThat(media.path("schema").size()).isPositive());
            if (created) assertThat(op.path("responses").has("200")).isFalse();
            op.path("parameters").forEach(parameter -> assertThat(parameter.path("description").asText())
                    .as("Parameter " + parameter.path("name") + " on " + url).isNotBlank());
        }));
        assertThat(actual).hasSize(55).isEqualTo(expected);
        assertThat(doc.path("security").size()).isZero();
        assertReferencesResolve(doc, doc);
    }

    @Test
    void schemasExposeValidationAndConcreteVnPayContracts() throws Exception {
        JsonNode doc = documentation(), schemas = doc.path("components").path("schemas");
        JsonNode product = schemas.path("ProductRequest");
        assertThat(product.path("required").toString()).contains("name", "price", "quantity", "categoryId", "brandId");
        assertThat(product.path("properties").path("name").path("maxLength").asInt()).isEqualTo(200);
        assertThat(schemas.path("CreateReviewRequest").path("properties").path("rating").path("maximum").asInt()).isEqualTo(5);
        assertThat(schemas.path("RegisterRequest").path("properties").path("password").path("writeOnly").asBoolean()).isTrue();
        assertThat(schemas.path("ChangePasswordRequest").path("required").size()).isEqualTo(3);
        assertThat(schemas.path("VnPayIpnResponse").path("properties").has("RspCode")).isTrue();
        assertThat(schemas.path("VnPayIpnResponse").path("properties").has("Message")).isTrue();
        assertThat(schemas.path("VnPayReturnResponse").path("properties").has("paymentStatus")).isTrue();
        for (String callback : new String[]{"return", "ipn"}) {
            JsonNode parameters = doc.path("paths").path("/api/payments/vnpay/" + callback).path("get").path("parameters");
            assertThat(parameters.size()).isEqualTo(12);
            assertThat(parameters.toString()).contains("vnp_SecureHash", "vnp_TxnRef", "vnp_Amount").doesNotContain("MultiValueMap");
        }
        var invalidReturn = http.getForEntity("/api/payments/vnpay/return", JsonNode.class);
        assertThat(invalidReturn.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var invalidIpn = http.getForEntity("/api/payments/vnpay/ipn", JsonNode.class);
        assertThat(invalidIpn.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(invalidIpn.getBody().path("RspCode").asText()).isEqualTo("99");
    }

    @Test
    void literalBearerAuthorizationWorksAndBusinessSecurityRemainsEnforced() throws Exception {
        JsonNode scheme = documentation().path("components").path("securitySchemes").path("BearerAuth");
        assertThat(scheme.path("type").asText()).isEqualTo("apiKey");
        assertThat(scheme.path("in").asText()).isEqualTo("header");
        assertThat(scheme.path("name").asText()).isEqualTo("Authorization");
        assertThat(scheme.path("description").asText()).contains("Bearer <JWT>");
        for (String path : new String[]{"/api/health", "/api/products", "/api/categories", "/api/brands"}) {
            assertThat(http.getForEntity(path, String.class).getStatusCode()).as(path).isEqualTo(HttpStatus.OK);
        }
        for (String path : new String[]{"/api/users/me", "/api/cart", "/api/orders/my-orders", "/api/wishlist", "/api/admin/orders"}) {
            assertThat(http.getForEntity(path, String.class).getStatusCode()).as(path).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        String email = "docs-" + UUID.randomUUID() + "@example.com";
        var registered = http.postForEntity("/api/auth/register", Map.of("fullName", "Docs Test", "email", email,
                "password", "Documentation123!"), JsonNode.class);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + registered.getBody().path("data").path("accessToken").asText());
        var request = new HttpEntity<>(headers);
        assertThat(http.exchange("/api/users/me", HttpMethod.GET, request, JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(http.exchange("/api/admin/orders", HttpMethod.GET, request, JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var user = users.findByEmail(email).orElseThrow();
        user.setRole(Role.ADMIN);
        users.saveAndFlush(user);
        assertThat(http.exchange("/api/admin/orders", HttpMethod.GET, request, JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        headers.set("Authorization", "Bearer invalid");
        assertThat(http.exchange("/api/users/me", HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        // Only documentation GET routes were added to the allowlist; unrelated paths stay protected.
        assertThat(http.getForEntity("/unmapped-documentation-path", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.postForEntity("/v3/api-docs", null, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private JsonNode documentation() throws Exception {
        var response = http.getForEntity("/v3/api-docs", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return mapper.readTree(response.getBody());
    }

    private void assertReferencesResolve(JsonNode node, JsonNode document) {
        if (node.isObject() && node.has("$ref")) {
            String ref = node.path("$ref").asText();
            assertThat(ref).startsWith("#/");
            assertThat(document.at(ref.substring(1)).isMissingNode()).as(ref).isFalse();
        }
        if (node.isContainerNode()) node.forEach(child -> assertReferencesResolve(child, document));
    }
}
