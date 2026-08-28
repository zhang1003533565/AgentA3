package com.example.appbackend.repository;
import com.example.appbackend.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.List;
public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByBankIdOrderByIdDesc(Long bankId);
    List<Question> findBySubjectOrderByIdDesc(String subject);

    @Query("""
            select distinct q from Question q
            where exists (select b.id from QuestionBank b where b.id = q.bankId and b.visibility = 'public')
               or exists (select i.id from QuestionBankItem i, QuestionBank b
                          where i.questionId = q.id and b.id = i.bankId and b.visibility = 'public')
            order by q.id desc
            """)
    List<Question> findAllInPublicBanks();

    @Query("""
            select distinct q from Question q
            where q.bankId = :bankId
               or exists (select i.id from QuestionBankItem i where i.questionId = q.id and i.bankId = :bankId)
            order by q.id desc
            """)
    List<Question> findAllInBank(@Param("bankId") Long bankId);

    @Query("""
            select distinct q from Question q
            where q.id = :questionId and (
                exists (select b.id from QuestionBank b
                        where b.id = q.bankId and (b.visibility = 'public' or b.ownerId = :userId))
                or exists (select i.id from QuestionBankItem i, QuestionBank b
                           where i.questionId = q.id and b.id = i.bankId
                             and (b.visibility = 'public' or b.ownerId = :userId))
            )
            """)
    Optional<Question> findAccessibleById(@Param("questionId") Long questionId, @Param("userId") Long userId);

    @Query("""
            select q from Question q, QuestionFavorite f
            where f.userId = :userId and f.questionId = q.id and (
                exists (select b.id from QuestionBank b
                        where b.id = q.bankId and (b.visibility = 'public' or b.ownerId = :userId))
                or exists (select i.id from QuestionBankItem i, QuestionBank b
                           where i.questionId = q.id and b.id = i.bankId
                             and (b.visibility = 'public' or b.ownerId = :userId))
            )
            order by f.createTime desc
            """)
    List<Question> findAccessibleFavorites(@Param("userId") Long userId);
}
