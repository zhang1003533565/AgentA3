package com.example.appbackend.repository;
import com.example.appbackend.entity.QuestionBank;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface QuestionBankRepository extends JpaRepository<QuestionBank, Long> {
    List<QuestionBank> findByVisibilityOrderByUpdateTimeDesc(String visibility);
    List<QuestionBank> findByOwnerIdOrderByUpdateTimeDesc(Long ownerId);
}
