import { useCallback, useEffect, useMemo, useState } from 'react'
import { Button, Card, Empty, Popconfirm, Segmented, Space, Table, Tabs, Tag, Typography, message } from 'antd'
import { ClearOutlined, ReloadOutlined, ThunderboltOutlined } from '@ant-design/icons'
import { clearAgentToolCache, getAgentToolCacheStats } from '../../../api/rag'
import './AgentCache.css'

const { Text, Title } = Typography

const formatCount = (value) => Number(value || 0).toLocaleString()

const formatPercent = (value) => {
  const number = Number(value || 0)
  if (Number.isNaN(number)) return '0.0%'
  return `${(number * 100).toFixed(1)}%`
}

const formatDuration = (value) => {
  const number = Number(value || 0)
  if (!number) return '-'
  return `${number.toLocaleString()} ms`
}

const formatTime = (value) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString()
}

const toolLabels = {
  java_schedule_api: '课表工具',
  java_activity_api: '活动工具',
  java_meeting_api: '会议工具',
  java_canteen_api: '餐饮工具',
  java_facility_api: '设施工具',
  java_secondhand_api: '旧物工具',
}

const getToolLabel = (value) => toolLabels[value] || value || '-'

const stringifyParams = (params, query) => {
  if (query) return query
  if (!params || typeof params !== 'object' || !Object.keys(params).length) return '-'
  return Object.entries(params).map(([key, value]) => `${key}=${value}`).join('&')
}

const shortText = (value, maxLength = 44) => {
  const text = String(value || '').trim()
  if (!text) return '-'
  return text.length > maxLength ? `${text.slice(0, maxLength)}...` : text
}

