package com.example.appbackend.service.impl;

import com.example.appbackend.dto.PythonProblemDTO;
import com.example.appbackend.entity.PythonProblem;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.PythonProblemRepository;
import com.example.appbackend.service.PythonProblemService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@Transactional
public class PythonProblemServiceImpl implements PythonProblemService {

    private static final Set<String> DIFFICULTIES = Set.of("easy", "medium", "hard");

    private static final String GENERATOR_AGENT_NAME = "python_problem_generator_agent";

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private PythonProblemRepository repository;

    @Autowired
    private PythonAiProxyService pythonAiProxyService;

    @Autowired
    private com.example.appbackend.service.CodeJudgeService codeJudgeService;

    // ==================== 小程序端 ====================

    @Override
    @Transactional(readOnly = true)
    public List<PythonProblemDTO.SummaryVO> listPublic() {
        List<PythonProblemDTO.SummaryVO> list = new ArrayList<>();
        for (PythonProblem p : repository.findByEnabledTrueOrderByNumberAsc()) {
            list.add(toSummary(p));
        }
        return list;
    }

    @Override
    @Transactional(readOnly = true)
    public PythonProblemDTO.DetailVO getDetail(Long id) {
        PythonProblem p = repository.findById(id)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "题目不存在"));
        if (!Boolean.TRUE.equals(p.getEnabled())) {
            throw new BusinessException(Result.NOT_FOUND_CODE, "题目不存在");
        }
        return toDetail(p);
    }

    @Override
    @Transactional(readOnly = true)
    public String getSolutionJson(Long id) {
        return repository.findById(id)
                .map(PythonProblem::getSolution)
                .filter(s -> s != null && !s.trim().isEmpty())
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public PythonProblemDTO.AIGenerateResponse aiGenerate(PythonProblemDTO.AIGenerateRequest req, String authorization) {
        String prompt = req.getPrompt() == null ? "" : req.getPrompt().trim();
        if (prompt.isEmpty()) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "请描述你的出题需求");
        }
        int count = req.getCount() == null ? 1 : req.getCount();
        if (count < 1 || count > 5) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "生成数量需在 1-5 之间");
        }
        String difficulty = req.getDifficulty() == null ? "" : req.getDifficulty().trim();
        if (!difficulty.isEmpty() && !DIFFICULTIES.contains(difficulty)) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "难度必须为 easy / medium / hard");
        }

        // 组装传给生成智能体的输入
        Map<String, Object> genRequest = new HashMap<>();
        genRequest.put("prompt", prompt);
        if (req.getTopic() != null && !req.getTopic().trim().isEmpty()) {
            genRequest.put("topic", req.getTopic().trim());
        }
        genRequest.put("count", count);
        if (!difficulty.isEmpty()) {
            genRequest.put("difficulty", difficulty);
        }
        if (req.getPreviousFeedback() != null && !req.getPreviousFeedback().trim().isEmpty()) {
            genRequest.put("previousFeedback", req.getPreviousFeedback().trim());
        }
        if (req.getPreviousProblems() != null && !req.getPreviousProblems().isEmpty()) {
            genRequest.put("previousProblems", req.getPreviousProblems());
        }
        // 参考题：优先用前端显式选择的题目标题（保证命中），否则回退到描述子串匹配
        String refTitle = req.getReferenceTitle() == null ? "" : req.getReferenceTitle().trim();
        Map<String, Object> reference = null;
        if (!refTitle.isEmpty()) {
            reference = matchReferenceProblemByTitle(refTitle);
        }
        if (reference == null) {
            reference = matchReferenceProblem(prompt);
        }
        if (reference != null) {
            genRequest.put("reference", reference);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("agentName", GENERATOR_AGENT_NAME);
        payload.put("input", writeJson(genRequest));
        payload.put("metadata", Map.of("requestPurpose", "question_generation"));
        payload.put("maxQuestions", count);
        if (!difficulty.isEmpty()) {
            payload.put("difficulty", difficulty);
        }

        Object answerObj = pythonAiProxyService.generatePythonProblems(payload, authorization);
        String answer = answerObj instanceof String s ? s
                : (answerObj instanceof Map<?, ?> m && m.get("answer") instanceof String s2 ? s2 : null);
        if (answer == null || answer.isBlank()) {
            throw new BusinessException(Result.ERROR_CODE, "AI 未返回题目内容，请重试或检查模型配置");
        }

        // 解析 {spec, problems} 结构
        Map<String, Object> spec = new LinkedHashMap<>();
        List<Map<String, Object>> problems = parseGeneratedResponse(answer, spec);
        if (problems.isEmpty()) {
            throw new BusinessException(Result.ERROR_CODE, "AI 生成结果为空或格式不正确，请重试");
        }
        int nextNumber = nextProblemNumber();
        List<PythonProblemDTO.GeneratedProblemVO> result = new ArrayList<>();
        for (Map<String, Object> p : problems) {
            PythonProblemDTO.GeneratedProblemVO vo = new PythonProblemDTO.GeneratedProblemVO();
            vo.setNumber(nextNumber++);
            vo.setTitle(nullableText(p.get("title")));
            vo.setDifficulty(normalizeDifficulty(nullableText(p.get("difficulty")), difficulty));
            vo.setTags(asStringList(p.get("tags")));
            vo.setDescription(nullableText(p.get("description")));
            vo.setExamples(asMapList(p.get("examples")));
            vo.setDefaultCode(nullableText(p.get("defaultCode")));
            vo.setFuncName(nullableText(p.get("funcName")));
            vo.setTestcases(asMapList(p.get("testcases")));
            vo.setSolution(asMapList(p.get("solution")));
            selfValidate(vo);
            result.add(vo);
        }
        PythonProblemDTO.AIGenerateResponse response = new PythonProblemDTO.AIGenerateResponse();
        response.setSpec(spec);
        response.setProblems(result);
        return response;
    }

    // ==================== AI 生成辅助 ====================

    /**
     * 用例自校验：用参考代码（solution 第一条）跑题目自己生成的测试用例，
     * 验证"参考代码能通过自己的用例"。通不过说明用例期望或参考代码有问题，
     * 标记为存疑提示人工审核，避免坏题入库。
     */
    private void selfValidate(PythonProblemDTO.GeneratedProblemVO vo) {
        try {
            String funcName = vo.getFuncName() == null ? "" : vo.getFuncName().trim();
            List<Map<String, Object>> solution = vo.getSolution();
            String code = "";
            if (solution != null && !solution.isEmpty()) {
                Object c = solution.get(0).get("code");
                code = c == null ? "" : String.valueOf(c).trim();
            }
            List<Map<String, Object>> testcases = vo.getTestcases();
            if (funcName.isEmpty() || code.isEmpty() || testcases == null || testcases.isEmpty()) {
                vo.setSelfCheck("skip");
                return;
            }
            List<com.example.appbackend.service.CodeJudgeService.JudgeCase> cases = new ArrayList<>();
            for (Map<String, Object> tc : testcases) {
                com.example.appbackend.service.CodeJudgeService.JudgeCase jc =
                        new com.example.appbackend.service.CodeJudgeService.JudgeCase();
                jc.input = nullableText(tc.get("input"));
                jc.expected = nullableText(tc.get("expected"));
                jc.mode = nullableText(tc.get("mode"));
                Object accepts = tc.get("accepts");
                if (accepts instanceof List<?> acceptList) {
                    List<String> acc = new ArrayList<>();
                    for (Object a : acceptList) {
                        if (a != null) acc.add(String.valueOf(a));
                    }
                    jc.accepts = acc;
                }
                cases.add(jc);
            }
            Map<String, Object> judgeResult = codeJudgeService.judge(code, funcName, cases);
            String status = String.valueOf(judgeResult.get("status"));
            if ("ac".equals(status)) {
                vo.setSelfCheck("pass");
            } else {
                vo.setSelfCheck("fail");
                vo.setSelfCheckDetail("参考代码未通过自身测试用例（" + judgeResult.get("passed")
                        + "/" + judgeResult.get("total") + "，" + judgeResult.get("statusLabel")
                        + "），请核对测试用例或参考代码后再入库");
            }
        } catch (Exception e) {
            // 自校验失败不阻断生成流程，标记为未校验
            vo.setSelfCheck("skip");
        }
    }

    /** 从题库中匹配用户描述里提到的参考题：标题作为子串出现在 prompt 中即命中 */
    private Map<String, Object> matchReferenceProblem(String prompt) {
        for (PythonProblem p : repository.findAll()) {
            String title = p.getTitle() == null ? "" : p.getTitle().trim();
            if (title.length() < 2) {
                continue;
            }
            if (prompt.contains(title)) {
                return buildReference(p);
            }
        }
        return null;
    }

    /** 按标题精确匹配参考题（前端显式选择时使用，保证命中） */
    private Map<String, Object> matchReferenceProblemByTitle(String title) {
        String target = title == null ? "" : title.trim();
        if (target.isEmpty()) {
            return null;
        }
        for (PythonProblem p : repository.findAll()) {
            String t = p.getTitle() == null ? "" : p.getTitle().trim();
            if (t.equals(target)) {
                return buildReference(p);
            }
        }
        return null;
    }

    private Map<String, Object> buildReference(PythonProblem p) {
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("title", p.getTitle() == null ? "" : p.getTitle().trim());
        ref.put("description", p.getDescription() == null ? "" : p.getDescription());
        ref.put("examples", readJsonMaps(p.getExamples()));
        ref.put("funcName", p.getFuncName() == null ? "" : p.getFuncName());
        ref.put("tags", readJsonList(p.getTags()));
        return ref;
    }

    /** 解析生成智能体的 answer JSON：{spec, problems}，spec 可能缺失时容错 */
    private List<Map<String, Object>> parseGeneratedResponse(String answer, Map<String, Object> spec) {
        try {
            Map<String, Object> root = JSON.readValue(answer, new TypeReference<Map<String, Object>>() {});
            if (root == null) {
                return Collections.emptyList();
            }
            if (root.get("spec") instanceof Map<?, ?> specMap) {
                spec.putAll(new LinkedHashMap<>((Map<String, Object>) specMap));
            }
            if (root.get("problems") instanceof List<?> problems) {
                List<Map<String, Object>> list = new ArrayList<>();
                for (Object item : problems) {
                    if (item instanceof Map<?, ?> m) {
                        list.add(new LinkedHashMap<>((Map<String, Object>) m));
                    }
                }
                return list;
            }
        } catch (Exception ignored) {
            // 结构异常时走下方空结果提示
        }
        return Collections.emptyList();
    }

    /** 下一可用题号：当前最大题号 + 1（题号仅展示用，允许空洞） */
    private int nextProblemNumber() {
        Integer maxNumber = repository.findMaxNumber();
        return (maxNumber == null ? 0 : maxNumber) + 1;
    }

    private String normalizeDifficulty(String aiValue, String requested) {
        if (DIFFICULTIES.contains(aiValue)) {
            return aiValue;
        }
        return DIFFICULTIES.contains(requested) ? requested : "medium";
    }

    private String nullableText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private List<String> asStringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
        }
        return result;
    }

    private List<Map<String, Object>> asMapList(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    result.add(new LinkedHashMap<>((Map<String, Object>) m));
                }
            }
        }
        return result;
    }

    // ==================== 管理端 ====================

    @Override
    @Transactional(readOnly = true)
    public List<PythonProblemDTO.AdminVO> listAdmin(String keyword, String difficulty, Boolean enabled) {
        Specification<PythonProblem> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.trim().isEmpty()) {
                String like = "%" + keyword.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("title"), like),
                        cb.like(root.get("number").as(String.class), like)
                ));
            }
            if (difficulty != null && !difficulty.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("difficulty"), difficulty.trim()));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<PythonProblemDTO.AdminVO> list = new ArrayList<>();
        for (PythonProblem p : repository.findAll(spec)) {
            list.add(toAdmin(p));
        }
        list.sort((a, b) -> Integer.compare(
                a.getNumber() == null ? 0 : a.getNumber(),
                b.getNumber() == null ? 0 : b.getNumber()));
        return list;
    }

    @Override
    public PythonProblemDTO.AdminVO create(PythonProblemDTO.ProblemRequest req) {
        validate(req, null);
        PythonProblem p = new PythonProblem();
        applyRequest(p, req);
        return toAdmin(repository.save(p));
    }

    @Override
    public PythonProblemDTO.AdminVO update(Long id, PythonProblemDTO.ProblemRequest req) {
        PythonProblem p = repository.findById(id)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "题目不存在"));
        validate(req, id);
        applyRequest(p, req);
        return toAdmin(repository.save(p));
    }

    @Override
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new BusinessException(Result.NOT_FOUND_CODE, "题目不存在");
        }
        repository.deleteById(id);
    }

    // ==================== 内部方法 ====================

    private void validate(PythonProblemDTO.ProblemRequest req, Long excludeId) {
        if (req.getNumber() == null) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "题号不能为空");
        }
        if (req.getTitle() == null || req.getTitle().trim().isEmpty()) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "标题不能为空");
        }
        if (req.getDifficulty() == null || !DIFFICULTIES.contains(req.getDifficulty())) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "难度必须为 easy / medium / hard");
        }
        repository.findByNumber(req.getNumber()).ifPresent(exist -> {
            if (excludeId == null || !Objects.equals(exist.getId(), excludeId)) {
                throw new BusinessException(Result.BAD_REQUEST_CODE, "题号已存在: " + req.getNumber());
            }
        });
        // 判题用例校验：填写了函数名则必须提供合法用例
        if (req.getFuncName() != null && !req.getFuncName().trim().isEmpty()) {
            List<Map<String, Object>> cases = req.getTestcases();
            if (cases == null || cases.isEmpty()) {
                throw new BusinessException(Result.BAD_REQUEST_CODE, "可判题题目必须至少包含一个测试用例");
            }
            for (Map<String, Object> tc : cases) {
                Object input = tc.get("input");
                Object expected = tc.get("expected");
                if (!(input instanceof String) || ((String) input).trim().isEmpty()
                        || !(expected instanceof String)) {
                    throw new BusinessException(Result.BAD_REQUEST_CODE, "测试用例需包含 input 与 expected 字符串字段");
                }
            }
        }
    }

    private void applyRequest(PythonProblem p, PythonProblemDTO.ProblemRequest req) {
        p.setNumber(req.getNumber());
        p.setTitle(req.getTitle().trim());
        p.setDifficulty(req.getDifficulty());
        p.setPassRate(req.getPassRate());
        p.setSubmissions(req.getSubmissions());
        p.setDescription(req.getDescription());
        p.setDefaultCode(req.getDefaultCode());
        p.setFuncName(req.getFuncName() == null ? "" : req.getFuncName().trim());
        p.setEnabled(req.getEnabled() == null ? Boolean.TRUE : req.getEnabled());
        p.setTags(writeJson(req.getTags() == null ? Collections.emptyList() : req.getTags()));
        p.setExamples(writeJson(req.getExamples() == null ? Collections.emptyList() : req.getExamples()));
        p.setTestcases(writeJson(req.getTestcases() == null ? Collections.emptyList() : req.getTestcases()));
        p.setSimilarIds(writeJson(req.getSimilarIds() == null ? Collections.emptyList() : req.getSimilarIds()));
        p.setSolution(writeJson(req.getSolution() == null ? Collections.emptyList() : req.getSolution()));
    }

    private PythonProblemDTO.SummaryVO toSummary(PythonProblem p) {
        PythonProblemDTO.SummaryVO vo = new PythonProblemDTO.SummaryVO();
        vo.setId(p.getId());
        vo.setNumber(p.getNumber());
        vo.setTitle(p.getTitle());
        vo.setDifficulty(p.getDifficulty());
        vo.setPassRate(p.getPassRate());
        vo.setSubmissions(p.getSubmissions());
        vo.setTags(readJsonList(p.getTags()));
        vo.setJudgeable(isJudgeable(p));
        return vo;
    }

    private PythonProblemDTO.DetailVO toDetail(PythonProblem p) {
        PythonProblemDTO.DetailVO vo = new PythonProblemDTO.DetailVO();
        vo.setId(p.getId());
        vo.setNumber(p.getNumber());
        vo.setTitle(p.getTitle());
        vo.setDifficulty(p.getDifficulty());
        vo.setPassRate(p.getPassRate());
        vo.setSubmissions(p.getSubmissions());
        vo.setTags(readJsonList(p.getTags()));
        vo.setJudgeable(isJudgeable(p));
        vo.setDescription(p.getDescription() == null ? "" : p.getDescription());
        vo.setExamples(readJsonMaps(p.getExamples()));
        vo.setDefaultCode(p.getDefaultCode() == null ? "" : p.getDefaultCode());
        vo.setFuncName(p.getFuncName() == null ? "" : p.getFuncName());
        vo.setTestcases(readJsonMaps(p.getTestcases()));
        vo.setSimilarIds(readJsonLongs(p.getSimilarIds()));
        return vo;
    }

    private PythonProblemDTO.AdminVO toAdmin(PythonProblem p) {
        PythonProblemDTO.AdminVO vo = new PythonProblemDTO.AdminVO();
        vo.setId(p.getId());
        vo.setNumber(p.getNumber());
        vo.setTitle(p.getTitle());
        vo.setDifficulty(p.getDifficulty());
        vo.setPassRate(p.getPassRate());
        vo.setSubmissions(p.getSubmissions());
        vo.setTags(readJsonList(p.getTags()));
        vo.setJudgeable(isJudgeable(p));
        vo.setEnabled(p.getEnabled());
        vo.setDescription(p.getDescription());
        vo.setExamples(readJsonMaps(p.getExamples()));
        vo.setDefaultCode(p.getDefaultCode());
        vo.setFuncName(p.getFuncName());
        vo.setTestcases(readJsonMaps(p.getTestcases()));
        vo.setSimilarIds(readJsonLongs(p.getSimilarIds()));
        vo.setSolution(readJsonMaps(p.getSolution()));
        vo.setCreateTime(p.getCreateTime());
        vo.setUpdateTime(p.getUpdateTime());
        return vo;
    }

    private boolean isJudgeable(PythonProblem p) {
        return p.getFuncName() != null && !p.getFuncName().trim().isEmpty()
                && !readJsonMaps(p.getTestcases()).isEmpty();
    }

    private String writeJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "JSON 序列化失败: " + e.getMessage());
        }
    }

    private List<String> readJsonList(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return JSON.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<Map<String, Object>> readJsonMaps(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return JSON.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<Long> readJsonLongs(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return JSON.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
