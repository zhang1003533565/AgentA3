import { useCallback, useEffect, useMemo, useState } from 'react'
import { Alert, Button, Card, Empty, Space, Table, Tag, Typography, message } from 'antd'
import { ClearOutlined, FireOutlined, ReloadOutlined, ThunderboltOutlined } from '@ant-design/icons'
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
      width: 96,
      render: (value, record) => (
        <Tag color={value ? 'green' : record.status === 'error' ? 'red' : 'default'}>
          {value ? '命中' : record.status === 'error' ? '异常' : '未命中'}
        </Tag>
      ),
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
    {
      title: '用户问题',
      dataIndex: 'inputPreview',
      ellipsis: true,
      render: (value) => <Text title={value}>{shortText(value, 52)}</Text>,
    },
    {
      title: '缓存码',
      dataIndex: 'cacheKey',
      width: 120,
      render: (value) => <Text code>{value || '-'}</Text>,
    },
    {
      title: '条数',
      dataIndex: 'dataCount',
      width: 78,
      render: formatCount,
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
      width: 180,
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
    {
      title: '用户问题',
      dataIndex: 'inputPreview',
      ellipsis: true,
      render: (value) => <Text title={value}>{shortText(value, 52)}</Text>,
    },
    { title: '命中', dataIndex: 'hitCount', width: 82, render: formatCount },
    { title: '条数', dataIndex: 'dataCount', width: 82, render: formatCount },
    { title: '原耗时', dataIndex: 'originElapsedMs', width: 96, render: formatDuration },
    { title: '创建时间', dataIndex: 'createdAt', width: 180, render: formatTime },
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

  return (
    <div className="agent-cache-page">
      <div className="agent-cache-head">
        <div>
          <Title level={3}>缓存监控</Title>
          <Text type="secondary">查看普通智能体调用校园业务接口时的缓存命中情况。</Text>
        </div>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={loadStats} loading={loading}>刷新</Button>
          <Button danger icon={<ClearOutlined />} onClick={handleClear} loading={clearing}>清空缓存</Button>
        </Space>
      </div>

      <Alert
        className="agent-cache-alert"
        type="info"
        showIcon
        message="当前监控的是普通智能体工具缓存"
        description="它覆盖 App 聊天和后台智能体测试中由 Leader 调用的课表、活动、会议、食堂、设施、旧物等 Java 校园接口；知识库检索缓存暂时不在这里处理。"
      />

      <div className="agent-cache-insights">
        {insights.map((item) => (
          <div className="agent-cache-insight" key={item.label}>
            <span>{item.label}</span>
            <strong title={item.value}>{shortText(item.value, 42)}</strong>
            <em>{item.detail}</em>
          </div>
        ))}
      </div>

      <Card
        className="agent-cache-card"
        title={(
          <Space>
            <ThunderboltOutlined />
            <span>智能体工具缓存</span>
            <Tag color={stats?.enabled ? 'green' : 'default'}>{stats?.enabled ? '已开启' : '未开启'}</Tag>
          </Space>
        )}
      >
        <div className="agent-cache-grid">
          <div>
            <span>命中率</span>
            <strong>{formatPercent(stats?.hitRate)}</strong>
          </div>
          <div>
            <span>总请求</span>
            <strong>{formatCount(stats?.requestCount)}</strong>
          </div>
          <div>
            <span>命中次数</span>
            <strong>{formatCount(stats?.hitCount)}</strong>
          </div>
          <div>
            <span>未命中次数</span>
            <strong>{formatCount(stats?.missCount)}</strong>
          </div>
          <div>
            <span>当前条目</span>
            <strong>{formatCount(stats?.entryCount)}</strong>
          </div>
          <div>
            <span>最多条目</span>
            <strong>{formatCount(stats?.maxEntries)}</strong>
          </div>
          <div>
            <span>有效时间</span>
            <strong>{formatCount(stats?.ttlSeconds)} 秒</strong>
          </div>
          <div>
            <span>节省耗时</span>
            <strong>{formatDuration(stats?.estimatedSavedMillis)}</strong>
          </div>
        </div>

        <div className="agent-cache-foot">
          <span>最近命中：{formatTime(stats?.lastHitAt)}</span>
          <span>最近未命中：{formatTime(stats?.lastMissAt)}</span>
        </div>
      </Card>

      <Card
        className="agent-cache-card"
        title={(
          <Space>
            <FireOutlined />
            <span>最近调用记录</span>
            <Tag color="blue">可定位具体命中</Tag>
          </Space>
        )}
      >
        <Table
          rowKey={(record) => record.id || `${record.cacheKey}-${record.time}-${record.path}`}
          loading={loading}
          dataSource={events}
          columns={eventColumns}
          pagination={{ pageSize: 8, showSizeChanger: false }}
          scroll={{ x: 1280 }}
          rowClassName={(record) => (record.cacheHit ? 'agent-cache-row-hit' : '')}
          locale={{ emptyText: <Empty description="暂无调用记录" /> }}
        />
      </Card>

      <Card className="agent-cache-card" title="当前缓存条目">
        <Table
          rowKey={(record) => record.cacheKey}
          loading={loading}
          dataSource={entries}
          columns={entryColumns}
          pagination={{ pageSize: 8, showSizeChanger: false }}
          scroll={{ x: 1320 }}
          locale={{ emptyText: <Empty description="暂无有效缓存条目" /> }}
        />
      </Card>

      <Card className="agent-cache-card" title="接口汇总">
        <Table
          rowKey={(record) => record.path}
          loading={loading}
          dataSource={rows}
          columns={pathColumns}
          pagination={false}
          scroll={{ x: 980 }}
          locale={{ emptyText: <Empty description="暂无缓存调用记录" /> }}
        />
      </Card>
    </div>
  )
}

export default AgentCache
