package com.example.appbackend.service;

import com.example.appbackend.dto.*;

import java.util.List;

public interface ForumReportService {

    PageResponse<ForumReportResponse> getReports(Integer page, Integer size, Integer status, Integer targetType);

    ForumReportResponse createReport(ForumReportCreateRequest request, Long reporterId);

    ForumReportResponse getReportDetail(Long id);

    ForumReportResponse handleReport(Long id, ForumReportHandleRequest request, Long handlerId);

    ForumReportStatisticsResponse getStatistics();

    List<ForumReportAuditLogResponse> getAuditLogs(Long reportId);
}
