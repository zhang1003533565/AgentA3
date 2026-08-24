export const parseFacilityStory = (value) => {
  if (!value || !String(value).trim()) {
    return { content: '', image: '' }
  }

  if (typeof value === 'object') {
    return {
      content: String(value.content || '').trim(),
      image: String(value.image || '').trim(),
    }
  }

  const text = String(value).trim()
  try {
    const parsed = JSON.parse(text)
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      return {
        content: String(parsed.content || '').trim(),
        image: String(parsed.image || '').trim(),
      }
    }
  } catch {
    return { content: text, image: '' }
  }

  return { content: text, image: '' }
}

export const serializeFacilityStory = ({ content, image } = {}) => {
  const nextStory = {
    content: String(content || '').trim(),
    image: String(image || '').trim(),
  }
  return nextStory.content || nextStory.image ? JSON.stringify(nextStory) : ''
}

