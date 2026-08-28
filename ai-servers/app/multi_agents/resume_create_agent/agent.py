"""
AI Resume Create Agent - Core Implementation
Generates structured resumes through multi-turn dialogue
"""

from typing import Any, Dict, List, Optional


class ResumeCreateAgent:
    """AI Resume Creation Agent
    
    This agent guides users through a structured dialogue to collect
    resume information and generate a professional resume.
    """
    
    def __init__(self):
        self.session_state = {}
        self.collected_data = {}
        
    async def process_request(
        self,
        user_message: str,
        context: Optional[List[Dict[str, Any]]] = None,
        **kwargs
    ) -> Dict[str, Any]:
        """Process user request for resume creation
        
        Args:
            user_message: User's input message
            context: Conversation history
            **kwargs: Additional parameters
            
        Returns:
            Response with intent, answer, and collected data
        """
        # Initialize session if not exists
        if not self.session_state:
            self.session_state = {
                'current_step': 'personal_info',
                'collected_data': {}
            }
        
        # Parse user input and collect information
        response = await self._collect_and_validate(user_message)
        
        # Check if resume is complete
        if self._is_resume_complete():
            response['action'] = 'exporting'
            response['collected_data'] = self.collected_data
        else:
            response['action'] = 'continue_gathering'
            response['next_step'] = self._get_next_step_instruction()
        
        return response
    
    async def _collect_and_validate(self, user_message: str) -> Dict[str, Any]:
        """Collect and validate user input"""
        current_step = self.session_state['current_step']
        
        # Store user input
        self.session_state['current_step'] = current_step
        self.session_state['user_input'] = user_message
        
        # Process based on current step
        if current_step == 'personal_info':
            return await self._handle_personal_info(user_message)
        elif current_step == 'education':
            return await self._handle_education(user_message)
        elif current_step == 'work_experience':
            return await self._handle_work_experience(user_message)
        elif current_step == 'skills':
            return await self._handle_skills(user_message)
        elif current_step == 'projects':
            return await self._handle_projects(user_message)
        else:
            return await self._handle_confirmation(user_message)
    
    async def _handle_personal_info(self, message: str) -> Dict[str, Any]:
        """Handle personal information collection"""
        return {
            'intent': 'gather_info',
            'answer': '您好！请先告诉我您的姓名和求职意向职位是什么？',
            'step': 'personal_info',
            'required_fields': ['name', 'position', 'phone', 'email']
        }
    
    async def _handle_education(self, message: str) -> Dict[str, Any]:
        """Handle education background collection"""
        return {
            'intent': 'gather_info',
            'answer': '好的，请告诉我您的教育背景（学校、专业、学历、入学和毕业时间）:',
            'step': 'education',
            'required_fields': ['school', 'major', 'degree', 'start_date', 'end_date']
        }
    
    async def _handle_work_experience(self, message: str) -> Dict[str, Any]:
        """Handle work experience collection"""
        return {
            'intent': 'gather_info',
            'answer': '请分享您的工作经历或实习经历（公司名称、职位、工作时间、主要职责和成果）:',
            'step': 'work_experience',
            'required_fields': ['company', 'position', 'start_date', 'end_date', 'responsibilities', 'achievements']
        }
    
    async def _handle_skills(self, message: str) -> Dict[str, Any]:
        """Handle skills collection"""
        return {
            'intent': 'gather_info',
            'answer': '请列出您的专业技能（编程语言、框架、工具等）:',
            'step': 'skills',
            'required_fields': ['category', 'items']
        }
    
    async def _handle_projects(self, message: str) -> Dict[str, Any]:
        """Handle projects collection"""
        return {
            'intent': 'gather_info',
            'answer': '请描述您的项目经验（项目名称、角色、时间、描述、技术栈和个人贡献）:',
            'step': 'projects',
            'required_fields': ['name', 'role', 'time', 'description', 'tech_stack', 'contributions']
        }
    
    async def _handle_confirmation(self, message: str) -> Dict[str, Any]:
        """Handle final confirmation"""
        return {
            'intent': 'confirm',
            'answer': '请问简历内容是否完整？如果准备好了，我可以为您生成最终的简历文档。',
            'step': 'confirmation',
            'can_export': self._is_resume_complete()
        }
    
    def _is_resume_complete(self) -> bool:
        """Check if all required information has been collected"""
        return bool(self.session_state.get('collected_data'))
    
    def _get_next_step_instruction(self) -> str:
        """Get instruction for next step"""
        steps = {
            'personal_info': '请先提供您的个人信息（姓名、求职意向、联系方式）',
            'education': '请提供您的教育背景信息',
            'work_experience': '请提供您的工作或实习经历',
            'skills': '请提供您的专业技能',
            'projects': '请提供您的项目经验'
        }
        current = self.session_state.get('current_step', 'personal_info')
        return steps.get(current, '请继续补充简历信息')
    
    def reset_session(self):
        """Reset the conversation session"""
        self.session_state = {}
        self.collected_data = {}


# Module exports
__all__ = ['ResumeCreateAgent']
__version__ = "1.0.0"
__author__ = "Campus AI Team"
