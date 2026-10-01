package com.raghu.pilliongo.repository;

import com.raghu.pilliongo.model.KnownLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnownLocationRepository extends JpaRepository<KnownLocation, Long> {
    boolean existsByName(String name);
    List<KnownLocation> findAllByOrderByNameAsc();
    Optional<KnownLocation> findFirstByNameIgnoreCase(String name);
}
