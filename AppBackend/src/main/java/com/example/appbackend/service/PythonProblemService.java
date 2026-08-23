package com.example.appbackend.service;

import com.example.appbackend.dto.PythonProblemDTO;

import java.util.List;

public interface PythonProblemService {

    // ========== 小程序端 ==========
    /** 上架题目摘要列表（题库页） */
    List<PythonProblemDTO.SummaryVO> listPublic();

    /** 题目详情（含测试用例，编程页判题用） */
    PythonProblemDTO.DetailVO getDetail(Long id);

    /** 题目标准答案（JSON 字符串，仅供 AI 辅助编程注入，不返回公开接口） */
    String getSolutionJson(Long id);

    /** AI 生成题目（草案，未入库）：对话式需求解析 + 自动匹配参考题 + 题号分配 */
    PythonProblemDTO.AIGenerateResponse aiGenerate(PythonProblemDTO.AIGenerateRequest req, String authorization);

    // ========== 管理端 ==========
    List<PythonProblemDTO.AdminVO> listAdmin(String keyword, String difficulty, Boolean enabled);

    PythonProblemDTO.AdminVO create(PythonProblemDTO.ProblemRequest req);

    PythonProblemDTO.AdminVO update(Long id, PythonProblemDTO.ProblemRequest req);

    void delete(Long id);
}
