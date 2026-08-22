from __future__ import annotations

import os
import tempfile
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class EmbeddedPptConfig:
    engine: str
    enabled: bool
    default_template: str
    source_root: Path

    @classmethod
    def from_env(cls) -> "EmbeddedPptConfig":
        source_root = Path(
            os.getenv("AI_PPT_SOURCE_ROOT")
            or (Path(tempfile.gettempdir()) / "agent-a3-ppt-sources")
        ).expanduser()
        return cls(
            engine="presenton-embedded",
            enabled=True,
            default_template=str(
                os.getenv("PPT_DEFAULT_TEMPLATE") or "general"
            ).strip(),
            source_root=source_root,
        )
