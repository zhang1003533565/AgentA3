package com.example.appbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Python 判题核心服务。
 * 同时供在线判题接口（/api/code/execute）与 AI 生成题目的用例自校验复用，
 * 保证两处判定逻辑完全一致。
 */
@Service
public class CodeJudgeService {

    private static final long TIMEOUT_SECONDS = 10;

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 单个判题用例 */
    public static class JudgeCase {
        public String input;
        public String expected;
        /** 额外可接受的答案（格式同 expected），用于多解题目，任一匹配即通过 */
        public List<String> accepts;
        /** 比较模式：exact 精确匹配（默认）/ set 外层数组无序 / deepset 递归无序 */
        public String mode;
    }

    /**
     * 用 code 逐条执行 testcases 并判题，返回与 /api/code/execute 一致的结果结构。
     */
    public Map<String, Object> judge(String code, String funcName, List<JudgeCase> testcases) {
        List<Map<String, Object>> results = new ArrayList<>();
        double totalMs = 0;
        double maxPeakMb = 0;

        for (JudgeCase tc : testcases) {
            Map<String, Object> caseResult = runOneTestcase(code, funcName, tc);
            if (caseResult.get("ms") instanceof Number) {
                totalMs += ((Number) caseResult.get("ms")).doubleValue();
            }
            if (caseResult.get("peakMb") instanceof Number) {
                maxPeakMb = Math.max(maxPeakMb, ((Number) caseResult.get("peakMb")).doubleValue());
            }
            caseResult.remove("ms");
            caseResult.remove("peakMb");
            results.add(caseResult);
        }

        long passedCount = results.stream()
                .filter(r -> "pass".equals(r.get("status")))
                .count();
        boolean allPass = passedCount == results.size();

        String status;
        String statusLabel;
        if (allPass) {
            status = "ac";
            statusLabel = "通过";
        } else if (results.stream().anyMatch(r -> "ce".equals(r.get("status")))) {
            status = "ce";
            statusLabel = "编译错误";
        } else if (results.stream().anyMatch(r -> "re".equals(r.get("status")))) {
            status = "re";
            statusLabel = "运行错误";
        } else if (results.stream().anyMatch(r -> "tle".equals(r.get("status")))) {
            status = "tle";
            statusLabel = "执行超时";
        } else {
            status = "wa";
            statusLabel = "解答错误";
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", status);
        data.put("statusLabel", statusLabel);
        data.put("runtime", Math.round(totalMs));
        data.put("memory", Math.round(maxPeakMb * 10) / 10.0);
        data.put("passed", passedCount);
        data.put("total", results.size());
        data.put("testcases", results);
        return data;
    }

    private Map<String, Object> runOneTestcase(String userCode, String funcName, JudgeCase tc) {
        Path tmpFile = null;
        try {
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
            String mode = (tc.mode == null || tc.mode.isEmpty()) ? "exact" : tc.mode;
            String expectedLiteral = JSON.writeValueAsString(tc.expected == null ? "" : tc.expected);
            String acceptsLiteral = JSON.writeValueAsString(
                    tc.accepts == null ? Collections.emptyList() : tc.accepts);
            String modeLiteral = JSON.writeValueAsString(mode);

            String fullCode = "import json, sys, io, time, tracemalloc\n"
                    + "__real_stdout__ = sys.stdout\n"
                    + "sys.stdout = io.StringIO()\n"
                    + setup
                    + userCode + "\n"
                    + "tracemalloc.start()\n"
                    + "__t0__ = time.perf_counter()\n"
                    + "__result__ = " + funcName + "(" + args + ")\n"
                    + "__ms__ = (time.perf_counter() - __t0__) * 1000.0\n"
                    + "__peak__ = tracemalloc.get_traced_memory()[1]\n"
                    + "tracemalloc.stop()\n"
                    + "__actual__ = json.dumps(__result__, separators=(',', ':'), ensure_ascii=False)\n"
                    + "__expected__ = " + expectedLiteral + "\n"
                    + "__accepts__ = list(" + acceptsLiteral + ")\n"
                    + "__mode__ = " + modeLiteral + "\n"
                    + judgeHelpers()
                    + "__ok__ = __judge__(__actual__, [__expected__] + __accepts__, __mode__)\n"
                    + "sys.stdout = __real_stdout__\n"
                    + "print(json.dumps({'status': 'pass' if __ok__ else 'fail', 'actual': __actual__, 'ms': __ms__, 'peak': __peak__}, ensure_ascii=False))\n";

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
                    r.put("status", "tle");
                    r.put("input", tc.input);
                    r.put("expected", tc.expected);
                    r.put("actual", "执行超时（单用例限时 " + TIMEOUT_SECONDS + " 秒）");
                    r.put("ms", (double) elapsed);
                    return r;
                }

                stdout = readAll(outReader);
                stderr = readAll(errReader);
            }

            long elapsed = System.currentTimeMillis() - start;
            String out = stdout == null ? "" : stdout.trim();
            String err = stderr == null ? "" : stderr.trim();

            if (out.isEmpty() && !err.isEmpty()) {
                String errMsg = lastLine(err);
                boolean compileError = errMsg.contains("SyntaxError") || errMsg.contains("IndentationError");
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("status", compileError ? "ce" : "re");
                r.put("input", tc.input);
                r.put("expected", tc.expected);
                r.put("actual", errMsg);
                r.put("ms", (double) elapsed);
                return r;
            }

            Map<String, Object> judged = parseJudgeOutput(lastLine(out));
            if (judged == null) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("status", "re");
                r.put("input", tc.input);
                r.put("expected", tc.expected);
                r.put("actual", "判题结果解析失败：" + lastLine(out));
                r.put("ms", (double) elapsed);
                return r;
            }

