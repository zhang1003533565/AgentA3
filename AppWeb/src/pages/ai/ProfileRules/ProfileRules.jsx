import { useEffect, useMemo, useState } from 'react'
import { Alert, Card, Empty, List, Space, Spin, Table, Tag, Typography, message } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
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

const reliabilityColors = {
  高: 'green',
  中高: 'cyan',
  中: 'blue',
  中低: 'gold',
  低: 'volcano',
}

function ProfileRules() {
  const [loading, setLoading] = useState(false)
  const [rules, setRules] = useState([])
  const [globalRules, setGlobalRules] = useState([])
  const [leaderRules, setLeaderRules] = useState([])
  const [evidenceFlow, setEvidenceFlow] = useState([])
  const [evidenceScoringCriteria, setEvidenceScoringCriteria] = useState([])
  const [sourceReliabilityRules, setSourceReliabilityRules] = useState([])
  const [scoreDeltaRules, setScoreDeltaRules] = useState([])
  const [updateDecisionSteps, setUpdateDecisionSteps] = useState([])
  const [leaderUsagePolicies, setLeaderUsagePolicies] = useState([])
  const [conflictPolicies, setConflictPolicies] = useState([])
  const [evidenceSubmissionFields, setEvidenceSubmissionFields] = useState([])
  const [evidenceSubmissionExamples, setEvidenceSubmissionExamples] = useState([])
  const [autoCaptureSources, setAutoCaptureSources] = useState([])
  const [evidenceProtocolRules, setEvidenceProtocolRules] = useState([])
  const [auditFields, setAuditFields] = useState([])
  const [acceptanceCriteria, setAcceptanceCriteria] = useState([])

  const fetchRules = async () => {
    setLoading(true)
    try {
      const res = await getUserProfileRules()
      const data = res.data || {}
      setRules(data.rules || [])
      setGlobalRules(data.globalRules || [])
      setLeaderRules(data.leaderRules || [])
      setEvidenceFlow(data.evidenceFlow || [])
      setEvidenceScoringCriteria(data.evidenceScoringCriteria || [])
      setSourceReliabilityRules(data.sourceReliabilityRules || [])
      setScoreDeltaRules(data.scoreDeltaRules || [])
      setUpdateDecisionSteps(data.updateDecisionSteps || [])
      setLeaderUsagePolicies(data.leaderUsagePolicies || [])
      setConflictPolicies(data.conflictPolicies || [])
      setEvidenceSubmissionFields(data.evidenceSubmissionFields || [])
      setEvidenceSubmissionExamples(data.evidenceSubmissionExamples || [])
      setAutoCaptureSources(data.autoCaptureSources || [])
      setEvidenceProtocolRules(data.evidenceProtocolRules || [])
      setAuditFields(data.auditFields || [])
      setAcceptanceCriteria(data.acceptanceCriteria || [])
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
      title: '维度',
      dataIndex: 'name',
      width: 120,
      render: (value, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{value}</Text>
          <Text type="secondary">{record.key}</Text>
        </Space>
      ),
    },
    {
      title: '来源',
      dataIndex: 'sourceTypes',
      render: (value = []) => (
        <Space size={4} wrap>
          {value.map((item) => <Tag key={item}>{item}</Tag>)}
        </Space>
      ),
    },
    {
      title: '更新策略',
      dataIndex: 'updateStrategy',
      render: (value, record) => (
        <Space direction="vertical" size={4}>
          <Tag color={policyColors[record.updatePolicy] || 'default'}>
            {policyLabels[record.updatePolicy] || record.updatePolicy}
          </Tag>
          <Text>{value}</Text>
        </Space>
      ),
    },
    {
      title: '融合规则',
      width: 220,
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Text>最低置信度：{record.minConfidence}</Text>
          <Text>汇总融合上限：±{record.singleUpdateLimit} 分</Text>
          <Text>历史基线：当前分数 + 历史置信度</Text>
          <Text>更新方式：定时聚合候选证据</Text>
        </Space>
      ),
    },
    {
      title: 'Leader 使用',
      dataIndex: 'leaderUsage',
      render: (value) => <Text>{value}</Text>,
    },
  ], [])

  const scoringColumns = useMemo(() => [
    {
      title: '评分项',
      dataIndex: 'name',
      width: 150,
      render: (value, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{value}</Text>
          <Text type="secondary">{record.key}</Text>
        </Space>
      ),
    },
    {
      title: '权重',
      dataIndex: 'weight',
      width: 90,
      render: (value) => <Tag color="blue">{value}%</Tag>,
    },
    { title: '说明', dataIndex: 'description' },
    {
      title: '高分信号',
      dataIndex: 'highScoreSignals',
      render: (value = []) => <TagGroup rows={value} color="green" />,
    },
    {
      title: '低分信号',
      dataIndex: 'lowScoreSignals',
      render: (value = []) => <TagGroup rows={value} color="volcano" />,
    },
  ], [])

  const sourceColumns = useMemo(() => [
    { title: '来源类型', dataIndex: 'sourceType', width: 190 },
    {
      title: '权重',
      dataIndex: 'weight',
      width: 90,
      render: (value) => <Tag color="blue">{value}</Tag>,
    },
    {
      title: '可靠性',
      dataIndex: 'reliability',
      width: 90,
      render: (value) => <Tag color={reliabilityColors[value] || 'default'}>{value}</Tag>,
    },
    { title: '更新权限', dataIndex: 'updatePermission' },
    { title: '例子', dataIndex: 'example' },
  ], [])

  const deltaColumns = useMemo(() => [
    {
      title: '等级',
      dataIndex: 'evidenceStrength',
      width: 120,
      render: (value, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{value}</Text>
          <Text type="secondary">{record.level}</Text>
        </Space>
      ),
    },
    {
      title: '建议变化',
      dataIndex: 'suggestedDelta',
      width: 110,
      render: (value) => <Tag color="gold">±{value}</Tag>,
    },
    { title: '应用规则', dataIndex: 'applyRule' },
    { title: '复核规则', dataIndex: 'reviewRule' },
  ], [])

  const decisionColumns = useMemo(() => [
    {
      title: '步骤',
      dataIndex: 'step',
      width: 80,
      render: (value) => <Tag color="geekblue">{value}</Tag>,
    },
    { title: '检查项', dataIndex: 'name', width: 160 },
    { title: '通过条件', dataIndex: 'passCondition' },
    { title: '失败处理', dataIndex: 'failAction' },
  ], [])

  const leaderPolicyColumns = useMemo(() => [
    { title: '画像信号', dataIndex: 'profileSignal', width: 140 },
    { title: '允许使用', dataIndex: 'allowedUse' },
    { title: '禁止使用', dataIndex: 'forbiddenUse' },
    { title: '回答风格', dataIndex: 'responseStyle' },
  ], [])

  const conflictColumns = useMemo(() => [
    { title: '冲突场景', dataIndex: 'scenario', width: 190 },
    { title: '决策规则', dataIndex: 'decisionRule' },
    { title: '证据处理', dataIndex: 'evidenceAction' },
    { title: 'Leader 行为', dataIndex: 'leaderBehavior' },
  ], [])

  const submissionColumns = useMemo(() => [
    {
      title: '字段',
      dataIndex: 'field',
      width: 150,
      render: (value, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{value}</Text>
          <Tag color={record.required ? 'red' : 'default'}>{record.required ? '必填' : '可选'}</Tag>
        </Space>
      ),
    },
    { title: '类型', dataIndex: 'type', width: 120 },
    { title: '标准映射', dataIndex: 'sourceStandard', width: 180 },
    { title: '说明', dataIndex: 'description' },
    { title: '示例', dataIndex: 'example' },
  ], [])

  const autoCaptureColumns = useMemo(() => [
    {
      title: '来源',
      dataIndex: 'sourceType',
      width: 110,
      render: (value) => <Tag color="blue">{value}</Tag>,
    },
    { title: '触发条件', dataIndex: 'trigger' },
    { title: '提交方', dataIndex: 'submitter', width: 170 },
    {
      title: '影响维度',
      dataIndex: 'dimensions',
      render: (value = []) => <TagGroup rows={value} color="cyan" />,
    },
    { title: '置信规则', dataIndex: 'confidenceRule' },
    { title: '限制', dataIndex: 'note' },
  ], [])

  return (
    <div className="profile-rules-page">
      <section className="profile-rules-hero">
        <div>
          <span className="profile-rules-kicker">PROFILE RULES</span>
          <Title level={1}>画像规则</Title>
          <p>管理个人画像雷达图的来源、更新节奏和 Leader 使用边界。</p>
        </div>
        <button className="profile-rules-refresh" type="button" onClick={fetchRules}>
          <ReloadOutlined />
          <span>刷新</span>
        </button>
      </section>

      <Spin spinning={loading}>
        <div className="profile-rules-grid">
          <Card title="全局更新原则" className="profile-rules-card">
            <RuleList rows={globalRules} emptyText="暂无全局规则" />
          </Card>
          <Card title="Leader 回答规则" className="profile-rules-card">
            <RuleList rows={leaderRules} emptyText="暂无 Leader 规则" />
          </Card>
        </div>

        <Card title="证据流转流程" className="profile-rules-card profile-rules-flow">
          <List
            dataSource={evidenceFlow}
            locale={{ emptyText: <Empty description="暂无流程规则" /> }}
            renderItem={(item) => <List.Item>{item}</List.Item>}
          />
        </Card>

        <Alert
          className="profile-rules-alert"
          type="info"
          showIcon
          message="设计原则"
          description="聊天、会议、做题和点击行为会实时记录为画像证据；雷达图分数由定时汇总任务统一更新。Leader 每次可读取画像，但不能直接修改画像分数。"
        />

        <Card title="证据提交协议" className="profile-rules-card profile-rules-flow">
          <RuleList rows={evidenceProtocolRules} emptyText="暂无证据协议" />
          <Table
            rowKey="field"
            columns={submissionColumns}
            dataSource={evidenceSubmissionFields}
            pagination={false}
            locale={{ emptyText: <Empty description="暂无证据提交字段" /> }}
          />
        </Card>

        <Card title="自动采集来源" className="profile-rules-card profile-rules-flow">
          <Table
            rowKey="sourceType"
            columns={autoCaptureColumns}
            dataSource={autoCaptureSources}
            pagination={false}
            locale={{ emptyText: <Empty description="暂无自动采集来源" /> }}
          />
        </Card>

        <Card title="提交示例" className="profile-rules-card profile-rules-flow">
          <List
            dataSource={evidenceSubmissionExamples}
            locale={{ emptyText: <Empty description="暂无提交示例" /> }}
            renderItem={(item) => (
              <List.Item>
                <Space direction="vertical" size={8} className="profile-rules-example">
                  <Space size={8} wrap>
                    <Text strong>{item.scenario}</Text>
                    <Tag color="geekblue">{item.sourceType}</Tag>
                    <Text type="secondary">{item.description}</Text>
                  </Space>
                  <pre>{formatJson(item.payload)}</pre>
                </Space>
              </List.Item>
            )}
          />
        </Card>

        <Card title="证据评分公式" className="profile-rules-card profile-rules-flow">
          <div className="profile-rules-formula">
            证据置信度 = 来源可靠性 35% + 表达明确度 25% + 重复出现度 20% + 时间新鲜度 10% + 与历史一致性 10%
          </div>
          <div className="profile-rules-formula profile-rules-formula--secondary">
            定时汇总时：画像当前值 = 历史画像基线 + 候选证据聚合变化量 × 融合权重；融合权重由历史置信度和最新证据置信度共同决定。
          </div>
          <Table
            rowKey="key"
            columns={scoringColumns}
            dataSource={evidenceScoringCriteria}
            pagination={false}
            locale={{ emptyText: <Empty description="暂无证据评分标准" /> }}
          />
        </Card>

        <Card title="来源可靠性权重" className="profile-rules-card profile-rules-flow">
          <Table
            rowKey="sourceType"
            columns={sourceColumns}
            dataSource={sourceReliabilityRules}
            pagination={false}
            locale={{ emptyText: <Empty description="暂无来源权重标准" /> }}
          />
        </Card>

        <div className="profile-rules-grid">
          <Card title="分值变化等级" className="profile-rules-card">
            <Table
              rowKey="level"
              columns={deltaColumns}
              dataSource={scoreDeltaRules}
              pagination={false}
              locale={{ emptyText: <Empty description="暂无分值变化标准" /> }}
            />
          </Card>
          <Card title="审计字段" className="profile-rules-card">
            <RuleList rows={auditFields} emptyText="暂无审计字段" />
          </Card>
        </div>

        <Card title="画像更新决策步骤" className="profile-rules-card profile-rules-flow">
          <Table
            rowKey="step"
            columns={decisionColumns}
            dataSource={updateDecisionSteps}
            pagination={false}
            locale={{ emptyText: <Empty description="暂无更新决策步骤" /> }}
          />
        </Card>

        <Card title="Leader 使用边界" className="profile-rules-card profile-rules-flow">
          <Table
            rowKey="profileSignal"
            columns={leaderPolicyColumns}
            dataSource={leaderUsagePolicies}
            pagination={false}
            locale={{ emptyText: <Empty description="暂无 Leader 使用边界" /> }}
          />
        </Card>

        <Card title="冲突处理策略" className="profile-rules-card profile-rules-flow">
          <Table
            rowKey="scenario"
            columns={conflictColumns}
            dataSource={conflictPolicies}
            pagination={false}
            locale={{ emptyText: <Empty description="暂无冲突处理策略" /> }}
          />
        </Card>

        <Card title="验收标准" className="profile-rules-card profile-rules-flow">
          <RuleList rows={acceptanceCriteria} emptyText="暂无验收标准" />
        </Card>

        <Card title="7 个画像维度规则" className="profile-rules-card">
          <Table
            rowKey="key"
            columns={columns}
            dataSource={rules}
            pagination={false}
            locale={{ emptyText: <Empty description="暂无画像维度规则" /> }}
            expandable={{
              expandedRowRender: (record) => (
                <div className="profile-rules-expand">
                  <div>
                    <Text strong>证据示例</Text>
                    <div className="profile-rules-tags">
                      {(record.evidenceExamples || []).map((item) => <Tag key={item} color="cyan">{item}</Tag>)}
                    </div>
                  </div>
                  <div>
                    <Text strong>校验规则</Text>
                    <ul>
                      {(record.validationRules || []).map((item) => <li key={item}>{item}</li>)}
                    </ul>
                  </div>
                </div>
              ),
            }}
          />
        </Card>
      </Spin>
    </div>
  )
}

function RuleList({ rows, emptyText }) {
  if (!rows?.length) {
    return <Empty description={emptyText} />
  }
  return (
    <List
      dataSource={rows}
      renderItem={(item) => (
        <List.Item>
          <Text>{item}</Text>
        </List.Item>
      )}
    />
  )
}

function TagGroup({ rows = [], color }) {
  if (!rows.length) return <Text type="secondary">-</Text>
  return (
    <Space size={[4, 4]} wrap>
      {rows.map((item) => <Tag key={item} color={color}>{item}</Tag>)}
    </Space>
  )
}

function formatJson(value) {
  try {
    return JSON.stringify(value || {}, null, 2)
  } catch (error) {
    return '{}'
  }
}

export default ProfileRules
