package com.electronics.store.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.electronics.store.dto.request.ChangePasswordRequest;
import com.electronics.store.dto.request.UpdateProfileRequest;
import com.electronics.store.dto.response.ApiResponse;
import com.electronics.store.dto.response.UserResponse;
import com.electronics.store.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @Operation(summary = "Get my profile",
            description = "AUTHENTICATED: Bearer JWT; USER or ADMIN; personal ownership applies. Returns safe current user details; never password/hash. No body.",
            tags = {"User Profile"},
            security = @SecurityRequirement(name = "BearerAuth"),
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser() {
        return ResponseEntity.ok(ApiResponse.ok("Current user retrieved successfully",
                userService.getCurrentUser()));
    }

    @Operation(summary = "Update my profile",
            description = "AUTHENTICATED: Bearer JWT; USER or ADMIN; personal ownership applies. Accepts only fullName/phone. Unknown fields including email/role/status return 400.",
            tags = {"User Profile"},
            security = @SecurityRequirement(name = "BearerAuth"),
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateCurrentUser(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Profile updated successfully", userService.updateCurrentUser(request)));
    }

    @Operation(summary = "Change my password",
            description = "AUTHENTICATED: Bearer JWT; USER or ADMIN; personal ownership applies. All three fields required. Current password must match (400 otherwise). New password must match confirmation, contain 8..72 characters and at most 72 UTF-8 bytes. Unknown fields rejected. Existing JWTs valid until expiry.",
            tags = {"User Profile"},
            security = @SecurityRequirement(name = "BearerAuth"),
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success", useReturnTypeSchema = true),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
            })
    @PutMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@RequestBody ChangePasswordRequest request) {
        userService.changeCurrentUserPassword(request);
        return ResponseEntity.ok(ApiResponse.ok("Password changed successfully"));
    }
}