function AgentCache() {
  const [loading, setLoading] = useState(false)
  const [clearing, setClearing] = useState(false)
  const [stats, setStats] = useState(null)
  const [eventFilter, setEventFilter] = useState('all')

  const loadStats = useCallback(async () => {
    setLoading(true)
    try {
      const res = await getAgentToolCacheStats()
      setStats(res.data || null)
    } catch (error) {
      setStats(null)
      message.error(error.message || '缓存统计加载失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadStats()
  }, [loadStats])

  const handleClear = async () => {
    setClearing(true)
    try {
      await clearAgentToolCache()
      message.success('缓存已清空')
      await loadStats()
    } catch (error) {
      message.error(error.message || '缓存清空失败')
    } finally {
      setClearing(false)
    }
  }

  const rows = useMemo(() => (
    Array.isArray(stats?.byPath) ? stats.byPath : []
  ), [stats])

  const events = useMemo(() => (
    Array.isArray(stats?.recentEvents) ? stats.recentEvents : []
  ), [stats])

  const filteredEvents = useMemo(() => {
    if (eventFilter === 'hit') return events.filter((item) => item.cacheHit)
    if (eventFilter === 'miss') return events.filter((item) => !item.cacheHit && item.status !== 'error')
    if (eventFilter === 'error') return events.filter((item) => item.status === 'error')
    return events
  }, [eventFilter, events])

  const entries = useMemo(() => (
    Array.isArray(stats?.cacheEntries) ? stats.cacheEntries : []
  ), [stats])

  const insights = useMemo(() => {
    const topPath = [...rows].sort((a, b) => Number(b.hitCount || 0) - Number(a.hitCount || 0))[0]
    const missPath = [...rows].sort((a, b) => Number(b.missCount || 0) - Number(a.missCount || 0))[0]
    const hotEntry = [...entries].sort((a, b) => (
      Number(b.hitCount || 0) * Number(b.originElapsedMs || 0)
    ) - (
      Number(a.hitCount || 0) * Number(a.originElapsedMs || 0)
    ))[0]
    const lastHit = events.find((item) => item.cacheHit)
    return [
      {
        label: '最近具体命中',
        value: lastHit ? `${getToolLabel(lastHit.toolName)} · ${lastHit.path}` : '暂无命中',
        detail: lastHit ? `缓存码 ${lastHit.cacheKey} · 节省 ${formatDuration(lastHit.savedMillis)}` : '重复问同一个校园业务问题后会出现',
      },
      {
        label: '最热命中接口',
        value: topPath?.hitCount ? topPath.path : '暂无热点',
        detail: topPath?.hitCount ? `命中 ${formatCount(topPath.hitCount)} 次 · 命中率 ${formatPercent(topPath.hitRate)}` : '还没有形成高频命中',
      },
      {
        label: '未命中最多接口',
        value: missPath?.missCount ? missPath.path : '暂无明显浪费',
        detail: missPath?.missCount ? `未命中 ${formatCount(missPath.missCount)} 次，可看参数是否过散` : '当前缓存利用正常',
      },
      {
        label: '最有价值条目',
        value: hotEntry?.hitCount ? `${hotEntry.path} · ${hotEntry.cacheKey}` : '暂无条目',
        detail: hotEntry?.hitCount ? `命中 ${formatCount(hotEntry.hitCount)} 次 · 原接口 ${formatDuration(hotEntry.originElapsedMs)}` : '需要先产生命中',
      },
    ]
  }, [entries, events, rows])

  const eventColumns = useMemo(() => [
    {
      title: '结果',
      dataIndex: 'cacheHit',
      width: 92,
      render: (value, record) => (
        <Tag color={value ? 'green' : record.status === 'error' ? 'red' : 'default'}>
          {value ? '命中' : record.status === 'error' ? '异常' : '未命中'}
        </Tag>
      ),
    },
    {
      title: '工具',
      dataIndex: 'toolName',
      width: 140,
      render: (value) => getToolLabel(value),
    },
    {
      title: '接口',
      dataIndex: 'path',
      width: 240,
      ellipsis: true,
      render: (value) => <Text code>{value || '-'}</Text>,
    },
    {
      title: '用户问题',
      dataIndex: 'inputPreview',
      ellipsis: true,
      render: (value) => <Text title={value}>{shortText(value, 52)}</Text>,
    },
    {
      title: '耗时/节省',
      width: 132,
      render: (_, record) => (
        <span>{formatDuration(record.elapsedMs)} / {formatDuration(record.savedMillis)}</span>
      ),
    },
    {
      title: '时间',
      dataIndex: 'time',
      width: 170,
      render: formatTime,
    },
  ], [])

  const entryColumns = useMemo(() => [
    {
      title: '缓存码',
      dataIndex: 'cacheKey',
      width: 120,
      render: (value) => <Text code>{value || '-'}</Text>,
    },
    {
      title: '工具',
      dataIndex: 'toolName',
      width: 150,
      render: (value) => getToolLabel(value),
    },
    {
      title: '接口',
      dataIndex: 'path',
      width: 240,
      ellipsis: true,
      render: (value) => <Text code>{value || '-'}</Text>,
    },
    {
      title: '参数',
      dataIndex: 'params',
      ellipsis: true,
      render: (value, record) => (
        <Text className="agent-cache-muted" title={stringifyParams(value, record.query)}>
          {shortText(stringifyParams(value, record.query), 52)}
        </Text>
      ),
    },
    { title: '命中', dataIndex: 'hitCount', width: 82, render: formatCount },
    { title: '条数', dataIndex: 'dataCount', width: 82, render: formatCount },
    { title: '过期时间', dataIndex: 'expiresAt', width: 180, render: formatTime },
  ], [])

  const pathColumns = useMemo(() => [
    {
      title: '接口',
      dataIndex: 'path',
      ellipsis: true,
      render: (value) => <Text code>{value || '-'}</Text>,
    },
    {
      title: '命中率',
      dataIndex: 'hitRate',
      width: 110,
      render: (value) => <Tag color={Number(value || 0) > 0 ? 'green' : 'default'}>{formatPercent(value)}</Tag>,
    },
    { title: '请求', dataIndex: 'requestCount', width: 90, render: formatCount },
    { title: '命中', dataIndex: 'hitCount', width: 90, render: formatCount },
    { title: '未命中', dataIndex: 'missCount', width: 90, render: formatCount },
    { title: '缓存条目', dataIndex: 'entryCount', width: 110, render: formatCount },
    { title: '节省耗时', dataIndex: 'estimatedSavedMillis', width: 120, render: formatDuration },
    { title: '最近命中', dataIndex: 'lastHitAt', width: 180, render: formatTime },
  ], [])

  const hitEventCount = events.filter((item) => item.cacheHit).length
  const missEventCount = events.filter((item) => !item.cacheHit && item.status !== 'error').length
  const errorEventCount = events.filter((item) => item.status === 'error').length

  return (
    <div className="agent-cache-page">
      <div className="agent-cache-head">
        <div>
          <Title level={2}>缓存监控</Title>
          <Text type="secondary">查看普通智能体调用校园业务接口时的缓存命中情况。</Text>
        </div>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={loadStats} loading={loading}>刷新</Button>
          <Popconfirm
            title="清空智能体工具缓存？"
            description="清空后已有缓存条目会失效，后续请求会重新访问接口。"
            okText="清空"
            cancelText="取消"
            okButtonProps={{ danger: true }}
            onConfirm={handleClear}
          >
            <Button danger icon={<ClearOutlined />} loading={clearing}>清空缓存</Button>
          </Popconfirm>
        </Space>
      </div>

      <Card className="agent-cache-shell">
        <Tabs
          items={[
            {
              key: 'overview',
              label: '总览',
              children: (
                <div className="agent-cache-overview">
                  <div className="agent-cache-metrics">
                    <div className="agent-cache-metric">
                      <span>缓存状态</span>
                      <strong>{stats?.enabled ? '已开启' : '未开启'}</strong>
                    </div>
                    <div className="agent-cache-metric">
                      <span>命中率</span>
                      <strong>{formatPercent(stats?.hitRate)}</strong>
                    </div>
                    <div className="agent-cache-metric">
                      <span>当前条目</span>
                      <strong>{formatCount(stats?.entryCount)} / {formatCount(stats?.maxEntries)}</strong>
                    </div>
                    <div className="agent-cache-metric">
                      <span>有效时间</span>
                      <strong>{formatCount(stats?.ttlSeconds)} 秒</strong>
                    </div>
                    <div className="agent-cache-metric">
                      <span>节省耗时</span>
                      <strong>{formatDuration(stats?.estimatedSavedMillis)}</strong>
                    </div>
                  </div>

                  <div className="agent-cache-diagnosis">
                    <div className="agent-cache-diagnosis-head">
                      <Space>
                        <ThunderboltOutlined />
                        <span>当前诊断</span>
                      </Space>
                      <Tag color={Number(stats?.hitCount || 0) ? 'green' : 'orange'}>
                        {Number(stats?.hitCount || 0) ? '已有命中' : '暂无命中'}
                      </Tag>
                    </div>
                    <div className="agent-cache-diagnosis-body">
                      <strong>
                        {Number(stats?.requestCount || 0)
                          ? Number(stats?.hitCount || 0)
                            ? '缓存已经生效，可以继续观察高频接口。'
                            : '已有请求但暂无命中。'
                          : '暂无调用数据。'}
                      </strong>
                      <Text type="secondary">
                        {Number(stats?.requestCount || 0)
                          ? Number(stats?.hitCount || 0)
                            ? '重点看接口汇总里的高请求、低命中接口，判断参数是否过散。'
                            : '建议连续用同一个校园业务问题测试两次，例如查询同一天课表或同一个食堂信息。'
                          : '请先在 App 聊天或智能体测试页触发一次 Leader 调用校园接口。'}
                      </Text>
                    </div>
                  </div>

                  <div className="agent-cache-insights">
                    {insights.map((item) => (
                      <div className="agent-cache-insight" key={item.label}>
                        <span>{item.label}</span>
                        <strong title={item.value}>{shortText(item.value, 42)}</strong>
                        <em>{item.detail}</em>
                      </div>
                    ))}
                  </div>

                  <div className="agent-cache-foot">
                    <span>总请求：{formatCount(stats?.requestCount)}</span>
                    <span>命中：{formatCount(stats?.hitCount)}</span>
                    <span>未命中：{formatCount(stats?.missCount)}</span>
                    <span>最近命中：{formatTime(stats?.lastHitAt)}</span>
                    <span>最近未命中：{formatTime(stats?.lastMissAt)}</span>
                  </div>
                </div>
              ),
            },
            {
              key: 'events',
              label: `调用记录 ${events.length}`,
              children: (
                <div className="agent-cache-tab-panel">
                  <div className="agent-cache-table-tools">
                    <Segmented
                      value={eventFilter}
                      options={[
                        { label: `全部 ${events.length}`, value: 'all' },
                        { label: `命中 ${hitEventCount}`, value: 'hit' },
                        { label: `未命中 ${missEventCount}`, value: 'miss' },
                        { label: `异常 ${errorEventCount}`, value: 'error' },
                      ]}
                      onChange={setEventFilter}
                    />
                    <Text type="secondary">展开行可查看缓存码、参数和返回条数。</Text>
                  </div>
                  <Table
                    rowKey={(record) => record.id || `${record.cacheKey}-${record.time}-${record.path}`}
                    loading={loading}
                    dataSource={filteredEvents}
                    columns={eventColumns}
                    pagination={{ pageSize: 8, showSizeChanger: false }}
                    scroll={{ x: 920 }}
                    rowClassName={(record) => (record.cacheHit ? 'agent-cache-row-hit' : '')}
                    expandable={{
                      expandedRowRender: (record) => (
                        <div className="agent-cache-detail-grid">
                          <span>缓存码：<Text code>{record.cacheKey || '-'}</Text></span>
                          <span>参数：{stringifyParams(record.params, record.query)}</span>
                          <span>条数：{formatCount(record.dataCount)}</span>
                        </div>
                      ),
                    }}
                    locale={{ emptyText: <Empty description="暂无调用记录" /> }}
                  />
                </div>
              ),
            },
            {
              key: 'entries',
              label: `缓存条目 ${entries.length}`,
              children: (
                <Table
                  rowKey={(record) => record.cacheKey}
                  loading={loading}
                  dataSource={entries}
                  columns={entryColumns}
                  pagination={{ pageSize: 8, showSizeChanger: false }}
                  scroll={{ x: 920 }}
                  expandable={{
                    expandedRowRender: (record) => (
                      <div className="agent-cache-detail-grid">
                        <span>用户问题：{record.inputPreview || '-'}</span>
                        <span>参数：{stringifyParams(record.params, record.query)}</span>
                        <span>原耗时：{formatDuration(record.originElapsedMs)}</span>
                        <span>创建时间：{formatTime(record.createdAt)}</span>
                      </div>
                    ),
                  }}
                  locale={{ emptyText: <Empty description="暂无有效缓存条目" /> }}
                />
              ),
            },
            {
              key: 'paths',
              label: `接口汇总 ${rows.length}`,
              children: (
                <Table
                  rowKey={(record) => record.path}
                  loading={loading}
                  dataSource={rows}
                  columns={pathColumns}
                  pagination={false}
                  scroll={{ x: 980 }}
                  locale={{ emptyText: <Empty description="暂无缓存调用记录" /> }}
                />
              ),
            },
          ]}
        />
      </Card>
    </div>
  )
}

export default AgentCache
