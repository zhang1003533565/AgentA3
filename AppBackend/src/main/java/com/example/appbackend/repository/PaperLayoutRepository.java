package com.example.appbackend.repository;

import com.example.appbackend.entity.PaperLayout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaperLayoutRepository extends JpaRepository<PaperLayout, Long> {
    Optional<PaperLayout> findByPaperId(Long paperId);
    void deleteByPaperId(Long paperId);
}
