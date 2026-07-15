#!/usr/bin/env python3
import argparse
import csv
import hashlib
import json
import re
import zipfile
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Set


PACK_RELATIVE = Path("artifacts/knowledge-base/python-course")
EVALUATION_RELATIVE = Path("evaluation/python-course/gold.jsonl")
CSV_FIELDS = ["source_id", "title", "origin", "author", "license", "sha256"]
ALLOWED_LICENSES = {
    "Apache-2.0",
    "BSD-2-Clause",
    "BSD-3-Clause",
    "CC-BY-4.0",
    "CC-BY-SA-4.0",
    "CC0-1.0",
    "MIT",
    "PSF-2.0",
    "Team-Authored",
}
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _load_json(path: Path, errors: List[str]) -> Optional[Dict[str, Any]]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        errors.append(f"missing file: {path}")
        return None
    except (OSError, json.JSONDecodeError) as exc:
        errors.append(f"invalid JSON {path}: {exc}")
        return None
    if not isinstance(value, dict):
        errors.append(f"JSON root must be an object: {path}")
        return None
    return value


def _safe_pack_path(pack: Path, raw_path: str, errors: List[str], source_id: str) -> Optional[Path]:
    relative = Path(raw_path)
    if not raw_path or relative.is_absolute() or ".." in relative.parts:
        errors.append(f"source {source_id} has unsafe origin path: {raw_path}")
        return None
    candidate = (pack / relative).resolve()
    try:
        candidate.relative_to(pack.resolve())
    except ValueError:
        errors.append(f"source {source_id} escapes pack root: {raw_path}")
        return None
    return candidate


def _safe_manifest_artifact_path(
    pack: Path,
    raw_path: Any,
    errors: List[str],
    location: str,
) -> Optional[Path]:
    value = str(raw_path or "").strip()
    relative = Path(value)
    if not value or relative.is_absolute() or ".." in relative.parts:
        errors.append(f"{location} must be a safe path relative to the knowledge pack: {value}")
        return None
    candidate = (pack / relative).resolve()
    try:
        candidate.relative_to(pack.resolve())
    except ValueError:
        errors.append(f"{location} escapes the knowledge pack: {value}")
        return None
    return candidate


def _validate_artifact_reference(
    pack: Path,
    raw_value: Any,
    errors: List[str],
    location: str,
    *,
    require_zip: bool = False,
) -> Optional[Path]:
    if not isinstance(raw_value, dict):
        errors.append(f"{location} must be an object with path and sha256")
        return None
    raw_path = raw_value.get("path")
    candidate = _safe_manifest_artifact_path(pack, raw_path, errors, f"{location}.path")
    expected_hash = str(raw_value.get("sha256") or "").strip().lower()
    if not SHA256_RE.fullmatch(expected_hash):
        errors.append(f"{location}.sha256 must be a lowercase SHA-256 digest")
    if candidate is None:
        return None
    if not candidate.is_file():
        errors.append(f"{location}.path does not exist: {raw_path}")
        return candidate
    if SHA256_RE.fullmatch(expected_hash):
        actual_hash = _sha256(candidate)
        if actual_hash != expected_hash:
            errors.append(
                f"{location}.sha256 mismatch: expected {expected_hash}, got {actual_hash}"
            )
    if require_zip:
        if candidate.suffix.lower() != ".zip":
            errors.append(f"{location}.path must point to a .zip MaxKB export")
        elif not zipfile.is_zipfile(candidate):
            errors.append(f"{location}.path is not a readable ZIP archive")
    return candidate


