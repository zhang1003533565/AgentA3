package com.example.appbackend.service.impl;

import com.example.appbackend.dto.LlmChatRequest;
import com.example.appbackend.dto.LlmChatResponse;
import com.example.appbackend.dto.MeetingDTO;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.dto.UserProfileDTO;
import com.example.appbackend.entity.MeetingAgentResult;
import com.example.appbackend.entity.MeetingParticipant;
import com.example.appbackend.entity.MeetingRecord;
import com.example.appbackend.entity.MeetingSession;
import com.example.appbackend.entity.Result;
import com.example.appbackend.entity.SystemConfig;
import com.example.appbackend.entity.SystemConfigTestLog;
import com.example.appbackend.entity.User;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.MeetingAgentResultRepository;
import com.example.appbackend.repository.MeetingParticipantRepository;
import com.example.appbackend.repository.MeetingRecordRepository;
import com.example.appbackend.repository.MeetingSessionRepository;
import com.example.appbackend.repository.SystemConfigRepository;
import com.example.appbackend.repository.SystemConfigTestLogRepository;
import com.example.appbackend.repository.UserRepository;
import com.example.appbackend.service.LlmService;
import com.example.appbackend.service.MeetingService;
import com.example.appbackend.service.UserProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class MeetingServiceImpl implements MeetingService {

    private static final Logger log = LoggerFactory.getLogger(MeetingServiceImpl.class);

    private static final Set<String> MEETING_AGENTS = Set.of(
            "meeting_controller_agent",
            "meeting_transcription_agent",
            "meeting_summary_agent",
            "meeting_member_analysis_agent",
            "meeting_resource_recommendation_agent",
            "meeting_voice_broadcast_agent"
    );
    private static final List<String> POST_MEETING_AGENT_ORDER = List.of(
            "meeting_transcription_agent",
            "meeting_summary_agent",
            "meeting_controller_agent",
            "meeting_member_analysis_agent",
            "meeting_resource_recommendation_agent"
    );

    private static final int LLM_INPUT_LIMIT = 3900;
    private static final List<String> AI_MODEL_CONFIG_FIELDS = List.of("provider", "base-url", "api-key", "model");
    private static final char[] ROOM_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int ROOM_CODE_LENGTH = 6;
    private static final SecureRandom ROOM_CODE_RANDOM = new SecureRandom();

    private final MeetingSessionRepository sessionRepository;
    private final MeetingParticipantRepository participantRepository;
    private final MeetingRecordRepository recordRepository;
    private final MeetingAgentResultRepository resultRepository;
    private final UserRepository userRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final SystemConfigTestLogRepository systemConfigTestLogRepository;
    private final LlmService llmService;
    private final UserProfileService userProfileService;

    public MeetingServiceImpl(MeetingSessionRepository sessionRepository,
                              MeetingParticipantRepository participantRepository,
                              MeetingRecordRepository recordRepository,
                              MeetingAgentResultRepository resultRepository,
                              UserRepository userRepository,
                              SystemConfigRepository systemConfigRepository,
                              SystemConfigTestLogRepository systemConfigTestLogRepository,
                              LlmService llmService,
                              UserProfileService userProfileService) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.recordRepository = recordRepository;
        this.resultRepository = resultRepository;
        this.userRepository = userRepository;
        this.systemConfigRepository = systemConfigRepository;
        this.systemConfigTestLogRepository = systemConfigTestLogRepository;
        this.llmService = llmService;
        this.userProfileService = userProfileService;
    }

    @Override
    @Transactional
    public MeetingDTO.SessionDetail createMeeting(Long userId, MeetingDTO.SessionRequest request) {
        MeetingSession session = new MeetingSession();
        session.setUserId(userId);
        session.setSessionId("meeting-" + UUID.randomUUID());
        session.setRoomCode(generateRoomCode());
        session.setMeetingType(normalizeMeetingType(request == null ? null : request.getMeetingType()));
        session.setTitle(normalizeTitle(request == null ? null : request.getTitle()));
        session.setStatus(normalizeStatus(request == null ? null : request.getStatus()));
        if (request != null && request.getScheduledStartTime() != null) {
            session.setScheduledStartTime(request.getScheduledStartTime());
        }
        if (MeetingSession.STATUS_ACTIVE.equals(session.getStatus())) {
            session.setStartTime(LocalDateTime.now());
        }
        session = sessionRepository.save(session);
        syncParticipants(session.getId(), withCurrentUser(userId, request == null ? List.of() : request.getParticipants()));
        if (request != null && StringUtils.hasText(request.getNotes())) {
            saveRecord(session, request.getNotes(), MeetingRecord.SOURCE_MANUAL);
        }
        refreshCounters(session);
        return buildDetail(session);
    }

    @Override
    @Transactional
    public MeetingDTO.SessionDetail createQuickMeeting(Long userId, MeetingDTO.QuickMeetingRequest request) {
        MeetingSession session = new MeetingSession();
        session.setUserId(userId);
        session.setSessionId("meeting-" + UUID.randomUUID());
        session.setRoomCode(generateRoomCode());
        session.setMeetingType(MeetingSession.TYPE_QUICK);
        session.setTitle(normalizeTitle(request == null ? null : request.getTitle()));
        session.setStatus(MeetingSession.STATUS_ACTIVE);
        session.setStartTime(LocalDateTime.now());
        session = sessionRepository.save(session);
        syncParticipants(session.getId(), withCurrentUser(userId, request == null ? List.of() : request.getParticipants()));
        refreshCounters(session);
        return buildDetail(session);
    }

    @Override
    @Transactional
    public MeetingDTO.SessionDetail reserveMeeting(Long userId, MeetingDTO.ReserveMeetingRequest request) {
        MeetingSession session = new MeetingSession();
        session.setUserId(userId);
        session.setSessionId("meeting-" + UUID.randomUUID());
        session.setRoomCode(generateRoomCode());
        session.setMeetingType(MeetingSession.TYPE_RESERVED);
        session.setTitle(normalizeTitle(request == null ? null : request.getTitle()));
        session.setStatus(MeetingSession.STATUS_IDLE);
        session.setScheduledStartTime(resolveScheduledStartTime(request == null ? null : request.getScheduledStartTime()));
        session = sessionRepository.save(session);
        syncParticipants(session.getId(), withCurrentUser(userId, request == null ? List.of() : request.getParticipants()));
        refreshCounters(session);
        return buildDetail(session);
    }

    @Override
    @Transactional
    public MeetingDTO.SessionDetail updateMeeting(Long userId, String sessionId, MeetingDTO.SessionRequest request) {
        MeetingSession session = findOwnedSession(userId, sessionId);
        if (request != null) {
            if (StringUtils.hasText(request.getTitle())) {
                session.setTitle(truncate(request.getTitle().trim(), 120));
            }
            if (StringUtils.hasText(request.getStatus())) {
                session.setStatus(normalizeStatus(request.getStatus()));
            }
            if (StringUtils.hasText(request.getMeetingType())) {
                session.setMeetingType(normalizeMeetingType(request.getMeetingType()));
            }
            if (request.getScheduledStartTime() != null) {
                session.setScheduledStartTime(request.getScheduledStartTime());
            }
            if (request.getParticipants() != null) {
                syncParticipants(session.getId(), request.getParticipants());
            }
            if (StringUtils.hasText(request.getNotes())) {
                String latestContent = latestRecordContent(session.getId());
                String normalizedNotes = request.getNotes().trim();
                if (!normalizedNotes.equals(latestContent)) {
                    saveRecord(session, normalizedNotes, MeetingRecord.SOURCE_MANUAL);
                }
            }
        }
        refreshCounters(session);
        return buildDetail(session);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MeetingDTO.SessionItem> listMeetings(Long userId, Integer pageNum, Integer pageSize, String keyword) {
        int safePage = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safeSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 50);
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : "";
        Page<MeetingSession> page = sessionRepository.searchByUserId(userId, normalizedKeyword, PageRequest.of(safePage - 1, safeSize));
        List<MeetingDTO.SessionItem> records = page.getContent().stream()
                .map(this::toSessionItem)
                .collect(Collectors.toList());
        return new PageResponse<>(records, page.getTotalElements(), safePage, safeSize);
    }

    @Override
    @Transactional
    public MeetingDTO.SessionDetail joinMeeting(Long userId, MeetingDTO.JoinRoomRequest request) {
        String roomCode = normalizeRoomCode(request == null ? null : request.getRoomCode());
        MeetingSession session = sessionRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "会议号不存在"));
        String displayName = resolveUserDisplayName(userId);
        if (!StringUtils.hasText(displayName) && request != null && StringUtils.hasText(request.getDisplayName())) {
            displayName = request.getDisplayName();
        }
        if (StringUtils.hasText(displayName)) {
            addParticipantIfMissing(session.getId(), displayName);
        }
        refreshCounters(session);
        return buildDetail(session);
    }

    @Override
    @Transactional(readOnly = true)
    public MeetingDTO.SessionDetail getMeeting(Long userId, String sessionId) {
        return buildDetail(findAccessibleSession(userId, sessionId));
    }

    @Override
    @Transactional
    public MeetingDTO.SessionDetail startMeeting(Long userId, String sessionId) {
        MeetingSession session = findAccessibleSession(userId, sessionId);
        if (MeetingSession.STATUS_ENDED.equals(session.getStatus())) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "已结束的会议不能重新开始");
        }
        session.setStatus(MeetingSession.STATUS_ACTIVE);
        if (session.getStartTime() == null) {
            session.setStartTime(LocalDateTime.now());
        }
        refreshCounters(session);
        return buildDetail(session);
    }

    @Override
    @Transactional
    public MeetingDTO.SessionDetail endMeeting(Long userId, String sessionId, String authorization) {
        MeetingSession session = findAccessibleSession(userId, sessionId);
        session.setStatus(MeetingSession.STATUS_ENDED);
        session.setEndTime(LocalDateTime.now());
        refreshCounters(session);
        MeetingDTO.SessionDetail detail = buildDetail(session);
        triggerPostMeetingOrganization(session.getSessionId(), authorization);
        return detail;
    }

    @Override
    @Transactional
    public MeetingDTO.SessionDetail organizeMeeting(Long userId, String sessionId, String authorization) {
        MeetingSession session = findAccessibleSession(userId, sessionId);
        organizeMeetingResults(session, authorization, true);
        refreshCounters(session);
        return buildDetail(session);
    }

    @Override
    @Transactional
    public void deleteMeeting(Long userId, String sessionId) {
        MeetingSession session = findOwnedSession(userId, sessionId);
        resultRepository.deleteByMeetingSessionId(session.getId());
        recordRepository.deleteByMeetingSessionId(session.getId());
        participantRepository.deleteByMeetingSessionId(session.getId());
        sessionRepository.delete(session);
    }

    @Override
    @Transactional
    public MeetingDTO.RecordItem addRecord(Long userId, String sessionId, MeetingDTO.RecordRequest request) {
        MeetingSession session = findAccessibleSession(userId, sessionId);
        MeetingRecord record = saveRecord(session, request.getContent(), request.getSource());
        refreshCounters(session);
        return toRecordItem(record);
    }

    @Override
    @Transactional
    public MeetingDTO.RunAgentResponse runAgent(Long userId, String sessionId, MeetingDTO.RunAgentRequest request, String authorization) {
        MeetingSession session = findAccessibleSession(userId, sessionId);
        String agentName = normalizeAgentName(request.getAgentName());
        String content = resolveMeetingContent(session, request.getContent());
        if (StringUtils.hasText(request.getContent())) {
            String latestContent = latestRecordContent(session.getId());
            String normalizedContent = request.getContent().trim();
            if (!normalizedContent.equals(latestContent)) {
                saveRecord(session, normalizedContent, MeetingRecord.SOURCE_MANUAL);
            }
        }

        LlmChatRequest chatRequest = new LlmChatRequest();
        chatRequest.setSessionId(session.getSessionId());
        chatRequest.setAgentName(agentName);
        chatRequest.setLlmModel(resolveMeetingLlmModel(request.getLlmModel()));
        chatRequest.setInput(truncate(buildAgentInput(session, content), LLM_INPUT_LIMIT));

        MeetingAgentResult result = runAndSaveAgent(session, chatRequest, authorization);

        refreshCounters(session);

        MeetingDTO.RunAgentResponse response = new MeetingDTO.RunAgentResponse();
        response.setSessionId(session.getSessionId());
        response.setAgentName(agentName);
        response.setAnswerType(result.getAnswerType());
        response.setAnswer(result.getAnswer());
        response.setDetail(buildDetail(session));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public MeetingDTO.RunAgentResponse previewAgent(Long userId, String sessionId, MeetingDTO.RunAgentRequest request, String authorization) {
        MeetingSession session = findAccessibleSession(userId, sessionId);
        String agentName = normalizeAgentName(request.getAgentName());
        String content = resolveMeetingContent(session, request.getContent());

        LlmChatRequest chatRequest = new LlmChatRequest();
        chatRequest.setSessionId(session.getSessionId());
        chatRequest.setAgentName(agentName);
        chatRequest.setLlmModel(resolveMeetingLlmModel(request.getLlmModel()));
        chatRequest.setInput(truncate(buildAgentInput(session, content), LLM_INPUT_LIMIT));

        MeetingDTO.RunAgentResponse response = new MeetingDTO.RunAgentResponse();
        response.setSessionId(session.getSessionId());
        response.setAgentName(agentName);
        response.setAnswerType("markdown");
        try {
            LlmChatResponse chatResponse = llmService.chat(chatRequest, authorization);
            response.setAnswerType(StringUtils.hasText(chatResponse.getAnswerType()) ? chatResponse.getAnswerType() : "markdown");
            response.setAnswer(StringUtils.hasText(chatResponse.getAnswer()) ? chatResponse.getAnswer() : "");
        } catch (BusinessException error) {
            if (error.getCode() < Result.ERROR_CODE) {
                throw error;
            }
            log.warn("preview meeting agent failed sessionId={} agentName={}: {}", session.getSessionId(), agentName, error.getMessage());
            response.setAnswer("");
            response.setErrorMessage(error.getMessage());
        }
        return response;
    }

    private void triggerPostMeetingOrganization(String sessionId, String authorization) {
        CompletableFuture.runAsync(() -> {
            try {
                MeetingSession latestSession = findSession(sessionId);
                organizeMeetingResults(latestSession, authorization, false);
                refreshCounters(latestSession);
            } catch (Exception error) {
                log.warn("post meeting organization skipped sessionId={}: {}", sessionId, error.getMessage());
            }
        });
    }

    private void organizeMeetingResults(MeetingSession session, String authorization, boolean failFast) {
        String content = allMeetingContent(session.getId());
        if (!StringUtils.hasText(content)) {
            log.info("skip post meeting organization sessionId={} because records are empty", session.getSessionId());
            return;
        }
        for (String agentName : POST_MEETING_AGENT_ORDER) {
            if (resultRepository.existsByMeetingSessionIdAndAgentName(session.getId(), agentName)) {
                continue;
            }
            try {
                LlmChatRequest chatRequest = new LlmChatRequest();
                chatRequest.setSessionId(session.getSessionId() + "-post-" + agentName);
                chatRequest.setAgentName(agentName);
                chatRequest.setLlmModel(resolveMeetingLlmModel(null));
                chatRequest.setInput(truncate(buildPostMeetingAgentInput(session, content, agentName), LLM_INPUT_LIMIT));
                runAndSaveAgent(session, chatRequest, authorization);
            } catch (BusinessException error) {
                if (failFast || error.getCode() < Result.ERROR_CODE) {
                    throw error;
                }
                log.warn("post meeting agent failed sessionId={} agentName={}: {}", session.getSessionId(), agentName, error.getMessage());
                return;
            } catch (Exception error) {
                if (failFast) {
                    throw new BusinessException(Result.ERROR_CODE, "会议整理失败: " + error.getMessage());
                }
                log.warn("post meeting agent failed sessionId={} agentName={}: {}", session.getSessionId(), agentName, error.getMessage());
                return;
            }
        }
    }

    private MeetingAgentResult runAndSaveAgent(MeetingSession session, LlmChatRequest chatRequest, String authorization) {
        LlmChatResponse chatResponse = llmService.chat(chatRequest, authorization);
        MeetingAgentResult result = new MeetingAgentResult();
        result.setMeetingSessionId(session.getId());
        result.setAgentName(chatRequest.getAgentName());
        result.setAnswerType(StringUtils.hasText(chatResponse.getAnswerType()) ? chatResponse.getAnswerType() : "markdown");
        result.setAnswer(StringUtils.hasText(chatResponse.getAnswer()) ? chatResponse.getAnswer() : "智能体没有返回可用内容。");
        MeetingAgentResult saved = resultRepository.save(result);
        captureMeetingProfileEvidence(session, saved);
        return saved;
    }

    private void captureMeetingProfileEvidence(MeetingSession session, MeetingAgentResult result) {
        if (session == null || result == null || session.getUserId() == null) {
            return;
        }
        UserProfileDTO.EvidenceRequest evidence = switch (result.getAgentName()) {
            case "meeting_member_analysis_agent" -> buildMeetingEvidence(
                    session,
                    result,
                    "weak_points",
                    "weakness",
                    -2,
                    "会议成员分析指出理解偏差或薄弱点：" + truncate(result.getAnswer(), 820),
                    List.of("会议成员分析", "薄弱点")
            );
            case "meeting_summary_agent", "meeting_controller_agent" -> buildMeetingEvidence(
                    session,
                    result,
                    "learning_progress",
                    "increase",
                    2,
                    "会议整理形成学习任务、进度或后续计划：" + truncate(result.getAnswer(), 820),
                    List.of("会议总结", "学习进度")
            );
            case "meeting_resource_recommendation_agent" -> buildMeetingEvidence(
                    session,
                    result,
                    "resource_preference",
                    "increase",
                    1,
                    "会议资源推荐体现学习资源需求或偏好：" + truncate(result.getAnswer(), 820),
                    List.of("会议资源推荐", "资源偏好")
            );
            default -> null;
        };
        if (evidence == null) {
            return;
        }
        try {
            userProfileService.addEvidence(session.getUserId(), evidence);
        } catch (Exception error) {
            log.warn("meeting profile evidence skipped sessionId={} agentName={}: {}", session.getSessionId(), result.getAgentName(), error.getMessage());
        }
    }

    private UserProfileDTO.EvidenceRequest buildMeetingEvidence(MeetingSession session,
                                                                MeetingAgentResult result,
                                                                String dimensionKey,
                                                                String direction,
                                                                int suggestedDelta,
                                                                String evidenceText,
                                                                List<String> evidenceTags) {
        UserProfileDTO.EvidenceRequest evidence = new UserProfileDTO.EvidenceRequest();
        evidence.setDimensionKey(dimensionKey);
        evidence.setSourceType("meeting");
        evidence.setSourceId(session.getSessionId() + ":" + result.getAgentName() + ":" + result.getId());
        evidence.setAction("analyzed");
        evidence.setObjectType("meeting");
        evidence.setObjectId(session.getSessionId());
        evidence.setObjectName(session.getTitle());
        evidence.setResult(agentLabel(result.getAgentName()));
        evidence.setEvidence(truncate(evidenceText, 1000));
        evidence.setDirection(direction);
        evidence.setSuggestedDelta(suggestedDelta);
        evidence.setEvidenceTags(evidenceTags.stream().filter(StringUtils::hasText).distinct().limit(6).toList());
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("submitter", "MeetingServiceImpl");
        metadata.put("meetingSessionId", session.getSessionId());
        metadata.put("roomCode", session.getRoomCode());
        metadata.put("agentName", result.getAgentName());
        metadata.put("answerType", result.getAnswerType());
        metadata.put("capturePolicy", "post_meeting_agent_result");
        evidence.setMetadata(metadata);
        return evidence;
    }

    private String agentLabel(String agentName) {
        return switch (agentName) {
            case "meeting_member_analysis_agent" -> "成员分析";
            case "meeting_summary_agent" -> "会议总结";
            case "meeting_controller_agent" -> "会议总控";
            case "meeting_resource_recommendation_agent" -> "资源推荐";
            default -> StringUtils.hasText(agentName) ? agentName : "会议智能体";
        };
    }

    private String allMeetingContent(Long meetingSessionId) {
        return recordRepository.findByMeetingSessionIdOrderByCreateTimeAscIdAsc(meetingSessionId).stream()
                .map(record -> String.join("\n",
                        "记录来源：" + (StringUtils.hasText(record.getSource()) ? record.getSource() : MeetingRecord.SOURCE_MANUAL),
                        "记录时间：" + record.getCreateTime(),
                        record.getContent()
                ))
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("\n\n"));
    }

    private String buildPostMeetingAgentInput(MeetingSession session, String content, String agentName) {
        List<String> participantNames = participantRepository.findByMeetingSessionIdOrderBySortOrderAscIdAsc(session.getId()).stream()
                .map(MeetingParticipant::getName)
                .toList();
        String task = switch (agentName) {
            case "meeting_transcription_agent" -> "请整理完整会议转写稿，修正明显断句问题，保留说话人和待确认片段。";
            case "meeting_summary_agent" -> "请生成正式会后纪要，区分核心观点、主要结论、任务分工、后续计划和待确认事项。";
            case "meeting_controller_agent" -> "请梳理会议状态、议题进度、任务分发、下一步调度建议和仍需补充的信息。";
            case "meeting_member_analysis_agent" -> "请基于发言证据分析成员参与情况、理解偏差、薄弱点和后续观察问题。";
            case "meeting_resource_recommendation_agent" -> "请基于会议暴露的问题和任务分工给出成员级学习资源推荐和推送计划。";
            default -> "请基于会议记录输出结构化会议处理结果。";
        };
        return String.join("\n",
                "会议主题：" + session.getTitle(),
                "会议状态：" + statusLabel(session.getStatus()),
                "会议号：" + session.getRoomCode(),
                "参会成员：" + (participantNames.isEmpty() ? "未填写" : String.join("、", participantNames)),
                "整理任务：" + task,
                "要求：只依据下方会议记录，不新增负责人、截止时间、结论或外部链接；缺失信息标注未明确。",
                "完整会议记录：",
                content
        );
    }

    private MeetingSession findSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "会议ID不能为空");
        }
        return sessionRepository.findBySessionId(sessionId.trim())
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "会议不存在"));
    }

    private MeetingSession findOwnedSession(Long userId, String sessionId) {
        MeetingSession session = findSession(sessionId);
        if (userId != null && userId.equals(session.getUserId())) {
            return session;
        }
        throw new BusinessException(Result.FORBIDDEN_CODE, "仅会议创建者可执行该操作");
    }

    private MeetingSession findAccessibleSession(Long userId, String sessionId) {
        MeetingSession session = findSession(sessionId);
        if (userId != null && userId.equals(session.getUserId())) {
            return session;
        }
        String displayName = resolveUserDisplayName(userId);
        if (StringUtils.hasText(displayName)) {
            boolean joined = participantRepository.findByMeetingSessionIdOrderBySortOrderAscIdAsc(session.getId()).stream()
                    .anyMatch(participant -> displayName.equals(participant.getName()));
            if (joined) {
                return session;
            }
        }
        throw new BusinessException(Result.FORBIDDEN_CODE, "请先通过会议号加入会议");
    }

    private void syncParticipants(Long meetingSessionId, List<String> participants) {
        participantRepository.deleteByMeetingSessionId(meetingSessionId);
        List<String> names = participants == null ? List.of() : participants.stream()
                .filter(StringUtils::hasText)
                .map(name -> truncate(name.trim(), 80))
                .distinct()
                .limit(20)
                .toList();
        List<MeetingParticipant> entities = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            MeetingParticipant participant = new MeetingParticipant();
            participant.setMeetingSessionId(meetingSessionId);
            participant.setName(names.get(i));
            participant.setSortOrder(i);
            entities.add(participant);
        }
        participantRepository.saveAll(entities);
    }

    private List<String> withCurrentUser(Long userId, List<String> participants) {
        List<String> merged = new ArrayList<>();
        String currentUserName = resolveUserDisplayName(userId);
        if (StringUtils.hasText(currentUserName)) {
            merged.add(currentUserName);
        }
        if (participants != null) {
            merged.addAll(participants);
        }
        return merged;
    }

    private String resolveUserDisplayName(Long userId) {
        if (userId == null) {
            return "";
        }
        return userRepository.findById(userId)
                .map(this::resolveUserDisplayName)
                .orElse("");
    }

    private String resolveUserDisplayName(User user) {
        if (StringUtils.hasText(user.getRealName())) {
            return user.getRealName().trim();
        }
        if (StringUtils.hasText(user.getUsername())) {
            return user.getUsername().trim();
        }
        if (StringUtils.hasText(user.getPersonalNumber())) {
            return user.getPersonalNumber().trim();
        }
        return "";
    }

    private void addParticipantIfMissing(Long meetingSessionId, String displayName) {
        String name = truncate(displayName.trim(), 80);
        if (!StringUtils.hasText(name)) {
            return;
        }
        List<MeetingParticipant> participants = participantRepository.findByMeetingSessionIdOrderBySortOrderAscIdAsc(meetingSessionId);
        boolean exists = participants.stream().anyMatch(participant -> name.equals(participant.getName()));
        if (exists || participants.size() >= 20) {
            return;
        }
        MeetingParticipant participant = new MeetingParticipant();
        participant.setMeetingSessionId(meetingSessionId);
        participant.setName(name);
        participant.setSortOrder(participants.size());
        participantRepository.save(participant);
    }

    private MeetingRecord saveRecord(MeetingSession session, String content, String source) {
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "会议记录不能为空");
        }
        MeetingRecord record = new MeetingRecord();
        record.setMeetingSessionId(session.getId());
        record.setSource(StringUtils.hasText(source) ? truncate(source.trim(), 40) : MeetingRecord.SOURCE_MANUAL);
        record.setContent(content.trim());
        session.setLastNote(truncate(content.trim().replaceAll("\\s+", " "), 500));
        return recordRepository.save(record);
    }

    private void refreshCounters(MeetingSession session) {
        if (!StringUtils.hasText(session.getRoomCode())) {
            session.setRoomCode(generateRoomCode());
        }
        session.setRecordCount((int) recordRepository.countByMeetingSessionId(session.getId()));
        session.setResultCount((int) resultRepository.countByMeetingSessionId(session.getId()));
        sessionRepository.save(session);
    }

    private String resolveMeetingContent(MeetingSession session, String requestContent) {
        if (StringUtils.hasText(requestContent)) {
            return requestContent.trim();
        }
        String content = latestRecordContent(session.getId());
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "请先填写会议记录");
        }
        return content;
    }

    private String latestRecordContent(Long meetingSessionId) {
        List<MeetingRecord> records = recordRepository.findByMeetingSessionIdOrderByCreateTimeAscIdAsc(meetingSessionId);
        if (records.isEmpty()) {
            return "";
        }
        return records.get(records.size() - 1).getContent();
    }

    private String buildAgentInput(MeetingSession session, String content) {
        List<String> participantNames = participantRepository.findByMeetingSessionIdOrderBySortOrderAscIdAsc(session.getId()).stream()
                .map(MeetingParticipant::getName)
                .toList();
        return String.join("\n",
                "会议主题：" + session.getTitle(),
                "会议状态：" + statusLabel(session.getStatus()),
                "参会成员：" + (participantNames.isEmpty() ? "未填写" : String.join("、", participantNames)),
                "会议记录：",
                content
        );
    }

    private MeetingDTO.SessionDetail buildDetail(MeetingSession session) {
        MeetingDTO.SessionDetail detail = new MeetingDTO.SessionDetail();
        detail.setSession(toSessionItem(session));
        detail.setParticipants(participantRepository.findByMeetingSessionIdOrderBySortOrderAscIdAsc(session.getId()).stream()
                .map(MeetingParticipant::getName)
                .collect(Collectors.toList()));
        detail.setRecords(recordRepository.findByMeetingSessionIdOrderByCreateTimeAscIdAsc(session.getId()).stream()
                .map(this::toRecordItem)
                .collect(Collectors.toList()));
        detail.setResults(resultRepository.findByMeetingSessionIdOrderByCreateTimeDescIdDesc(session.getId()).stream()
                .map(this::toResultItem)
                .collect(Collectors.toList()));
        return detail;
    }

    private MeetingDTO.SessionItem toSessionItem(MeetingSession session) {
        MeetingDTO.SessionItem item = new MeetingDTO.SessionItem();
        item.setSessionId(session.getSessionId());
        item.setRoomCode(session.getRoomCode());
        item.setTitle(session.getTitle());
        item.setMeetingType(session.getMeetingType());
        item.setStatus(session.getStatus());
        item.setScheduledStartTime(session.getScheduledStartTime());
        item.setStartTime(session.getStartTime());
        item.setEndTime(session.getEndTime());
        item.setLastNote(session.getLastNote());
        item.setParticipantCount(participantRepository.findByMeetingSessionIdOrderBySortOrderAscIdAsc(session.getId()).size());
        item.setRecordCount(session.getRecordCount() == null ? 0 : session.getRecordCount());
        item.setResultCount(session.getResultCount() == null ? 0 : session.getResultCount());
        item.setCreateTime(session.getCreateTime());
        item.setUpdateTime(session.getUpdateTime());
        return item;
    }

    private MeetingDTO.RecordItem toRecordItem(MeetingRecord record) {
        MeetingDTO.RecordItem item = new MeetingDTO.RecordItem();
        item.setId(record.getId());
        item.setSource(record.getSource());
        item.setContent(record.getContent());
        item.setCreateTime(record.getCreateTime());
        return item;
    }

    private MeetingDTO.AgentResultItem toResultItem(MeetingAgentResult result) {
        MeetingDTO.AgentResultItem item = new MeetingDTO.AgentResultItem();
        item.setId(result.getId());
        item.setAgentName(result.getAgentName());
        item.setAnswerType(result.getAnswerType());
        item.setAnswer(result.getAnswer());
        item.setCreateTime(result.getCreateTime());
        return item;
    }

    private String normalizeTitle(String title) {
        return StringUtils.hasText(title) ? truncate(title.trim(), 120) : "新的会议";
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return MeetingSession.STATUS_IDLE;
        }
        String normalized = status.trim();
        if (Set.of(MeetingSession.STATUS_IDLE, MeetingSession.STATUS_ACTIVE, MeetingSession.STATUS_PAUSED, MeetingSession.STATUS_ENDED).contains(normalized)) {
            return normalized;
        }
        throw new BusinessException(Result.BAD_REQUEST_CODE, "会议状态不正确");
    }

    private String normalizeMeetingType(String meetingType) {
        if (!StringUtils.hasText(meetingType)) {
            return MeetingSession.TYPE_QUICK;
        }
        String normalized = meetingType.trim();
        if (Set.of(MeetingSession.TYPE_QUICK, MeetingSession.TYPE_RESERVED).contains(normalized)) {
            return normalized;
        }
        throw new BusinessException(Result.BAD_REQUEST_CODE, "会议类型不正确");
    }

    private LocalDateTime resolveScheduledStartTime(LocalDateTime scheduledStartTime) {
        return scheduledStartTime == null ? LocalDateTime.now().plusHours(1) : scheduledStartTime;
    }

    private String normalizeRoomCode(String roomCode) {
        if (!StringUtils.hasText(roomCode)) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "会议号不能为空");
        }
        String normalized = roomCode.trim().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9]{4,12}")) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "会议号格式不正确");
        }
        return normalized;
    }

    private String generateRoomCode() {
        for (int attempt = 0; attempt < 32; attempt++) {
            String code = randomRoomCode();
            if (!sessionRepository.existsByRoomCode(code)) {
                return code;
            }
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private String randomRoomCode() {
        StringBuilder code = new StringBuilder(ROOM_CODE_LENGTH);
        for (int i = 0; i < ROOM_CODE_LENGTH; i++) {
            code.append(ROOM_CODE_CHARS[ROOM_CODE_RANDOM.nextInt(ROOM_CODE_CHARS.length)]);
        }
        return code.toString();
    }

    private String normalizeAgentName(String agentName) {
        if (!StringUtils.hasText(agentName)) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "智能体名称不能为空");
        }
        String normalized = agentName.trim();
        if (!MEETING_AGENTS.contains(normalized)) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "仅支持会议智能体");
        }
        return normalized;
    }

    private String resolveMeetingLlmModel(String requestedModel) {
        if (StringUtils.hasText(requestedModel)) {
            return requestedModel.trim();
        }
        String testedPrefix = latestTestedTextModelPrefix();
        if (StringUtils.hasText(testedPrefix)) {
            return testedPrefix;
        }
        String configuredPrefix = firstCompleteTextModelPrefix();
        if (StringUtils.hasText(configuredPrefix)) {
            return configuredPrefix;
        }
        throw new BusinessException(
                Result.ERROR_CODE,
                "会议总结需要先配置并测试成功一个语言模型，请到后台 AI 模块 > 模型配置中完成语言模型测试"
        );
    }

    private String latestTestedTextModelPrefix() {
        List<SystemConfigTestLog> logs = systemConfigTestLogRepository
                .findByConfigKeyStartingWithAndSuccessOrderByCreateTimeDescIdDesc("ai.service.text.", true, Pageable.ofSize(50));
        return logs.stream()
                .map(log -> extractAiModelConfigPrefix(log.getConfigKey()))
                .filter(StringUtils::hasText)
                .filter(this::isCompleteAiModelConfig)
                .findFirst()
                .orElse("");
    }

    private String firstCompleteTextModelPrefix() {
        Map<String, Set<String>> fieldsByPrefix = new HashMap<>();
        systemConfigRepository.findByConfigKeyStartingWithAndStatus("ai.service.text.", 1).forEach(config -> {
            String prefix = extractAiModelConfigPrefix(config.getConfigKey());
            String field = extractAiModelConfigField(config.getConfigKey());
            if (StringUtils.hasText(prefix) && StringUtils.hasText(field) && StringUtils.hasText(config.getConfigValue())) {
                fieldsByPrefix.computeIfAbsent(prefix, ignored -> new java.util.HashSet<>()).add(field);
            }
        });
        return fieldsByPrefix.entrySet().stream()
                .filter(entry -> entry.getValue().containsAll(AI_MODEL_CONFIG_FIELDS))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("");
    }

    private boolean isCompleteAiModelConfig(String configPrefix) {
        return AI_MODEL_CONFIG_FIELDS.stream().allMatch(field ->
                systemConfigRepository.findByConfigKeyAndStatus(configPrefix + "." + field, 1)
                        .map(SystemConfig::getConfigValue)
                        .filter(StringUtils::hasText)
                        .isPresent()
        );
    }

    private String extractAiModelConfigPrefix(String configKey) {
        if (!StringUtils.hasText(configKey)) {
            return "";
        }
        for (String field : AI_MODEL_CONFIG_FIELDS) {
            String suffix = "." + field;
            if (configKey.endsWith(suffix)) {
                return configKey.substring(0, configKey.length() - suffix.length());
            }
        }
        return "";
    }

    private String extractAiModelConfigField(String configKey) {
        if (!StringUtils.hasText(configKey)) {
            return "";
        }
        for (String field : AI_MODEL_CONFIG_FIELDS) {
            if (configKey.endsWith("." + field)) {
                return field;
            }
        }
        return "";
    }

    private String statusLabel(String status) {
        return switch (status) {
            case MeetingSession.STATUS_ACTIVE -> "会议中";
            case MeetingSession.STATUS_PAUSED -> "已暂停";
            case MeetingSession.STATUS_ENDED -> "已结束";
            default -> "待开始";
        };
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
