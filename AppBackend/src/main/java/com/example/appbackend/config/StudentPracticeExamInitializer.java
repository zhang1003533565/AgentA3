package com.example.appbackend.config;

import com.example.appbackend.dto.ExamPaperDTO;
import com.example.appbackend.entity.ExamPaper;
import com.example.appbackend.entity.ExamPaperQuestion;
import com.example.appbackend.entity.ExamQuestion;
import com.example.appbackend.repository.ExamPaperQuestionRepository;
import com.example.appbackend.repository.ExamPaperRepository;
import com.example.appbackend.repository.ExamQuestionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class StudentPracticeExamInitializer implements ApplicationRunner {
    private static final String SOURCE_AGENT = "system-practice-bank";
    private static final String SOURCE_TITLE = "Python基础综合题库（500题）";
    private static final String PAPER_TITLE = "Python基础能力随机考试";
    private static final int BANK_SIZE = 500;
    private static final int ATTEMPT_SIZE = 84;

    private final ExamQuestionRepository questionRepository;
    private final ExamPaperRepository paperRepository;
    private final ExamPaperQuestionRepository paperQuestionRepository;
    private final ObjectMapper objectMapper;

    public StudentPracticeExamInitializer(
            ExamQuestionRepository questionRepository,
            ExamPaperRepository paperRepository,
            ExamPaperQuestionRepository paperQuestionRepository,
            ObjectMapper objectMapper) {
        this.questionRepository = questionRepository;
        this.paperRepository = paperRepository;
        this.paperQuestionRepository = paperQuestionRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<ExamQuestion> bank = new ArrayList<>(
                questionRepository.findBySourceAgentAndStatusOrderByIdAsc(SOURCE_AGENT, 1));
        while (bank.size() < BANK_SIZE) {
            ExamQuestion created = new ExamQuestion();
            configureQuestion(created, bank.size() + 1);
            bank.add(questionRepository.save(created));
        }
        for (int index = 0; index < BANK_SIZE; index++) {
            configureQuestion(bank.get(index), index + 1);
        }
        bank = questionRepository.saveAll(bank.subList(0, BANK_SIZE));

        ExamPaper paper = paperRepository.findFirstByTitleAndStatus(PAPER_TITLE, 1)
                .orElseGet(this::createPaper);
        configurePaper(paper);
        paper = paperRepository.save(paper);
        Long currentPaperId = paper.getId();
        for (String legacyTitle : List.of("Python 基础能力随机测试", "Python基础能力随机测试")) {
            paperRepository.findFirstByTitleAndStatus(legacyTitle, 1)
                    .filter(legacy -> !legacy.getId().equals(currentPaperId))
                    .ifPresent(legacy -> {
                        legacy.setPublished(false);
                        paperRepository.save(legacy);
                    });
        }

        Map<Long, ExamPaperQuestion> existing = paperQuestionRepository
                .findByPaperIdOrderBySortOrderAscIdAsc(paper.getId()).stream()
                .collect(Collectors.toMap(ExamPaperQuestion::getQuestionId, Function.identity()));
        List<ExamPaperQuestion> synchronizedQuestions = new ArrayList<>();
        for (int index = 0; index < BANK_SIZE; index++) {
            ExamQuestion source = bank.get(index);
            ExamPaperQuestion target = existing.getOrDefault(source.getId(), new ExamPaperQuestion());
            target.setPaperId(paper.getId());
            target.setQuestionId(source.getId());
            target.setSortOrder(index + 1);
            target.setSectionOrder(sectionOrder(source.getType()));
            target.setScore(source.getScore());
            target.setType(source.getType());
            target.setStem(source.getStem());
            target.setBodyJson(source.getBodyJson());
            target.setAnswerJson(source.getAnswerJson());
            target.setAnalysis(source.getAnalysis());
            target.setScoringJson(source.getScoringJson());
            synchronizedQuestions.add(target);
        }
        paperQuestionRepository.saveAll(synchronizedQuestions);
    }

    private ExamPaper createPaper() {
        ExamPaper paper = new ExamPaper();
        paper.setCreatedBy(1L);
        paper.setStatus(1);
        paper.setPublished(true);
        paper.setPublishTime(LocalDateTime.now());
        paper.setPageSize(ExamPaperDTO.PageSize.A4);
        paper.setOrientation(ExamPaperDTO.Orientation.PORTRAIT);
        paper.setColumnsCount(1);
        paper.setSelectionMode(ExamPaperDTO.SelectionMode.RANDOM);
        return paper;
    }

    private void configurePaper(ExamPaper paper) {
        paper.setTitle(PAPER_TITLE);
        paper.setSubtitle("500题综合题库，每次随机84题，总分100分");
        paper.setDurationMinutes(120);
        paper.setPrecautions("请独立完成答题。答案会自动保存；交卷前可通过答题卡检查未答题目，考试结束后可查看成绩与解析。");
        paper.setQuestionCount(ATTEMPT_SIZE);
        paper.setTotalScore(BigDecimal.valueOf(100));
        paper.setPublished(true);
    }

    private void configureQuestion(ExamQuestion question, int number) {
        question.setSourceQuestionId("PY-PRACTICE-" + number);
        question.setScore(BigDecimal.ONE);
        question.setDifficulty(number % 5 == 0 ? "medium" : "easy");
        question.setTagsJson(json(List.of("Python基础", "学生考试")));
        question.setSourceTitle(SOURCE_TITLE);
        question.setSourceAgent(SOURCE_AGENT);
        question.setSourceScene("seed");
        question.setVisibility(ExamQuestion.VISIBILITY_PUBLIC);
        question.setStatus(1);
        question.setScoringJson(json(Map.of("mode", "exact", "rubrics", List.of())));

        if (number <= 200) {
            int left = number + 3;
            int right = number % 11 + 1;
            int correct = left + right;
            question.setType("single_choice");
            question.setStem("运行 Python 表达式 `" + left + " + " + right + "`，输出结果是？");
            question.setBodyJson(json(Map.of(
                    "options", List.of(
                            option("A", String.valueOf(correct)),
                            option("B", String.valueOf(correct + 1)),
                            option("C", String.valueOf(correct - 1)),
                            option("D", String.valueOf(correct + 2))),
                    "shuffleOptions", false)));
            question.setAnswerJson(json(Map.of("correctOption", "A")));
            question.setAnalysis("加法表达式的计算结果为 " + correct + "。");
            question.setKnowledgePointsJson(json(List.of("python.expression.arithmetic")));
        } else if (number <= 250) {
            question.setType("multiple_choice");
            question.setStem("下列哪些是 Python 的内置集合类型？");
            question.setBodyJson(json(Map.of(
                    "options", List.of(option("A", "list"), option("B", "dict"),
                            option("C", "set"), option("D", "interface")),
                    "shuffleOptions", false)));
            question.setAnswerJson(json(Map.of("correctOptions", List.of("A", "B", "C"))));
            question.setAnalysis("list、dict、set 是 Python 内置集合类型；interface 不是内置类型。");
            question.setKnowledgePointsJson(json(List.of("python.data_type.collection")));
        } else if (number <= 350) {
            boolean correct = number % 2 == 0;
            String statement = correct
                    ? "Python 的 list 是可变序列，可以使用 append 方法添加元素。"
                    : "Python 的 tuple 创建后可以直接修改其中的元素。";
            question.setType("true_false");
            question.setStem(statement);
            question.setBodyJson(json(Map.of("statement", statement)));
            question.setAnswerJson(json(Map.of("correct", correct)));
            question.setAnalysis(correct ? "list 支持原地修改和追加。" : "tuple 是不可变序列，不能直接修改元素。");
            question.setKnowledgePointsJson(json(List.of("python.data_type.sequence")));
        } else if (number <= 400) {
            question.setType("fill_blank");
            question.setStem("Python 中定义函数使用关键字 ____，返回结果使用关键字 ____。");
            question.setBodyJson(json(Map.of("blanks", List.of(
                    Map.of("id", "blank-1", "placeholder", "第1空"),
                    Map.of("id", "blank-2", "placeholder", "第2空")))));
            question.setAnswerJson(json(Map.of("blanks", List.of(
                    Map.of("id", "blank-1", "answers", List.of("def")),
                    Map.of("id", "blank-2", "answers", List.of("return"))))));
            question.setAnalysis("定义函数使用 def，返回结果使用 return。");
            question.setKnowledgePointsJson(json(List.of("python.function.syntax")));
        } else if (number <= 425) {
            configureRubricQuestion(question, "short_answer",
                    "简述 Python 列表与元组的主要区别。",
                    "列表可变，使用方括号；元组不可变，使用圆括号；应根据是否需要修改数据选择。",
                    List.of(
                            rubric("指出列表可变", 1, "列表", "可变"),
                            rubric("指出元组不可变", 1, "元组", "不可变"),
                            rubric("说明列表使用方括号", 1, "列表", "方括号"),
                            rubric("说明元组使用圆括号", 1, "元组", "圆括号"),
                            rubric("说明使用场景", 1, "修改", "选择")),
                    "python.data_type.sequence");
        } else if (number <= 450) {
            configureRubricQuestion(question, "essay",
                    "论述异常处理对 Python 程序可靠性的作用。",
                    "异常处理可捕获错误、避免程序意外终止、提供恢复或提示、记录问题，并应避免无范围地捕获所有异常。",
                    List.of(
                            rubric("说明捕获运行错误", 1, "捕获", "错误"),
                            rubric("说明避免意外终止", 1, "避免", "终止"),
                            rubric("说明恢复或友好提示", 1, "恢复", "提示"),
                            rubric("说明日志记录", 1, "记录", "日志"),
                            rubric("说明精确捕获原则", 1, "异常", "范围")),
                    "python.exception.reliability");
        } else if (number <= 475) {
            configureRubricQuestion(question, "material_analysis",
                    "材料：某程序循环读取用户输入并转换为整数，遇到非数字输入时程序直接退出。请分析问题并提出改进方案。",
                    "问题是转换异常未处理。应使用 try/except 捕获 ValueError，提示用户重新输入，通过循环继续，并可记录异常信息。",
                    List.of(
                            rubric("识别转换异常", 1, "转换", "异常"),
                            rubric("使用异常处理结构", 1, "try", "except"),
                            rubric("捕获ValueError", 1, "ValueError"),
                            rubric("提示并重新输入", 1, "提示", "重新"),
                            rubric("循环继续或记录", 1, "循环", "记录")),
                    "python.exception.application");
        } else {
            configureRubricQuestion(question, "calculation",
                    "某算法包含一个执行 n 次的外层循环，每次外层循环内执行 n 次基本操作。请计算基本操作次数，并分析时间复杂度。",
                    "外层执行 n 次，内层每次执行 n 次，总操作次数为 n×n=n²，忽略常数和低阶项，时间复杂度为 O(n²)。",
                    List.of(
                            rubric("列出外层次数", 1, "外层", "n"),
                            rubric("列出内层次数", 1, "内层", "n"),
                            rubric("写出乘法关系", 1, "n×n"),
                            rubric("得出操作次数", 1, "n²"),
                            rubric("得出时间复杂度", 1, "O(n²)")),
                    "python.algorithm.complexity");
        }
        question.setRawQuestionJson(json(Map.of(
                "id", question.getSourceQuestionId(),
                "type", question.getType(),
                "stem", question.getStem())));
    }

    private Map<String, String> option(String key, String text) {
        return Map.of("key", key, "text", text);
    }

    private void configureRubricQuestion(
            ExamQuestion question,
            String type,
            String stem,
            String referenceAnswer,
            List<Map<String, Object>> rubrics,
            String knowledgePoint) {
        question.setType(type);
        question.setScore(BigDecimal.valueOf(5));
        question.setStem(stem);
        question.setBodyJson(json(Map.of(
                "answerMode", "text",
                "minLength", 20,
                "rubricCount", rubrics.size())));
        question.setAnswerJson(json(Map.of("referenceAnswer", referenceAnswer)));
        question.setAnalysis(referenceAnswer);
        question.setScoringJson(json(Map.of(
                "mode", type.equals("calculation") ? "step" : "rubric",
                "rubrics", rubrics)));
        question.setKnowledgePointsJson(json(List.of(knowledgePoint)));
    }

    private Map<String, Object> rubric(String criterion, int score, String... keywords) {
        return Map.of(
                "criterion", criterion,
                "score", score,
                "keywords", List.of(keywords));
    }

    private int sectionOrder(String type) {
        return switch (type) {
            case "single_choice" -> 1;
            case "multiple_choice" -> 2;
            default -> 3;
        };
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
