from app.rag.vector_stores.scaffolded import ScaffoldedVectorStore


class FaissVectorStore(ScaffoldedVectorStore):
    name = "faiss"
    dependency = "faiss-cpu"
    required_env = ["RAG_FAISS_INDEX_DIR"]
    optional_env = ["RAG_FAISS_NORMALIZE_L2"]
    description = "Local FAISS adapter scaffold for future dense vector retrieval."
