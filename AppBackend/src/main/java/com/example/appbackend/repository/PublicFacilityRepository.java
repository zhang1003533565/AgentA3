package com.example.appbackend.repository;

import com.example.appbackend.entity.PublicFacility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PublicFacilityRepository extends JpaRepository<PublicFacility, Long> {

    Page<PublicFacility> findByType(String type, Pageable pageable);

    Page<PublicFacility> findByNameContaining(String name, Pageable pageable);

    Page<PublicFacility> findByTypeAndNameContaining(String type, String name, Pageable pageable);

    List<PublicFacility> findAllByType(String type);

    List<PublicFacility> findAllByStatus(String status);
}
