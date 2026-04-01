<template>
	<view class="login-page">
		<!-- 居中卡片 -->
		<view class="login-card">
			<!-- 标题 & 品牌 -->
			<view class="header">
				<view class="logo-icon">
					<view class="logo-symbol">SC</view>
				</view>
				<view class="title-wrapper">
					<text class="title-cn">智慧校园</text>
					<text class="title-en">SMART CAMPUS</text>
					<text class="subtitle">
						登录后即可查看校园活动、报名与签到
					</text>
				</view>
			</view>

			<!-- 登录 / 注册分段控制 -->
			<view class="tabs">
				<view class="tabs-track">
					<view
						class="tabs-slider"
						:class="{ 'tabs-slider-right': !isLogin }"
					></view>
					<view
						class="tabs-item"
						:class="{ active: isLogin }"
						@click="switchTab(true)"
					>
						<text>登录</text>
					</view>
					<view
						class="tabs-item"
						:class="{ active: !isLogin }"
						@click="switchTab(false)"
					>
						<text>注册</text>
					</view>
				</view>
			</view>

			<!-- 表单区域 -->
			<view class="form">
				<view class="input-group">
					<text class="input-label">用户名</text>
					<view class="input-shell">
						<input
							class="input-field"
							type="text"
							v-model="formData.username"
							placeholder="请输入学号/账号"
							maxlength="50"
						/>
					</view>
				</view>

				<view class="input-group">
					<text class="input-label">密码</text>
					<view class="input-shell">
						<input
							class="input-field"
							:type="showPassword ? 'text' : 'password'"
							v-model="formData.password"
							placeholder="请输入密码（至少6位）"
							maxlength="100"
						/>
						<view class="eye-icon" @click="togglePassword">
							<svg v-if="!showPassword" class="eye-svg" viewBox="0 0 24 24">
								<path
									d="M12 5C7 5 3.3 8.1 2 12c1.3 3.9 5 7 10 7s8.7-3.1 10-7c-1.3-3.9-5-7-10-7Z"
									fill="none"
									stroke="#6b7280"
									stroke-width="1.8"
									stroke-linecap="round"
									stroke-linejoin="round"
								/>
								<circle
									cx="12"
									cy="12"
									r="3"
									fill="none"
									stroke="#6b7280"
									stroke-width="1.8"
								/>
							</svg>
							<svg v-else class="eye-svg" viewBox="0 0 24 24">
								<path
									d="M4.5 4.5 19.5 19.5"
									fill="none"
									stroke="#6b7280"
									stroke-width="1.8"
									stroke-linecap="round"
								/>
								<path
									d="M5 8.5C6.8 6.4 9.3 5 12 5c4.3 0 7.9 2.8 9.5 7-0.7 2-1.9 3.6-3.4 4.8"
									fill="none"
									stroke="#6b7280"
									stroke-width="1.8"
									stroke-linecap="round"
									stroke-linejoin="round"
								/>
								<path
									d="M15.5 15.5A4 4 0 0 1 8.5 8.5"
									fill="none"
									stroke="#6b7280"
									stroke-width="1.8"
									stroke-linecap="round"
									stroke-linejoin="round"
								/>
							</svg>
						</view>
					</view>
				</view>

				<!-- 注册额外字段 -->
				<block v-if="!isLogin">
					<view class="input-group">
						<text class="input-label">确认密码</text>
						<view class="input-shell">
							<input
								class="input-field"
								:type="showConfirmPassword ? 'text' : 'password'"
								v-model="formData.confirmPassword"
								placeholder="请再次输入密码"
								maxlength="100"
							/>
							<view class="eye-icon" @click="toggleConfirmPassword">
								<svg v-if="!showConfirmPassword" class="eye-svg" viewBox="0 0 24 24">
									<path
										d="M12 5C7 5 3.3 8.1 2 12c1.3 3.9 5 7 10 7s8.7-3.1 10-7c-1.3-3.9-5-7-10-7Z"
										fill="none"
										stroke="#6b7280"
										stroke-width="1.8"
										stroke-linecap="round"
										stroke-linejoin="round"
									/>
									<circle
										cx="12"
										cy="12"
										r="3"
										fill="none"
										stroke="#6b7280"
										stroke-width="1.8"
									/>
								</svg>
								<svg v-else class="eye-svg" viewBox="0 0 24 24">
									<path
										d="M4.5 4.5 19.5 19.5"
										fill="none"
										stroke="#6b7280"
										stroke-width="1.8"
										stroke-linecap="round"
									/>
									<path
										d="M5 8.5C6.8 6.4 9.3 5 12 5c4.3 0 7.9 2.8 9.5 7-0.7 2-1.9 3.6-3.4 4.8"
										fill="none"
										stroke="#6b7280"
										stroke-width="1.8"
										stroke-linecap="round"
										stroke-linejoin="round"
									/>
									<path
										d="M15.5 15.5A4 4 0 0 1 8.5 8.5"
										fill="none"
										stroke="#6b7280"
										stroke-width="1.8"
										stroke-linecap="round"
										stroke-linejoin="round"
									/>
								</svg>
							</view>
						</view>
					</view>

					<view class="input-group">
						<text class="input-label">邮箱（选填）</text>
						<view class="input-shell">
							<input
								class="input-field"
								type="text"
								v-model="formData.email"
								placeholder="用于找回密码和通知"
								maxlength="100"
							/>
						</view>
					</view>

					<view class="input-group">
						<text class="input-label">手机号（选填）</text>
						<view class="input-shell">
							<input
								class="input-field"
								type="number"
								v-model="formData.phone"
								placeholder="用于接收活动提醒"
								maxlength="11"
							/>
						</view>
					</view>
				</block>

				<!-- 登录选项 -->
				<view class="login-options" v-if="isLogin">
					<view class="remember-me" @click="rememberMe = !rememberMe">
						<view class="checkbox" :class="{ checked: rememberMe }">
							<view class="checkbox-mark" v-if="rememberMe"></view>
						</view>
						<text class="option-text">记住我</text>
					</view>
					<text class="forgot-password" @click="goToForgot">忘记密码</text>
				</view>

				<!-- 提交按钮 -->
				<button
					class="primary-btn"
					:loading="loading"
					:disabled="loading"
					@click="handleSubmit"
				>
					{{ loading ? (isLogin ? '登录中...' : '注册中...') : (isLogin ? '登录' : '注册') }}
				</button>
			</view>
		</view>
	</view>
