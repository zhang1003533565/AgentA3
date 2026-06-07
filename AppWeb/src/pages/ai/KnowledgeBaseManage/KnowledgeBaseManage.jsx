import { useEffect, useState } from 'react'
import { Alert, Button, Card, Col, Collapse, Empty, Form, Input, Row, Select, Space, Statistic, Table, Tag, Typography, Upload, message } from 'antd'
import { ApiOutlined, BranchesOutlined, DatabaseOutlined, DownloadOutlined, FileTextOutlined, ReloadOutlined, UploadOutlined } from '@ant-design/icons'
import {
  convertPdf,
  convertPpt,
  getRagDocuments,
  getRagEmbeddingHealth,
  getRagGraphStoreHealth,
  getRagVectorStoreHealth,
  ingestRagDocuments,
  runRagRecallTest,
} from '../../../api/rag'
import '../RagManage/RagManage.css'
import './KnowledgeBaseManage.css'

const { TextArea } = Input
const { Text, Title } = Typography

const documentColumns = [
  { title: '来源', dataIndex: 'source', ellipsis: true },
  { title: 'Chunk', dataIndex: 'chunkCount', width: 100, render: (value) => value ?? '-' },
  { title: '向量库', dataIndex: 'collection', width: 220, ellipsis: true },
  { title: '大小', dataIndex: 'size', width: 120, render: (value) => (Number.isFinite(Number(value)) ? `${Math.ceil(Number(value) / 1024)} KB` : '-') },
  {
    title: '更新时间',
    dataIndex: 'updatedAt',
    width: 180,
    render: (value) => (value ? new Date(Number(value) * 1000).toLocaleString() : '-'),
  },
]

const recallColumns = [
  { title: '来源', dataIndex: 'source', width: 220, ellipsis: true },
  { title: '分数', dataIndex: 'score', width: 110, render: (value) => (value === null || value === undefined ? '-' : Number(value).toFixed(4)) },
  { title: '内容', dataIndex: 'content', ellipsis: true },
]

const recallStrategyOptions = [
  { value: 'hybrid_search', label: '混合检索 hybrid_search' },
  { value: 'parent_child', label: '父子切片 parent_child' },
  { value: 'reranking', label: '重排序 reranking' },
  { value: 'naive_rag', label: '基础检索 naive_rag' },
  { value: 'multi_query_rag', label: '多查询 multi_query_rag' },
]

const isSupportedKnowledgeFile = (file) => /\.(docx|txt)$/i.test(file?.name || file?.originFileObj?.name || '')
const isPptxFile = (file) => /\.pptx$/i.test(file?.name || file?.originFileObj?.name || '')
const isPdfFile = (file) => /\.pdf$/i.test(file?.name || file?.originFileObj?.name || '')

const readFileAsBase64 = (file) => new Promise((resolve, reject) => {
  const reader = new FileReader()
  reader.onload = () => {
    const result = String(reader.result || '')
    resolve(result.includes(',') ? result.split(',').pop() : result)
  }
  reader.onerror = reject
  reader.readAsDataURL(file)
})

