import { useState, useEffect } from 'react'
import { message, Modal, Form, Select, Button, Table, Tag, Space, Popconfirm, Input, Checkbox } from 'antd'
import { CheckOutlined, CloseOutlined, EyeOutlined, SearchOutlined } from '@ant-design/icons'
import { getRegistrationList, auditRegistration, batchAuditRegistration } from '../../../api/registration'
import { getActivityList } from '../../../api/activity'
import './AuditManage.css'

const { Option } = Select
const { TextArea } = Input

// 报名状态映射
const statusMap = {
  0: { text: '待审核', color: 'orange' },
  1: { text: '已通过', color: 'green' },
  2: { text: '已拒绝', color: 'red' },
  3: { text: '已取消', color: 'default' },
  4: { text: '已签到', color: 'blue' },
  5: { text: '缺席', color: 'purple' }
}

function AuditManage() {
  const [registrations, setRegistrations] = useState([])
  const [activities, setActivities] = useState([])
  const [loading, setLoading] = useState(false)
  const [selectedRowKeys, setSelectedRowKeys] = useState([])
  const [auditModalVisible, setAuditModalVisible] = useState(false)
  const [auditForm] = Form.useForm()
  const [currentRecord, setCurrentRecord] = useState(null)
  const [isBatchAudit, setIsBatchAudit] = useState(false)
  const [searchForm] = Form.useForm()

  // 获取报名列表
  const fetchRegistrations = async (params = {}) => {
    setLoading(true)
    try {
      // 必须先选择活动才能查看报名列表
      if (params.activityId) {
        const res = await getRegistrationList(params.activityId)
        if (res.code === 200) {
          setRegistrations(res.data || [])
        }
      } else {
        // 没有选择活动时，清空列表
        setRegistrations([])
      }
    } finally {
      setLoading(false)
    }
  }

  // 获取活动列表
  const fetchActivities = async () => {
    try {
      const res = await getActivityList()
      if (res.code === 200) {
        setActivities(res.data?.records || [])
      }
    } catch (error) {
      console.error('获取活动列表失败:', error)
    }
  }

  useEffect(() => {
    fetchRegistrations()
    fetchActivities()
  }, [])

  // 搜索
  const handleSearch = (values) => {
    fetchRegistrations(values)
  }

  // 重置搜索
  const handleReset = () => {
    searchForm.resetFields()
    fetchRegistrations()
  }

  // 打开审核弹窗
  const handleAudit = (record, isBatch = false) => {
    setCurrentRecord(record)
    setIsBatchAudit(isBatch)
    auditForm.resetFields()
    setAuditModalVisible(true)
  }

  // 提交审核
  const handleAuditSubmit = async () => {
    try {
      const values = await auditForm.validateFields()
      
      let res
      if (isBatchAudit) {
        // 批量审核 - 后端参数名是 auditStatus
        res = await batchAuditRegistration(selectedRowKeys, values.status, values.remark)
      } else {
        // 单个审核 - 后端参数名是 auditStatus
        res = await auditRegistration(currentRecord.id, values.status, values.remark)
      }

      if (res.code === 200) {
        message.success('审核成功')
        setAuditModalVisible(false)
        setSelectedRowKeys([])
        fetchRegistrations()
      }
    } catch (error) {
      console.error('审核失败:', error)
    }
  }

  // 查看详情
  const handleView = (record) => {
    Modal.info({
      title: '报名详情',
      width: 500,
      content: (
        <div className="registration-detail">
          <p><strong>报名ID：</strong>{record.id}</p>
          <p><strong>活动名称：</strong>{record.activityName}</p>
          <p><strong>报名人：</strong>{record.realName || record.username}</p>
          <p><strong>学号：</strong>{record.studentNo}</p>
          <p><strong>手机号：</strong>{record.phone}</p>
          <p><strong>报名时间：</strong>{record.signupTime}</p>
          <p><strong>状态：</strong>{statusMap[record.status]?.text}</p>
          {record.remark && <p><strong>备注：</strong>{record.remark}</p>}
        </div>
      )
    })
  }

  // 表格选择配置
  const rowSelection = {
    selectedRowKeys,
    onChange: (keys) => setSelectedRowKeys(keys),
    getCheckboxProps: (record) => ({
      disabled: record.status !== 0 // 只能选中待审核的
    })
  }

  // 表格列定义
  const columns = [
    {
      title: '报名ID',
      dataIndex: 'id',
      width: 80
    },
    {
      title: '活动名称',
      dataIndex: 'activityName',
      ellipsis: true
    },
    {
      title: '报名人',
      dataIndex: 'realName',
      render: (text, record) => text || record.username
    },
    {
      title: '学号',
      dataIndex: 'studentNo',
      width: 120
    },
    {
      title: '手机号',
      dataIndex: 'phone',
      width: 130
    },
    {
      title: '报名时间',
      dataIndex: 'signupTime',
      width: 160
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status) => (
        <Tag color={statusMap[status]?.color}>
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
        <Space size="small">
          <Button 
            type="text" 
            icon={<EyeOutlined />} 
            onClick={() => handleView(record)}
          >
            查看
          </Button>
          {record.status === 0 && (
            <>
              <Button 
                type="text" 
                style={{ color: '#52c41a' }}
                icon={<CheckOutlined />} 
                onClick={() => handleAudit({ ...record, auditStatus: 1 })}
              >
                通过
              </Button>
              <Button 
                type="text" 
                danger
                icon={<CloseOutlined />} 
                onClick={() => handleAudit({ ...record, auditStatus: 2 })}
              >
                拒绝
              </Button>
            </>
          )}
        </Space>
      )
    }
  ]

  return (
    <div className="audit-manage-container">
      {/* 主内容 */}
      <main className="manage-main">
        {/* 页面标题 */}
        <div className="page-header">
          <h2>报名审核</h2>
        </div>

        {/* 搜索栏 */}
        <div className="search-bar">
          <Form form={searchForm} layout="inline" onFinish={handleSearch}>
            <Form.Item name="activityId">
              <Select placeholder="选择活动" allowClear style={{ width: 200 }}>
                {activities.map(act => (
                  <Option key={act.id} value={act.id}>{act.title}</Option>
                ))}
              </Select>
            </Form.Item>
            <Form.Item name="status">
              <Select placeholder="选择状态" allowClear style={{ width: 120 }}>
                {Object.entries(statusMap).map(([key, value]) => (
                  <Option key={key} value={parseInt(key)}>{value.text}</Option>
                ))}
              </Select>
            </Form.Item>
            <Form.Item>
              <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>搜索</Button>
              <Button onClick={handleReset} style={{ marginLeft: 8 }}>重置</Button>
            </Form.Item>
          </Form>
          {selectedRowKeys.length > 0 && (
            <Button 
              type="primary" 
              onClick={() => handleAudit(null, true)}
            >
              批量审核 ({selectedRowKeys.length})
            </Button>
          )}
        </div>

        {/* 报名列表 */}
        <Table
          rowSelection={rowSelection}
          columns={columns}
          dataSource={registrations}
          rowKey="id"
          loading={loading}
          pagination={{
            pageSize: 10,
            showTotal: (total) => `共 ${total} 条`
          }}
          scroll={{ x: 1200 }}
        />
      </main>

      {/* 审核弹窗 */}
      <Modal
        title={isBatchAudit ? '批量审核' : '审核报名'}
        open={auditModalVisible}
        onOk={handleAuditSubmit}
        onCancel={() => setAuditModalVisible(false)}
        width={500}
        okText="确定"
        cancelText="取消"
      >
        <Form
          form={auditForm}
          layout="vertical"
          style={{ marginTop: 16 }}
          initialValues={{ status: currentRecord?.auditStatus || 1 }}
        >
          <Form.Item
            name="status"
            label="审核结果"
            rules={[{ required: true, message: '请选择审核结果' }]}
          >
            <Select placeholder="请选择审核结果">
              <Option value={1}>通过</Option>
              <Option value={2}>拒绝</Option>
            </Select>
          </Form.Item>

          <Form.Item
            name="remark"
            label="审核备注"
          >
            <TextArea rows={3} placeholder="请输入审核备注（可选）" maxLength={200} showCount />
          </Form.Item>

          {isBatchAudit && (
            <p style={{ color: '#666' }}>
              即将审核 {selectedRowKeys.length} 条报名记录
            </p>
          )}
        </Form>
      </Modal>
    </div>
  )
}

export default AuditManage
