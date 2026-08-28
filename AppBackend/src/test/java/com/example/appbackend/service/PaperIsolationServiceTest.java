package com.example.appbackend.service;

import com.example.appbackend.dto.PaperDTO;
import com.example.appbackend.entity.Paper;
import com.example.appbackend.entity.Question;
import com.example.appbackend.entity.QuestionBank;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaperIsolationServiceTest {
    private static final long USER_A = 101L;
    private static final long USER_B = 202L;

    @Mock private QuestionBankRepository bankRepo;
    @Mock private QuestionRepository questionRepo;
    @Mock private QuestionBankItemRepository bankItemRepo;
    @Mock private QuestionFavoriteRepository favoriteRepo;
    @Mock private PaperRepository paperRepo;
    @Mock private PaperQuestionRepository paperQuestionRepo;
    @Mock private PaperLayoutRepository paperLayoutRepo;
    @InjectMocks private PaperService service;

    private Paper paperA;
    private Paper paperB;

    @BeforeEach
    void setUp() {
        paperA = paper(11L, USER_A);
        paperB = paper(22L, USER_B);
    }

    @Test
    void publicQuestionsUseOnlyRepositoryPublicBankQuery() {
        when(questionRepo.findAllInPublicBanks()).thenReturn(List.of());
        when(favoriteRepo.findByUserIdOrderByCreateTimeDesc(USER_A)).thenReturn(List.of());

        service.questions(null, USER_A, null, null, null, null, false);

        verify(questionRepo).findAllInPublicBanks();
        verify(questionRepo, never()).findAll();
    }

    @Test
    void foreignPrivateBankCannotBeRead() {
        QuestionBank bankB = new QuestionBank();
        bankB.setId(8L); bankB.setOwnerId(USER_B); bankB.setVisibility("private");
        when(bankRepo.findById(8L)).thenReturn(Optional.of(bankB));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.questions(8L, USER_A, null, null, null, null, false));

        assertEquals(403, error.getCode());
        verify(questionRepo, never()).findAllInBank(anyLong());
    }

    @Test
    void inaccessiblePrivateQuestionCannotBeReadOrFavorited() {
        when(questionRepo.findAccessibleById(99L, USER_A)).thenReturn(Optional.empty());

        assertEquals(404, assertThrows(BusinessException.class,
                () -> service.questionDetail(99L, USER_A, null)).getCode());
        assertEquals(404, assertThrows(BusinessException.class,
                () -> service.toggleFavorite(99L, USER_A, true)).getCode());
        verify(favoriteRepo, never()).save(any());
    }

    @Test
    void favoritesAreUserScopedAndPermissionFilteredInRepository() {
        when(questionRepo.findAccessibleFavorites(USER_A)).thenReturn(List.of());
        when(favoriteRepo.findByUserIdOrderByCreateTimeDesc(USER_A)).thenReturn(List.of());

        service.questions(null, USER_A, null, null, null, null, true);

        verify(questionRepo).findAccessibleFavorites(USER_A);
        verify(questionRepo, never()).findAccessibleFavorites(USER_B);
    }

    @Test
    void foreignPaperIdCannotRevealSelectionState() {
        when(paperRepo.findById(22L)).thenReturn(Optional.of(paperB));

        BusinessException listError = assertThrows(BusinessException.class,
                () -> service.questions(null, USER_A, null, null, null, 22L, false));
        BusinessException detailError = assertThrows(BusinessException.class,
                () -> service.questionDetail(1L, USER_A, 22L));

        assertEquals(403, listError.getCode());
        assertEquals(403, detailError.getCode());
        verifyNoInteractions(paperQuestionRepo);
    }

    @Test
    void ownPaperIdMayReadSelectionState() {
        Question question = new Question();
        question.setId(1L); question.setBankId(1L); question.setQuestionType("单选题"); question.setContent("题目");
        when(paperRepo.findById(11L)).thenReturn(Optional.of(paperA));
        when(questionRepo.findAccessibleById(1L, USER_A)).thenReturn(Optional.of(question));
        when(paperQuestionRepo.findByPaperIdAndQuestionId(11L, 1L)).thenReturn(Optional.empty());
        when(favoriteRepo.findByUserIdAndQuestionId(USER_A, 1L)).thenReturn(Optional.empty());

        PaperDTO.QuestionVO result = service.questionDetail(1L, USER_A, 11L);

        assertFalse(result.getSelected());
    }

    private Paper paper(Long id, Long creatorId) {
        Paper paper = new Paper();
        paper.setId(id); paper.setCreatorId(creatorId); paper.setName("测试试卷");
        paper.setSubject("测试科目"); paper.setCategory("测试分类");
        return paper;
    }
}
