import hashlib
import hmac
import json
import os
import subprocess
import sys
import tempfile
from datetime import datetime, timedelta, timezone
from pathlib import Path
from unittest.mock import patch

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.rag.document_conversion import generated_exporter


@pytest.fixture
def export_root():
    original_root = generated_exporter.EXPORT_ROOT
    original_ttl_hours = getattr(generated_exporter, "EXPORT_TTL_HOURS", None)
    original_max_bytes = getattr(generated_exporter, "EXPORT_MAX_BYTES", None)
    with tempfile.TemporaryDirectory() as temp_dir:
        generated_exporter.EXPORT_ROOT = Path(temp_dir)
        generated_exporter.EXPORT_TTL_HOURS = 168
        generated_exporter.EXPORT_MAX_BYTES = 1024 * 1024
        yield Path(temp_dir)
    generated_exporter.EXPORT_ROOT = original_root
    if original_ttl_hours is not None:
        generated_exporter.EXPORT_TTL_HOURS = original_ttl_hours
    if original_max_bytes is not None:
        generated_exporter.EXPORT_MAX_BYTES = original_max_bytes


def _create_export():
    result = generated_exporter.export_generated_answer(
        "# 可下载资料\n\n- 服务端受控读取",
        "markdown",
        {
            "executedAgent": "textbook_knowledge_agent",
            "toolToggles": {
                "docx_export_tool": False,
                "excel_export_tool": False,
                "content_archive_tool": False,
            },
        },
    )
    return result.attachments[0]


def _sidecar_path(root: Path, storage_key: str) -> Path:
    return root / f"{storage_key}.meta.json"


def test_generated_exports_are_not_publicly_mounted():
    mounted_paths = {getattr(route, "path", "") for route in app.routes}

    assert "/uploads/ai-exports" not in mounted_paths


def test_internal_download_requires_per_file_capability_and_ignores_authorization(export_root):
    attachment = _create_export()
    client = TestClient(app)
    path = f"/internal/rag/exports/{attachment['storageKey']}"

    missing = client.get(path)
    bearer_only = client.get(path, headers={"Authorization": "Bearer forged-user-token"})
    wrong = client.get(path, headers={"X-AI-Export-Capability": "wrong-capability"})
    success = client.get(path, headers={
        "Authorization": "Bearer irrelevant",
        "X-AI-Export-Capability": attachment["internalCapability"],
    })

    assert missing.status_code == 403
    assert bearer_only.status_code == 403
    assert wrong.status_code == 403
    assert success.status_code == 200
    assert success.content == (export_root / attachment["storageKey"]).read_bytes()
    assert hashlib.sha256(success.content).hexdigest() == attachment["sha256"]


def test_capability_rejection_uses_constant_time_digest_comparison(export_root):
    attachment = _create_export()
    client = TestClient(app)
    real_compare_digest = hmac.compare_digest

    with patch.object(
        generated_exporter.hmac,
        "compare_digest",
        wraps=real_compare_digest,
    ) as compare_digest:
        response = client.get(
            f"/internal/rag/exports/{attachment['storageKey']}",
            headers={"X-AI-Export-Capability": "forged"},
        )

    assert response.status_code == 403
    compare_digest.assert_called_once()


def test_download_rejects_traversal_expiry_and_integrity_mismatch(export_root):
    attachment = _create_export()
    client = TestClient(app)
    capability_headers = {"X-AI-Export-Capability": attachment["internalCapability"]}

    traversal = client.get(
        "/internal/rag/exports/%2e%2e%2Fsecret.txt",
        headers=capability_headers,
    )
    assert traversal.status_code == 404

    payload_path = export_root / attachment["storageKey"]
    payload_path.write_bytes(payload_path.read_bytes() + b"tampered")
    corrupted = client.get(
        f"/internal/rag/exports/{attachment['storageKey']}",
        headers=capability_headers,
    )
    assert corrupted.status_code == 409

    attachment = _create_export()
    manifest_path = _sidecar_path(export_root, attachment["storageKey"])
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    manifest["createdAt"] = (datetime.now(timezone.utc) - timedelta(hours=2)).isoformat().replace(
        "+00:00", "Z"
    )
    manifest["expiresAt"] = (datetime.now(timezone.utc) - timedelta(seconds=1)).isoformat().replace(
        "+00:00", "Z"
    )
    manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
    expired = client.get(
        f"/internal/rag/exports/{attachment['storageKey']}",
        headers={"X-AI-Export-Capability": attachment["internalCapability"]},
    )
    assert expired.status_code == 410
    assert not (export_root / attachment["storageKey"]).exists()
    assert not manifest_path.exists()


def test_sidecar_is_sufficient_for_restart_recovery(export_root):
    attachment = _create_export()
    ai_servers_root = Path(__file__).resolve().parents[1]
    script = """
from app.rag.document_conversion.generated_exporter import open_generated_export
record = open_generated_export(%r, %r)
print(record.sha256)
""" % (attachment["storageKey"], attachment["internalCapability"])
    environment = os.environ.copy()
    environment["AI_EXPORT_ROOT"] = str(export_root)
    environment["AI_ENV"] = "development"
    environment["PYTHONPATH"] = str(ai_servers_root)

    result = subprocess.run(
        [sys.executable, "-c", script],
        cwd=ai_servers_root,
        env=environment,
        check=False,
        capture_output=True,
        text=True,
    )

    assert result.returncode == 0, result.stderr
    assert result.stdout.strip() == attachment["sha256"]


def test_production_import_fails_fast_without_explicit_export_root():
    ai_servers_root = Path(__file__).resolve().parents[1]
    environment = os.environ.copy()
    environment["AI_ENV"] = "production"
    environment.pop("AI_EXPORT_ROOT", None)
    environment["PYTHONPATH"] = str(ai_servers_root)

    result = subprocess.run(
        [sys.executable, "-c", "import app.main"],
        cwd=ai_servers_root,
        env=environment,
        check=False,
        capture_output=True,
        text=True,
    )

    assert result.returncode != 0
    assert "AI_EXPORT_ROOT" in result.stderr
