import { Button, Card, Col, Form, Input, InputNumber, Radio, Row, Select, Space, Switch, Typography } from 'antd'
import { UndoOutlined } from '@ant-design/icons'

import { SOURCE_LAYOUT_DEFAULTS } from './examPaperPreviewState'

const { Text } = Typography
const marginOptions = [
  { value: 'NORMAL', label: '普通' },
  { value: 'NARROW', label: '窄' },
  { value: 'WIDE', label: '宽' },
  { value: 'BINDING', label: '源码装订线' },
  { value: 'CUSTOM', label: '自定义' },
]

function ExamPaperFormatPanel({ form, onRestoreDefaults }) {
  const marginPreset = Form.useWatch(['layout', 'marginPreset'], form)

  return (
    <Card
      title="页面格式"
      className="exam-paper-card"
      extra={<Button icon={<UndoOutlined />} onClick={onRestoreDefaults}>恢复源码默认值</Button>}
    >
      <Text type="secondary">默认值逐项对应源码模板：A3 横向、装订线、双栏、栏距 425 twips。</Text>
      <Row gutter={[16, 4]} className="exam-paper-format-grid">
        <Col xs={24} md={12} lg={6}>
          <Form.Item name={['layout', 'renderMode']} label="生成模式" rules={[{ required: true }]}>
            <Radio.Group optionType="button" buttonStyle="solid" options={[{ value: 'TEMPLATE', label: '源码模板' }, { value: 'SIMPLE', label: '简单模式' }]} />
          </Form.Item>
        </Col>
        <Col xs={12} md={6} lg={3}><Form.Item name={['layout', 'pageSize']} label="纸张"><Select options={['A3', 'A4', 'B4'].map((value) => ({ value, label: value }))} /></Form.Item></Col>
        <Col xs={12} md={6} lg={4}><Form.Item name={['layout', 'orientation']} label="方向"><Select options={[{ value: 'LANDSCAPE', label: '横向' }, { value: 'PORTRAIT', label: '纵向' }]} /></Form.Item></Col>
        <Col xs={12} md={6} lg={4}><Form.Item name={['layout', 'columnsCount']} label="栏数"><Select options={[{ value: 1, label: '单栏' }, { value: 2, label: '双栏' }]} /></Form.Item></Col>
        <Col xs={12} md={6} lg={4}><Form.Item name={['layout', 'columnSpace']} label="栏距（twips）" rules={[{ required: true }]}><InputNumber min={0} max={2880} precision={0} className="exam-paper-number" /></Form.Item></Col>
        <Col xs={12} md={6} lg={3}><Form.Item name={['layout', 'hasBindingLine']} label="装订线" valuePropName="checked"><Switch checkedChildren="启用" unCheckedChildren="关闭" /></Form.Item></Col>
        <Col xs={24} md={12} lg={8}><Form.Item name={['layout', 'marginPreset']} label="页边距"><Select options={marginOptions} /></Form.Item></Col>
        {marginPreset === 'CUSTOM' && ['Top', 'Right', 'Bottom', 'Left'].map((side, index) => (
          <Col xs={12} md={6} lg={4} key={side}>
            <Form.Item
              name={['layout', `customMargin${side}`]}
              label={`自定义${['上', '右', '下', '左'][index]}边距（twips）`}
              rules={[{ required: true, message: '请输入边距' }]}
            >
              <InputNumber min={0} max={7200} precision={0} className="exam-paper-number" />
            </Form.Item>
          </Col>
        ))}
        <Col span={24}><Form.Item name={['layout', 'headerInfo']} label="页眉信息"><Input maxLength={300} showCount /></Form.Item></Col>
        <Col xs={24} md={8}><Form.Item name={['layout', 'titleFontSize']} label="标题字号（半磅）"><InputNumber min={10} max={120} precision={0} className="exam-paper-number" /></Form.Item></Col>
        <Col xs={24} md={8}><Form.Item name={['layout', 'subtitleFontSize']} label="副标题字号（半磅）"><InputNumber min={10} max={72} precision={0} className="exam-paper-number" /></Form.Item></Col>
        <Col xs={24} md={8}><Form.Item name={['layout', 'bodyFontSize']} label="正文字号（半磅）"><InputNumber min={10} max={72} precision={0} className="exam-paper-number" /></Form.Item></Col>
      </Row>
      <Space size="small"><Text type="secondary">源码字号默认：</Text><Text>{SOURCE_LAYOUT_DEFAULTS.titleFontSize / 2} / {SOURCE_LAYOUT_DEFAULTS.subtitleFontSize / 2} / {SOURCE_LAYOUT_DEFAULTS.bodyFontSize / 2} 磅</Text></Space>
    </Card>
  )
}

export default ExamPaperFormatPanel