            Map<String, Object> r = new LinkedHashMap<>();
            r.put("status", "pass".equals(judged.get("status")) ? "pass" : "fail");
            r.put("input", tc.input);
            r.put("expected", tc.expected);
            r.put("actual", judged.get("actual") == null ? "" : String.valueOf(judged.get("actual")));
            r.put("ms", judged.get("ms") instanceof Number
                    ? ((Number) judged.get("ms")).doubleValue() : (double) elapsed);
            if (judged.get("peak") instanceof Number) {
                r.put("peakMb", ((Number) judged.get("peak")).doubleValue() / 1048576.0);
            }
            return r;

        } catch (Exception e) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("status", "re");
            r.put("input", tc.input);
            r.put("expected", tc.expected);
            r.put("actual", "运行错误: " + e.getMessage());
            r.put("ms", 0.0);
            return r;
        } finally {
            if (tmpFile != null) {
                try { Files.deleteIfExists(tmpFile); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Python 侧比较函数源码。判定规则（按顺序）：
     * 1. 去除首尾空白后精确匹配 expected 或任一 accepts；
     * 2. 去掉全部空白后匹配（兼容输出格式空格差异）；
     * 3. 双方均为 JSON 数字时按数值比较（兼容 1024 与 1024.0）；
     * 4. mode=set：外层数组排序后比较；mode=deepset：递归排序后比较。
     */
    private static String judgeHelpers() {
        return "def __key__(v):\n"
                + "    return json.dumps(v, separators=(',', ':'), ensure_ascii=False, sort_keys=True)\n"
                + "def __norm_deep__(v):\n"
                + "    if isinstance(v, list):\n"
                + "        return sorted([__norm_deep__(x) for x in v], key=__key__)\n"
                + "    return v\n"
                + "def __norm_shallow__(v):\n"
                + "    if isinstance(v, list):\n"
                + "        return sorted(v, key=__key__)\n"
                + "    return v\n"
                + "def __judge__(a, cands, mode):\n"
                + "    a = (a or '').strip()\n"
                + "    for c in cands:\n"
                + "        e = (c or '').strip()\n"
                + "        if a == e:\n"
                + "            return True\n"
                + "        if ''.join(a.split()) == ''.join(e.split()):\n"
                + "            return True\n"
                + "        try:\n"
                + "            av = json.loads(a)\n"
                + "            ev = json.loads(e)\n"
                + "        except Exception:\n"
                + "            continue\n"
                + "        if isinstance(av, bool) or isinstance(ev, bool):\n"
                + "            continue\n"
                + "        if isinstance(av, (int, float)) and isinstance(ev, (int, float)):\n"
                + "            if av == ev:\n"
                + "                return True\n"
                + "            continue\n"
                + "        if mode == 'deepset':\n"
                + "            if __norm_deep__(av) == __norm_deep__(ev):\n"
                + "                return True\n"
                + "        elif mode == 'set':\n"
                + "            if __norm_shallow__(av) == __norm_shallow__(ev):\n"
                + "                return True\n"
                + "    return False\n";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJudgeOutput(String line) {
        if (line == null || line.isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> map = JSON.readValue(line, Map.class);
            return map;
        } catch (Exception e) {
            return null;
        }
    }

    private String lastLine(String text) {
        if (text == null) {
            return "";
        }
        String[] lines = text.split("\\r?\\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            if (!lines[i].trim().isEmpty()) {
                return lines[i].trim();
            }
        }
        return text.trim();
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
}
