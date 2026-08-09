package com.example.appbackend.service.impl;

import com.example.appbackend.dto.FlowchartDTO;
import com.example.appbackend.repository.SystemConfigRepository;
import com.example.appbackend.service.SystemConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.mockito.Mockito.mock;

class FlowchartAIServiceImplTest {

    @Test
    void buildPromptUsesWeightedConstraintLanguage() throws Exception {
        FlowchartDTO.GenerateRequest request = new FlowchartDTO.GenerateRequest();
        request.setSceneType("ADMIN");
        request.setNodeGranularity("SIMPLE");
        request.setDecisionMode("FORCE");
        request.setSwimlaneMode("ROLE");
        request.setDisplayItems(List.of("STEP", "ROLE"));

        String prompt = invokeBuildPrompt(request, "主管拒绝后返回员工修改");

        Assertions.assertTrue(prompt.contains("【最高优先级业务事实｜HARD / Highest】"));
        Assertions.assertTrue(prompt.contains("【强约束｜HARD】最终流程图必须采用角色泳道"));
        Assertions.assertTrue(prompt.contains("【强规则｜HIGH】强制优先使用判断节点"));
        Assertions.assertTrue(prompt.contains("【较强偏好｜MEDIUM-HIGH】节点粒度要求偏简略"));
        Assertions.assertTrue(prompt.contains("【语境偏好｜MEDIUM】请优先按照行政流程语境理解该需求"));
    }

    @Test
    void decisionNoneCoercesDecisionNodesToProcessWithoutDroppingBranchText() throws Exception {
        FlowchartDTO.GenerateRequest request = new FlowchartDTO.GenerateRequest();
        request.setDecisionMode("NONE");
        request.setSwimlaneMode("NONE");

        String json = """
                {
                  "title": "请假流程",
                  "nodes": [
                    {"id": "start", "type": "start", "label": "开始"},
                    {"id": "approve", "type": "decision", "label": "审核通过？"}
                  ],
                  "edges": [
                    {"source": "start", "target": "approve", "label": "拒绝返回修改"}
                  ],
                  "lanes": [{"id": "employee", "label": "员工", "type": "role"}]
                }
                """;

        FlowchartDTO.FlowchartData data = invokeParseAndValidate(json, request);

        Assertions.assertEquals("process", data.getNodes().get(1).getType());
        Assertions.assertEquals("拒绝返回修改", data.getEdges().get(0).getLabel());
        Assertions.assertEquals("DISABLED", data.getResolvedDecisionMode());
        Assertions.assertEquals("NONE", data.getResolvedSwimlaneMode());
        Assertions.assertTrue(data.getLanes().isEmpty());
    }

    private String invokeBuildPrompt(FlowchartDTO.GenerateRequest request, String inputText) throws Exception {
        Method method = FlowchartAIServiceImpl.class.getDeclaredMethod(
                "buildPrompt", FlowchartDTO.GenerateRequest.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service(), request, inputText);
    }

    private FlowchartDTO.FlowchartData invokeParseAndValidate(String json, FlowchartDTO.GenerateRequest request) throws Exception {
        Method method = FlowchartAIServiceImpl.class.getDeclaredMethod(
                "parseAndValidate", String.class, FlowchartDTO.GenerateRequest.class);
        method.setAccessible(true);
        return (FlowchartDTO.FlowchartData) method.invoke(service(), json, request);
    }

    private FlowchartAIServiceImpl service() {
        return new FlowchartAIServiceImpl(
                mock(SystemConfigService.class),
                mock(SystemConfigRepository.class),
                new ObjectMapper()
        );
    }
}
