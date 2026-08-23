# Contract

- agentName: `python_problem_generator_agent`
- input: JSON `{topic: string, difficulty: "easy"|"medium"|"hard", count: 1..5}`
- output: strict JSON `{"problems": [...]}` aligned with the python_problem table fields
- testcases: `input` uses comma-joined named args (e.g. `nums = [2,7,11,15], target = 9`); `expected` is the judge JSON representation (array/string/number/boolean); optional `mode` (`set`/`deepset`) or `accepts` for multi-answer
- solution: 1+ entries `{name, idea, code, complexity}` with runnable reference code
- failure: invalid or incomplete JSON must not be silently fixed; missing info goes into a clear error rather than fabricated data
