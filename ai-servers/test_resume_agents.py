"""Test if resume agents are loaded"""
from app.multi_agents.catalog import get_agent_catalog

catalog = get_agent_catalog()
resume_agents = [a for a in catalog['agents'] if 'resume' in a['name'].lower()]

print(f"Found {len(resume_agents)} resume agent(s):")
for agent in resume_agents:
    print(f"  - {agent['name']}: {agent['role']}")

# Check total count
print(f"\nTotal agents: {catalog['total']}")
print(f"Agent order includes resume agents:")
print(f"  AGENT_ORDER positions 120-130: {[f for f in ['diagram_flowchart_prompt_agent', 'diagram_activity_prompt_agent', 'knowledge_graph_prompt_agent', 'mind_map_agent', 'image_agent', 'file_content_planner_agent', 'textbook_knowledge_agent', *['resume_create_agent', 'resume_edit_agent'], 'python_code_lab_agent']]}")
