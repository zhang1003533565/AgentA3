# Resume Create Agent - Contract Definition

## Input Schema

```json
{
  "user_message": "string",           // User's current message
  "context": "array",                  // Conversation history
  "session_token": "string"            // Session identifier for state persistence
}
```

## Output Schema

### Simple Response (During Information Gathering)

```json
{
  "intent": "gather_info|confirm",    // Current interaction mode
  "answer": "string",                  // AI response text to user
  "step": "personal_info|education|work_experience|skills|projects",
  "required_fields": ["string"],      // Fields to collect at this step
  "next_step": "string"                // Guidance for next input
}
```

### Final Response (After Complete Information Collection)

```json
{
  "intent": "confirm",
  "answer": "string",                  // Confirmation message
  "action": "exporting|continue_gathering",
  "collected_data": {
    "personal_info": {
      "name": "string",
      "position": "string",
      "phone": "string",
      "email": "string",
      "work_years": "string (optional)",
      "location": "string (optional)"
    },
    "education": [
      {
        "school": "string",
        "degree": "string",
        "major": "string",
        "start_date": "string",
        "end_date": "string",
        "courses": ["string"],         // Optional
        "gpa": "string",               // Optional
        "awards": ["string"]           // Optional
      }
    ],
    "work_experience": [
      {
        "company": "string",
        "position": "string",
        "start_date": "string",
        "end_date": "string",
        "responsibilities": ["string"],
        "achievements": ["string"]
      }
    ],
    "skills": [
      {
        "category": "string",
        "items": ["string"]
      }
    ],
    "projects": [
      {
        "name": "string",
        "role": "string",
        "time": "string",
        "description": "string",
        "tech_stack": ["string"],
        "contributions": ["string"]
      }
    ]
  }
}
```

## Example Interaction Flow

**User**: "帮我写一份简历"

**AI Response**:
```json
{
  "intent": "gather_info",
  "answer": "您好！很高兴帮助您生成简历。请问您的姓名和求职意向职位是什么？",
  "step": "personal_info",
  "required_fields": ["name", "position", "phone", "email"]
}
```

**User**: "我叫张三，想应聘 Java 开发工程师岗位，电话 13800138000，邮箱 zhangsan@email.com"

**AI Response**:
```json
{
  "intent": "gather_info",
  "answer": "好的，已记录您的基本信息。请告诉我您的教育背景（学校、专业、学历、入学和毕业时间）:",
  "step": "education",
  "required_fields": ["school", "major", "degree", "start_date", "end_date"]
}
```

...

**User**: "准备好了，可以生成了"

**AI Response**:
```json
{
  "intent": "confirm",
  "answer": "很好！我已收集到完整信息，将为您生成最终的简历文档。",
  "action": "exporting",
  "collected_data": {...}
}
```

## Error Handling

- If information is incomplete: Return with intent="gather_info" and next_step guidance
- If user provides unclear input: Ask for clarification with specific examples
- If session timeout: Reset and start over

---

**Version**: 1.0.0  
**Last Updated**: 2026-08-24
