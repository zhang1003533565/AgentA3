package com.example.appbackend.service.impl;

import com.example.appbackend.dto.LlmChatRequest;
import com.example.appbackend.dto.LlmChatResponse;
import com.example.appbackend.dto.MeetingDTO;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.MeetingAgentResult;
import com.example.appbackend.entity.MeetingParticipant;
import com.example.appbackend.entity.MeetingRecord;
import com.example.appbackend.entity.MeetingSession;
import com.example.appbackend.entity.Result;
import com.example.appbackend.entity.User;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.MeetingAgentResultRepository;
import com.example.appbackend.repository.MeetingParticipantRepository;
import com.example.appbackend.repository.MeetingRecordRepository;
import com.example.appbackend.repository.MeetingSessionRepository;
import com.example.appbackend.repository.UserRepository;
import com.example.appbackend.service.LlmService;
import com.example.appbackend.service.MeetingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
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

    private static final int LLM_INPUT_LIMIT = 3900;
    private static final char[] ROOM_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int ROOM_CODE_LENGTH = 6;
    private static final SecureRandom ROOM_CODE_RANDOM = new SecureRandom();

    private final MeetingSessionRepository sessionRepository;
    private final MeetingParticipantRepository participantRepository;
    private final MeetingRecordRepository recordRepository;
    private final MeetingAgentResultRepository resultRepository;
    private final UserRepository userRepository;
    private final LlmService llmService;

    public MeetingServiceImpl(MeetingSessionRepository sessionRepository,
                              MeetingParticipantRepository participantRepository,
                              MeetingRecordRepository recordRepository,
                              MeetingAgentResultRepository resultRepository,
                              UserRepository userRepository,
                              LlmService llmService) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.recordRepository = recordRepository;
        this.resultRepository = resultRepository;
        this.userRepository = userRepository;
        this.llmService = llmService;
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
        MeetingSession session = findAccessibleSession(sessionId);
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
        return buildDetail(findAccessibleSession(sessionId));
    }

    @Override
    @Transactional
    public MeetingDTO.SessionDetail startMeeting(Long userId, String sessionId) {
        MeetingSession session = findAccessibleSession(sessionId);
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
    public MeetingDTO.SessionDetail endMeeting(Long userId, String sessionId) {
        MeetingSession session = findAccessibleSession(sessionId);
        session.setStatus(MeetingSession.STATUS_ENDED);
        session.setEndTime(LocalDateTime.now());
        refreshCounters(session);
        return buildDetail(session);
    }

    @Override
    @Transactional
    public MeetingDTO.RecordItem addRecord(Long userId, String sessionId, MeetingDTO.RecordRequest request) {
        MeetingSession session = findAccessibleSession(sessionId);
        MeetingRecord record = saveRecord(session, request.getContent(), request.getSource());
        refreshCounters(session);
        return toRecordItem(record);
    }

    @Override
    @Transactional
    public MeetingDTO.RunAgentResponse runAgent(Long userId, String sessionId, MeetingDTO.RunAgentRequest request, String authorization) {
        MeetingSession session = findAccessibleSession(sessionId);
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
        chatRequest.setLlmModel(request.getLlmModel());
        chatRequest.setInput(truncate(buildAgentInput(session, content), LLM_INPUT_LIMIT));

        LlmChatResponse chatResponse = llmService.chat(chatRequest, authorization);
        MeetingAgentResult result = new MeetingAgentResult();
        result.setMeetingSessionId(session.getId());
        result.setAgentName(agentName);
        result.setAnswerType(StringUtils.hasText(chatResponse.getAnswerType()) ? chatResponse.getAnswerType() : "markdown");
        result.setAnswer(StringUtils.hasText(chatResponse.getAnswer()) ? chatResponse.getAnswer() : "智能体没有返回可用内容。");
        result = resultRepository.save(result);

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
        MeetingSession session = findAccessibleSession(sessionId);
        String agentName = normalizeAgentName(request.getAgentName());
        String content = resolveMeetingContent(session, request.getContent());

        LlmChatRequest chatRequest = new LlmChatRequest();
        chatRequest.setSessionId(session.getSessionId());
        chatRequest.setAgentName(agentName);
        chatRequest.setLlmModel(request.getLlmModel());
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

    private MeetingSession findAccessibleSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "会议ID不能为空");
        }
        return sessionRepository.findBySessionId(sessionId.trim())
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "会议不存在"));
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
