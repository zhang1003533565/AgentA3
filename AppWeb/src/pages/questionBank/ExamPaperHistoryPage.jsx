import ExamPaperHistory from '../ai/ExamPaper/ExamPaperHistory'
import '../ai/ExamPaper/ExamPaper.css'

export default function ExamPaperHistoryPage() {
  return (
    <div className="exam-paper-page">
      <ExamPaperHistory />
    </div>
  )
}
import { Typography } from 'antd'
import ExamPaperHistory from '../ai/ExamPaper/ExamPaperHistory'
import '../ai/ExamPaper/ExamPaper.css'

const { Title, Paragraph } = Typography

export default function ExamPaperHistoryPage() {
  return (
    <div className="exam-paper-page">
      <section className="exam-paper-hero">
        <span className="exam-paper-kicker">EXAM PAPER</span>
        <Title level={1}>生成的试卷</Title>
        <Paragraph>查看、预览和下载已经生成的试卷。</Paragraph>
      </section>
      <ExamPaperHistory />
    </div>
  )
}
