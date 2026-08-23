package com.example.appbackend.service;

import com.example.appbackend.dto.*;
import org.springframework.data.domain.Page;

public interface SecondhandReportService {

    SecondhandReportResponse createReport(SecondhandReportCreateRequest createRequest, Long reporterId);

    Page<SecondhandReportResponse> getReports(Integer page, Integer size, Integer status);

    SecondhandReportResponse getReportDetail(Long id);

    SecondhandReportResponse handleReport(Long id, SecondhandReportHandleRequest handleRequest, Long handlerId);

    SecondhandReportStatisticsResponse getStatistics();
}
