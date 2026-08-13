import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Avatar, Button, Form, Input, Modal, Popconfirm, Progress, Select, Space, Table, Tag, message } from 'antd'
import {
  ArrowLeftOutlined, CalendarOutlined, DeleteOutlined, DownloadOutlined, EyeOutlined, FileDoneOutlined,
  PlusOutlined, SearchOutlined, TeamOutlined, UserOutlined,
} from '@ant-design/icons'
import { getActivityDetail, getActivityList } from '../../../api/activity'
import { adminAddRegistration, getRegistrationList, removeRegistrationByManager } from '../../../api/registration'
import { getUserList } from '../../../api/user'
import '../ActivityManage/ActivityManage.css'
import './RegistrationManage.css'
import '../activityCards.css'

const nowTime = () => Date.now()

function regStatus(activity) {
  const cur = Number(activity.currentPeople) || 0
  const max = Number(activity.maxPeople) || 0
  if (max > 0 && cur >= max) return { text: '已满', color: 'red' }
  const end = new Date(String(activity.signupEndTime || '').replace(' ', 'T'))
  if (!isNaN(end.getTime()) && end.getTime() < nowTime()) return { text: '已结束', color: 'default' }
  return { text: '报名中', color: 'green' }
}

function regPercent(activity) {
  const cur = Number(activity.currentPeople) || 0
  const max = Number(activity.maxPeople) || 0
  if (max <= 0) return cur > 0 ? 100 : 0
  return Math.min(100, Math.round((cur / max) * 10000) / 100)
}

function formatDateTime(str) {
  if (!str) return '-'
  return String(str).replace('T', ' ').replace(/:\d{2}$/, '')
}

