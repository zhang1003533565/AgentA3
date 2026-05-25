import json
import os
import time
from typing import Any, Dict, List, Optional
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from app.utils.logger import get_logger
from app.utils.text_utils import (
    format_weekday,
    parse_requested_week,
    parse_requested_weekday,
    parse_session_start,
)

logger = get_logger("rag.retrievers.java_backend")


class JavaBackendRetriever:
    def __init__(self) -> None:
        self.enabled = True
        self.java_base_url = os.getenv("JAVA_BACKEND_BASE_URL", "http://localhost:8080").rstrip("/")
        self.timeout_seconds = int(os.getenv("JAVA_BACKEND_TIMEOUT_SECONDS", "8"))

    def _get_json(self, path: str, authorization: str, params: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        if not self.enabled:
            return {}

        query = ""
        if params:
            normalized = {k: v for k, v in params.items() if v is not None and v != ""}
            if normalized:
                query = "?" + urlencode(normalized)

        url = f"{self.java_base_url}{path}{query}"
        req = Request(url, method="GET")
        req.add_header("Authorization", authorization)
        req.add_header("Accept", "application/json")

        started = time.perf_counter()
        try:
            with urlopen(req, timeout=self.timeout_seconds) as resp:
                body = resp.read().decode("utf-8")
                elapsed_ms = int((time.perf_counter() - started) * 1000)
                logger.info("java api ok path=%s elapsed_ms=%s", path, elapsed_ms)
                return json.loads(body)
        except Exception:
            elapsed_ms = int((time.perf_counter() - started) * 1000)
            logger.exception("java api failed path=%s elapsed_ms=%s disable_store=true", path, elapsed_ms)
            self.enabled = False
            return {}

    def _extract_result_data(self, payload: Dict[str, Any]) -> Any:
        if not isinstance(payload, dict):
            return None
        if payload.get("code") != 200:
            return None
        return payload.get("data")

    def search_schedule(self, authorization: str, input_text: str) -> List[Dict[str, Any]]:
        if not self.enabled:
            return []

        requested_week = parse_requested_week(input_text)
        requested_weekday = parse_requested_weekday(input_text)

        if requested_week is not None:
            payload = self._get_json(f"/api/schedule/week/{requested_week}", authorization)
        else:
            payload = self._get_json("/api/schedule/current-week", authorization)

        schedules = self._extract_result_data(payload)
        if not isinstance(schedules, list):
            return []

        if requested_weekday is not None:
            schedules = [item for item in schedules if item.get("weekday") == requested_weekday]

        schedules.sort(key=lambda item: ((item.get("weekday") or 99), parse_session_start(item.get("classSessions"))))

        results: List[Dict[str, Any]] = []
        for row in schedules[:12]:
            result = {
                "type": "course_schedule",
                "id": row.get("id"),
                "name": row.get("courseName"),
                "teacherName": row.get("teacherName"),
                "location": row.get("location"),
                "weekday": row.get("weekday"),
                "weekdayText": format_weekday(row.get("weekday")),
                "classSessions": row.get("classSessions"),
                "weekRange": row.get("weekRange"),
                "assessmentType": row.get("assessmentType"),
                "campus": row.get("campus"),
                "classCode": row.get("classCode"),
                "credit": row.get("credit"),
            }
            if requested_week is not None:
                result["requestedWeek"] = requested_week
            if requested_weekday is not None:
                result["requestedWeekday"] = requested_weekday
                result["requestedWeekdayText"] = format_weekday(requested_weekday)
            results.append(result)
        return results

    def search_keyword(self, authorization: str, keyword: str) -> List[Dict[str, Any]]:
        if not self.enabled or not keyword:
            return []

        keyword_lower = keyword.lower()
        results: List[Dict[str, Any]] = []
        matched_restaurant_ids: set[int] = set()
        matched_stall_ids: set[int] = set()
        coupon_ids: set[int] = set()

        facilities_payload = self._get_json(
            "/api/v1/facility/list",
            authorization,
            params={"type": 1, "name": keyword, "pageNum": 1, "pageSize": 20},
        )
        facilities_page = self._extract_result_data(facilities_payload)
        facilities = facilities_page.get("records", []) if isinstance(facilities_page, dict) else []
        for row in facilities[:5]:
            results.append({
                "type": "restaurant",
                "id": row.get("id"),
                "name": row.get("facilityName"),
                "location": row.get("location"),
                "description": row.get("description"),
            })
            if row.get("id") is not None:
                matched_restaurant_ids.add(int(row["id"]))

        stalls_payload = self._get_json("/api/v1/canteen-stall/list", authorization)
        stalls = self._extract_result_data(stalls_payload)
        if not isinstance(stalls, list):
            stalls = []
        matched_stalls = [
            row for row in stalls
            if self._contains_keyword(keyword_lower, row.get("stallName"), row.get("category"), row.get("location"), row.get("description"))
        ]
        matched_stalls.sort(key=lambda item: item.get("score") if item.get("score") is not None else -1, reverse=True)
        for row in matched_stalls[:8]:
            results.append({
                "type": "stall",
                "id": row.get("id"),
                "name": row.get("stallName"),
                "category": row.get("category"),
                "restaurantId": row.get("restaurantId"),
                "score": row.get("score"),
                "avgPrice": row.get("avgPrice"),
            })
            if row.get("id") is not None:
                matched_stall_ids.add(int(row["id"]))
            if row.get("restaurantId") is not None:
                matched_restaurant_ids.add(int(row["restaurantId"]))

        dish_candidates: List[Dict[str, Any]] = []
        for params in (
            {"name": keyword},
            {"category": keyword},
            {"taste": keyword},
        ):
            dishes_payload = self._get_json("/api/v1/dish/list", authorization, params=params)
            dishes = self._extract_result_data(dishes_payload)
            if isinstance(dishes, list):
                dish_candidates.extend(dishes)

        dedup_dishes: Dict[Any, Dict[str, Any]] = {}
        for dish in dish_candidates:
            dish_id = dish.get("id")
            dedup_dishes[dish_id if dish_id is not None else id(dish)] = dish

        filtered_dishes = [
            row for row in dedup_dishes.values()
            if self._contains_keyword(keyword_lower, row.get("name"), row.get("category"), row.get("taste"), row.get("description"))
        ]
        filtered_dishes.sort(key=lambda item: item.get("rating") if item.get("rating") is not None else -1, reverse=True)
        for row in filtered_dishes[:8]:
            results.append({
                "type": "dish",
                "id": row.get("id"),
                "name": row.get("name"),
                "stallId": row.get("stallId"),
                "category": row.get("category"),
                "taste": row.get("taste"),
                "rating": row.get("rating"),
                "price": row.get("price"),
            })
            if row.get("stallId") is not None:
                matched_stall_ids.add(int(row["stallId"]))

        coupons_payload = self._get_json("/api/v1/promotion-coupon/list", authorization)
        coupons = self._extract_result_data(coupons_payload)
        if not isinstance(coupons, list):
            coupons = []

        matched_coupons = [
            row for row in coupons
            if self._contains_keyword(
                keyword_lower,
                row.get("couponName"),
                row.get("description"),
                row.get("pickupLocation"),
                row.get("tagType"),
                row.get("merchantName"),
                row.get("stallName"),
                row.get("facilityName"),
            )
        ]
        for row in matched_coupons[:8]:
            if row.get("id") is not None:
                coupon_ids.add(int(row["id"]))
            results.append(self._coupon_result(row))

        for row in coupons:
            row_id = row.get("id")
            if row_id is None:
                continue
            if int(row_id) in coupon_ids:
                continue
            if row.get("stallId") in matched_stall_ids or row.get("facilityId") in matched_restaurant_ids:
                coupon_ids.add(int(row_id))
                results.append(self._coupon_result(row))

        return results[:10]

    def _contains_keyword(self, keyword: str, *fields: Any) -> bool:
        for field in fields:
            if field is None:
                continue
            text = str(field).lower()
            if keyword in text:
                return True
        return False

    def _coupon_result(self, row: Dict[str, Any]) -> Dict[str, Any]:
        return {
            "type": "coupon",
            "id": row.get("id"),
            "name": row.get("couponName"),
            "category": row.get("category"),
            "tagType": row.get("tagType"),
            "pickupLocation": row.get("pickupLocation"),
            "description": row.get("description"),
            "merchantName": row.get("merchantName"),
            "stallName": row.get("stallName"),
            "facilityName": row.get("facilityName"),
            "startDate": row.get("startDate"),
            "endDate": row.get("endDate"),
        }


java_backend_retriever = JavaBackendRetriever()
