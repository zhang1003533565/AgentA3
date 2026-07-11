import { Tabs, Typography } from 'antd'
import ExamPaperCreate from './ExamPaperCreate'
import './ExamPaper.css'

const { Title } = Typography

function ExamPaper({ onCreated }) {
  return (
    <div className="exam-paper-page">
      <section className="exam-paper-hero">
        <span className="exam-paper-kicker">EXAM PAPER</span>
        <Title level={1}>试卷管理</Title>
        <p>从现有题库手工选题或按规则随机组卷，并在保存前调整题序与分值。</p>
      </section>

      <Tabs
        className="exam-paper-tabs"
        defaultActiveKey="create"
        items={[{
          key: 'create',
          label: '创建试卷',
          children: <ExamPaperCreate onCreated={onCreated} />,
        }]}
      />
    </div>
  )
}

export default ExamPaper
