<script>
        import { getMessageState, startMessageSync, refreshMessageState, refreshChatListState } from '@/utils/messageStore'

        const APP_MESSAGE_REFRESH_INTERVAL = 30000
        const APP_FULL_REFRESH_INTERVAL = 120000
        let lastFullRefreshAt = 0

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
                        startMessageSync()
                        const lastSyncAt = Number(getMessageState().lastSyncAt || 0)
                        if (Date.now() - lastSyncAt > APP_MESSAGE_REFRESH_INTERVAL) {
                                const now = Date.now()
                                if (now - lastFullRefreshAt > APP_FULL_REFRESH_INTERVAL) {
                                        lastFullRefreshAt = now
                                        refreshMessageState('app-show')
                                } else {
                                        refreshChatListState('app-show')
                                }
                        }
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
	.uni-scroll-view::-webkit-scrollbar,
	.uni-scroll-view-content::-webkit-scrollbar {
		width: 0;
		height: 0;
		display: none;
	}
</style>
