import { useMemo, useState } from 'react'
import { Button, Card, Empty, Input, Select, Table, Tag } from 'antd'
import { SearchOutlined } from '@ant-design/icons'
import { getWorkspacePage } from '../../data/portalData'
import './WorkspacePage.css'

const colorMap = {
  正常: 'green',
  启用: 'green',
  可用: 'green',
  已完成: 'green',
  已通过: 'green',
  已发送: 'green',
  营业中: 'green',
  正常营业: 'green',
  可领取: 'green',
  进行中: 'processing',
  处理中: 'processing',
  报名中: 'processing',
  签到中: 'processing',
  推荐中: 'processing',
  上升: 'processing',
  稳定: 'blue',
  已结束: 'default',
  已售出: 'default',
  已领完: 'default',
  已隐藏: 'orange',
  待审核: 'orange',
  待处理: 'orange',
  待开始: 'orange',
  待上新: 'orange',
  需人工: 'orange',
  需补充信息: 'orange',
  需复核: 'orange',
  已停用: 'red',
  停用: 'red',
  已拒绝: 'red',
  已拦截: 'red',
  已暂停: 'red',
  维护中: 'red',
  维修中: 'red',
  维修封闭: 'red',
  降级中: 'red',
  告警中: 'red',
  高优先级: 'red',
  高风险: 'red',
  规划中: 'gold',
  测试中: 'purple',
  灰度中: 'purple',
  聚合中: 'purple',
  热门: 'magenta',
  高热度: 'magenta',
  女生宿舍: 'magenta',
  男生宿舍: 'cyan',
  学生: 'blue',
  活动发布者: 'cyan',
  论坛审核员: 'purple',
  文本处理: 'blue',
  图像生成: 'purple',
  视频生成: 'magenta',
  P0: 'red',
  P1: 'orange',
}

function renderCell(value, type) {
  if (type === 'tag' || type === 'status') {
    return <Tag color={colorMap[value] || 'default'}>{value}</Tag>
  }

  if (type === 'progress') {
    return (
      <div className="workspace-progress">
        <div className="workspace-progress-track">
          <span style={{ width: `${value}%` }} />
        </div>
        <strong>{value}%</strong>
      </div>
    )
  }

  return value
}

function WorkspacePage({ pageKey }) {
  const page = getWorkspacePage(pageKey)
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState('全部')

  const filteredRows = useMemo(() => {
    if (!page) return []

    return page.rows.filter((row) => {
      const text = JSON.stringify(row).toLowerCase()
      const matchKeyword = !keyword || text.includes(keyword.toLowerCase())
      const matchStatus = status === '全部' || row.status === status || row.type === status
      return matchKeyword && matchStatus
    })
  }, [keyword, page, status])

  if (!page) {
    return <Empty description="页面配置不存在" />
  }

  const columns = page.columns.map((column) => ({
    title: column.title,
    dataIndex: column.dataIndex,
    key: column.dataIndex,
    render: (value) => renderCell(value, column.type),
  }))

  return (
    <div className="workspace-page">
      <section className="workspace-hero">
        <div>
          <span className="workspace-badge">{page.badge}</span>
          <h1>{page.title}</h1>
          <p>{page.description}</p>
        </div>
        <div className="workspace-actions">
          {page.actions.map((action) => (
            <Button key={action}>{action}</Button>
          ))}
        </div>
      </section>

      <section className="workspace-stats">
        {page.stats.map((item) => (
          <Card key={item.label} className="workspace-stat-card">
            <span>{item.label}</span>
            <strong>{item.value}</strong>
            <em>{item.detail}</em>
          </Card>
        ))}
      </section>

      <section className="workspace-main">
        <Card
          className="workspace-table-card"
          title="业务看板"
          extra={
            <div className="workspace-filters">
              <Input
                allowClear
                placeholder="搜索关键字"
                prefix={<SearchOutlined />}
                value={keyword}
                onChange={(event) => setKeyword(event.target.value)}
              />
              <Select
                value={status}
                onChange={setStatus}
                options={page.filters.status.map((item) => ({ value: item, label: item }))}
              />
            </div>
          }
        >
          <Table
            columns={columns}
            dataSource={filteredRows}
            rowKey="id"
            pagination={{ pageSize: 6, showSizeChanger: false }}
          />
        </Card>

        <div className="workspace-side">
          <Card title="当前关注">
            <ul className="workspace-list">
              {page.focus.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </Card>
          <Card title="实时动态">
            <div className="workspace-timeline">
              {page.timeline.map((item) => (
                <div key={`${item.time}-${item.title}`} className="workspace-timeline-item">
                  <span>{item.time}</span>
                  <div>
                    <strong>{item.title}</strong>
                    <p>{item.detail}</p>
                  </div>
                </div>
              ))}
            </div>
          </Card>
        </div>
      </section>
    </div>
  )
}

export default WorkspacePage
