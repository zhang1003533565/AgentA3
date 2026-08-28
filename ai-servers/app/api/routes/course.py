import json
import logging
import os
import re
from typing import Any, Optional

import httpx
from fastapi import APIRouter, Header, HTTPException
from pydantic import AliasChoices, BaseModel, Field

from app.model_providers.runtime_config import build_llm_runtime_config


class CourseGenerateRequest(BaseModel):
    prompt: str = Field(..., min_length=1, description="用户输入的课程生成要求")
    estimated_minutes: int = Field(
        default=0,
        ge=0,
        validation_alias=AliasChoices("estimated_minutes", "estimatedMinutes"),
    )


class CourseSection(BaseModel):
    title: str
    content: str


class CourseGenerateResponse(BaseModel):
    chapterTitle: str
    estimated_minutes: int = Field(default=0, ge=0)
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


def _fallback_course_generation(prompt: str, estimated_minutes: int = 0) -> dict[str, Any]:
    return {
        "chapterTitle": "第一章：Python基础入门",
        "estimated_minutes": estimated_minutes,
        "sections": [
            {
                "title": "1.1 变量与数据类型",
                "content": f"这是根据要求 “{prompt}” 生成的测试内容：变量和数据类型是编程的基础，掌握它们可以帮助学生理解程序如何存储和处理信息。",
            },
        ],
    }


async def _generate_course_json(
    prompt: str,
    estimated_minutes: int = 0,
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
        if effective_api_key:
            raise HTTPException(status_code=502, detail="AI model runtime configuration is incomplete")
        return _fallback_course_generation(prompt, estimated_minutes)

    payload = {
        "model": effective_model,
        "temperature": 0.2,
        "response_format": {"type": "json_object"},
        "messages": [
            {
                "role": "system",
                "content": (
                    "你是一名拥有丰富教学与科研经验的资深高校讲师。请把用户要求扩展为一章真正可供大学生学习的完整教材内容，"
                    "讲解必须严谨、具体、循序渐进，不能敷衍，不能输出一两句的占位符。"
                    "本章 content 的中文字数必须不少于 800 字，且必须包含：核心概念及定义、原理或推导、至少一个具体可运行的代码示例（适用时使用 Python 或用户指定语言）、"
                    "代码逐段解释、至少两个真实应用场景、常见错误或易混淆点，以及至少 3 道课后思考题。"
                    "代码示例必须有实际逻辑，严禁使用‘此处略’‘内容待补充’‘请自行实现’等占位表达。"
                    "只能输出一个章节，并严格输出纯 JSON；禁止 Markdown 代码块、反引号、注释或任何 JSON 外的解释文字。"
                    "JSON 必须包含 chapterTitle、estimated_minutes 和 sections 字段。estimated_minutes 必须是非负整数，单位为分钟；"
                    "estimated_minutes 必须直接使用请求中的 estimated_minutes；如果请求没有传入该字段，必须为 0。"
                    "sections 必须是数组，至少包含一个对象，每个对象只能包含 title 和 content 字段。"
                ),
            },
            {
                "role": "user",
                "content": (
                    "根据以下教学要求生成课程章节内容：\n"
                    f"{prompt}\n\n"
                    f"请按系统要求返回一个完整、详细、至少 800 字的章节 JSON。请求中的 estimated_minutes 为 {estimated_minutes}，请原值返回，不能省略。"
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
            raise HTTPException(status_code=502, detail="AI response content is empty")

        try:
            article = json.loads(cleaned_content)
        except json.JSONDecodeError as exc:
            logger.error(
                "AI response is not valid JSON after cleaning. Raw content: %s",
                raw_content,
                exc_info=True,
            )
            raise HTTPException(status_code=502, detail="AI response is not valid JSON") from exc

        if not isinstance(article, dict):
            logger.error(
                "AI response is not a JSON object. Type=%s. Raw content: %s",
                type(article).__name__,
                raw_content,
            )
            raise HTTPException(status_code=502, detail="AI response is not a JSON object")

        chapter_title = str(article.get("chapterTitle") or "课程章节").strip()
        estimated_minutes_raw = estimated_minutes
        try:
            estimated_minutes = int(estimated_minutes_raw)
        except (TypeError, ValueError):
            estimated_minutes = 0
        if estimated_minutes < 0:
            estimated_minutes = 0

        sections = article.get("sections") or []
        if not isinstance(sections, list):
            logger.error(
                "AI response field 'sections' is not an array. Actual type=%s. Raw content: %s",
                type(sections).__name__,
                raw_content,
            )
            raise HTTPException(status_code=502, detail="AI response field 'sections' is not an array")

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
            raise HTTPException(status_code=502, detail="AI response contains no usable sections")

        content_length = sum(len(section["content"]) for section in sanitized_sections)
        if content_length < 800:
            logger.error("AI response content is too short: %s characters", content_length)
            raise HTTPException(status_code=502, detail="AI response content must contain at least 800 characters")

        # 严格保持单章节返回格式，每次请求只返回一个章节对象
        return {
            "chapterTitle": chapter_title,
            "estimated_minutes": estimated_minutes,
            "sections": sanitized_sections,
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

    if not runtime_api_key:
        return CourseGenerateResponse(**_fallback_course_generation(prompt, request.estimated_minutes))

    try:
        result = await _generate_course_json(
            prompt,
            estimated_minutes=request.estimated_minutes,
            provider=x_ai_provider,
            base_url=x_ai_base_url,
            api_key=x_ai_api_key,
            model=x_ai_model,
        )
        return CourseGenerateResponse(**result)
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"AI model request failed: {exc}") from exc


__all__ = ["course_router"]