function KnowledgeBaseManage() {
  const [bootLoading, setBootLoading] = useState(false)
  const [actionLoading, setActionLoading] = useState(false)
  const [documents, setDocuments] = useState([])
  const [health, setHealth] = useState({})
  const [uploadFileList, setUploadFileList] = useState([])
  const [convertFileList, setConvertFileList] = useState([])
  const [convertResult, setConvertResult] = useState(null)
  const [convertLoading, setConvertLoading] = useState(false)
  const [recallLoading, setRecallLoading] = useState(false)
  const [recallResult, setRecallResult] = useState(null)
  const [recallError, setRecallError] = useState('')
  const [ingestForm] = Form.useForm()
  const [convertForm] = Form.useForm()
  const [recallForm] = Form.useForm()

  const refresh = async () => {
    setBootLoading(true)
    try {
      const [documentRes, vectorHealthRes, embeddingHealthRes, graphHealthRes] = await Promise.all([
        getRagDocuments(),
        getRagVectorStoreHealth(),
        getRagEmbeddingHealth(),
        getRagGraphStoreHealth(),
      ])
      setDocuments(documentRes.data?.documents || [])
      setHealth({
        vector: vectorHealthRes.data,
        embedding: embeddingHealthRes.data,
        graph: graphHealthRes.data,
      })
    } catch (error) {
      message.error(error.message || '加载知识库数据失败')
    } finally {
      setBootLoading(false)
    }
  }

  useEffect(() => {
    refresh()
  }, [])

  const handleIngest = async (values) => {
    setActionLoading(true)
    try {
      const selectedFile = uploadFileList[0]?.originFileObj || uploadFileList[0]
      const textContent = values.content || ''
      const sourceName = values.source || selectedFile?.name || '后台录入.txt'
      if (!selectedFile && !textContent.trim()) {
        message.warning('请粘贴文档内容，或选择一个本地文件')
        return
      }
      if (selectedFile && !isSupportedKnowledgeFile(selectedFile)) {
        message.warning('知识库暂时只支持上传 DOCX 或 TXT 文件')
        return
      }
      if (!selectedFile && sourceName && !isSupportedKnowledgeFile({ name: sourceName })) {
        message.warning('来源文件名请使用 .txt 或 .docx 后缀')
        return
      }
      const contentBase64 = selectedFile ? await readFileAsBase64(selectedFile) : undefined
      const res = await ingestRagDocuments({
        documents: [{
          source: sourceName,
          content: textContent,
          contentBase64,
          metadata: {
            origin: 'knowledge_base_console',
            uploadMode: selectedFile ? 'file_base64' : 'text',
          },
        }],
      })
      message.success(`已入库 ${res.data?.storedCount || 0} 个文档`)
      ingestForm.resetFields()
      setUploadFileList([])
      await refresh()
    } catch (error) {
      message.error(error.message || '知识入库失败')
    } finally {
      setActionLoading(false)
    }
  }

  const handleDocumentConvert = async () => {
    const selectedFile = convertFileList[0]?.originFileObj || convertFileList[0]
    if (!selectedFile) {
      message.warning('请先选择一个 PDF 或 PPTX 文件')
      return
    }
    const isPptx = isPptxFile(selectedFile)
    const isPdf = isPdfFile(selectedFile)
    if (!isPptx && !isPdf) {
      message.warning('请选择 PDF 或 PPTX 文件')
      return
    }
    setConvertLoading(true)
    setConvertResult(null)
    try {
      const formData = new FormData()
      formData.append('file', selectedFile)
      if (!isPptx) {
        formData.append('targetFormat', 'docx')
      }
      const res = isPptx ? await convertPpt(formData) : await convertPdf(formData)
      setConvertResult(res.data)
      message.success(isPptx ? 'PPTX 转 DOCX 完成' : 'PDF 转 DOCX 完成')
    } catch (error) {
      message.error(error.message || '文档转换失败')
    } finally {
      setConvertLoading(false)
    }
  }

  const handleRecallTest = async (values) => {
    setRecallLoading(true)
    setRecallError('')
    setRecallResult(null)
    try {
      const res = await runRagRecallTest({
        query: values.query,
        keyword: values.keyword || undefined,
        intent: values.intent || 'campus_search',
        ragStrategy: values.ragStrategy || 'hybrid_search',
        metadata: {},
      })
      setRecallResult(res.data)
      message.success(`召回完成，命中 ${(res.data?.documents || []).length} 条`)
    } catch (error) {
      const errorMessage = error.message || '召回测试失败'
      setRecallError(errorMessage)
      message.error(errorMessage)
    } finally {
      setRecallLoading(false)
    }
  }

  const downloadConvertedFile = (result) => {
    if (!result?.contentBase64) {
      message.warning('转换结果没有可下载内容')
      return
    }
    const binary = window.atob(result.contentBase64)
    const bytes = new Uint8Array(binary.length)
    for (let index = 0; index < binary.length; index += 1) {
      bytes[index] = binary.charCodeAt(index)
    }
    const blob = new Blob([bytes], { type: result.mimeType || 'application/octet-stream' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = result.fileName || 'knowledge-base-convert-result'
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
  }

  const renderHealthCard = (key, title, icon) => {
    const data = health[key] || {}
    const healthy = data.status === 'implemented' || data.configured
    return (
      <Card className="rag-health-card">
        <div className="rag-health-card__top">
          <span className="rag-health-card__icon">{icon}</span>
          <Tag color={healthy ? 'green' : 'orange'}>{data.status || '-'}</Tag>
        </div>
        <Statistic title={title} value={data.backend || data.provider || '-'} />
        <Text type="secondary">configured: {String(data.configured ?? true)}</Text>
      </Card>
    )
  }

  return (
    <div className="rag-manage knowledge-base-manage">
      <section className="rag-hero knowledge-base-hero">
        <div>
          <span className="rag-kicker">Knowledge Base Console</span>
          <Title level={1}>知识库管理</Title>
          <p>使用 Docker Milvus 管理 RAG 知识文档、向量库状态和文档转换，本地文件索引不再作为默认知识库。</p>
        </div>
        <Button icon={<ReloadOutlined />} onClick={refresh} loading={bootLoading}>
          刷新知识库
        </Button>
      </section>

      <Space direction="vertical" size="large" className="rag-full">
        <Row gutter={[20, 20]}>
          <Col xs={24} md={8}>{renderHealthCard('vector', '向量库', <DatabaseOutlined />)}</Col>
          <Col xs={24} md={8}>{renderHealthCard('embedding', 'Embedding', <ApiOutlined />)}</Col>
          <Col xs={24} md={8}>{renderHealthCard('graph', '图谱存储', <BranchesOutlined />)}</Col>
        </Row>

        <Row gutter={[20, 20]}>
          <Col xs={24} lg={9}>
            <Card title="新增知识文档" className="rag-panel-card">
              <Form form={ingestForm} layout="vertical" onFinish={handleIngest}>
                <Form.Item name="source" label="来源文件名">
                  <Input placeholder="例如：校园卡服务.docx" />
                </Form.Item>
                <Form.Item label="本地文件">
                  <Upload
                    accept=".docx,.txt,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain"
                    beforeUpload={(file) => {
                      if (!isSupportedKnowledgeFile(file)) {
                        message.warning('知识库暂时只支持上传 DOCX 或 TXT 文件')
                        return Upload.LIST_IGNORE
                      }
                      setUploadFileList([file])
                      ingestForm.setFieldsValue({ source: file.name })
                      return false
                    }}
                    fileList={uploadFileList}
                    maxCount={1}
                    onRemove={() => {
                      setUploadFileList([])
                      return true
                    }}
                  >
                    <Button icon={<UploadOutlined />}>选择文件</Button>
                  </Upload>
                  <Text type="secondary">暂时仅支持 DOCX、TXT；文件会解析、切分并写入 Docker Milvus 向量库。</Text>
                </Form.Item>
                <Form.Item name="content" label="文档内容">
                  <TextArea rows={10} placeholder="粘贴 TXT 文本内容，或选择 DOCX / TXT 文件" />
                </Form.Item>
                <Button type="primary" htmlType="submit" icon={<FileTextOutlined />} loading={actionLoading} block>
                  入库并索引
                </Button>
              </Form>
            </Card>
          </Col>
          <Col xs={24} lg={15}>
            <Card title="Milvus 已入库文档" extra={<Button icon={<ReloadOutlined />} onClick={refresh} loading={bootLoading}>刷新</Button>} className="rag-panel-card">
              <Table
                rowKey={(record) => record.source}
                columns={documentColumns}
                dataSource={documents}
                pagination={{ pageSize: 8 }}
              />
            </Card>
          </Col>
        </Row>

        <Row gutter={[20, 20]}>
          <Col xs={24} lg={9}>
            <Card title="召回测试" className="rag-panel-card">
              <Form
                form={recallForm}
                layout="vertical"
                initialValues={{ ragStrategy: 'hybrid_search', intent: 'campus_search' }}
                onFinish={handleRecallTest}
              >
                <Alert
                  className="rag-inline-alert"
                  type="info"
                  showIcon
                  message="只测试知识库召回，不调用模型生成回答。"
                />
                <Form.Item name="query" label="测试问题" rules={[{ required: true, message: '请输入要测试召回的问题' }]}>
                  <TextArea rows={4} placeholder="例如：深度学习中的反向传播是什么？" />
                </Form.Item>
                <Form.Item name="keyword" label="关键词">
                  <Input placeholder="可选；用于增强关键词召回" />
                </Form.Item>
                <Form.Item name="ragStrategy" label="召回策略">
                  <Select options={recallStrategyOptions} />
                </Form.Item>
                <Form.Item name="intent" label="意图标识">
                  <Input placeholder="campus_search" />
                </Form.Item>
                <Button type="primary" htmlType="submit" icon={<ReloadOutlined />} loading={recallLoading} block>
                  开始召回测试
                </Button>
              </Form>
            </Card>
          </Col>
          <Col xs={24} lg={15}>
            <Card title="召回结果" className="rag-panel-card">
              {recallError ? (
                <Alert type="error" showIcon message="召回测试失败" description={recallError} />
              ) : recallResult ? (
                <Space direction="vertical" size="large" className="rag-full">
                  <div className="rag-agent-test-status">
                    <Tag color="green">召回完成</Tag>
                    <Tag color="cyan">{recallResult.metadata?.strategyLabel || recallResult.strategy}</Tag>
                    <Tag color="blue">命中：{(recallResult.documents || []).length}</Tag>
                    {recallResult.metadata?.backend ? <Tag>后端：{recallResult.metadata.backend}</Tag> : null}
                  </div>
                  <Table
                    rowKey={(record) => record.id || `${record.source}-${record.content}`}
                    columns={recallColumns}
                    dataSource={recallResult.documents || []}
                    pagination={{ pageSize: 6 }}
                  />
                  <Collapse
                    items={[
                      {
                        key: 'metadata',
                        label: 'Trace / Metadata',
                        children: <pre className="rag-code-block">{JSON.stringify({ trace: recallResult.trace, metadata: recallResult.metadata }, null, 2)}</pre>,
                      },
                    ]}
                  />
                </Space>
              ) : (
                <Empty description="输入测试问题后，召回命中的知识片段会显示在这里" />
              )}
            </Card>
          </Col>
        </Row>

        <Row gutter={[20, 20]}>
          <Col xs={24} lg={9}>
            <Card title="文档转换" className="rag-panel-card">
              <Form
                form={convertForm}
                layout="vertical"
                onFinish={handleDocumentConvert}
              >
                <Alert
                  className="rag-inline-alert"
                  type="info"
                  showIcon
                  message="支持 PDF 转 DOCX、PPTX 转 DOCX；PDF 仅处理原生可提取文字，PPTX 会按幻灯片顺序重排内容并保留图片。"
                />
                <Form.Item label="文件" required>
                  <Upload
                    accept="application/pdf,.pdf,application/vnd.openxmlformats-officedocument.presentationml.presentation,.pptx"
                    beforeUpload={(file) => {
                      const lowerName = file.name.toLowerCase()
                      if (!lowerName.endsWith('.pdf') && !lowerName.endsWith('.pptx')) {
                        message.warning('请选择 PDF 或 PPTX 文件')
                        return Upload.LIST_IGNORE
                      }
                      setConvertFileList([file])
                      setConvertResult(null)
                      return false
                    }}
                    fileList={convertFileList}
                    maxCount={1}
                    onRemove={() => {
                      setConvertFileList([])
                      setConvertResult(null)
                      return true
                    }}
                  >
                    <Button icon={<UploadOutlined />}>选择文件</Button>
                  </Upload>
                  <Text type="secondary">支持 .pdf 和 .pptx，输出统一为可下载的 DOCX。</Text>
                </Form.Item>
                <Button type="primary" htmlType="submit" icon={<FileTextOutlined />} loading={convertLoading} block>
                  转换为 DOCX
                </Button>
              </Form>
            </Card>
          </Col>
          <Col xs={24} lg={15}>
            <Card title="转换结果" className="rag-panel-card">
              {convertResult ? (
                <Space direction="vertical" size="large" className="rag-full">
                  <div className="rag-agent-test-status">
                    <Tag color="green">转换完成</Tag>
                    <Tag color="blue">{convertResult.format}</Tag>
                    <Tag color="cyan">{convertResult.downloadType}</Tag>
                    {Number.isFinite(convertResult.slideCount) ? <Tag color="purple">页数：{convertResult.slideCount}</Tag> : null}
                    {Number.isFinite(convertResult.imageCount) ? <Tag color="geekblue">图片：{convertResult.imageCount}</Tag> : null}
                  </div>
                  <Space wrap>
                    <Text strong>{convertResult.fileName}</Text>
                    <Text type="secondary">{convertResult.contentLength ? `${Math.ceil(convertResult.contentLength / 1024)} KB` : ''}</Text>
                    <Button type="primary" icon={<DownloadOutlined />} onClick={() => downloadConvertedFile(convertResult)}>
                      下载结果
                    </Button>
                  </Space>
                  <Alert type="success" showIcon message="DOCX 已生成，点击上方按钮下载。" />
                </Space>
              ) : (
                <Empty description="上传 PDF 或 PPTX 后，DOCX 转换结果会显示在这里" />
              )}
            </Card>
          </Col>
        </Row>
      </Space>
    </div>
  )
}

export default KnowledgeBaseManage
