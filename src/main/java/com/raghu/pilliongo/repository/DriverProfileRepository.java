package com.raghu.pilliongo.repository;

import com.raghu.pilliongo.model.DriverProfile;
import com.raghu.pilliongo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DriverProfileRepository extends JpaRepository<DriverProfile,Long> {


    Optional<DriverProfile> findByUser(User user);


}
