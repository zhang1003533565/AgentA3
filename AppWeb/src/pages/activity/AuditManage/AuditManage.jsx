import { useState, useEffect } from 'react'
import { message, Modal, Form, Select, Button, Table, Tag, Space, Popconfirm, Input, Checkbox } from 'antd'
import { CheckOutlined, CloseOutlined, EyeOutlined, SearchOutlined } from '@ant-design/icons'
import { getRegistrationList, auditRegistration, batchAuditRegistration } from '../../../api/registration'
import { getActivityList } from '../../../api/activity'
import './AuditManage.css'

const { Option } = Select
const { TextArea } = Input

// 鎶ュ悕鐘舵€佹槧灏?
const statusMap = {
  PENDING: { text: '待审核', color: 'orange' },
  CANCEL_PENDING: { text: '取消待审核', color: 'gold' },
  APPROVED: { text: '已通过', color: 'green' },
  REJECTED: { text: '已拒绝', color: 'red' }
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
  const [activeActivityId, setActiveActivityId] = useState(null)

  // 鑾峰彇鎶ュ悕鍒楄〃
  const fetchRegistrations = async (params = {}) => {
    setLoading(true)
    try {
      // 蹇呴』鍏堥€夋嫨娲诲姩鎵嶈兘鏌ョ湅鎶ュ悕鍒楄〃
      if (params.activityId) {
        const res = await getRegistrationList(params.activityId)
        if (res.code === 200) {
          // 鍚庣杩斿洖 Result.data = PageResponse锛岄渶鍙?records
          setRegistrations(res.data?.records || [])
        }
      } else {
        // 娌℃湁閫夋嫨娲诲姩鏃讹紝娓呯┖鍒楄〃
        setRegistrations([])
      }
    } finally {
      setLoading(false)
    }
  }

  // 鑾峰彇娲诲姩鍒楄〃
  const fetchActivities = async () => {
    try {
      const res = await getActivityList()
      if (res.code === 200) {
        const list = res.data?.records || []
        setActivities(list)
        return list
      }
    } catch (error) {
      console.error('鑾峰彇娲诲姩鍒楄〃澶辫触:', error)
    }
    return []
  }

  useEffect(() => {
    // 鍒濆鍖栵細鍏堟媺娲诲姩鍒楄〃锛屽啀榛樿鎷夌涓€涓椿鍔ㄧ殑鎶ュ悕鍒楄〃
    ;(async () => {
      const acts = await fetchActivities()
      if (acts && acts.length > 0) {
        const id = acts[0].id
        setActiveActivityId(id)
        searchForm.setFieldsValue({ activityId: id })
        fetchRegistrations({ activityId: id })
      } else {
        setActiveActivityId(null)
        setRegistrations([])
      }
    })()
  }, [searchForm])

  // 鎼滅储
  const handleSearch = (values) => {
    setActiveActivityId(values?.activityId || null)
    fetchRegistrations(values)
  }

  // 閲嶇疆鎼滅储
  const handleReset = () => {
    searchForm.resetFields()
    setActiveActivityId(null)
    fetchRegistrations()
  }

  // 鎵撳紑瀹℃牳寮圭獥
  const handleAudit = (record, isBatch = false) => {
    setCurrentRecord(record)
    setIsBatchAudit(isBatch)
    auditForm.resetFields()
    setAuditModalVisible(true)
  }

  // 鎻愪氦瀹℃牳
  const handleAuditSubmit = async () => {
    try {
      const values = await auditForm.validateFields()
      
      let res
      if (isBatchAudit) {
        // 鎵归噺瀹℃牳 - 鍚庣鍙傛暟鍚嶆槸 auditStatus
        res = await batchAuditRegistration(selectedRowKeys, values.status, values.remark)
      } else {
        // 鍗曚釜瀹℃牳 - 鍚庣鍙傛暟鍚嶆槸 auditStatus
        res = await auditRegistration(currentRecord.id, values.status, values.remark)
      }

      if (res.code === 200) {
        message.success('瀹℃牳鎴愬姛')
        setAuditModalVisible(false)
        setSelectedRowKeys([])
        if (activeActivityId) {
          fetchRegistrations({ activityId: activeActivityId })
        } else {
          fetchRegistrations()
        }
      }
    } catch (error) {
      console.error('瀹℃牳澶辫触:', error)
    }
  }

  // 鏌ョ湅璇︽儏
  const handleView = (record) => {
    Modal.info({
      title: '鎶ュ悕璇︽儏',
      width: 500,
      content: (
        <div className="registration-detail">
          <p><strong>报名 ID：</strong>{record.id}</p>
          <p><strong>活动 ID：</strong>{record.activityId}</p>
          <p><strong>报名人：</strong>{record.realName || record.username}</p>
          <p><strong>报名时间：</strong>{record.signupTime}</p>
          <p><strong>状态：</strong>{statusMap[record.status]?.text || record.status}</p>
          {record.remark && <p><strong>备注：</strong>{record.remark}</p>}
        </div>
      )
    })
  }

  // 琛ㄦ牸閫夋嫨閰嶇疆
  const rowSelection = {
    selectedRowKeys,
    onChange: (keys) => setSelectedRowKeys(keys),
    getCheckboxProps: (record) => ({
      disabled: record.status !== 'PENDING' && record.status !== 'CANCEL_PENDING' // 仅待审核可批量处理
    })
  }

  // 琛ㄦ牸鍒楀畾涔?
  const columns = [
    {
      title: '鎶ュ悕ID',
      dataIndex: 'id',
      width: 80
    },
    {
      title: '娲诲姩鍚嶇О',
      dataIndex: 'activityName',
      ellipsis: true
    },
    {
      title: '报名人',
      dataIndex: 'realName',
      render: (text, record) => text || record.username
    },
    {
      title: '瀛﹀彿',
      dataIndex: 'studentNo',
      width: 120
    },
    {
      title: '手机号',
      dataIndex: 'phone',
      width: 130
    },
    {
      title: '鎶ュ悕鏃堕棿',
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
      title: '鎿嶄綔',
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
            鏌ョ湅
          </Button>
          {(record.status === 'PENDING' || record.status === 'CANCEL_PENDING') && (
            <>
              <Button 
                type="text" 
                style={{ color: '#52c41a' }}
                icon={<CheckOutlined />} 
                onClick={() => handleAudit({ ...record, auditStatus: 'APPROVED' })}
              >
                閫氳繃
              </Button>
              <Button 
                type="text" 
                danger
                icon={<CloseOutlined />} 
                onClick={() => handleAudit({ ...record, auditStatus: 'REJECTED' })}
              >
                鎷掔粷
              </Button>
            </>
          )}
        </Space>
      )
    }
  ]

  return (
    <div className="audit-manage-container">
      {/* 涓诲唴瀹?*/}
      <main className="manage-main">
        {/* 椤甸潰鏍囬 */}
        <div className="page-header">
          <h2>鎶ュ悕瀹℃牳</h2>
        </div>

        {/* 鎼滅储鏍?*/}
        <div className="search-bar">
          <Form form={searchForm} layout="inline" onFinish={handleSearch}>
            <Form.Item name="activityId">
              <Select placeholder="閫夋嫨娲诲姩" allowClear style={{ width: 200 }}>
                {activities.map(act => (
                  <Option key={act.id} value={act.id}>{act.title}</Option>
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
              <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>鎼滅储</Button>
              <Button onClick={handleReset} style={{ marginLeft: 8 }}>閲嶇疆</Button>
            </Form.Item>
          </Form>
          {selectedRowKeys.length > 0 && (
            <Button 
              type="primary" 
              onClick={() => handleAudit(null, true)}
            >
              鎵归噺瀹℃牳 ({selectedRowKeys.length})
            </Button>
          )}
        </div>

        {/* 鎶ュ悕鍒楄〃 */}
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

      {/* 瀹℃牳寮圭獥 */}
      <Modal
        title={isBatchAudit ? '鎵归噺瀹℃牳' : '瀹℃牳鎶ュ悕'}
        open={auditModalVisible}
        onOk={handleAuditSubmit}
        onCancel={() => setAuditModalVisible(false)}
        width={500}
        okText="纭畾"
        cancelText="鍙栨秷"
      >
        <Form
          form={auditForm}
          layout="vertical"
          style={{ marginTop: 16 }}
          initialValues={{ status: currentRecord?.auditStatus || 'APPROVED' }}
        >
          <Form.Item
            name="status"
            label="瀹℃牳缁撴灉"
            rules={[{ required: true, message: '璇烽€夋嫨瀹℃牳缁撴灉' }]}
          >
            <Select placeholder="璇烽€夋嫨瀹℃牳缁撴灉">
              <Option value={'APPROVED'}>閫氳繃</Option>
              <Option value={'REJECTED'}>鎷掔粷</Option>
            </Select>
          </Form.Item>

          <Form.Item
            name="remark"
            label="瀹℃牳澶囨敞"
          >
            <TextArea rows={3} placeholder="璇疯緭鍏ュ鏍稿娉紙鍙€夛級" maxLength={200} showCount />
          </Form.Item>

          {isBatchAudit && (
            <p style={{ color: '#666' }}>
              鍗冲皢瀹℃牳 {selectedRowKeys.length} 鏉℃姤鍚嶈褰?
            </p>
          )}
        </Form>
      </Modal>
    </div>
  )
}

export default AuditManage
