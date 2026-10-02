package com.raghu.pilliongo.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
@Entity
// OTPs are always looked up by email (latest one first).
@Table(name="otp_verifications", indexes = {
        @Index(name = "idx_otp_email", columnList = "email")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpVerification {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;
    @Column(nullable=false)
    private String email;
    @Column(nullable=false)
    private String otp;
    @Column(nullable=false)
    private LocalDateTime expiryDate;
    @Builder.Default
    private boolean used=false;
}
