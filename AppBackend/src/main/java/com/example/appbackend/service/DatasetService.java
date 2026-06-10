package com.example.appbackend.service;

import com.example.appbackend.dto.DatasetDTO;
import com.example.appbackend.dto.PageResponse;

/**
 * 知识库管理服务（对标 Dify DatasetService + DocumentService + SegmentService）
 */
public interface DatasetService {

    // ====== Dataset（知识库） ======

    DatasetDTO.DatasetVO createDataset(DatasetDTO.CreateRequest request, Long userId);

    DatasetDTO.DatasetVO getDataset(Long datasetId);

    DatasetDTO.DatasetVO updateDataset(Long datasetId, DatasetDTO.CreateRequest request);

    void deleteDataset(Long datasetId);

    PageResponse<DatasetDTO.DatasetListItem> listDatasets(String keyword, int current, int size);

    // ====== ProcessRule（处理规则） ======

    DatasetDTO.ProcessRuleVO createProcessRule(Long datasetId, DatasetDTO.ProcessRuleRequest request, Long userId);

    // ====== Document（文档） ======

    DatasetDTO.DocumentVO createDocument(Long datasetId, DatasetDTO.DocumentCreateRequest request, Long userId, String authorization);

    DatasetDTO.DocumentVO getDocument(Long documentId);

    PageResponse<DatasetDTO.DocumentListItem> listDocuments(Long datasetId, String keyword, int current, int size);

    void deleteDocument(Long documentId);

    void enableDocument(Long documentId, boolean enabled);

    // ====== Segment（分段） ======

    PageResponse<DatasetDTO.SegmentListItem> listSegments(Long documentId, String keyword, int current, int size);

    DatasetDTO.SegmentVO getSegment(Long segmentId);

    DatasetDTO.SegmentVO updateSegment(Long segmentId, DatasetDTO.SegmentUpdateRequest request);

    void deleteSegment(Long segmentId);

    void toggleSegment(Long segmentId, boolean enabled);

    // ====== ChildChunk（子片段） ======

    java.util.List<DatasetDTO.ChildChunkVO> listChildChunks(Long segmentId);
}
