"""
AI Resume Edit Agent - Core Implementation
Optimizes existing resumes through analysis and suggestions
"""

from typing import Any, Dict, List, Optional


class ResumeEditAgent:
    """AI Resume Editing Agent
    
    This agent analyzes user's existing resume and provides
    professional optimization suggestions.
    """
    
    def __init__(self):
        self.current_resume = {}
        self.optimization_history = []
        self.current_section = None
        
    async def process_request(
        self,
        user_message: str,
        context: Optional[List[Dict[str, Any]]] = None,
        uploaded_resume: Optional[Dict[str, Any]] = None,
        target_position: Optional[str] = None,
        **kwargs
    ) -> Dict[str, Any]:
        """Process user request for resume editing
        
        Args:
            user_message: User's input message
            context: Conversation history
            uploaded_resume: Parsed resume JSON from frontend
            target_position: Target job position for optimization
            
        Returns:
            Response with analysis, suggestions, and optimizations
        """
        # Initialize or update resume data
        if uploaded_resume:
            self.current_resume = uploaded_resume
        
        # Analyze and optimize resume
        response = await self._analyze_and_optimize(user_message, target_position)
        
        return response
    
    async def _analyze_and_optimize(
        self,
        message: str,
        target_position: Optional[str] = None
    ) -> Dict[str, Any]:
        """Analyze resume and provide optimization suggestions"""
        
        if not self.current_resume:
            return {
                'intent': 'analyze',
                'answer': '请先上传您的简历内容，我可以帮您分析并优化。',
                'suggestions': [],
                'optimized_data': None,
                'action': 'waiting_for_upload'
            }
        
        # Analyze each section
        optimization_suggestions = await self._generate_suggestions()
        
        # Check if optimization is complete
        if self._is_optimization_complete():
            return {
                'intent': 'export_optimized',
                'answer': '我已经完成了所有优化建议，请问是否满意？如果可以，我可以生成最终的优化版简历。',
                'optimization_suggestions': optimization_suggestions,
                'optimized_data': self.current_resume,
                'action': 'exporting',
                'can_export': True
            }
        else:
            return {
                'intent': 'optimize_section',
                'answer': f'我发现了 {len(optimization_suggestions)} 处可以优化的地方：\n\n' + 
                         '\n'.join([f'- {s["reason"]}' for s in optimization_suggestions[:3]]),
                'optimization_suggestions': optimization_suggestions[:5],  # Show top 5 suggestions
                'current_resume': self.current_resume,
                'action': 'suggesting',
                'can_export': False
            }
    
    async def _generate_suggestions(self) -> List[Dict[str, Any]]:
        """Generate optimization suggestions for all sections"""
        suggestions = []
        
        # Personal info optimization
        if 'personal_info' in self.current_resume:
            pi_suggestions = await self._optimize_personal_info()
            suggestions.extend(pi_suggestions)
        
        # Education optimization
        if 'education' in self.current_resume:
            edu_suggestions = await self._optimize_education()
            suggestions.extend(edu_suggestions)
        
        # Work experience optimization (highest priority)
        if 'work_experience' in self.current_resume:
            work_suggestions = await self._optimize_work_experience()
            suggestions.extend(work_suggestions)
        
        # Skills optimization
        if 'skills' in self.current_resume:
            skills_suggestions = await self._optimize_skills()
            suggestions.extend(skills_suggestions)
        
        # Projects optimization
        if 'projects' in self.current_resume:
            project_suggestions = await self._optimize_projects()
            suggestions.extend(project_suggestions)
        
        # Sort by priority
        priority_order = {'high': 0, 'medium': 1, 'low': 2}
        suggestions.sort(key=lambda x: priority_order.get(x.get('priority', 'low'), 2))
        
        return suggestions
    
    async def _optimize_personal_info(self) -> List[Dict[str, Any]]:
        """Optimize personal information section"""
        suggestions = []
        personal_info = self.current_resume.get('personal_info', {})
        
        if not personal_info.get('position'):
            suggestions.append({
                'section': 'personal_info',
                'current': '求职意向未填写',
                'suggested': '明确写出目标职位，如"Java 高级开发工程师"',
                'reason': '明确的求职意向有助于 HR 快速定位',
                'priority': 'high'
            })
        
        if not personal_info.get('phone') or not personal_info.get('email'):
            suggestions.append({
                'section': 'personal_info',
                'current': '联系方式不完整',
                'suggested': '确保提供准确的手机号码和邮箱地址',
                'reason': '完整的联系方式便于 HR 联系面试',
                'priority': 'high'
            })
        
        return suggestions
    
    async def _optimize_education(self) -> List[Dict[str, Any]]:
        """Optimize education background section"""
        suggestions = []
        education_list = self.current_resume.get('education', [])
        
        for edu in education_list:
            if not edu.get('start_date') or not edu.get('end_date'):
                suggestions.append({
                    'section': 'education',
                    'current': f'{edu.get("school", "学校")}: 缺少在校时间',
                    'suggested': f'{edu.get("school", "学校")}：YYYY.MM - YYYY.MM',
                    'reason': '清晰的在校时间有助于 HR 评估经验年限',
                    'priority': 'medium'
                })
            
            if not edu.get('gpa') and edu.get('degree') in ['本科', '硕士']:
                suggestions.append({
                    'section': 'education',
                    'current': f'{edu.get("school", "学校")}: 未突出成绩亮点',
                    'suggested': '添加 GPA、排名或奖学金信息（如：GPA 3.8/4.0, 前 10%）',
                    'reason': '优秀的成绩是学习能力的证明',
                    'priority': 'low'
                })
        
        return suggestions
    
    async def _optimize_work_experience(self) -> List[Dict[str, Any]]:
        """Optimize work experience section"""
        suggestions = []
        work_list = self.current_resume.get('work_experience', [])
        
        for idx, work in enumerate(work_list, 1):
            # Check for vague descriptions
            responsibilities = work.get('responsibilities', [])
            for resp in responsibilities:
                vague_phrases = ['负责', '参与', '做', '编写', '处理']
                if any(resp.startswith(vague) for vague in vague_phrases):
                    suggestions.append({
                        'section': 'work_experience',
                        'current': f'经历{idx}: "{resp}"',
                        'suggested': '主导 XX 模块开发，使用 XX 技术栈，减少 API 响应时间 40%',
                        'reason': '原表述过于笼统，缺乏具体的技术栈和项目背景',
                        'priority': 'high'
                    })
            
            # Check for missing quantification
            achievements = work.get('achievements', [])
            has_quantified = any(any(word in a for word in ['%', '人', '倍', '万', '元']) for a in achievements)
            if not has_quantified:
                suggestions.append({
                    'section': 'work_experience',
                    'current': '经历没有量化成果',
                    'suggested': '添加性能提升百分比、管理人数、项目规模等数据',
                    'reason': '量化的成果最能说服 HR',
                    'priority': 'high'
                })
        
        return suggestions
    
    async def _optimize_skills(self) -> List[Dict[str, Any]]:
        """Optimize skills section"""
        suggestions = []
        skills_list = self.current_resume.get('skills', [])
        
        if not skills_list:
            suggestions.append({
                'section': 'skills',
                'current': '专业技能未填写',
                'suggested': '按分类列出技能及熟练程度（精通/熟悉/了解）',
                'reason': '清晰的技术栈有助于匹配岗位需求',
                'priority': 'high'
            })
        else:
            for skill in skills_list:
                items = skill.get('items', [])
                if len(items) < 2:
                    suggestions.append({
                        'section': 'skills',
                        'current': f'{skill.get("category", "技能类别")}: 技能描述太少',
                        'suggested': '至少列出 3-5 项相关技术，并标注掌握程度',
                        'reason': '详细的技术栈展示专业深度',
                        'priority': 'medium'
                    })
        
        return suggestions
    
    async def _optimize_projects(self) -> List[Dict[str, Any]]:
        """Optimize projects section"""
        suggestions = []
        projects = self.current_resume.get('projects', [])
        
        for idx, project in enumerate(projects, 1):
            if not project.get('tech_stack'):
                suggestions.append({
                    'section': 'projects',
                    'current': f'项目{idx}: 缺少技术栈说明',
                    'suggested': '明确列出使用的主要技术和框架',
                    'reason': '技术栈是面试官重点关注的信息',
                    'priority': 'high'
                })
            
            contributions = project.get('contributions', [])
            if len(contributions) < 2:
                suggestions.append({
                    'section': 'projects',
                    'current': f'项目{idx}: 个人贡献描述不够详细',
                    'suggested': '使用 STAR 法则详细说明个人职责和成果',
                    'reason': '突出个人在项目中的实际贡献',
                    'priority': 'medium'
                })
        
        return suggestions
    
    def _is_optimization_complete(self) -> bool:
        """Check if optimization suggestions are complete"""
        return len(self.optimization_history) >= 5
    
    def reset_session(self):
        """Reset the conversation session"""
        self.current_resume = {}
        self.optimization_history = []
        self.current_section = None


# Module exports
__all__ = ['ResumeEditAgent']
__version__ = "1.0.0"
__author__ = "Campus AI Team"