</template>

<script>
import { login as apiLogin, register as apiRegister } from '../../api/user.js'
import { getToken, setToken, setUserInfo } from '../../utils/storage.js'

export default {
	data() {
		return {
			isLogin: true,
			loading: false,
			showPassword: false,
			showConfirmPassword: false,
			rememberMe: false,
			formData: {
				username: '',
				password: '',
				confirmPassword: '',
				email: '',
				phone: ''
			}
		}
	},
	onLoad() {
		if (getToken()) {
			uni.switchTab({
				url: '/pages/index/index'
			})
		}
	},
	methods: {
		switchTab(isLogin) {
			this.isLogin = isLogin
			this.resetForm()
		},
		resetForm() {
			this.formData = {
				username: '',
				password: '',
				confirmPassword: '',
				email: '',
				phone: ''
			}
			this.showPassword = false
			this.showConfirmPassword = false
		},
		togglePassword() {
			this.showPassword = !this.showPassword
		},
		toggleConfirmPassword() {
			this.showConfirmPassword = !this.showConfirmPassword
		},
		validateForm() {
			if (!this.formData.username.trim()) {
				uni.showToast({ title: '请输入用户名', icon: 'none' })
				return false
			}
			if (!this.formData.password) {
				uni.showToast({ title: '请输入密码', icon: 'none' })
				return false
			}
			if (this.formData.password.length < 6) {
				uni.showToast({ title: '密码长度至少6位', icon: 'none' })
				return false
			}
			if (!this.isLogin) {
				if (!this.formData.confirmPassword) {
					uni.showToast({ title: '请确认密码', icon: 'none' })
					return false
				}
				if (this.formData.password !== this.formData.confirmPassword) {
					uni.showToast({ title: '两次密码不一致', icon: 'none' })
					return false
				}
			}
			return true
		},
		async handleSubmit() {
			if (!this.validateForm()) return

			this.loading = true
			const data = {
				username: this.formData.username.trim(),
				password: this.formData.password
			}
			if (!this.isLogin) {
				if (this.formData.email) data.email = this.formData.email.trim()
				if (this.formData.phone) data.phone = this.formData.phone.trim()
			}

			try {
				const result = this.isLogin ? await apiLogin(data) : await apiRegister(data)
				setToken(result.data.token)
				setUserInfo({
					id: result.data.id,
					userId: result.data.id,
					username: result.data.username,
					role: result.data.role,
					phone: result.data.phone,
					realName: result.data.realName,
					college: result.data.college,
					major: result.data.major,
					className: result.data.className,
					personalNumber: result.data.personalNumber,
					studentId: result.data.personalNumber
				})

				uni.showToast({
					title: this.isLogin ? '登录成功' : '注册成功',
					icon: 'success'
				})

				setTimeout(() => {
					uni.reLaunch({
						url: '/pages/index/index'
					})
				}, 1500)
			} catch (error) {
				console.error('请求错误:', error)
			} finally {
				this.loading = false
			}
		},
		goToForgot() {
			uni.showToast({
				title: '功能开发中',
				icon: 'none'
			})
		}
	}
}
</script>

