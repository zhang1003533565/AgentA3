// 文件路径：mini_program_app/api/watermark.js
import { request } from '@/utils/request.js'

// 1. 获取历史记录列表
export const getHistoryList = () => {
	return request({ url: '/history/list', method: 'GET' });
};

// 2. 新增一条历史记录
export const addHistory = (data) => {
	return request({ url: '/history/add', method: 'POST', data: data });
};

// 3. 删除单条历史记录
export const deleteHistory = (id) => {
	return request({ url: '/history/delete/' + id, method: 'DELETE' });
};