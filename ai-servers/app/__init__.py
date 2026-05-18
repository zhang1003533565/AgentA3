from pathlib import Path

try:
    from dotenv import load_dotenv
except ImportError:  # fallback when dependency is not installed yet
    def load_dotenv(*args, **kwargs):  # type: ignore
        return False

ROOT_DIR = Path(__file__).resolve().parents[1]
load_dotenv(ROOT_DIR / ".env")