def _read_sources(pack: Path, errors: List[str]) -> Dict[str, Dict[str, str]]:
    path = pack / "sources.csv"
    try:
        with path.open("r", encoding="utf-8-sig", newline="") as handle:
            reader = csv.DictReader(handle)
            if reader.fieldnames != CSV_FIELDS:
                errors.append(
                    f"sources.csv header must be {','.join(CSV_FIELDS)}; got {reader.fieldnames}"
                )
                return {}
            rows = list(reader)
    except OSError as exc:
        errors.append(f"cannot read {path}: {exc}")
        return {}

    by_id: Dict[str, Dict[str, str]] = {}
    for line_number, row in enumerate(rows, start=2):
        source_id = str(row.get("source_id") or "").strip()
        if not source_id:
            errors.append(f"sources.csv line {line_number} has blank source_id")
            continue
        if source_id in by_id:
            errors.append(f"duplicate source_id: {source_id}")
            continue
        license_id = str(row.get("license") or "").strip()
        if license_id not in ALLOWED_LICENSES:
            errors.append(f"source {source_id} uses unknown license: {license_id}")
        expected_hash = str(row.get("sha256") or "").strip().lower()
        if not SHA256_RE.fullmatch(expected_hash):
            errors.append(f"source {source_id} has invalid sha256: {expected_hash}")
        source_path = _safe_pack_path(pack, str(row.get("origin") or "").strip(), errors, source_id)
        if source_path is not None:
            if not source_path.is_file():
                errors.append(f"source {source_id} file is missing: {source_path}")
            elif SHA256_RE.fullmatch(expected_hash):
                actual_hash = _sha256(source_path)
                if actual_hash != expected_hash:
                    errors.append(
                        f"source {source_id} hash mismatch: expected {expected_hash}, got {actual_hash}"
                    )
        by_id[source_id] = {key: str(value or "").strip() for key, value in row.items()}
    return by_id


