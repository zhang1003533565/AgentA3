import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, Form, Input, Popconfirm, Progress, Select, Space, Table, Tag, message } from 'antd'
import {
  CalendarOutlined, CheckCircleOutlined, ClockCircleOutlined, DeleteOutlined, EyeOutlined,
  PlayCircleOutlined, PlusOutlined, SearchOutlined, TeamOutlined,
} from '@ant-design/icons'
import { batchDeleteActivity, deleteActivity, getActivityList, getCategoryList } from '../../../api/activity'
import './ActivityManage.css'
import '../activityCards.css'
import { PHASE_CONFIG, getPhaseKey } from '../activityHelpers'

const { Option } = Select

const PHASE_TABS = [
  { key: 'all', label: '活动总数', color: '#2563eb', bg: '#eff6ff', icon: <CalendarOutlined /> },
  { key: 'upcoming', label: '即将开始', color: '#fa8c16', bg: '#fff7e6', icon: <ClockCircleOutlined /> },
  { key: 'ongoing', label: '进行中', color: '#52c41a', bg: '#f6ffed', icon: <PlayCircleOutlined /> },
  { key: 'ended', label: '已结束', color: '#6b7280', bg: '#f3f4f6', icon: <CheckCircleOutlined /> },
  { key: 'full', label: '已满', color: '#f5222d', bg: '#fff1f0', icon: <TeamOutlined /> },
]

const regPercent = (record) => {
  const cur = Number(record.currentPeople) || 0
  const max = Number(record.maxPeople) || 0
  if (max <= 0) return cur > 0 ? 100 : 0
  return Math.min(100, Math.round((cur / max) * 10000) / 100)
}

const formatShortTime = (text) => (text ? String(text).replace('T', ' ').replace(/:\d{2}$/, '') : '-')

