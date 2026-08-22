<script>
	export default {
		globalData: {
			currentTab: 'index'  // 底部导航当前高亮：index | activity | message | mine
		},
		onLaunch: function() {
			console.log('App Launch')
		},
		onShow: function() {
			console.log('App Show')
		},
		onHide: function() {
			console.log('App Hide')
		}
	}
</script>

<style>
	/* 工业级画布：全局 body/页面背景极浅灰 */
	page {
		background-color: #F7F7F9;
	}

	html,
	body,
	page,
	uni-page-body,
	.uni-page-body,
	uni-scroll-view,
	.uni-scroll-view,
	.uni-scroll-view-content {
		scrollbar-width: none;
		-ms-overflow-style: none;
	}

	html::-webkit-scrollbar,
	body::-webkit-scrollbar,
	page::-webkit-scrollbar,
	uni-page-body::-webkit-scrollbar,
	.uni-page-body::-webkit-scrollbar,
	uni-scroll-view::-webkit-scrollbar,
	.uni-scroll-view-content::-webkit-scrollbar {
		width: 0;
		height: 0;
		display: none;
	}
</style>
