from pathlib import Path

AI_SERVER_ROOT = Path(__file__).resolve().parents[2]

KNOWLEDGE_BASE_DIR = "knowledge_base/raw"

CHUNK_SIZE = 800
CHUNK_OVERLAP = 120
PARENT_CHUNK_SIZE = 1600
PARENT_CHUNK_OVERLAP = 160
CHILD_CHUNK_SIZE = 420
CHILD_CHUNK_OVERLAP = 80

VECTOR_STORE_BACKEND = "milvus"
EMBEDDING_PROVIDER = "local_lexical"

MILVUS_URI = "http://localhost:19530"
MILVUS_COLLECTION = "smart_campus_knowledge"
MILVUS_PARENT_CHILD_COLLECTION = "smart_campus_knowledge_parent_child"
MILVUS_DIMENSION = 384
MILVUS_METRIC_TYPE = "COSINE"


def knowledge_base_root(root_dir: str = KNOWLEDGE_BASE_DIR) -> Path:
    path = Path(root_dir)
    if path.is_absolute():
        return path
    return AI_SERVER_ROOT / path
