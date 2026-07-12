export const getExamPaperPublishState = (published) => (
  published
    ? { label: '已发布', color: 'green' }
    : { label: '未发布', color: 'default' }
)

export const getExamPaperPublishAction = (published) => (
  published
    ? {
        label: '取消发布',
        successMessage: '试卷已取消发布',
        confirmTitle: '确认取消发布？',
        confirmDescription: '取消后新用户将无法开始答题，已有进行中的答题不受影响。',
      }
    : {
        label: '发布到 App',
        successMessage: '试卷已发布到 App',
        confirmTitle: null,
        confirmDescription: null,
      }
)

export const getPublishRefreshArgs = (pagination, keyword) => [
  pagination.current,
  pagination.pageSize,
  keyword.trim(),
]
