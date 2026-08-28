package com.example.appbackend.service.impl;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第七步既有 Bug 修复：会后智能体 sessionId 必须不超过 Python 侧 64 字符上限，
 * 同时保持「不同会议 / 不同智能体」之间的唯一性，且不改变未超限时的既有格式。
 */
class MeetingAgentSessionIdTest {

    private static final int MAX = MeetingServiceImpl.CHAT_SESSION_ID_MAX;

    /** 真实的会议会话 ID 形态：meeting-<uuid>，共 44 字符。 */
    private static final String BASE = "meeting-41244d5a-b72a-42fb-891d-f20c3fc67fc5";

    /** AppBackend 实际存在的会后智能体名（第六步报告中的 5 个）。 */
    private static final List<String> POST_MEETING_AGENTS = List.of(
            "meeting_transcription_agent",
            "meeting_summary_agent",
            "meeting_controller_agent",
            "meeting_member_analysis_agent",
            "meeting_resource_recommendation_agent"
    );

    @Test
    void keepsOriginalFormatWhenWithinLimit() {
        // 未超限时必须与旧的 base + "-post-" + agentName 拼接完全一致，避免改变既有会话键
        String shortBase = "meeting-abc";
        String shortAgent = "sum_agent";
        assertEquals(shortBase + "-post-" + shortAgent,
                MeetingServiceImpl.buildAgentChatSessionId(shortBase, "post", shortAgent));
        // Agent 2 的 ai-minutes 场景（44 + 11 = 55，本来就合法）输出保持不变
        assertEquals(BASE + "-ai-minutes",
                MeetingServiceImpl.buildAgentChatSessionId(BASE, "ai-minutes", ""));
    }

    @Test
    void allRealPostMeetingAgentsFitWithinLimit() {
        for (String agent : POST_MEETING_AGENTS) {
            String sessionId = MeetingServiceImpl.buildAgentChatSessionId(BASE, "post", agent);
            assertTrue(sessionId.length() <= MAX,
                    agent + " 生成的 sessionId 超长: len=" + sessionId.length() + " value=" + sessionId);
            assertTrue(sessionId.startsWith(BASE),
                    "会议标识被截断，可能破坏跨会议唯一性: " + sessionId);
        }
    }

    @Test
    void veryLongAgentNameStillFitsAndKeepsMeetingIdentity() {
        String agent = "meeting_" + "a".repeat(120) + "_recommendation_agent";
        String sessionId = MeetingServiceImpl.buildAgentChatSessionId(BASE, "post", agent);
        assertTrue(sessionId.length() <= MAX, "超长智能体名仍超限: len=" + sessionId.length());
        assertTrue(sessionId.startsWith(BASE + "-post-"), sessionId);
    }

    @Test
    void baseNearLimitStillFits() {
        String base = "m".repeat(MAX - 9); // 55 字符
        String sessionId = MeetingServiceImpl.buildAgentChatSessionId(base, "post", "meeting_transcription_agent");
        assertTrue(sessionId.length() <= MAX, "临界 base 仍超限: len=" + sessionId.length());
        assertTrue(sessionId.contains("-post-"), sessionId);
    }

    @Test
    void overlongBaseIsTruncatedFromTailAndStillFits() {
        String base = "meeting-" + "x".repeat(100);
        String sessionId = MeetingServiceImpl.buildAgentChatSessionId(base, "post", "meeting_transcription_agent");
        assertTrue(sessionId.length() <= MAX, "超长 base 未被裁剪: len=" + sessionId.length());
        // 保留的是 base 尾部（UUID 尾部含熵）
        assertTrue(sessionId.contains("xxxxx"), "未保留 base 尾部特征: " + sessionId);
    }

    @Test
    void distinctAgentsNeverCollide() {
        Set<String> generated = new HashSet<>();
        for (String agent : POST_MEETING_AGENTS) {
            generated.add(MeetingServiceImpl.buildAgentChatSessionId(BASE, "post", agent));
        }
        assertEquals(POST_MEETING_AGENTS.size(), generated.size(),
                "不同智能体的 sessionId 因截断发生碰撞: " + generated);
    }

    @Test
    void agentsSharingLongPrefixStillDiffer() {
        // 前缀高度相似、只在尾部区分的两个智能体名，压缩后仍必须不同
        String first = MeetingServiceImpl.buildAgentChatSessionId(
                BASE, "post", "meeting_resource_recommendation_agent_v1");
        String second = MeetingServiceImpl.buildAgentChatSessionId(
                BASE, "post", "meeting_resource_recommendation_agent_v2");
        assertTrue(first.length() <= MAX && second.length() <= MAX);
        assertTrue(!first.equals(second), "同前缀不同尾部被截断成同一值: " + first);
    }

    @Test
    void distinctMeetingsNeverCollideForSameAgent() {
        Set<String> generated = new HashSet<>();
        List<String> bases = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            bases.add("meeting-00000000-0000-4000-8000-" + String.format("%012d", i));
        }
        for (String base : bases) {
            generated.add(MeetingServiceImpl.buildAgentChatSessionId(base, "post", "meeting_transcription_agent"));
        }
        assertEquals(bases.size(), generated.size(), "不同会议被压缩成同一 sessionId");
    }

    @Test
    void generationIsDeterministic() {
        String first = MeetingServiceImpl.buildAgentChatSessionId(BASE, "post", "meeting_transcription_agent");
        String second = MeetingServiceImpl.buildAgentChatSessionId(BASE, "post", "meeting_transcription_agent");
        assertEquals(first, second, "同一输入必须产出稳定值，否则会议记忆分片会漂移");
    }

    @Test
    void handlesNullInputsSafely() {
        assertTrue(MeetingServiceImpl.buildAgentChatSessionId(null, null, null).length() <= MAX);
        assertEquals(BASE, MeetingServiceImpl.buildAgentChatSessionId(BASE, "", ""));
    }
}
