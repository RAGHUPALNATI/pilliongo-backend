package com.raghu.pilliongo.repository;

import com.raghu.pilliongo.model.LocationRequest;
import com.raghu.pilliongo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LocationRequestRepository extends JpaRepository<LocationRequest, Long> {
    List<LocationRequest> findAllByOrderByCreatedAtDesc();
    List<LocationRequest> findAllByUserOrderByCreatedAtDesc(User user);
}
