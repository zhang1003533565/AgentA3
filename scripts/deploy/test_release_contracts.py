import os
import subprocess
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class SubmissionReleaseContractTest(unittest.TestCase):
    def test_server_deploy_and_workflow_share_submission_compose_and_images(self):
        workflow = (ROOT / ".github/workflows/deploy.yml").read_text(encoding="utf-8")
        server_script = (ROOT / "AppBackend/deploy/deploy-on-server.sh").read_text(encoding="utf-8")
        deploy_readme = (ROOT / "AppBackend/deploy/README.md").read_text(encoding="utf-8")

        self.assertIn("deploy/compose.submission.yml", server_script)
        self.assertIn('ENV_FILE="${ENV_FILE:-deploy/.env}"', server_script)
        for variable in ["BACKEND_IMAGE", "WEB_IMAGE", "AI_SERVER_IMAGE", "IMAGE_TAG"]:
            self.assertIn(variable, workflow)
            self.assertIn(variable, server_script)
        for shared_secret in ["JWT_SECRET", "AI_INTERNAL_TOKEN"]:
            self.assertIn(shared_secret, workflow)
            self.assertIn(shared_secret, server_script)
        self.assertIn("deploy/compose.submission.yml", deploy_readme)
        self.assertNotIn("AppBackend/docker-compose.yml", deploy_readme)

    def test_compose_fails_closed_and_runtime_guard_rejects_placeholders(self):
        compose = (ROOT / "deploy/compose.submission.yml").read_text(encoding="utf-8")

        for variable in ["MYSQL_ROOT_PASSWORD", "JWT_SECRET", "AI_INTERNAL_TOKEN"]:
            self.assertIn(f"${{{variable}:?", compose)
        self.assertIn("CHANGE_ME", compose)
        self.assertIn("tr '[:upper:]' '[:lower:]'", compose)
        self.assertIn("service_completed_successfully", compose)
        self.assertIn("REDIS_HOST: redis", compose)
        self.assertIn("REDIS_URL: redis://redis:6379/0", compose)

    def test_verify_script_checks_dependency_readiness_and_business_boundary(self):
        verify = (ROOT / "deploy/verify.sh").read_text(encoding="utf-8")

        self.assertIn("/actuator/readiness", verify)
        self.assertIn("/internal/readiness", verify)
        self.assertIn("/api/auth/current-user", verify)
        self.assertIn("SMOKE_TOKEN", verify)
        self.assertIn("check_json_up", verify)
        self.assertIn("check_business_success", verify)

    def test_final_submission_requires_authenticated_smoke_token_before_network_probes(self):
        verify_path = ROOT / "deploy/verify.sh"
        environment = os.environ.copy()
        environment["FINAL_SUBMISSION"] = "true"
        environment.pop("SMOKE_TOKEN", None)

        completed = subprocess.run(
            ["bash", str(verify_path)],
            cwd=ROOT,
            env=environment,
            capture_output=True,
            text=True,
            check=False,
        )

        self.assertNotEqual(0, completed.returncode)
        self.assertIn("SMOKE_TOKEN", completed.stderr)
        self.assertNotIn("localhost:8080", completed.stdout)


if __name__ == "__main__":
    unittest.main()
