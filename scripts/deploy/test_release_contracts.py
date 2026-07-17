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
        self.assertIn("SMOKE_TOKEN", workflow)
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

    def test_local_java_and_python_share_development_internal_token_default(self):
        application_yml = (ROOT / "AppBackend/src/main/resources/application.yml").read_text(encoding="utf-8")
        python_auth = (ROOT / "ai-servers/app/security/internal_auth.py").read_text(encoding="utf-8")

        default_token = "dev-internal-token-change-me-32chars"
        self.assertIn(f"${{AI_INTERNAL_TOKEN:{default_token}}}", application_yml)
        self.assertIn(f'DEFAULT_INTERNAL_TOKEN = "{default_token}"', python_auth)

    def test_verify_script_checks_dependency_readiness_and_business_boundary(self):
        verify = (ROOT / "deploy/verify.sh").read_text(encoding="utf-8")

        self.assertIn("/actuator/readiness", verify)
        self.assertIn("/internal/readiness", verify)
        self.assertIn("/internal/models/providers", verify)
        self.assertIn("check_json_contains", verify)
        self.assertIn("/api/auth/current-user", verify)
        self.assertIn("SMOKE_TOKEN", verify)
        self.assertIn("check_json_up", verify)
        self.assertIn("check_business_success", verify)

    def test_server_deploy_prunes_old_docker_artifacts_without_touching_volumes(self):
        server_script = (ROOT / "AppBackend/deploy/deploy-on-server.sh").read_text(encoding="utf-8")

        self.assertIn("DEPLOY_PRUNE_DOCKER", server_script)
        self.assertIn("DEPLOY_PRUNE_UNTIL", server_script)
        self.assertIn("docker image prune -af --filter", server_script)
        self.assertIn("docker builder prune -af --filter", server_script)
        self.assertNotIn("docker system prune -af --volumes", server_script)
        self.assertNotIn("docker volume prune", server_script)

    def test_web_image_builds_with_same_origin_api_mode(self):
        dockerfile = (ROOT / "AppWeb/Dockerfile").read_text(encoding="utf-8")

        self.assertIn("ARG VITE_API_MODE=relative", dockerfile)
        self.assertIn("ENV VITE_API_MODE=$VITE_API_MODE", dockerfile)

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
