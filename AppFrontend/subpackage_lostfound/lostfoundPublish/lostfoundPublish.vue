<template>
	<view class="publish-container">
		<nav-bar :title="publishType === 'lost' ? '发布寻物' : '发布招领'" />
		
		<view class="publish-content">
			<!-- 类型切换 -->
			<view class="type-selector block-white">
				<view class="type-item" :class="{ active: publishType === 'lost' }" @click="publishType = 'lost'">
					<text class="type-text">寻物启事</text>
				</view>
				<view class="type-item" :class="{ active: publishType === 'found' }" @click="publishType = 'found'">
					<text class="type-text">招领信息</text>
				</view>
			</view>

			<!-- 表单部分 -->
			<view class="form-section block-white">
				<view class="form-item">
					<text class="label">标题</text>
					<input type="text" v-model="formData.title" placeholder="请输入物品名称" class="form-input" />
				</view>
				
				<view class="form-item">
					<text class="label">地点</text>
					<input type="text" v-model="formData.location" placeholder="请输入发现或丢失地点" class="form-input" />
				</view>

				<view class="form-item">
					<text class="label">联系方式</text>
					<input type="text" v-model="formData.contact" placeholder="请输入联系电话或微信号" class="form-input" />
				</view>

				<view class="form-item no-border">
					<text class="label">详情描述</text>
					<textarea v-model="formData.desc" placeholder="请详细描述物品特征..." class="form-textarea" />
				</view>
			</view>

			<!-- 图片上传 (模拟) -->
			<view class="image-section block-white">
				<text class="label">添加图片</text>
				<view class="upload-grid">
					<view class="upload-btn" @click="handleUpload">
						<view class="plus-icon"></view>
					</view>
				</view>
			</view>

			<view class="submit-btn-wrap">
				<button class="submit-btn" @click="handleSubmit">立即发布</button>
			</view>
		</view>
	</view>
</template>

<script>
	import NavBar from '@/components/nav-bar/nav-bar.vue'
	
	export default {
		components: { NavBar },
		data() {
			return {
				publishType: 'lost', // lost or found
				formData: {
					title: '',
					location: '',
					contact: '',
					desc: ''
				}
			}
		},
		onLoad(options) {
			if (options.type) {
				this.publishType = options.type
			}
		},
		methods: {
			handleUpload() {
				uni.chooseImage({
					count: 1,
					success: (res) => {
						uni.showToast({ title: '图片已选择', icon: 'none' })
					}
				})
			},
			handleSubmit() {
				if (!this.formData.title || !this.formData.location) {
					uni.showToast({ title: '请完善标题和地点', icon: 'none' })
					return
				}
				uni.showLoading({ title: '提交中...' })
				setTimeout(() => {
					uni.hideLoading()
					uni.showToast({ title: '发布成功', icon: 'success' })
					setTimeout(() => {
						uni.navigateBack()
					}, 1500)
				}, 1000)
			}
		}
	}
</script>

<style lang="scss">
	.publish-container {
		min-height: 100vh;
		background-color: #F7F7F9;
	}

	.publish-content {
		padding: 24rpx 32rpx;
	}

	.type-selector {
		display: flex;
		padding: 8rpx;
		border-radius: 16rpx;
		margin-bottom: 24rpx;
		
		.type-item {
			flex: 1;
			height: 72rpx;
			display: flex;
			align-items: center;
			justify-content: center;
			border-radius: 12rpx;
			transition: all 0.2s;
			
			.type-text {
				font-size: 28rpx;
				color: #8E8E93;
			}
			
			&.active {
				background-color: #7AA1D2;
				.type-text {
					color: #FFFFFF;
					font-weight: 600;
				}
			}
		}
	}

	.form-section {
		border-radius: 20rpx;
		padding: 0 32rpx;
		margin-bottom: 24rpx;
	}

	.form-item {
		display: flex;
		align-items: center;
		padding: 32rpx 0;
		border-bottom: 1rpx solid #F0F0F2;
		
		&.no-border { border-bottom: none; }
		
		.label {
			width: 160rpx;
			font-size: 30rpx;
			color: #1D1D1F;
		}
		
		.form-input {
			flex: 1;
			font-size: 30rpx;
			color: #3A3A3C;
		}
		
		.form-textarea {
			flex: 1;
			height: 200rpx;
			font-size: 30rpx;
			color: #3A3A3C;
			padding-top: 8rpx;
		}
	}

	.image-section {
		border-radius: 20rpx;
		padding: 32rpx;
		margin-bottom: 60rpx;
		
		.label {
			font-size: 30rpx;
			color: #1D1D1F;
			display: block;
			margin-bottom: 24rpx;
		}
		
		.upload-grid {
			display: flex;
			flex-wrap: wrap;
			gap: 20rpx;
		}
		
		.upload-btn {
			width: 160rpx;
			height: 160rpx;
			background-color: #F2F2F7;
			border-radius: 12rpx;
			display: flex;
			align-items: center;
			justify-content: center;
			border: 1rpx dashed #C7C7CC;
			position: relative;
			
			.plus-icon {
				width: 48rpx;
				height: 48rpx;
				position: relative;
				
				&::before, &::after {
					content: '';
					position: absolute;
					background-color: #8E8E93;
					border-radius: 2rpx;
				}
				
				&::before {
					width: 100%;
					height: 4rpx;
					left: 0;
					top: 50%;
					transform: translateY(-50%);
				}
				
				&::after {
					width: 4rpx;
					height: 100%;
					top: 0;
					left: 50%;
					transform: translateX(-50%);
				}
			}
		}
	}

	.submit-btn-wrap {
		padding: 0 40rpx;
	}

	.submit-btn {
		width: 100%;
		height: 88rpx;
		line-height: 88rpx;
		background-color: #7AA1D2;
		color: #FFFFFF;
		border-radius: 44rpx;
		font-size: 32rpx;
		font-weight: 600;
		border: none;
		&::after { border: none; }
		
		&:active {
			opacity: 0.8;
		}
	}
</style>
