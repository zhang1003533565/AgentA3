class CriticAgent:
    def refine(self, answer: str) -> str:
        return (answer or "").strip()


critic_agent = CriticAgent()
