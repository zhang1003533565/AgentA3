from __future__ import annotations

import argparse
import base64
import json
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.rag.indexing.document_loader import DocumentLoader  # noqa: E402
from app.rag.pipelines import IngestInputDocument, RagIngestionPipeline  # noqa: E402
from app.rag.vector_stores import DEFAULT_VECTOR_STORE_BACKEND, build_vector_store  # noqa: E402


def main() -> int:
    parser = argparse.ArgumentParser(description="Build Smart Campus RAG knowledge base.")
    parser.add_argument("--source-dir", default=os.getenv("RAG_KNOWLEDGE_BASE_DIR", "knowledge_base/raw"))
    parser.add_argument("--backend", default=os.getenv("RAG_VECTOR_STORE_BACKEND", DEFAULT_VECTOR_STORE_BACKEND))
    parser.add_argument("--limit", type=int, default=0)
    args = parser.parse_args()

    os.environ["RAG_VECTOR_STORE_BACKEND"] = args.backend
    source_dir = resolve_path(args.source_dir)
    loader = DocumentLoader()
    files = [
        path for path in sorted(source_dir.rglob("*"))
        if path.is_file()
        and path.suffix.lower() in DocumentLoader.SUPPORTED_SUFFIXES
        and "/.index/" not in str(path)
        and "/api_ingest/" not in str(path)
    ]
    if args.limit > 0:
        files = files[:args.limit]
    if not files:
        print(json.dumps({
            "backend": args.backend,
            "sourceDir": str(source_dir),
            "storedCount": 0,
            "message": "没有找到可入库文件",
        }, ensure_ascii=False, indent=2))
        return 0

    documents = []
    for path in files:
        content = path.read_bytes()
        documents.append(IngestInputDocument(
            content_base64=base64.b64encode(content).decode("ascii"),
            source=str(path.relative_to(source_dir)),
            metadata={
                "originalPath": str(path),
                "builder": "scripts/build_knowledge_base.py",
            },
        ))

    result = RagIngestionPipeline(root_dir=str(source_dir)).run(documents)
    vector_store = build_vector_store(root_dir=source_dir, backend=args.backend)
    health = vector_store.health()
    print(json.dumps({
        "backend": args.backend,
        "sourceDir": str(source_dir),
        "storedCount": result.stored_count,
        "indexedChunkCount": result.indexed_chunk_count,
        "indexPath": result.index_path,
        "parentChildIndexPath": str(source_dir / ".index" / "parent_child_chunks.jsonl"),
        "vectorStoreHealth": health,
        "documents": [item.__dict__ for item in result.documents],
        "trace": [{"stage": item.stage, "detail": item.detail} for item in result.trace],
    }, ensure_ascii=False, indent=2))
    return 0


def resolve_path(value: str) -> Path:
    path = Path(value)
    if path.is_absolute():
        return path
    return ROOT / path


if __name__ == "__main__":
    raise SystemExit(main())
