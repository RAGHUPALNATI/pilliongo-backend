package com.raghu.pilliongo.service;

import com.raghu.pilliongo.dto.*;
import com.raghu.pilliongo.exception.EmailNotFoundException;
import com.raghu.pilliongo.model.*;
import com.raghu.pilliongo.repository.*;
import com.raghu.pilliongo.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final VehicleRepository vehicleRepository;
    private final OtpRepository otpRepository;
    private final EmailService emailService;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    // STEP 1 — Register
    // Up to 3 save() calls (user, driver profile, OTP record) — wrap in
    // @Transactional so a failure partway through (e.g. OTP save fails)
    // doesn't leave a User row in the DB with no way to ever verify it.
    @Transactional
    public String register(RegisterRequest request) {

        // block ADMIN registration from public endpoint
        if ("ADMIN".equalsIgnoreCase(request.getRole())) {
            throw new RuntimeException("Admin registration is not allowed");
        }

        // check duplicate email
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        // save user
        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.valueOf(request.getRole().toUpperCase()))
                .emailVerified(false)
                .active(true)
                .build();

        userRepository.save(user);

        // if driver, save driver profile + their first Vehicle. A driver can
        // own more than one vehicle (added later from their profile — see
        // VehicleController), so registration's vehicle fields become
        // Vehicle #1 in the vehicles table, not just DriverProfile's
        // display-only fields.
        if (user.getRole() == Role.DRIVER) {
            DriverProfile profile = DriverProfile.builder()
                    .user(user)
                    .vehicleType(request.getVehicleType())
                    .vehicleModel(request.getVehicleModel())
                    .vehiclePlate(request.getVehiclePlate())
                    .available(true)
                    .totalEarnings(0.0)
                    .build();
            driverProfileRepository.save(profile);

            if (request.getVehicleType() != null && !request.getVehicleType().isBlank()
                    && request.getVehicleModel() != null && !request.getVehicleModel().isBlank()
                    && request.getVehiclePlate() != null && !request.getVehiclePlate().isBlank()) {
                Vehicle vehicle = Vehicle.builder()
                        .driver(profile)
                        .vehicleType(request.getVehicleType().trim())
                        .vehicleModel(request.getVehicleModel().trim())
                        .vehiclePlate(request.getVehiclePlate().trim().toUpperCase())
                        // This is always the driver's very first vehicle —
                        // registration only ever creates one — so it's the primary.
                        .primaryVehicle(true)
                        .build();
                vehicleRepository.save(vehicle);
            }
        }

        // generate and send OTP
        String otp = String.valueOf(new Random().nextInt(900000) + 100000);

        OtpVerification otpVerification = OtpVerification.builder()
                .email(request.getEmail())
                .otp(otp)
                .expiryDate(LocalDateTime.now().plusMinutes(10))
                .used(false)
                .build();

        otpRepository.save(otpVerification);
        emailService.sendOtpEmail(request.getEmail(), otp);

        log.info("User registered: {}", request.getEmail());
        return "Registration successful. OTP sent to " + request.getEmail();
    }

    // STEP 2 — Verify OTP
    // 2 save() calls (user.emailVerified, otpRecord.used) — both should
    // succeed or both roll back, otherwise a user could end up verified
    // with an OTP that's still marked reusable, or vice versa.
    @Transactional
    public String verifyOtp(OtpVerifyRequest request) {

        OtpVerification otpRecord = otpRepository
                .findTopByEmailOrderByIdDesc(request.getEmail())
                .orElseThrow(() -> new RuntimeException("OTP not found"));

        if (otpRecord.isUsed()) {
            throw new RuntimeException("OTP already used");
        }

        if (otpRecord.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("OTP expired");
        }

        if (!otpRecord.getOtp().equals(request.getOtp())) {
            throw new RuntimeException("Invalid OTP");
        }

        // mark email verified
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setEmailVerified(true);
        userRepository.save(user);

        // mark OTP used
        otpRecord.setUsed(true);
        otpRepository.save(otpRecord);

        return "Email verified. You can now login.";
    }

    // STEP 3 — Login
    public AuthResponse login(LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.isEmailVerified()) {
            throw new RuntimeException("Please verify your email first");
        }

        if (!user.isActive()) {
            throw new RuntimeException("Your account has been suspended");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Failed login for email: {}", request.getEmail());
            throw new RuntimeException("Invalid password");
        }

        String token = jwtUtil.generateToken(
                user.getEmail(),
                user.getRole().name()
        );

        return new AuthResponse(
                token,
                user.getRole().name(),
                user.getFullName(),
                user.getEmail()
        );
    }

    // STEP 4 — Resend OTP
    public String resendOtp(String email) {
        userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String otp = String.valueOf(new Random().nextInt(900000) + 100000);

        OtpVerification otpVerification = OtpVerification.builder()
                .email(email)
                .otp(otp)
                .expiryDate(LocalDateTime.now().plusMinutes(10))
                .used(false)
                .build();

        otpRepository.save(otpVerification);
        emailService.sendOtpEmail(email, otp);

        return "OTP resent to " + email;
    }

    // STEP 5 — Update Profile
    // Up to 2 save() calls (user, driver profile) — wrap in @Transactional
    // so a driver's profile update can't partially apply.
    @Transactional
    public AuthResponse updateProfile(String email, ProfileUpdateRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            user.setPhone(request.getPhone().trim());
        }
        userRepository.save(user);

        if (user.getRole() == Role.DRIVER) {
            DriverProfile profile = driverProfileRepository.findByUser(user).orElse(null);
            if (profile != null) {
                if (request.getVehicleType() != null && !request.getVehicleType().isBlank()) {
                    profile.setVehicleType(request.getVehicleType().trim());
                }
                if (request.getVehicleModel() != null && !request.getVehicleModel().isBlank()) {
                    profile.setVehicleModel(request.getVehicleModel().trim());
                }
                if (request.getVehiclePlate() != null && !request.getVehiclePlate().isBlank()) {
                    profile.setVehiclePlate(request.getVehiclePlate().trim());
                }
                driverProfileRepository.save(profile);
            }
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        return new AuthResponse(token, user.getRole().name(), user.getFullName(), user.getEmail());
    }

    // STEP 6a — Forgot Password: request an OTP
    public String forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EmailNotFoundException("No account found with this email"));

        String otp = String.valueOf(new Random().nextInt(900000) + 100000);

        OtpVerification otpVerification = OtpVerification.builder()
                .email(email)
                .otp(otp)
                .expiryDate(LocalDateTime.now().plusMinutes(10))
                .used(false)
                .build();
        otpRepository.save(otpVerification);

        emailService.sendPasswordResetEmail(email, user.getFullName(), otp);

        log.info("Password reset OTP requested for: {}", email);
        return "OTP sent to your email";
    }

    // STEP 6b — Forgot Password: verify the OTP, issue a short-lived reset
    // token. Reuses the same otp_verifications lookup pattern as
    // verifyOtp() above (findTopByEmailOrderByIdDesc), per the "reuse the
    // existing entity/repository" instruction — forgotPassword() always
    // inserts a fresh row, so this always finds that latest OTP as long as
    // nothing else generated a newer one for the same email in between.
    public String verifyResetOtp(String email, String otp) {
        OtpVerification otpRecord = otpRepository
                .findTopByEmailOrderByIdDesc(email)
                .orElseThrow(() -> new RuntimeException("Invalid or expired OTP"));

        if (otpRecord.isUsed()
                || otpRecord.getExpiryDate().isBefore(LocalDateTime.now())
                || !otpRecord.getOtp().equals(otp)) {
            throw new RuntimeException("Invalid or expired OTP");
        }

        otpRecord.setUsed(true);
        otpRepository.save(otpRecord);

        log.info("Password reset OTP verified for: {}", email);
        return jwtUtil.generateResetToken(email);
    }

    // STEP 6c — Forgot Password: consume the reset token and set the new
    // password. newPassword/confirmPassword are already checked at the DTO
    // level (@Size(min=6), both @NotBlank) — these are a second,
    // explicit layer per the spec, and the match check can't be expressed
    // as a single-field Bean Validation annotation anyway.
    @Transactional
    public String resetPassword(String email, String resetToken, String newPassword, String confirmPassword) {
        if (!jwtUtil.validateResetToken(resetToken, email)) {
            throw new RuntimeException("Invalid or expired reset token");
        }

        if (!newPassword.equals(confirmPassword)) {
            throw new RuntimeException("Passwords do not match");
        }

        if (newPassword.length() < 6) {
            throw new RuntimeException("Password must be at least 6 characters");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        log.info("Password reset completed for: {}", email);
        return "Password reset successfully. Please login.";
    }
}