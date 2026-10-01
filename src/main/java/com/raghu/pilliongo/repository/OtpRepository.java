package com.raghu.pilliongo.repository;


import com.raghu.pilliongo.model.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpRepository extends JpaRepository<OtpVerification,Long> {
    Optional<OtpVerification> findTopByEmailOrderByIdDesc(String email);
}
