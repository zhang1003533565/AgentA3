package com.example.appbackend.repository;
import com.example.appbackend.entity.QuestionFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface QuestionFavoriteRepository extends JpaRepository<QuestionFavorite, Long> {
    Optional<QuestionFavorite> findByUserIdAndQuestionId(Long userId, Long questionId);
    List<QuestionFavorite> findByUserIdOrderByCreateTimeDesc(Long userId);
}
