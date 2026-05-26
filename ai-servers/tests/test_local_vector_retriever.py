import json
import tempfile
import unittest
from pathlib import Path

from app.rag.retrievers.vector import VectorRetriever
from app.rag.retrievers.keyword import KeywordRetriever
from app.rag.retrievers.hybrid import HybridRetriever
from app.rag.retrievers.parent_child import ParentChildRetriever
from app.rag.rerankers import LexicalReranker
from app.rag.core.types import RagDocument, RagQuery
from app.rag.evaluators import RetrievalGrader
from app.rag.engine import rag_engine
from app.rag.query_transformers.hyde import HydeTransformer
from app.rag.query_transformers.multi_query import MultiQueryTransformer
from app.multi_agents.textbook_knowledge_agent.agent import TextbookKnowledgeAgent
from app.rag.retrievers import java_backend_retriever


class LocalVectorRetrieverTest(unittest.TestCase):
    def test_rag_engine_registers_all_document_strategies(self):
        expected = {
            "naive_rag",
            "multi_query_rag",
            "hyde",
            "semantic_chunking",
            "parent_child",
            "hybrid_search",
            "reranking",
            "crag",
            "self_rag",
            "adaptive_rag",
            "graph_rag",
            "text_to_sql",
            "agentic_rag",
            "multi_agent_rag",
            "multimodal_rag",
            "speculative_rag",
        }

        self.assertEqual(expected, set(rag_engine.list_strategies()))

    def test_all_rag_strategies_are_runtime_runnable(self):
        query = RagQuery(text="校园卡补办地点和食堂优惠券统计", keyword="校园卡补办")

        for strategy_name in rag_engine.list_strategies():
            with self.subTest(strategy_name=strategy_name):
                result = rag_engine.get_strategy(strategy_name).run(query)

                self.assertEqual(strategy_name, result.strategy)
                self.assertTrue(result.metadata.get("implemented"))
                self.assertTrue(result.trace)

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

    def test_retrievers_read_local_chunk_index_first(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            index_dir = root / ".index"
            index_dir.mkdir()
            indexed_source = str(root / "api_ingest" / "card.md")
            (index_dir / "local_chunks.jsonl").write_text(
                json.dumps({
                    "id": "indexed-card#chunk-0",
                    "content": "校园卡补办地点在行政楼一楼服务大厅，需要携带学生证。",
                    "source": indexed_source,
                    "metadata": {"chunkIndex": 0, "modality": "markdown"},
                }, ensure_ascii=False) + "\n",
                encoding="utf-8",
            )

            vector_results = VectorRetriever(root_dir=temp_dir).search("校园卡补办地点", top_k=2)
            keyword_results = KeywordRetriever(root_dir=temp_dir).search("校园卡补办地点", top_k=2)
            hybrid_results = HybridRetriever(root_dir=temp_dir).search("校园卡补办地点", top_k=2)

            self.assertTrue(vector_results)
            self.assertTrue(keyword_results)
            self.assertTrue(hybrid_results)
            self.assertEqual("indexed-card#chunk-0", vector_results[0].id)
            self.assertEqual("local_jsonl", vector_results[0].metadata.get("indexSource"))
            self.assertEqual("local_jsonl", keyword_results[0].metadata.get("indexSource"))
            self.assertEqual("local_jsonl", hybrid_results[0].metadata.get("indexSource"))

    def test_parent_child_retriever_returns_parent_context(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            (root / "guide.md").write_text(
                "校园卡服务指南。\n\n"
                "补办地点在行政楼一楼服务大厅，需要携带学生证。\n\n"
                "补办后可在食堂、图书馆和体育馆继续使用。",
                encoding="utf-8",
            )

            retriever = ParentChildRetriever(root_dir=temp_dir)
            results = retriever.search("补办地点 学生证", top_k=2)

            self.assertTrue(results)
            self.assertIn("校园卡服务指南", results[0].content)
            self.assertIn("matchedChildContent", results[0].metadata)

    def test_lexical_reranker_prioritizes_term_coverage(self):
        documents = [
            RagDocument(id="a", content="校园卡可以在线查看余额。", score=0.5),
            RagDocument(id="b", content="校园卡补办地点在行政楼一楼服务大厅。", score=0.1),
        ]

        reranker = LexicalReranker()
        results = reranker.rerank("校园卡补办地点", documents)

        self.assertEqual("b", results[0].id)
        self.assertTrue(results[0].metadata.get("rerankScore"))

    def test_retrieval_grader_scores_relevance(self):
        grader = RetrievalGrader()
        relevant = grader.grade(
            "校园卡补办地点",
            [RagDocument(id="doc", content="校园卡补办地点在行政楼一楼服务大厅。", score=0.2)],
        )
        irrelevant = grader.grade(
            "校园卡补办地点",
            [RagDocument(id="doc", content="体育馆羽毛球场地预约。", score=0.0)],
        )

        self.assertTrue(relevant.sufficient)
        self.assertFalse(irrelevant.sufficient)

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

    def test_textbook_knowledge_agent_returns_document_meta(self):
        old_enabled = java_backend_retriever.enabled
        try:
            java_backend_retriever.enabled = False
            with tempfile.TemporaryDirectory() as temp_dir:
                root = Path(temp_dir)
                (root / "notice.md").write_text("校园卡补办地点在行政楼一楼服务大厅。", encoding="utf-8")

                agent = TextbookKnowledgeAgent()
                agent.vector_retriever = VectorRetriever(root_dir=temp_dir, chunk_size=80, overlap=10)
                agent.hybrid_retriever = HybridRetriever(root_dir=temp_dir)
                agent.parent_child_retriever = ParentChildRetriever(root_dir=temp_dir)
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

    def test_textbook_knowledge_agent_supports_parent_child(self):
        old_enabled = java_backend_retriever.enabled
        try:
            java_backend_retriever.enabled = False
            with tempfile.TemporaryDirectory() as temp_dir:
                root = Path(temp_dir)
                (root / "guide.md").write_text(
                    "校园卡服务指南。\n\n补办地点在行政楼一楼服务大厅，需要携带学生证。",
                    encoding="utf-8",
                )

                agent = TextbookKnowledgeAgent()
                agent.parent_child_retriever = ParentChildRetriever(root_dir=temp_dir)
                results, meta = agent.retrieve_with_meta(
                    authorization="Bearer test",
                    intent="campus_search",
                    keyword="校园卡补办",
                    input_text="补办地点在哪里",
                    rag_strategy="parent_child",
                )

                self.assertTrue(results)
                self.assertEqual("parent_child", meta["documentRetriever"])
                self.assertTrue(meta["parentChildEnabled"])
                self.assertIn("matchedChildContent", results[0]["metadata"])
        finally:
            java_backend_retriever.enabled = old_enabled

    def test_textbook_knowledge_agent_supports_crag_repair(self):
        old_enabled = java_backend_retriever.enabled
        try:
            java_backend_retriever.enabled = False
            with tempfile.TemporaryDirectory() as temp_dir:
                root = Path(temp_dir)
                (root / "repair.md").write_text(
                    "校园服务指南：校园卡补办地点在行政楼一楼服务大厅。",
                    encoding="utf-8",
                )

                agent = TextbookKnowledgeAgent()
                agent.vector_retriever = VectorRetriever(root_dir=temp_dir, chunk_size=80, overlap=10)
                agent.hybrid_retriever = HybridRetriever(root_dir=temp_dir)
                results, meta = agent.retrieve_with_meta(
                    authorization="Bearer test",
                    intent="campus_search",
                    keyword="完全无关词",
                    input_text="校园卡补办地点",
                    rag_strategy="crag",
                )

                self.assertTrue(results)
                self.assertEqual("hybrid_search+retrieval_grader+repair", meta["documentRetriever"])
                self.assertTrue(meta["correctiveEnabled"])
                self.assertIn(meta["correctiveAction"], {"none", "multi_query_repair+rerank"})
                self.assertIn("retrievalGrade", meta)
        finally:
            java_backend_retriever.enabled = old_enabled

    def test_textbook_knowledge_agent_supports_multi_query_and_hyde(self):
        old_enabled = java_backend_retriever.enabled
        try:
            java_backend_retriever.enabled = False
            with tempfile.TemporaryDirectory() as temp_dir:
                root = Path(temp_dir)
                (root / "card.md").write_text("校园卡补办地点在行政楼一楼服务大厅。", encoding="utf-8")

                agent = TextbookKnowledgeAgent()
                agent.vector_retriever = VectorRetriever(root_dir=temp_dir, chunk_size=80, overlap=10)
                agent.hybrid_retriever = HybridRetriever(root_dir=temp_dir)
                agent.parent_child_retriever = ParentChildRetriever(root_dir=temp_dir)

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
