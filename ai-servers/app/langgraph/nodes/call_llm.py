from app.langgraph.state import ConversationState
from app.multi_agents.image_agent.agent import image_agent
from app.multi_agents.leader_agent.agent import leader_agent
from app.multi_agents.md_knowledge_agent.agent import md_knowledge_agent
from app.multi_agents.mind_map_agent.agent import mind_map_agent
from app.multi_agents.ppt_agent.agent import ppt_agent
from app.multi_agents.textbook_knowledge_agent.agent import textbook_knowledge_agent
from app.multi_agents.textbook_question_bank_agent.agent import textbook_question_bank_agent
from app.services.langchain_chat_service import get_chat_service


def call_llm_node(state: ConversationState) -> None:
    if state.intent == "mind_map":
        state.answer = mind_map_agent.build_mind_map(state.input_text, state.matched_results)
    elif state.intent == "md_knowledge":
        state.answer = md_knowledge_agent.extract_knowledge_points(state.input_text, state.matched_results)
    elif state.intent == "textbook_knowledge":
        state.answer = textbook_knowledge_agent.summarize_knowledge_points(state.input_text, state.matched_results)
    elif state.intent == "question_bank":
        state.answer = textbook_question_bank_agent.generate_questions(state.input_text, state.matched_results)
    elif state.intent == "ppt":
        state.answer = ppt_agent.build_outline(state.input_text, state.matched_results)
    elif state.intent == "image":
        state.answer = image_agent.build_image_prompt(state.input_text, state.matched_results)
    else:
        state.answer = leader_agent.answer(
            prompt=state.prompt,
            input_text=state.input_text,
            history=state.history,
            search_keyword=state.search_keyword,
            search_results=state.matched_results,
            chat_service=get_chat_service(),
        )
    state.trace.append({
        "stage": "answer",
        "detail": {
            "agent": state.retrieval_meta.get("targetAgent", "leader_agent"),
            "intent": state.intent,
            "answerLength": len(state.answer or ""),
            "matchedCount": len(state.matched_results or []),
        },
    })
