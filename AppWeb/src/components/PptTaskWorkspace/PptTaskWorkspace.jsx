import { useCallback, useEffect, useMemo, useState } from 'react'
import { Button, Progress, Space, Tag, Typography } from 'antd'
import { DownloadOutlined, ReloadOutlined } from '@ant-design/icons'
import { getPptTask } from '../../api/ppt'
import { API_BASE_URL } from '../../config/apiBase'
import { PPT_TERMINAL_STATUSES, pptStageLabel } from '../../utils/pptUtils'
import './PptTaskWorkspace.css'

const { Text } = Typography

const statusColor = (status) => {
  if (status === 'completed') return 'success'
  if (status === 'failed' || status === 'timed_out') return 'error'
  if (status === 'cancelled') return 'default'
  return 'processing'
}

export default function PptTaskWorkspace({ seedTask }) {
  const [task, setTask] = useState(seedTask || null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const taskId = task?.taskId || seedTask?.taskId || ''

  const refreshTask = useCallback(async (options = {}) => {
    if (!taskId) return null
    const { silent = false } = options
    if (!silent) setLoading(true)
    setError('')
    try {
      const res = await getPptTask(taskId)
      const payload = res?.data && typeof res.data === 'object' ? res.data : res
      setTask(payload || null)
      return payload
    } catch (err) {
      setError(err.message || '任务状态查询失败')
      return null
    } finally {
      if (!silent) setLoading(false)
    }
  }, [taskId])

  useEffect(() => {
    setTask(seedTask || null)
  }, [seedTask])

  useEffect(() => {
    if (!taskId) return undefined
    let active = true
    let timer = null
    const poll = async () => {
      if (!active) return
      const payload = await refreshTask({ silent: true })
      if (!active || !payload) return
      if (PPT_TERMINAL_STATUSES.has(String(payload.status || '')) && timer) {
        window.clearInterval(timer)
        timer = null
      }
    }
    poll()
    timer = window.setInterval(poll, 2500)
    return () => {
      active = false
      if (timer) window.clearInterval(timer)
    }
  }, [taskId, refreshTask])

  const previews = useMemo(() => (
    Array.isArray(task?.previews) ? task.previews.slice(0, 6) : []
  ), [task?.previews])

  const downloadPptx = useCallback(async () => {
    if (!taskId) return
    const token = localStorage.getItem('token') || ''
    const url = `${API_BASE_URL}/api/app/ai/ppt/tasks/${encodeURIComponent(taskId)}/files/pptx`
    const response = await fetch(url, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
    if (!response.ok) {
      throw new Error(`PPTX 下载失败(${response.status})`)
    }
    const blob = await response.blob()
    const objectUrl = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = objectUrl
    anchor.download = `${task?.sourceName || task?.title || 'presentation'}.pptx`
    anchor.click()
    URL.revokeObjectURL(objectUrl)
  }, [task?.sourceName, task?.title, taskId])

  if (!taskId) return null

  const status = String(task?.status || seedTask?.status || 'queued')
  const progress = Number(task?.progress ?? seedTask?.progress ?? 0)
  const message = String(task?.message || seedTask?.message || '任务已创建，正在排队处理。')
  const stage = task?.stage || seedTask?.stage

  return (
    <div className="ppt-task-workspace">
      <div className="ppt-task-workspace__header">
        <div>
          <Text strong>{task?.sourceName || task?.title || seedTask?.title || 'PPT 生成任务'}</Text>
          <div className="ppt-task-workspace__meta">
            <Tag color={statusColor(status)}>{status}</Tag>
            <Tag>{pptStageLabel(stage)}</Tag>
            {task?.totalSlides ? <Tag>{task.totalSlides} 页</Tag> : null}
            {task?.templateId ? <Tag>模板 {task.templateId}</Tag> : null}
          </div>
        </div>
        <Space>
          <Button size="small" icon={<ReloadOutlined />} loading={loading} onClick={() => refreshTask()}>
            刷新
          </Button>
          {status === 'completed' ? (
            <Button size="small" type="primary" icon={<DownloadOutlined />} onClick={downloadPptx}>
              下载 PPTX
            </Button>
          ) : null}
        </Space>
      </div>

      <Progress percent={Math.max(0, Math.min(100, progress))} status={status === 'failed' ? 'exception' : undefined} />
      <div className="ppt-task-workspace__message">{message}</div>
      {error ? <Text type="danger">{error}</Text> : null}
      {task?.error?.message ? <Text type="danger">{task.error.message}</Text> : null}

      {previews.length ? (
        <div className="ppt-task-workspace__previews">
          {previews.map((item) => (
            <figure key={`${item.slideIndex || item.index}-${item.storageKey || item.url}`} className="ppt-task-workspace__preview">
              {item.url ? <img src={item.url} alt={`第 ${item.slideIndex || item.index} 页`} /> : null}
              <figcaption>第 {item.slideIndex || item.index} 页</figcaption>
            </figure>
          ))}
        </div>
      ) : null}
    </div>
  )
}