def _validate_manifest(
    pack: Path,
    manifest: Optional[Dict[str, Any]],
    sources: Dict[str, Dict[str, str]],
    errors: List[str],
) -> Set[str]:
    if manifest is None:
        return set()
    required = {
        "schemaVersion",
        "packVersion",
        "status",
        "courseKey",
        "maxkbVersion",
        "documentCount",
        "paragraphCount",
        "splitConfiguration",
        "courseChapters",
        "sourceIds",
        "sources",
        "exportArtifact",
        "authorizationEvidence",
        "reviewSignoff",
    }
    missing = sorted(required - set(manifest))
    if missing:
        errors.append(f"manifest missing fields: {', '.join(missing)}")
    if manifest.get("courseKey") != "python":
        errors.append("manifest courseKey must be python")
    status = manifest.get("status")
    if status not in {"needs_export", "ready"}:
        errors.append("manifest status must be needs_export or ready")

    raw_source_ids = manifest.get("sourceIds")
    if not isinstance(raw_source_ids, list) or any(not isinstance(item, str) for item in raw_source_ids):
        errors.append("manifest sourceIds must be a string array")
        source_ids: Set[str] = set()
    else:
        source_ids = set(raw_source_ids)
        if len(source_ids) != len(raw_source_ids):
            errors.append("manifest sourceIds contains duplicates")
    csv_ids = set(sources)
    if source_ids != csv_ids:
        errors.append(
            f"manifest/source CSV IDs differ: manifest={sorted(source_ids)} csv={sorted(csv_ids)}"
        )

    manifest_sources = manifest.get("sources")
    if not isinstance(manifest_sources, list):
        errors.append("manifest sources must be an array")
        manifest_sources = []
    seen_manifest_ids: Set[str] = set()
    for item in manifest_sources:
        if not isinstance(item, dict):
            errors.append("manifest sources entries must be objects")
            continue
        source_id = str(item.get("sourceId") or "").strip()
        if not source_id:
            errors.append("manifest source entry has blank sourceId")
            continue
        if source_id in seen_manifest_ids:
            errors.append(f"manifest source entry is duplicated: {source_id}")
        seen_manifest_ids.add(source_id)
        csv_row = sources.get(source_id)
        if csv_row is None:
            errors.append(f"manifest source is not declared in sources.csv: {source_id}")
            continue
        if str(item.get("path") or "").strip() != csv_row["origin"]:
            errors.append(f"manifest source path differs from sources.csv: {source_id}")
        if str(item.get("sha256") or "").strip().lower() != csv_row["sha256"].lower():
            errors.append(f"manifest source hash differs from sources.csv: {source_id}")
    if seen_manifest_ids != csv_ids:
        errors.append(
            f"manifest sources entries differ from CSV IDs: manifest={sorted(seen_manifest_ids)} csv={sorted(csv_ids)}"
        )

    if status == "needs_export":
        for field in ["maxkbVersion", "documentCount", "paragraphCount", "splitConfiguration"]:
            if manifest.get(field) is not None:
                errors.append(f"needs_export manifest must keep {field}=null")
        if manifest_sources or source_ids:
            errors.append("needs_export manifest cannot claim exported sources")
        if manifest.get("courseChapters") not in ([], None):
            errors.append("needs_export manifest cannot claim verified course chapters")
        for field in ["exportArtifact", "authorizationEvidence", "reviewSignoff"]:
            if manifest.get(field) is not None:
                errors.append(f"needs_export manifest must keep {field}=null")
    elif status == "ready":
        if not isinstance(manifest.get("maxkbVersion"), str) or not manifest.get("maxkbVersion", "").strip():
            errors.append("ready manifest requires maxkbVersion")
        for field in ["documentCount", "paragraphCount"]:
            value = manifest.get(field)
            if not isinstance(value, int) or isinstance(value, bool) or value < 1:
                errors.append(f"ready manifest requires positive integer {field}")
        if not isinstance(manifest.get("splitConfiguration"), dict) or not manifest.get("splitConfiguration"):
            errors.append("manifest.splitConfiguration must be a non-empty object when status=ready")
        raw_chapters = manifest.get("courseChapters")
        if (
            not isinstance(raw_chapters, list)
            or not raw_chapters
            or any(not isinstance(item, str) or not item.strip() for item in raw_chapters)
        ):
            errors.append("manifest.courseChapters must be a non-empty string array when status=ready")
        elif len({item.strip() for item in raw_chapters}) != len(raw_chapters):
            errors.append("manifest.courseChapters contains duplicates")
        if not sources:
            errors.append("ready manifest requires at least one source")
        document_count = manifest.get("documentCount")
        paragraph_count = manifest.get("paragraphCount")
        if isinstance(document_count, int) and not isinstance(document_count, bool):
            if document_count != len(sources):
                errors.append(
                    "manifest.documentCount must equal the number of declared source documents: "
                    f"documentCount={document_count}, sources={len(sources)}"
                )
        if (
            isinstance(paragraph_count, int)
            and not isinstance(paragraph_count, bool)
            and isinstance(document_count, int)
            and not isinstance(document_count, bool)
            and paragraph_count < document_count
        ):
            errors.append("manifest.paragraphCount cannot be smaller than manifest.documentCount")

        _validate_artifact_reference(
            pack,
            manifest.get("exportArtifact"),
            errors,
            "manifest.exportArtifact",
            require_zip=True,
        )
        _validate_artifact_reference(
            pack,
            manifest.get("authorizationEvidence"),
            errors,
            "manifest.authorizationEvidence",
        )
        authorization_evidence = manifest.get("authorizationEvidence")
        if isinstance(authorization_evidence, dict):
            covered_source_ids = authorization_evidence.get("coveredSourceIds")
            if (
                not isinstance(covered_source_ids, list)
                or any(not isinstance(item, str) or not item.strip() for item in covered_source_ids)
                or len(set(covered_source_ids)) != len(covered_source_ids)
                or set(covered_source_ids) != source_ids
            ):
                errors.append(
                    "manifest.authorizationEvidence.coveredSourceIds must match manifest.sourceIds exactly"
                )
        _validate_artifact_reference(
            pack,
            manifest.get("reviewSignoff"),
            errors,
            "manifest.reviewSignoff",
        )
        review_signoff = manifest.get("reviewSignoff")
        if isinstance(review_signoff, dict):
            signed_by = review_signoff.get("signedBy")
            if (
                not isinstance(signed_by, list)
                or len({str(item).strip() for item in signed_by if str(item).strip()}) < 2
            ):
                errors.append("manifest.reviewSignoff.signedBy requires two distinct non-blank reviewers")
            signed_at = str(review_signoff.get("signedAt") or "").strip()
            if not re.fullmatch(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z", signed_at):
                errors.append("manifest.reviewSignoff.signedAt must use UTC YYYY-MM-DDTHH:MM:SSZ")
    return source_ids


def _validate_checksums(pack: Path, errors: List[str]) -> None:
    path = pack / "checksums.sha256"
    required_paths = {"README.md", "manifest.json", "sources.csv"}
    declared: Dict[str, str] = {}
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as exc:
        errors.append(f"cannot read {path}: {exc}")
        return
    for line_number, line in enumerate(lines, start=1):
        if not line.strip():
            continue
        parts = line.split("  ", 1)
        if len(parts) != 2 or not SHA256_RE.fullmatch(parts[0]):
            errors.append(f"checksums.sha256 line {line_number} is invalid")
            continue
        declared[parts[1]] = parts[0]
    missing = sorted(required_paths - set(declared))
    if missing:
        errors.append(f"checksums.sha256 missing entries: {', '.join(missing)}")
    for relative, expected_hash in declared.items():
        candidate = _safe_pack_path(pack, relative, errors, f"checksum:{relative}")
        if candidate is None:
            continue
        if not candidate.is_file():
            errors.append(f"checksum target is missing: {relative}")
            continue
        actual_hash = _sha256(candidate)
        if actual_hash != expected_hash:
            errors.append(
                f"checksum mismatch for {relative}: expected {expected_hash}, got {actual_hash}"
            )


def _evidence_ids(raw_evidence: Any) -> Iterable[str]:
    if not isinstance(raw_evidence, list):
        return []
    values: List[str] = []
    for item in raw_evidence:
        if isinstance(item, str):
            values.append(item.strip())
        elif isinstance(item, dict):
            values.append(str(item.get("sourceId") or item.get("source_id") or "").strip())
    return [value for value in values if value]


def _validate_evaluation(root: Path, declared_source_ids: Set[str], errors: List[str]) -> None:
    path = root / EVALUATION_RELATIVE
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as exc:
        errors.append(f"cannot read {path}: {exc}")
        return
    for line_number, line in enumerate(lines, start=1):
        if not line.strip():
            continue
        try:
            record = json.loads(line)
        except json.JSONDecodeError as exc:
            errors.append(f"gold.jsonl line {line_number} is invalid JSON: {exc}")
            continue
        if not isinstance(record, dict):
            errors.append(f"gold.jsonl line {line_number} must be an object")
            continue
        for source_id in _evidence_ids(record.get("expectedEvidence")):
            if source_id not in declared_source_ids:
                errors.append(
                    f"gold.jsonl line {line_number} references undeclared source: {source_id}"
                )


def collect_validation_errors(root: Path, *, require_evaluation: bool = True) -> List[str]:
    root = root.resolve()
    pack = root / PACK_RELATIVE
    errors: List[str] = []
    sources = _read_sources(pack, errors)
    manifest = _load_json(pack / "manifest.json", errors)
    declared_source_ids = _validate_manifest(pack, manifest, sources, errors)
    _validate_checksums(pack, errors)
    if require_evaluation:
        _validate_evaluation(root, declared_source_ids, errors)
    return errors


def main() -> int:
    default_root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description="Validate the frozen Python-course knowledge pack")
    parser.add_argument("--repo-root", type=Path, default=default_root)
    parser.add_argument(
        "--skip-evaluation",
        action="store_true",
        help="Validate the pack before gold.jsonl is added; final quality gates must not use this option",
    )
    args = parser.parse_args()
    errors = collect_validation_errors(args.repo_root, require_evaluation=not args.skip_evaluation)
    if errors:
        for error in errors:
            print(f"[FAIL] {error}")
        return 1
    print("[PASS] Python-course knowledge manifest, sources, hashes, and evidence references are valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
