import csv
import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from validate_python_course import collect_validation_errors


class PythonCourseKnowledgeValidationTest(unittest.TestCase):
    def _write_pack(self, root: Path, *, sources=None, evidence=None):
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


if __name__ == "__main__":
    unittest.main()
