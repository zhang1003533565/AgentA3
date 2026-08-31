import { useEffect, useState } from 'react'
import { Tag } from 'antd'
import { API_BASE_URL } from '../../config/apiBase'
import './PptTemplatePicker.css'

const resolveThumbnailPath = (thumbnailUrl) => {
  const raw = String(thumbnailUrl || '').trim()
  if (!raw) return ''
  if (raw.startsWith('http://') || raw.startsWith('https://')) return raw
  return raw.startsWith('/') ? `${API_BASE_URL}${raw}` : `${API_BASE_URL}/${raw}`
}

export default function PptTemplatePicker({
  templates = [],
  value = '',
  onChange,
  disabled = false,
}) {
  const [thumbMap, setThumbMap] = useState({})

  useEffect(() => {
    let cancelled = false
    const loadThumbnails = async () => {
      const token = localStorage.getItem('token') || ''
      const entries = await Promise.all(templates.map(async (template) => {
        const url = resolveThumbnailPath(template.thumbnailUrl)
        if (!url) return [template.id, '']
        try {
          const response = await fetch(url, {
            headers: token ? { Authorization: `Bearer ${token}` } : {},
          })
          if (!response.ok) return [template.id, '']
          const blob = await response.blob()
          return [template.id, URL.createObjectURL(blob)]
        } catch {
          return [template.id, '']
        }
      }))
      if (cancelled) {
        entries.forEach(([, objectUrl]) => {
          if (objectUrl) URL.revokeObjectURL(objectUrl)
        })
        return
      }
      setThumbMap(Object.fromEntries(entries.filter(([, objectUrl]) => objectUrl)))
    }
    loadThumbnails()
    return () => {
      cancelled = true
      Object.values(thumbMap).forEach((objectUrl) => {
        if (objectUrl) URL.revokeObjectURL(objectUrl)
      })
    }
  }, [templates])

  if (!templates.length) return null

  return (
    <div className="ppt-template-picker">
      {templates.map((template) => {
        const selected = value === template.id
        const thumb = thumbMap[template.id]
        return (
          <button
            key={template.id}
            type="button"
            className={`ppt-template-picker__card${selected ? ' is-selected' : ''}`}
            disabled={disabled}
            onClick={() => onChange?.(template.id)}
          >
            {thumb ? (
              <img className="ppt-template-picker__thumb" src={thumb} alt={template.name} />
            ) : (
              <div className="ppt-template-picker__thumb" />
            )}
            <div className="ppt-template-picker__meta">
              <span className="ppt-template-picker__name">{template.name}</span>
              {template.default ? <Tag>默认</Tag> : null}
            </div>
            <div className="ppt-template-picker__desc">{template.description}</div>
          </button>
        )
      })}
    </div>
  )
}
