import tempfile
import unittest
from pathlib import Path

from app.rag.retrievers.vector import VectorRetriever
from app.rag.retrievers.keyword import KeywordRetriever
from app.rag.retrievers.hybrid import HybridRetriever
from app.rag.rerankers import LexicalReranker
from app.rag.core.types import RagDocument
from app.rag.query_transformers.hyde import HydeTransformer
from app.rag.query_transformers.multi_query import MultiQueryTransformer
from app.multi_agents.retriever_agent.agent import RetrieverAgent
from app.rag.retrievers import java_backend_retriever


class LocalVectorRetrieverTest(unittest.TestCase):
    def test_search_markdown_documents(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            (root / "library.md").write_text(
                "图书馆开放时间为早上八点到晚上十点，期末周会延长开放。",
                encoding="utf-8",
            )
            (root / "canteen.md").write_text(
                "第一学生餐厅提供黄焖鸡和麻辣烫。",
                encoding="utf-8",
            )

            retriever = VectorRetriever(root_dir=temp_dir, chunk_size=80, overlap=10)
            results = retriever.search("图书馆开放时间", top_k=2)

            self.assertTrue(results)
            self.assertIn("图书馆", results[0].content)
            self.assertTrue(results[0].score and results[0].score > 0)

    def test_keyword_retriever_searches_exact_terms(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            (root / "card.md").write_text("校园卡补办地点在行政楼一楼服务大厅。", encoding="utf-8")
            (root / "sports.md").write_text("体育馆开放羽毛球预约。", encoding="utf-8")

            retriever = KeywordRetriever(root_dir=temp_dir, chunk_size=80, overlap=10)
            results = retriever.search("校园卡补办", top_k=2)

            self.assertTrue(results)
            self.assertIn("校园卡补办", results[0].content)
            self.assertTrue(results[0].metadata.get("keywordScore"))

    def test_hybrid_retriever_fuses_keyword_and_vector_results(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            (root / "library.md").write_text("图书馆开放时间为早上八点到晚上十点。", encoding="utf-8")

            retriever = HybridRetriever(root_dir=temp_dir)
            results = retriever.search("图书馆开放时间", top_k=2)

            self.assertTrue(results)
            self.assertEqual("library.md", Path(results[0].source).name)
            self.assertTrue(results[0].metadata.get("hybridScore"))

    def test_lexical_reranker_prioritizes_term_coverage(self):
        documents = [
            RagDocument(id="a", content="校园卡可以在线查看余额。", score=0.5),
            RagDocument(id="b", content="校园卡补办地点在行政楼一楼服务大厅。", score=0.1),
        ]

        reranker = LexicalReranker()
        results = reranker.rerank("校园卡补办地点", documents)

        self.assertEqual("b", results[0].id)
        self.assertTrue(results[0].metadata.get("rerankScore"))

    def test_multi_query_transformer_expands_query(self):
        transformer = MultiQueryTransformer()
        queries = transformer.transform("校园卡在哪里补办")

        self.assertGreaterEqual(len(queries), 3)
        self.assertIn("校园卡在哪里补办", queries)
        self.assertTrue(any("办理" in query for query in queries))

    def test_hyde_transformer_builds_hypothetical_document(self):
        transformer = HydeTransformer()
        document = transformer.transform("校园卡在哪里补办")

        self.assertIn("校园卡在哪里补办", document)
        self.assertIn("办理地点", document)

    def test_retriever_agent_returns_document_meta(self):
        old_enabled = java_backend_retriever.enabled
        try:
            java_backend_retriever.enabled = False
            with tempfile.TemporaryDirectory() as temp_dir:
                root = Path(temp_dir)
                (root / "notice.md").write_text("校园卡补办地点在行政楼一楼服务大厅。", encoding="utf-8")

                agent = RetrieverAgent()
                agent.vector_retriever = VectorRetriever(root_dir=temp_dir, chunk_size=80, overlap=10)
                agent.hybrid_retriever = HybridRetriever(root_dir=temp_dir)
                results, meta = agent.retrieve_with_meta(
                    authorization="Bearer test",
                    intent="campus_search",
                    keyword="校园卡补办",
                    input_text="校园卡在哪里补办",
                    rag_strategy="reranking",
                )

                self.assertEqual(0, meta["javaBackendCount"])
                self.assertGreaterEqual(meta["documentCount"], 1)
                self.assertEqual("hybrid_search+lexical_reranker", meta["documentRetriever"])
                self.assertTrue(meta["rerankingEnabled"])
                self.assertEqual("knowledge_document", results[0]["type"])
        finally:
            java_backend_retriever.enabled = old_enabled

    def test_retriever_agent_supports_multi_query_and_hyde(self):
        old_enabled = java_backend_retriever.enabled
        try:
            java_backend_retriever.enabled = False
            with tempfile.TemporaryDirectory() as temp_dir:
                root = Path(temp_dir)
                (root / "card.md").write_text("校园卡补办地点在行政楼一楼服务大厅。", encoding="utf-8")

                agent = RetrieverAgent()
                agent.vector_retriever = VectorRetriever(root_dir=temp_dir, chunk_size=80, overlap=10)
                agent.hybrid_retriever = HybridRetriever(root_dir=temp_dir)

                multi_results, multi_meta = agent.retrieve_with_meta(
                    authorization="Bearer test",
                    intent="campus_search",
                    keyword="校园卡补办",
                    input_text="校园卡在哪里补办",
                    rag_strategy="multi_query_rag",
                )
                hyde_results, hyde_meta = agent.retrieve_with_meta(
                    authorization="Bearer test",
                    intent="campus_search",
                    keyword="校园卡补办",
                    input_text="校园卡在哪里补办",
                    rag_strategy="hyde",
                )

                self.assertTrue(multi_results)
                self.assertEqual("vector+multi_query", multi_meta["documentRetriever"])
                self.assertTrue(multi_meta["transformedQueries"])
                self.assertTrue(hyde_results)
                self.assertEqual("vector+hyde", hyde_meta["documentRetriever"])
                self.assertEqual("hyde", hyde_meta["queryTransform"])
        finally:
            java_backend_retriever.enabled = old_enabled


if __name__ == "__main__":
    unittest.main()
