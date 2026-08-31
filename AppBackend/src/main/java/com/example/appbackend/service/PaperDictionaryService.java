package com.example.appbackend.service;

import com.example.appbackend.entity.PaperDictionary;
import com.example.appbackend.repository.PaperDictionaryRepository;
import com.example.appbackend.repository.PaperRepository;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.entity.Result;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class PaperDictionaryService {
    private final PaperDictionaryRepository repository;
    private final PaperRepository paperRepository;

    public PaperDictionaryService(PaperDictionaryRepository repository, PaperRepository paperRepository) {
        this.repository = repository;
        this.paperRepository = paperRepository;
    }

    public List<PaperDictionary> list(String type, Long userId) {
        requireUser(userId);
        return repository.findVisibleByType(type, userId);
    }

    @Transactional
    public PaperDictionary create(String type, String name, Long userId) {
        requireUser(userId);
        if (!Set.of("subject", "paper_category").contains(type)) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "不支持的字典类型");
        }
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isEmpty()) throw new IllegalArgumentException("名称不能为空");
        if (normalizedName.length() > 120) throw new IllegalArgumentException("名称不能超过120个字符");
        if (repository.findVisibleByTypeAndName(type, normalizedName, userId).isPresent()) {
            throw new BusinessException(Result.BAD_REQUEST_CODE,
                    "subject".equals(type) ? "该科目已存在" : "该试卷分类已存在");
        }

        PaperDictionary item = new PaperDictionary();
        item.setDictType(type);
        item.setDictCode("custom_" + userId + "_" + UUID.randomUUID().toString().replace("-", ""));
        item.setName(normalizedName);
        item.setCreatorId(userId);
        item.setSortOrder(repository.findByDictTypeAndEnabledTrueOrderBySortOrderAscIdAsc(type).size() + 1);
        item.setEnabled(true);
        return repository.save(item);
    }

    @Transactional
    public void delete(Long id, Long userId) {
        requireUser(userId);
        PaperDictionary item = repository.findById(id)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "字典项不存在"));
        if (item.getCreatorId() == null) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "系统默认字典不能删除");
        }
        if (!item.getCreatorId().equals(userId)) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "无权删除其他用户创建的字典项");
        }
        if ("subject".equals(item.getDictType()) && paperRepository.existsBySubjectIdAndCreatorId(item.getId(), userId)) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "该科目已被试卷使用，无法删除");
        }
        if ("paper_category".equals(item.getDictType()) && paperRepository.existsByCategoryAndCreatorId(item.getName(), userId)) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "该分类已被试卷使用，无法删除");
        }
        repository.delete(item);
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        }
    }

    @PostConstruct
    @Transactional
    public void initializeDefaults() {
        seed("subject", new String[][]{{"1", "Python程序设计"}, {"2", "Java程序设计"}, {"3", "数据库"}, {"4", "计算机网络"}, {"5", "数据结构"}});
        seed("question_type", new String[][]{{"single", "单选题"}, {"multiple", "多选题"}, {"true_false", "判断题"}, {"blank", "填空题"}, {"short_answer", "简答题"}, {"programming", "编程题"}});
        seed("difficulty", new String[][]{{"easy", "简单"}, {"medium", "中等"}, {"hard", "困难"}});
        seed("paper_category", new String[][]{{"final_exam", "期末考试"}, {"chapter_test", "章节测试"}, {"mock_exam", "模拟练习"}, {"interview", "面试练习"}, {"custom", "自定义"}});
        seed("bank_type", new String[][]{{"final_review", "期末复习"}, {"interview", "面试题"}, {"chapter_practice", "章节练习"}, {"key_questions", "重点题"}, {"wrong_questions", "错题整理"}, {"custom", "自定义"}});
    }

    private void seed(String type, String[][] values) {
        for (int i = 0; i < values.length; i++) {
            if (repository.findByDictTypeAndDictCode(type, values[i][0]).isPresent()) continue;
            PaperDictionary item = new PaperDictionary();
            item.setDictType(type);
            item.setDictCode(values[i][0]);
            item.setName(values[i][1]);
            item.setSortOrder(i + 1);
            item.setEnabled(true);
            repository.save(item);
        }
    }
}
