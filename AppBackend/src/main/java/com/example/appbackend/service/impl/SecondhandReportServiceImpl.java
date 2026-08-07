package com.example.appbackend.service.impl;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.SecondhandItem;
import com.example.appbackend.entity.SecondhandReport;
import com.example.appbackend.entity.User;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.SecondhandItemRepository;
import com.example.appbackend.repository.SecondhandReportRepository;
import com.example.appbackend.repository.UserRepository;
import com.example.appbackend.service.SecondhandReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
@Transactional
public class SecondhandReportServiceImpl implements SecondhandReportService {

    private static final Logger log = LoggerFactory.getLogger(SecondhandReportServiceImpl.class);
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_HANDLED = 1;
    private static final int STATUS_REJECTED = 2;
    private static final String ACTION_IGNORE = "IGNORE";
    private static final String ACTION_OFFLINE_ITEM = "OFFLINE_ITEM";

    @Autowired
    private SecondhandReportRepository reportRepository;

    @Autowired
    private SecondhandItemRepository itemRepository;

    @Autowired
    private UserRepository userRepository;

    @Override
    public SecondhandReportResponse createReport(SecondhandReportCreateRequest request, Long reporterId) {
        if (reporterId == null) {
            throw new BusinessException(401, "请先登录");
        }

        SecondhandItem item = itemRepository.findById(request.getItemId())
                .orElseThrow(() -> new BusinessException(404, "被举报的物品不存在"));

        SecondhandReport report = new SecondhandReport();
        report.setReporterId(reporterId);
        report.setReporterName(trimToNull(request.getReporterName()));
        report.setReporterContact(trimToNull(request.getReporterContact()));
        report.setItemId(item.getId());
        report.setItemTitle(item.getTitle());
        report.setItemSellerId(item.getUserId());
        report.setItemSellerName(resolveUserName(item.getUserId()));
        report.setReasonType(request.getReasonType());
        report.setReason(trimToNull(request.getReason()));
        report.setStatus(STATUS_PENDING);

        SecondhandReport saved = reportRepository.save(report);
        log.info("Secondhand report created. reportId={}, itemId={}, reporterId={}",
                saved.getId(), item.getId(), reporterId);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SecondhandReportResponse> getReports(Integer page, Integer size, Integer status) {
        validateStatus(status);

        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 10 : size;
        PageRequest pageRequest = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createTime"));

        Page<SecondhandReport> reportPage;
        if (status != null) {
            reportPage = reportRepository.findByStatus(status, pageRequest);
        } else {
            reportPage = reportRepository.findAllBy(pageRequest);
        }

        return reportPage.map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public SecondhandReportResponse getReportDetail(Long id) {
        SecondhandReport report = reportRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "举报记录不存在"));
        return toResponse(report);
    }

    @Override
    public SecondhandReportResponse handleReport(Long id, SecondhandReportHandleRequest request, Long handlerId) {
        SecondhandReport report = reportRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "举报记录不存在"));
        if (report.getStatus() == null || report.getStatus() != STATUS_PENDING) {
            throw new BusinessException(400, "举报已处理，不能重复处理");
        }

        String action = normalizeAction(request.getAction());
        if (ACTION_OFFLINE_ITEM.equals(action)) {
            offlineItem(report.getItemId());
            report.setStatus(STATUS_HANDLED);
        } else {
            report.setStatus(STATUS_REJECTED);
        }

        report.setHandleAction(action);
        report.setHandleBy(handlerId);
        report.setHandleTime(LocalDateTime.now());
        report.setHandleResult(resolveHandleResult(request.getHandleResult(), action));

        SecondhandReport saved = reportRepository.save(report);
        log.info("Secondhand report handled. reportId={}, action={}, itemId={}, handlerId={}, result={}",
                saved.getId(), action, saved.getItemId(), handlerId, saved.getHandleResult());
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public SecondhandReportStatisticsResponse getStatistics() {
        return new SecondhandReportStatisticsResponse(
                reportRepository.count(),
                reportRepository.countByStatus(STATUS_PENDING),
                reportRepository.countByStatus(STATUS_HANDLED),
                reportRepository.countByStatus(STATUS_REJECTED));
    }

    private void offlineItem(Long itemId) {
        SecondhandItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(404, "物品不存在"));
        if (item.getStatus() == 2) {
            item.setStatus(4);
            itemRepository.save(item);
            log.info("Item offline due to report. itemId={}", itemId);
        }
    }

    private String normalizeAction(String action) {
        String normalized = action == null ? "" : action.trim().toUpperCase();
        if (ACTION_IGNORE.equals(normalized) || "REJECT".equals(normalized)) {
            return ACTION_IGNORE;
        }
        if (ACTION_OFFLINE_ITEM.equals(normalized) || "OFFLINE".equals(normalized)) {
            return ACTION_OFFLINE_ITEM;
        }
        throw new BusinessException(400, "处理动作仅支持 IGNORE 或 OFFLINE_ITEM");
    }

    private String resolveHandleResult(String handleResult, String action) {
        if (handleResult != null && !handleResult.isBlank()) {
            return handleResult.trim();
        }
        return ACTION_OFFLINE_ITEM.equals(action) ? "举报成立，已下架商品" : "举报不成立，已忽略";
    }

    private void validateStatus(Integer status) {
        if (status == null) {
            return;
        }
        if (status != STATUS_PENDING && status != STATUS_HANDLED && status != STATUS_REJECTED) {
            throw new BusinessException(400, "举报状态仅支持 0、1、2");
        }
    }

    private SecondhandReportResponse toResponse(SecondhandReport report) {
        SecondhandReportResponse response = new SecondhandReportResponse();
        response.setId(report.getId());
        response.setReporterId(report.getReporterId());
        response.setReporterName(report.getReporterName());
        response.setReporterContact(report.getReporterContact());
        response.setItemId(report.getItemId());
        response.setItemTitle(report.getItemTitle());
        response.setItemSellerId(report.getItemSellerId());
        response.setItemSellerName(report.getItemSellerName());
        response.setReasonType(report.getReasonType());
        response.setReasonTypeText(resolveReasonTypeText(report.getReasonType()));
        response.setReason(report.getReason());
        response.setStatus(report.getStatus());
        response.setStatusText(resolveStatusText(report.getStatus()));
        response.setHandleAction(report.getHandleAction());
        response.setHandleResult(report.getHandleResult());
        response.setHandleBy(report.getHandleBy());
        response.setHandleByName(resolveUserName(report.getHandleBy()));
        response.setHandleTime(report.getHandleTime());
        response.setCreateTime(report.getCreateTime());
        return response;
    }

    private String resolveReasonTypeText(Integer reasonType) {
        if (reasonType == null) return "-";
        switch (reasonType) {
            case 1: return "虚假信息";
            case 2: return "不良行为";
            case 3: return "其他违规";
            default: return "未知类型";
        }
    }

    private String resolveStatusText(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case 0: return "待处理";
            case 1: return "已处理";
            case 2: return "已驳回";
            default: return "未知";
        }
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
