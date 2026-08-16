import { useEffect, useMemo, useState } from 'react'
import { Button, Empty, Space, Spin, Table, Tag, Typography, message } from 'antd'
import {
  ReloadOutlined,
  RobotOutlined,
  SafetyCertificateOutlined,
  SyncOutlined,
} from '@ant-design/icons'
import { getUserProfileRules } from '../../../api/userProfile'
import './ProfileRules.css'

const { Text, Title } = Typography

const policyLabels = {
  stable: '稳定',
  slow: '慢更新',
  medium: '中频',
  faster: '较快',
}

const policyColors = {
  stable: 'blue',
  slow: 'green',
  medium: 'gold',
  faster: 'volcano',
}

const emptyRules = {
  rules: [],
  globalRules: [],
  leaderRules: [],
  evidenceFlow: [],
}

function ProfileRules() {
  const [loading, setLoading] = useState(false)
  const [ruleData, setRuleData] = useState(emptyRules)

  const fetchRules = async () => {
    setLoading(true)
    try {
      const res = await getUserProfileRules()
      const data = res.data || {}
      setRuleData({
        rules: data.rules || [],
        globalRules: data.globalRules || [],
        leaderRules: data.leaderRules || [],
        evidenceFlow: data.evidenceFlow || [],
      })
    } catch (error) {
      message.error(error.message || '画像规则加载失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchRules()
  }, [])

  const columns = useMemo(() => [
    {
      title: '画像维度',
      dataIndex: 'name',
      width: 160,
      render: (value, record) => (
        <div className="profile-rules-dimension">
          <span className="profile-rules-dimension-dot" />
          <div>
            <Text strong>{value}</Text>
            <Text type="secondary">{record.key}</Text>
          </div>
        </div>
      ),
    },
    {
      title: '主要来源',
      dataIndex: 'sourceTypes',
      render: (value = []) => (
        <Space size={[4, 6]} wrap>
          {value.map((item) => <Tag key={item}>{item}</Tag>)}
        </Space>
      ),
    },
    {
      title: '更新节奏',
      dataIndex: 'updateStrategy',
      width: 280,
      render: (value, record) => (
        <div className="profile-rules-policy">
          <Tag color={policyColors[record.updatePolicy] || 'default'}>
            {policyLabels[record.updatePolicy] || record.updatePolicy}
          </Tag>
          <Text>{value}</Text>
        </div>
      ),
    },
    {
      title: 'Leader 使用方式',
      dataIndex: 'leaderUsage',
      render: (value) => <Text>{value}</Text>,
    },
  ], [])

  return (
    <div className="profile-rules-page">
      <header className="profile-rules-header">
        <div>
          <Title level={2}>画像规则</Title>
          <p>查看画像如何形成、何时更新，以及 Leader 可以如何使用。</p>
        </div>
        <Button icon={<ReloadOutlined />} loading={loading} onClick={fetchRules}>
          刷新
        </Button>
      </header>

      <Spin spinning={loading}>
        <section className="profile-rules-principles" aria-labelledby="profile-principles-title">
          <div className="profile-rules-section-heading">
            <div>
              <span className="profile-rules-eyebrow">核心原则</span>
              <Title id="profile-principles-title" level={4}>先记录证据，再稳定更新画像</Title>
            </div>
            <div className="profile-rules-badges" aria-label="画像规则特点">
              <span><SafetyCertificateOutlined /> 可追溯</span>
              <span><SyncOutlined /> 慢更新</span>
              <span><RobotOutlined /> 只读使用</span>
            </div>
          </div>

          <div className="profile-rules-principle-grid">
            <RuleSummary
              icon={<SyncOutlined />}
              title="画像如何更新"
              rows={ruleData.globalRules}
              emptyText="暂无更新规则"
            />
            <RuleSummary
              icon={<RobotOutlined />}
              title="Leader 使用边界"
              rows={ruleData.leaderRules}
              emptyText="暂无 Leader 规则"
            />
          </div>
        </section>

        <section className="profile-rules-section" aria-labelledby="profile-flow-title">
          <div className="profile-rules-section-title">
            <div>
              <span className="profile-rules-eyebrow">更新流程</span>
              <Title id="profile-flow-title" level={4}>证据如何变成画像</Title>
            </div>
            <Text type="secondary">单条行为不会直接改变分数</Text>
          </div>
          <FlowList rows={ruleData.evidenceFlow} />
        </section>

        <section className="profile-rules-section profile-rules-dimensions" aria-labelledby="profile-dimensions-title">
          <div className="profile-rules-section-title">
            <div>
              <span className="profile-rules-eyebrow">维度规则</span>
              <Title id="profile-dimensions-title" level={4}>7 个画像维度</Title>
            </div>
            <Text type="secondary">展开行可查看证据与校验细节</Text>
          </div>
          <Table
            rowKey="key"
            columns={columns}
            dataSource={ruleData.rules}
            pagination={false}
            scroll={{ x: 960 }}
            locale={{ emptyText: <Empty description="暂无画像维度规则" /> }}
            expandable={{
              expandedRowRender: (record) => (
                <div className="profile-rules-expand">
                  <div className="profile-rules-metrics">
                    <span>最低置信度<strong>{record.minConfidence}</strong></span>
                    <span>单次更新上限<strong>±{record.singleUpdateLimit} 分</strong></span>
                  </div>
                  <DetailList title="证据示例" rows={record.evidenceExamples} tags />
                  <DetailList title="校验规则" rows={record.validationRules} />
                </div>
              ),
            }}
          />
        </section>
      </Spin>
    </div>
  )
}

function RuleSummary({ icon, title, rows = [], emptyText }) {
  if (!rows.length) {
    return <Empty description={emptyText} />
  }

  const visibleRows = rows.slice(0, 4)
  const remainingRows = rows.slice(4)

  return (
    <article className="profile-rules-summary">
      <div className="profile-rules-summary-title">
        <span>{icon}</span>
        <h3>{title}</h3>
      </div>
      <ul>
        {visibleRows.map((item) => <li key={item}>{item}</li>)}
      </ul>
      {remainingRows.length > 0 && (
        <details>
          <summary>查看其余 {remainingRows.length} 条</summary>
          <ul>
            {remainingRows.map((item) => <li key={item}>{item}</li>)}
          </ul>
        </details>
      )}
    </article>
  )
}

function FlowList({ rows = [] }) {
  if (!rows.length) {
    return <Empty description="暂无更新流程" />
  }

  return (
    <ol className="profile-rules-flow">
      {rows.map((item, index) => (
        <li key={item}>
          <span>{String(index + 1).padStart(2, '0')}</span>
          <p>{item.replace(/^\d+[.、]\s*/, '')}</p>
        </li>
      ))}
    </ol>
  )
}

function DetailList({ title, rows = [], tags = false }) {
  return (
    <div className="profile-rules-detail">
      <Text strong>{title}</Text>
      {rows.length ? (
        tags
          ? <div>{rows.map((item) => <Tag key={item}>{item}</Tag>)}</div>
          : <ul>{rows.map((item) => <li key={item}>{item}</li>)}</ul>
      ) : <Text type="secondary">暂无</Text>}
    </div>
  )
}

export default ProfileRules
