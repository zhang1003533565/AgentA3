/* eslint-disable react-hooks/exhaustive-deps */
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import {
  Avatar,
  Button,
  Checkbox,
  Dropdown,
  Empty,
  Input,
  Select,
  Space,
  Spin,
  Switch,
  Typography,
  message,
} from 'antd'
import {
  ArrowLeftOutlined,
  BookOutlined,
  CheckOutlined,
  CommentOutlined,
  DeleteOutlined,
  EditOutlined,
  HolderOutlined,
  PlusCircleOutlined,
  PlusOutlined,
  SearchOutlined,
  SettingOutlined,
  ThunderboltOutlined,
  UserOutlined,
  AppstoreOutlined,
  MoreOutlined,
} from '@ant-design/icons'
import {
  fetchMaxKbAsset,
  getMaxKbAccounts,
  getMaxKbKnowledgeDetail,
  getMaxKbParagraphs,
  runMaxKbHitTest,
} from '../../../api/maxkbKnowledge'
import './ParagraphManage.css'

const { Text, Title } = Typography

const unwrapMaxKbPayload = (response) => {
  let payload = response?.data ?? response
  if (payload && typeof payload === 'object' && 'data' in payload && ('code' in payload || 'msg' in payload || 'message' in payload)) {
    payload = payload.data
  }
  return payload
}

const normalizePage = (response) => {
  const payload = unwrapMaxKbPayload(response)
  if (Array.isArray(payload)) {
    return { records: payload, total: payload.length, page: 1, size: payload.length || 30 }
  }
  const records = payload?.records || payload?.list || payload?.items || payload?.rows || []
  return {
    records: Array.isArray(records) ? records : [],
    total: Number(payload?.total ?? payload?.count ?? records.length ?? 0),
    page: Number(payload?.page ?? payload?.current_page ?? payload?.current ?? 1),
    size: Number(payload?.size ?? payload?.page_size ?? payload?.pageSize ?? 30),
  }
}

const textValue = (...values) => {
  const value = values.find((item) => item !== undefined && item !== null && item !== '')
  return value === undefined ? '-' : String(value)
}

const recordKey = (record) => record?.id || record?.paragraph_id || JSON.stringify(record).slice(0, 80)

const getParagraphTitle = (record, index) => (
  textValue(record.title, record.name, `未命名分段 ${index + 1}`)
)

function MaxKbMarkdownImage({ accountId, src, alt }) {
  const [objectUrl, setObjectUrl] = useState('')
  const [failed, setFailed] = useState(false)
  const shouldProxy = src && (
    src.startsWith('/oss/file/')
    || src.startsWith('/.oss/file/')
    || src.startsWith('./oss/file/')
    || src.startsWith('./.oss/file/')
    || src.includes('/oss/file/')
    || src.includes('/.oss/file/')
  )

  useEffect(() => {
    let active = true
    let nextObjectUrl = ''
    setFailed(false)
    setObjectUrl('')

    if (!src) {
      setFailed(true)
      return undefined
    }
    if (!shouldProxy) {
      setObjectUrl(src)
      return undefined
    }
    if (!accountId) {
      setFailed(true)
      return undefined
    }

    fetchMaxKbAsset(accountId, src)
      .then((blob) => {
        if (!active) return
        nextObjectUrl = URL.createObjectURL(blob)
        setObjectUrl(nextObjectUrl)
      })
      .catch(() => {
        if (active) setFailed(true)
      })

    return () => {
      active = false
      if (nextObjectUrl) {
        URL.revokeObjectURL(nextObjectUrl)
      }
    }
  }, [accountId, shouldProxy, src])

  if (failed) {
    return <span className="maxkb-image-fallback">{alt || 'image'}</span>
  }

  return objectUrl ? <img src={objectUrl} alt={alt || ''} className="maxkb-markdown-image" /> : <span className="maxkb-image-loading">{alt || 'image'}</span>
}

const imagePattern = /!\[([^\]]*)\]\(([^)]+)\)/g

const splitTableCells = (line) => {
  let value = line.trim()
  if (value.startsWith('|')) value = value.slice(1)
  if (value.endsWith('|')) value = value.slice(0, -1)
  return value.split('|').map((cell) => cell.trim())
}

const isTableDivider = (line) => /^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$/.test(line)

const isTableLine = (line) => line.includes('|') && splitTableCells(line).length > 1

