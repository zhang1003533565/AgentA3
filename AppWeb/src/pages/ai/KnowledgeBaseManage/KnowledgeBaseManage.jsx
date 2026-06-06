import { useEffect, useState } from 'react'
import { Alert, Button, Card, Col, Collapse, Empty, Form, Input, Row, Select, Space, Statistic, Table, Tag, Typography, Upload, message } from 'antd'
import { ApiOutlined, BranchesOutlined, DatabaseOutlined, DownloadOutlined, FileTextOutlined, ReloadOutlined, UploadOutlined } from '@ant-design/icons'
import ReactMarkdown from 'react-markdown'
import {
  convertPdf,
  convertPpt,
  getRagDocuments,
  getRagEmbeddingHealth,
  getRagGraphStoreHealth,
  getRagVectorStoreHealth,
  ingestRagDocuments,
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

const isPptxFile = (file) => /\.pptx$/i.test(file?.name || file?.originFileObj?.name || '')

const readFileAsBase64 = (file) => new Promise((resolve, reject) => {
  const reader = new FileReader()
  reader.onload = () => {
    const result = String(reader.result || '')
    resolve(result.includes(',') ? result.split(',').pop() : result)
  }
  reader.onerror = reject
  reader.readAsDataURL(file)
})

const MarkdownPreview = ({ source, assets = [] }) => {
  const assetPreviewUrls = new Map(
    assets
      .filter((asset) => asset.path && asset.previewDataUrl)
      .map((asset) => [asset.path, asset.previewDataUrl]),
  )

  return (
    <article className="rag-markdown-preview">
      <ReactMarkdown
        components={{
          img: ({ src = '', alt = '' }) => {
            const previewSrc = assetPreviewUrls.get(src)
            return previewSrc ? (
              <span className="rag-markdown-image">
                <img src={previewSrc} alt={alt || 'PDF 提取图片'} />
                <span className="rag-markdown-image__caption">{alt || src}</span>
              </span>
            ) : (
              <span className="rag-markdown-missing-image">图片资源：{alt || src}</span>
            )
          },
        }}
      >
        {source || '暂无预览'}
      </ReactMarkdown>
    </article>
  )
}

function KnowledgeBaseManage() {
  const [bootLoading, setBootLoading] = useState(false)
  const [actionLoading, setActionLoading] = useState(false)
  const [documents, setDocuments] = useState([])
  const [health, setHealth] = useState({})
  const [uploadFileList, setUploadFileList] = useState([])
  const [convertFileList, setConvertFileList] = useState([])
  const [convertResult, setConvertResult] = useState(null)
  const [convertLoading, setConvertLoading] = useState(false)
  const [ingestForm] = Form.useForm()
  const [convertForm] = Form.useForm()

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
      if (!selectedFile && !textContent.trim()) {
        message.warning('请粘贴文档内容，或选择一个本地文件')
        return
      }
      const contentBase64 = selectedFile ? await readFileAsBase64(selectedFile) : undefined
      const res = await ingestRagDocuments({
        documents: [{
          source: values.source || selectedFile?.name || '后台录入.md',
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

  const handleDocumentConvert = async (values) => {
    const selectedFile = convertFileList[0]?.originFileObj || convertFileList[0]
    if (!selectedFile) {
      message.warning('请先选择一个 PDF 或 PPTX 文件')
      return
    }
    const isPptx = isPptxFile(selectedFile)
    if (isPptx && values.targetFormat !== 'docx') {
      message.warning('PPTX 当前仅支持转换为 DOCX')
      return
    }
    setConvertLoading(true)
    setConvertResult(null)
    try {
      const formData = new FormData()
      formData.append('file', selectedFile)
      if (!isPptx) {
        formData.append('targetFormat', values.targetFormat)
      }
      const res = isPptx ? await convertPpt(formData) : await convertPdf(formData)
      setConvertResult(res.data)
      message.success(isPptx ? 'PPTX 转 DOCX 完成' : 'PDF 转换完成')
    } catch (error) {
      message.error(error.message || '文档转换失败')
    } finally {
      setConvertLoading(false)
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
                  <Input placeholder="例如：校园卡服务.md" />
                </Form.Item>
                <Form.Item label="本地文件">
                  <Upload
                    beforeUpload={(file) => {
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
                  <Text type="secondary">支持 Markdown、TXT、CSV、TSV、JSON、HTML、PDF 和图片；文件会解析、切分并写入 Docker Milvus 向量库。</Text>
                </Form.Item>
                <Form.Item name="content" label="文档内容">
                  <TextArea rows={10} placeholder="粘贴 Markdown、文本、表格摘要等知识内容" />
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
            <Card title="文档转换" className="rag-panel-card">
              <Form
                form={convertForm}
                layout="vertical"
                initialValues={{ targetFormat: 'md' }}
                onFinish={handleDocumentConvert}
              >
                <Alert
                  className="rag-inline-alert"
                  type="info"
                  showIcon
                  message="支持 PDF 转 Markdown/DOCX，PPTX 转 DOCX；PPTX 会按幻灯片顺序重排内容并保留图片。"
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
                      if (lowerName.endsWith('.pptx')) {
                        convertForm.setFieldsValue({ targetFormat: 'docx' })
                      }
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
                </Form.Item>
                <Form.Item name="targetFormat" label="输出格式" rules={[{ required: true, message: '请选择输出格式' }]}>
                  <Select
                    options={[
                      { value: 'md', label: 'Markdown（zip，含图片 assets）' },
                      { value: 'docx', label: 'DOCX（尽量保留图片与基础排版）' },
                    ]}
                  />
                </Form.Item>
                <Button type="primary" htmlType="submit" icon={<FileTextOutlined />} loading={convertLoading} block>
                  开始转换
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
                  {convertResult.outputType === 'markdown' ? (
                    <Collapse
                      defaultActiveKey={['preview']}
                      items={[
                        {
                          key: 'preview',
                          label: 'Markdown 预览',
                          children: <MarkdownPreview source={convertResult.preview} assets={convertResult.assets} />,
                        },
                        {
                          key: 'assets',
                          label: '图片资源',
                          children: (
                            <Table
                              rowKey="path"
                              columns={[
                                { title: '文件', dataIndex: 'name' },
                                { title: '页码', dataIndex: 'page', width: 100 },
                                { title: '路径', dataIndex: 'path' },
                                { title: '大小', dataIndex: 'size', width: 120, render: (value) => `${Math.ceil(Number(value || 0) / 1024)} KB` },
                              ]}
                              dataSource={convertResult.assets || []}
                              pagination={{ pageSize: 5 }}
                              size="small"
                            />
                          ),
                        },
                      ]}
                    />
                  ) : (
                    <Alert type="success" showIcon message="DOCX 已生成，点击上方按钮下载。" />
                  )}
                </Space>
              ) : (
                <Empty description="上传 PDF 或 PPTX 后，转换结果会显示在这里" />
              )}
            </Card>
          </Col>
        </Row>
      </Space>
    </div>
  )
}

export default KnowledgeBaseManage