function ActivityManage() {
  const navigate = useNavigate()
  const [activities, setActivities] = useState([])
  const [categories, setCategories] = useState([])
  const [loading, setLoading] = useState(false)
  const [selectedRowKeys, setSelectedRowKeys] = useState([])
  const [searchForm] = Form.useForm()
  const initialized = useRef(false)
  const [queryParams, setQueryParams] = useState({
    keyword: '',
    categoryId: undefined,
  })
  const [phase, setPhase] = useState('all')
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
  })

  const fetchActivities = useCallback(async () => {
    setLoading(true)
    try {
      const res = await getActivityList({ page: 1, size: 1000 })
      if (res.code === 200) {
        setActivities(res.data?.records || [])
      }
    } finally {
      setLoading(false)
    }
  }, [])

  const fetchCategories = useCallback(async () => {
    try {
      const res = await getCategoryList()
      if (res.code === 200) {
        setCategories(res.data || [])
      }
    } catch (error) {
      console.error('获取分类失败:', error)
    }
  }, [])

  useEffect(() => {
    if (!initialized.current) {
      initialized.current = true
      fetchActivities()
      fetchCategories()
    }
  }, [fetchActivities, fetchCategories])

  const counts = useMemo(() => {
    const result = { all: activities.length, upcoming: 0, ongoing: 0, ended: 0, full: 0 }
    activities.forEach((item) => {
      result[getPhaseKey(item)] += 1
    })
    return result
  }, [activities])

  const filteredActivities = useMemo(() => {
    const keyword = (queryParams.keyword || '').trim().toLowerCase()
    return activities.filter((item) => {
      if (phase !== 'all' && getPhaseKey(item) !== phase) return false
      if (queryParams.categoryId != null && Number(item.categoryId) !== Number(queryParams.categoryId)) return false
      if (keyword && !String(item.title || '').toLowerCase().includes(keyword)) return false
      return true
    })
  }, [activities, phase, queryParams])

  const pageData = useMemo(() => {
    const start = (pagination.current - 1) * pagination.pageSize
    return filteredActivities.slice(start, start + pagination.pageSize)
  }, [filteredActivities, pagination])

  const handleSearch = (values) => {
    setQueryParams({
      keyword: values.keyword?.trim() || '',
      categoryId: values.categoryId,
    })
    setPagination((prev) => ({ ...prev, current: 1 }))
  }

  const handleReset = () => {
    searchForm.resetFields()
    setQueryParams({
      keyword: '',
      categoryId: undefined,
    })
    setPhase('all')
    setPagination((prev) => ({ ...prev, current: 1 }))
  }

  const handlePhaseChange = (key) => {
    setPhase(key)
    setPagination((prev) => ({ ...prev, current: 1 }))
  }

  const handleDelete = async (id) => {
    try {
      const res = await deleteActivity(id)
      if (res.code === 200) {
        message.success('删除成功')
        fetchActivities()
      }
    } catch (error) {
      message.error(error.message || '删除失败')
    }
  }

  const handleBatchDelete = async () => {
    try {
      const res = await batchDeleteActivity(selectedRowKeys)
      if (res.code === 200) {
        message.success('批量删除成功')
        setSelectedRowKeys([])
        fetchActivities()
      }
    } catch (error) {
      message.error(error.message || '批量删除失败')
    }
  }

  const columns = [
    {
      title: '活动标题',
      dataIndex: 'title',
      width: 110,
      ellipsis: true,
    },
    {
      title: '分类',
      dataIndex: 'category',
      width: 80,
      render: (category) => category?.categoryName || '-',
    },
    {
      title: '地点',
      dataIndex: 'location',
      width: 90,
      ellipsis: true,
    },
    {
      title: '报名人数',
      key: 'people',
      width: 160,
      render: (_, record) => (
        <div className="am-people">
          <span className="am-people-count">{Number(record.currentPeople) || 0} / {Number(record.maxPeople) || 0}</span>
          <Progress percent={regPercent(record)} size="small" format={(p) => `${Number(p).toFixed(2)}%`} />
        </div>
      ),
    },
    {
      title: '报名截止',
      dataIndex: 'signupEndTime',
      width: 120,
      render: (text) => formatShortTime(text),
    },
    {
      title: '阶段',
      width: 80,
      render: (_, record) => {
        const phase = PHASE_CONFIG[getPhaseKey(record)]
        return <Tag color={phase.color}>{phase.text}</Tag>
      },
    },
    {
      title: '开始时间',
      dataIndex: 'startTime',
      width: 120,
      render: (text) => formatShortTime(text),
    },
    {
      title: '操作',
      key: 'action',
      width: 170,
      fixed: 'right',
      render: (_, record) => (
        <Space size={4} direction="vertical">
          <Space size={4}>
            <Button type="text" size="small" onClick={() => navigate(`/activity/${record.id}/edit`)}>
              编辑
            </Button>
            <Button type="text" size="small" onClick={() => navigate(`/activity/${record.id}/registrations`)}>
              报名名单
            </Button>
          </Space>
          <Space size={4}>
            <Button type="text" size="small" icon={<EyeOutlined />} onClick={() => navigate(`/activity/${record.id}`)}>
              详情
            </Button>
            <Popconfirm title="确定删除该活动吗？" onConfirm={() => handleDelete(record.id)}>
              <Button type="text" size="small" danger icon={<DeleteOutlined />}>
                删除
              </Button>
            </Popconfirm>
          </Space>
        </Space>
      ),
    },
  ]

  return (
    <div className="activity-manage-container">
      <main className="manage-main">
        <div className="ac-stat-row">
          {PHASE_TABS.map((tab) => (
            <button
              key={tab.key}
              type="button"
              className={`ac-stat-card ${phase === tab.key ? 'active' : ''}`}
              style={{ borderTop: `3px solid ${tab.color}`, background: tab.bg }}
              onClick={() => handlePhaseChange(tab.key)}
            >
              <span className="ac-stat-card-icon" style={{ color: tab.color }}>{tab.icon}</span>
              <span className="ac-stat-card-text">
                <span className="ac-stat-card-value">{counts[tab.key]}</span>
                <span className="ac-stat-card-label">{tab.label}</span>
              </span>
            </button>
          ))}
        </div>

        <div className="search-bar">
          <Form form={searchForm} layout="inline" onFinish={handleSearch}>
            <Form.Item name="keyword">
              <Input placeholder="搜索活动标题" prefix={<SearchOutlined />} allowClear />
            </Form.Item>
            <Form.Item name="categoryId">
              <Select placeholder="选择分类" allowClear style={{ width: 140 }}>
                {categories.map((cat) => (
                  <Option key={cat.id} value={cat.id}>
                    {cat.name}
                  </Option>
                ))}
              </Select>
            </Form.Item>
            <Form.Item>
              <Button type="primary" htmlType="submit">
                搜索
              </Button>
              <Button onClick={handleReset} style={{ marginLeft: 8 }}>
                重置
              </Button>
            </Form.Item>
          </Form>
          <Space>
            {selectedRowKeys.length > 0 ? (
              <Popconfirm title={`确定删除选中的 ${selectedRowKeys.length} 个活动吗？`} onConfirm={handleBatchDelete}>
                <Button danger icon={<DeleteOutlined />}>
                  批量删除
                </Button>
              </Popconfirm>
            ) : null}
            <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/activity/create')}>
              创建活动
            </Button>
          </Space>
        </div>

        <Table
          columns={columns}
          dataSource={pageData}
          rowKey="id"
          loading={loading}
          scroll={{ x: 1020 }}
          rowSelection={{
            selectedRowKeys,
            onChange: setSelectedRowKeys,
          }}
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total: filteredActivities.length,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`,
          }}
          onChange={(pag) => {
            setPagination({ current: pag.current, pageSize: pag.pageSize })
          }}
        />
      </main>
    </div>
  )
}

export default ActivityManage