const normalizeMarkdownLine = (line) => {
  const trimmed = line.trim()
  if (/^#{1,6}\s+/.test(trimmed)) {
    return trimmed.replace(/^#{1,6}\s+/, '')
  }
  if (/^[-*+]\s+/.test(trimmed)) {
    return trimmed.replace(/^[-*+]\s+/, '')
  }
  return line
}

function InlineMarkdown({ accountId, text }) {
  const nodes = []
  let lastIndex = 0
  String(text || '').replace(imagePattern, (match, alt, src, offset) => {
    if (offset > lastIndex) {
      nodes.push(String(text).slice(lastIndex, offset))
    }
    nodes.push(<MaxKbMarkdownImage key={`${src}-${offset}`} accountId={accountId} src={src} alt={alt} />)
    lastIndex = offset + match.length
    return match
  })
  if (lastIndex < String(text || '').length) {
    nodes.push(String(text).slice(lastIndex))
  }
  return nodes.map((node, index) => {
    if (typeof node !== 'string') return node
    const parts = node.split(/(\*\*[^*]+\*\*)/g).filter(Boolean)
    return parts.map((part, partIndex) => (
      part.startsWith('**') && part.endsWith('**')
        ? <strong key={`${index}-${partIndex}`}>{part.slice(2, -2)}</strong>
        : <span key={`${index}-${partIndex}`}>{part}</span>
    ))
  })
}

function MaxKbMarkdownContent({ accountId, content }) {
  const lines = String(content || '').replace(/\r\n/g, '\n').split('\n')
  const blocks = []
  let index = 0

  while (index < lines.length) {
    const line = lines[index]
    if (!line.trim()) {
      index += 1
      continue
    }

    if (isTableLine(line) && isTableDivider(lines[index + 1] || '')) {
      const header = splitTableCells(line)
      const rows = []
      index += 2
      while (index < lines.length && isTableLine(lines[index])) {
        rows.push(splitTableCells(lines[index]))
        index += 1
      }
      blocks.push({ type: 'table', header, rows })
      continue
    }

    const textLines = []
    while (
      index < lines.length
      && lines[index].trim()
      && !(isTableLine(lines[index]) && isTableDivider(lines[index + 1] || ''))
    ) {
      textLines.push(lines[index])
      index += 1
    }
    blocks.push({ type: 'lines', lines: textLines })
  }

  return (
    <>
      {blocks.map((block, blockIndex) => {
        if (block.type === 'table') {
          return (
            <table className="maxkb-rendered-table" key={`table-${blockIndex}`}>
              <thead>
                <tr>
                  {block.header.map((cell, cellIndex) => (
                    <th key={cellIndex}><InlineMarkdown accountId={accountId} text={cell} /></th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {block.rows.map((row, rowIndex) => (
                  <tr key={rowIndex}>
                    {block.header.map((_, cellIndex) => (
                      <td key={cellIndex}><InlineMarkdown accountId={accountId} text={row[cellIndex] || ''} /></td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          )
        }

        return (
          <div className="maxkb-rendered-lines" key={`lines-${blockIndex}`}>
            {block.lines.map((text, lineIndex) => {
              const normalized = normalizeMarkdownLine(text)
              return (
                <p key={lineIndex} className={/^#{1,6}\s+/.test(text.trim()) ? 'is-heading' : ''}>
                  <InlineMarkdown accountId={accountId} text={normalized} />
                </p>
              )
            })}
          </div>
        )
      })}
    </>
  )
}

function ParagraphManage() {
  const navigate = useNavigate()
  const { knowledgeId, documentId } = useParams()
  const [searchParams] = useSearchParams()
  const [accountId, setAccountId] = useState(searchParams.get('accountId'))
  const [documentName, setDocumentName] = useState(searchParams.get('documentName') || '')
  const [knowledgeName, setKnowledgeName] = useState(searchParams.get('knowledgeName') || '')
  const [paragraphRows, setParagraphRows] = useState([])
  const [loading, setLoading] = useState(false)
  const [total, setTotal] = useState(0)
  const [searchType, setSearchType] = useState('title')
  const [search, setSearch] = useState('')
  const [batchMode, setBatchMode] = useState(false)
  const [selectedKeys, setSelectedKeys] = useState([])
  const [hitLoading, setHitLoading] = useState(false)

  const selectedKeySet = useMemo(() => new Set(selectedKeys), [selectedKeys])
  const checkedAll = paragraphRows.length > 0 && selectedKeys.length === paragraphRows.length
  const indeterminate = selectedKeys.length > 0 && selectedKeys.length < paragraphRows.length

  const unsupportedAction = (label) => {
    message.info(`${label} 需要 MaxKB 对应写入接口开放后才能执行`)
  }

  const ensureAccountId = useCallback(async () => {
    if (accountId) return accountId
    const res = await getMaxKbAccounts({ current: 1, size: 20, status: 1 })
    const records = res.data?.records || []
    const nextAccountId = records.find((item) => item.status === 1)?.id || records[0]?.id
    if (!nextAccountId) {
      message.warning('请先在系统管理中配置 MaxKB 账号')
      return null
    }
    setAccountId(String(nextAccountId))
    return String(nextAccountId)
  }, [accountId])

  const fetchKnowledgeName = useCallback(async (nextAccountId) => {
    if (!nextAccountId || !knowledgeId || knowledgeName) return
    try {
      const res = await getMaxKbKnowledgeDetail(nextAccountId, knowledgeId)
      const payload = unwrapMaxKbPayload(res.data)
      setKnowledgeName(textValue(payload?.name, payload?.knowledge_name, payload?.title, '知识库'))
    } catch {
      setKnowledgeName('知识库')
    }
  }, [knowledgeId, knowledgeName])

  const fetchParagraphs = useCallback(async (params = {}) => {
    const nextAccountId = await ensureAccountId()
    if (!nextAccountId || !knowledgeId || !documentId) return
    setLoading(true)
    try {
      const keyword = params.search ?? search
      const res = await getMaxKbParagraphs(nextAccountId, knowledgeId, documentId, {
        page: 1,
        page_size: 200,
        [searchType]: keyword || undefined,
      })
      const data = normalizePage(res.data)
      setParagraphRows(data.records)
      setTotal(data.total)
      setSelectedKeys([])
      if (!documentName && data.records[0]?.document_name) {
        setDocumentName(data.records[0].document_name)
      }
      fetchKnowledgeName(nextAccountId)
    } catch (error) {
      message.error(error.message || '分段加载失败')
    } finally {
      setLoading(false)
    }
  }, [documentId, documentName, ensureAccountId, fetchKnowledgeName, knowledgeId, search, searchType])

  useEffect(() => {
    fetchParagraphs()
  }, [])

  const runQuickHitTest = async () => {
    const nextAccountId = await ensureAccountId()
    const firstContent = paragraphRows[0]?.content || paragraphRows[0]?.text
    if (!nextAccountId || !knowledgeId || !firstContent) {
      message.warning('暂无可用于召回测试的分段内容')
      return
    }
    setHitLoading(true)
    try {
      await runMaxKbHitTest(nextAccountId, {
        knowledge_id: knowledgeId,
        query_text: String(firstContent).slice(0, 80),
        top_number: 5,
        similarity: 0.6,
        search_mode: 'blend',
      })
      message.success('召回测试已发送')
    } catch (error) {
      message.error(error.message || '召回测试失败')
    } finally {
      setHitLoading(false)
    }
  }

  const toggleSelect = (key) => {
    setSelectedKeys((prev) => (
      prev.includes(key) ? prev.filter((item) => item !== key) : [...prev, key]
    ))
  }

  const handleCheckAll = (event) => {
    setSelectedKeys(event.target.checked ? paragraphRows.map(recordKey) : [])
  }

  const scrollToParagraph = (key) => {
    document.getElementById(`paragraph-${key}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  const backToDocuments = () => {
    navigate('/ai/knowledge')
  }

  return (
    <div className="maxkb-paragraph-page">
      <header className="maxkb-paragraph-topbar">
        <div className="maxkb-paragraph-brand">
          <div className="maxkb-paragraph-brand-mark">Z</div>
          <strong>流光知识库</strong>
        </div>
        <nav className="maxkb-paragraph-topnav">
          <Button type="primary" icon={<BookOutlined />} onClick={() => navigate('/ai/knowledge')}>知识库</Button>
          <Button type="text" icon={<CommentOutlined />} loading={hitLoading} onClick={runQuickHitTest}>聊天测试</Button>
          <Button type="text" icon={<AppstoreOutlined />} onClick={() => unsupportedAction('模型')}>模型</Button>
          <Button type="text" icon={<SettingOutlined />} onClick={() => navigate('/ai/knowledge')}>系统管理</Button>
          <Avatar className="maxkb-paragraph-avatar" icon={<UserOutlined />} />
        </nav>
      </header>

      <div className="maxkb-paragraph-titlebar">
        <div className="maxkb-paragraph-title">
          <Button type="text" icon={<ArrowLeftOutlined />} onClick={backToDocuments} />
          <Title level={3}>{documentName || documentId}</Title>
        </div>
        <Space>
          {!batchMode ? (
            <Button onClick={() => setBatchMode(true)}>批量选择</Button>
          ) : (
            <Button onClick={() => {
              setBatchMode(false)
              setSelectedKeys([])
            }}>
              取消选择
            </Button>
          )}
          {!batchMode ? <Button type="primary" onClick={() => unsupportedAction('添加分段')}>添加分段</Button> : null}
        </Space>
      </div>

      <section className="maxkb-paragraph-card">
        <div className="maxkb-paragraph-card-head">
          <Text strong>{total || paragraphRows.length} 段落</Text>
          <Input.Group compact className="maxkb-paragraph-search">
            <Select
              value={searchType}
              onChange={(value) => {
                setSearchType(value)
                setSearch('')
              }}
              options={[
                { value: 'title', label: '标题' },
                { value: 'content', label: '内容' },
              ]}
            />
            <Input
              allowClear
              prefix={<SearchOutlined />}
              value={search}
              placeholder="搜索"
              onChange={(event) => setSearch(event.target.value)}
              onPressEnter={() => fetchParagraphs({ search })}
            />
          </Input.Group>
        </div>

        <div className="maxkb-paragraph-layout">
          <aside className="maxkb-paragraph-sidebar">
            <div className="maxkb-paragraph-sidebar-title">{total || paragraphRows.length} 段落</div>
            <div className="maxkb-paragraph-anchor-list">
              {paragraphRows.map((item, index) => {
                const key = recordKey(item)
                return (
                  <button key={key} type="button" onClick={() => scrollToParagraph(key)}>
                    {getParagraphTitle(item, index)}
                  </button>
                )
              })}
            </div>
          </aside>

          <main className="maxkb-paragraph-detail">
            <Spin spinning={loading}>
              {!paragraphRows.length ? (
                <Empty className="maxkb-paragraph-empty" description="暂无分段" />
              ) : (
                <div className="maxkb-paragraph-list">
                  {paragraphRows.map((item, index) => {
                    const key = recordKey(item)
                    const selected = selectedKeySet.has(key)
                    return (
                      <div className="maxkb-paragraph-row" id={`paragraph-${key}`} key={key}>
                        {batchMode ? (
                          <Checkbox checked={selected} onChange={() => toggleSelect(key)} />
                        ) : (
                          <HolderOutlined className="maxkb-paragraph-drag" />
                        )}
                        <article
                          className={`maxkb-paragraph-box ${item.is_active === false ? 'is-disabled' : ''} ${selected ? 'is-selected' : ''}`}
                          onClick={() => batchMode && toggleSelect(key)}
                        >
                          {!batchMode ? (
                            <div className="maxkb-paragraph-operation">
                              <Switch size="small" checked={item.is_active !== false} onChange={() => unsupportedAction('启停分段')} />
                              <span className="maxkb-paragraph-divider" />
                              <Button type="text" icon={<EditOutlined />} onClick={(event) => {
                                event.stopPropagation()
                                unsupportedAction('编辑分段')
                              }} />
                              <Button type="text" icon={<PlusCircleOutlined />} onClick={(event) => {
                                event.stopPropagation()
                                unsupportedAction('前方添加分段')
                              }} />
                              <Dropdown
                                trigger={['click']}
                                menu={{
                                  items: [
                                    { key: 'generate', icon: <ThunderboltOutlined />, label: '生成问题', onClick: () => unsupportedAction('生成问题') },
                                    { key: 'move', icon: <HolderOutlined />, label: '移动位置', onClick: () => unsupportedAction('移动位置') },
                                    { key: 'delete', icon: <DeleteOutlined />, label: '删除', danger: true, onClick: () => unsupportedAction('删除分段') },
                                  ],
                                }}
                              >
                                <Button type="text" icon={<MoreOutlined />} onClick={(event) => event.stopPropagation()} />
                              </Dropdown>
                            </div>
                          ) : null}
                          <h2>{getParagraphTitle(item, index)}</h2>
                          <div className="maxkb-paragraph-content">
                            <MaxKbMarkdownContent accountId={accountId} content={textValue(item.content, item.text)} />
                          </div>
                        </article>
                      </div>
                    )
                  })}
                </div>
              )}
            </Spin>
          </main>
        </div>

        {batchMode ? (
          <div className="maxkb-paragraph-batchbar">
            <div className="maxkb-paragraph-batchbar-inner">
              <Checkbox checked={checkedAll} indeterminate={indeterminate} onChange={handleCheckAll}>
                全选
              </Checkbox>
              <Button disabled={!selectedKeys.length} onClick={() => unsupportedAction('生成问题')}>生成问题</Button>
              <Button disabled={!selectedKeys.length} onClick={() => unsupportedAction('迁移')}>迁移</Button>
              <Button disabled={!selectedKeys.length} onClick={() => unsupportedAction('删除')}>删除</Button>
              <Text type="secondary">已选择 {selectedKeys.length}/{total || paragraphRows.length} 个文档</Text>
              <Button type="link" onClick={() => {
                setBatchMode(false)
                setSelectedKeys([])
              }}>
                取消选择
              </Button>
            </div>
          </div>
        ) : null}
      </section>
    </div>
  )
}

export default ParagraphManage
