package com.example.appbackend.repository;
import com.example.appbackend.entity.QuestionBankItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface QuestionBankItemRepository extends JpaRepository<QuestionBankItem,Long>{
    List<QuestionBankItem> findByBankIdOrderByCreateTimeDesc(Long bankId);
    Optional<QuestionBankItem> findByBankIdAndQuestionId(Long bankId,Long questionId);
    void deleteByBankId(Long bankId);
    void deleteByBankIdAndQuestionId(Long bankId, Long questionId);
}
