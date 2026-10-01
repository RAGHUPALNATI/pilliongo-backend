package com.raghu.pilliongo.controller;

import com.raghu.pilliongo.dto.*;
import com.raghu.pilliongo.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.Authentication;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequest request) {
        String message = authService.register(request);
        return ResponseEntity.ok(message);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<String> verifyOtp(@RequestBody OtpVerifyRequest request) {
        String message = authService.verifyOtp(request);
        return ResponseEntity.ok(message);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<String> resendOtp(@RequestBody Map<String, String> body) {
        String message = authService.resendOtp(body.get("email"));
        return ResponseEntity.ok(message);
    }

    @PutMapping("/profile")
    public ResponseEntity<AuthResponse> updateProfile(
            @RequestBody ProfileUpdateRequest request,
            Authentication auth) {
        AuthResponse response = authService.updateProfile(auth.getName(), request);
        return ResponseEntity.ok(response);
    }

    // FORGOT PASSWORD — step 1: request an OTP. 404 (via EmailNotFoundException
    // + GlobalExceptionHandler) if the email isn't registered, 200 otherwise.
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        String message = authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(message);
    }

    // FORGOT PASSWORD — step 2: verify the OTP and hand back a short-lived
    // reset token the frontend must carry into step 3.
    @PostMapping("/verify-reset-otp")
    public ResponseEntity<Map<String, String>> verifyResetOtp(@Valid @RequestBody VerifyResetOtpRequest request) {
        String resetToken = authService.verifyResetOtp(request.getEmail(), request.getOtp());
        Map<String, String> body = new HashMap<>();
        body.put("message", "OTP verified. You can now reset your password.");
        body.put("resetToken", resetToken);
        return ResponseEntity.ok(body);
    }

    // FORGOT PASSWORD — step 3: consume the reset token, set the new password.
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        String message = authService.resetPassword(
                request.getEmail(),
                request.getResetToken(),
                request.getNewPassword(),
                request.getConfirmPassword());
        return ResponseEntity.ok(message);
    }
}