package com.example.appbackend.controller;

import com.example.appbackend.dto.PaperDTO;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.PaperService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/papers")
@Tag(name = "试卷生成", description = "教师手动从题库选题组卷")
public class PaperController {
    @Autowired private PaperService service;
    private Long user(HttpServletRequest r) { Object id = r.getAttribute("userId"); if (id == null) throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录"); return (Long) id; }

    @GetMapping("/banks") @Operation(summary = "题库列表")
    public Result<List<PaperDTO.BankVO>> banks(@RequestParam(defaultValue = "public") String visibility, @RequestParam(required = false) String keyword, HttpServletRequest r) { return Result.success(service.listBanks(user(r), visibility, keyword)); }
    @PostMapping("/banks") public Result<PaperDTO.BankVO> createBank(@RequestBody PaperDTO.BankRequest req, HttpServletRequest r) { return Result.success("创建成功", service.saveBank(req, user(r), null)); }
    @PutMapping("/banks/{id}") public Result<PaperDTO.BankVO> updateBank(@PathVariable Long id, @RequestBody PaperDTO.BankRequest req, HttpServletRequest r) { return Result.success("更新成功", service.saveBank(req, user(r), id)); }
    @DeleteMapping("/banks/{id}") public Result<Void> deleteBank(@PathVariable Long id, HttpServletRequest r) { service.deleteBank(id, user(r)); return Result.success("删除成功", null); }
    @PostMapping("/banks/{id}/questions/{questionId}") public Result<Void> addToBank(@PathVariable Long id,@PathVariable Long questionId,HttpServletRequest r){service.addToBank(id,questionId,user(r));return Result.success("已加入题库",null);}
    @GetMapping("/banks/{bankId}/questions") public Result<List<PaperDTO.QuestionVO>> questions(@PathVariable Long bankId, @RequestParam(required = false) String keyword, @RequestParam(required = false) String type, @RequestParam(required = false) String difficulty, @RequestParam(required = false) Long paperId, HttpServletRequest r) { return Result.success(service.questions(bankId, user(r), keyword, type, difficulty, paperId, false)); }
    @GetMapping("/favorites") public Result<List<PaperDTO.QuestionVO>> favorites(@RequestParam(required = false) String keyword, @RequestParam(required = false) String type, @RequestParam(required = false) String difficulty, @RequestParam(required = false) Long paperId, HttpServletRequest r) { return Result.success(service.questions(null, user(r), keyword, type, difficulty, paperId, true)); }
    @PostMapping("/questions/{questionId}/favorite") public Result<Void> favorite(@PathVariable Long questionId, HttpServletRequest r) { service.toggleFavorite(questionId, user(r), true); return Result.success("收藏成功", null); }
    @DeleteMapping("/questions/{questionId}/favorite") public Result<Void> unfavorite(@PathVariable Long questionId, HttpServletRequest r) { service.toggleFavorite(questionId, user(r), false); return Result.success("已取消收藏", null); }
    @GetMapping("/questions/{questionId}") public Result<PaperDTO.QuestionVO> questionDetail(@PathVariable Long questionId, @RequestParam(required = false) Long paperId, HttpServletRequest r) { return Result.success(service.questionDetail(questionId, user(r), paperId)); }

    @GetMapping public Result<List<PaperDTO.PaperVO>> list(@RequestParam(required = false) String status, @RequestParam(required = false) String keyword, HttpServletRequest r) { return Result.success(service.listPapers(user(r), status, keyword)); }
    @PostMapping public Result<PaperDTO.PaperVO> create(@RequestBody PaperDTO.PaperRequest req, HttpServletRequest r) { return Result.success("草稿已创建", service.createPaper(req, user(r))); }
    @GetMapping("/{id}") public Result<PaperDTO.PaperVO> detail(@PathVariable Long id, HttpServletRequest r) { return Result.success(service.getPaper(id, user(r))); }
    @GetMapping("/{id}/questions") public Result<PaperDTO.PaperVO> paperQuestions(@PathVariable Long id, HttpServletRequest r) { return Result.success(service.getPaper(id, user(r))); }
    @PutMapping("/{id}") public Result<PaperDTO.PaperVO> update(@PathVariable Long id, @RequestBody PaperDTO.PaperRequest req, HttpServletRequest r) { return Result.success("保存成功", service.updatePaper(id, req, user(r))); }
    @PostMapping("/{id}/questions") public Result<PaperDTO.PaperVO> add(@PathVariable Long id, @RequestBody PaperDTO.QuestionRequest req, HttpServletRequest r) { return Result.success("加入成功", service.addQuestion(id, req, user(r))); }
    @PutMapping("/{id}/questions/{questionId}") public Result<PaperDTO.PaperVO> updateQuestion(@PathVariable Long id, @PathVariable Long questionId, @RequestBody PaperDTO.ScoreRequest req, HttpServletRequest r) { return Result.success("更新成功", service.updateQuestion(id, questionId, req, user(r))); }
    @DeleteMapping("/{id}/questions/{questionId}") public Result<PaperDTO.PaperVO> remove(@PathVariable Long id, @PathVariable Long questionId, HttpServletRequest r) { return Result.success("移除成功", service.removeQuestion(id, questionId, user(r))); }
    @PostMapping("/{id}/complete") public Result<PaperDTO.PaperVO> complete(@PathVariable Long id, HttpServletRequest r) { return Result.success("组卷完成", service.complete(id, user(r))); }
    @PostMapping("/{id}/copy") public Result<PaperDTO.PaperVO> copy(@PathVariable Long id, HttpServletRequest r) { return Result.success("复制成功", service.copy(id, user(r))); }
    @DeleteMapping("/{id}") public Result<Void> delete(@PathVariable Long id, HttpServletRequest r) { service.deletePaper(id, user(r)); return Result.success("删除成功", null); }
    @GetMapping("/banks/{id}") public Result<PaperDTO.BankVO> bankDetail(@PathVariable Long id, HttpServletRequest r) { return Result.success(service.getBank(id, user(r))); }
    @GetMapping("/banks/public/questions") public Result<List<PaperDTO.QuestionVO>> publicQuestions(@RequestParam(required = false) String keyword, @RequestParam(required = false) String type, @RequestParam(required = false) String difficulty, @RequestParam(required = false) Long paperId, HttpServletRequest r) { return Result.success(service.questions(null, user(r), keyword, type, difficulty, paperId, false)); }
    @DeleteMapping("/banks/{id}/questions/{questionId}") public Result<Void> removeFromBank(@PathVariable Long id,@PathVariable Long questionId,HttpServletRequest r){service.removeFromBank(id,questionId,user(r));return Result.success("移除成功",null);}
}
