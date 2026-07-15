import csv
import hashlib
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from validate_python_course import collect_validation_errors


class PythonCourseKnowledgeValidationTest(unittest.TestCase):
    def _write_pack(
        self,
        root: Path,
        *,
        sources=None,
        evidence=None,
        export_path="exports/python-course-maxkb.zip",
        export_hash_override=None,
        include_governance_evidence=True,
    ):
        pack = root / "artifacts/knowledge-base/python-course"
        evaluation = root / "evaluation/python-course"
        pack.mkdir(parents=True)
        evaluation.mkdir(parents=True)
        sources = sources or []
        source_ids = [item["source_id"] for item in sources]

        with (pack / "sources.csv").open("w", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(
                handle,
                fieldnames=["source_id", "title", "origin", "author", "license", "sha256"],
            )
            writer.writeheader()
            writer.writerows(sources)

        manifest_sources = []
        for item in sources:
            source_path = pack / item["origin"]
            source_path.parent.mkdir(parents=True, exist_ok=True)
            source_path.write_text("Python source\n", encoding="utf-8")
            digest = hashlib.sha256(source_path.read_bytes()).hexdigest()
            item["sha256"] = digest
            manifest_sources.append({
                "sourceId": item["source_id"],
                "path": item["origin"],
                "sha256": digest,
            })

        if sources:
            with (pack / "sources.csv").open("w", encoding="utf-8", newline="") as handle:
                writer = csv.DictWriter(
                    handle,
                    fieldnames=["source_id", "title", "origin", "author", "license", "sha256"],
                )
                writer.writeheader()
                writer.writerows(sources)

        export_artifact = None
        authorization_evidence = None
        review_signoff = None
        if sources:
            export_file = pack / export_path
            export_file.parent.mkdir(parents=True, exist_ok=True)
            if ".." not in Path(export_path).parts:
                with zipfile.ZipFile(export_file, "w") as archive:
                    archive.writestr("manifest.json", '{"kind":"maxkb-export"}')
                export_hash = hashlib.sha256(export_file.read_bytes()).hexdigest()
            else:
                export_hash = "0" * 64
            export_artifact = {
                "path": export_path,
                "sha256": export_hash_override or export_hash,
            }
            if include_governance_evidence:
                evidence_dir = pack / "evidence"
                evidence_dir.mkdir(parents=True, exist_ok=True)
                authorization_file = evidence_dir / "redistribution-authorization.md"
                authorization_file.write_text("团队确认拥有这些课程资料的提交与再分发权。\n", encoding="utf-8")
                signoff_file = evidence_dir / "knowledge-review-signoff.md"
                signoff_file.write_text("知识负责人和合规复核人已完成签字复核。\n", encoding="utf-8")
                authorization_evidence = {
                    "path": "evidence/redistribution-authorization.md",
                    "sha256": hashlib.sha256(authorization_file.read_bytes()).hexdigest(),
                    "coveredSourceIds": source_ids,
                }
                review_signoff = {
                    "path": "evidence/knowledge-review-signoff.md",
                    "sha256": hashlib.sha256(signoff_file.read_bytes()).hexdigest(),
                    "signedBy": ["knowledge-owner", "compliance-reviewer"],
                    "signedAt": "2026-07-15T12:00:00Z",
                }

        manifest = {
            "schemaVersion": 1,
            "packVersion": "test",
            "status": "ready" if sources else "needs_export",
            "courseKey": "python",
            "maxkbVersion": "test" if sources else None,
            "documentCount": len(sources) if sources else None,
            "paragraphCount": len(sources) if sources else None,
            "splitConfiguration": {"mode": "test"} if sources else None,
            "courseChapters": ["测试章节"] if sources else [],
            "sourceIds": source_ids,
            "sources": manifest_sources,
            "exportArtifact": export_artifact,
            "authorizationEvidence": authorization_evidence,
            "reviewSignoff": review_signoff,
        }
        (pack / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
        (pack / "README.md").write_text("test\n", encoding="utf-8")
        checksums = []
        for relative in ["README.md", "manifest.json", "sources.csv"]:
            digest = hashlib.sha256((pack / relative).read_bytes()).hexdigest()
            checksums.append(f"{digest}  {relative}")
        (pack / "checksums.sha256").write_text("\n".join(checksums) + "\n", encoding="utf-8")

        record = {
            "id": "test-001",
            "query": "测试",
            "expectedEvidence": evidence or [],
            "answerType": "deterministic",
            "expectedAnswer": "测试",
            "shouldRefuse": False,
        }
        (evaluation / "gold.jsonl").write_text(json.dumps(record) + "\n", encoding="utf-8")

    def test_honest_needs_export_pack_is_valid(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self._write_pack(root)

            self.assertEqual([], collect_validation_errors(root))

    def test_unknown_license_and_undeclared_evidence_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self._write_pack(
                root,
                sources=[{
                    "source_id": "source-1",
                    "title": "测试来源",
                    "origin": "sources/source-1.txt",
                    "author": "Team",
                    "license": "Unknown-License",
                    "sha256": "",
                }],
                evidence=["source-not-declared"],
            )

            errors = collect_validation_errors(root)

            self.assertTrue(any("Unknown-License" in error for error in errors))
            self.assertTrue(any("source-not-declared" in error for error in errors))

    def test_ready_pack_requires_real_export_authorization_and_review_signoff(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self._write_pack(
                root,
                sources=[{
                    "source_id": "source-1",
                    "title": "测试来源",
                    "origin": "sources/source-1.txt",
                    "author": "Team",
                    "license": "Team-Authored",
                    "sha256": "",
                }],
                include_governance_evidence=False,
            )

            errors = collect_validation_errors(root)

            self.assertTrue(any("manifest.authorizationEvidence" in error for error in errors))
            self.assertTrue(any("manifest.reviewSignoff" in error for error in errors))

    def test_ready_export_path_and_hash_are_verified_inside_pack(self):
        source = [{
            "source_id": "source-1",
            "title": "测试来源",
            "origin": "sources/source-1.txt",
            "author": "Team",
            "license": "Team-Authored",
            "sha256": "",
        }]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self._write_pack(root, sources=source, export_path="../outside.zip")

            errors = collect_validation_errors(root)

            self.assertTrue(any("manifest.exportArtifact.path" in error for error in errors))

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self._write_pack(root, sources=source, export_hash_override="0" * 64)

            errors = collect_validation_errors(root)

            self.assertTrue(any("manifest.exportArtifact.sha256" in error for error in errors))

    def test_ready_pack_with_consistent_export_and_governance_evidence_is_valid(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self._write_pack(
                root,
                sources=[{
                    "source_id": "source-1",
                    "title": "测试来源",
                    "origin": "sources/source-1.txt",
                    "author": "Team",
                    "license": "Team-Authored",
                    "sha256": "",
                }],
                evidence=["source-1"],
            )

            self.assertEqual([], collect_validation_errors(root))

    def test_authorization_evidence_must_cover_every_declared_source(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self._write_pack(
                root,
                sources=[{
                    "source_id": "source-1",
                    "title": "测试来源",
                    "origin": "sources/source-1.txt",
                    "author": "Team",
                    "license": "Team-Authored",
                    "sha256": "",
                }],
            )
            pack = root / "artifacts/knowledge-base/python-course"
            manifest_path = pack / "manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["authorizationEvidence"]["coveredSourceIds"] = ["different-source"]
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            checksums = []
            for relative in ["README.md", "manifest.json", "sources.csv"]:
                digest = hashlib.sha256((pack / relative).read_bytes()).hexdigest()
                checksums.append(f"{digest}  {relative}")
            (pack / "checksums.sha256").write_text("\n".join(checksums) + "\n", encoding="utf-8")

            errors = collect_validation_errors(root)

            self.assertTrue(any("manifest.authorizationEvidence.coveredSourceIds" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
