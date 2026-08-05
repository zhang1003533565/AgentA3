package com.example.appbackend.repository;
import com.example.appbackend.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByBankIdOrderByIdDesc(Long bankId);
    List<Question> findBySubjectOrderByIdDesc(String subject);
}
