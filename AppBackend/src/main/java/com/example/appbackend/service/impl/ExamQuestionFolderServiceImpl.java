package com.example.appbackend.service.impl;

import com.example.appbackend.dto.ExamQuestionDTO;
import com.example.appbackend.dto.ExamQuestionFolderDTO;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.ExamQuestion;
import com.example.appbackend.entity.ExamQuestionFolder;
import com.example.appbackend.entity.ExamQuestionFolderItem;
import com.example.appbackend.entity.Result;
import com.example.appbackend.entity.User;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ExamQuestionFolderItemRepository;
import com.example.appbackend.repository.ExamQuestionFolderRepository;
import com.example.appbackend.repository.UserRepository;
import com.example.appbackend.service.ExamQuestionFolderService;
import com.example.appbackend.service.ExamQuestionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ExamQuestionFolderServiceImpl implements ExamQuestionFolderService {

    private final ExamQuestionFolderRepository folderRepository;
    private final ExamQuestionFolderItemRepository itemRepository;
    private final UserRepository userRepository;
    private final ExamQuestionService examQuestionService;

    public ExamQuestionFolderServiceImpl(
            ExamQuestionFolderRepository folderRepository,
            ExamQuestionFolderItemRepository itemRepository,
            UserRepository userRepository,
            ExamQuestionService examQuestionService) {
        this.folderRepository = folderRepository;
        this.itemRepository = itemRepository;
        this.userRepository = userRepository;
        this.examQuestionService = examQuestionService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExamQuestionFolderDTO.FolderVO> listFolders(
            String visibility,
            Long viewerId,
            boolean admin,
            Long ownerUserId,
            String ownerKeyword) {
        String normalized = normalizeVisibility(visibility);

        if (ExamQuestionFolder.VISIBILITY_PUBLIC.equals(normalized)) {
            return toFolderVos(folderRepository.findVisibleFolders(
                    normalized, viewerId, false, null, List.of(-1L), true), viewerId);
        }

        // PRIVATE for normal users: only own folders
        if (!admin) {
            if (ownerUserId != null && !ownerUserId.equals(viewerId)) {
                throw new BusinessException(Result.FORBIDDEN_CODE, "无权查看他人私有题库");
            }
            return toFolderVos(folderRepository.findVisibleFolders(
                    normalized, viewerId, false, null, List.of(-1L), true), viewerId);
        }

        // Admin PRIVATE with optional filters
        List<Long> ownerIds = resolveOwnerIds(ownerKeyword);
        if (StringUtils.hasText(ownerKeyword) && ownerIds.isEmpty() && ownerUserId == null) {
            return List.of();
        }
        boolean filterByKeywordIds = !ownerIds.isEmpty();
        return toFolderVos(folderRepository.findVisibleFolders(
                normalized,
                viewerId,
                true,
                ownerUserId,
                filterByKeywordIds ? ownerIds : List.of(-1L),
                !filterByKeywordIds), viewerId);
    }

    @Override
    @Transactional
    public ExamQuestionFolderDTO.FolderVO createFolder(
            ExamQuestionFolderDTO.CreateRequest request,
            Long userId) {
        String visibility = normalizeVisibility(request.getVisibility());
        ExamQuestionFolder folder = new ExamQuestionFolder();
        folder.setName(request.getName().trim());
        folder.setVisibility(visibility);
        folder.setOwnerUserId(userId);
        folder.setStatus(1);
        folderRepository.save(folder);
        return toFolderVo(folder, 0L, userId, loadUser(userId));
    }

    @Override
    @Transactional
    public ExamQuestionFolderDTO.FolderVO renameFolder(
            Long folderId,
            ExamQuestionFolderDTO.RenameRequest request,
            Long userId,
            boolean admin) {
        ExamQuestionFolder folder = requireEditableFolder(folderId, userId, admin);
        folder.setName(request.getName().trim());
        folderRepository.save(folder);
        return toFolderVo(folder, itemRepository.countByFolderId(folderId), userId, loadUser(folder.getOwnerUserId()));
    }

    @Override
    @Transactional
    public ExamQuestionFolderDTO.FolderVO changeVisibility(
            Long folderId,
            ExamQuestionFolderDTO.VisibilityRequest request,
            Long userId,
            boolean admin) {
        ExamQuestionFolder folder = requireEditableFolder(folderId, userId, admin);
        String visibility = normalizeVisibility(request.getVisibility());
        folder.setVisibility(visibility);
        folderRepository.save(folder);

        if (ExamQuestionFolder.VISIBILITY_PUBLIC.equals(visibility)
                && Boolean.TRUE.equals(request.getPublishContainedQuestions())) {
            Page<ExamQuestionFolderItem> items = itemRepository.findByFolderIdOrderByCreateTimeDescIdDesc(
                    folderId, PageRequest.of(0, 500));
            for (ExamQuestionFolderItem item : items.getContent()) {
                try {
                    examQuestionService.setQuestionVisibility(
                            item.getQuestionId(), ExamQuestion.VISIBILITY_PUBLIC, userId, admin);
                } catch (BusinessException ignored) {
                    // skip questions the operator cannot publish
                }
            }
        }
        return toFolderVo(folder, itemRepository.countByFolderId(folderId), userId, loadUser(folder.getOwnerUserId()));
    }

    @Override
    @Transactional
    public void deleteFolder(Long folderId, Long userId, boolean admin) {
        ExamQuestionFolder folder = requireEditableFolder(folderId, userId, admin);
        folder.setStatus(0);
        folderRepository.save(folder);
        itemRepository.deleteByFolderId(folderId);
    }

    @Override
    @Transactional(readOnly = true)
    public ExamQuestionFolderDTO.FolderDetailVO getFolderDetail(Long folderId, Long userId, boolean admin) {
        ExamQuestionFolder folder = requireReadableFolder(folderId, userId, admin);
        ExamQuestionFolderDTO.FolderDetailVO detail = new ExamQuestionFolderDTO.FolderDetailVO();
        copyFolderFields(detail, folder, itemRepository.countByFolderId(folderId), userId, loadUser(folder.getOwnerUserId()));
        PageResponse<ExamQuestionDTO.QuestionVO> page = listFolderQuestions(folderId, 1, 50, userId, admin);
        detail.setQuestions(page.getRecords() != null ? page.getRecords() : List.of());
        return detail;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExamQuestionDTO.QuestionVO> listFolderQuestions(
            Long folderId,
            Integer current,
            Integer size,
            Long userId,
            boolean admin) {
        requireReadableFolder(folderId, userId, admin);
        int page = current == null || current < 1 ? 0 : current - 1;
        int pageSize = size == null || size < 1 ? 10 : Math.min(size, 100);
        Page<ExamQuestionFolderItem> itemPage = itemRepository.findByFolderIdOrderByCreateTimeDescIdDesc(
                folderId, PageRequest.of(page, pageSize));

        List<ExamQuestionDTO.QuestionVO> questions = new ArrayList<>();
        for (ExamQuestionFolderItem item : itemPage.getContent()) {
            try {
                questions.add(examQuestionService.getQuestion(item.getQuestionId(), userId));
            } catch (BusinessException ignored) {
                // skip questions no longer visible to current user
            }
        }
        PageResponse<ExamQuestionDTO.QuestionVO> response = new PageResponse<>();
        response.setRecords(questions);
        response.setTotal(itemPage.getTotalElements());
        response.setPage(page + 1);
        response.setSize(pageSize);
        return response;
    }

    @Override
    @Transactional
    public void addQuestion(Long folderId, Long questionId, Long userId, boolean admin) {
        ExamQuestionFolder folder = requireEditableFolder(folderId, userId, admin);
        examQuestionService.getQuestion(questionId, userId);
        if (!itemRepository.existsByFolderIdAndQuestionId(folderId, questionId)) {
            ExamQuestionFolderItem item = new ExamQuestionFolderItem();
            item.setFolderId(folder.getId());
            item.setQuestionId(questionId);
            itemRepository.save(item);
        }
        folderRepository.save(folder);
    }

    @Override
    @Transactional
    public void removeQuestion(Long folderId, Long questionId, Long userId, boolean admin) {
        requireEditableFolder(folderId, userId, admin);
        itemRepository.findByFolderIdAndQuestionId(folderId, questionId)
                .ifPresent(itemRepository::delete);
    }

    @Override
    @Transactional
    public ExamQuestionFolderDTO.PushQuestionsResult pushQuestions(
            Long sourceFolderId,
            ExamQuestionFolderDTO.PushQuestionsRequest request,
            Long userId,
            boolean admin) {
        ExamQuestionFolder source = requireEditableFolder(sourceFolderId, userId, admin);
        if (request.getTargetFolderId() == null || request.getTargetFolderId().equals(sourceFolderId)) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "请选择与源收藏夹不同的目标收藏夹");
        }
        ExamQuestionFolder target = requireEditableFolder(request.getTargetFolderId(), userId, admin);
        List<Long> questionIds = request.getQuestionIds() == null
                ? List.of()
                : request.getQuestionIds().stream().filter(Objects::nonNull).distinct().toList();
        if (questionIds.isEmpty()) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "至少选择一道题");
        }

        boolean targetPublic = ExamQuestionFolder.VISIBILITY_PUBLIC.equals(target.getVisibility());
        boolean publishQuestions = Boolean.TRUE.equals(request.getPublishQuestions());
        boolean removeFromSource = Boolean.TRUE.equals(request.getRemoveFromSource());
        int pushed = 0;
        int published = 0;
        int removed = 0;

        for (Long questionId : questionIds) {
            itemRepository.findByFolderIdAndQuestionId(sourceFolderId, questionId)
                    .orElseThrow(() -> new BusinessException(Result.BAD_REQUEST_CODE, "题目不在源收藏夹中: " + questionId));

            ExamQuestionDTO.QuestionVO question = examQuestionService.getQuestion(questionId, userId);
            boolean questionPrivate = ExamQuestion.VISIBILITY_PRIVATE.equalsIgnoreCase(
                    question.getVisibility() == null ? "" : question.getVisibility());

            if (targetPublic && questionPrivate) {
                if (!publishQuestions) {
                    throw new BusinessException(
                            Result.BAD_REQUEST_CODE,
                            "目标为公共收藏夹，私有题目需勾选「同步公开题目」后再推送");
                }
                examQuestionService.setQuestionVisibility(
                        questionId, ExamQuestion.VISIBILITY_PUBLIC, userId, admin);
                published++;
            }

            if (!itemRepository.existsByFolderIdAndQuestionId(target.getId(), questionId)) {
                ExamQuestionFolderItem item = new ExamQuestionFolderItem();
                item.setFolderId(target.getId());
                item.setQuestionId(questionId);
                itemRepository.save(item);
            }
            pushed++;

            if (removeFromSource) {
                itemRepository.findByFolderIdAndQuestionId(sourceFolderId, questionId)
                        .ifPresent(itemRepository::delete);
                removed++;
            }
        }

        folderRepository.save(source);
        folderRepository.save(target);

        ExamQuestionFolderDTO.PushQuestionsResult result = new ExamQuestionFolderDTO.PushQuestionsResult();
        result.setTargetFolderId(target.getId());
        result.setTargetFolderName(target.getName());
        result.setTargetVisibility(target.getVisibility());
        result.setPushedCount(pushed);
        result.setPublishedCount(published);
        result.setRemovedFromSourceCount(removed);
        return result;
    }

    private List<Long> resolveOwnerIds(String ownerKeyword) {
        if (!StringUtils.hasText(ownerKeyword)) {
            return List.of();
        }
        String keyword = ownerKeyword.trim();
        Set<Long> ids = new HashSet<>(userRepository.findIdsByUsernameOrPersonalNumber(keyword));
        if (keyword.chars().allMatch(Character::isDigit)) {
            try {
                ids.add(Long.parseLong(keyword));
            } catch (NumberFormatException ignored) {
                // ignore
            }
        }
        userRepository.findByUsername(keyword).map(User::getId).ifPresent(ids::add);
        userRepository.findByPersonalNumber(keyword).map(User::getId).ifPresent(ids::add);
        return new ArrayList<>(ids);
    }

    private ExamQuestionFolder requireReadableFolder(Long folderId, Long userId, boolean admin) {
        ExamQuestionFolder folder = folderRepository.findByIdAndStatus(folderId, 1)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "收藏夹不存在"));
        if (ExamQuestionFolder.VISIBILITY_PUBLIC.equals(folder.getVisibility())) {
            return folder;
        }
        if (admin || Objects.equals(folder.getOwnerUserId(), userId)) {
            return folder;
        }
        throw new BusinessException(Result.FORBIDDEN_CODE, "无权查看该私有收藏夹");
    }

    private ExamQuestionFolder requireEditableFolder(Long folderId, Long userId, boolean admin) {
        ExamQuestionFolder folder = folderRepository.findByIdAndStatus(folderId, 1)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "收藏夹不存在"));
        if (admin || Objects.equals(folder.getOwnerUserId(), userId)) {
            return folder;
        }
        throw new BusinessException(Result.FORBIDDEN_CODE, "无权修改该收藏夹");
    }

    private String normalizeVisibility(String visibility) {
        if (!StringUtils.hasText(visibility)) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "可见范围不能为空");
        }
        String normalized = visibility.trim().toUpperCase(Locale.ROOT);
        if (!ExamQuestionFolder.VISIBILITY_PUBLIC.equals(normalized)
                && !ExamQuestionFolder.VISIBILITY_PRIVATE.equals(normalized)) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "可见范围必须是 PUBLIC 或 PRIVATE");
        }
        return normalized;
    }

    private List<ExamQuestionFolderDTO.FolderVO> toFolderVos(List<ExamQuestionFolder> folders, Long viewerId) {
        if (folders.isEmpty()) {
            return List.of();
        }
        List<Long> folderIds = folders.stream().map(ExamQuestionFolder::getId).toList();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : itemRepository.countGroupedByFolderIds(folderIds)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        Set<Long> ownerIds = folders.stream().map(ExamQuestionFolder::getOwnerUserId).collect(Collectors.toSet());
        Map<Long, User> owners = userRepository.findAllById(ownerIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user, (a, b) -> a));
        List<ExamQuestionFolderDTO.FolderVO> result = new ArrayList<>();
        for (ExamQuestionFolder folder : folders) {
            result.add(toFolderVo(
                    folder,
                    counts.getOrDefault(folder.getId(), 0L),
                    viewerId,
                    owners.get(folder.getOwnerUserId())));
        }
        return result;
    }

    private ExamQuestionFolderDTO.FolderVO toFolderVo(
            ExamQuestionFolder folder,
            Long questionCount,
            Long viewerId,
            User owner) {
        ExamQuestionFolderDTO.FolderVO vo = new ExamQuestionFolderDTO.FolderVO();
        copyFolderFields(vo, folder, questionCount, viewerId, owner);
        return vo;
    }

    private void copyFolderFields(
            ExamQuestionFolderDTO.FolderVO vo,
            ExamQuestionFolder folder,
            Long questionCount,
            Long viewerId,
            User owner) {
        vo.setId(folder.getId());
        vo.setName(folder.getName());
        vo.setVisibility(folder.getVisibility());
        vo.setVisibilityLabel(ExamQuestionFolder.VISIBILITY_PUBLIC.equals(folder.getVisibility()) ? "公共" : "私有");
        vo.setOwnerUserId(folder.getOwnerUserId());
        if (owner != null) {
            vo.setOwnerUsername(owner.getUsername());
            vo.setOwnerPersonalNumber(owner.getPersonalNumber());
        }
        vo.setQuestionCount(questionCount == null ? 0L : questionCount);
        vo.setCreateTime(folder.getCreateTime());
        vo.setUpdateTime(folder.getUpdateTime());
        vo.setOwnedByCurrentUser(Objects.equals(folder.getOwnerUserId(), viewerId));
    }

    private User loadUser(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId).orElse(null);
    }
}
