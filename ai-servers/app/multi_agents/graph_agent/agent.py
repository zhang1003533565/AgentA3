from app.rag.retrievers.graph import GraphRetriever


class GraphAgent:
    def __init__(self) -> None:
        self.retriever = GraphRetriever()

    def search(self, query: str):
        return self.retriever.search_paths(query)


graph_agent = GraphAgent()
