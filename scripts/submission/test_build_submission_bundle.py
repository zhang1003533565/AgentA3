import importlib.util
import json
from pathlib import Path
import tempfile
import unittest


MODULE_PATH = Path(__file__).with_name("build_submission_bundle.py")


def load_module():
    spec = importlib.util.spec_from_file_location("build_submission_bundle", MODULE_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class SubmissionBundleContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.module = load_module()

    def test_only_safe_source_and_final_artifacts_are_included(self):
        accepted = [
            "README.md",
            "deploy/.env.example",
            "AppBackend/src/main/java/Example.java",
            "artifacts/submission/AgentA3-中国软件杯演示.pptx",
            "output/docx/AgentA3-需求设计与开发说明书.docx",
            "output/pdf/AgentA3-需求设计与开发说明书.pdf",
        ]
        rejected = [
            ".env",
            "deploy/.env",
            "AppWeb/node_modules/pkg/index.js",
            "AppBackend/target/app.jar",
            "AppFrontend/dist/index.html",
            "ai-servers/.venv/bin/python",
            "scripts/__pycache__/tool.pyc",
            "artifacts/submission/AgentA3-中国软件杯演示/slide-1.png",
            "artifacts/submission/AgentA3-中国软件杯演示.pptx.inspect.ndjson",
            "docs/project-document/output/AgentA3-项目需求设计开发说明书.pdf",
            "docs/project-document/output/render-final/page-01.png",
            "artifacts/submission/AgentA3-submission.zip",
        ]

        for path in accepted:
            with self.subTest(path=path):
                self.assertTrue(self.module.should_include(Path(path)))
        for path in rejected:
            with self.subTest(path=path):
                self.assertFalse(self.module.should_include(Path(path)))

    def test_submission_uses_the_single_reviewed_document_output(self):
        self.assertEqual(
            Path("output/docx/AgentA3-需求设计与开发说明书.docx"),
            self.module.FINAL_DOCX,
        )
        self.assertEqual(
            Path("output/pdf/AgentA3-需求设计与开发说明书.pdf"),
            self.module.FINAL_PDF,
        )

    def test_pending_external_evidence_blocks_final_but_not_rehearsal_bundle(self):
        statuses = self.module.SubmissionStatuses(
            knowledge="needs_export",
            factual="not_run",
            load="not_run",
            video="missing",
        )

        self.assertEqual(
            [
                "Python 课程知识包尚未 ready",
                "30 题事实评测尚未 completed 且 passed",
                "5×50 压测尚未 completed 且 passed",
                "7 分钟演示视频尚未提供",
            ],
            self.module.final_gate_failures(statuses),
        )
        self.assertEqual([], self.module.bundle_gate_failures(statuses, allow_pending_evidence=True))

    def test_reads_honest_statuses_without_promoting_missing_files(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            (root / "artifacts/knowledge-base/python-course").mkdir(parents=True)
            (root / "artifacts/verification").mkdir(parents=True)
            (root / "artifacts/submission").mkdir(parents=True)
            (root / "artifacts/knowledge-base/python-course/manifest.json").write_text(
                json.dumps({"status": "ready"}), encoding="utf-8"
            )
            (root / "artifacts/verification/python-course-factual.json").write_text(
                json.dumps({"status": "completed", "passed": True}), encoding="utf-8"
            )
            (root / "artifacts/verification/python-course-load.json").write_text(
                json.dumps({"status": "completed", "passed": True}), encoding="utf-8"
            )

            without_video = self.module.read_submission_statuses(root)
            self.assertEqual("ready", without_video.knowledge)
            self.assertEqual("completed_passed", without_video.factual)
            self.assertEqual("completed_passed", without_video.load)
            self.assertEqual("missing", without_video.video)

            (root / "artifacts/submission/AgentA3-演示视频.mp4").write_bytes(b"video")
            self.assertEqual("present", self.module.read_submission_statuses(root).video)

    def test_manifest_is_sorted_and_contains_hashes_not_absolute_paths(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            (root / "z.txt").write_text("z", encoding="utf-8")
            (root / "a.txt").write_text("a", encoding="utf-8")
            manifest = self.module.build_manifest(
                root=root,
                files=[Path("z.txt"), Path("a.txt")],
                revision="abc123",
                branch="codex/test",
                generated_at="2026-07-15T00:00:00Z",
                statuses=self.module.SubmissionStatuses("ready", "completed_passed", "completed_passed", "present"),
                rehearsal=False,
            )

            self.assertEqual(["a.txt", "z.txt"], [item["path"] for item in manifest["files"]])
            self.assertEqual(64, len(manifest["files"][0]["sha256"]))
            self.assertNotIn(str(root), json.dumps(manifest, ensure_ascii=False))


if __name__ == "__main__":
    unittest.main()
