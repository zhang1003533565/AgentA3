{
  "questionType": "debug",
  "problem": {
    "id": 1,
    "number": 1,
    "title": "两数之和",
    "difficulty": "easy",
    "description": "给定一个整数数组 nums 和一个整数目标值 target，请你在该数组中找出和为目标值 target 的那两个整数，并返回它们的数组下标。",
    "examples": [
      {"input": ["nums = [2,7,11,15], target = 9"], "output": ["[0,1]"]}
    ],
    "tags": ["数组", "哈希表"],
    "funcName": "two_sum"
  },
  "userCode": "def two_sum(nums, target):\n    for i in range(len(nums)):\n        for j in range(i+1, len(nums)):\n            if nums[i] + nums[j] == target:\n                return [i, j]\n    return []",
  "judgeResult": {
    "status": "tle",
    "passed": 1,
    "total": 3,
    "testcases": [
      {"status": "pass", "input": "[2,7,11,15] 9", "expected": "[0, 1]", "actual": "[0, 1]"},
      {"status": "fail", "input": "[...长数组...] 目标值", "expected": "[...]", "actual": "超时"}
    ]
  },
  "followUp": null,
  "history": []
}
