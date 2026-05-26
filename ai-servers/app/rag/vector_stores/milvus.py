from app.rag.vector_stores.scaffolded import ScaffoldedVectorStore


class MilvusVectorStore(ScaffoldedVectorStore):
    name = "milvus"
    dependency = "pymilvus"
    required_env = ["RAG_MILVUS_URI", "RAG_MILVUS_COLLECTION"]
    optional_env = ["RAG_MILVUS_TOKEN", "RAG_MILVUS_DB_NAME", "RAG_MILVUS_VECTOR_FIELD"]
    description = "Milvus adapter scaffold for remote vector search and scalable RAG indexing."
