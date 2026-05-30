# image_agent

`image_agent` 是图片生成智能体，目标是把用户的自然语言需求、课程知识点、RAG 证据和用户画像转化为可执行的图片生成任务。

## 当前目标

图片智能体需要从“只生成图片提示词”升级为完整的文生图能力：

- 单张文生图：输入一个主题或提示词，返回一张生成图片。
- 批量文生图：一次生成多张图片，支持同一提示词多图变体，也支持多条提示词批量任务。
- 结果回传：返回图片 `url` 或 `base64`，并包含每张图片的生成状态。
- 任务状态：长耗时任务返回 `taskId`，支持前端轮询生成进度。
- 参数保留：保留并回传最终提示词、风格、尺寸、数量、种子、负向提示词等信息。

## 建议接口

后续 FastAPI 可补充以下接口：

- `POST /internal/images/generate`: 单张文生图。
- `POST /internal/images/batch`: 批量文生图。
- `GET /internal/images/tasks/{taskId}`: 查询任务状态。

Java 后端可对应转发为：

- `POST /api/ai/images/generate`
- `POST /api/ai/images/batch`
- `GET /api/ai/images/tasks/{taskId}`

## 请求示例

```json
{
  "prompt": "为操作系统进程调度生成一张课堂教学插图",
  "style": "教学插画",
  "size": "1024x1024",
  "count": 4,
  "seed": 123456,
  "negativePrompt": "低清晰度、文字乱码、人物畸形"
}
```

## 响应示例

```json
{
  "taskId": "img_20260530_0001",
  "mode": "batch",
  "status": "success",
  "prompt": "一张清晰的教学插画，展示操作系统进程调度...",
  "style": "教学插画",
  "size": "1024x1024",
  "count": 4,
  "seed": 123456,
  "negativePrompt": "低清晰度、文字乱码、人物畸形",
  "images": [
    {
      "index": 0,
      "url": "https://example.com/images/0.png",
      "base64": "",
      "status": "success",
      "seed": 123456,
      "errorMessage": ""
    }
  ],
  "message": "生成完成"
}
```

## 王敏当前负责范围

优先完成图片智能体目标定义、Qwen 图片模型调用方案、单张生成、批量生成、结果结构和前端展示协议。后续再与后端同学对接 Java 转发接口，与 APP 端对接文生图页面。
