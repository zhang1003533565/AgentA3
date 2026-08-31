"""
AI Generate Resume Agent
====================
Agent Code: resume_create_agent
Description: AI 生成简历智能体，支持对话式分步收集信息并生成专业简历内容
Author: Campus AI Team
Date: 2026-08-24

Core Capabilities:
1. Chat-based information gathering (personal info, education, work experience, skills, projects)
2. Step-by-step resume generation with user feedback
3. Support for different resume types and formats
4. Output structured JSON content for Java backend to convert to DOCX/PDF

Input Format:
{
  "user_id": "user123",
  "chat_context": [...],  // conversation history
  "resume_snapshot": {     // optional: previous resume data
    "personal_info": {...},
    "education": [...],
    "work_experience": [...],
    "skills": [...],
    "projects": [...]
  }
}

Output Format:
{
  "intent": "gather_info|confirm|generate_resume|revision",
  "target": "resume_create_agent",
  "answer": "自然中文回复给用户",
  "collected_data": {      // if intent == generate_resume, the complete resume data
    "personal_info": {...},
    "education": [...],
    "work_experience": [...],
    "skills": [...],
    "projects": [...]
  },
  "next_step": "询问用户接下来要补充哪部分" if intent == gather_info else null,
  "action": "continue_gathering|finalizing|exporting"
}
"""
