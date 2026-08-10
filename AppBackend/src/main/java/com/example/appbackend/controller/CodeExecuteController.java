package com.example.appbackend.controller;

import com.example.appbackend.entity.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/code")
@Tag(name = "代码执行", description = "Python 在线编程判题接口")
public class CodeExecuteController {

    private static final long TIMEOUT_SECONDS = 10;

    @PostMapping("/execute")
    @Operation(summary = "执行 Python 代码并判题")
    public Result<Map<String, Object>> execute(@RequestBody ExecuteRequest req) {
        if (req.code == null || req.code.trim().isEmpty()) {
            return Result.badRequest("代码不能为空");
        }
        if (req.testcases == null || req.testcases.isEmpty()) {
            return Result.badRequest("测试用例不能为空");
        }

        List<Map<String, Object>> results = new ArrayList<>();
        long totalMs = 0;

        for (ExecuteRequest.Testcase tc : req.testcases) {
            Map<String, Object> caseResult = runOneTestcase(req.code, req.funcName, tc);
            results.add(caseResult);
            Object ms = caseResult.get("ms");
            if (ms instanceof Number) {
                totalMs += ((Number) ms).longValue();
            }
        }

        long passedCount = results.stream()
                .filter(r -> "pass".equals(r.get("status")))
                .count();
        boolean allPass = passedCount == results.size();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", allPass ? "ac" : "wa");
        data.put("statusLabel", allPass ? "通过" : "解答错误");
        data.put("runtime", totalMs);
        data.put("memory", 14.0);
        data.put("passed", passedCount);
        data.put("total", results.size());
        data.put("testcases", results);

        return Result.success(data);
    }

    private Map<String, Object> runOneTestcase(String userCode, String funcName, ExecuteRequest.Testcase tc) {
        Path tmpFile = null;
        try {
            // 解析输入参数，如 "nums = [2,7,11,15], target = 9"
            String[] parts = tc.input.split(",\\s*(?=[a-zA-Z_]\\w*\\s*=)");
            StringBuilder setup = new StringBuilder();
            List<String> argNames = new ArrayList<>();
            for (String part : parts) {
                String trimmed = part.trim();
                int eqIdx = trimmed.indexOf('=');
                if (eqIdx > 0) {
                    String name = trimmed.substring(0, eqIdx).trim();
                    String value = trimmed.substring(eqIdx + 1).trim();
                    setup.append(name).append(" = ").append(value).append("\n");
                    argNames.add(name);
                }
            }

            String args = String.join(", ", argNames);
            String fullCode = "import json, sys, io\n"
                    + "sys.stdout = io.StringIO()\n"
                    + setup
                    + userCode + "\n"
                    + "__result__ = " + funcName + "(" + args + ")\n"
                    + "print(json.dumps(__result__, separators=(',', ':')))\n";

            tmpFile = Files.createTempFile("pycode_", ".py");
            Files.writeString(tmpFile, fullCode, StandardCharsets.UTF_8);

            long start = System.currentTimeMillis();
            ProcessBuilder pb = new ProcessBuilder("python", tmpFile.toAbsolutePath().toString());
            pb.redirectErrorStream(false);
            Process process = pb.start();

            String stdout;
            String stderr;
            try (BufferedReader outReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                 BufferedReader errReader = new BufferedReader(
                         new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {

                boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    long elapsed = System.currentTimeMillis() - start;
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("status", "fail");
                    r.put("input", tc.input);
                    r.put("expected", tc.expected);
                    r.put("actual", "超时（超过 " + TIMEOUT_SECONDS + " 秒）");
                    r.put("ms", elapsed);
                    return r;
                }

                stdout = readAll(outReader);
                stderr = readAll(errReader);
            }

            long elapsed = System.currentTimeMillis() - start;

            if (!stderr.isEmpty()) {
                // 取最后一行作为错误信息
                String errMsg = stderr;
                String[] errLines = stderr.split("\n");
                if (errLines.length > 0) {
                    errMsg = errLines[errLines.length - 1];
                }
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("status", "fail");
                r.put("input", tc.input);
                r.put("expected", tc.expected);
                r.put("actual", errMsg);
                r.put("ms", elapsed);
                return r;
            }

            String actual = stdout.trim();
            if (actual.isEmpty()) actual = "None";

            // 规范化比较：去掉空格
            String normActual = actual.replaceAll("\\s+", "");
            String normExpected = tc.expected.replaceAll("\\s+", "");
            boolean pass = normActual.equals(normExpected);

            Map<String, Object> r = new LinkedHashMap<>();
            r.put("status", pass ? "pass" : "fail");
            r.put("input", tc.input);
            r.put("expected", tc.expected);
            r.put("actual", actual);
            r.put("ms", elapsed);
            return r;

        } catch (Exception e) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("status", "fail");
            r.put("input", tc.input);
            r.put("expected", tc.expected);
            r.put("actual", "运行错误: " + e.getMessage());
            r.put("ms", 0);
            return r;
        } finally {
            if (tmpFile != null) {
                try { Files.deleteIfExists(tmpFile); } catch (Exception ignored) {}
            }
        }
    }

    private String readAll(BufferedReader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(line);
        }
        return sb.toString();
    }

    public static class ExecuteRequest {
        public String code;
        public String funcName;
        public List<Testcase> testcases;

        public static class Testcase {
            public String input;
            public String expected;
        }
    }
}
