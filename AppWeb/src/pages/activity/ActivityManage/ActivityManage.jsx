import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, Form, Input, Popconfirm, Select, Space, Table, Tag, message } from 'antd'
import { DeleteOutlined, EyeOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons'
import { batchDeleteActivity, deleteActivity, getActivityList, getCategoryList, searchActivities } from '../../../api/activity'
import './ActivityManage.css'
import { getPhase } from '../activityHelpers'

const { Option } = Select

function ActivityManage() {
  const navigate = useNavigate()
  const [activities, setActivities] = useState([])
  const [categories, setCategories] = useState([])
  const [loading, setLoading] = useState(false)
  const [selectedRowKeys, setSelectedRowKeys] = useState([])
  const [searchForm] = Form.useForm()
  const initialized = useRef(false)
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
  })

  const fetchActivities = async (params = {}) => {
    setLoading(true)
    try {
      const page = params.page || pagination.current
      const size = params.size || pagination.pageSize
      const res = await getActivityList({
        page,
        size,
        ...params,
      })
      if (res.code === 200) {
        setActivities(res.data?.records || [])
        setPagination({
          current: page,
          pageSize: size,
          total: res.data?.total || 0,
        })
      }
    } finally {
      setLoading(false)
    }
  }

  const fetchSearchResults = async (params) => {
    setLoading(true)
    try {
      const res = await searchActivities(params)
      if (res.code === 200) {
        setActivities(res.data?.records || [])
        setPagination((prev) => ({
          ...prev,
          current: params.page || 1,
          pageSize: params.size || prev.pageSize,
          total: res.data?.total || 0,
        }))
      }
    } finally {
      setLoading(false)
    }
  }

  const fetchCategories = async () => {
    try {
      const res = await getCategoryList()
      if (res.code === 200) {
        setCategories(res.data || [])
      }
    } catch (error) {
      console.error('获取分类失败:', error)
    }
  }

  useEffect(() => {
    if (!initialized.current) {
      initialized.current = true
      fetchActivities()
      fetchCategories()
    }
  }, [])

  const handleSearch = (values) => {
    const params = { page: 1, size: pagination.pageSize }
    setPagination((prev) => ({ ...prev, current: 1 }))

    if (values.keyword) {
      fetchSearchResults({ ...params, keyword: values.keyword })
      return
    }

    if (values.categoryId) params.categoryId = values.categoryId
    fetchActivities(params)
  }

  const handleReset = () => {
    searchForm.resetFields()
    setPagination((prev) => ({ ...prev, current: 1 }))
    fetchActivities({ page: 1, size: pagination.pageSize })
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
      width: 220,
      ellipsis: true,
    },
    {
      title: '分类',
      dataIndex: 'category',
      width: 120,
      render: (category) => category?.categoryName || '-',
    },
    {
      title: '地点',
      dataIndex: 'location',
      width: 180,
      ellipsis: true,
    },
    {
      title: '人数',
      dataIndex: 'currentPeople',
      width: 110,
      render: (text, record) => `${text || 0}/${record.maxPeople || 0}`,
    },
    {
      title: '阶段',
      width: 100,
      render: (_, record) => {
        const phase = getPhase(record)
        return <Tag color={phase.color}>{phase.text}</Tag>
      },
    },
    {
      title: '开始时间',
      dataIndex: 'startTime',
      width: 180,
    },
    {
      title: '操作',
      key: 'action',
      width: 260,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button type="text" icon={<EyeOutlined />} onClick={() => navigate(`/activity/${record.id}`)}>
            详情
          </Button>
          <Button type="text" onClick={() => navigate(`/activity/${record.id}/edit`)}>
            编辑
          </Button>
          <Button type="text" onClick={() => navigate(`/activity/${record.id}`)}>
            报名名单
          </Button>
          <Popconfirm title="确定删除该活动吗？" onConfirm={() => handleDelete(record.id)}>
            <Button type="text" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div className="activity-manage-container">
      <main className="manage-main">
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
          dataSource={activities}
          rowKey="id"
          loading={loading}
          scroll={{ x: 1180 }}
          rowSelection={{
            selectedRowKeys,
            onChange: setSelectedRowKeys,
          }}
          pagination={{
            ...pagination,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`,
          }}
          onChange={(pag) => {
            setPagination((prev) => ({ ...prev, current: pag.current, pageSize: pag.pageSize }))
            fetchActivities({ page: pag.current, size: pag.pageSize })
          }}
        />
      </main>
    </div>
  )
}

export default ActivityManage
