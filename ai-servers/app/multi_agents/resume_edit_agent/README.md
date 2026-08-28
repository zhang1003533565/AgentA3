"""
AI Resume Edit Agent (一键改简历)
====================================
Agent Code: resume_edit_agent
Description: AI 一键改简历智能体，支持用户上传简历后对话优化简历内容
Author: Campus AI Team
Date: 2026-08-24

Core Capabilities:
1. Upload and parse existing resume content
2. Dialogue-based optimization suggestions
3. Rewrite weak descriptions to be more professional and quantified
4. Optimize for specific job positions
5. Export optimized resume in structured JSON format

Input Format:
{
  "user_id": "user123",
  "chat_context": [...],
  "uploaded_resume": {      // parsed resume content from uploaded file
    "personal_info": {...},
    "education": [...],
    "work_experience": [...],
    "skills": [...],
    "projects": [...]
  },
  "target_position": ""     // optional: job position to optimize for
}

Output Format:
{
  "intent": "analyze|optimize_section|export_optimized",
  "target": "resume_edit_agent",
  "answer": "自然中文回复给用户",
  "optimized_data": {      // if intent == export_optimized, the fully optimized resume
    "personal_info": {...},
    "education": [...],
    "work_experience": [...],
    "skills": [...],
    "projects": [...]
  },
  "optimization_suggestions": [  // analysis phase only
    {"section": "work_experience", "current": "...", "suggested": "...", "reason": "..."}
  ],
  "action": "suggesting|editing|exporting"
}
"""
