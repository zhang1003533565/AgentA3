export const statusMap = {
  DRAFT: { text: '未发布', color: 'default' },
  PUBLISHED: { text: '已发布', color: 'green' },
  COMPLETED: { text: '已结束', color: 'blue' },
}

export const parseTime = (value) => (value ? new Date(String(value).replace(' ', 'T')) : null)

export const getPhase = (record) => {
  if (record?.status === 'DRAFT') {
    return { text: '未发布', color: 'default' }
  }

  const now = new Date()
  const startTime = parseTime(record?.startTime)
  const endTime = parseTime(record?.endTime)

  if (endTime && now >= endTime) {
    return { text: '已结束', color: 'blue' }
  }
  if (startTime && now >= startTime) {
    return { text: '进行中', color: 'green' }
  }
  return { text: '报名中', color: 'gold' }
}

export const toDateTimeInput = (value) => {
  if (!value) return undefined
  return String(value).replace(' ', 'T').slice(0, 16)
}

export const toBackendDateTime = (value) => {
  if (!value) return null
  const withoutT = value.replace('T', ' ')
  return withoutT.match(/:\d{2}:\d{2}$/) ? withoutT : `${withoutT}:00`
}

export const parseImageList = (images) => {
  if (Array.isArray(images)) return images.filter(Boolean)
  if (!images) return []
  if (typeof images === 'string') {
    try {
      const parsed = JSON.parse(images)
      return Array.isArray(parsed) ? parsed.filter(Boolean) : []
    } catch (error) {
      return []
    }
  }
  return []
}
