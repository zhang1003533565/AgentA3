<template>
	<view class="login-container">
		<!-- 背景装饰 -->
		<view class="bg-decoration">
			<view class="bg-circle circle-1"></view>
			<view class="bg-circle circle-2"></view>
			<view class="bg-circle circle-3"></view>
		</view>

		<view class="login-box">
			<!-- Logo区域 -->
			<view class="logo-section">
				<view class="logo">🏫</view>
				<text class="app-name">智慧校园</text>
				<text class="app-slogan">Smart Campus</text>
			</view>

			<!-- 登录/注册表单 -->
			<view class="form-section">
				<!-- 切换标签 -->
				<view class="tab-bar">
					<text 
						class="tab-item" 
						:class="{ active: isLogin }"
						@click="switchTab(true)"
					>登录</text>
					<text 
						class="tab-item" 
						:class="{ active: !isLogin }"
						@click="switchTab(false)"
					>注册</text>
				</view>

				<!-- 表单内容 -->
				<view class="form-content">
					<view class="input-group">
						<text class="input-label">用户名</text>
						<input 
							class="input-field" 
							type="text" 
							v-model="formData.username"
							placeholder="请输入用户名"
							maxlength="50"
						/>
					</view>

					<view class="input-group">
						<text class="input-label">密码</text>
						<input 
							class="input-field" 
							:type="showPassword ? 'text' : 'password'"
							v-model="formData.password"
							placeholder="请输入密码（至少6位）"
							maxlength="100"
						/>
						<text class="eye-icon" @click="togglePassword">{{ showPassword ? '👁️' : '👁️‍🗨️' }}</text>
					</view>

					<!-- 注册额外字段 -->
					<block v-if="!isLogin">
						<view class="input-group">
							<text class="input-label">确认密码</text>
							<input 
								class="input-field" 
								:type="showConfirmPassword ? 'text' : 'password'"
								v-model="formData.confirmPassword"
								placeholder="请再次输入密码"
								maxlength="100"
							/>
							<text class="eye-icon" @click="toggleConfirmPassword">{{ showConfirmPassword ? '👁️' : '👁️‍🗨️' }}</text>
						</view>

						<view class="input-group">
							<text class="input-label">邮箱（选填）</text>
							<input 
								class="input-field" 
								type="text" 
								v-model="formData.email"
								placeholder="请输入邮箱"
								maxlength="100"
							/>
						</view>

						<view class="input-group">
							<text class="input-label">手机号（选填）</text>
							<input 
								class="input-field" 
								type="number" 
								v-model="formData.phone"
								placeholder="请输入手机号"
								maxlength="11"
							/>
						</view>
					</block>

					<!-- 登录选项 -->
					<view class="login-options" v-if="isLogin">
						<view class="remember-me" @click="rememberMe = !rememberMe">
							<text class="checkbox">{{ rememberMe ? '☑️' : '⬜' }}</text>
							<text class="option-text">记住我</text>
						</view>
						<text class="forgot-password" @click="goToForgot">忘记密码?</text>
					</view>

					<!-- 提交按钮 -->
					<button 
						class="submit-btn" 
						:loading="loading"
						:disabled="loading"
						@click="handleSubmit"
					>
						{{ loading ? (isLogin ? '登录中...' : '注册中...') : (isLogin ? '登 录' : '注 册') }}
					</button>
				</view>
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
					username: result.data.username,
					role: result.data.role,
					phone: result.data.phone
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
	.login-container {
		min-height: 100vh;
		background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
		position: relative;
		overflow: hidden;
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 40rpx;
	}

	/* 背景装饰 */
	.bg-decoration {
		position: absolute;
		top: 0;
		left: 0;
		right: 0;
		bottom: 0;
		overflow: hidden;
	}

	.bg-circle {
		position: absolute;
		border-radius: 50%;
		background: rgba(255, 255, 255, 0.1);
	}

	.circle-1 {
		width: 300rpx;
		height: 300rpx;
		top: -100rpx;
		right: -100rpx;
	}

	.circle-2 {
		width: 200rpx;
		height: 200rpx;
		bottom: -50rpx;
		left: -50rpx;
	}

	.circle-3 {
		width: 150rpx;
		height: 150rpx;
		top: 50%;
		left: 10%;
	}

	/* 登录框 */
	.login-box {
		width: 100%;
		max-width: 600rpx;
		background: white;
		border-radius: 30rpx;
		padding: 60rpx 40rpx;
		position: relative;
		z-index: 10;
		box-shadow: 0 20rpx 60rpx rgba(0, 0, 0, 0.3);
	}

	/* Logo区域 */
	.logo-section {
		display: flex;
		flex-direction: column;
		align-items: center;
		margin-bottom: 50rpx;
	}

	.logo {
		font-size: 100rpx;
		margin-bottom: 20rpx;
	}

	.app-name {
		font-size: 48rpx;
		font-weight: bold;
		color: #333;
		margin-bottom: 10rpx;
	}

	.app-slogan {
		font-size: 24rpx;
		color: #999;
	}

	/* 标签栏 */
	.tab-bar {
		display: flex;
		margin-bottom: 40rpx;
		border-bottom: 2rpx solid #f0f0f0;
	}

	.tab-item {
		flex: 1;
		text-align: center;
		padding: 20rpx 0;
		font-size: 32rpx;
		color: #999;
		position: relative;
		transition: all 0.3s;
	}

	.tab-item.active {
		color: #667eea;
		font-weight: bold;
	}

	.tab-item.active::after {
		content: '';
		position: absolute;
		bottom: -2rpx;
		left: 50%;
		transform: translateX(-50%);
		width: 60rpx;
		height: 4rpx;
		background: #667eea;
		border-radius: 2rpx;
	}

	/* 表单内容 */
	.form-content {
		display: flex;
		flex-direction: column;
		gap: 30rpx;
	}

	.input-group {
		display: flex;
		flex-direction: column;
		gap: 10rpx;
		position: relative;
	}

	.input-label {
		font-size: 26rpx;
		color: #666;
		font-weight: 500;
	}

	.input-field {
		height: 90rpx;
		background: #f8f9fa;
		border-radius: 16rpx;
		padding: 0 30rpx;
		font-size: 30rpx;
		color: #333;
		border: 2rpx solid transparent;
		transition: all 0.3s;
	}

	.input-field:focus {
		border-color: #667eea;
		background: white;
	}

	.eye-icon {
		position: absolute;
		right: 30rpx;
		bottom: 20rpx;
		font-size: 36rpx;
		padding: 10rpx;
	}

	/* 登录选项 */
	.login-options {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-top: 10rpx;
	}

	.remember-me {
		display: flex;
		align-items: center;
		gap: 10rpx;
	}

	.checkbox {
		font-size: 32rpx;
	}

	.option-text {
		font-size: 26rpx;
		color: #666;
	}

	.forgot-password {
		font-size: 26rpx;
		color: #667eea;
	}

	/* 提交按钮 */
	.submit-btn {
		margin-top: 20rpx;
		height: 100rpx;
		background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
		color: white;
		font-size: 34rpx;
		font-weight: bold;
		border-radius: 50rpx;
		border: none;
		box-shadow: 0 10rpx 30rpx rgba(102, 126, 234, 0.4);
		transition: all 0.3s;
	}

	.submit-btn:active {
		transform: scale(0.98);
		box-shadow: 0 5rpx 15rpx rgba(102, 126, 234, 0.4);
	}

	.submit-btn[disabled] {
		opacity: 0.7;
	}
</style>
