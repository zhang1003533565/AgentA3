package com.example.appbackend.service.impl;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.ForumComment;
import com.example.appbackend.entity.ForumPost;
import com.example.appbackend.entity.ForumReport;
import com.example.appbackend.entity.ForumReportAuditLog;
import com.example.appbackend.entity.User;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ForumCommentRepository;
import com.example.appbackend.repository.ForumPostRepository;
import com.example.appbackend.repository.ForumReportAuditLogRepository;
import com.example.appbackend.repository.ForumReportRepository;
import com.example.appbackend.repository.UserRepository;
import com.example.appbackend.service.CommentService;
import com.example.appbackend.service.ForumReportService;
import com.example.appbackend.service.PostService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class ForumReportServiceImpl implements ForumReportService {

    private static final Logger log = LoggerFactory.getLogger(ForumReportServiceImpl.class);
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_HANDLED = 1;
    private static final int STATUS_REJECTED = 2;
    private static final int TARGET_POST = 1;
    private static final int TARGET_COMMENT = 2;
    private static final String ACTION_IGNORE = "IGNORE";
    private static final String ACTION_DELETE_CONTENT = "DELETE_CONTENT";
    private static final String POST_STATUS_PUBLISHED = "PUBLISHED";
    private static final String COMMENT_STATUS_NORMAL = "NORMAL";

    @Autowired
    private ForumReportRepository reportRepository;

    @Autowired
    private ForumReportAuditLogRepository auditLogRepository;

    @Autowired
    private ForumPostRepository postRepository;

    @Autowired
    private ForumCommentRepository commentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostService postService;

    @Autowired
    private CommentService commentService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ForumReportResponse> getReports(Integer page, Integer size, Integer status, Integer targetType) {
        validateStatus(status);
        validateTargetType(targetType);

        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 10 : size;
        PageRequest pageRequest = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<ForumReport> reportPage = reportRepository.findReports(status, targetType, pageRequest);
        List<ForumReportResponse> records = reportPage.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return new PageResponse<>(records, reportPage.getTotalElements(), safePage, safeSize);
    }

    @Override
    public ForumReportResponse createReport(ForumReportCreateRequest request, Long reporterId) {
        if (reporterId == null) {
            throw new BusinessException(401, "请先登录");
        }
        validateTargetType(request.getTargetType());
        validateTargetAvailable(request.getTargetType(), request.getTargetId());

        ForumReport report = new ForumReport();
        report.setReporterId(reporterId);
        report.setTargetType(request.getTargetType());
        report.setTargetId(request.getTargetId());
        report.setReasonType(request.getReasonType());
        report.setReasonText(trimToNull(request.getReasonText()));
        report.setDescription(trimToNull(request.getDescription()));
        report.setStatus(STATUS_PENDING);

        ForumReport saved = reportRepository.save(report);
        saveAuditLog(saved, "CREATE_REPORT", reporterId, "用户提交举报");
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ForumReportResponse getReportDetail(Long id) {
        ForumReport report = reportRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "举报记录不存在"));
        return toResponse(report);
    }

    @Override
    public ForumReportResponse handleReport(Long id, ForumReportHandleRequest request, Long handlerId) {
        ForumReport report = reportRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "举报记录不存在"));
        if (report.getStatus() == null || report.getStatus() != STATUS_PENDING) {
            throw new BusinessException(400, "举报已处理，不能重复处理");
        }

        String action = normalizeAction(request.getAction());
        if (ACTION_DELETE_CONTENT.equals(action)) {
            deleteTargetContent(report);
            report.setStatus(STATUS_HANDLED);
        } else {
            report.setStatus(STATUS_REJECTED);
        }

        report.setHandleAction(action);
        report.setHandleBy(handlerId);
        report.setHandleTime(LocalDateTime.now());
        report.setHandleResult(resolveHandleResult(request.getHandleResult(), action));

        ForumReport saved = reportRepository.save(report);
        saveAuditLog(saved, action, handlerId, saved.getHandleResult());
        log.info("Forum report handled. reportId={}, action={}, targetType={}, targetId={}, handlerId={}, result={}",
                saved.getId(), action, saved.getTargetType(), saved.getTargetId(), handlerId, saved.getHandleResult());
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ForumReportStatisticsResponse getStatistics() {
        return new ForumReportStatisticsResponse(
                reportRepository.count(),
                reportRepository.countByStatus(STATUS_PENDING),
                reportRepository.countByStatus(STATUS_HANDLED),
                reportRepository.countByStatus(STATUS_REJECTED),
                reportRepository.countByTargetType(TARGET_POST),
                reportRepository.countByTargetType(TARGET_COMMENT));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ForumReportAuditLogResponse> getAuditLogs(Long reportId) {
        if (!reportRepository.existsById(reportId)) {
            throw new BusinessException(404, "举报记录不存在");
        }
        return auditLogRepository.findByReportIdOrderByCreateTimeDesc(reportId).stream()
                .map(this::toAuditLogResponse)
                .collect(Collectors.toList());
    }

    private void validateTargetAvailable(Integer targetType, Long targetId) {
        if (targetId == null) {
            throw new BusinessException(400, "举报目标ID不能为空");
        }
        if (targetType == TARGET_POST) {
            ForumPost post = postRepository.findById(targetId)
                    .orElseThrow(() -> new BusinessException(404, "帖子不存在或已删除"));
            if (!POST_STATUS_PUBLISHED.equals(post.getStatus())) {
                throw new BusinessException(400, "帖子已不可举报");
            }
            return;
        }
        if (targetType == TARGET_COMMENT) {
            ForumComment comment = commentRepository.findById(targetId)
                    .orElseThrow(() -> new BusinessException(404, "评论不存在或已删除"));
            if (!COMMENT_STATUS_NORMAL.equals(comment.getStatus())) {
                throw new BusinessException(400, "评论已不可举报");
            }
        }
    }

    private void deleteTargetContent(ForumReport report) {
        if (report.getTargetType() == TARGET_POST) {
            postService.deletePostByAdmin(report.getTargetId());
            return;
        }
        if (report.getTargetType() == TARGET_COMMENT) {
            commentService.deleteCommentByAdmin(report.getTargetId());
            return;
        }
        throw new BusinessException(400, "不支持的举报目标类型");
    }

    private String normalizeAction(String action) {
        String normalized = action == null ? "" : action.trim().toUpperCase();
        if (ACTION_IGNORE.equals(normalized) || "REJECT".equals(normalized)) {
            return ACTION_IGNORE;
        }
        if (ACTION_DELETE_CONTENT.equals(normalized) || "DELETE".equals(normalized)) {
            return ACTION_DELETE_CONTENT;
        }
        throw new BusinessException(400, "处理动作仅支持 IGNORE 或 DELETE_CONTENT");
    }

    private String resolveHandleResult(String handleResult, String action) {
        if (handleResult != null && !handleResult.isBlank()) {
            return handleResult.trim();
        }
        return ACTION_DELETE_CONTENT.equals(action) ? "举报成立，已删除被举报内容" : "举报不成立，已忽略";
    }

    private void validateStatus(Integer status) {
        if (status == null) {
            return;
        }
        if (status != STATUS_PENDING && status != STATUS_HANDLED && status != STATUS_REJECTED) {
            throw new BusinessException(400, "举报状态仅支持 0、1、2");
        }
    }

    private void validateTargetType(Integer targetType) {
        if (targetType == null) {
            return;
        }
        if (targetType != TARGET_POST && targetType != TARGET_COMMENT) {
            throw new BusinessException(400, "举报类型仅支持 1-帖子、2-评论");
        }
    }

    private void saveAuditLog(ForumReport report, String action, Long operatorId, String remark) {
        ForumReportAuditLog auditLog = new ForumReportAuditLog();
        auditLog.setReportId(report.getId());
        auditLog.setAction(action);
        auditLog.setOperatorId(operatorId);
        auditLog.setTargetType(report.getTargetType());
        auditLog.setTargetId(report.getTargetId());
        auditLog.setRemark(remark);
        auditLogRepository.save(auditLog);
    }

    private ForumReportResponse toResponse(ForumReport report) {
        ForumReportResponse response = new ForumReportResponse();
        response.setId(report.getId());
        response.setReporterId(report.getReporterId());
        response.setReporterName(resolveUserName(report.getReporterId()));
        response.setTargetType(report.getTargetType());
        response.setTargetId(report.getTargetId());
        response.setReasonType(report.getReasonType());
        response.setReasonText(report.getReasonText());
        response.setDescription(report.getDescription());
        response.setStatus(report.getStatus());
        response.setHandleAction(report.getHandleAction());
        response.setHandleResult(report.getHandleResult());
        response.setHandleBy(report.getHandleBy());
        response.setHandleByName(resolveUserName(report.getHandleBy()));
        response.setHandleTime(report.getHandleTime());
        response.setCreateTime(report.getCreateTime());
        response.setUpdateTime(report.getUpdateTime());
        fillTargetInfo(report, response);
        return response;
    }

    private ForumReportAuditLogResponse toAuditLogResponse(ForumReportAuditLog auditLog) {
        ForumReportAuditLogResponse response = new ForumReportAuditLogResponse();
        response.setId(auditLog.getId());
        response.setReportId(auditLog.getReportId());
        response.setAction(auditLog.getAction());
        response.setOperatorId(auditLog.getOperatorId());
        response.setOperatorName(resolveUserName(auditLog.getOperatorId()));
        response.setTargetType(auditLog.getTargetType());
        response.setTargetId(auditLog.getTargetId());
        response.setRemark(auditLog.getRemark());
        response.setCreateTime(auditLog.getCreateTime());
        return response;
    }

    private void fillTargetInfo(ForumReport report, ForumReportResponse response) {
        if (report.getTargetType() == TARGET_POST) {
            postRepository.findById(report.getTargetId())
                    .ifPresentOrElse(post -> fillPostTarget(post, response), () -> response.setTargetTitle("原帖子已删除"));
            return;
        }
        if (report.getTargetType() == TARGET_COMMENT) {
            commentRepository.findById(report.getTargetId())
                    .ifPresentOrElse(comment -> fillCommentTarget(comment, response), () -> response.setTargetTitle("原评论已删除"));
        }
    }

    private void fillPostTarget(ForumPost post, ForumReportResponse response) {
        response.setTargetTitle(post.getTitle());
        response.setTargetAuthorId(post.getUserId());
        response.setTargetAuthor(resolveUserName(post.getUserId()));
    }

    private void fillCommentTarget(ForumComment comment, ForumReportResponse response) {
        String content = comment.getContent();
        response.setTargetTitle(content != null && content.length() > 50 ? content.substring(0, 50) + "..." : content);
        response.setTargetAuthorId(comment.getUserId());
        response.setTargetAuthor(resolveUserName(comment.getUserId()));
    }

    private String resolveUserName(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .map(this::displayName)
                .orElse("用户不存在");
    }

    private String displayName(User user) {
        if (user.getRealName() != null && !user.getRealName().isBlank()) {
            return user.getRealName();
        }
        return user.getUsername();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
