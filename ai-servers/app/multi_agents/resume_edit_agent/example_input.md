# Resume Edit Agent - Example Input

## Sample User Requests

### Initial Request (with Resume Upload)
```
帮我看一下这份简历哪里需要优化
```

### With Uploaded Resume JSON
```json
{
  "personal_info": {
    "name": "张三",
    "position": "",
    "phone": "13800138000",
    "email": "zhangsan@email.com"
  },
  "education": [
    {
      "school": "北京大学",
      "major": "计算机科学与技术",
      "degree": "本科",
      "start_date": "2020.09",
      "end_date": "2024.06"
    }
  ],
  "work_experience": [
    {
      "company": "某互联网公司",
      "position": "Java 开发实习生",
      "start_date": "2023.06",
      "end_date": "2023.09",
      "responsibilities": ["负责公司后台开发工作"],
      "achievements": ["完成了实习任务"]
    }
  ],
  "skills": [
    {
      "category": "编程语言",
      "items": ["Java", "Python"]
    },
    {
      "category": "框架工具",
      "items": ["Spring Boot", "MyBatis", "MySQL"]
    }
  ],
  "projects": []
}
```

### Follow-up Questions
```
我的求职意向是 Java 开发工程师
这些建议不错，请帮我优化
可以导出最终版本了吗？
```

---

**Note**: The agent should analyze the uploaded resume, provide optimization suggestions section by section, and allow user to confirm before exporting.
