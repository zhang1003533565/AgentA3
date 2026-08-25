import json
import logging
import os
import re
from typing import Any, Optional

import httpx
from fastapi import APIRouter, Header, HTTPException
from pydantic import BaseModel, Field

from app.model_providers.runtime_config import build_llm_runtime_config


class CourseGenerateRequest(BaseModel):
    prompt: str = Field(..., min_length=1, description="用户输入的课程生成要求")


class CourseSection(BaseModel):
    title: str
    content: str


class CourseGenerateResponse(BaseModel):
    chapterTitle: str
    sections: list[CourseSection]


logger = logging.getLogger(__name__)
course_router = APIRouter(tags=["course"])


def _clean_llm_json_text(raw_content: str) -> str:
    cleaned = str(raw_content or "").strip()
    if not cleaned:
        return ""

    cleaned = cleaned.strip("`\n\r \t")
    cleaned = re.sub(r"^```\s*", "", cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r"^json\s+and\s+", "", cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r"^json\s*", "", cleaned, flags=re.IGNORECASE)
    cleaned = cleaned.strip()
    if cleaned.startswith("```"):
        cleaned = re.sub(r"^```\s*", "", cleaned, flags=re.IGNORECASE)
    return cleaned.strip("`\n\r \t")


def _fallback_course_generation(prompt: str) -> dict[str, Any]:
    return {
        "chapterTitle": "第一章：Python基础入门",
        "sections": [
            {
                "title": "1.1 变量与数据类型",
                "content": f"这是根据要求 “{prompt}” 生成的测试内容：变量和数据类型是编程的基础，掌握它们可以帮助学生理解程序如何存储和处理信息。",
            },
        ],
    }


async def _generate_course_json(
    prompt: str,
    provider: Optional[str] = None,
    base_url: Optional[str] = None,
    api_key: Optional[str] = None,
    model: Optional[str] = None,
) -> dict[str, Any]:
    effective_provider = (provider or os.getenv("AI_PROVIDER", "openai_compatible") or "openai_compatible").strip()
    effective_base_url = (base_url or os.getenv("AI_BASE_URL", "")).strip()
    effective_api_key = (api_key or os.getenv("AI_API_KEY", "")).strip()
    effective_model = (model or os.getenv("AI_MODEL", "gpt-4o-mini") or "gpt-4o-mini").strip()

    runtime_config = build_llm_runtime_config(
        provider=effective_provider,
        base_url=effective_base_url,
        api_key=effective_api_key,
        model=effective_model,
    )
    if runtime_config is None or not runtime_config.base_url or not runtime_config.api_key:
        return _fallback_course_generation(prompt)

    payload = {
        "model": effective_model,
        "temperature": 0.2,
        "response_format": {"type": "json_object"},
        "messages": [
            {
                "role": "system",
                "content": (
                    "你是一个只能输出 JSON 的机器。禁止输出 Markdown 代码块、反引号或任何解释性文字。"
                    "必须严格输出包含 chapterTitle 和 sections 数组的纯 JSON，且必须且只能生成 1 个章节。"
                    "不能输出多个章节，也不能输出额外字段、说明文字、代码块或注释。"
                    "必须且只能输出如下结构：{\"chapterTitle\":\"字符串\",\"sections\":[{\"title\":\"字符串\",\"content\":\"字符串\"}]}"
                    "注意：只能返回一个章节对象，不能生成多个章节。"
                ),
            },
            {
                "role": "user",
                "content": (
                    "根据以下教学要求生成课程章节内容：\n"
                    f"{prompt}\n\n"
                    "请返回纯 JSON，且只包含 chapterTitle 与 sections。"
                    "chapterTitle 是章节标题，sections 必须是长度为 1 的数组，数组元素只有一个 section，且 section 必须包含 title 和 content。"
                    "必须且只能生成 1 个章节，不能输出多个章节，也不能输出任何解释性文字。"
                ),
            },
        ],
    }

    async with httpx.AsyncClient(timeout=180.0) as client:
        response = await client.post(
            runtime_config.base_url.rstrip("/") + "/chat/completions",
            headers={
                "Authorization": f"Bearer {runtime_config.api_key}",
                "Content-Type": "application/json",
            },
            json=payload,
        )
        response.raise_for_status()
        data = response.json()
        content = data["choices"][0]["message"]["content"]

        raw_content = str(content or "").strip()
        cleaned_content = _clean_llm_json_text(raw_content)
        if not cleaned_content:
            logger.error("AI response content is empty. Raw content: %s", raw_content)
            return _fallback_course_generation(prompt)

        try:
            article = json.loads(cleaned_content)
        except json.JSONDecodeError as exc:
            logger.error(
                "AI response is not valid JSON after cleaning. Raw content: %s",
                raw_content,
                exc_info=True,
            )
            return _fallback_course_generation(prompt)

        if not isinstance(article, dict):
            logger.error(
                "AI response is not a JSON object. Type=%s. Raw content: %s",
                type(article).__name__,
                raw_content,
            )
            return _fallback_course_generation(prompt)

        chapter_title = str(article.get("chapterTitle") or "课程章节").strip()
        sections = article.get("sections") or []
        if not isinstance(sections, list):
            logger.error(
                "AI response field 'sections' is not an array. Actual type=%s. Raw content: %s",
                type(sections).__name__,
                raw_content,
            )
            return _fallback_course_generation(prompt)

        sanitized_sections = []
        for section in sections:
            if not isinstance(section, dict):
                continue
            sanitized_sections.append({
                "title": str(section.get("title") or "新小节").strip(),
                "content": str(section.get("content") or "").strip(),
            })

        if not sanitized_sections:
            logger.error("AI response 'sections' is empty after parse. Raw content: %s", raw_content)
            return _fallback_course_generation(prompt)

        # 严格保持单章节返回格式，每次请求只返回一个章节对象
        return {
            "chapterTitle": chapter_title,
            "sections": sanitized_sections[:1],
        }


@course_router.post("/generate")
async def generate_course(
    request: CourseGenerateRequest,
    x_ai_provider: Optional[str] = Header(default=None, alias="X-AI-Provider"),
    x_ai_base_url: Optional[str] = Header(default=None, alias="X-AI-Base-Url"),
    x_ai_api_key: Optional[str] = Header(default=None, alias="X-AI-Api-Key"),
    x_ai_model: Optional[str] = Header(default=None, alias="X-AI-Model"),
) -> CourseGenerateResponse:
    prompt = request.prompt.strip()
    if not prompt:
        raise HTTPException(status_code=400, detail="prompt 不能为空")

    runtime_base_url = (x_ai_base_url or os.getenv("AI_BASE_URL", "")).strip()
    runtime_api_key = (x_ai_api_key or os.getenv("AI_API_KEY", "")).strip()

    if not runtime_base_url or not runtime_api_key:
        return CourseGenerateResponse(**_fallback_course_generation(prompt))

    try:
        result = await _generate_course_json(
            prompt,
            provider=x_ai_provider,
            base_url=x_ai_base_url,
            api_key=x_ai_api_key,
            model=x_ai_model,
        )
        return CourseGenerateResponse(**result)
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"AI model request failed: {exc}") from exc


__all__ = ["course_router"]
