package com.smartqueue.web.controller;

import com.smartqueue.web.dto.AuthDtos.LoginRequest;
import com.smartqueue.web.dto.AuthDtos.LoginResponse;
import com.smartqueue.web.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Role-separated login and JWT refresh endpoints")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/staff/login")
    @Operation(summary = "Staff Login", description = "Authenticates staff members. Admins are rejected with 403.")
    public ResponseEntity<LoginResponse> staffLogin(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        return ResponseEntity.ok(authService.loginStaff(request, response));
    }

    @PostMapping("/admin/login")
    @Operation(summary = "Admin Login", description = "Authenticates administrators. Staff members are rejected with 403.")
    public ResponseEntity<LoginResponse> adminLogin(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        return ResponseEntity.ok(authService.loginAdmin(request, response));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh JWT Token", description = "Rotates refresh token and issues a fresh 15-minute JWT cookie.")
    public ResponseEntity<LoginResponse> refreshToken(
            HttpServletRequest request,
            HttpServletResponse response) {
        return ResponseEntity.ok(authService.refreshToken(request, response));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Revokes refresh token and clears httpOnly cookies.")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        authService.logout(request, response);
        return ResponseEntity.ok().build();
    }
}
