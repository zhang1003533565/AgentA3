<script>
	import { startMessageSync, refreshMessageState } from '@/utils/messageStore'

	export default {
		globalData: {
			currentTab: 'index'  // 底部导航当前高亮：index | activity | message | mine
		},
		onLaunch: function() {
			console.log('App Launch')
			startMessageSync()
		},
		onShow: function() {
			console.log('App Show')
			refreshMessageState('app-show')
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
</style>
