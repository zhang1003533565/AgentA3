import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Button, Popconfirm, Space, Table, Tag, message } from 'antd'
import { getActivityDetail } from '../../../api/activity'
import { getRegistrationList, removeRegistrationByManager } from '../../../api/registration'
import '../ActivityManage/ActivityManage.css'
import { getPhase, parseImageList, statusMap } from '../activityHelpers'

function ActivityDetail() {
  const navigate = useNavigate()
  const { id } = useParams()
  const [loading, setLoading] = useState(false)
  const [record, setRecord] = useState(null)
  const [registrationRows, setRegistrationRows] = useState([])

  useEffect(() => {
    const run = async () => {
      setLoading(true)
      try {
        const [detailRes, registrationRes] = await Promise.all([
          getActivityDetail(id),
          getRegistrationList(id, { page: 1, size: 999 }),
        ])
        setRecord(detailRes?.data || null)
        setRegistrationRows(registrationRes?.data?.records || [])
      } finally {
        setLoading(false)
      }
    }

    run()
  }, [id])

  const handleRemoveRegistration = async (registrationId) => {
    try {
      await removeRegistrationByManager(registrationId)
      setRegistrationRows((rows) => rows.filter((item) => item.id !== registrationId))
      setRecord((prev) => (prev ? { ...prev, currentPeople: Math.max(0, (prev.currentPeople || 0) - 1) } : prev))
      message.success('已移除报名人')
    } catch (error) {
      message.error(error?.message || '移除失败')
    }
  }

  const phase = record ? getPhase(record) : null
  const galleryImages = parseImageList(record?.images)

  return (
    <div className="activity-manage-container">
      <main className="manage-main">
        <div className="page-header">
          <h2>活动详情</h2>
        </div>

        <div className="search-bar" style={{ display: 'block' }}>
          <div className="activity-detail">
            {record?.coverImage ? (
              <p>
                <strong>封面：</strong>
                <div style={{ marginTop: 12 }}>
                  <img
                    src={record.coverImage}
                    alt="活动封面"
                    style={{ width: 280, height: 160, objectFit: 'cover', borderRadius: 12, border: '1px solid #e5e7eb' }}
                  />
                </div>
              </p>
            ) : null}
            <p><strong>标题：</strong>{record?.title || '-'}</p>
            <p><strong>分类：</strong>{record?.category?.categoryName || '-'}</p>
            <p><strong>地点：</strong>{record?.location || '-'}</p>
            <p><strong>人数：</strong>{record?.currentPeople || 0}/{record?.maxPeople || 0}</p>
            <p><strong>活动时间：</strong>{record?.startTime || '-'} 至 {record?.endTime || '-'}</p>
            <p><strong>报名时间：</strong>{record?.signupStartTime || '-'} 至 {record?.signupEndTime || '-'}</p>
            <p><strong>联系人：</strong>{record?.contactName || '-'} ({record?.contactPhone || '-'})</p>
            <p><strong>活动阶段：</strong>{phase ? <Tag color={phase.color}>{phase.text}</Tag> : '-'}</p>
            <p><strong>系统状态：</strong>{record ? <Tag color={statusMap[record.status]?.color}>{statusMap[record.status]?.text}</Tag> : '-'}</p>
            <p><strong>详情：</strong>{record?.content || '-'}</p>
            {galleryImages.length ? (
              <div style={{ marginTop: 20 }}>
                <p><strong>活动图片：</strong></p>
                <div className="activity-image-grid">
                  {galleryImages.map((url, index) => (
                    <div key={`${url}-${index}`} className={`activity-image-card ${record?.coverImage === url ? 'cover' : ''}`}>
                      <img src={url} alt={`活动图片${index + 1}`} />
                    </div>
                  ))}
                </div>
              </div>
            ) : null}
          </div>

          <Space style={{ marginTop: 16 }}>
            <Button type="primary" onClick={() => navigate(`/activity/${id}/edit`)}>
              编辑活动
            </Button>
            <Button onClick={() => navigate('/activity/manage')}>返回列表</Button>
          </Space>
        </div>

        <div className="search-bar" style={{ display: 'block' }}>
          <div style={{ marginBottom: 16, fontWeight: 600 }}>
            报名名单
          </div>
          <Table
            rowKey="id"
            loading={loading}
            dataSource={registrationRows}
            pagination={false}
            locale={{ emptyText: '暂无报名记录' }}
            columns={[
              {
                title: '姓名',
                dataIndex: 'realName',
                render: (text, item) => text || item.username || '-',
              },
              {
                title: '学号/编号',
                dataIndex: 'personalNumber',
                render: (text) => text || '-',
              },
              {
                title: '手机号',
                dataIndex: 'phone',
                render: (text) => text || '-',
              },
              {
                title: '报名时间',
                dataIndex: 'signupTime',
                render: (text) => text || '-',
              },
              {
                title: '操作',
                key: 'action',
                width: 120,
                render: (_, item) => (
                  <Popconfirm title="确定移除该报名人吗？" onConfirm={() => handleRemoveRegistration(item.id)}>
                    <Button type="text" danger>
                      移除
                    </Button>
                  </Popconfirm>
                ),
              },
            ]}
          />
        </div>
      </main>
    </div>
  )
}

export default ActivityDetail
