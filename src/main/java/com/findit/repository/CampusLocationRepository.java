package com.findit.repository;

import com.findit.domain.CampusLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CampusLocationRepository extends JpaRepository<CampusLocation, Long> {

    List<CampusLocation> findByActiveTrueOrderByNameAsc();

    Optional<CampusLocation> findByNameIgnoreCase(String name);
}
