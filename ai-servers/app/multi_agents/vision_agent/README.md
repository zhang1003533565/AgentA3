# vision_agent

图片识别智能体由 `recognize_image_tool` 调用，专门处理聊天过程中上传的图片。

它绑定“视觉理解”模型，与负责文生图的 `image_agent` 分离，避免把识图请求错误路由到图片生成模型。
