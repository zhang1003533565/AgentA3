import { useState, useEffect, useRef } from 'react'
import { message, Modal, Form, Input, Select, InputNumber, Button, Table, Tag, Space, Popconfirm } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined, EyeOutlined, SearchOutlined } from '@ant-design/icons'
import { getActivityList, createActivity, updateActivity, deleteActivity, batchDeleteActivity, getCategoryList, searchActivities, publishActivity } from '../../../api/activity'
import { getRegistrationList, removeRegistrationByManager } from '../../../api/registration'
import './ActivityManage.css'

const { TextArea } = Input
const { Option } = Select

const statusMap = {
  DRAFT: { text: '待发布', color: 'default' },
  PUBLISHED: { text: '已发布', color: 'green' },
  COMPLETED: { text: '已结束', color: 'blue' }
}

const parseTime = (value) => (value ? new Date(value.replace(' ', 'T')) : null)

const getPhase = (record) => {
  if (record.status === 'DRAFT') {
    return { text: '待发布', color: 'default' }
  }

  const now = new Date()
  const startTime = parseTime(record.startTime)
  const endTime = parseTime(record.endTime)

  if (endTime && now >= endTime) {
    return { text: '已结束', color: 'blue' }
  }
  if (startTime && now >= startTime) {
    return { text: '进行中', color: 'green' }
  }
  return { text: '报名中', color: 'gold' }
}