function RegistrationManage() {
  const navigate = useNavigate()
  const { id } = useParams()
  // 有 id：某个活动的报名学生名单；无 id：全部活动的报名情况概览（侧边栏入口）
  const isGlobal = !id

  const [activity, setActivity] = useState(null)
  const [activities, setActivities] = useState([])
  const [registrations, setRegistrations] = useState([])
  const [loading, setLoading] = useState(false)
  const [keyword, setKeyword] = useState('')
  const [studentKeyword, setStudentKeyword] = useState('')
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10 })
  const [searchForm] = Form.useForm()
  const [studentForm] = Form.useForm()
  const [addModalOpen, setAddModalOpen] = useState(false)
  const [studentOptions, setStudentOptions] = useState([])
  const [studentsLoading, setStudentsLoading] = useState(false)
  const [addUserId, setAddUserId] = useState(undefined)
  const [addSubmitting, setAddSubmitting] = useState(false)

  const fetchData = useCallback(async () => {
    setLoading(true)
    try {
      if (isGlobal) {
        const res = await getActivityList({ page: 1, size: 999 })
        if (res?.code === 200) {
          setActivities(res.data?.records || [])
        }
      } else {
        const [detailRes, regRes] = await Promise.all([
          getActivityDetail(id),
          getRegistrationList(id, { page: 1, size: 999 }),
        ])
        if (detailRes?.code === 200) {
          setActivity(detailRes.data || null)
        }
        if (regRes?.code === 200) {
          setRegistrations(regRes.data?.records || [])
        }
      }
    } catch (error) {
      message.error(error?.message || '加载报名数据失败')
    } finally {
      setLoading(false)
    }
  }, [id, isGlobal])

  useEffect(() => {
    fetchData()
  }, [fetchData])

  // ---------- 单活动：添加 / 移除报名 ----------
  const loadStudents = async () => {
    setStudentsLoading(true)
    try {
      const res = await getUserList({ page: 1, size: 999, role: 'STUDENT', status: 1 })
      if (res?.code === 200) {
        setStudentOptions(res.data?.records || [])
      }
    } catch (error) {
      message.error(error?.message || '加载学生列表失败')
    } finally {
      setStudentsLoading(false)
    }
  }

  const openAddModal = () => {
    setAddUserId(undefined)
    setAddModalOpen(true)
    if (studentOptions.length === 0) {
      loadStudents()
    }
  }

  const handleAddSubmit = async () => {
    if (addUserId == null) {
      message.warning('请选择要添加的学生')
      return
    }
    setAddSubmitting(true)
    try {
      const res = await adminAddRegistration(id, addUserId)
      if (res?.code === 200) {
        message.success('已添加报名')
        setAddModalOpen(false)
        setAddUserId(undefined)
        fetchData()
      }
    } catch (error) {
      message.error(error?.message || '添加失败')
    } finally {
      setAddSubmitting(false)
    }
  }

  const handleRemove = async (registrationId, realName) => {
    try {
      const res = await removeRegistrationByManager(registrationId)
      if (res?.code === 200) {
        message.success(`已移除${realName ? `「${realName}」的` : ''}报名`)
        fetchData()
      }
    } catch (error) {
      message.error(error?.message || '移除失败')
    }
  }

  // ---------- 全局：已有活动的报名情况概览 ----------
  const filteredActivities = useMemo(() => {
    const kw = keyword.trim().toLowerCase()
    return activities.filter((a) => !kw || String(a.title || '').toLowerCase().includes(kw))
  }, [activities, keyword])

  const activityStats = useMemo(() => {
    const result = { total: activities.length, open: 0, full: 0, totalReg: 0 }
    activities.forEach((a) => {
      result.totalReg += Number(a.currentPeople) || 0
      const st = regStatus(a)
      if (st.text === '已满') result.full += 1
      else if (st.text === '报名中') result.open += 1
    })
    return result
  }, [activities])

  const activityPageData = useMemo(() => {
    const start = (pagination.current - 1) * pagination.pageSize
    return filteredActivities.slice(start, start + pagination.pageSize)
  }, [filteredActivities, pagination])

  const handleActivitySearch = (values) => {
    setKeyword(values.keyword?.trim() || '')
    setPagination((prev) => ({ ...prev, current: 1 }))
  }

  const handleActivityReset = () => {
    searchForm.resetFields()
    setKeyword('')
    setPagination((prev) => ({ ...prev, current: 1 }))
  }

  const exportOverview = () => {
    if (!filteredActivities.length) {
      message.info('暂无可导出的活动数据')
      return
    }
    const header = ['活动标题', '分类', '报名人数', '人数上限', '报名截止', '报名状态']
    const rows = filteredActivities.map((a) => [
      a.title || '',
      a.category?.categoryName || '',
      String(Number(a.currentPeople) || 0),
      String(Number(a.maxPeople) || 0),
      formatDateTime(a.signupEndTime),
      regStatus(a).text,
    ])
    const csv = '\uFEFF' + [header, ...rows]
      .map((row) => row.map((cell) => `"${String(cell).replace(/"/g, '""')}"`).join(','))
      .join('\r\n')
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = '活动报名情况.csv'
    link.click()
    URL.revokeObjectURL(url)
  }

  const activityColumns = [
    {
      title: '活动标题',
      dataIndex: 'title',
      width: 200,
      ellipsis: true,
      render: (text, record) => (
        <Button type="link" style={{ padding: 0 }} onClick={() => navigate(`/activity/${record.id}`)}>
          {text || '-'}
        </Button>
      ),
    },
    {
      title: '分类',
      dataIndex: 'category',
      width: 100,
      render: (category) => category?.categoryName || '-',
    },
    {
      title: '报名人数',
      key: 'people',
      width: 110,
      render: (_, record) => `${Number(record.currentPeople) || 0} / ${Number(record.maxPeople) || 0}`,
    },
    {
      title: '报名进度',
      key: 'progress',
      width: 180,
      render: (_, record) => <Progress percent={regPercent(record)} size="small" format={(p) => `${Number(p).toFixed(2)}%`} />,
    },
    {
      title: '报名截止',
      dataIndex: 'signupEndTime',
      width: 150,
      render: (text) => formatDateTime(text),
    },
    {
      title: '状态',
      key: 'status',
      width: 90,
      render: (_, record) => {
        const st = regStatus(record)
        return <Tag color={st.color}>{st.text}</Tag>
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      fixed: 'right',
      render: (_, record) => (
        <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => navigate(`/activity/${record.id}/registrations`)}>
          查看名单
        </Button>
      ),
    },
  ]

  // ---------- 单活动：报名学生名单 ----------
  const filteredRegistrations = useMemo(() => {
    const kw = studentKeyword.trim().toLowerCase()
    return registrations.filter((item) => {
      if (!kw) return true
      return (
        String(item.realName || '').toLowerCase().includes(kw) ||
        String(item.username || '').toLowerCase().includes(kw) ||
        String(item.personalNumber || '').toLowerCase().includes(kw)
      )
    })
  }, [registrations, studentKeyword])

  const studentPageData = useMemo(() => {
    const start = (pagination.current - 1) * pagination.pageSize
    return filteredRegistrations.slice(start, start + pagination.pageSize)
  }, [filteredRegistrations, pagination])

  const handleStudentSearch = (values) => {
    setStudentKeyword(values.keyword?.trim() || '')
    setPagination((prev) => ({ ...prev, current: 1 }))
  }

  const handleStudentReset = () => {
    studentForm.resetFields()
    setStudentKeyword('')
    setPagination((prev) => ({ ...prev, current: 1 }))
  }

  const exportStudents = () => {
    if (!filteredRegistrations.length) {
      message.info('暂无可导出的报名记录')
      return
    }
    const header = ['报名人', '用户名', '学号', '报名时间']
    const rows = filteredRegistrations.map((item) => [
      item.realName || '',
      item.username || '',
      item.personalNumber || '',
      item.signupTime || '',
    ])
    const csv = '\uFEFF' + [header, ...rows]
      .map((row) => row.map((cell) => `"${String(cell).replace(/"/g, '""')}"`).join(','))
      .join('\r\n')
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${activity?.title || '活动'}-报名名单.csv`
    link.click()
    URL.revokeObjectURL(url)
  }

  const studentColumns = [
    {
      title: '报名人',
      key: 'student',
      width: 140,
      render: (_, item) => (
        <div className="reg-user">
          <Avatar className="reg-avatar">{String(item.realName || item.username || '?').charAt(0)}</Avatar>
          <div className="reg-user-info">
            <span className="reg-user-name">{item.realName || '-'}</span>
            <span className="reg-user-sub">{item.username || ''}</span>
          </div>
        </div>
      ),
    },
    {
      title: '学号',
      dataIndex: 'personalNumber',
      width: 140,
      render: (text) => text || '-',
    },
    {
      title: '报名时间',
      dataIndex: 'signupTime',
      width: 170,
      render: (text) => text || '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 90,
      render: (_, item) => (
        <Popconfirm title="确定移除该学生的报名吗？" onConfirm={() => handleRemove(item.id, item.realName)}>
          <Button type="text" size="small" danger icon={<DeleteOutlined />}>移除</Button>
        </Popconfirm>
      ),
    },
  ]

  return (
    <div className="activity-manage-container">
      <main className="manage-main">
        {isGlobal ? (
          <>
            <div className="ac-stat-row ac-stat-row--4">
              <div className="ac-stat-card ac-stat-card--static" style={{ borderTop: '3px solid #2563eb', background: '#eff6ff' }}>
                <span className="ac-stat-card-icon" style={{ color: '#2563eb' }}><CalendarOutlined /></span>
                <span className="ac-stat-card-text">
                  <span className="ac-stat-card-value">{activityStats.total}</span>
                  <span className="ac-stat-card-label">活动总数</span>
                </span>
              </div>
              <div className="ac-stat-card ac-stat-card--static" style={{ borderTop: '3px solid #16a34a', background: '#f0fdf4' }}>
                <span className="ac-stat-card-icon" style={{ color: '#16a34a' }}><FileDoneOutlined /></span>
                <span className="ac-stat-card-text">
                  <span className="ac-stat-card-value">{activityStats.open}</span>
                  <span className="ac-stat-card-label">报名中</span>
                </span>
              </div>
              <div className="ac-stat-card ac-stat-card--static" style={{ borderTop: '3px solid #ef4444', background: '#fef2f2' }}>
                <span className="ac-stat-card-icon" style={{ color: '#ef4444' }}><TeamOutlined /></span>
                <span className="ac-stat-card-text">
                  <span className="ac-stat-card-value">{activityStats.full}</span>
                  <span className="ac-stat-card-label">已满</span>
                </span>
              </div>
              <div className="ac-stat-card ac-stat-card--static" style={{ borderTop: '3px solid #d97706', background: '#fffbeb' }}>
                <span className="ac-stat-card-icon" style={{ color: '#d97706' }}><UserOutlined /></span>
                <span className="ac-stat-card-text">
                  <span className="ac-stat-card-value">{activityStats.totalReg}</span>
                  <span className="ac-stat-card-label">报名总人次</span>
                </span>
              </div>
            </div>

            <div className="search-bar">
              <Form form={searchForm} layout="inline" onFinish={handleActivitySearch}>
                <Form.Item name="keyword">
                  <Input placeholder="搜索活动标题" prefix={<SearchOutlined />} allowClear style={{ width: 240 }} />
                </Form.Item>
                <Form.Item>
                  <Space>
                    <Button type="primary" htmlType="submit">搜索</Button>
                    <Button onClick={handleActivityReset}>重置</Button>
                  </Space>
                </Form.Item>
              </Form>
              <Space>
                <Button icon={<DownloadOutlined />} onClick={exportOverview}>导出概况</Button>
              </Space>
            </div>

            <Table
              rowKey="id"
              loading={loading}
              columns={activityColumns}
              dataSource={activityPageData}
              scroll={{ x: 980 }}
              pagination={{
                current: pagination.current,
                pageSize: pagination.pageSize,
                total: filteredActivities.length,
                showSizeChanger: true,
                showTotal: (total) => `共 ${total} 个活动`,
              }}
              onChange={(pag) => {
                setPagination({ current: pag.current, pageSize: pag.pageSize })
              }}
            />
          </>
        ) : (
          <>
            {activity?.title ? (
              <div className="page-header">
                <h2>{activity.title}</h2>
              </div>
            ) : null}

            <div className="ac-stat-row ac-stat-row--2">
              <div className="ac-stat-card ac-stat-card--static" style={{ borderTop: '3px solid #2563eb', background: '#eff6ff' }}>
                <span className="ac-stat-card-icon" style={{ color: '#2563eb' }}><FileDoneOutlined /></span>
                <span className="ac-stat-card-text">
                  <span className="ac-stat-card-value">{registrations.length}</span>
                  <span className="ac-stat-card-label">报名人次</span>
                </span>
              </div>
              <div className="ac-stat-card ac-stat-card--static" style={{ borderTop: '3px solid #16a34a', background: '#f0fdf4' }}>
                <span className="ac-stat-card-icon" style={{ color: '#16a34a' }}><TeamOutlined /></span>
                <span className="ac-stat-card-text">
                  <span className="ac-stat-card-value">{Number(activity?.currentPeople) || 0} / {Number(activity?.maxPeople) || 0}</span>
                  <span className="ac-stat-card-label">名额</span>
                </span>
              </div>
            </div>

            <div className="search-bar">
              <Form form={studentForm} layout="inline" onFinish={handleStudentSearch}>
                <Form.Item name="keyword">
                  <Input placeholder="搜索姓名 / 学号" prefix={<SearchOutlined />} allowClear style={{ width: 220 }} />
                </Form.Item>
                <Form.Item>
                  <Space>
                    <Button type="primary" htmlType="submit">搜索</Button>
                    <Button onClick={handleStudentReset}>重置</Button>
                  </Space>
                </Form.Item>
              </Form>
              <Space>
                <Button type="primary" icon={<PlusOutlined />} onClick={openAddModal}>添加报名</Button>
                <Button icon={<DownloadOutlined />} onClick={exportStudents}>导出名单</Button>
                <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/activity/manage')}>返回列表</Button>
              </Space>
            </div>

            <Table
              rowKey="id"
              loading={loading}
              columns={studentColumns}
              dataSource={studentPageData}
              scroll={{ x: 620 }}
              pagination={{
                current: pagination.current,
                pageSize: pagination.pageSize,
                total: filteredRegistrations.length,
                showSizeChanger: true,
                showTotal: (total) => `共 ${total} 条`,
              }}
              onChange={(pag) => {
                setPagination({ current: pag.current, pageSize: pag.pageSize })
              }}
            />

            <Modal
              title="添加报名"
              open={addModalOpen}
              onOk={handleAddSubmit}
              onCancel={() => setAddModalOpen(false)}
              okText="确认添加"
              cancelText="取消"
              confirmLoading={addSubmitting}
              width={520}
            >
              <p style={{ marginTop: 0 }}>
                为活动「{activity?.title || ''}」添加一名学生的报名
              </p>
              <Select
                placeholder="搜索并选择学生（姓名 / 学号 / 用户名）"
                showSearch
                loading={studentsLoading}
                style={{ width: '100%' }}
                value={addUserId}
                onChange={setAddUserId}
                filterOption={(input, option) =>
                  String(option.label || '').toLowerCase().includes(input.toLowerCase())
                }
                options={studentOptions.map((u) => ({
                  value: u.id,
                  label: `${u.realName || ''}${u.personalNumber ? `（${u.personalNumber}）` : ''}${u.username ? ` · ${u.username}` : ''}`,
                }))}
              />
            </Modal>
          </>
        )}
      </main>
    </div>
  )
}

export default RegistrationManage