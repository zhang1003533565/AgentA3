from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, List

from app.rag.indexing.multimodal_parser import MultimodalParser


@dataclass
class LoadedDocument:
    id: str
    content: str
    source: str


class DocumentLoader:
    SUPPORTED_SUFFIXES = {".md", ".markdown", ".txt", ".csv", ".json", ".html", ".htm"}

    def __init__(self) -> None:
        self.multimodal_parser = MultimodalParser()

    def load(self, source: str) -> Iterable[LoadedDocument]:
        path = Path(source)
        if path.is_dir():
            return self.load_directory(path)
        if path.is_file() and path.suffix.lower() in self.SUPPORTED_SUFFIXES:
            return [self._load_file(path)]
        return []

    def load_directory(self, source: Path) -> List[LoadedDocument]:
        documents: List[LoadedDocument] = []
        if not source.exists():
            return documents
        for path in sorted(source.rglob("*")):
            if not path.is_file() or path.suffix.lower() not in self.SUPPORTED_SUFFIXES:
                continue
            documents.append(self._load_file(path))
        return documents

    def _load_file(self, path: Path) -> LoadedDocument:
        if path.suffix.lower() in {".csv", ".json", ".html", ".htm", ".md", ".markdown"}:
            content = self.multimodal_parser.parse(str(path)).get("text", "")
        else:
            content = path.read_text(encoding="utf-8", errors="ignore")
        return LoadedDocument(
            id=str(path.resolve()),
            content=content,
            source=str(path),
        )
