# Model Providers

模型服务商的运行时代码统一放在这里。

- `base.py`：通用模型服务商接口
- `deepseek/`：DeepSeek 兼容 OpenAI API 的实现
- `xiaomi/`：小米 MiMo OpenAI API 格式实现，推荐 `base-url=https://api.xiaomimimo.com/v1`、`model=mimo-v2.5-pro`

后续新增 OpenAI、Qwen、Ollama 等 provider 时，也按一服务商一目录放在这里。
