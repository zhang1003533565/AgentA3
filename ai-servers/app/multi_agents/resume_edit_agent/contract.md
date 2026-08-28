# Resume Edit Agent - Contract Definition

## Input Schema

```json
{
  "user_message": "string",           // User's current message
  "context": "array",                  // Conversation history
  "uploaded_resume": {                // Parsed resume JSON from frontend (optional)
    "personal_info": {...},
    "education": [...],
    "work_experience": [...],
    "skills": [...],
    "projects": [...]
  },
  "target_position": "string",        // Target job position for optimization
  "session_token": "string"            // Session identifier for state persistence
}
```

## Output Schema

### Initial Response (Waiting for Resume Upload)

```json
{
  "intent": "analyze",
  "answer": "string",                  // AI response text to user
  "action": "waiting_for_upload",      // Waiting, suggesting, editing, or exporting
  "suggestions": []                    // Empty array initially
}
```

### Analysis Response (During Optimization Process)

```json
{
  "intent": "optimize_section|analyze",
  "answer": "string",                  // AI summary of issues found
  "optimization_suggestions": [
    {
      "section": "work_experience|education|skills|projects|personal_info",
      "current": "string",             // Original content
      "suggested": "string",           // Optimized suggestion
      "reason": "string",              // Why this change is needed
      "priority": "high|medium|low"
    }
  ],
  "current_resume": {...},             // Current resume state
  "action": "suggesting",              // Or "exporting"
  "can_export": false                  // Whether ready to export
}
```

### Final Export Response (Optimization Complete)

```json
{
  "intent": "export_optimized",
  "answer": "string",                  // Confirmation message
  "optimization_suggestions": [...],   // All suggestions made
  "optimized_data": {                  // Final optimized resume
    "personal_info": {...},
    "education": [...],
    "work_experience": [...],
    "skills": [...],
    "projects": [...]
  },
  "action": "exporting",
  "can_export": true
}
```

## Example Interaction Flow

**User**: "帮我看看这份简历哪里需要优化"  
(with uploaded_resume attached)

**AI Response**:
```json
{
  "intent": "optimize_section",
  "answer": "我发现了几处可以优化的地方：\n\n1. 工作经历缺少量化成果\n2. 专业技能描述过于笼统\n3. 项目经验技术栈不明确\n\n请查看详细的优化建议...",
  "optimization_suggestions": [
    {
      "section": "work_experience",
      "current": "负责公司后台开发工作",
      "suggested": "主导公司 ERP 系统后端架构设计，使用 Spring Boot+MyBatis 技术栈",
      "reason": "原表述过于笼统，缺乏具体的技术栈和项目背景",
      "priority": "high"
    },
    {
      "section": "skills",
      "current": ["Java", "Spring"],
      "suggested": "熟练掌握 Java、Spring Boot、Spring Cloud、MyBatis、MySQL、Redis",
      "reason": "技能描述不够详细，应按掌握程度分类",
      "priority": "medium"
    }
  ],
  "current_resume": {...},
  "action": "suggesting",
  "can_export": false
}
```

**User**: "这些建议不错，我可以导出吗？"

**AI Response**:
```json
{
  "intent": "export_optimized",
  "answer": "很好！我已经完成了所有优化建议的整合。以下是最终的优化版本，可以直接导出为 PDF 或 Word 文档。",
  "optimization_suggestions": [...],
  "optimized_data": {
    "personal_info": {
      "name": "张三",
      "position": "Java 高级开发工程师",
      ...
    },
    "work_experience": [
      {
        "company": "XX 科技有限公司",
        "position": "Java 开发工程师",
        "start_date": "2023.03",
        "end_date": "至今",
        "responsibilities": [
          "主导 ERP 系统后端架构设计"
        ],
        "achievements": [
          "使用 Spring Boot 重构核心模块，API 响应时间减少 40%"
        ]
      }
    ],
    ...
  },
  "action": "exporting",
  "can_export": true
}
```

## Error Handling

- If no resume uploaded: Return action="waiting_for_upload"
- If resume format invalid: Ask user to upload in valid format
- If session timeout: Reset and request resume upload again

---

**Version**: 1.0.0  
**Last Updated**: 2026-08-24
