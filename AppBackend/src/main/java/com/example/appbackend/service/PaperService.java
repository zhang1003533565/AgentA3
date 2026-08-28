package com.example.appbackend.service;

import com.example.appbackend.dto.PaperDTO;
import com.example.appbackend.entity.*;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PaperService {
    @Autowired private QuestionBankRepository bankRepo;
    @Autowired private QuestionRepository questionRepo;
    @Autowired private QuestionBankItemRepository bankItemRepo;
    @Autowired private QuestionFavoriteRepository favoriteRepo;
    @Autowired private PaperRepository paperRepo;
    @Autowired private PaperQuestionRepository paperQuestionRepo;
    @Autowired private PaperLayoutRepository paperLayoutRepo;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public List<PaperDTO.BankVO> listBanks(Long userId, String visibility, String keyword) {
        List<QuestionBank> banks = "private".equals(visibility) ? bankRepo.findByOwnerIdOrderByUpdateTimeDesc(userId) : bankRepo.findByVisibilityOrderByUpdateTimeDesc("public");
        return banks.stream().filter(b -> keyword == null || keyword.isBlank() || b.getName().toLowerCase().contains(keyword.toLowerCase())).map(this::bankVO).toList();
    }
    @Transactional public PaperDTO.BankVO saveBank(PaperDTO.BankRequest req, Long userId, Long id) {
        if (req.getName() == null || req.getName().isBlank()) throw new BusinessException(Result.BAD_REQUEST_CODE, "题库名称不能为空");
        QuestionBank b = id == null ? new QuestionBank() : bankRepo.findById(id).orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "题库不存在"));
        if (id != null && !Objects.equals(b.getOwnerId(), userId)) throw new BusinessException(Result.FORBIDDEN_CODE, "无权修改该题库");
        String visibility = "shared".equals(req.getVisibility()) ? "shared" : "private";
        b.setName(req.getName().trim()); b.setSubjectId(req.getSubjectId()); b.setDescription(req.getDescription()); b.setBankType(req.getBankType()); b.setVisibility(visibility); b.setOwnerId(userId);
        return bankVO(bankRepo.save(b));
    }
    @Transactional public void deleteBank(Long id, Long userId) { QuestionBank b = bankRepo.findById(id).orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "题库不存在")); if (!Objects.equals(b.getOwnerId(), userId)) throw new BusinessException(Result.FORBIDDEN_CODE, "无权删除该题库"); bankItemRepo.deleteByBankId(id); bankRepo.delete(b); }
    @Transactional public void addToBank(Long bankId, Long questionId, Long userId) { QuestionBank b=bankRepo.findById(bankId).orElseThrow(()->new BusinessException(Result.NOT_FOUND_CODE,"题库不存在")); if(!Objects.equals(b.getOwnerId(),userId)) throw new BusinessException(Result.FORBIDDEN_CODE,"无权操作该题库"); accessibleQuestion(questionId, userId); if(bankItemRepo.findByBankIdAndQuestionId(bankId,questionId).isPresent()) throw new BusinessException(Result.BAD_REQUEST_CODE,"题目已在该题库中"); QuestionBankItem item=new QuestionBankItem();item.setBankId(bankId);item.setQuestionId(questionId);item.setAddedBy(userId);bankItemRepo.save(item); }

    public PaperDTO.BankVO getBank(Long bankId, Long userId) { QuestionBank b=bankRepo.findById(bankId).orElseThrow(()->new BusinessException(Result.NOT_FOUND_CODE,"题库不存在")); if(!Objects.equals(b.getOwnerId(),userId)) throw new BusinessException(Result.FORBIDDEN_CODE,"无权查看该题库"); return bankVO(b); }
    @Transactional public void removeFromBank(Long bankId, Long questionId, Long userId) { QuestionBank b=bankRepo.findById(bankId).orElseThrow(()->new BusinessException(Result.NOT_FOUND_CODE,"题库不存在")); if(!Objects.equals(b.getOwnerId(),userId)) throw new BusinessException(Result.FORBIDDEN_CODE,"无权操作该题库"); if(bankItemRepo.findByBankIdAndQuestionId(bankId,questionId).isEmpty()) throw new BusinessException(Result.NOT_FOUND_CODE,"题目不在该题库中"); bankItemRepo.deleteByBankIdAndQuestionId(bankId,questionId); }
    public List<PaperDTO.QuestionVO> questions(Long bankId, Long userId, String keyword, String type, String difficulty, Long paperId, boolean favorites) {
        validatePaperAccess(paperId, userId);
        List<Question> qs;
        if (favorites) qs = questionRepo.findAccessibleFavorites(userId);
        else if (bankId == null) qs = questionRepo.findAllInPublicBanks();
        else { QuestionBank b=bankRepo.findById(bankId).orElseThrow(()->new BusinessException(Result.NOT_FOUND_CODE,"题库不存在")); if(!"public".equals(b.getVisibility()) && !Objects.equals(b.getOwnerId(),userId)) throw new BusinessException(Result.FORBIDDEN_CODE,"无权查看该题库"); qs=questionRepo.findAllInBank(bankId); }
        Set<Long> favIds = favoriteRepo.findByUserIdOrderByCreateTimeDesc(userId).stream().map(QuestionFavorite::getQuestionId).collect(Collectors.toSet());
        Set<Long> selected = paperId == null ? Set.of() : paperQuestionRepo.findByPaperIdOrderByQuestionOrderAsc(paperId).stream().map(PaperQuestion::getQuestionId).collect(Collectors.toSet());
        return qs.stream().filter(q -> keyword == null || keyword.isBlank() || q.getContent().contains(keyword) || Objects.toString(q.getKnowledgePoint(), "").contains(keyword)).filter(q -> type == null || type.isBlank() || type.equals(q.getQuestionType())).filter(q -> difficulty == null || difficulty.isBlank() || difficulty.equals(q.getDifficulty())).map(q -> questionVO(q, favIds.contains(q.getId()), selected.contains(q.getId()))).toList();
    }
    public PaperDTO.QuestionVO questionDetail(Long questionId, Long userId, Long paperId) {
        validatePaperAccess(paperId, userId);
        Question q = accessibleQuestion(questionId, userId);
        boolean selected = paperId != null && paperQuestionRepo.findByPaperIdAndQuestionId(paperId, questionId).isPresent();
        return questionVO(q, favoriteRepo.findByUserIdAndQuestionId(userId, questionId).isPresent(), selected);
    }
    @Transactional public boolean toggleFavorite(Long questionId, Long userId, boolean add) { Optional<QuestionFavorite> old = favoriteRepo.findByUserIdAndQuestionId(userId, questionId); if (add && old.isEmpty()) { Question q = accessibleQuestion(questionId, userId); QuestionFavorite f = new QuestionFavorite(); f.setUserId(userId); f.setQuestionId(q.getId()); favoriteRepo.save(f); } else if (!add) old.ifPresent(favoriteRepo::delete); return add; }

    @Transactional public PaperDTO.PaperVO createPaper(PaperDTO.PaperRequest req, Long userId) { validatePaper(req); Paper p = new Paper(); apply(p, req); p.setCreatorId(userId); p.setStatus("draft"); return paperVO(paperRepo.save(p), userId); }
    @Transactional public PaperDTO.PaperVO updatePaper(Long id, PaperDTO.PaperRequest req, Long userId) { Paper p = ownPaper(id, userId); validatePaper(req); apply(p, req); return paperVO(paperRepo.save(p), userId); }
    public List<PaperDTO.PaperVO> listPapers(Long userId, String status, String keyword) { return paperRepo.findByCreatorIdOrderByUpdateTimeDesc(userId).stream().filter(p -> status == null || status.isBlank() || status.equals(p.getStatus())).filter(p -> keyword == null || keyword.isBlank() || p.getName().contains(keyword)).map(p -> paperVO(p, userId)).toList(); }
    public PaperDTO.PaperVO getPaper(Long id, Long userId) { return paperVO(ownPaper(id, userId), userId); }
    @Transactional public PaperDTO.PaperVO addQuestion(Long paperId, PaperDTO.QuestionRequest req, Long userId) { Paper p = ownPaper(paperId, userId); if (!"draft".equals(p.getStatus())) throw new BusinessException(Result.BAD_REQUEST_CODE, "已完成试卷不能修改"); accessibleQuestion(req.getQuestionId(), userId); if (paperQuestionRepo.findByPaperIdAndQuestionId(paperId, req.getQuestionId()).isPresent()) throw new BusinessException(Result.BAD_REQUEST_CODE, "题目已加入试卷"); List<PaperQuestion> all = paperQuestionRepo.findByPaperIdOrderByQuestionOrderAsc(paperId); PaperQuestion pq = new PaperQuestion(); pq.setPaperId(paperId); pq.setQuestionId(req.getQuestionId()); pq.setQuestionOrder(req.getQuestionOrder() == null ? all.size() + 1 : req.getQuestionOrder()); pq.setScore(req.getScore() == null || req.getScore() <= 0 ? 5 : req.getScore()); pq.setSourceType(req.getSourceType()); pq.setSourceId(req.getSourceId()); paperQuestionRepo.save(pq); return paperVO(p, userId); }
    @Transactional public PaperDTO.PaperVO updateQuestion(Long paperId, Long questionId, PaperDTO.ScoreRequest req, Long userId) { Paper p = ownPaper(paperId, userId); PaperQuestion pq = paperQuestionRepo.findByPaperIdAndQuestionId(paperId, questionId).orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "试卷题目不存在")); if (req.getScore() != null) { if (req.getScore() <= 0) throw new BusinessException(Result.BAD_REQUEST_CODE, "分值必须大于0"); pq.setScore(req.getScore()); }
        if (req.getQuestionOrder() != null && !Objects.equals(req.getQuestionOrder(), pq.getQuestionOrder())) { int oldOrder = pq.getQuestionOrder(); PaperQuestion other = paperQuestionRepo.findByPaperIdOrderByQuestionOrderAsc(paperId).stream().filter(x -> Objects.equals(x.getQuestionOrder(), req.getQuestionOrder())).findFirst().orElse(null); pq.setQuestionOrder(1_000_000 + oldOrder); paperQuestionRepo.saveAndFlush(pq); if (other != null) { other.setQuestionOrder(oldOrder); paperQuestionRepo.saveAndFlush(other); } pq.setQuestionOrder(req.getQuestionOrder()); }
        paperQuestionRepo.save(pq); return paperVO(p, userId); }
    @Transactional
    public PaperDTO.PaperVO removeQuestion(Long paperId, Long questionId, Long userId) {
        Paper p = ownPaper(paperId, userId);
        paperQuestionRepo.deleteByPaperIdAndQuestionId(paperId, questionId);
        paperQuestionRepo.flush();

        List<PaperQuestion> remaining = paperQuestionRepo.findByPaperIdOrderByQuestionOrderAsc(paperId);
        for (int index = 0; index < remaining.size(); index++) {
            remaining.get(index).setQuestionOrder(index + 1);
        }
        paperQuestionRepo.saveAll(remaining);
        paperQuestionRepo.flush();
        return paperVO(p, userId);
    }
    @Transactional public PaperDTO.PaperVO complete(Long id, Long userId) { Paper p = ownPaper(id, userId); List<PaperQuestion> qs = paperQuestionRepo.findByPaperIdOrderByQuestionOrderAsc(id); if (qs.isEmpty()) throw new BusinessException(Result.BAD_REQUEST_CODE, "至少选择一道题"); p.setStatus("completed"); return paperVO(paperRepo.save(p), userId); }
    @Transactional public PaperDTO.PaperVO copy(Long id, Long userId) { Paper old = ownPaper(id, userId); Paper p = new Paper(); p.setName(old.getName() + "（副本）"); p.setSubjectId(old.getSubjectId()); p.setSubject(old.getSubject()); p.setCategory(old.getCategory()); p.setRemark(old.getRemark()); p.setDuration(old.getDuration()); p.setCreatorId(userId); p.setStatus("draft"); p = paperRepo.save(p); for (PaperQuestion q : paperQuestionRepo.findByPaperIdOrderByQuestionOrderAsc(id)) { PaperQuestion copy = new PaperQuestion(); copy.setPaperId(p.getId()); copy.setQuestionId(q.getQuestionId()); copy.setQuestionOrder(q.getQuestionOrder()); copy.setScore(q.getScore()); copy.setSourceType(q.getSourceType()); copy.setSourceId(q.getSourceId()); paperQuestionRepo.save(copy); } return paperVO(p, userId); }
    @Transactional
    public void deletePaper(Long id, Long userId) {
        Paper paper = ownPaper(id, userId);
        paperLayoutRepo.deleteByPaperId(id);
        paperLayoutRepo.flush();
        paperQuestionRepo.deleteAllInBatch(paperQuestionRepo.findByPaperIdOrderByQuestionOrderAsc(id));
        paperRepo.delete(paper);
        paperRepo.flush();
    }

    private Paper ownPaper(Long id, Long userId) { Paper p = paperRepo.findById(id).orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "试卷不存在")); if (!Objects.equals(p.getCreatorId(), userId)) throw new BusinessException(Result.FORBIDDEN_CODE, "无权操作该试卷"); return p; }
    private void validatePaperAccess(Long paperId, Long userId) { if (paperId != null) ownPaper(paperId, userId); }
    private Question accessibleQuestion(Long questionId, Long userId) { return questionRepo.findAccessibleById(questionId, userId).orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "题目不存在或无权访问")); }
    private void validatePaper(PaperDTO.PaperRequest r) { if (r.getName() == null || r.getName().isBlank()) throw new BusinessException(Result.BAD_REQUEST_CODE, "试卷名称不能为空"); if (r.getSubject() == null || r.getSubject().isBlank()) throw new BusinessException(Result.BAD_REQUEST_CODE, "科目不能为空"); if (r.getCategory() == null || r.getCategory().isBlank()) throw new BusinessException(Result.BAD_REQUEST_CODE, "分类不能为空"); }
    private void apply(Paper p, PaperDTO.PaperRequest r) { p.setName(r.getName().trim()); p.setSubjectId(r.getSubjectId()); p.setSubject(r.getSubject().trim()); p.setCategory(r.getCategory().trim()); p.setRemark(r.getRemark()); p.setDuration(r.getDuration()); if (r.getTotalScore() != null) p.setTotalScore(r.getTotalScore()); }
    private PaperDTO.BankVO bankVO(QuestionBank b) { PaperDTO.BankVO v = new PaperDTO.BankVO(); v.setId(b.getId()); v.setName(b.getName()); v.setSubjectId(b.getSubjectId()); v.setVisibility(b.getVisibility()); v.setOwnerId(b.getOwnerId()); v.setDescription(b.getDescription()); v.setBankType(b.getBankType()); v.setQuestionCount(!"public".equals(b.getVisibility()) ? (long)bankItemRepo.findByBankIdOrderByCreateTimeDesc(b.getId()).size() : (long)questionRepo.findByBankIdOrderByIdDesc(b.getId()).size()); v.setUpdateTime(b.getUpdateTime() == null ? null : b.getUpdateTime().format(FMT)); return v; }
    private PaperDTO.QuestionVO questionVO(Question q, boolean fav, boolean selected) { PaperDTO.QuestionVO v = new PaperDTO.QuestionVO(); v.setId(q.getId()); v.setBankId(q.getBankId()); v.setSubjectId(q.getSubjectId()); v.setCreatorId(q.getCreatorId()); v.setSubject(q.getSubject()); v.setChapter(q.getChapter()); v.setKnowledgePoint(q.getKnowledgePoint()); v.setQuestionType(q.getQuestionType()); v.setDifficulty(q.getDifficulty()); v.setContent(q.getContent()); v.setOptions(q.getOptions()); v.setAnswer(q.getAnswer()); v.setAnalysis(q.getAnalysis()); v.setFavorited(fav); v.setSelected(selected); bankRepo.findById(q.getBankId()).ifPresent(b -> v.setBankName(b.getName())); return v; }
    private PaperDTO.PaperVO paperVO(Paper p, Long userId) { PaperDTO.PaperVO v = new PaperDTO.PaperVO(); v.setId(p.getId()); v.setName(p.getName()); v.setSubjectId(p.getSubjectId()); v.setSubject(p.getSubject()); v.setCategory(p.getCategory()); v.setRemark(p.getRemark()); v.setDuration(p.getDuration()); v.setStatus(p.getStatus()); v.setCreatorId(p.getCreatorId()); List<PaperQuestion> rows = paperQuestionRepo.findByPaperIdOrderByQuestionOrderAsc(p.getId()); v.setQuestions(rows.stream().map(q -> { PaperDTO.PaperQuestionVO x = new PaperDTO.PaperQuestionVO(); x.setId(q.getId()); x.setPaperId(q.getPaperId()); x.setQuestionId(q.getQuestionId()); x.setQuestionOrder(q.getQuestionOrder()); x.setScore(q.getScore()); x.setSourceType(q.getSourceType()); questionRepo.findById(q.getQuestionId()).ifPresent(z -> x.setQuestion(questionVO(z, favoriteRepo.findByUserIdAndQuestionId(userId, z.getId()).isPresent(), true))); return x; }).toList()); v.setQuestionCount(rows.size()); v.setTotalScore(rows.stream().mapToInt(q -> q.getScore() == null ? 0 : q.getScore()).sum()); v.setCreateTime(p.getCreateTime() == null ? null : p.getCreateTime().format(FMT)); v.setUpdateTime(p.getUpdateTime() == null ? null : p.getUpdateTime().format(FMT)); return v; }
}
