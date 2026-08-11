package com.example.appbackend.service.impl;

import com.example.appbackend.dto.FlowchartDTO;
import com.example.appbackend.entity.FlowchartRecord;
import com.example.appbackend.repository.FlowchartRecordRepository;
import com.example.appbackend.service.FileParseService;
import com.example.appbackend.service.FileSummaryService;
import com.example.appbackend.service.FlowchartAIService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlowchartServiceImplTest {

    @Test
    void generateCombinesDescriptionAndFileTextAndReturnsResolvedModes() {
        FlowchartAIService aiService = mock(FlowchartAIService.class);
        FlowchartRecordRepository recordRepository = mock(FlowchartRecordRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        FlowchartServiceImpl service = new FlowchartServiceImpl(
                aiService,
                mock(FileParseService.class),
                mock(FileSummaryService.class),
                recordRepository,
                objectMapper
        );

        when(recordRepository.save(any(FlowchartRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(aiService.generate(any(FlowchartDTO.GenerateRequest.class), anyString(), isNull()))
                .thenAnswer(invocation -> {
                    FlowchartDTO.GenerateRequest request = invocation.getArgument(0);
                    String inputText = invocation.getArgument(1);
                    Assertions.assertEquals("ADMIN", request.getSceneType());
                    Assertions.assertEquals("DETAILED", request.getNodeGranularity());
                    Assertions.assertEquals("AUTO", request.getDecisionMode());
                    Assertions.assertEquals("ROLE", request.getSwimlaneMode());
                    Assertions.assertTrue(inputText.contains("员工提交请假申请"));
                    Assertions.assertTrue(inputText.contains("主管拒绝后返回修改"));
                    return leaveFlowchart();
                });

        FlowchartDTO.GenerateRequest request = new FlowchartDTO.GenerateRequest();
        request.setContent("员工提交请假申请");
        request.setSourceText("主管拒绝后返回修改，通过后人事备案");
        request.setSceneType("ADMIN");
        request.setNodeGranularity("DETAILED");
        request.setDecisionMode("AUTO");
        request.setSwimlaneMode("ROLE");

        FlowchartDTO.GenerateResponse response = service.generate(7L, request, null);

        Assertions.assertEquals("请假审批流程图", response.getTitle());
        Assertions.assertEquals("ADMIN", response.getSceneType());
        Assertions.assertEquals("DETAILED", response.getNodeGranularity());
        Assertions.assertEquals("ENABLED", response.getResolvedDecisionMode());
        Assertions.assertEquals("ROLE", response.getResolvedSwimlaneMode());
        Assertions.assertFalse(response.getLanes().isEmpty());
    }

    @Test
    void generateAllowsFileOnlySourceText() {
        FlowchartAIService aiService = mock(FlowchartAIService.class);
        FlowchartRecordRepository recordRepository = mock(FlowchartRecordRepository.class);
        FlowchartServiceImpl service = new FlowchartServiceImpl(
                aiService,
                mock(FileParseService.class),
                mock(FileSummaryService.class),
                recordRepository,
                new ObjectMapper()
        );

        when(recordRepository.save(any(FlowchartRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(aiService.generate(any(FlowchartDTO.GenerateRequest.class), anyString(), isNull()))
                .thenReturn(simpleFlowchart());

        FlowchartDTO.GenerateRequest request = new FlowchartDTO.GenerateRequest();
        request.setDescription("");
        request.setSourceText("打开 App，登录，查看课程");
        request.setSwimlaneMode("NONE");

        FlowchartDTO.GenerateResponse response = service.generate(9L, request, null);

        Assertions.assertEquals("DISABLED", response.getResolvedDecisionMode());
        Assertions.assertEquals("NONE", response.getResolvedSwimlaneMode());
        Assertions.assertTrue(response.getLanes().isEmpty());
    }

    @Test
    void detailCompletesResolvedMetadataForHistoryReopen() throws Exception {
        FlowchartRecordRepository recordRepository = mock(FlowchartRecordRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        FlowchartServiceImpl service = new FlowchartServiceImpl(
                mock(FlowchartAIService.class),
                mock(FileParseService.class),
                mock(FileSummaryService.class),
                recordRepository,
                objectMapper
        );

        FlowchartDTO.FlowchartData data = leaveFlowchart();
        data.getNodes().get(0).setLaneId(null);
        data.getNodes().get(0).setLane("员工");
        data.setResolvedSwimlaneMode(null);
        FlowchartRecord record = new FlowchartRecord();
        record.setId("fc1");
        record.setUserId(7L);
        record.setTitle("请假审批流程图");
        record.setProcessType("ADMIN");
        record.setFlowJson(objectMapper.writeValueAsString(data));

        when(recordRepository.findByIdAndUserId("fc1", 7L)).thenReturn(Optional.of(record));

        FlowchartDTO.GenerateResponse response = service.detail(7L, "fc1");

        Assertions.assertEquals("ROLE", response.getResolvedSwimlaneMode());
        Assertions.assertEquals(2, response.getLanes().size());
        Assertions.assertEquals("employee", response.getNodes().get(0).getLaneId());
    }

    private FlowchartDTO.FlowchartData leaveFlowchart() {
        FlowchartDTO.FlowchartData data = new FlowchartDTO.FlowchartData();
        data.setTitle("请假审批流程图");
        FlowchartDTO.Lane employee = lane("employee", "员工", "role");
        FlowchartDTO.Lane manager = lane("manager", "主管", "role");
        data.getLanes().add(employee);
        data.getLanes().add(manager);
        data.getNodes().add(node("start", "开始", "start", "employee"));
        data.getNodes().add(node("submit", "提交申请", "process", "employee"));
        data.getNodes().add(node("approve", "审核通过？", "decision", "manager"));
        data.getEdges().add(edge("start", "submit", ""));
        data.getEdges().add(edge("submit", "approve", ""));
        data.getEdges().add(edge("approve", "submit", "拒绝"));
        return data;
    }

    private FlowchartDTO.FlowchartData simpleFlowchart() {
        FlowchartDTO.FlowchartData data = new FlowchartDTO.FlowchartData();
        data.setTitle("查看课程流程图");
        data.getNodes().add(node("start", "开始", "start", ""));
        data.getNodes().add(node("login", "登录", "process", ""));
        data.getNodes().add(node("end", "结束", "end", ""));
        data.getEdges().add(edge("start", "login", ""));
        data.getEdges().add(edge("login", "end", ""));
        return data;
    }

    private FlowchartDTO.Lane lane(String id, String label, String type) {
        FlowchartDTO.Lane lane = new FlowchartDTO.Lane();
        lane.setId(id);
        lane.setLabel(label);
        lane.setName(label);
        lane.setType(type);
        return lane;
    }

    private FlowchartDTO.Node node(String id, String label, String type, String laneId) {
        FlowchartDTO.Node node = new FlowchartDTO.Node();
        node.setId(id);
        node.setLabel(label);
        node.setName(label);
        node.setType(type);
        node.setLaneId(laneId);
        return node;
    }

    private FlowchartDTO.Edge edge(String source, String target, String label) {
        FlowchartDTO.Edge edge = new FlowchartDTO.Edge();
        edge.setSource(source);
        edge.setTarget(target);
        edge.setLabel(label);
        return edge;
    }
}
