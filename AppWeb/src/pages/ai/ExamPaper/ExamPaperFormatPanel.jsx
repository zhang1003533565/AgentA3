import { Button, Card, Checkbox, Col, Form, InputNumber, Radio, Row, Select, Space, Switch, Typography } from 'antd'
import { ReloadOutlined, UndoOutlined } from '@ant-design/icons'

import {
  SOURCE_LAYOUT_DEFAULTS,
  STUDENT_HEADER_FIELDS,
  buildStudentHeaderInfo,
} from './examPaperPreviewState'

const { Text } = Typography
const TWIPS_PER_CM = 1440 / 2.54
const marginOptions = [
  { value: 'NORMAL', label: '标准' },
  { value: 'NARROW', label: '紧凑' },
  { value: 'WIDE', label: '宽松' },
  { value: 'BINDING', label: '标准装订线' },
  { value: 'CUSTOM', label: '自定义' },
]
const renderModeOptions = [
  { value: 'TEMPLATE', label: '标准模板' },
  { value: 'SIMPLE', label: '简洁模板' },
]

const twipsToCm = (twips) => {
  if (twips === null || twips === undefined || twips === '') return twips
  const number = Number(twips)
  return Number.isFinite(number) ? Number((number / TWIPS_PER_CM).toFixed(2)) : twips
}

const cmToTwips = (cm) => {
  if (cm === null || cm === undefined || cm === '') return cm
  const number = Number(cm)
  return Number.isFinite(number) ? Math.round(number * TWIPS_PER_CM) : cm
}

function ExamPaperFormatPanel({ form, onRestoreDefaults, onApplyPreview, applyLoading = false }) {
  const marginPreset = Form.useWatch(['layout', 'marginPreset'], form)
  const studentInfoVisible = Form.useWatch(['layout', 'studentInfoVisible'], form)
  const studentInfoFields = Form.useWatch(['layout', 'studentInfoFields'], form)
  const studentInfoPreview = buildStudentHeaderInfo({ studentInfoVisible, studentInfoFields })
  const required = [{ required: true, message: '请选择或填写该项' }]

  return (
    <Card
      title="页面格式"
      className="exam-paper-card exam-paper-format-card"
    >
      <Text type="secondary">默认值逐项对应标准模板：A3 横向、装订线、双栏、栏距约 0.75 cm。</Text>
      <div className="exam-paper-format-stack">
        <section className="exam-paper-format-section">
          <Text strong>模板</Text>
          <Form.Item name={['layout', 'renderMode']} rules={required} className="exam-paper-format-control">
            <Radio.Group optionType="button" buttonStyle="solid" options={renderModeOptions} />
          </Form.Item>
        </section>

        <section className="exam-paper-format-section">
          <Text strong>纸张与版式</Text>
          <Row gutter={[12, 8]} className="exam-paper-format-grid">
            <Col xs={12}>
              <Form.Item name={['layout', 'pageSize']} label="纸张" rules={required}>
                <Select options={['A3', 'A4'].map((value) => ({ value, label: value }))} />
              </Form.Item>
            </Col>
            <Col xs={12}>
              <Form.Item name={['layout', 'orientation']} label="方向" rules={required}>
                <Select options={[{ value: 'LANDSCAPE', label: '横向' }, { value: 'PORTRAIT', label: '纵向' }]} />
              </Form.Item>
            </Col>
            <Col xs={12}>
              <Form.Item name={['layout', 'columnsCount']} label="栏数" rules={required}>
                <Select options={[{ value: 1, label: '单栏' }, { value: 2, label: '双栏' }]} />
              </Form.Item>
            </Col>
            <Col xs={12}>
              <Form.Item
                name={['layout', 'columnSpace']}
                label="栏距"
                rules={required}
                getValueProps={(value) => ({ value: twipsToCm(value) })}
                normalize={(value) => cmToTwips(value)}
              >
                <InputNumber min={0} max={5.08} step={0.05} precision={2} addonAfter="cm" className="exam-paper-number" />
              </Form.Item>
            </Col>
            <Col xs={24}>
              <Form.Item name={['layout', 'hasBindingLine']} label="装订线" valuePropName="checked">
                <Switch checkedChildren="启用" unCheckedChildren="关闭" />
              </Form.Item>
            </Col>
            <Col xs={24}>
              <Form.Item name={['layout', 'marginPreset']} label="页边距" rules={required}>
                <Select options={marginOptions} />
              </Form.Item>
            </Col>
            {marginPreset === 'CUSTOM' && ['Top', 'Right', 'Bottom', 'Left'].map((side, index) => (
              <Col xs={12} key={side}>
                <Form.Item
                  name={['layout', `customMargin${side}`]}
                  label={['上边距', '右边距', '下边距', '左边距'][index]}
                  rules={[{ required: true, message: '请输入边距' }]}
                  getValueProps={(value) => ({ value: twipsToCm(value) })}
                  normalize={(value) => cmToTwips(value)}
                >
                  <InputNumber min={0} max={12.7} step={0.1} precision={2} addonAfter="cm" className="exam-paper-number" />
                </Form.Item>
              </Col>
            ))}
          </Row>
        </section>

        <section className="exam-paper-format-section">
          <Text strong>学生信息栏</Text>
          <Form.Item name={['layout', 'studentInfoVisible']} label="显示学生信息栏" valuePropName="checked">
            <Switch checkedChildren="显示" unCheckedChildren="隐藏" />
          </Form.Item>
          {studentInfoVisible !== false && (
            <>
              <Form.Item name={['layout', 'studentInfoFields']} label="字段勾选">
                <Checkbox.Group options={STUDENT_HEADER_FIELDS} className="exam-paper-student-fields" />
              </Form.Item>
              <div className="exam-paper-student-info-preview">
                <Text type="secondary">预览：</Text>
                <Text>{studentInfoPreview || '不显示学生信息栏'}</Text>
              </div>
            </>
          )}
        </section>

        <section className="exam-paper-format-section">
          <Text strong>字号</Text>
          <Row gutter={[12, 8]} className="exam-paper-format-grid">
            <Col xs={12}>
              <Form.Item name={['layout', 'titleFontSize']} label="标题字号" rules={required}>
                <InputNumber min={10} max={120} precision={0} className="exam-paper-number" />
              </Form.Item>
            </Col>
            <Col xs={12}>
              <Form.Item name={['layout', 'subtitleFontSize']} label="副标题字号" rules={required}>
                <InputNumber min={10} max={72} precision={0} className="exam-paper-number" />
              </Form.Item>
            </Col>
            <Col xs={24}>
              <Form.Item name={['layout', 'bodyFontSize']} label="正文字号" rules={required}>
                <InputNumber min={10} max={72} precision={0} className="exam-paper-number" />
              </Form.Item>
            </Col>
          </Row>
          <Text type="secondary">标准字号默认：{SOURCE_LAYOUT_DEFAULTS.titleFontSize / 2} / {SOURCE_LAYOUT_DEFAULTS.subtitleFontSize / 2} / {SOURCE_LAYOUT_DEFAULTS.bodyFontSize / 2} 磅</Text>
        </section>

        <div className="exam-paper-format-actions">
          <Button icon={<UndoOutlined />} onClick={onRestoreDefaults}>恢复默认</Button>
          {onApplyPreview && (
            <Button type="primary" icon={<ReloadOutlined />} loading={applyLoading} onClick={onApplyPreview}>
              应用并重新生成预览
            </Button>
          )}
        </div>
      </div>
    </Card>
  )
}

export default ExamPaperFormatPanel
