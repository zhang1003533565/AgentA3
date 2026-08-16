from typing import Any, Dict, List

from app.services.java_backend import JavaBackendRetriever, java_backend_retriever


class DataStore:
    """
    Backward-compatible adapter.
    Adapter around Java backend APIs. Third-party knowledge-base connections belong in Java.
    """

    def __init__(self, retriever: JavaBackendRetriever) -> None:
        self._retriever = retriever

    @property
    def enabled(self) -> bool:
        return self._retriever.enabled

    @enabled.setter
    def enabled(self, value: bool) -> None:
        self._retriever.enabled = value

    @property
    def java_base_url(self) -> str:
        return self._retriever.java_base_url

    @java_base_url.setter
    def java_base_url(self, value: str) -> None:
        self._retriever.java_base_url = value

    @property
    def timeout_seconds(self) -> int:
        return self._retriever.timeout_seconds

    @timeout_seconds.setter
    def timeout_seconds(self, value: int) -> None:
        self._retriever.timeout_seconds = value

    def search_schedule(self, authorization: str, input_text: str) -> List[Dict[str, Any]]:
        return self._retriever.search_schedule(authorization, input_text)

    def search_service_tool(self, authorization: str, tool_name: str, input_text: str) -> List[Dict[str, Any]]:
        return self._retriever.search_service_tool(authorization, tool_name, input_text)

    def search_service_tool_with_meta(self, authorization: str, tool_name: str, input_text: str) -> tuple[List[Dict[str, Any]], Dict[str, Any]]:
        return self._retriever.search_service_tool_with_meta(authorization, tool_name, input_text)

    def search_keyword(self, authorization: str, keyword: str) -> List[Dict[str, Any]]:
        return self._retriever.search_keyword(authorization, keyword)

    def get_tool_cache_stats(self) -> Dict[str, Any]:
        return self._retriever.tool_cache_stats()

    def clear_tool_cache(self) -> Dict[str, Any]:
        return self._retriever.clear_tool_cache()



data_store = DataStore(java_backend_retriever)
