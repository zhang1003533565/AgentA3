package com.example.appbackend.service.impl;

import com.example.appbackend.dto.ExamQuestionFolderDTO;
import com.example.appbackend.entity.ExamQuestionFolder;
import com.example.appbackend.entity.User;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ExamQuestionFolderItemRepository;
import com.example.appbackend.repository.ExamQuestionFolderRepository;
import com.example.appbackend.repository.UserRepository;
import com.example.appbackend.service.ExamQuestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamQuestionFolderServiceImplTest {

    @Mock ExamQuestionFolderRepository folderRepository;
    @Mock ExamQuestionFolderItemRepository itemRepository;
    @Mock UserRepository userRepository;
    @Mock ExamQuestionService examQuestionService;

    private ExamQuestionFolderServiceImpl service;
    private final AtomicLong ids = new AtomicLong(10);

    @BeforeEach
    void setUp() {
        service = new ExamQuestionFolderServiceImpl(
                folderRepository, itemRepository, userRepository, examQuestionService);
    }

    @Test
    void createPrivateFolderOwnedByCurrentUser() {
        when(folderRepository.save(any())).thenAnswer(invocation -> {
            ExamQuestionFolder folder = invocation.getArgument(0);
            if (folder.getId() == null) {
                folder.setId(ids.incrementAndGet());
            }
            return folder;
        });

        ExamQuestionFolderDTO.CreateRequest request = new ExamQuestionFolderDTO.CreateRequest();
        request.setName("我的练习");
        request.setVisibility("private");

        User owner = new User();
        owner.setId(7L);
        owner.setUsername("zzs");
        when(userRepository.findById(7L)).thenReturn(Optional.of(owner));

        ExamQuestionFolderDTO.FolderVO vo = service.createFolder(request, 7L);

        ArgumentCaptor<ExamQuestionFolder> captor = ArgumentCaptor.forClass(ExamQuestionFolder.class);
        verify(folderRepository).save(captor.capture());
        ExamQuestionFolder saved = captor.getValue();
        assertEquals(ExamQuestionFolder.VISIBILITY_PRIVATE, saved.getVisibility());
        assertEquals(7L, saved.getOwnerUserId());
        assertEquals("我的练习", vo.getName());
        assertEquals("私有", vo.getVisibilityLabel());
        assertTrue(vo.getOwnedByCurrentUser());
    }

    @Test
    void nonAdminCannotListOthersPrivateFoldersByOwnerId() {
        BusinessException error = assertThrows(BusinessException.class, () ->
                service.listFolders("PRIVATE", 7L, false, 99L, null));
        assertEquals(403, error.getCode());
        verify(folderRepository, never()).findVisibleFolders(
                anyString(), anyLong(), anyBoolean(), any(), anyCollection(), anyBoolean());
    }

    @Test
    void nonAdminCannotReadOthersPrivateFolder() {
        ExamQuestionFolder folder = new ExamQuestionFolder();
        folder.setId(3L);
        folder.setName("别人的");
        folder.setVisibility(ExamQuestionFolder.VISIBILITY_PRIVATE);
        folder.setOwnerUserId(99L);
        folder.setStatus(1);
        when(folderRepository.findByIdAndStatus(3L, 1)).thenReturn(Optional.of(folder));

        BusinessException error = assertThrows(BusinessException.class, () ->
                service.getFolderDetail(3L, 7L, false));
        assertEquals(403, error.getCode());
    }

    @Test
    void adminCanListPrivateFoldersFilteredByKeyword() {
        when(userRepository.findIdsByUsernameOrPersonalNumber("zzs")).thenReturn(List.of(7L));
        when(userRepository.findByUsername("zzs")).thenReturn(Optional.empty());
        when(userRepository.findByPersonalNumber("zzs")).thenReturn(Optional.empty());

        ExamQuestionFolder folder = new ExamQuestionFolder();
        folder.setId(1L);
        folder.setName("zzs私有");
        folder.setVisibility(ExamQuestionFolder.VISIBILITY_PRIVATE);
        folder.setOwnerUserId(7L);
        folder.setStatus(1);
        when(folderRepository.findVisibleFolders(
                eq("PRIVATE"), eq(1L), eq(true), isNull(), eq(List.of(7L)), eq(false)))
                .thenReturn(List.of(folder));
        List<Object[]> groupedCounts = new ArrayList<>();
        groupedCounts.add(new Object[]{1L, 2L});
        when(itemRepository.countGroupedByFolderIds(List.of(1L))).thenReturn(groupedCounts);
        User owner = new User();
        owner.setId(7L);
        owner.setUsername("zzs");
        owner.setPersonalNumber("A3DEMO001");
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(owner));

        List<ExamQuestionFolderDTO.FolderVO> list = service.listFolders("PRIVATE", 1L, true, null, "zzs");
        assertEquals(1, list.size());
        assertEquals(2L, list.get(0).getQuestionCount());
        assertEquals("zzs", list.get(0).getOwnerUsername());
    }
}
