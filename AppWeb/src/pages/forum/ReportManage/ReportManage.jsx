import { useState, useEffect } from 'react'
import { message, Modal, Form, Input, Button, Table, Tag, Space, Select } from 'antd'
import { EyeOutlined, SearchOutlined } from '@ant-design/icons'
import './ReportManage.css'

const { Option } = Select
const { TextArea } = Input

// 举报类型映射
const targetTypeMap = {
  1: { text: '帖子', color: 'blue' },
  2: { text: '评论', color: 'green' },
  3: { text: '用户', color: 'purple' }
}

// 处理状态映射
const statusMap = {
  0: { text: '待处理', color: 'orange' },
  1: { text: '已处理', color: 'green' },
  2: { text: '已驳回', color: 'default' }
}

// 举报原因映射
const reasonMap = {
  1: '垃圾广告',
  2: '虚假信息',
  3: '人身攻击',
  4: '色情低俗',
  5: '违法违规',
  6: '其他'
}

function ReportManage() {
  const [reports, setReports] = useState([])
  const [loading, setLoading] = useState(false)
  const [searchForm] = Form.useForm()
  const [handleModalVisible, setHandleModalVisible] = useState(false)
  const [handleForm] = Form.useForm()
  const [currentReport, setCurrentReport] = useState(null)
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0
  })

  // 获取举报列表（暂时使用空数据，等待后端API）
  const fetchReports = async (params = {}) => {
    setLoading(true)
    try {
      // TODO: 等待后端实现举报API
      // const res = await getReportList(params)
      setReports([])
      setPagination({
        ...pagination,
        total: 0
      })
    } catch (error) {
      console.error('获取举报列表失败:', error)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchReports()
  }, [])

  // 搜索
  const handleSearch = (values) => {
    setPagination({ ...pagination, current: 1 })
    fetchReports(values)
  }

  // 重置搜索
  const handleReset = () => {
    searchForm.resetFields()
    setPagination({ ...pagination, current: 1 })
    fetchReports()
  }

  // 查看详情
  const handleView = (record) => {
    Modal.info({
      title: '举报详情',
      width: 600,
      content: (
        <div className="report-detail">
          <p><strong>举报ID：</strong>{record.id}</p>
          <p><strong>举报人：</strong>{record.reporterName}</p>
          <p><strong>举报类型：</strong>{targetTypeMap[record.targetType]?.text}</p>
          <p><strong>被举报对象：</strong>{record.targetTitle}</p>
          <p><strong>被举报用户：</strong>{record.targetAuthor}</p>
          <p><strong>举报原因：</strong>{record.reasonText}</p>
          <p><strong>举报描述：</strong>{record.description}</p>
          <p><strong>状态：</strong>{statusMap[record.status]?.text}</p>
          <p><strong>举报时间：</strong>{record.createTime}</p>
          {record.status !== 0 && (
            <>
              <p><strong>处理人：</strong>{record.handleBy}</p>
              <p><strong>处理时间：</strong>{record.handleTime}</p>
              <p><strong>处理结果：</strong>{record.handleResult}</p>
            </>
          )}
        </div>
      )
    })
  }

  // 打开处理弹窗
  const handleOpenModal = (record) => {
    setCurrentReport(record)
    handleForm.resetFields()
    setHandleModalVisible(true)
  }

  // 提交处理
  const handleSubmit = async () => {
    try {
      const values = await handleForm.validateFields()
      // TODO: 等待后端实现处理举报API
      message.success('处理成功')
      setHandleModalVisible(false)
      fetchReports()
    } catch (error) {
      console.error('处理失败:', error)
    }
  }

  // 表格列定义
  const columns = [
    {
      title: 'ID',
      dataIndex: 'id',
      width: 60
    },
    {
      title: '举报人',
      dataIndex: 'reporterName',
      width: 100
    },
    {
      title: '举报类型',
      dataIndex: 'targetType',
      width: 90,
      render: (type) => (
        <Tag color={targetTypeMap[type]?.color}>
          {targetTypeMap[type]?.text}
        </Tag>
      )
    },
    {
      title: '被举报对象',
      dataIndex: 'targetTitle',
      ellipsis: true,
      width: 150
    },
    {
      title: '被举报用户',
      dataIndex: 'targetAuthor',
      width: 100
    },
    {
      title: '举报原因',
      dataIndex: 'reasonText',
      width: 100
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (status) => (
        <Tag color={statusMap[status]?.color}>
          {statusMap[status]?.text}
        </Tag>
      )
    },
    {
      title: '举报时间',
      dataIndex: 'createTime',
      width: 160
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
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
            <Button 
              type="text" 
              onClick={() => handleOpenModal(record)}
            >
              处理
            </Button>
          )}
        </Space>
      )
    }
  ]

  return (
    <div className="report-manage-container">
      {/* 主内容 */}
      <main className="manage-main">
        {/* 页面标题 */}
        <div className="page-header">
          <h2>举报处理</h2>
        </div>
        {/* 搜索栏 */}
        <div className="search-bar">
          <Form form={searchForm} layout="inline" onFinish={handleSearch}>
            <Form.Item name="targetType">
              <Select placeholder="举报类型" allowClear style={{ width: 120 }}>
                {Object.entries(targetTypeMap).map(([key, value]) => (
                  <Option key={key} value={parseInt(key)}>{value.text}</Option>
                ))}
              </Select>
            </Form.Item>
            <Form.Item name="status">
              <Select placeholder="处理状态" allowClear style={{ width: 120 }}>
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
        </div>

        {/* 举报列表 */}
        <Table
          columns={columns}
          dataSource={reports}
          rowKey="id"
          loading={loading}
          pagination={{
            ...pagination,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`
          }}
          locale={{ emptyText: '暂无举报记录' }}
          onChange={(pag) => {
            setPagination({ ...pagination, current: pag.current, pageSize: pag.pageSize })
            fetchReports({ page: pag.current, size: pag.pageSize })
          }}
          scroll={{ x: 1100 }}
        />
      </main>

      {/* 处理弹窗 */}
      <Modal
        title="处理举报"
        open={handleModalVisible}
        onOk={handleSubmit}
        onCancel={() => setHandleModalVisible(false)}
        width={500}
        okText="确定"
        cancelText="取消"
      >
        <Form
          form={handleForm}
          layout="vertical"
          style={{ marginTop: 16 }}
        >
          <p>举报类型：<strong>{currentReport && targetTypeMap[currentReport.targetType]?.text}</strong></p>
          <p>被举报对象：<strong>{currentReport?.targetTitle}</strong></p>
          <Form.Item
            name="handleResult"
            label="处理结果"
            rules={[{ required: true, message: '请输入处理结果' }]}
          >
            <TextArea rows={4} placeholder="请输入处理结果" maxLength={200} showCount />
          </Form.Item>
          <Form.Item
            name="action"
            label="处理动作"
            rules={[{ required: true, message: '请选择处理动作' }]}
          >
            <Select placeholder="请选择处理动作">
              <Option value={1}>删除违规内容</Option>
              <Option value={2}>警告用户</Option>
              <Option value={3}>封禁用户</Option>
              <Option value={4}>驳回举报</Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default ReportManage
