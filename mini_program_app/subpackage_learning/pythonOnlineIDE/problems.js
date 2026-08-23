/**
 * Python 在线编程模块本地常量
 *
 * 题目内容已改为后端动态下发（见 api/pythonProblem.js 与 /api/python-problem/* 接口），
 * 本文件仅保留本地存储键：做题进度与代码草稿仍保存在端上，键以题目 id 为准。
 */

/** 本地存储 key：已解决题目 id 数组（进度持久化） */
export const PROGRESS_STORAGE_KEY = 'py_online_solved'

/** 本地存储 key 前缀：每道题的代码草稿，完整 key 为 CODE_DRAFT_PREFIX + 题目 id */
export const CODE_DRAFT_PREFIX = 'py_online_code_'
