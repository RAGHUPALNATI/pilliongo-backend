package com.raghu.pilliongo.repository;

import com.raghu.pilliongo.model.KnownLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnownLocationRepository extends JpaRepository<KnownLocation, Long> {
    boolean existsByName(String name);
    List<KnownLocation> findAllByOrderByNameAsc();
}
