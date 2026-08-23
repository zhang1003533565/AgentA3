package com.example.appbackend.controller;

import com.example.appbackend.entity.Result;
import com.example.appbackend.service.CodeJudgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/code")
@Tag(name = "代码执行", description = "Python 在线编程判题接口")
public class CodeExecuteController {

    @Autowired
    private CodeJudgeService codeJudgeService;

    @PostMapping("/execute")
    @Operation(summary = "执行 Python 代码并判题")
    public Result<Map<String, Object>> execute(@RequestBody ExecuteRequest req) {
        if (req.code == null || req.code.trim().isEmpty()) {
            return Result.badRequest("代码不能为空");
        }
        if (req.testcases == null || req.testcases.isEmpty()) {
            return Result.badRequest("测试用例不能为空");
        }

        List<CodeJudgeService.JudgeCase> cases = new ArrayList<>();
        for (ExecuteRequest.Testcase tc : req.testcases) {
            CodeJudgeService.JudgeCase c = new CodeJudgeService.JudgeCase();
            c.input = tc.input;
            c.expected = tc.expected;
            c.accepts = tc.accepts;
            c.mode = tc.mode;
            cases.add(c);
        }

        return Result.success(codeJudgeService.judge(req.code, req.funcName, cases));
    }

    public static class ExecuteRequest {
        public String code;
        public String funcName;
        public List<Testcase> testcases;

        public static class Testcase {
            public String input;
            public String expected;
            /** 额外可接受的答案（格式同 expected），用于多解题目，任一匹配即通过 */
            public List<String> accepts;
            /** 比较模式：exact 精确匹配（默认）/ set 外层数组无序 / deepset 递归无序 */
            public String mode;
        }
    }
}