<style>
	.login-page {
		min-height: 100vh;
		background: linear-gradient(135deg, #eff6ff, #ffffff 45%, #e0e7ff);
		display: flex;
		align-items: flex-start;
		justify-content: center;
		padding: 80rpx 48rpx 40rpx;
		position: relative;
		overflow: hidden;
		animation: page-fade-in 320ms ease-out;
	}

	.login-card {
		width: 100%;
		max-width: 640rpx;
		padding: 20rpx 0 56rpx;
		position: relative;
		z-index: 1;
		display: flex;
		flex-direction: column;
		row-gap: 32rpx;
	}

	/* 头部 */
	.header {
		display: flex;
		flex-direction: column;
		align-items: center;
		row-gap: 16rpx;
	}

	.logo-icon {
		width: 72rpx;
		height: 72rpx;
		border-radius: 24rpx;
	background-color: #eff6ff;
		display: flex;
		align-items: center;
		justify-content: center;
	}

	.logo-symbol {
	color: #5C7A99;
		font-size: 34rpx;
		font-weight: 600;
		letter-spacing: 2rpx;
	}

	.title-wrapper {
		display: flex;
		flex-direction: column;
		align-items: center;
		text-align: center;
	}

	.title-cn {
	font-size: 42rpx;
		font-weight: 600;
		color: #0f172a;
	margin-bottom: 4rpx;
	}

	.title-en {
		font-size: 22rpx;
		color: #6b7280;
	letter-spacing: 4rpx;
	}

	.subtitle {
	margin-top: 8rpx;
		font-size: 24rpx;
		color: #6b7280;
	}

	/* 顶部标签 */
	.tabs {
		margin-top: 32rpx;
	}

	.tabs-track {
		position: relative;
		display: flex;
		background-color: rgba(15, 23, 42, 0.04);
		backdrop-filter: blur(24rpx);
		border-radius: 24rpx;
		padding: 4rpx;
	}

	.tabs-slider {
		position: absolute;
		top: 4rpx;
		bottom: 4rpx;
		left: 4rpx;
		width: calc(50% - 4rpx);
		background-color: #ffffff;
		border-radius: 20rpx;
		transition: all 300ms ease-in-out;
		z-index: 0;
	}

	.tabs-slider-right {
		transform: translateX(100%);
	}

	.tabs-item {
		flex: 1;
		height: 72rpx;
	border-radius: 20rpx;
		display: flex;
		align-items: center;
		justify-content: center;
		font-size: 26rpx;
	color: #9ca3af;
		position: relative;
		z-index: 1;
	}

	.tabs-item.active {
	color: #111827;
	}

	/* 表单 */
	.form {
		display: flex;
		flex-direction: column;
		row-gap: 24rpx;
		margin-top: 32rpx;
	}

	.input-group {
		display: flex;
		flex-direction: column;
	row-gap: 4rpx;
	}

	.input-label {
	font-size: 22rpx;
	color: #6b7280;
	font-weight: 500;
	}

	.input-shell {
		height: 88rpx;
		border-radius: 24rpx;
		background-color: #ffffff;
		border-width: 2rpx;
		border-style: solid;
		border-color: transparent;
		padding: 0 24rpx;
		display: flex;
		align-items: center;
		transition: border-color 160ms ease-out, background-color 160ms ease-out;
	}

	.input-shell:focus-within {
		border-color: #5C7A99;
		background-color: #ffffff;
	}

	.input-field {
		flex: 1;
		font-size: 28rpx;
		color: #333333;
	}

	.input-field::placeholder {
	color: #9ca3af;
	}

	.eye-icon {
		width: 40rpx;
		height: 40rpx;
		display: flex;
		align-items: center;
		justify-content: center;
	}

	.eye-svg {
		width: 32rpx;
		height: 32rpx;
	}

	/* 登录选项 */
	.login-options {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-top: 12rpx;
	}

	.remember-me {
		display: flex;
		align-items: center;
		column-gap: 12rpx;
	}

	.checkbox {
		width: 32rpx;
		height: 32rpx;
	border-radius: 8rpx;
		border-width: 2rpx;
		border-style: solid;
	border-color: #e5e7eb;
		background-color: #ffffff;
		display: flex;
		align-items: center;
		justify-content: center;
	}

	.checkbox.checked {
		border-color: #5C7A99;
		background-color: #5C7A99;
	}

	.checkbox-mark {
		width: 16rpx;
		height: 16rpx;
		background-color: #ffffff;
	}

	.option-text {
	font-size: 24rpx;
	color: #4b5563;
	}

	.forgot-password {
	font-size: 24rpx;
	color: #5C7A99;
	font-weight: 500;
	}

	/* 主按钮 */
	.primary-btn {
		margin-top: 48rpx;
		width: 100%;
		height: 92rpx;
		line-height: 92rpx;
		border-radius: 24rpx;
		background: linear-gradient(90deg, #6B8BA4, #5C7A99);
		color: #ffffff;
		font-size: 30rpx;
		font-weight: 600;
		letter-spacing: 4rpx;
		border-width: 2rpx;
		border-style: solid;
		border-color: #4A6278;
		transition: transform 200ms ease-out, opacity 200ms ease-out;
	}

	.primary-btn:hover {
		transform: translateY(-4rpx);
	}

	.primary-btn:active {
		transform: translateY(0) scale(0.98);
	}

	.primary-btn[disabled] {
		opacity: 0.7;
	}

	/* 悬浮助手按钮 */
	/* 动效 */
	@keyframes page-fade-in {
		from {
			opacity: 0;
			transform: translate3d(0, 10rpx, 0);
		}
		to {
			opacity: 1;
			transform: translate3d(0, 0, 0);
		}
	}
</style>
