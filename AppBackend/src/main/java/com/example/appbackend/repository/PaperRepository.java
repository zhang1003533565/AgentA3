package com.example.appbackend.repository;
import com.example.appbackend.entity.Paper;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PaperRepository extends JpaRepository<Paper, Long> {
    List<Paper> findByCreatorIdOrderByUpdateTimeDesc(Long creatorId);
}
