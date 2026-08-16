import os
import subprocess
import unittest
from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[2]


class SubmissionReleaseContractTest(unittest.TestCase):
    def test_server_deploy_and_workflow_share_submission_compose_and_images(self):
        workflow = (ROOT / ".github/workflows/deploy.yml").read_text(encoding="utf-8")
        server_script = (ROOT / "AppBackend/deploy/deploy-on-server.sh").read_text(encoding="utf-8")
        deploy_readme = (ROOT / "AppBackend/deploy/README.md").read_text(encoding="utf-8")

        self.assertIn("deploy/compose.submission.yml", server_script)
        self.assertIn('ENV_FILE="${ENV_FILE:-deploy/.env}"', server_script)
        for variable in ["BACKEND_IMAGE", "WEB_IMAGE", "AI_SERVER_IMAGE", "MYSQL_IMAGE", "REDIS_IMAGE", "IMAGE_TAG"]:
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
        self.assertIn("image: ${MYSQL_IMAGE:-mysql:8.4.5}", compose)
        self.assertIn("image: ${REDIS_IMAGE:-redis:7.4.2-alpine}", compose)

    def test_submission_compose_does_not_publish_stateful_service_ports(self):
        compose = (ROOT / "deploy/compose.submission.yml").read_text(encoding="utf-8")

        for service in ["mysql", "redis"]:
            match = re.search(rf"^  {service}:\n(?P<body>(?:    .*\n|      .*\n|        .*\n)*)", compose, re.MULTILINE)
            self.assertIsNotNone(match, service)
            self.assertNotIn("\n    ports:", match.group("body"), service)
        self.assertIn("jdbc:mysql://mysql:3306/", compose)
        self.assertIn("REDIS_URL: redis://redis:6379/0", compose)

    def test_backend_exam_preview_cache_is_ephemeral(self):
        compose = (ROOT / "deploy/compose.submission.yml").read_text(encoding="utf-8")

        self.assertIn("tmpfs:", compose)
        self.assertIn("- /tmp/agent-a3-exam-preview", compose)
        self.assertNotIn("backend-preview:", compose)
        self.assertNotIn("backend-preview:/tmp/agent-a3-exam-preview", compose)

    def test_submission_runtime_host_ports_avoid_common_java_defaults(self):
        workflow = (ROOT / ".github/workflows/deploy.yml").read_text(encoding="utf-8")
        compose = (ROOT / "deploy/compose.submission.yml").read_text(encoding="utf-8")
        verify = (ROOT / "deploy/verify.sh").read_text(encoding="utf-8")
        env_example = (ROOT / "deploy/.env.example").read_text(encoding="utf-8")

        self.assertIn("${BACKEND_PORT:-18080}:8080", compose)
        self.assertIn("${AI_PORT:-18081}:8081", compose)
        self.assertIn("BACKEND_PORT: ${{ vars.BACKEND_PORT || '18080' }}", workflow)
        self.assertIn("AI_PORT: ${{ vars.AI_PORT || '18081' }}", workflow)
        self.assertIn("BACKEND_PORT,AI_PORT", workflow)
        self.assertIn("DEPLOY_FORCE_RELEASE_PORTS", workflow)
        self.assertIn("DEPLOY_RELEASE_PORTS", workflow)
        self.assertIn("localhost:18080", verify)
        self.assertIn("localhost:18081", verify)
        self.assertIn("BACKEND_PORT=18080", env_example)
        self.assertIn("AI_PORT=18081", env_example)
        self.assertIn("DEPLOY_FORCE_RELEASE_PORTS=true", env_example)
        self.assertIn("DEPLOY_RELEASE_PORTS=8080 8081 18080 18081 3000", env_example)

    def test_deploy_mirrors_runtime_base_images_to_acr_before_server_pull(self):
        workflow = (ROOT / ".github/workflows/deploy.yml").read_text(encoding="utf-8")

        self.assertIn("Mirror Runtime Images To ACR", workflow)
        self.assertIn("docker pull mysql:8.4.5", workflow)
        self.assertIn('docker tag mysql:8.4.5 "$MYSQL_IMAGE"', workflow)
        self.assertIn('docker push "$MYSQL_IMAGE"', workflow)
        self.assertIn("docker pull redis:7.4.2-alpine", workflow)
        self.assertIn('docker tag redis:7.4.2-alpine "$REDIS_IMAGE"', workflow)
        self.assertIn('docker push "$REDIS_IMAGE"', workflow)
        self.assertIn("MYSQL_IMAGE,REDIS_IMAGE", workflow)

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
        self.assertNotIn("DEPLOY_PRUNE_UNTIL", server_script)
        self.assertIn("DEPLOY_FORCE_RELEASE_PORTS", server_script)
        self.assertIn("DEPLOY_RELEASE_PORTS", server_script)
        self.assertIn('env_file_value BACKEND_PORT', server_script)
        self.assertIn('env_file_value DEPLOY_RELEASE_PORTS', server_script)
        self.assertIn('down --remove-orphans', server_script)
        self.assertLess(
            server_script.index('run --rm --no-deps config-guard'),
            server_script.index('down --remove-orphans'),
        )
        self.assertLess(
            server_script.index('"${compose[@]}" down --remove-orphans'),
            server_script.index('\nrelease_configured_ports\n'),
        )
        self.assertLess(
            server_script.index('\nrelease_configured_ports\n'),
            server_script.index('up -d --remove-orphans'),
        )
        self.assertIn('docker ps -q --filter "publish=${port}"', server_script)
        self.assertIn("docker rm -f $container_ids", server_script)
        self.assertIn("fuser -k -n tcp", server_script)
        self.assertIn("kill -9 $pids", server_script)
        self.assertIn("dump_deploy_diagnostics", server_script)
        self.assertIn('logs --no-color --tail=200 "$service"', server_script)
        self.assertIn('ps -a', server_script)
        self.assertIn('docker ps --format', server_script)
        self.assertIn('if ! "${compose[@]}" up -d --remove-orphans', server_script)
        self.assertIn("docker image prune -af", server_script)
        self.assertIn("docker builder prune -af", server_script)
        self.assertNotIn('--filter "until=', server_script)
        self.assertLess(
            server_script.index('prune_docker_artifacts "before image pull"'),
            server_script.index('"${compose[@]}" pull config-guard mysql redis backend ai-server web'),
        )
        self.assertLess(
            server_script.index('bash deploy/verify.sh'),
            server_script.index('prune_docker_artifacts "after successful deployment"'),
        )
        self.assertNotIn("docker system prune -af --volumes", server_script)
        self.assertNotIn("docker volume prune", server_script)
        self.assertNotIn("down --volumes", server_script)

    def test_server_deploy_discards_tracked_changes_before_syncing_branch(self):
        workflow = (ROOT / ".github/workflows/deploy.yml").read_text(encoding="utf-8")
        server_script = (ROOT / "AppBackend/deploy/deploy-on-server.sh").read_text(encoding="utf-8")

        for source in [workflow, server_script]:
            self.assertIn('git checkout -f -B "$DEPLOY_BRANCH" "origin/$DEPLOY_BRANCH"', source)
            self.assertIn('git reset --hard "origin/$DEPLOY_BRANCH"', source)
            self.assertNotIn("git pull --ff-only", source)
            self.assertNotIn("git stash push", source)

    def test_server_deploy_reports_missing_required_runtime_secrets_before_compose(self):
        server_script = (ROOT / "AppBackend/deploy/deploy-on-server.sh").read_text(encoding="utf-8")

        self.assertIn("missing_required", server_script)
        for variable in ["MYSQL_ROOT_PASSWORD", "JWT_SECRET", "AI_INTERNAL_TOKEN"]:
            self.assertIn(variable, server_script)
        self.assertIn("Missing required deployment secrets", server_script)
        self.assertLess(
            server_script.index("Missing required deployment secrets"),
            server_script.index('compose=(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE")'),
        )

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
