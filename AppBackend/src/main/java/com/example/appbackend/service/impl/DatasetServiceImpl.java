package com.example.appbackend.service.impl;

import com.example.appbackend.dto.DatasetDTO;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.*;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.*;
import com.example.appbackend.service.DatasetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DatasetServiceImpl implements DatasetService {

    private static final Logger log = LoggerFactory.getLogger(DatasetServiceImpl.class);

    private final DatasetRepository datasetRepository;
    private final DatasetProcessRuleRepository processRuleRepository;
    private final KbDocumentRepository documentRepository;
    private final DocumentSegmentRepository segmentRepository;
    private final ChildChunkRepository childChunkRepository;
    private final PythonAiProxyService pythonAiProxyService;

    public DatasetServiceImpl(DatasetRepository datasetRepository,
                              DatasetProcessRuleRepository processRuleRepository,
                              KbDocumentRepository documentRepository,
                              DocumentSegmentRepository segmentRepository,
                              ChildChunkRepository childChunkRepository,
                              PythonAiProxyService pythonAiProxyService) {
        this.datasetRepository = datasetRepository;
        this.processRuleRepository = processRuleRepository;
        this.documentRepository = documentRepository;
        this.segmentRepository = segmentRepository;
        this.childChunkRepository = childChunkRepository;
        this.pythonAiProxyService = pythonAiProxyService;
    }

    // ====== Dataset ======

    @Override
    @Transactional
    public DatasetDTO.DatasetVO createDataset(DatasetDTO.CreateRequest request, Long userId) {
        Dataset dataset = new Dataset();
        dataset.setName(request.getName());
        dataset.setDescription(request.getDescription());
        dataset.setIndexingTechnique(StringUtils.hasText(request.getIndexingTechnique()) ? request.getIndexingTechnique() : Dataset.INDEXING_HIGH_QUALITY);
        dataset.setEmbeddingModel(request.getEmbeddingModel());
        dataset.setEmbeddingModelProvider(request.getEmbeddingModelProvider());
        dataset.setRetrievalModel(request.getRetrievalModel());
        dataset.setChunkStructure(request.getChunkStructure());
        dataset.setPermission(StringUtils.hasText(request.getPermission()) ? request.getPermission() : Dataset.PERMISSION_ONLY_ME);
        dataset.setCreatedBy(userId);
        dataset = datasetRepository.save(dataset);
        log.info("created dataset id={} name={}", dataset.getId(), dataset.getName());
        return toDatasetVO(dataset, null);
    }

    @Override
    public DatasetDTO.DatasetVO getDataset(Long datasetId) {
        Dataset dataset = requireDataset(datasetId);
        List<DatasetProcessRule> rules = processRuleRepository.findByDatasetIdOrderByCreateTimeDesc(datasetId);
        DatasetProcessRule latestRule = rules.isEmpty() ? null : rules.get(0);
        return toDatasetVO(dataset, latestRule);
    }

    @Override
    @Transactional
    public DatasetDTO.DatasetVO updateDataset(Long datasetId, DatasetDTO.CreateRequest request) {
        Dataset dataset = requireDataset(datasetId);
        if (StringUtils.hasText(request.getName())) dataset.setName(request.getName());
        if (request.getDescription() != null) dataset.setDescription(request.getDescription());
        if (StringUtils.hasText(request.getIndexingTechnique())) dataset.setIndexingTechnique(request.getIndexingTechnique());
        if (request.getEmbeddingModel() != null) dataset.setEmbeddingModel(request.getEmbeddingModel());
        if (request.getEmbeddingModelProvider() != null) dataset.setEmbeddingModelProvider(request.getEmbeddingModelProvider());
        if (request.getRetrievalModel() != null) dataset.setRetrievalModel(request.getRetrievalModel());
        if (request.getChunkStructure() != null) dataset.setChunkStructure(request.getChunkStructure());
        if (StringUtils.hasText(request.getPermission())) dataset.setPermission(request.getPermission());
        dataset = datasetRepository.save(dataset);
        List<DatasetProcessRule> rules = processRuleRepository.findByDatasetIdOrderByCreateTimeDesc(datasetId);
        DatasetProcessRule latestRule = rules.isEmpty() ? null : rules.get(0);
        return toDatasetVO(dataset, latestRule);
    }

    @Override
    @Transactional
    public void deleteDataset(Long datasetId) {
        Dataset dataset = requireDataset(datasetId);
        // 删除子片段、分段、文档、处理规则
        List<KbDocument> documents = documentRepository.findByDatasetIdOrderByPositionAsc(datasetId);
        for (KbDocument doc : documents) {
            List<DocumentSegment> segments = segmentRepository.findByDocumentIdOrderByPositionAsc(doc.getId());
            for (DocumentSegment segment : segments) {
                childChunkRepository.deleteAll(childChunkRepository.findBySegmentIdOrderByPositionAsc(segment.getId()));
            }
            segmentRepository.deleteAll(segments);
        }
        documentRepository.deleteAll(documents);
        processRuleRepository.deleteAll(processRuleRepository.findByDatasetIdOrderByCreateTimeDesc(datasetId));
        datasetRepository.delete(dataset);
        log.info("deleted dataset id={}", datasetId);
    }

    @Override
    public PageResponse<DatasetDTO.DatasetListItem> listDatasets(String keyword, int current, int size) {
        Page<Dataset> page = datasetRepository.searchAll(
                StringUtils.hasText(keyword) ? keyword : null,
                PageRequest.of(Math.max(0, current - 1), size));
        List<DatasetDTO.DatasetListItem> records = page.getContent().stream().map(this::toDatasetListItem).collect(Collectors.toList());
        return new PageResponse<>(records, page.getTotalElements(), current, size);
    }

    // ====== ProcessRule ======

    @Override
    @Transactional
    public DatasetDTO.ProcessRuleVO createProcessRule(Long datasetId, DatasetDTO.ProcessRuleRequest request, Long userId) {
        requireDataset(datasetId);
        DatasetProcessRule rule = new DatasetProcessRule();
        rule.setDatasetId(datasetId);
        rule.setMode(StringUtils.hasText(request.getMode()) ? request.getMode() : DatasetProcessRule.MODE_AUTOMATIC);
        rule.setRules(request.getRules());
        rule.setCreatedBy(userId);
        rule = processRuleRepository.save(rule);
        return toProcessRuleVO(rule);
    }

    // ====== Document ======

    @Override
    @Transactional
    public DatasetDTO.DocumentVO createDocument(Long datasetId, DatasetDTO.DocumentCreateRequest request, Long userId, String authorization) {
        Dataset dataset = requireDataset(datasetId);

        // 1. 创建处理规则（如果有）
        DatasetProcessRule processRule = null;
        if (StringUtils.hasText(request.getProcessMode())) {
            DatasetProcessRule pr = new DatasetProcessRule();
            pr.setDatasetId(datasetId);
            pr.setMode(request.getProcessMode());
            pr.setRules(request.getProcessRules());
            pr.setCreatedBy(userId);
            processRule = processRuleRepository.save(pr);
        }

        // 2. 创建文档记录
        KbDocument doc = new KbDocument();
        doc.setDatasetId(datasetId);
        doc.setName(request.getName());
        doc.setDataSourceType(StringUtils.hasText(request.getDataSourceType()) ? request.getDataSourceType() : KbDocument.DATA_SOURCE_TEXT);
        doc.setDocForm(StringUtils.hasText(request.getDocForm()) ? request.getDocForm() : KbDocument.DOC_FORM_TEXT);
        doc.setDocType(StringUtils.hasText(request.getDocType()) ? request.getDocType() : KbDocument.DOC_TYPE_OTHER);
        doc.setDocMetadata(request.getDocMetadata());
        doc.setIndexingStatus(KbDocument.STATUS_WAITING);
        doc.setBatch(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        doc.setProcessRuleId(processRule != null ? processRule.getId() : null);
        doc.setCreatedBy(userId);

        // 保存数据来源信息
        if (KbDocument.DATA_SOURCE_TEXT.equals(doc.getDataSourceType())) {
            doc.setDataSourceInfo("{\"type\":\"text_input\"}");
        } else {
            doc.setDataSourceInfo("{\"type\":\"upload_file\",\"fileName\":\"" + request.getName() + "\"}");
        }

        // 计算最大 position
        List<KbDocument> existing = documentRepository.findByDatasetIdOrderByPositionAsc(datasetId);
        int maxPosition = existing.stream().mapToInt(KbDocument::getPosition).max().orElse(0);
        doc.setPosition(maxPosition + 1);

        doc = documentRepository.save(doc);

        // 3. 更新知识库文档计数
        dataset.setDocumentCount(existing.size() + 1);
        datasetRepository.save(dataset);

        // 4. 转发到 Python 服务处理（异步索引）
        try {
            Map<String, Object> ingestRequest = buildIngestRequest(doc, request, dataset);
            Object result = pythonAiProxyService.ingestRagDocuments(ingestRequest, authorization);
            // 处理成功后更新状态
            doc.setIndexingStatus(KbDocument.STATUS_COMPLETED);
            doc.setCompletedAt(LocalDateTime.now());
            if (result instanceof Map<?, ?> resultMap) {
                Object chunkCount = resultMap.get("indexedChunkCount");
                if (chunkCount instanceof Number num) {
                    doc.setSegmentCount(num.intValue());
                }
                Object storedCount = resultMap.get("storedCount");
                if (storedCount instanceof Number num) {
                    doc.setWordCount(num.intValue());
                }
            }
            doc = documentRepository.save(doc);
            log.info("document indexed successfully id={} name={}", doc.getId(), doc.getName());
        } catch (Exception e) {
            log.error("document indexing failed id={} error={}", doc.getId(), e.getMessage());
            doc.setIndexingStatus(KbDocument.STATUS_ERROR);
            doc.setErrorMessage(e.getMessage());
            doc = documentRepository.save(doc);
        }

        return toDocumentVO(doc, dataset.getName());
    }

    @Override
    public DatasetDTO.DocumentVO getDocument(Long documentId) {
        KbDocument doc = requireDocument(documentId);
        Dataset dataset = requireDataset(doc.getDatasetId());
        return toDocumentVO(doc, dataset.getName());
    }

    @Override
    public PageResponse<DatasetDTO.DocumentListItem> listDocuments(Long datasetId, String keyword, int current, int size) {
        requireDataset(datasetId);
        Page<KbDocument> page = documentRepository.searchByDatasetId(
                datasetId,
                StringUtils.hasText(keyword) ? keyword : null,
                PageRequest.of(Math.max(0, current - 1), size));
        List<DatasetDTO.DocumentListItem> records = page.getContent().stream().map(this::toDocumentListItem).collect(Collectors.toList());
        return new PageResponse<>(records, page.getTotalElements(), current, size);
    }

    @Override
    public PageResponse<DatasetDTO.DocumentListItem> listDocumentsSorted(Long datasetId, String keyword, String sortBy, int current, int size) {
        requireDataset(datasetId);
        Sort sort = buildSort(sortBy);
        Page<KbDocument> page = documentRepository.searchByDatasetIdWithSort(
                datasetId,
                StringUtils.hasText(keyword) ? keyword : null,
                PageRequest.of(Math.max(0, current - 1), size, sort));
        List<DatasetDTO.DocumentListItem> records = page.getContent().stream().map(this::toDocumentListItem).collect(Collectors.toList());
        return new PageResponse<>(records, page.getTotalElements(), current, size);
    }

    private Sort buildSort(String sortBy) {
        if (sortBy == null || sortBy.isEmpty() || "created_at".equals(sortBy)) {
            return Sort.by(Sort.Direction.DESC, "createTime");
        }
        return switch (sortBy) {
            case "-created_at" -> Sort.by(Sort.Direction.ASC, "createTime");
            case "hit_count" -> Sort.by(Sort.Direction.ASC, "hitCount");
            case "-hit_count" -> Sort.by(Sort.Direction.DESC, "hitCount");
            default -> Sort.by(Sort.Direction.DESC, "createTime");
        };
    }

    @Override
    @Transactional
    public void deleteDocument(Long documentId) {
        KbDocument doc = requireDocument(documentId);
        Long datasetId = doc.getDatasetId();
        // 删除子片段
        List<DocumentSegment> segments = segmentRepository.findByDocumentIdOrderByPositionAsc(documentId);
        for (DocumentSegment segment : segments) {
            childChunkRepository.deleteAll(childChunkRepository.findBySegmentIdOrderByPositionAsc(segment.getId()));
        }
        segmentRepository.deleteAll(segments);
        documentRepository.delete(doc);
        // 更新知识库统计
        Dataset dataset = requireDataset(datasetId);
        long newCount = documentRepository.countByDatasetId(datasetId);
        dataset.setDocumentCount((int) newCount);
        datasetRepository.save(dataset);
    }

    @Override
    @Transactional
    public void enableDocument(Long documentId, boolean enabled) {
        KbDocument doc = requireDocument(documentId);
        doc.setEnabled(enabled ? 1 : 0);
        documentRepository.save(doc);
    }

    @Override
    @Transactional
    public void processDocumentAction(Long documentId, String action) {
        KbDocument doc = requireDocument(documentId);
        if ("pause".equals(action)) {
            if (!KbDocument.STATUS_INDEXING.equals(doc.getIndexingStatus())) {
                throw new BusinessException(Result.BAD_REQUEST_CODE, "仅索引中的文档可以暂停，当前状态: " + doc.getIndexingStatus());
            }
            doc.setIndexingStatus(KbDocument.STATUS_PAUSED);
        } else if ("resume".equals(action)) {
            String currentStatus = doc.getIndexingStatus();
            if (!KbDocument.STATUS_PAUSED.equals(currentStatus) && !KbDocument.STATUS_ERROR.equals(currentStatus)) {
                throw new BusinessException(Result.BAD_REQUEST_CODE, "仅暂停或错误的文档可以恢复，当前状态: " + currentStatus);
            }
            doc.setIndexingStatus(KbDocument.STATUS_INDEXING);
        } else {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "不支持的操作: " + action + "，仅支持 pause / resume");
        }
        documentRepository.save(doc);
        log.info("document id={} action={} newStatus={}", documentId, action, doc.getIndexingStatus());
    }

    @Override
    @Transactional
    public DatasetDTO.DocumentVO renameDocument(Long documentId, DatasetDTO.RenameRequest request) {
        KbDocument doc = requireDocument(documentId);
        doc.setName(request.getName());
        doc = documentRepository.save(doc);
        Dataset dataset = requireDataset(doc.getDatasetId());
        return toDocumentVO(doc, dataset.getName());
    }

    @Override
    @Transactional
    public void archiveDocument(Long documentId) {
        KbDocument doc = requireDocument(documentId);
        doc.setArchived(1);
        documentRepository.save(doc);
    }

    @Override
    @Transactional
    public void unarchiveDocument(Long documentId) {
        KbDocument doc = requireDocument(documentId);
        doc.setArchived(0);
        documentRepository.save(doc);
    }

    @Override
    @Transactional
    public void retryFailedDocuments(Long datasetId, DatasetDTO.RetryRequest request) {
        requireDataset(datasetId);
        if (request.getDocumentIds() == null || request.getDocumentIds().isEmpty()) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "documentIds 不能为空");
        }
        int retried = 0;
        for (Long docId : request.getDocumentIds()) {
            KbDocument doc = documentRepository.findById(docId).orElse(null);
            if (doc != null && doc.getDatasetId().equals(datasetId) && KbDocument.STATUS_ERROR.equals(doc.getIndexingStatus())) {
                doc.setIndexingStatus(KbDocument.STATUS_WAITING);
                doc.setErrorMessage(null);
                documentRepository.save(doc);
                retried++;
                log.info("retrying failed document id={} datasetId={}", docId, datasetId);
            }
        }
        log.info("retry completed datasetId={} requested={} retried={}", datasetId, request.getDocumentIds().size(), retried);
    }

    // ====== Segment ======

    @Override
    public PageResponse<DatasetDTO.SegmentListItem> listSegments(Long documentId, String keyword, int current, int size) {
        requireDocument(documentId);
        Page<DocumentSegment> page = segmentRepository.searchByDocumentId(
                documentId,
                StringUtils.hasText(keyword) ? keyword : null,
                PageRequest.of(Math.max(0, current - 1), size));
        KbDocument doc = requireDocument(documentId);
        List<DatasetDTO.SegmentListItem> records = page.getContent().stream()
                .map(s -> toSegmentListItem(s, doc.getName()))
                .collect(Collectors.toList());
        return new PageResponse<>(records, page.getTotalElements(), current, size);
    }

    @Override
    public DatasetDTO.SegmentVO getSegment(Long segmentId) {
        DocumentSegment segment = requireSegment(segmentId);
        KbDocument doc = requireDocument(segment.getDocumentId());
        List<ChildChunk> childChunks = childChunkRepository.findBySegmentIdOrderByPositionAsc(segmentId);
        return toSegmentVO(segment, doc.getName(), childChunks);
    }

    @Override
    @Transactional
    public DatasetDTO.SegmentVO updateSegment(Long segmentId, DatasetDTO.SegmentUpdateRequest request) {
        DocumentSegment segment = requireSegment(segmentId);
        if (request.getContent() != null) {
            segment.setContent(request.getContent());
            segment.setWordCount(request.getContent().length());
        }
        if (request.getAnswer() != null) segment.setAnswer(request.getAnswer());
        if (request.getKeywords() != null) segment.setKeywords(request.getKeywords());
        if (request.getEnabled() != null) segment.setEnabled(request.getEnabled());
        segment = segmentRepository.save(segment);
        KbDocument doc = requireDocument(segment.getDocumentId());
        List<ChildChunk> childChunks = childChunkRepository.findBySegmentIdOrderByPositionAsc(segmentId);
        return toSegmentVO(segment, doc.getName(), childChunks);
    }

    @Override
    @Transactional
    public void deleteSegment(Long segmentId) {
        DocumentSegment segment = requireSegment(segmentId);
        Long documentId = segment.getDocumentId();
        childChunkRepository.deleteAll(childChunkRepository.findBySegmentIdOrderByPositionAsc(segmentId));
        segmentRepository.delete(segment);
        // 更新文档分段计数
        long newCount = segmentRepository.countByDocumentId(documentId);
        documentRepository.updateSegmentCount(documentId, (int) newCount);
    }

    @Override
    @Transactional
    public void toggleSegment(Long segmentId, boolean enabled) {
        DocumentSegment segment = requireSegment(segmentId);
        segment.setEnabled(enabled ? 1 : 0);
        segmentRepository.save(segment);
    }

    @Override
    @Transactional
    public DatasetDTO.SegmentVO createSegment(Long documentId, DatasetDTO.CreateSegmentRequest request) {
        KbDocument doc = requireDocument(documentId);
        // 计算下一个 position
        List<DocumentSegment> existing = segmentRepository.findByDocumentIdOrderByPositionAsc(documentId);
        int maxPosition = existing.stream().mapToInt(DocumentSegment::getPosition).max().orElse(0);

        DocumentSegment segment = new DocumentSegment();
        segment.setDatasetId(doc.getDatasetId());
        segment.setDocumentId(documentId);
        segment.setPosition(maxPosition + 1);
        segment.setContent(request.getContent());
        segment.setWordCount(request.getContent() != null ? request.getContent().length() : 0);
        segment.setAnswer(request.getAnswer());
        if (request.getKeywords() != null) {
            try {
                segment.setKeywords(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(request.getKeywords()));
            } catch (Exception e) {
                segment.setKeywords("[]");
            }
        }
        segment.setStatus(DocumentSegment.STATUS_COMPLETED);
        segment = segmentRepository.save(segment);

        // 更新文档分段计数
        long newCount = segmentRepository.countByDocumentId(documentId);
        documentRepository.updateSegmentCount(documentId, (int) newCount);

        List<ChildChunk> childChunks = childChunkRepository.findBySegmentIdOrderByPositionAsc(segment.getId());
        return toSegmentVO(segment, doc.getName(), childChunks);
    }

    @Override
    @Transactional
    public void batchToggleSegments(Long documentId, String action, List<Long> segmentIds) {
        requireDocument(documentId);
        if (segmentIds == null || segmentIds.isEmpty()) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "segmentIds 不能为空");
        }
        int enabledValue;
        if ("enable".equals(action)) {
            enabledValue = 1;
        } else if ("disable".equals(action)) {
            enabledValue = 0;
        } else {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "不支持的操作: " + action + "，仅支持 enable / disable");
        }
        for (Long segId : segmentIds) {
            DocumentSegment segment = segmentRepository.findById(segId).orElse(null);
            if (segment != null && segment.getDocumentId().equals(documentId)) {
                segment.setEnabled(enabledValue);
                segmentRepository.save(segment);
            }
        }
        log.info("batch toggle segments documentId={} action={} count={}", documentId, action, segmentIds.size());
    }

    @Override
    @Transactional
    public void batchDeleteSegments(Long documentId, List<Long> segmentIds) {
        KbDocument doc = requireDocument(documentId);
        if (segmentIds == null || segmentIds.isEmpty()) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "segmentIds 不能为空");
        }
        int deleted = 0;
        for (Long segId : segmentIds) {
            DocumentSegment segment = segmentRepository.findById(segId).orElse(null);
            if (segment != null && segment.getDocumentId().equals(documentId)) {
                childChunkRepository.deleteAll(childChunkRepository.findBySegmentIdOrderByPositionAsc(segId));
                segmentRepository.delete(segment);
                deleted++;
            }
        }
        // 更新文档分段计数
        long newCount = segmentRepository.countByDocumentId(documentId);
        documentRepository.updateSegmentCount(documentId, (int) newCount);
        log.info("batch deleted segments documentId={} requested={} deleted={}", documentId, segmentIds.size(), deleted);
    }

    // ====== ChildChunk ======

    @Override
    public List<DatasetDTO.ChildChunkVO> listChildChunks(Long segmentId) {
        requireSegment(segmentId);
        return childChunkRepository.findBySegmentIdOrderByPositionAsc(segmentId)
                .stream().map(this::toChildChunkVO).collect(Collectors.toList());
    }

    // ====== Helper Methods ======

    private Dataset requireDataset(Long datasetId) {
        return datasetRepository.findById(datasetId)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "知识库不存在: " + datasetId));
    }

    private KbDocument requireDocument(Long documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "文档不存在: " + documentId));
    }

    private DocumentSegment requireSegment(Long segmentId) {
        return segmentRepository.findById(segmentId)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "分段不存在: " + segmentId));
    }

    private Map<String, Object> buildIngestRequest(KbDocument doc, DatasetDTO.DocumentCreateRequest request, Dataset dataset) {
        Map<String, Object> ingestRequest = new HashMap<>();
        if (StringUtils.hasText(request.getEmbeddingModel())) {
            ingestRequest.put("embeddingModel", request.getEmbeddingModel());
        }

        Map<String, Object> documentItem = new HashMap<>();
        documentItem.put("source", doc.getName());

        if (KbDocument.DATA_SOURCE_TEXT.equals(doc.getDataSourceType())) {
            documentItem.put("content", request.getContent());
        } else {
            documentItem.put("contentBase64", request.getContentBase64());
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("origin", "knowledge_base_console");
        metadata.put("knowledgeBaseId", String.valueOf(dataset.getId()));
        metadata.put("knowledgeBaseName", dataset.getName());
        metadata.put("javaDocumentId", doc.getId());
        metadata.put("javaDatasetId", doc.getDatasetId());

        if (StringUtils.hasText(doc.getDocMetadata())) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> extra = new com.fasterxml.jackson.databind.ObjectMapper().readValue(doc.getDocMetadata(), Map.class);
                metadata.putAll(extra);
            } catch (Exception ignored) {
            }
        }

        documentItem.put("metadata", metadata);
        ingestRequest.put("documents", List.of(documentItem));
        return ingestRequest;
    }

    // ====== VO Converters ======

    private DatasetDTO.DatasetVO toDatasetVO(Dataset d, DatasetProcessRule rule) {
        DatasetDTO.DatasetVO vo = new DatasetDTO.DatasetVO();
        vo.setId(d.getId());
        vo.setName(d.getName());
        vo.setDescription(d.getDescription());
        vo.setProvider(d.getProvider());
        vo.setPermission(d.getPermission());
        vo.setIndexingTechnique(d.getIndexingTechnique());
        vo.setEmbeddingModel(d.getEmbeddingModel());
        vo.setEmbeddingModelProvider(d.getEmbeddingModelProvider());
        vo.setRetrievalModel(d.getRetrievalModel());
        vo.setChunkStructure(d.getChunkStructure());
        vo.setDocumentCount(d.getDocumentCount());
        vo.setWordCount(d.getWordCount());
        vo.setCreatedBy(d.getCreatedBy());
        vo.setCreateTime(d.getCreateTime());
        vo.setUpdateTime(d.getUpdateTime());
        if (rule != null) vo.setProcessRule(toProcessRuleVO(rule));
        return vo;
    }

    private DatasetDTO.DatasetListItem toDatasetListItem(Dataset d) {
        DatasetDTO.DatasetListItem item = new DatasetDTO.DatasetListItem();
        item.setId(d.getId());
        item.setName(d.getName());
        item.setDescription(d.getDescription());
        item.setIndexingTechnique(d.getIndexingTechnique());
        item.setEmbeddingModel(d.getEmbeddingModel());
        item.setChunkStructure(d.getChunkStructure());
        item.setDocumentCount(d.getDocumentCount());
        item.setWordCount(d.getWordCount());
        item.setSegmentCount(segmentRepository.countByDatasetId(d.getId()));
        item.setCreateTime(d.getCreateTime());
        item.setUpdateTime(d.getUpdateTime());
        return item;
    }

    private DatasetDTO.ProcessRuleVO toProcessRuleVO(DatasetProcessRule r) {
        DatasetDTO.ProcessRuleVO vo = new DatasetDTO.ProcessRuleVO();
        vo.setId(r.getId());
        vo.setDatasetId(r.getDatasetId());
        vo.setMode(r.getMode());
        vo.setRules(r.getRules());
        vo.setCreatedBy(r.getCreatedBy());
        vo.setCreateTime(r.getCreateTime());
        return vo;
    }

    private DatasetDTO.DocumentVO toDocumentVO(KbDocument doc, String datasetName) {
        DatasetDTO.DocumentVO vo = new DatasetDTO.DocumentVO();
        vo.setId(doc.getId());
        vo.setDatasetId(doc.getDatasetId());
        vo.setDatasetName(datasetName);
        vo.setPosition(doc.getPosition());
        vo.setName(doc.getName());
        vo.setDataSourceType(doc.getDataSourceType());
        vo.setDataSourceInfo(doc.getDataSourceInfo());
        vo.setIndexingStatus(doc.getIndexingStatus());
        vo.setDocForm(doc.getDocForm());
        vo.setDocType(doc.getDocType());
        vo.setDocMetadata(doc.getDocMetadata());
        vo.setWordCount(doc.getWordCount());
        vo.setTokens(doc.getTokens());
        vo.setSegmentCount(doc.getSegmentCount());
        vo.setBatch(doc.getBatch());
        vo.setProcessRuleId(doc.getProcessRuleId());
        vo.setEnabled(doc.getEnabled());
        vo.setArchived(doc.getArchived());
        vo.setHitCount(doc.getHitCount());
        vo.setErrorMessage(doc.getErrorMessage());
        vo.setIndexingStartedAt(doc.getIndexingStartedAt());
        vo.setParsingCompletedAt(doc.getParsingCompletedAt());
        vo.setCleaningCompletedAt(doc.getCleaningCompletedAt());
        vo.setSplittingCompletedAt(doc.getSplittingCompletedAt());
        vo.setCompletedAt(doc.getCompletedAt());
        vo.setCreateTime(doc.getCreateTime());
        vo.setUpdateTime(doc.getUpdateTime());
        return vo;
    }

    private DatasetDTO.DocumentListItem toDocumentListItem(KbDocument doc) {
        DatasetDTO.DocumentListItem item = new DatasetDTO.DocumentListItem();
        item.setId(doc.getId());
        item.setDatasetId(doc.getDatasetId());
        item.setName(doc.getName());
        item.setDataSourceType(doc.getDataSourceType());
        item.setIndexingStatus(doc.getIndexingStatus());
        item.setDocForm(doc.getDocForm());
        item.setWordCount(doc.getWordCount());
        item.setSegmentCount(doc.getSegmentCount());
        item.setEnabled(doc.getEnabled());
        item.setArchived(doc.getArchived());
        item.setHitCount(doc.getHitCount());
        item.setErrorMessage(doc.getErrorMessage());
        item.setCompletedAt(doc.getCompletedAt());
        item.setCreateTime(doc.getCreateTime());
        item.setUpdateTime(doc.getUpdateTime());
        return item;
    }

    private DatasetDTO.SegmentVO toSegmentVO(DocumentSegment s, String documentName, List<ChildChunk> childChunks) {
        DatasetDTO.SegmentVO vo = new DatasetDTO.SegmentVO();
        vo.setId(s.getId());
        vo.setDatasetId(s.getDatasetId());
        vo.setDocumentId(s.getDocumentId());
        vo.setDocumentName(documentName);
        vo.setPosition(s.getPosition());
        vo.setContent(s.getContent());
        vo.setAnswer(s.getAnswer());
        vo.setWordCount(s.getWordCount());
        vo.setTokens(s.getTokens());
        vo.setKeywords(s.getKeywords());
        vo.setIndexNodeId(s.getIndexNodeId());
        vo.setHitCount(s.getHitCount());
        vo.setEnabled(s.getEnabled());
        vo.setStatus(s.getStatus());
        vo.setErrorMessage(s.getErrorMessage());
        vo.setCreateTime(s.getCreateTime());
        vo.setUpdateTime(s.getUpdateTime());
        if (childChunks != null && !childChunks.isEmpty()) {
            vo.setChildChunks(childChunks.stream().map(this::toChildChunkVO).collect(Collectors.toList()));
        }
        return vo;
    }

    private DatasetDTO.SegmentListItem toSegmentListItem(DocumentSegment s, String documentName) {
        DatasetDTO.SegmentListItem item = new DatasetDTO.SegmentListItem();
        item.setId(s.getId());
        item.setDocumentId(s.getDocumentId());
        item.setDocumentName(documentName);
        item.setPosition(s.getPosition());
        item.setContent(s.getContent());
        item.setWordCount(s.getWordCount());
        item.setHitCount(s.getHitCount());
        item.setEnabled(s.getEnabled());
        item.setStatus(s.getStatus());
        item.setCreateTime(s.getCreateTime());
        return item;
    }

    private DatasetDTO.ChildChunkVO toChildChunkVO(ChildChunk c) {
        DatasetDTO.ChildChunkVO vo = new DatasetDTO.ChildChunkVO();
        vo.setId(c.getId());
        vo.setSegmentId(c.getSegmentId());
        vo.setDocumentId(c.getDocumentId());
        vo.setDatasetId(c.getDatasetId());
        vo.setPosition(c.getPosition());
        vo.setContent(c.getContent());
        vo.setWordCount(c.getWordCount());
        vo.setIndexNodeId(c.getIndexNodeId());
        vo.setType(c.getType());
        vo.setCreateTime(c.getCreateTime());
        return vo;
    }
}
