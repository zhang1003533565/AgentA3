from pathlib import Path
import zipfile


def test_windows_pptx_tasks_use_docker_by_default(monkeypatch):
    from app.ppt_generation import presenton_html_renderer as renderer

    monkeypatch.setattr(renderer.os, "name", "nt")
    monkeypatch.delenv("PPTX_EXPORT_BACKEND", raising=False)

    assert renderer._use_docker_pptx_export(True) is True
    assert renderer._use_docker_pptx_export(False) is False


def test_windows_local_backend_does_not_silently_use_low_fidelity_export(monkeypatch):
    from app.ppt_generation import presenton_html_renderer as renderer

    monkeypatch.setattr(renderer.os, "name", "nt")
    monkeypatch.setenv("PPTX_EXPORT_BACKEND", "local")

    assert renderer._use_docker_pptx_export(True) is False


def test_windows_native_export_writes_editable_pptx(tmp_path):
    from app.ppt_generation import presenton_html_renderer as renderer

    path = tmp_path / "native.pptx"
    renderer._export_windows_native_pptx(
        [{
            "ui": {
                "background": "#ffffff",
                "components": [{
                    "type": "text",
                    "name": "title",
                    "position": {"x": 80, "y": 80},
                    "size": {"width": 600, "height": 80},
                    "font": {"size": 36, "color": "#111827"},
                    "text": "本地导出测试",
                }],
            },
        }],
        path,
    )

    assert path.is_file()
    with zipfile.ZipFile(path) as archive:
        assert "ppt/slides/slide1.xml" in archive.namelist()


def test_docker_payload_maps_host_assets_and_runtime(tmp_path):
    from app.ppt_generation import presenton_html_renderer as renderer

    host_root = renderer._ROOT
    runtime_root = tmp_path / "runtime"
    host_asset = host_root / "data" / "ai-exports" / "image.png"
    payload = {
        "templateRoot": str(host_root / "app" / "ppt_generation" / "assets" / "templates" / "general"),
        "outputRoot": str(runtime_root),
        "slides": [{"imagePath": str(host_asset)}],
    }

    mapped = renderer._dockerize_payload(payload, "general", runtime_root)

    assert mapped["templateRoot"] == "/app/templates/general"
    assert mapped["outputRoot"] == "/app/runtime"
    assert mapped["slides"][0]["imagePath"].startswith("/app/host/")


def test_docker_renderer_returns_structured_result(monkeypatch, tmp_path):
    from app.ppt_generation import presenton_html_renderer as renderer

    input_path = tmp_path / "task.json"
    input_path.write_text("{}", encoding="utf-8")
    calls = []

    class Completed:
        returncode = 0
        stdout = '{"slideCount": 2, "pptxPath": "/app/runtime/task.pptx"}\n'
        stderr = ""

    def fake_run(command, **kwargs):
        calls.append(command)
        return Completed()

    monkeypatch.setenv("PPTX_EXPORT_DOCKER_IMAGE", "test-pptx-exporter:latest")
    monkeypatch.setattr(renderer.subprocess, "run", fake_run)

    result = renderer._run_docker_renderer(input_path, tmp_path)

    assert result["slideCount"] == 2
    assert result["pptxPath"] == "/app/runtime/task.pptx"
    assert calls[0][:3] == ["docker", "image", "inspect"]
    assert calls[1][0:3] == ["docker", "run", "--rm"]
    assert "test-pptx-exporter:latest" in calls[1]
    assert calls[1][-1] == "/app/runtime/task.json"


def test_renderer_path_maps_container_runtime_to_host(tmp_path):
    from app.ppt_generation import presenton_html_renderer as renderer

    assert renderer._host_path_from_renderer("/app/runtime/task.pptx", tmp_path) == Path(tmp_path) / "task.pptx"
    assert renderer._host_path_from_renderer("file:///app/runtime/task.pptx", tmp_path) == Path(tmp_path) / "task.pptx"


def test_exporter_image_declares_deterministic_cjk_font_alias():
    root = Path(__file__).resolve().parents[1]
    dockerfile = (root / "Dockerfile.pptx-exporter").read_text(encoding="utf-8")
    font_config = (root / "presenton_runtime" / "fontconfig-pptx.conf").read_text(
        encoding="utf-8"
    )

    assert "COPY presenton_runtime/fontconfig-pptx.conf /etc/fonts/local.conf" in dockerfile
    assert "Noto Serif" in font_config
    assert "Noto Serif CJK SC" in font_config
    assert "Microsoft YaHei" in font_config
    assert "Noto Sans CJK SC" in font_config


def test_normalize_pptx_slide_size_metadata_repairs_legacy_preset(tmp_path):
    from app.ppt_generation import presenton_html_renderer as renderer

    path = tmp_path / "legacy.pptx"
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr(
            "ppt/presentation.xml",
            '<p:presentation xmlns:p="urn:test"><p:sldSz cx="12192000" cy="6858000" type="screen4x3"/></p:presentation>',
        )

    renderer._normalize_pptx_slide_size_metadata(path)

    with zipfile.ZipFile(path) as archive:
        xml = archive.read("ppt/presentation.xml")
    assert b'type="screen16x9"' in xml
