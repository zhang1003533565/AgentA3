from app.image_generation.qwen_provider import QwenImageProvider
from app.models.image_generation import ImageGenerationRequest


def _request() -> ImageGenerationRequest:
    return ImageGenerationRequest(
        prompt="Python 循环思维导图",
        model="qwen-image-plus",
        baseUrl="https://example.com",
        apiKey="test-key",
    )


def _response_for(result):
    provider = QwenImageProvider()
    return provider._build_response(
        "provider-task",
        {"output": {"task_status": "SUCCEEDED", "results": [result]}},
        request=_request(),
        task_id="local-task",
        mode="single",
    )


def test_image_item_propagates_explicit_content_type():
    response = _response_for({
        "url": "https://example.com/generated/image",
        "content_type": "image/webp; charset=binary",
    })

    assert response.images[0].contentType == "image/webp"


def test_image_item_infers_content_type_from_known_url_extension():
    response = _response_for({
        "url": "https://example.com/generated/image.webp?signature=redacted",
    })

    assert response.images[0].contentType == "image/webp"


def test_image_item_does_not_fabricate_png_for_unknown_url():
    response = _response_for({
        "url": "https://example.com/generated/image",
    })

    assert response.images[0].contentType == ""
