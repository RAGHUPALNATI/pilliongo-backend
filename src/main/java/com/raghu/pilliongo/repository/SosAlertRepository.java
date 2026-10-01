package com.raghu.pilliongo.repository;

import com.raghu.pilliongo.model.SosAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SosAlertRepository extends JpaRepository<SosAlert, Long> {
    List<SosAlert> findAllByOrderByCreatedAtDesc();
    long countByStatus(SosAlert.SosStatus status);
}
