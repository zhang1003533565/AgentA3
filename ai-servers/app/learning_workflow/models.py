from typing import Annotated, Any, Dict, List, Literal, Optional, Union

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


ResourceType = Literal[
    "knowledge_note",
    "mind_map",
    "practice_set",
    "code_lab",
    "extended_reading",
]
QuestionType = Literal[
    "single_choice",
    "multiple_choice",
    "true_false",
    "fill_blank",
    "code_output",
]
REQUIRED_QUESTION_TYPES = frozenset(
    {"single_choice", "multiple_choice", "true_false", "fill_blank", "code_output"}
)


class StrictWorkflowModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


def _normalize_non_empty_unique(values: List[str], field_name: str) -> List[str]:
    normalized = [str(value).strip() for value in values]
    if any(not value for value in normalized):
        raise ValueError(f"{field_name} must not contain blank values")
    if len(set(normalized)) != len(normalized):
        raise ValueError(f"{field_name} must be unique")
    return normalized


class ResourceBrief(StrictWorkflowModel):
    resourceType: ResourceType
    goal: str = Field(min_length=1)
    difficulty: str = ""
    constraints: List[str] = Field(default_factory=list)
    evidenceIds: List[str] = Field(min_length=1)

    @field_validator("goal")
    @classmethod
    def strip_goal(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("goal must not be blank")
        return value

    @field_validator("evidenceIds")
    @classmethod
    def validate_evidence_ids(cls, values: List[str]) -> List[str]:
        return _normalize_non_empty_unique(values, "evidenceIds")


class LearningPathItem(StrictWorkflowModel):
    order: int = Field(ge=1)
    title: str = Field(min_length=1)
    goal: str = Field(min_length=1)
    evidenceIds: List[str] = Field(min_length=1)

    @field_validator("title", "goal")
    @classmethod
    def strip_required_text(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("value must not be blank")
        return value

    @field_validator("evidenceIds")
    @classmethod
    def validate_evidence_ids(cls, values: List[str]) -> List[str]:
        return _normalize_non_empty_unique(values, "evidenceIds")


class LearningPathDraft(StrictWorkflowModel):
    title: str = Field(min_length=1)
    goal: str = Field(min_length=1)
    items: List[LearningPathItem] = Field(min_length=1)
    personalizationReasons: List[str] = Field(min_length=1)

    @field_validator("title", "goal")
    @classmethod
    def strip_required_text(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("value must not be blank")
        return value

    @field_validator("personalizationReasons")
    @classmethod
    def validate_personalization_reasons(cls, values: List[str]) -> List[str]:
        return _normalize_non_empty_unique(values, "personalizationReasons")

    @model_validator(mode="after")
    def validate_item_order(self):
        orders = [item.order for item in self.items]
        expected = list(range(1, len(self.items) + 1))
        if orders != expected:
            raise ValueError(f"items order must be contiguous from 1: {orders}")
        return self


class LearningPlan(StrictWorkflowModel):
    pathDraft: LearningPathDraft
    resourceBriefs: List[ResourceBrief] = Field(min_length=1)
    evidenceIds: List[str] = Field(min_length=1)

    @field_validator("evidenceIds")
    @classmethod
    def validate_evidence_ids(cls, values: List[str]) -> List[str]:
        return _normalize_non_empty_unique(values, "evidenceIds")

    @model_validator(mode="after")
    def validate_unique_resource_briefs(self):
        resource_types = [brief.resourceType for brief in self.resourceBriefs]
        if len(set(resource_types)) != len(resource_types):
            raise ValueError("resourceBriefs must contain unique resourceType values")
        return self


class KnowledgeNotePayload(StrictWorkflowModel):
    kind: Literal["knowledge_note"]
    note: Dict[str, Any] = Field(min_length=1)


class MindMapPayload(StrictWorkflowModel):
    kind: Literal["mind_map"]
    mindMap: Dict[str, Any] = Field(min_length=1)


class PracticeQuestion(StrictWorkflowModel):
    type: QuestionType
    stem: str = Field(min_length=1)
    options: List[Any] = Field(default_factory=list)
    answer: Any
    explanation: str = Field(min_length=1)
    evidenceIds: List[str] = Field(min_length=1)

    @field_validator("stem", "explanation")
    @classmethod
    def strip_required_text(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("value must not be blank")
        return value

    @field_validator("answer")
    @classmethod
    def validate_answer(cls, value: Any) -> Any:
        if value is None:
            raise ValueError("answer must be provided")
        if isinstance(value, str) and not value.strip():
            raise ValueError("answer must not be blank")
        if isinstance(value, (list, dict)) and not value:
            raise ValueError("answer must not be empty")
        return value

    @field_validator("evidenceIds")
    @classmethod
    def validate_evidence_ids(cls, values: List[str]) -> List[str]:
        return _normalize_non_empty_unique(values, "evidenceIds")


class PracticeSetPayload(StrictWorkflowModel):
    kind: Literal["practice_set"]
    questions: List[PracticeQuestion] = Field(min_length=1)
    metadata: Dict[str, Any] = Field(default_factory=dict)

    @model_validator(mode="after")
    def validate_required_question_types(self):
        question_types = {question.type for question in self.questions}
        missing = sorted(REQUIRED_QUESTION_TYPES - question_types)
        if missing:
            raise ValueError(f"practice_set missing required question types: {', '.join(missing)}")
        return self


class CodeLabPayload(StrictWorkflowModel):
    kind: Literal["code_lab"]
    codeLab: Dict[str, Any] = Field(min_length=1)


class ExtendedReadingPayload(StrictWorkflowModel):
    kind: Literal["extended_reading"]
    reading: Dict[str, Any] = Field(min_length=1)


ResourcePayload = Annotated[
    Union[
        KnowledgeNotePayload,
        MindMapPayload,
        PracticeSetPayload,
        CodeLabPayload,
        ExtendedReadingPayload,
    ],
    Field(discriminator="kind"),
]


class WorkflowResource(StrictWorkflowModel):
    resourceType: ResourceType
    agentName: str = Field(min_length=1)
    content: str = Field(min_length=1)
    payload: ResourcePayload
    evidenceIds: List[str] = Field(min_length=1)
    reviewStatus: Literal["pending", "passed", "rejected"] = "pending"
    reviewIssues: List[str] = Field(default_factory=list)

    @field_validator("agentName", "content")
    @classmethod
    def strip_required_text(cls, value: str) -> str:
        stripped = value.strip()
        if not stripped:
            raise ValueError("value must not be blank")
        return stripped

    @field_validator("evidenceIds")
    @classmethod
    def normalize_evidence_ids(cls, values: List[str]) -> List[str]:
        return _normalize_non_empty_unique(values, "evidenceIds")

    @model_validator(mode="after")
    def validate_payload_type(self):
        if self.payload.kind != self.resourceType:
            raise ValueError("payload kind must match resourceType")
        return self


class ResourceReviewItem(StrictWorkflowModel):
    resourceType: ResourceType
    reviewStatus: Literal["passed", "rejected"]
    reviewIssues: List[str] = Field(default_factory=list)
    evidenceIds: List[str] = Field(min_length=1)

    @field_validator("evidenceIds")
    @classmethod
    def validate_evidence_ids(cls, values: List[str]) -> List[str]:
        return _normalize_non_empty_unique(values, "evidenceIds")


class ResourceReviewResult(StrictWorkflowModel):
    reviews: List[ResourceReviewItem] = Field(min_length=1)
    evidenceIds: List[str] = Field(min_length=1)

    @field_validator("evidenceIds")
    @classmethod
    def validate_evidence_ids(cls, values: List[str]) -> List[str]:
        return _normalize_non_empty_unique(values, "evidenceIds")

    @model_validator(mode="after")
    def validate_unique_reviews(self):
        resource_types = [review.resourceType for review in self.reviews]
        if len(set(resource_types)) != len(resource_types):
            raise ValueError("reviews must contain unique resourceType values")
        return self


class ResourcePackageMetadata(StrictWorkflowModel):
    packageId: str = Field(min_length=1)
    title: str = Field(min_length=1)
    resourceCount: int = Field(ge=4)
    resourceTypes: List[ResourceType] = Field(min_length=4)
    evidenceIds: List[str] = Field(min_length=1)

    @field_validator("packageId", "title")
    @classmethod
    def strip_package_text(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("value must not be blank")
        return value

    @field_validator("evidenceIds")
    @classmethod
    def validate_evidence_ids(cls, values: List[str]) -> List[str]:
        return _normalize_non_empty_unique(values, "evidenceIds")

    @field_validator("resourceTypes")
    @classmethod
    def validate_unique_resource_types(cls, values: List[str]) -> List[str]:
        if len(set(values)) != len(values):
            raise ValueError("resourceTypes must be unique")
        return values


class LearningWorkflowRequest(StrictWorkflowModel):
    workflowId: str = Field(min_length=1)
    userId: int
    courseKey: Literal["python"]
    topic: str = Field(min_length=1, max_length=500)
    profileSnapshot: Dict[str, Any]
    masterySnapshot: List[Dict[str, Any]]
    pathSnapshot: Dict[str, Any]
    references: List[Dict[str, Any]] = Field(min_length=1)
    requestedResourceTypes: List[str] = Field(min_length=1)

    @field_validator("workflowId", "topic")
    @classmethod
    def strip_request_text(cls, value: str) -> str:
        stripped = value.strip()
        if not stripped:
            raise ValueError("value must not be blank")
        return stripped

    @field_validator("references")
    @classmethod
    def canonicalize_references(cls, values: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        normalized: List[Dict[str, Any]] = []
        seen = set()
        for index, reference in enumerate(values):
            if not isinstance(reference, dict):
                raise ValueError(f"references[{index}] must be an object")
            identifiers = {
                str(reference[key]).strip()
                for key in ("id", "evidenceId", "referenceId")
                if reference.get(key) is not None and str(reference[key]).strip()
            }
            if not identifiers:
                raise ValueError(f"references[{index}] missing evidence ID")
            if len(identifiers) != 1:
                raise ValueError(f"references[{index}] contains conflicting evidence IDs")
            evidence_id = next(iter(identifiers))
            if evidence_id in seen:
                raise ValueError(f"duplicate reference evidence ID: {evidence_id}")
            seen.add(evidence_id)
            canonical = {
                key: value
                for key, value in reference.items()
                if key not in {"id", "evidenceId", "referenceId"}
            }
            canonical["id"] = evidence_id
            normalized.append(canonical)
        return normalized

    @field_validator("requestedResourceTypes")
    @classmethod
    def normalize_requested_resource_types(cls, values: List[str]) -> List[str]:
        return _normalize_non_empty_unique(values, "requestedResourceTypes")


class WorkflowEvent(StrictWorkflowModel):
    sequence: int = Field(ge=1)
    stage: Literal["planning", "generation", "review", "rewrite", "packaging", "completed"]
    status: Literal["started", "completed", "failed"] = "completed"
    agentName: Optional[str] = None
    resourceType: Optional[ResourceType] = None
    message: str = ""


class LearningWorkflowResult(StrictWorkflowModel):
    workflowId: str
    status: Literal["completed"] = "completed"
    events: List[WorkflowEvent]
    resources: List[WorkflowResource]
    packageMetadata: ResourcePackageMetadata
    pathDraft: LearningPathDraft


__all__ = [
    "CodeLabPayload",
    "ExtendedReadingPayload",
    "KnowledgeNotePayload",
    "LearningPathDraft",
    "LearningPathItem",
    "LearningPlan",
    "LearningWorkflowRequest",
    "LearningWorkflowResult",
    "MindMapPayload",
    "PracticeQuestion",
    "PracticeSetPayload",
    "ResourceBrief",
    "ResourcePackageMetadata",
    "ResourcePayload",
    "ResourceReviewItem",
    "ResourceReviewResult",
    "ResourceType",
    "WorkflowEvent",
    "WorkflowResource",
]
