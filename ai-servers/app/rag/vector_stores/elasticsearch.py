from app.rag.vector_stores.scaffolded import ScaffoldedVectorStore


class ElasticsearchVectorStore(ScaffoldedVectorStore):
    name = "elasticsearch"
    dependency = "elasticsearch"
    required_env = ["RAG_ELASTICSEARCH_URL", "RAG_ELASTICSEARCH_INDEX"]
    optional_env = ["RAG_ELASTICSEARCH_API_KEY", "RAG_ELASTICSEARCH_USERNAME", "RAG_ELASTICSEARCH_PASSWORD"]
    description = "Elasticsearch adapter scaffold for hybrid lexical/vector retrieval."
