package com.raghu.pilliongo.repository;

import com.raghu.pilliongo.model.RouteLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RouteLocationRepository extends JpaRepository<RouteLocation, Long> {
    // A place name can now have several fixed-fare rows — one per distinct
    // "from" location it's fixed-priced from (e.g. "Butani Colony" priced
    // separately from LPU Main Gate and from Meheru) — so this always
    // returns every row for that name instead of assuming there's only
    // one. (findByName/existsByName were removed: with more than one row
    // sharing a name now allowed, Spring Data's Optional-returning derived
    // query would throw NonUniqueResultException the moment a place had a
    // second fixed fare from a different origin.)
    List<RouteLocation> findAllByName(String name);
}
