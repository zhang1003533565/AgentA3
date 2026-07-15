from typing import Any, Dict, List, Literal, Optional

from pydantic import BaseModel, ConfigDict, Field, field_validator


ResourceType = Literal[
    "knowledge_note",
    "mind_map",
    "practice_set",
    "code_lab",
    "presentation",
    "extended_reading",
]


class StrictWorkflowModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class WorkflowResource(StrictWorkflowModel):
    resourceType: ResourceType
    agentName: str = Field(min_length=1)
    content: str = Field(min_length=1)
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
        normalized = [str(value).strip() for value in values]
        if any(not value for value in normalized):
            raise ValueError("evidenceIds must not contain blank values")
        if len(set(normalized)) != len(normalized):
            raise ValueError("evidenceIds must be unique")
        return normalized


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

    @field_validator("requestedResourceTypes")
    @classmethod
    def normalize_requested_resource_types(cls, values: List[str]) -> List[str]:
        normalized = [str(value).strip() for value in values]
        if any(not value for value in normalized):
            raise ValueError("requestedResourceTypes must not contain blank values")
        if len(set(normalized)) != len(normalized):
            raise ValueError("requestedResourceTypes must be unique")
        return normalized


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
    packageMetadata: Dict[str, Any]
    pathDraft: Dict[str, Any]


__all__ = [
    "LearningWorkflowRequest",
    "LearningWorkflowResult",
    "ResourceType",
    "WorkflowEvent",
    "WorkflowResource",
]