function ActivityManage() {
  const [activities, setActivities] = useState([])
  const [categories, setCategories] = useState([])
  const [loading, setLoading] = useState(false)
  const [modalVisible, setModalVisible] = useState(false)
  const [modalTitle, setModalTitle] = useState('创建活动')
  const [editingId, setEditingId] = useState(null)
  const [form] = Form.useForm()
  const [searchForm] = Form.useForm()
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0
  })
  const initialized = useRef(false)
  const [viewModalVisible, setViewModalVisible] = useState(false)
  const [viewRecord, setViewRecord] = useState(null)
  const [selectedRowKeys, setSelectedRowKeys] = useState([])
  const [batchDeleteModalVisible, setBatchDeleteModalVisible] = useState(false)
  const [registrationModalVisible, setRegistrationModalVisible] = useState(false)
  const [registrationLoading, setRegistrationLoading] = useState(false)
  const [registrationRows, setRegistrationRows] = useState([])
  const [registrationActivity, setRegistrationActivity] = useState(null)

  // 获取活动列表
  const fetchActivities = async (params = {}) => {
    setLoading(true)
    try {
      // 使用传入的参数或当前分页状态
      const page = params.page || pagination.current
      const size = params.size || pagination.pageSize
      const res = await getActivityList({
        page,
        size,
        ...params
      })
      if (res.code === 200) {
        setActivities(res.data?.records || [])
        setPagination({
          current: page,
          pageSize: size,
          total: res.data?.total || 0
        })
      }
    } finally {
      setLoading(false)
    }
  }

  // 获取分类列表
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

  // 搜索 - 使用搜索接口
  const handleSearch = (values) => {
    setPagination({ ...pagination, current: 1 })
    const params = { page: 1, size: pagination.pageSize }
    
    if (values.keyword) {
      // 有关键词时使用搜索接口（只传keyword）
      fetchSearchResults({ ...params, keyword: values.keyword })
    } else {
      // 无关键词时使用普通列表（支持分类和状态筛选）
      if (values.categoryId) params.categoryId = values.categoryId
      if (values.status !== undefined && values.status !== null) params.status = values.status
      fetchActivities(params)
    }
  }

  // 搜索活动
  const fetchSearchResults = async (params) => {
    setLoading(true)
    try {
      const res = await searchActivities(params)
      if (res.code === 200) {
        setActivities(res.data?.records || [])
        setPagination({
          ...pagination,
          total: res.data?.total || 0
        })
      }
    } finally {
      setLoading(false)
    }
  }

  // 重置搜索
  const handleReset = () => {
    searchForm.resetFields()
    setPagination({ ...pagination, current: 1 })
    fetchActivities()
  }

  // 打开创建弹窗
  const handleCreate = () => {
    setModalTitle('创建活动')
    setEditingId(null)
    form.resetFields()
    setModalVisible(true)
  }

  // 打开编辑弹窗
  const handleEdit = (record) => {
    setModalTitle('编辑活动')
    setEditingId(record.id)
    form.setFieldsValue({
      title: record.title,
      categoryId: record.categoryId,
      location: record.location,
      maxPeople: record.maxPeople,
      content: record.content,
      contactName: record.contactName,
      contactPhone: record.contactPhone,
      startTime: record.startTime,
      endTime: record.endTime,
      signupStartTime: record.signupStartTime,
      signupEndTime: record.signupEndTime
    })
    setModalVisible(true)
  }

  // 格式化日期时间为后端需要的格式
  const formatDateTime = (dateTimeStr) => {
    if (!dateTimeStr) return null
    // 将 "2026-03-17T15:27" 或 "2026-03-17T15:27:00" 转换为 "2026-03-17 15:27:00"
    const withoutT = dateTimeStr.replace('T', ' ')
    // 如果已经有秒了，直接返回；否则加上秒
    if (withoutT.match(/:\d{2}:\d{2}$/)) {
      return withoutT
    }
    return withoutT + ':00'
  }

  // 提交表单
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      const data = {
        title: values.title,
        categoryId: values.categoryId,
        location: values.location,
        maxPeople: values.maxPeople,
        content: values.content,
        contactName: values.contactName,
        contactPhone: values.contactPhone,
        startTime: formatDateTime(values.startTime),
        endTime: formatDateTime(values.endTime),
        signupStartTime: formatDateTime(values.signupStartTime),
        signupEndTime: formatDateTime(values.signupEndTime)
      }

      let res
      if (editingId) {
        res = await updateActivity(editingId, data)
      } else {
        res = await createActivity(data)
      }

      if (res.code === 200) {
        message.success(editingId ? '更新成功' : '创建成功')
        setModalVisible(false)
        fetchActivities()
      }
    } catch (error) {
      console.error('提交失败:', error)
    }
  }

  // 删除活动
  const handleDelete = async (id) => {
    try {
      const res = await deleteActivity(id)
      if (res.code === 200) {
        alert('删除成功')
        fetchActivities()
      }
    } catch (error) {
      console.error('删除失败:', error)
      // 检查是否是外键约束错误
      const errorMsg = error.message || error.msg || ''
      if (errorMsg.includes('Cannot delete or update a parent row')) {
        alert('该活动已有报名记录，无法删除')
      } else {
        alert('删除失败：' + (errorMsg || '未知错误'))
      }
    }
  }

  // 发布活动（DRAFT -> PUBLISHED），用于让 AppFrontend 的“校园活动”可见
  const handlePublish = async (id) => {
    try {
      const res = await publishActivity(id)
      if (res.code === 200) {
        message.success('发布成功')
        fetchActivities()
      }
    } catch (error) {
      console.error('发布失败:', error)
      const errorMsg = error.message || error.msg || '发布失败'
      message.error(errorMsg)
    }
  }

  // 打开批量删除确认弹窗
  const handleBatchDelete = () => {
    if (selectedRowKeys.length === 0) {
      alert('请选择要删除的活动')
      return
    }
    setBatchDeleteModalVisible(true)
  }

  // 确认批量删除
  const confirmBatchDelete = async () => {
    try {
      const res = await batchDeleteActivity(selectedRowKeys)
      if (res.code === 200) {
        alert('批量删除成功')
        setSelectedRowKeys([])
        setBatchDeleteModalVisible(false)
        fetchActivities()
      }
    } catch (error) {
      console.error('批量删除失败:', error)
      // 检查是否是外键约束错误
      const errorMsg = error.message || error.msg || ''
      if (errorMsg.includes('Cannot delete or update a parent row')) {
        alert('部分活动已有报名记录，无法删除')
      } else {
        alert('批量删除失败：' + (errorMsg || '未知错误'))
      }
    }
  }

  // 查看详情
  const handleView = (record) => {
    setViewRecord(record)
    setViewModalVisible(true)
  }

  const handleViewRegistrations = async (record) => {
    setRegistrationActivity(record)
    setRegistrationModalVisible(true)
    setRegistrationLoading(true)
    try {
      const res = await getRegistrationList(record.id, { page: 1, size: 999 })
      if (res.code === 200) {
        setRegistrationRows(res.data?.records || [])
      }
    } finally {
      setRegistrationLoading(false)
    }
  }

  const handleRemoveRegistration = async (registrationId) => {
    try {
      const res = await removeRegistrationByManager(registrationId)
      if (res.code === 200) {
        message.success('已移除报名人')
        setRegistrationRows((rows) => rows.filter((item) => item.id !== registrationId))
        setActivities((rows) => rows.map((item) => (
          item.id === registrationActivity?.id
            ? { ...item, currentPeople: Math.max(0, (item.currentPeople || 0) - 1) }
            : item
        )))
        setRegistrationActivity((item) => (
          item ? { ...item, currentPeople: Math.max(0, (item.currentPeople || 0) - 1) } : item
        ))
      }
    } catch (error) {
      console.error('移除报名失败:', error)
    }
  }

  // 表格列定义
  const columns = [
    {
      title: 'ID',
      dataIndex: 'id',
      width: 60,
      fixed: 'left'
    },
    {
      title: '活动标题',
      dataIndex: 'title',
      ellipsis: true,
      width: 200
    },
    {
      title: '分类',
      dataIndex: 'category',
      width: 100,
      render: (category) => category?.categoryName || '-'
    },
    {
      title: '地点',
      dataIndex: 'location',
      ellipsis: true,
      width: 140
    },
    {
      title: '人数',
      dataIndex: 'currentPeople',
      width: 90,
      render: (text, record) => `${text}/${record.maxPeople}`
    },
    {
      title: '时间信息',
      width: 170,
      render: (_, record) => (
        <div style={{ fontSize: 12, lineHeight: 1.5 }}>
          <div><span style={{ color: '#666' }}>活动:</span> {record.startTime?.slice(0, 10)}</div>
          <div><span style={{ color: '#666' }}>截止:</span> {record.signupEndTime?.slice(0, 10)}</div>
        </div>
      )
    },
    {
      title: '阶段',
      width: 90,
      render: (_, record) => {
        const phase = getPhase(record)
        return (
          <Tag color={phase.color} style={{ fontSize: 12, margin: 0 }}>
            {phase.text}
          </Tag>
        )
      }
    },
    {
      title: '发布状态',
      dataIndex: 'status',
      width: 90,
      render: (status) => (
        <Tag color={statusMap[status]?.color} style={{ fontSize: 12, margin: 0 }}>
          {statusMap[status]?.text}
        </Tag>
      )
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      fixed: 'right',
      render: (_, record) => (
        <Space size={4}>
          <Button 
            type="text" 
            size="small"
            icon={<EyeOutlined />} 
            onClick={() => handleView(record)}
          >
            查看
          </Button>
          <Button 
            type="text" 
            size="small"
            icon={<EditOutlined />} 
            onClick={() => handleEdit(record)}
          >
            编辑
          </Button>
          {record.status === 'DRAFT' && (
            <Button
              type="text"
              size="small"
              onClick={() => handlePublish(record.id)}
            >
              发布
            </Button>
          )}
          <Button
            type="text"
            size="small"
            onClick={() => handleViewRegistrations(record)}
          >
            报名名单
          </Button>
          <Popconfirm
            title="确定删除该活动吗？"
            onConfirm={() => handleDelete(record.id)}
            okText="确定"
            cancelText="取消"
          >
            <Button type="text" danger size="small" icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      )
    }
  ]

  return (
    <div className="activity-manage-container">
      {/* 主内容 */}
      <main className="manage-main">
        {/* 页面标题 */}
        <div className="page-header">
          <h2>活动管理</h2>
        </div>

        {/* 搜索栏 */}
        <div className="search-bar">
          <Form form={searchForm} layout="inline" onFinish={handleSearch}>
            <Form.Item name="keyword">
              <Input placeholder="搜索活动标题" prefix={<SearchOutlined />} allowClear />
            </Form.Item>
            <Form.Item name="categoryId">
              <Select placeholder="选择分类" allowClear style={{ width: 120 }}>
                {categories.map(cat => (
                  <Option key={cat.id} value={cat.id}>{cat.name}</Option>
                ))}
              </Select>
            </Form.Item>
            <Form.Item name="status">
              <Select placeholder="选择状态" allowClear style={{ width: 120 }}>
                {Object.entries(statusMap).map(([key, value]) => (
                  <Option key={key} value={key}>{value.text}</Option>
                ))}
              </Select>
            </Form.Item>
            <Form.Item>
              <Button type="primary" htmlType="submit">搜索</Button>
              <Button onClick={handleReset} style={{ marginLeft: 8 }}>重置</Button>
            </Form.Item>
          </Form>
          <Space>
            {selectedRowKeys.length > 0 && (
              <Button danger icon={<DeleteOutlined />} onClick={handleBatchDelete}>
                批量删除 ({selectedRowKeys.length})
              </Button>
            )}
            <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
              创建活动
            </Button>
          </Space>
        </div>

        {/* 活动列表 */}
        <Table
          columns={columns}
          dataSource={activities}
          rowKey="id"
          loading={loading}
          scroll={{ x: 1280 }}
          rowSelection={{
            selectedRowKeys,
            onChange: setSelectedRowKeys
          }}
          pagination={{
            ...pagination,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`
          }}
          onChange={(pag) => {
            const newPagination = { ...pagination, current: pag.current, pageSize: pag.pageSize }
            setPagination(newPagination)
            fetchActivities({ page: pag.current, size: pag.pageSize })
          }}
        />
      </main>

      {/* 创建/编辑弹窗 */}
      <Modal
        title={modalTitle}
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        width={700}
        okText="确定"
        cancelText="取消"
      >
        <Form
          form={form}
          layout="vertical"
          style={{ marginTop: 16 }}
        >
          <Form.Item
            name="title"
            label="活动标题"
            rules={[{ required: true, message: '请输入活动标题' }]}
          >
            <Input placeholder="请输入活动标题" maxLength={100} showCount />
          </Form.Item>

          <div className="form-row">
            <Form.Item
              name="categoryId"
              label="活动分类"
              rules={[{ required: true, message: '请选择活动分类' }]}
              style={{ flex: 1 }}
            >
              <Select placeholder="请选择分类">
                {categories.map(cat => (
                  <Option key={cat.id} value={cat.id}>{cat.name}</Option>
                ))}
              </Select>
            </Form.Item>

            <Form.Item
              name="maxPeople"
              label="人数上限"
              rules={[{ required: true, message: '请输入人数上限' }]}
              style={{ flex: 1, marginLeft: 16 }}
            >
              <InputNumber min={1} max={9999} placeholder="人数上限" style={{ width: '100%' }} />
            </Form.Item>
          </div>

          <Form.Item
            name="location"
            label="活动地点"
            rules={[{ required: true, message: '请输入活动地点' }]}
          >
            <Input placeholder="请输入活动地点" />
          </Form.Item>

          <div className="form-row">
            <Form.Item
              name="startTime"
              label="活动开始时间"
              rules={[{ required: true, message: '请输入活动开始时间' }]}
              style={{ flex: 1 }}
            >
              <Input type="datetime-local" style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              name="endTime"
              label="活动结束时间"
              rules={[{ required: true, message: '请输入活动结束时间' }]}
              style={{ flex: 1, marginLeft: 16 }}
            >
              <Input type="datetime-local" style={{ width: '100%' }} />
            </Form.Item>
          </div>

          <div className="form-row">
            <Form.Item
              name="signupStartTime"
              label="报名开始时间"
              rules={[{ required: true, message: '请输入报名开始时间' }]}
              style={{ flex: 1 }}
            >
              <Input type="datetime-local" style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              name="signupEndTime"
              label="报名结束时间"
              rules={[{ required: true, message: '请输入报名结束时间' }]}
              style={{ flex: 1, marginLeft: 16 }}
            >
              <Input type="datetime-local" style={{ width: '100%' }} />
            </Form.Item>
          </div>

          <div className="form-row">
            <Form.Item
              name="contactName"
              label="联系人"
              rules={[{ required: true, message: '请输入联系人' }]}
              style={{ flex: 1 }}
            >
              <Input placeholder="请输入联系人" />
            </Form.Item>

            <Form.Item
              name="contactPhone"
              label="联系电话"
              rules={[{ required: true, message: '请输入联系电话' }]}
              style={{ flex: 1, marginLeft: 16 }}
            >
              <Input placeholder="请输入联系电话" />
            </Form.Item>
          </div>

          <Form.Item
            name="content"
            label="活动详情"
            rules={[{ required: true, message: '请输入活动详情' }]}
          >
            <TextArea rows={4} placeholder="请输入活动详情" maxLength={2000} showCount />
          </Form.Item>
        </Form>
      </Modal>

      {/* 查看详情弹窗 */}
      <Modal
        title="活动详情"
        open={viewModalVisible}
        onCancel={() => setViewModalVisible(false)}
        width={600}
        footer={null}
      >
        {viewRecord && (
          <div className="activity-detail" style={{ padding: '16px 0' }}>
            <p><strong>标题：</strong>{viewRecord.title}</p>
            <p><strong>分类：</strong>{viewRecord.category?.categoryName}</p>
            <p><strong>地点：</strong>{viewRecord.location}</p>
            <p><strong>人数：</strong>{viewRecord.currentPeople}/{viewRecord.maxPeople}</p>
            <p><strong>活动时间：</strong>{viewRecord.startTime} 至 {viewRecord.endTime}</p>
            <p><strong>报名时间：</strong>{viewRecord.signupStartTime} 至 {viewRecord.signupEndTime}</p>
            <p><strong>联系人：</strong>{viewRecord.contactName} ({viewRecord.contactPhone})</p>
            <p><strong>活动阶段：</strong><Tag color={getPhase(viewRecord).color}>{getPhase(viewRecord).text}</Tag></p>
            <p><strong>发布状态：</strong><Tag color={statusMap[viewRecord.status]?.color}>{statusMap[viewRecord.status]?.text}</Tag></p>
            <p><strong>详情：</strong>{viewRecord.content}</p>
          </div>
        )}
      </Modal>

      <Modal
        title={registrationActivity ? `${registrationActivity.title} 报名名单` : '报名名单'}
        open={registrationModalVisible}
        onCancel={() => setRegistrationModalVisible(false)}
        footer={null}
        width={820}
      >
        <div style={{ marginBottom: 16, color: '#666' }}>
          当前报名人数：<strong>{registrationActivity?.currentPeople || 0}</strong>
          {registrationActivity?.maxPeople ? ` / ${registrationActivity.maxPeople}` : ''}
        </div>
        <Table
          rowKey="id"
          loading={registrationLoading}
          dataSource={registrationRows}
          pagination={false}
          locale={{ emptyText: '暂无报名记录' }}
          columns={[
            {
              title: '姓名',
              dataIndex: 'realName',
              render: (text, record) => text || record.username || '-'
            },
            {
              title: '学号/编号',
              dataIndex: 'personalNumber',
              render: (text) => text || '-'
            },
            {
              title: '手机号',
              dataIndex: 'phone',
              render: (text) => text || '-'
            },
            {
              title: '报名时间',
              dataIndex: 'signupTime',
              render: (text) => text || '-'
            },
            {
              title: '操作',
              key: 'action',
              width: 120,
              render: (_, record) => (
                <Popconfirm
                  title="确定移除该报名人吗？"
                  onConfirm={() => handleRemoveRegistration(record.id)}
                  okText="确定"
                  cancelText="取消"
                >
                  <Button type="text" danger size="small">
                    移除
                  </Button>
                </Popconfirm>
              )
            }
          ]}
        />
      </Modal>

      {/* 批量删除确认弹窗 */}
      <Modal
        title="确认批量删除"
        open={batchDeleteModalVisible}
        onOk={confirmBatchDelete}
        onCancel={() => setBatchDeleteModalVisible(false)}
        okText="确定"
        cancelText="取消"
        okButtonProps={{ danger: true }}
      >
        <p>确定要删除选中的 {selectedRowKeys.length} 个活动吗？</p>
      </Modal>
    </div>
  )
}

export default ActivityManage
