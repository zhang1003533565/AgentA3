import { Typography } from 'antd'
import ExamPaperCreate from '../ai/ExamPaper/ExamPaperCreate'
import '../ai/ExamPaper/ExamPaper.css'

const { Title, Paragraph } = Typography

export default function ExamPaperCreatePage() {
  return (
    <div className="exam-paper-page">
      <section className="exam-paper-hero">
        <span className="exam-paper-kicker">EXAM PAPER</span>
        <Title level={1}>试卷生成</Title>
        <Paragraph>从现有题库手工选题或按规则随机组卷。</Paragraph>
      </section>
      <ExamPaperCreate />
    </div>
  )
}
