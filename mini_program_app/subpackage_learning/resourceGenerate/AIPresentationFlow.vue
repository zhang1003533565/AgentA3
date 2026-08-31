<template>
  <view class="ppt-flow" :class="{ 'ppt-flow--floating-actions': hasFloatingActions }">
    <view class="flow-heading">
      <view class="flow-heading__copy">
        <text class="flow-heading__title">{{ stepMeta[currentStep - 1].title }}</text>
      </view>
      <view v-if="canRestartFlow" class="flow-heading__actions">
        <view class="restart-generation-button" @tap="requestRestartFlow">重新生成</view>
      </view>
    </view>

    <view class="stepper-card">
      <view class="stepper-card__head">
        <text>{{ currentStep }} / {{ stepMeta.length }}</text>
      </view>
      <scroll-view class="step-scroll" scroll-x scroll-with-animation :scroll-into-view="`ppt-step-${currentStep}`" :show-scrollbar="false">
        <view class="stepper">
          <view class="stepper__track">
            <view class="stepper__track-value" :style="{ width: stepperProgress }">
              <view class="stepper__track-pulse"></view>
            </view>
          </view>
          <view
            v-for="item in stepMeta"
            :id="`ppt-step-${item.id}`"
            :key="item.id"
            class="stepper__item"
            :class="{ 'stepper__item--active': currentStep === item.id, 'stepper__item--done': currentStep > item.id, 'stepper__item--clickable': isStepNavigable(item) }"
            @tap="navigateToStep(item.id)"
          >
            <view class="stepper__marker">
              <view class="stepper__number">
                <text v-if="currentStep <= item.id">{{ item.id }}</text>
                <text v-else class="stepper__check">✓</text>
              </view>
            </view>
            <view class="stepper__copy">
              <text class="stepper__label">{{ item.shortTitle }}</text>
              <text class="stepper__state">{{ stepStateLabel(item) }}</text>
            </view>
          </view>
        </view>
      </scroll-view>
    </view>

    <view v-if="modelConfigError" class="recover-card recover-card--warning">
      <view>
        <text class="recover-card__title">模型还没有准备好</text>
        <text class="recover-card__desc">{{ lastPptError || 'PPT 生成使用已测试的模型配置。请先确认 API key 已配置并测试成功。' }}</text>
      </view>
      <button class="recover-card__button" @tap="openModelHelp">查看说明</button>
    </view>

    <view v-if="isTemplateStep || isUploadStep" class="panel">
      <view v-if="isTemplateStep && templateEntryMode === 'library'" class="template-library-entry">
        <view class="template-library-hero">
          <view class="template-library-hero__copy">
            <text class="template-library-hero__title">{{ templateHeroTitle }}</text>
            <text class="template-library-hero__desc">{{ templateHeroDescription }}</text>
          </view>
          <view class="template-library-hero__stack">
            <view></view><view></view><view></view>
          </view>
        </view>

        <scroll-view class="template-category-scroll" scroll-x :show-scrollbar="false">
          <view class="template-category-tabs">
            <view
              v-for="category in templateCategories"
              :key="category.id"
              class="template-category-tab"
              :class="{ 'template-category-tab--active': templateCategory === category.id }"
              @tap="selectTemplateCategory(category.id)"
            >
              <text>{{ category.name }}</text>
            </view>
          </view>
        </scroll-view>

        <view v-if="templateCatalogLoading" class="template-loading-card">
          <view class="template-loading-card__thumb"></view>
          <view class="template-loading-card__lines"><text></text><text></text><text></text></view>
        </view>
        <view v-else-if="filteredPptTemplates.length" class="template-library-list">
          <view
            v-for="template in filteredPptTemplates"
            :key="template.id"
            class="template-library-card"
            :class="{ 'template-library-card--selected': pptStyle === template.id }"
            @tap="selectPptTemplate(template.id)"
          >
            <image v-if="template.thumbnailUrl" class="template-library-card__thumb" :src="template.thumbnailUrl" mode="aspectFill" @error="onTemplateThumbnailError(template.id)" />
            <view v-else class="template-library-card__thumb template-library-card__thumb--fallback">
              <text></text><text></text><text></text>
            </view>
            <view class="template-library-card__body">
              <view class="template-library-card__head">
                <text class="template-library-card__name">{{ template.name }}</text>
                <text class="template-library-card__tag">{{ template.categoryLabel }}</text>
              </view>
              <text class="template-library-card__desc">{{ template.description }}</text>
              <view class="template-library-card__meta">
                <text>{{ template.layoutCount || 0 }} 种页面布局</text>
                <view class="template-library-card__actions">
                  <text v-if="pptStyle === template.id" class="template-library-card__selected">已选择</text>
                  <text @tap.stop="showTemplateDetail(template.id)">查看详情</text>
                </view>
              </view>
            </view>
          </view>
        </view>
        <view v-else class="template-empty">
          <text>{{ templateOptionsLoading ? '正在加载模板…' : (templateCatalogAvailable ? '当前分类暂无模板' : '模板目录暂不可用，请稍后重试') }}</text>
          <text v-if="!templateOptionsLoading" class="template-empty__retry" @tap="loadPptOptions(true)">重新加载模板</text>
        </view>

        <view class="bottom-actions">
          <view class="bottom-actions__buttons">
            <button v-if="currentStep > 1" class="secondary-button" :disabled="apiBusy" @tap="goPrevious">上一步</button>
            <button class="primary-button" :disabled="!selectedTemplate || apiBusy" @tap="goNext">{{ apiBusy ? '正在生成大纲…' : templateNextLabel }}</button>
          </view>
        </view>
      </view>

      <view v-else-if="isTemplateStep && templateEntryMode === 'detail'" class="template-detail-entry">
        <view class="layout-cover" @tap="openLayoutViewer">
          <view class="layout-cover__stage">
            <image v-if="layoutPreviewImages[0]" class="layout-cover__image" :src="layoutPreviewImages[0]" mode="aspectFit" />
            <view v-else class="layout-cover__placeholder">
              <text v-if="layoutPreviewFailed[`${selectedTemplate?.id}:0`]">版式图加载失败，点击重试</text>
              <text v-else>正在加载版式图…</text>
            </view>
          </view>
          <view class="layout-cover__bar">
            <view class="layout-cover__hint">
              <text class="layout-cover__title">{{ selectedTemplateName }}</text>
              <text class="layout-cover__desc">共 {{ selectedTemplateLayouts.length }} 页版式 · 点击全屏逐页翻看</text>
            </view>
            <view class="layout-cover__action"><text>全屏预览</text></view>
          </view>
        </view>

        <view class="template-detail-card">
          <view class="template-detail-card__body">
            <text class="template-detail-card__eyebrow">{{ selectedTemplateCategoryLabel }}</text>
            <text class="template-detail-card__title">{{ selectedTemplateName }}</text>
            <text class="template-detail-card__desc">{{ selectedTemplateDescription }}</text>
            <view class="template-detail-card__stats">
              <view><text>{{ selectedTemplateLayoutCount }}</text><text>版式</text></view>
            </view>
          </view>
        </view>

        <view class="bottom-actions">
          <view class="bottom-actions__buttons">
            <button class="secondary-button" :disabled="apiBusy" @tap="showTemplateLibrary">返回模板库</button>
            <button class="primary-button" :disabled="apiBusy" @tap="goNext">{{ apiBusy ? '正在生成大纲…' : templateDetailActionLabel }}</button>
          </view>
        </view>
      </view>

      <view v-else class="template-upload-entry">
        <view v-if="currentStep > 1 && selectedTemplate" class="selected-template-strip">
          <view class="selected-template-strip__preview">
            <image v-if="selectedTemplate && selectedTemplate.thumbnailUrl" :src="selectedTemplate.thumbnailUrl" mode="aspectFill" @error="onTemplateThumbnailError(selectedTemplate.id)" />
            <text v-else>{{ selectedTemplateName.slice(0, 1) }}</text>
          </view>
          <view class="selected-template-strip__main">
            <text class="selected-template-strip__label">已选模板</text>
            <text class="selected-template-strip__name">{{ selectedTemplateName }}</text>
          </view>
          <view class="selected-template-strip__actions">
            <text @tap="showTemplateDetail">查看</text>
            <text @tap="showTemplateLibrary">更换</text>
          </view>
        </view>

        <view class="product-hero product-hero--compact">
          <view class="product-hero__copy">
            <text class="product-hero__eyebrow">资料生成 · 内容优先</text>
            <text class="product-hero__title">{{ uploadHeroTitle }}</text>
            <text class="product-hero__desc">{{ uploadHeroDescription }}</text>
          </view>
          <view class="product-hero__slide">
            <text></text><text></text><text></text>
          </view>
        </view>

        <view class="outline-mode-selector">
          <view class="outline-mode-selector__head">
            <text class="outline-mode-selector__title">选择上传类型</text>
          </view>
          <view class="outline-mode-selector__options">
            <view
              v-for="mode in outlineModes"
              :key="mode.id"
              class="outline-mode-card"
              :class="{ 'outline-mode-card--selected': outlineMode === mode.id }"
              @tap="selectOutlineMode(mode.id)"
            >
              <view class="outline-mode-card__radio">
                <view v-if="outlineMode === mode.id" class="outline-mode-card__radio-dot"></view>
              </view>
              <view class="outline-mode-card__copy">
                <text class="outline-mode-card__title">{{ mode.shortName || mode.name }}</text>
                <text class="outline-mode-card__desc">{{ mode.description }}</text>
              </view>
            </view>
          </view>
        </view>

        <view class="field">
          <text class="field__label">学习资料</text>
          <view class="source-input-card">
            <view class="source-input-card__head">
              <view class="source-input-card__status">
                <view class="source-input-card__icon" :class="{ 'source-input-card__icon--empty': !fileInfo }">
                  <text v-if="fileInfo">{{ fileKindLabel }}</text>
                  <text v-else>FILE</text>
                </view>
                <view class="source-input-card__copy">
                  <text>{{ fileInfo ? fileInfo.name : '上传文件或直接粘贴内容' }}</text>
                  <text>{{ fileInfo ? `${fileInfo.sizeLabel} · ${sourceFileId ? '上传完成' : '读取完成'}` : supportedSourceHint }}</text>
                </view>
              </view>
              <view class="source-input-card__actions">
                <text @tap.stop="chooseTxtFile">{{ fileInfo && !fileInfo.manual ? '重传' : '上传文件' }}</text>
                <text v-if="fileInfo" class="source-input-card__delete" @tap.stop="removeFile">删除</text>
              </view>
            </view>
            <textarea
              v-model="manualSourceContent"
              class="source-textarea"
              :maxlength="20000"
              :placeholder="fileInfo && !fileInfo.manual && !fileContent ? '文件已上传，也可以在这里补充生成要求或粘贴额外资料' : '可以直接粘贴课堂笔记、复习提纲或老师给的资料内容'"
              @input="applyManualSourceInput"
            />
          </view>
        </view>

        <view v-if="fileInfo && fileContent" class="preview-card">
          <view class="preview-card__head">
            <text class="preview-card__title">资料预览</text>
            <text class="preview-card__count">已读取 {{ formattedCharacterCount }} 字</text>
          </view>
          <text class="preview-card__content" :class="{ 'preview-card__content--expanded': previewExpanded }">{{ previewContent }}</text>
          <view v-if="hasPreviewOverflow" class="preview-card__toggle" @tap="previewExpanded = !previewExpanded">
            <text>{{ previewExpanded ? '收起内容' : '显示全部' }}</text>
            <text class="preview-card__toggle-arrow" :class="{ 'preview-card__toggle-arrow--expanded': previewExpanded }">⌄</text>
          </view>
        </view>

        <view class="bottom-actions">
          <view class="bottom-actions__buttons">
            <button v-if="currentStep > 1" class="secondary-button" :disabled="apiBusy" @tap="goPrevious">上一步</button>
            <button class="primary-button" :disabled="!fileInfo || apiBusy" @tap="goNext">{{ apiBusy ? '正在生成大纲…' : uploadNextLabel }}</button>
          </view>
        </view>
      </view>
    </view>

    <view v-else-if="currentStep === 3" class="panel outline-panel">
      <view class="editor-toolbar">
        <view>
          <text class="editor-toolbar__title">PPT 大纲</text>
          <text class="editor-toolbar__desc">确认页面顺序和标题，生成时会写入已选模板</text>
        </view>
        <view class="outline-toolbar-actions">
          <text class="outline-page-count">{{ validOutlineItems.length || outlineItems.length }} 页</text>
          <view class="outline-history-button" @tap="openHistory('outline')">记录</view>
        </view>
      </view>

      <view class="outline-name-field">
        <text>名称</text>
        <input v-model="outlineName" :maxlength="60" placeholder="请输入大纲名称" spellcheck="false" />
      </view>

      <view class="outline-list">
        <view v-for="(item, index) in outlineItems" :key="item.id" class="outline-item">
          <view class="outline-item__head">
            <view class="outline-item__order">{{ index + 1 }}</view>
            <input v-model="item.title" :maxlength="80" placeholder="输入大纲标题" spellcheck="false" />
          </view>
          <view class="outline-item__meta">
            <picker :range="outlineLevelLabels" :value="outlineLevelIndex(item.level)" @change="updateOutlineItemLevel(index, $event)">
              <view class="outline-level-picker">
                <text>{{ outlineLevelLabel(item.level) }}</text>
                <text class="outline-level-picker__chevron"></text>
              </view>
            </picker>
            <view class="outline-item__actions">
              <text v-if="index > 0" @tap="moveOutlineItem(index, -1)">上移</text>
              <text v-if="index < outlineItems.length - 1" @tap="moveOutlineItem(index, 1)">下移</text>
              <text class="outline-item__delete" @tap="removeOutlineItem(index)">删除</text>
            </view>
          </view>
        </view>
      </view>

      <button class="add-outline-button" @tap="addOutlineItem">+ 添加页面</button>
      <view class="outline-save-tip">
        <text>大纲可保存到记录，之后可再次使用</text>
        <text v-if="outlineSavedAt">已保存 {{ outlineSavedAt }}</text>
      </view>
      <view class="bottom-actions bottom-actions--three">
        <view class="bottom-actions__buttons">
          <button class="secondary-button" :disabled="apiBusy" @tap="goPrevious">上一步</button>
          <button class="secondary-button" :disabled="apiBusy || !validOutlineItems.length" @tap="saveOutlineSnapshot(true)">保存大纲</button>
          <button class="primary-button" :disabled="validOutlineItems.length < 2" @tap="confirmOutline">下一步</button>
        </view>
      </view>
    </view>

    <view v-else-if="currentStep === 4" class="panel">
      <view v-if="selectedTemplate" class="template-usage-card">
        <view class="template-usage-card__main">
          <view class="template-usage-card__preview">
            <image v-if="selectedTemplate.thumbnailUrl" :src="selectedTemplate.thumbnailUrl" mode="aspectFill" @error="onTemplateThumbnailError(selectedTemplate.id)" />
            <text v-else>{{ selectedTemplateName.slice(0, 1) }}</text>
          </view>
          <view class="template-usage-card__copy">
            <text class="template-usage-card__label">当前模板</text>
            <text class="template-usage-card__name">{{ selectedTemplateName }}</text>
            <text class="template-usage-card__desc">{{ selectedTemplateDescription }}</text>
          </view>
        </view>
        <view class="template-usage-card__metrics">
          <view><text>{{ selectedTemplateLayoutCount }}</text><text>可用版式</text></view>
          <view><text>{{ validOutlineItems.length || outlineItems.length }}</text><text>预计页数</text></view>
        </view>
        <view class="template-usage-card__actions">
          <text @tap="showTemplateLibrary">更换模板</text>
        </view>
      </view>

      <view class="settings-section">
        <text class="settings-section__title settings-section__title--block">页面组成</text>
        <view v-for="option in pageOptions" :key="option.key" class="switch-row">
          <text>{{ option.label }}</text>
          <switch :checked="settings[option.key]" color="#5265f5" @change="toggleSetting(option.key, $event)" />
        </view>
        <view class="switch-row switch-row--visuals">
          <view>
            <text class="switch-row__title">生成辅助配图</text>
            <text class="switch-row__desc">匹配图标、流程图和结构图</text>
          </view>
          <switch :checked="settings.includeVisuals" color="#5265f5" @change="toggleSetting('includeVisuals', $event)" />
        </view>
        <view class="visual-mode-row">
          <text class="switch-row__title">配图生成方式</text>
          <view class="segmented visual-mode-segmented">
            <view v-for="mode in imageModes" :key="mode.id" class="segmented__item" :class="{ 'segmented__item--active': settings.imageMode === mode.id }" @tap="setImageMode(mode.id)">
              <text>{{ mode.name }}</text>
            </view>
          </view>
          <text class="switch-row__desc">先留空可在生成后上传替换；选择 AI 才会调用图片模型。</text>
        </view>
      </view>

      <view v-if="slideGenerationSnapshot" class="slide-task-card">
        <view class="slide-task-card__head">
          <text>{{ slideGenerationSnapshot.message || '正在生成逐页内容' }}</text>
          <text>{{ Math.min(99, Number(slideGenerationSnapshot.progress || 0)) }}%</text>
        </view>
        <view class="slide-task-card__track"><view :style="{ width: `${Math.min(99, Number(slideGenerationSnapshot.progress || 0))}%` }"></view></view>
        <view class="slide-task-card__stats">
          <view><text>{{ slideGenerationSnapshot.completedSlides || 0 }}</text><text>已完成</text></view>
          <view><text>{{ slideGenerationSnapshot.remainingSlides || 0 }}</text><text>剩余</text></view>
          <view><text>{{ slideGenerationCurrentLabel }}</text><text>当前页</text></view>
        </view>
        <text v-if="slideGenerationProcessingLabel" class="slide-task-card__processing">正在生成：{{ slideGenerationProcessingLabel }}</text>
        <button v-if="apiBusy" class="text-button slide-task-card__cancel" @tap="cancelGeneration">取消生成</button>
      </view>

      <view class="bottom-actions">
        <view class="bottom-actions__buttons">
          <button class="secondary-button" :disabled="apiBusy" @tap="goPrevious">上一步</button>
          <button class="primary-button" :disabled="apiBusy" @tap="handleSettingsNext">{{ apiBusy ? '正在生成页面…' : settingsNextLabel }}</button>
        </view>
      </view>
    </view>

    <view v-else-if="currentStep === 5" class="panel page-editor-panel">
      <view class="editor-toolbar">
        <view>
          <text class="editor-toolbar__title">逐页编辑</text>
          <text class="editor-toolbar__desc">正式生成前，确认每页标题、内容和提示词</text>
        </view>
        <view class="editor-toolbar__aside">
          <text class="page-editor-count">{{ activeSlideIndex + 1 }} / {{ slides.length }}</text>
          <text v-if="hasLastSuccessfulResult" class="editor-toolbar__result-link" @tap="returnToLastSuccessfulResult">查看上次成品</text>
        </view>
      </view>

      <scroll-view class="slide-tabs" scroll-x :scroll-into-view="`slide-tab-${activeSlideIndex}`" :show-scrollbar="false">
        <view class="slide-tabs__inner">
          <view
            v-for="(slide, index) in slides"
            :id="`slide-tab-${index}`"
            :key="slide.id"
            class="slide-tab"
            :class="{ 'slide-tab--active': activeSlideIndex === index }"
            @tap="selectEditorSlide(index)"
          >
            <text>{{ index + 1 }}</text>
            <text>{{ slide.title || '未命名页面' }}</text>
            <text v-if="slide.layoutLocked" class="slide-tab__lock">已锁定</text>
          </view>
        </view>
      </scroll-view>

      <view v-if="activeSlide" class="slide-editor">
        <view v-if="canRetryGeneration" class="retry-render-card">
          <view>
            <text class="retry-render-card__title">上次渲染没有完成</text>
            <text class="retry-render-card__desc">{{ renderFailureMessage }}</text>
          </view>
          <button class="retry-render-card__button" :disabled="apiBusy" @tap="retryGenerationTask">{{ apiBusy ? '重试中…' : '重试渲染' }}</button>
        </view>

        <view class="slide-editor__preview" :class="`slide-editor__preview--${pptStyle}`" :style="editorPreviewFrameStyle">
          <image
            v-if="editorPreviewImage && editorPreviewSlideIndex === activeSlideIndex"
            class="slide-editor__preview-image"
            :src="editorPreviewImage"
            mode="aspectFit"
            @tap="openEditorPreview"
          />
          <image
            v-else
            class="slide-editor__preview-image slide-editor__preview-image--empty"
            src="/static/images/ppt-preview-empty.svg"
            mode="aspectFit"
          />
          <view v-if="editorPreviewError" class="slide-editor__preview-status">
            <text>{{ editorPreviewError }}</text>
          </view>
          <view v-if="editorPreviewLoading" class="slide-editor__preview-status slide-editor__preview-status--loading">
            <view class="slide-editor__preview-spinner"></view>
            <text>正在渲染中</text>
          </view>
        </view>

        <view class="slide-layout-lock-card" :class="{ 'slide-layout-lock-card--locked': activeSlide.layoutLocked }">
          <view class="slide-layout-lock-card__icon" :class="{ 'slide-layout-lock-card__icon--locked': activeSlide.layoutLocked }">
            <text>{{ activeSlide.layoutLocked ? '✓' : '锁' }}</text>
          </view>
          <view class="slide-layout-lock-card__copy">
            <text class="slide-layout-lock-card__title">{{ activeSlide.layoutLocked ? '当前页面已锁定' : '锁定当前页面' }}</text>
            <text class="slide-layout-lock-card__desc">
              {{ activeSlide.layoutLocked ? '生成时保留当前预览版式，仍可修改下方文字' : '生成时保留当前预览版式，不自动更换布局' }}
            </text>
            <text class="slide-layout-lock-card__layout">{{ selectedTemplateName }} · {{ activeSlideLayoutLabel }}</text>
          </view>
          <button class="slide-layout-lock-card__action" :class="{ 'slide-layout-lock-card__action--locked': activeSlide.layoutLocked }" @tap="toggleActiveSlideLock">
            {{ activeSlide.layoutLocked ? '解除锁定' : '锁定页面' }}
          </button>
        </view>

        <view class="edit-field">
          <view class="edit-field__label"><text>页面标题</text><text>{{ activeSlide.title.length }}/80</text></view>
          <input v-model="activeSlide.title" :maxlength="80" placeholder="请输入页面标题" @input="onEditorContentInput" />
        </view>
        <view class="edit-field">
          <view class="edit-field__label"><text>页面内容</text><text>支持修改单页内容</text></view>
          <textarea v-model="activeSlide.content" :maxlength="1200" auto-height placeholder="请输入本页需要展示的知识点和说明" @input="onEditorContentInput" />
        </view>
        <view class="prompt-field prompt-field--shared">
          <view class="prompt-field__head">
            <view><text>公共提示词</text><text>所有页面共同生效</text></view>
            <view class="prompt-badge">全局</view>
          </view>
          <textarea v-model="sharedPrompt" :maxlength="800" auto-height placeholder="例如：保持学习资料准确，使用简洁排版，突出关键概念" @input="markEditorDirty" />
          <text class="prompt-field__hint">在任意页面修改后，会同步到全部页面。</text>
        </view>
        <view class="prompt-field">
          <view class="prompt-field__head">
            <view><text>单页私有提示词</text><text>仅对第 {{ activeSlideIndex + 1 }} 页生效</text></view>
            <view class="prompt-badge prompt-badge--private">私有</view>
          </view>
          <textarea v-model="activeSlide.privatePrompt" :maxlength="800" auto-height placeholder="例如：本页使用左右对比布局，增加函数图像示意" @input="onEditorContentInput" />
              <text class="prompt-field__hint">点击“确认并生成”后生效，用于补充本页配图和强调重点；不会改变固定模板结构。</text>
        </view>

        <view class="slide-editor__navigation">
          <button class="secondary-button" :disabled="activeSlideIndex === 0" @tap="selectEditorSlide(activeSlideIndex - 1)">上一页</button>
          <button class="secondary-button" :disabled="activeSlideIndex === slides.length - 1" @tap="selectEditorSlide(activeSlideIndex + 1)">下一页</button>
        </view>
      </view>

      <view class="bottom-actions">
        <view class="bottom-actions__buttons">
          <button class="secondary-button" :disabled="apiBusy" @tap="goPrevious">返回设置</button>
          <button v-if="hasLastSuccessfulResult" class="secondary-button" :disabled="apiBusy" @tap="returnToLastSuccessfulResult">返回上次成品</button>
          <button class="primary-button" :disabled="apiBusy" @tap="startGeneration">{{ apiBusy ? '正在创建任务…' : editorPrimaryLabel }}</button>
        </view>
      </view>
    </view>

    <view v-else-if="currentStep === 6" class="panel progress-panel">
      <view class="progress-hero">
        <view class="progress-ring" :style="{ '--progress': `${progress * 3.6}deg` }">
          <view class="progress-ring__inner"><text>{{ progress }}</text><text>%</text></view>
        </view>
        <text class="progress-hero__stage">{{ activeGenerationStep.activeText }}</text>
        <text class="progress-hero__message">{{ progressMessage }}</text>
      </view>

      <view class="generation-list">
        <view v-for="(item, index) in generationSteps" :key="item.id" class="generation-item">
          <view class="generation-item__rail">
            <view class="generation-item__dot" :class="generationStatusClass(index)">
              <text v-if="index < activeGenerationIndex">✓</text>
              <text v-else>{{ index + 1 }}</text>
            </view>
            <view v-if="index < generationSteps.length - 1" class="generation-item__line" :class="{ 'generation-item__line--done': index < activeGenerationIndex }"></view>
          </view>
          <view class="generation-item__body">
            <text class="generation-item__title">{{ generationStepTitle(item, index) }}</text>
            <text class="generation-item__desc">{{ index < activeGenerationIndex ? item.doneText : index === activeGenerationIndex ? item.description : '等待中' }}</text>
          </view>
          <view v-if="index === activeGenerationIndex" class="loading-dots"><text></text><text></text><text></text></view>
        </view>
      </view>

      <view class="overall-progress">
        <view class="overall-progress__head"><text>总体进度</text><text>{{ progress }}%</text></view>
        <view class="overall-progress__track"><view class="overall-progress__value" :style="{ width: `${progress}%` }"></view></view>
        <text class="overall-progress__time">{{ generationRuntimeHint }}</text>
      </view>

      <view class="generation-actions">
        <button class="secondary-button" @tap="returnToEditor">返回编辑</button>
        <button class="secondary-button" @tap="cancelGeneration">取消生成</button>
      </view>
    </view>

    <view v-else-if="currentStep === 7" class="panel result-panel">
      <view class="success-hero">
        <view class="success-icon">✓</view>
        <text class="success-hero__title">{{ qualityStatus === 'partial' ? 'PPT 已生成，部分页面需复核' : 'PPT 已生成' }}</text>
        <text class="success-hero__desc">共 {{ pageCount }} 页，使用 {{ selectedTemplateName }} 模板，PPTX 已生成。</text>
      </view>

      <view class="result-summary">
        <view class="result-summary__name">
          <view class="result-summary__file-icon">P</view>
          <view><text>{{ resultName }}</text><text>演示文稿 · {{ pageCount }} 页</text></view>
        </view>
        <view class="result-summary__meta"><text>生成完成</text><text>刚刚</text></view>
      </view>

      <view class="format-status-panel">
        <view class="format-status-panel__head">
          <view>
            <text class="format-status-panel__title">导出文件</text>
            <text class="format-status-panel__desc">{{ exportStatusCopy }}</text>
          </view>
          <text v-if="availableExportFormats.length">PPTX 可下载</text>
          <text v-else>生成失败</text>
        </view>
        <view
          v-for="format in exportFormats"
          :key="format.id"
          class="format-status-row"
          :class="{ 'format-status-row--disabled': !isExportAvailable(format.id) }"
          @tap="selectExportFormat(format.id)"
        >
          <view class="format-status-row__icon" :class="`format-status-row__icon--${format.id}`">{{ format.icon }}</view>
          <view class="format-status-row__body">
            <text>{{ format.name }}</text>
            <text v-if="isExportAvailable(format.id)">文件已生成，可进入导出下载</text>
            <text v-else>{{ formatErrorMessage(format.id) }}</text>
          </view>
          <text class="format-status-row__state">{{ isExportAvailable(format.id) ? '已生成' : '不可用' }}</text>
        </view>
      </view>

      <view class="preview-section">
        <view class="preview-section__head"><text>页面预览</text><text>共 {{ pageCount }} 页</text></view>
        <scroll-view class="slide-preview-feed" scroll-y enhanced :show-scrollbar="false">
          <view v-for="slide in visibleSlides" :key="slide" class="slide-preview-feed__item" @tap="activeSlideIndex = slide - 1; openSlidePreview(slide)">
            <image v-if="previewImages[slide]" class="slide-preview-feed__image" :src="previewImages[slide]" mode="widthFix" />
            <view v-else class="slide-thumb__canvas slide-preview-feed__fallback" :class="[`slide-thumb__canvas--${pptStyle}`, { 'slide-thumb__canvas--cover': slide === 1 }]">
              <text class="slide-thumb__number">{{ String(slide).padStart(2, '0') }}</text>
              <text class="slide-thumb__title">{{ slideTitle(slide) }}</text>
              <view class="slide-thumb__lines"><text></text><text></text><text></text></view>
              <view class="slide-thumb__decor"></view>
            </view>
            <view class="slide-preview-feed__meta"><text>第 {{ slide }} 页</text><text>{{ slideTitle(slide) }}</text></view>
          </view>
        </scroll-view>
        <view class="image-replace-row">
          <view>
            <text class="image-replace-row__title">替换第 {{ activeSlideIndex + 1 }} 页配图</text>
            <text class="image-replace-row__desc">模板图片可留空，也可以在生成后上传自己的图片</text>
          </view>
          <button class="text-button image-replace-row__button" :disabled="apiBusy || !taskId" @tap="uploadSlideImage">上传替换</button>
        </view>
      </view>

      <view class="bottom-actions">
        <view class="bottom-actions__buttons">
          <button class="secondary-button" @tap="returnToEditor">继续修改</button>
          <button class="secondary-button" @tap="restartFromSettings">重新生成</button>
          <button class="primary-button" :disabled="!availableExportFormats.length" @tap="goExportStep">导出下载</button>
        </view>
      </view>
    </view>

    <view v-else class="panel export-panel">

      <view class="export-status-card" :class="{ 'export-status-card--warning': !isExportAvailable(exportFormat) }">
        <view>
          <text class="export-status-card__title">{{ exportStatusCopy }}</text>
          <text class="export-status-card__desc">
            {{ isExportAvailable(exportFormat) ? `当前选择 ${selectedExportFormatName}，可直接下载。` : selectedExportIssue }}
          </text>
        </view>
        <button v-if="!isExportAvailable(exportFormat) && primaryExportFormat" class="export-status-card__button" @tap="switchToPrimaryExportFormat">
          切换可用格式
        </button>
      </view>

      <view
        v-for="format in exportFormats"
        :key="format.id"
        class="export-choice"
        :class="{ 'export-choice--selected': exportFormat === format.id, 'export-choice--disabled': !isExportAvailable(format.id) }"
        @tap="selectExportFormat(format.id)"
      >
        <view class="export-choice__icon" :class="`export-choice__icon--${format.id}`">{{ format.icon }}</view>
        <view class="export-choice__body">
          <text class="export-choice__title">{{ format.name }} <text>（.{{ format.id }}）</text></text>
          <text class="export-choice__desc">{{ format.description }}</text>
          <text v-if="isExportAvailable(format.id)" class="export-choice__ready">已生成，可下载</text>
          <text v-else class="export-choice__issue">{{ formatErrorMessage(format.id) }}</text>
        </view>
        <view class="radio-dot" :class="{ 'radio-dot--selected': exportFormat === format.id }"><text v-if="exportFormat === format.id">✓</text></view>
      </view>

      <button class="primary-button primary-button--full" :disabled="exportPreparing || !isExportAvailable(exportFormat)" @tap="prepareExport">
        {{ exportPreparing ? '正在下载文件…' : '下载文件' }}
      </button>

      <view v-if="exportReady" class="download-ready">
        <view class="download-ready__illustration">
          <view class="download-ready__file">P</view>
          <view class="download-ready__arrow">↓</view>
        </view>
        <text class="download-ready__title">文件已生成</text>
        <text class="download-ready__name">{{ downloadFileName }}</text>
        <text class="download-ready__hint">文件已下载，可使用 PowerPoint、WPS 或系统阅读器打开</text>
      </view>

      <button class="back-result-button" @tap="currentStep = 7">返回生成结果</button>
    </view>

      <view v-if="operationFeedback.active" class="operation-feedback">
        <view class="operation-feedback__head"><text>{{ operationFeedback.message }}</text><text>{{ operationFeedback.progress }}%</text></view>
        <view class="operation-feedback__track"><view class="operation-feedback__value" :style="{ width: `${operationFeedback.progress}%` }"></view></view>
        <text class="operation-feedback__detail">{{ operationFeedback.detail }}</text>
        <button class="text-button operation-feedback__cancel" @tap="cancelGeneration">取消生成</button>
      </view>

    <view v-if="layoutViewerVisible" class="layout-fullscreen">
      <view class="layout-fullscreen__bar">
        <view class="layout-fullscreen__back" @tap="closeLayoutViewer"></view>
        <view class="layout-fullscreen__bar-main">
          <text class="layout-fullscreen__title">{{ selectedTemplateName }}</text>
          <text class="layout-fullscreen__count">{{ activeLayoutIndex + 1 }} / {{ selectedTemplateLayouts.length }} 页版式</text>
        </view>
      </view>
      <scroll-view
        v-show="layoutViewerReady"
        class="layout-fullscreen__scroll"
        scroll-y
        :scroll-top="layoutScrollTop"
        @scroll="onLayoutStripScroll"
      >
        <view
          v-for="(layout, index) in selectedTemplateLayouts"
          :id="`layout-item-${index}`"
          :key="layout.id || index"
          class="layout-fullscreen__item"
          @tap="retryLayoutPreview(index)"
        >
          <image v-if="layoutPreviewImages[index]" class="layout-fullscreen__image" :src="layoutPreviewImages[index]" mode="widthFix" />
          <view v-else class="layout-fullscreen__placeholder">
            <text v-if="layoutPreviewFailed[`${selectedTemplate?.id}:${index}`]">版式图加载失败，点击重试</text>
            <text v-else>正在加载版式图…</text>
            <text class="layout-fullscreen__name">{{ layout?.name }}</text>
          </view>
        </view>
      </scroll-view>
      <view v-if="!layoutViewerReady" class="layout-fullscreen__loading">
        <view class="layout-fullscreen__loading-spinner"></view>
        <text class="layout-fullscreen__loading-text">正在加载模板版式 {{ layoutLoadedCount }} / {{ selectedTemplateLayouts.length }}</text>
        <text class="layout-fullscreen__loading-hint">加载完成后即可浏览</text>
      </view>
      <view v-if="layoutViewerReady" class="layout-fullscreen__footer">
        <view class="layout-fullscreen__use" @tap="useTemplateFromViewer"><text>使用该模板</text></view>
      </view>
    </view>

    <view v-if="historyOpen" class="history-mask" @tap="historyOpen = false">
      <view class="history-drawer" @tap.stop>
        <view class="history-drawer__head">
          <view>
            <text class="history-drawer__title">历史记录</text>
            <text class="history-drawer__desc">大纲和生成配置保存在当前设备</text>
          </view>
          <view class="history-drawer__close" @tap="historyOpen = false">×</view>
        </view>
        <view class="history-tabs">
          <view :class="{ 'history-tabs__item--active': historyTab === 'generation' }" class="history-tabs__item" @tap="historyTab = 'generation'">生成记录</view>
          <view :class="{ 'history-tabs__item--active': historyTab === 'outline' }" class="history-tabs__item" @tap="historyTab = 'outline'">大纲记录</view>
        </view>

        <scroll-view class="history-list" scroll-y>
          <view v-if="currentHistory.length">
            <view v-for="item in currentHistory" :key="item.id" class="history-card">
              <view class="history-card__head">
                <view class="history-card__type" :class="{ 'history-card__type--upload': item.source === 'original_outline' }">
                  {{ historyTab === 'outline' ? (item.source === 'ai_outline' ? 'AI 大纲' : '原文大纲') : 'PPT' }}
                </view>
                <text>{{ item.createdAt }}</text>
              </view>
              <text class="history-card__title">{{ item.name }}</text>
              <text class="history-card__meta">
                {{ historyTab === 'outline' ? `${item.items.length} 个大纲项` : `${item.pageCount} 页 · ${styleName(item.pptStyle)}` }}
              </text>
              <view class="history-card__actions">
                <text @tap="reuseHistory(item)">{{ historyTab === 'outline' ? '载入并编辑' : '使用此配置' }}</text>
                <text class="history-card__delete" @tap="deleteHistory(item.id)">删除</text>
              </view>
            </view>
          </view>
          <view v-else class="history-empty">
            <view class="history-empty__icon"><text></text><text></text><text></text></view>
            <text class="history-empty__title">{{ historyTab === 'outline' ? '暂无大纲记录' : '暂无生成记录' }}</text>
            <text class="history-empty__desc">{{ historyTab === 'outline' ? '保存过的大纲会显示在这里' : '完成一次 PPT 生成后会显示在这里' }}</text>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script>
import {
  cancelPptTask,
  createPptTask,
  createPptOutlineTask,
  createPptSlidesTask,
  downloadPptLayoutPreview,
  downloadPptPreview,
  downloadPptTaskFile,
  downloadPptTemplateThumbnail,
  clearPptTemplateThumbnailCache,
  generatePptSlides,
  getPptOptions,
  getPptTask,
  replacePptSlideImage,
  renderPptPreview,
  retryPptTask,
  streamPptTask,
  uploadPptSourceFile
} from '@/api/ppt.js'

const DEFAULT_PPT_PAGE_COUNT = 30

export default {
  name: 'AiPresentationFlow',
  props: {
    initialTopic: { type: String, default: '' },
    initialEntryMode: { type: String, default: 'sourceFirst' }
  },
  data() {
    const templateFirst = this.initialEntryMode === 'templateFirst'
    return {
      currentStep: 1,
      entryMode: templateFirst ? 'templateFirst' : 'sourceFirst',
      fileInfo: null,
      fileContent: '',
      manualSourceContent: '',
      sourceFileId: '',
      enhancedEngineAvailable: false,
      templateCatalogAvailable: true,
      previewExpanded: false,
      outlineMode: 'ai_outline',
      outlineName: '',
      outlineItems: [],
      outlineDocument: null,
      outlineSourceDirty: false,
      layoutMarkdown: '',
      outlineSavedAt: '',
      outlineLevels: [
        { value: 1, label: '章节' },
        { value: 2, label: '小节' },
        { value: 3, label: '知识点' }
      ],
      pageCount: DEFAULT_PPT_PAGE_COUNT,
      pptStyle: 'general',
      templateEntryMode: templateFirst ? 'library' : 'upload',
      templateUploadSource: templateFirst ? 'library' : 'upload',
      templateCategory: 'all',
      templateCategories: [
        { id: 'all', name: '全部模板' },
        { id: 'study', name: '课堂复习' },
        { id: 'report', name: '汇报展示' }
      ],
      templateOptionsLoading: false,
      // 模板查看/更换是从哪个页面进入的，关闭预览或确认模板后回到原上下文。
      templateReturnContext: null,
      activeLayoutIndex: 0,
      layoutViewerVisible: false,
      layoutViewerReady: false,
      layoutLoadedCount: 0,
      layoutScrollTop: 0,
      layoutItemStride: 0,
      layoutPreviewCache: {},
      layoutPreviewPending: {},
      templateThumbPending: {},
      // 与任务恢复缓存绑定，避免退出页面后模板列表重建时丢失已下载封面。
      templateThumbnailState: {},
      layoutPreviewFailed: {},
      contentLevel: 'standard',
      slides: [],
      activeSlideIndex: 0,
      editorPreviewImage: '',
      editorPreviewSlideIndex: -1,
      editorPreviewCache: {},
      editorPreviewLoading: false,
      editorPreviewError: '',
      editorPreviewTimer: null,
      editorPreviewRequestId: 0,
      editorPreviewQueued: false,
      editorPreviewInFlight: {},
      editorPreviewRevisions: {},
      editorPreviewSession: 0,
      editorPreviewBatchRunId: 0,
      editorPreviewBatchPromise: null,
      editorPreviewRenderTail: Promise.resolve(),
      sharedPrompt: '保持资料内容准确，版面简洁清晰，突出核心知识点，使用清晰易读的视觉层级。',
      settings: {
        includeCover: true,
        includeCatalog: true,
        includeSection: true,
        includeSummary: true,
        includeVisuals: true,
        imageMode: 'placeholder'
      },
      imageModes: [
        { id: 'placeholder', name: '先留空' },
        { id: 'ai', name: 'AI 生成' }
      ],
      progress: 0,
      generationTimer: null,
      generationStream: null,
      slideGenerationTaskId: '',
      slideGenerationStream: null,
      slideGenerationSnapshot: null,
      slidesDirty: false,
      outlineGenerationTaskId: '',
      outlineGenerationStream: null,
      outlineGenerationSnapshot: null,
      generationRunId: 0,
      outlineGenerationRunId: 0,
      slideGenerationRunId: 0,
      taskId: '',
      taskResult: null,
      pendingTaskFingerprint: '',
      completedTaskFingerprint: '',
      editorDirty: false,
      lastSuccessfulResult: null,
      previewImages: {},
      generationWarnings: [],
      contentQuality: null,
      modelConfigError: false,
      lastPptError: '',
      apiBusy: false,
      operationFeedback: { active: false, progress: 0, message: '', detail: '' },
      operationFeedbackTimer: null,
      draftPersistTimer: null,
      restoringSavedWork: false,

      showAllSlides: false,
      exportFormat: 'pptx',
      exportPreparing: false,
      exportReady: false,
      exportTimer: null,
      historyOpen: false,
      historyTab: 'generation',
      generationHistory: [],
      outlineHistory: [],
      stepMeta: [
        templateFirst
          ? { id: 1, shortTitle: '选模板', title: '选择 PPT 模板', description: '先选择可直接套用的模板' }
          : { id: 1, shortTitle: '上传资料', title: '上传演示资料', description: '先确定要生成 PPT 的内容' },
        templateFirst
          ? { id: 2, shortTitle: '上传资料', title: '上传演示资料', description: '再提交要写入模板的资料' }
          : { id: 2, shortTitle: '选模板', title: '选择 PPT 模板', description: '根据这份资料选择合适的模板' },
        { id: 3, shortTitle: '编辑大纲', title: '编辑 PPT 大纲', description: '确认内容结构，并将本次大纲独立保存' },
        { id: 4, shortTitle: '设置 PPT', title: '设置 PPT', description: '设置 PPT 的展示效果' },
        { id: 5, shortTitle: '编辑页面', title: '编辑页面内容', description: '逐页调整内容、公共提示词和单页提示词' },
        { id: 6, shortTitle: '生成进度', title: '正在生成 PPT', description: 'AI 正在整理你的资料，请稍候' },
        { id: 7, shortTitle: '生成结果', title: 'PPT 生成完成', description: '预览生成效果并确认导出' },
        { id: 8, shortTitle: '导出下载', title: '导出下载', description: '选择需要导出的文件格式' }
      ],
      outlineModes: [
        { id: 'ai_outline', shortName: '非大纲资料', name: '非大纲资料', description: '上传课件、笔记或成套资料，系统将自动整理大纲', fit: '适合只有主题、笔记或零散资料' },
        { id: 'original_outline', shortName: '上传大纲', name: '上传大纲', description: '已准备好提纲或目录时可直接上传，尽量保留原结构', fit: '适合已有清晰目录的资料' }
      ],
      pptStyles: [],
      pageOptions: [
        { key: 'includeCover', label: '包含封面' },
        { key: 'includeCatalog', label: '包含目录' },
        { key: 'includeSection', label: '包含章节页' },
        { key: 'includeSummary', label: '包含总结页' }
      ],
      generationSteps: [
        { id: 'preparing', activeText: '正在整理页面内容', description: '整理已确认的页面内容', doneText: '页面内容整理完成' },
        { id: 'quality_check', activeText: '正在检查页面质量', description: '检查文字密度和固定模板的适配情况', doneText: '页面质量检查完成' },
        { id: 'visuals', activeText: '正在生成配图', description: '生成页面需要的视觉素材', doneText: '配图生成完成' },
        { id: 'rendering', activeText: '正在渲染页面', description: '生成每页预览图', doneText: '页面渲染完成' },
        { id: 'exporting', activeText: '正在生成 PPTX', description: '整理可编辑的 PowerPoint 文件', doneText: 'PPTX 文件生成完成' }
      ],
      exportFormats: [
        { id: 'pptx', icon: 'P', name: 'PowerPoint 格式', description: '可使用 PowerPoint 或 WPS 打开并继续编辑。' }
      ]
    }
  },
  computed: {
    validOutlineItems() {
      return this.outlineItems.filter(item => String(item.title || '').trim())
    },
    outlineLevelLabels() {
      return this.outlineLevels.map(level => level.label)
    },
    activeSlide() {
      return this.slides[this.activeSlideIndex] || null
    },
    templateFirstEnabled() {
      return this.entryMode === 'templateFirst'
    },
    templateStepIndex() {
      return this.templateFirstEnabled ? 1 : 2
    },
    uploadStepIndex() {
      return this.templateFirstEnabled ? 2 : 1
    },
    isTemplateStep() {
      return this.currentStep === this.templateStepIndex && this.templateEntryMode !== 'upload'
    },
    templateLibraryVisible() {
      return this.isTemplateStep && this.templateEntryMode === 'library'
    },
    isUploadStep() {
      return this.currentStep === this.uploadStepIndex && this.templateEntryMode === 'upload'
    },
    templateHeroTitle() {
      return this.templateFirstEnabled ? '先选择一套 PPT 模板' : '为刚才的资料选择模板'
    },
    templateHeroDescription() {
      return this.templateFirstEnabled
        ? '选定模板后再提交资料，系统会把内容写入所选版式。'
        : '模板只在这里确认一次，后续会按所选版式生成 PPTX 和页面预览。'
    },
    uploadHeroTitle() {
      return this.templateFirstEnabled ? '提交演示资料' : '先上传演示资料'
    },
    uploadHeroDescription() {
      return this.templateFirstEnabled
        ? '已选择模板，现在上传文件或粘贴内容来生成 PPT。'
        : '先确定要生成的内容，再选择适合这份资料的 PPT 模板。'
    },
    templateNextLabel() {
      if (this.templateReturnContext?.entryMode !== 'upload' && this.templateReturnContext?.currentStep) return '使用并返回设置'
      if (!this.templateFirstEnabled && this.canReturnToOutline) return '返回大纲'
      return this.templateFirstEnabled ? '下一步：上传资料' : '生成大纲'
    },
    templateDetailActionLabel() {
      if (this.templateReturnContext?.entryMode !== 'upload' && this.templateReturnContext?.currentStep) return '使用并返回设置'
      if (!this.templateFirstEnabled && this.canReturnToOutline) return '返回大纲'
      return this.templateFirstEnabled ? '使用该模板' : '使用并生成大纲'
    },
    uploadNextLabel() {
      if (this.canReturnToOutline) return '返回大纲'
      if (this.validOutlineItems.length >= 2) return '重新生成大纲'
      return this.templateFirstEnabled ? '生成大纲' : '下一步：选择模板'
    },
    activeSlideLayoutLabel() {
      const layouts = this.selectedTemplateLayouts
      return layouts[this.activeSlideIndex % Math.max(1, layouts.length)]?.name || '图文内容'
    },
    editorPreviewFrameStyle() {
      const background = this.activeSlide?.ui?.background
      const value = typeof background === 'string'
        ? background.trim()
        : (background && typeof background === 'object'
          ? String(background.color || background.fill || background.value || '').trim()
          : '')
      if (!value || !/^(#|rgb\(|rgba\(|hsl\(|hsla\(|linear-gradient\(|radial-gradient\(|url\()/i.test(value)) return {}
      return { background: value }
    },
    currentGenerationPage() {
      const fromTask = Number(this.taskResult?.currentSlide || 0)
      if (fromTask) return Math.min(this.pageCount, Math.max(1, fromTask))
      return Math.min(this.pageCount, Math.max(1, Math.ceil((this.progress / 100) * this.pageCount)))
    },
    currentGenerationLayoutLabel() {
      const layouts = this.selectedTemplateLayouts
      return layouts[(this.currentGenerationPage - 1) % Math.max(1, layouts.length)]?.name || '图文内容'
    },
    currentHistory() {
      return this.historyTab === 'outline' ? this.outlineHistory : this.generationHistory
    },
    stepperProgress() {
      const total = Math.max(1, this.stepMeta.length - 1)
      return `${Math.max(0, Math.min(100, ((this.currentStep - 1) / total) * 100))}%`
    },
    templateCatalogLoading() {
      return this.templateOptionsLoading && !this.pptStyles.length
    },
    categorizedPptTemplates() {
      return this.pptStyles.map(item => {
        const category = this.templateCategoryOf(item)
        return {
          ...item,
          category,
          categoryLabel: this.templateCategoryName(category)
        }
      })
    },
    filteredPptTemplates() {
      if (this.templateCategory === 'all') return this.categorizedPptTemplates
      return this.categorizedPptTemplates.filter(item => item.category === this.templateCategory)
    },
    selectedTemplate() {
      return this.categorizedPptTemplates.find(item => item.id === this.pptStyle) || null
    },
    selectedTemplateName() {
      return this.selectedTemplate?.name || this.pptStyle || '默认模板'
    },
    selectedTemplateDescription() {
      return this.selectedTemplate?.description || '已选择模板，可继续上传资料生成 PPT。'
    },
    selectedTemplateLayoutCount() {
      return Number(this.selectedTemplate?.layoutCount || 0)
    },
    selectedTemplateCategoryLabel() {
      return this.selectedTemplate?.categoryLabel || '模板'
    },
    selectedTemplateLayouts() {
      const templateLayouts = Array.isArray(this.selectedTemplate?.layouts) ? this.selectedTemplate.layouts : []
      if (templateLayouts.length) {
        return templateLayouts.map((layout, index) => this.normalizeTemplateLayout(layout, index))
      }
      const total = this.selectedTemplateLayoutCount
      const baseLayouts = [
        { id: 'cover', name: '标题封面', type: 'cover', desc: '课程名、主题、日期', previewItems: [] },
        { id: 'catalog', name: '目录列表', type: 'catalog', desc: '章节结构与学习路径', previewItems: [] },
        { id: 'content', name: '知识点内容', type: 'content', desc: '标题、要点、说明', previewItems: [] },
        { id: 'focus', name: '重点强调', type: 'focus', desc: '适合核心结论页', previewItems: [] },
        { id: 'visual', name: '图文讲解', type: 'visual', desc: '图示、流程、对比', previewItems: [] },
        { id: 'summary', name: '复习总结', type: 'summary', desc: '回顾与行动建议', previewItems: [] }
      ]
      if (!total) return baseLayouts
      return baseLayouts.slice(0, Math.min(baseLayouts.length, Math.max(4, total)))
    },

    layoutPreviewImages() {
      const templateId = this.selectedTemplate?.id
      return (templateId && this.layoutPreviewCache[templateId]) || {}
    },
    currentLayout() {
      const layouts = this.selectedTemplateLayouts
      return layouts[this.activeLayoutIndex] || layouts[0] || null
    },
    selectedImageModeLabel() {
      const mode = this.imageModes.find(item => item.id === this.settings.imageMode)
      if (!this.settings.includeVisuals) return '不生成'
      return mode?.name || '先留空'
    },
    slideGenerationCurrentLabel() {
      const value = Number(this.slideGenerationSnapshot?.currentSlide || 0)
      return value ? String(value) : '-'
    },
    slideGenerationProcessingLabel() {
      const values = this.slideGenerationSnapshot?.processingSlides
      if (!Array.isArray(values) || !values.length) return ''
      return values.map(value => `第 ${value} 页`).join('、')
    },

    supportedSourceHint() {
      return this.enhancedEngineAvailable
        ? '支持 TXT、Word、PPT 和表格文件，最大 25MB'
        : '当前仅支持单个 TXT 文件'
    },
    fileKindLabel() {
      const name = String(this.fileInfo?.name || '').trim()
      const extension = (name.match(/\.([a-z0-9]+)$/i) || [])[1]
      if (!extension) return this.enhancedEngineAvailable ? 'FILE' : 'TXT'
      const value = extension.toUpperCase()
      if (['DOC', 'DOCX'].includes(value)) return 'DOC'
      if (['PPT', 'PPTX'].includes(value)) return 'PPT'
      if (['XLS', 'XLSX'].includes(value)) return 'XLS'
      return value.slice(0, 4)
    },
    hasFloatingActions() {
      return [1, 2, 3, 4, 5, 6, 7, 8].includes(this.currentStep)
    },
    canRetryGeneration() {
      return Boolean(this.taskId && ['failed', 'cancelled', 'timed_out'].includes(String(this.taskResult?.status || '')))
    },
    qualityStatus() {
      const value = String(this.taskResult?.qualityStatus || '').toLowerCase()
      if (value === 'partial' || value === 'blocked') return 'partial'
      if (this.generationWarnings.length || this.formatErrorList.length) return 'partial'
      return 'complete'
    },
    renderFailureMessage() {
      const failures = Array.isArray(this.taskResult?.error?.slides)
        ? this.taskResult.error.slides
        : []
      if (failures.length) {
        const summary = failures.slice(0, 3).map(item => {
          const slide = Number(item?.slide || 0)
          const errors = Array.isArray(item?.errors) ? item.errors.filter(Boolean) : []
          return `第${slide}页${errors.length ? `：${errors.join('、')}` : '质量校验未通过'}`
        }).join('；')
        return failures.length > 3 ? `${summary}；另有${failures.length - 3}页` : summary
      }
      return this.lastPptError || this.taskResult?.message || '可以保留当前页面内容，重新提交模板渲染。'
    },

    previewContent() {
      const content = (this.fileContent || '').trim()
      return this.previewExpanded ? content : content.slice(0, 420)
    },
    hasPreviewOverflow() {
      return (this.fileContent || '').trim().length > 420
    },
    formattedCharacterCount() {
      return String((this.fileContent || '').replace(/\s/g, '').length).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
    },
    activeGenerationIndex() {
      if (this.progress >= 100 || this.taskResult?.status === 'completed') return this.generationSteps.length - 1
      const stageIndex = {
        queued: 0,
        preparing: 0,
        quality_check: 1,
        visuals: 2,
        rendering: 3,
        exporting: 4
      }[String(this.taskResult?.stage || '')]
      if (Number.isInteger(stageIndex)) return stageIndex
      return Math.min(Math.floor(this.progress / (100 / this.generationSteps.length)), this.generationSteps.length - 1)
    },
    activeGenerationStep() {
      return this.generationSteps[this.activeGenerationIndex]
    },
    progressMessage() {
      return this.taskResult?.message || this.activeGenerationStep.description
    },
    remainingTime() {
      const seconds = Math.max(1, Math.ceil((100 - this.progress) / 5))
      return seconds > 10 ? `${seconds - 5}～${seconds + 5} 秒` : `约 ${seconds} 秒`
    },
    generationRuntimeHint() {
      if (this.taskResult?.message) return this.taskResult.message
      const processing = this.taskResult?.processingSlides
      if (Array.isArray(processing) && processing.length) return `正在处理第 ${processing.join('、')} 页`
      return `预计还需 ${this.remainingTime}`
    },
    exportAttachmentTypes() {
      if (!this.taskResult || !Array.isArray(this.taskResult.attachments)) return []
      return this.taskResult.attachments
        .map(item => String(item?.type || '').toLowerCase())
        .filter(Boolean)
    },
    availableExportFormats() {
      return this.exportFormats
        .map(item => item.id)
        .filter(format => this.exportAttachmentTypes.includes(format))
    },
    primaryExportFormat() {
      if (this.availableExportFormats.includes(this.exportFormat)) return this.exportFormat
      return this.availableExportFormats[0] || ''
    },
    selectedExportFormatName() {
      return this.exportFormats.find(item => item.id === this.exportFormat)?.name || String(this.exportFormat || '').toUpperCase()
    },
    selectedExportIssue() {
      return this.formatErrorMessage(this.exportFormat)
    },
    formatErrorList() {
      const errors = this.taskResult?.formatErrors || {}
      return Object.keys(errors).map(format => ({
        format,
        label: this.exportFormats.find(item => item.id === format)?.name || String(format).toUpperCase(),
        message: String(errors[format] || '')
      })).filter(item => item.message)
    },
    exportStatusCopy() {
       if (!this.taskResult) return '等待生成结果'
       if (this.qualityStatus === 'partial') return '页面已完成自动排版修复，建议检查预览后直接下载'
       if (!this.availableExportFormats.length) return '本次任务未返回可下载文件'
       return 'PPTX 已生成，可直接下载'
    },
    resultName() {
      const name = this.fileInfo?.name || this.initialTopic || '演示文稿.txt'
      return name.replace(/\.(?:txt|docx?|pptx?|xlsx?)$/i, '') || '演示文稿'
    },
    visibleSlides() {
      return Array.from({ length: this.pageCount }, (_, index) => index + 1)
    },
    downloadFileName() {
      return `${this.resultName}.pptx`
    },
    hasLastSuccessfulResult() {
      return Boolean(
        this.lastSuccessfulResult?.taskId
        && String(this.lastSuccessfulResult?.taskResult?.status || '') === 'completed'
      )
    },
    editorPrimaryLabel() {
      if (this.hasLastSuccessfulResult && !this.editorDirty) return '查看生成结果'
      return this.hasLastSuccessfulResult ? '确认修改并生成' : '确认并生成'
    },
    canReturnToEditor() {
      return this.slides.length >= 2 && !this.slidesDirty
    },
    canReturnToOutline() {
      return this.validOutlineItems.length >= 2 && !this.outlineSourceDirty
    },
    settingsNextLabel() {
      if (this.canReturnToEditor) return '返回编辑'
      return this.slides.length >= 2 ? '重新生成页面内容' : '编辑页面内容'
    },
    canRestartFlow() {
      return Boolean(
        this.fileInfo
        || String(this.fileContent || '').trim()
        || String(this.manualSourceContent || '').trim()
        || String(this.sourceFileId || '').trim()
        || this.outlineItems.length
        || this.slides.length
        || this.hasActivePptTask()
      )
    }
  },
  watch: {
    // 选中哪个模板才补哪张封面缩略图
    pptStyle(id) {
      this.ensureTemplateThumbnail(id)
      this.scheduleDraftPersistence()
    },
    fileInfo() {
      this.scheduleDraftPersistence()
    },
    currentStep() {
      this.scheduleDraftPersistence()
    },
    templateEntryMode() {
      this.scheduleDraftPersistence()
    },
    templateCategory() {
      this.scheduleDraftPersistence()
    },
    contentLevel() {
      this.scheduleDraftPersistence()
    },
    sharedPrompt() {
      this.scheduleDraftPersistence()
    },
    fileContent() {
      this.scheduleDraftPersistence()
    },
    manualSourceContent() {
      this.scheduleDraftPersistence()
    },
    sourceFileId() {
      this.scheduleDraftPersistence()
    },
    outlineMode() {
      this.scheduleDraftPersistence()
    },
    outlineName() {
      this.scheduleDraftPersistence()
    },
    outlineItems: {
      deep: true,
      handler() {
        this.scheduleDraftPersistence()
      }
    },
    settings: {
      deep: true,
      handler() {
        this.scheduleDraftPersistence()
      }
    },
    slides: {
      deep: true,
      handler() {
        this.scheduleDraftPersistence()
      }
    }
  },
  created() {
    // 先展示可用的基础模板，再后台刷新完整目录；入口不能被模板服务拖成空白页。
    this.applyLocalPptTemplateFallback()
    this.restoreHistories()
    this.loadPptOptions()
    this.promptSavedWork()
  },
  beforeDestroy() {
    if (this.hasActivePptTask()) this.persistCurrentTaskSnapshot()
    else this.persistDraft()
    this.clearTimers()
  },
  methods: {
    async loadPptOptions(forceRefresh = false) {
      this.templateOptionsLoading = true
      try {
        const options = await getPptOptions({ forceRefresh })
        this.enhancedEngineAvailable = Boolean(options.enhancedEngineAvailable)
        this.templateCatalogAvailable = options.templateCatalogAvailable !== false
        const templates = (options.templates || []).filter(item => item?.id && item?.name)
        if (templates.length) {
          const previousTemplates = new Map(this.pptStyles.map(item => [String(item.id), item]))
          // 只取元数据，缩略图按需下载：进入页面不再一次性拉全部模板图
          this.pptStyles = templates.map(item => ({
            id: String(item.id),
            name: String(item.name),
            description: String(item.description || `${Number(item.layoutCount || 0)} 种页面布局`),
            layoutCount: Number(item.layoutCount || 0),
            layouts: Array.isArray(item.layouts) ? item.layouts : [],
            // options 刷新时保留已经下载的本地路径，避免列表重新渲染瞬间退回占位封面。
            thumbnailUrl: previousTemplates.get(String(item.id))?.thumbnailUrl
              || this.templateThumbnailState[String(item.id)]
              || '',
            hasThumbnail: Boolean(item.thumbnailUrl)
              || Boolean(previousTemplates.get(String(item.id))?.thumbnailUrl)
              || Boolean(this.templateThumbnailState[String(item.id)]),
            default: Boolean(item.default || item.defaultOption)
          }))
          if (!this.pptStyles.some(item => item.id === this.pptStyle)) {
            this.pptStyle = (this.pptStyles.find(item => item.default) || this.pptStyles[0]).id
          }
          this.ensureTemplateThumbnail(this.pptStyle)
          // 模板库首屏可见时仅为当前分类补封面缩略图（不加载版式图）
          if (this.templateLibraryVisible) this.loadTemplateThumbnails()
        } else {
          this.applyLocalPptTemplateFallback()
        }
      } catch (error) {
        this.applyLocalPptTemplateFallback()
        this.templateCatalogAvailable = false
        this.lastPptError = '模板服务暂时不可用，已保留基础模板；可稍后重新加载模板。'
      } finally {
        this.templateOptionsLoading = false
      }
    },
    applyLocalPptTemplateFallback() {
      if (!this.pptStyles.length) {
        this.pptStyles = [{
          id: 'general',
          name: '简约通用',
          description: '清晰留白，适合课程复习与知识讲解',
          layoutCount: 12,
          layouts: [],
          thumbnailUrl: '',
          hasThumbnail: false,
          default: true
        }]
      }
      if (!this.pptStyles.some(item => item.id === this.pptStyle)) {
        this.pptStyle = 'general'
      }
    },
    stepStateLabel(item) {
      if (this.currentStep > item.id) return '已完成'
      if (this.currentStep === item.id) return '进行中'
      if (this.isStepAvailable(item.id)) return '可进入'
      return '待处理'
    },
    isStepAvailable(step) {
      const target = Number(step)
      if (!target) return false
      if (target === this.templateStepIndex) {
        return this.templateFirstEnabled ? Boolean(this.selectedTemplate) : Boolean(this.fileInfo)
      }
      if (target === this.uploadStepIndex) {
        return this.templateFirstEnabled ? Boolean(this.selectedTemplate) : Boolean(this.fileInfo)
      }
      if (target === 3 || target === 4) return this.validOutlineItems.length >= 2
      if (target === 5) return this.slides.length >= 2
      if (target === 6) return this.hasActivePptTask() || this.hasLastSuccessfulResult
      if (target === 7 || target === 8) return this.hasLastSuccessfulResult
      return false
    },
    isStepNavigable(item) {
      const target = Number(item?.id)
      if (!target || this.apiBusy || target === this.currentStep) return false
      return this.isStepAvailable(target)
    },
    navigateToStep(step) {
      const target = Number(step)
      if (this.apiBusy) {
        uni.showToast({ title: '当前任务进行中，请先取消任务', icon: 'none' })
        return
      }
      if (!this.isStepNavigable({ id: target })) return
      if (target === this.templateStepIndex) {
        this.templateEntryMode = 'library'
      } else if (target === this.uploadStepIndex) {
        this.templateEntryMode = 'upload'
      }
      this.templateReturnContext = null
      this.currentStep = target
      if (target === 5) {
        this.enterEditorPreview()
      } else if (target === 7) {
        this.loadPreviewImages()
      }
    },
    templateCategoryOf(template) {
      const id = String(template?.id || '').toLowerCase()
      const name = String(template?.name || '').toLowerCase()
      if (/(general|standard|swift)/.test(id) || /(general|standard|education|study|swift)/.test(name)) return 'study'
      return 'report'
    },
    templateCategoryName(id) {
      return this.templateCategories.find(item => item.id === id)?.name || '模板'
    },
    normalizeTemplateLayout(layout = {}, index = 0) {
      const id = String(layout.id || `layout-${index + 1}`)
      const previewTexts = Array.isArray(layout.previewTexts) ? layout.previewTexts : []
      const slots = Array.isArray(layout.slots) ? layout.slots : []
      return {
        id,
        name: this.templateLayoutName(id, index),
        type: this.templateLayoutType(id, layout, index),
        desc: String(layout.description || this.templateLayoutDescription(id, layout)).trim(),
        previewItems: [...previewTexts, ...slots].map(item => String(item || '').trim()).filter(Boolean).slice(0, 6)
      }
    },
    templateLayoutName(id = '', index = 0) {
      const value = String(id).toLowerCase()
      if (/cover|title/.test(value)) return '标题封面'
      if (/agenda|catalog|toc|contents?/.test(value)) return '目录列表'
      if (/summary|closing|conclusion/.test(value)) return '复习总结'
      if (/image|visual|photo|picture/.test(value)) return '图文讲解'
      if (/quote|focus|highlight|big/.test(value)) return '重点强调'
      return `版式 ${index + 1}`
    },
    templateLayoutType(id = '', layout = {}, index = 0) {
      const value = `${id} ${(layout.elementTypes || []).join(' ')}`.toLowerCase()
      if (/cover|title/.test(value)) return 'cover'
      if (/agenda|catalog|toc|contents?/.test(value)) return 'catalog'
      if (/quote|focus|highlight|big/.test(value)) return 'focus'
      if (/image|visual|photo|picture/.test(value)) return 'visual'
      if (/summary|closing|conclusion/.test(value)) return 'summary'
      return index % 5 === 0 ? 'visual' : 'content'
    },
    templateLayoutDescription(id = '', layout = {}) {
      const slots = Array.isArray(layout.slots) ? layout.slots.filter(Boolean).slice(0, 3).join('、') : ''
      if (slots) return `包含 ${slots}`
      const elementTypes = Array.isArray(layout.elementTypes) ? layout.elementTypes.join(' / ') : ''
      return elementTypes ? `元素：${elementTypes}` : '模板原始版式'
    },
    selectTemplateCategory(id) {
      if (this.apiBusy) return
      this.templateCategory = id
      this.persistPptTemplateSelection()
      // 切换分类时补该分类封面，避免新分类白卡
      this.loadTemplateThumbnails()
    },
    selectPptTemplate(id) {
      if (this.apiBusy) return
      if (!id) return
      if (this.pptStyle !== id) {
        if (this.hasLastSuccessfulResult) this.markEditorDirty()
        if (this.slides.length) this.slidesDirty = true
      }
      this.pptStyle = id
      // 模板切换本身也要写入活动任务缓存；否则用户在设置页退出时，缓存仍会保留默认模板。
      this.persistPptTemplateSelection()
    },
    selectOutlineMode(mode) {
      if (!mode || this.outlineMode === mode) return
      this.outlineMode = mode
      this.markOutlineSourceDirty()
    },
    markOutlineSourceDirty() {
      if (!this.outlineItems.length) return
      this.outlineSourceDirty = true
      if (this.slides.length) {
        this.slidesDirty = true
        this.markEditorDirty()
      }
    },
    markEditorDirty() {
      if (this.hasLastSuccessfulResult) this.editorDirty = true
    },
    setImageMode(mode) {
      if (this.settings.imageMode === mode) return
      this.settings.imageMode = mode
      if (this.slides.length) this.slidesDirty = true
      this.markEditorDirty()
    },
    // 单个模板缩略图按需下载，pending 去重防重复请求
    ensureTemplateThumbnail(templateId) {
      const template = this.pptStyles.find(item => item.id === templateId)
      if (!template || template.thumbnailUrl || !template.hasThumbnail) return Promise.resolve()
      if (this.templateThumbPending[templateId]) return this.templateThumbPending[templateId]
      const request = (async () => {
        try {
          const thumbnailPath = await downloadPptTemplateThumbnail(templateId)
          template.thumbnailUrl = thumbnailPath
          this.templateThumbnailState = {
            ...this.templateThumbnailState,
            [templateId]: thumbnailPath
          }
          this.persistPptTemplateSelection()
        } catch (error) {
          // 网络抖动不能把模板永久标记成“无封面”，下次进入或重新加载时继续重试。
          template.thumbnailUrl = ''
          template.hasThumbnail = true
        } finally {
          delete this.templateThumbPending[templateId]
        }
      })()
      this.templateThumbPending[templateId] = request
      return request
    },
    onTemplateThumbnailError(templateId) {
      const template = this.pptStyles.find(item => item.id === templateId)
      if (!template) return
      clearPptTemplateThumbnailCache(templateId)
      template.thumbnailUrl = ''
      template.hasThumbnail = true
      const nextState = { ...this.templateThumbnailState }
      delete nextState[templateId]
      this.templateThumbnailState = nextState
      this.ensureTemplateThumbnail(templateId)
    },
    persistPptTemplateSelection() {
      const activeTask = this.taskId
        ? { kind: 'final', taskId: this.taskId }
        : this.slideGenerationTaskId
          ? { kind: 'slides', taskId: this.slideGenerationTaskId }
          : this.outlineGenerationTaskId
            ? { kind: 'outline', taskId: this.outlineGenerationTaskId }
            : null
      if (activeTask) this.persistActiveTask(activeTask.kind, activeTask.taskId)
    },
    // 模板库可见时，只为当前分类的卡片补图，小并发避免请求被吞
    loadTemplateThumbnails() {
      const queue = this.filteredPptTemplates.filter(item => item.hasThumbnail && !item.thumbnailUrl).map(item => item.id)
      if (!queue.length) return
      let cursor = 0
      const worker = async () => {
        while (cursor < queue.length) {
          const id = queue[cursor++]
          await this.ensureTemplateThumbnail(id)
        }
      }
      for (let i = 0; i < Math.min(3, queue.length); i += 1) worker()
    },
    rememberTemplateReturnContext() {
      // 已经在模板区域内部切换库/详情时，保留最初的外部入口。
      if (this.isTemplateStep) return
      this.templateReturnContext = {
        currentStep: this.currentStep,
        entryMode: this.templateEntryMode
      }
    },
    restoreTemplateReturnContext() {
      const context = this.templateReturnContext
      this.templateReturnContext = null
      if (!context?.currentStep) {
        this.templateEntryMode = 'library'
        this.currentStep = this.templateStepIndex
        return
      }
      this.templateEntryMode = context.entryMode || 'library'
      this.currentStep = context.currentStep
    },
    returnFromTemplateSelection() {
      const context = this.templateReturnContext
      if (!context?.currentStep || context.entryMode === 'upload') return false
      this.templateReturnContext = null
      this.templateEntryMode = context.entryMode || 'library'
      this.currentStep = context.currentStep
      return true
    },
    showTemplateLibrary() {
      if (this.apiBusy) return
      this.rememberTemplateReturnContext()
      this.templateEntryMode = 'library'
      this.currentStep = this.templateStepIndex
      this.loadTemplateThumbnails()
    },
    showTemplateDetail(id = '') {
      if (this.apiBusy) return
      if (id) {
        // 从模板库打开详情时没有外部页面，关闭后回模板库；若模板库由
        // 上传资料/设置页打开，则沿用那个外部入口。
        if (!this.templateReturnContext) {
          this.templateReturnContext = {
            currentStep: this.templateStepIndex,
            entryMode: 'library'
          }
        }
      } else {
        // 上传资料页的“查看”直接打开详情，必须记住上传页而不是当前模板页。
        this.rememberTemplateReturnContext()
      }
      if (id) {
        this.pptStyle = id
        this.persistPptTemplateSelection()
      }
      if (!this.selectedTemplate && this.pptStyles.length) {
        this.pptStyle = this.pptStyles[0].id
        this.persistPptTemplateSelection()
      }
      this.activeLayoutIndex = 0
      this.templateEntryMode = 'detail'
      this.currentStep = this.templateStepIndex
      this.openLayoutViewer()
    },
    onLayoutStripScroll(event) {
      const scrollTop = Number(event?.detail?.scrollTop || 0)
      const stride = this.layoutItemStride || 300
      const total = this.selectedTemplateLayouts.length
      const index = Math.min(total - 1, Math.max(0, Math.round(scrollTop / stride)))
      if (index !== this.activeLayoutIndex) {
        this.activeLayoutIndex = index
      }
      this.measureLayoutStride()
    },
    measureLayoutStride() {
      if (this.layoutItemStride || !this.layoutViewerVisible) return
      const query = uni.createSelectorQuery().in(this)
      query.select('#layout-item-0').boundingClientRect()
      query.select('#layout-item-1').boundingClientRect()
      query.exec(rects => {
        const first = rects && rects[0]
        const second = rects && rects[1]
        if (first && second && second.top - first.top > 0) {
          this.layoutItemStride = second.top - first.top
        } else if (first && first.height > 0) {
          this.layoutItemStride = first.height + 12
        }
      })
    },
    openLayoutViewer() {
      this.layoutScrollTop = 0
      this.layoutItemStride = 0
      this.layoutViewerVisible = true
      // 只加载用户点开的这一个模板：全部版式图加载完（loading 门）才放行浏览；
      // 小程序 downloadFile 有并发上限，用小并发队列避免请求被吞
      const templateId = this.selectedTemplate?.id
      const total = this.selectedTemplateLayouts.length
      this.layoutLoadedCount = 0
      const cache = (templateId && this.layoutPreviewCache[templateId]) || {}
      const cachedCount = this.selectedTemplateLayouts.filter((layout, index) => cache[index]).length
      if (!templateId || !total || cachedCount >= total) {
        this.layoutViewerReady = true
        this.$nextTick(() => setTimeout(() => this.measureLayoutStride(), 80))
        return
      }
      this.layoutViewerReady = false
      this.layoutLoadedCount = cachedCount
      const finishOne = () => {
        this.layoutLoadedCount += 1
        if (this.layoutLoadedCount >= total) {
          this.layoutViewerReady = true
          this.$nextTick(() => setTimeout(() => this.measureLayoutStride(), 80))
        }
      }
      const concurrency = Math.min(6, total)
      let cursor = 0
      const worker = async () => {
        while (cursor < total) {
          const index = cursor++
          try {
            await this.ensureLayoutPreview(templateId, index)
          } finally {
            finishOne()
          }
        }
      }
      for (let i = 0; i < concurrency; i += 1) worker()
    },
    useTemplateFromViewer() {
      this.layoutViewerVisible = false
      this.templateReturnContext = null
      this.goNext()
    },
    closeLayoutViewer() {
      this.layoutViewerVisible = false
      this.restoreTemplateReturnContext()
    },
    async ensureLayoutPreview(templateId, index) {
      const cache = this.layoutPreviewCache[templateId] || {}
      if (cache[index]) return cache[index]
      const pendingKey = `${templateId}:${index}`
      if (this.layoutPreviewPending[pendingKey]) return this.layoutPreviewPending[pendingKey]
      const request = (async () => {
        try {
          const tempPath = await downloadPptLayoutPreview(templateId, index + 1)
          this.layoutPreviewCache = {
            ...this.layoutPreviewCache,
            [templateId]: {
              ...(this.layoutPreviewCache[templateId] || {}),
              [index]: tempPath
            }
          }
          this.layoutPreviewFailed[pendingKey] = false
          return tempPath
        } catch (error) {
          if (!this.layoutPreviewFailed[pendingKey] && index === this.activeLayoutIndex) {
            uni.showToast({ title: '版式图加载失败，可重试', icon: 'none' })
          }
          this.layoutPreviewFailed[pendingKey] = true
          return ''
        } finally {
          delete this.layoutPreviewPending[pendingKey]
        }
      })()
      this.layoutPreviewPending[pendingKey] = request
      return request
    },
    retryLayoutPreview(index) {
      const templateId = this.selectedTemplate?.id
      if (!templateId) return
      this.layoutPreviewFailed[`${templateId}:${index}`] = false
      this.ensureLayoutPreview(templateId, index)
    },
    showTemplateUpload(sourceMode = this.templateEntryMode) {
      if (this.apiBusy) return
      if (!this.selectedTemplate && this.pptStyles.length) {
        this.pptStyle = this.pptStyles[0].id
      }
      this.templateUploadSource = sourceMode === 'detail' ? 'detail' : 'upload'
      this.templateEntryMode = 'upload'
      this.currentStep = this.uploadStepIndex
    },
    chooseTxtFile() {
      if (typeof uni.chooseFile === 'function') {
        uni.chooseFile({
          count: 1,
          extension: this.enhancedEngineAvailable
            ? ['txt', 'doc', 'docx', 'ppt', 'pptx', 'xls', 'xlsx']
            : ['txt'],
          success: result => this.handleSelectedFile((result.tempFiles || [])[0], (result.tempFilePaths || [])[0]),
          fail: error => {
            if (!String(error?.errMsg || '').includes('cancel')) uni.showToast({ title: '文件选择失败', icon: 'none' })
          }
        })
        return
      }
      if (typeof uni.chooseMessageFile === 'function') {
        uni.chooseMessageFile({
          count: 1,
          type: 'file',
          extension: this.enhancedEngineAvailable
            ? ['txt', 'doc', 'docx', 'ppt', 'pptx', 'xls', 'xlsx']
            : ['txt'],
          success: result => this.handleSelectedFile((result.tempFiles || [])[0]),
          fail: error => {
            if (!String(error?.errMsg || '').includes('cancel')) uni.showToast({ title: '文件选择失败', icon: 'none' })
          }
        })
        return
      }
      uni.showToast({ title: '当前环境暂不支持选择文件', icon: 'none' })
    },
    async handleSelectedFile(file = {}, fallbackPath = '') {
      const name = String(file.name || fallbackPath.split('/').pop() || '学习资料.txt')
      const isText = /\.txt$/i.test(name)
      if (!isText && !this.enhancedEngineAvailable) {
        uni.showToast({ title: '请选择 TXT 格式文件', icon: 'none' })
        return
      }
      try {
        if (!isText) {
          const path = file.path || file.tempFilePath || fallbackPath
          if (!path) throw new Error('未获取到待上传文件路径')
          uni.showLoading({ title: '正在上传资料' })
          try {
            const uploaded = await uploadPptSourceFile(path, name)
            if (!uploaded.fileId) throw new Error('服务端未返回资料文件编号')
            this.markOutlineSourceDirty()
            this.sourceFileId = String(uploaded.fileId)
            this.fileContent = ''
            this.manualSourceContent = ''
            this.previewExpanded = false
            const size = Number(file.size || uploaded.size || 0)
            this.fileInfo = { name, size, sizeLabel: this.formatFileSize(size) }
          } finally {
            uni.hideLoading()
          }
          return
        }
        const content = await this.readTextFile(file, fallbackPath)
        if (!content.trim()) {
          uni.showToast({ title: 'TXT 文件内容为空', icon: 'none' })
          return
        }
        this.markOutlineSourceDirty()
        this.fileContent = content
        this.manualSourceContent = content
        this.sourceFileId = ''
        this.previewExpanded = false
        const estimatedSize = typeof Blob !== 'undefined' ? new Blob([content]).size : encodeURIComponent(content).replace(/%[0-9A-F]{2}/g, 'x').length
        const size = Number(file.size || estimatedSize || 0)
        this.fileInfo = { name, size, sizeLabel: this.formatFileSize(size) }
      } catch (error) {
        const action = isText ? '文件读取' : '资料上传'
        const detail = String(error?.message || error?.errMsg || '').trim()
        uni.showToast({ title: detail || `${action}失败`, icon: 'none' })
      }
    },
    readTextFile(file, fallbackPath) {
      const nativeFile = file.file || file
      if (typeof FileReader !== 'undefined' && typeof Blob !== 'undefined' && nativeFile instanceof Blob) {
        return new Promise((resolve, reject) => {
          const reader = new FileReader()
          reader.onload = event => resolve(String(event.target?.result || ''))
          reader.onerror = reject
          reader.readAsText(nativeFile, 'UTF-8')
        })
      }
      const path = file.path || file.tempFilePath || fallbackPath
      if (typeof uni.getFileSystemManager === 'function' && path) {
        return new Promise((resolve, reject) => {
          uni.getFileSystemManager().readFile({ filePath: path, encoding: 'utf8', success: result => resolve(String(result.data || '')), fail: reject })
        })
      }
      return Promise.reject(new Error('unsupported file reader'))
    },
    formatFileSize(size) {
      if (size < 1024) return `${size} B`
      if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
      return `${(size / 1024 / 1024).toFixed(1)} MB`
    },
    removeFile() {
      if (this.apiBusy) {
        uni.showToast({ title: '生成进行中，请先取消任务', icon: 'none' })
        return
      }
      this.clearTaskContext()
      this.fileInfo = null
      this.fileContent = ''
      this.manualSourceContent = ''
      this.sourceFileId = ''
      this.previewExpanded = false
      this.outlineName = ''
      this.outlineItems = []
      this.outlineDocument = null
      this.outlineSourceDirty = false
      this.layoutMarkdown = ''
      this.outlineSavedAt = ''
      this.slides = []
    },
    clearTaskContext(clearStorage = true) {
      this.generationRunId += 1
      this.outlineGenerationRunId += 1
      this.slideGenerationRunId += 1
      this.taskId = ''
      this.taskResult = null
      this.pendingTaskFingerprint = ''
      this.completedTaskFingerprint = ''
      this.editorDirty = false
      this.lastSuccessfulResult = null
      this.slidesDirty = false
      this.outlineSourceDirty = false
      this.outlineGenerationTaskId = ''
      this.slideGenerationTaskId = ''
      this.outlineGenerationSnapshot = null
      this.slideGenerationSnapshot = null
      this.generationWarnings = []
      this.contentQuality = null
      this.previewImages = {}
      this.resetEditorPreviewSession()
      this.editorPreviewImage = ''
      this.editorPreviewSlideIndex = -1
      this.lastPptError = ''
      this.exportReady = false
      if (clearStorage) {
        try { uni.removeStorageSync('aiPptActiveTask') } catch (error) {}
      }
    },
    applyManualSourceInput(event) {
      this.markOutlineSourceDirty()
      const content = String(event?.detail?.value ?? this.manualSourceContent ?? '')
      this.manualSourceContent = content
      if (!content.trim()) {
        if (this.fileInfo?.manual || (this.fileInfo && !this.sourceFileId)) {
          this.fileInfo = null
          this.fileContent = ''
          this.sourceFileId = ''
        }
        return
      }
      if (this.fileInfo && !this.fileInfo.manual) {
        this.previewExpanded = false
        return
      }
      const estimatedSize = typeof Blob !== 'undefined' ? new Blob([content]).size : encodeURIComponent(content).replace(/%[0-9A-F]{2}/g, 'x').length
      this.fileContent = content
      this.sourceFileId = ''
      this.fileInfo = {
        name: '手动输入资料.txt',
        size: estimatedSize,
        sizeLabel: this.formatFileSize(estimatedSize),
        manual: true
      }
      this.previewExpanded = false
    },
    async goNext() {
      if (this.isTemplateStep) {
        if (!this.selectedTemplate) return
        if (this.returnFromTemplateSelection()) return
        if (this.templateFirstEnabled) {
          this.templateEntryMode = 'upload'
          this.currentStep = this.uploadStepIndex
          return
        }
        return this.handleOutlineEntryNext()
      }
      if (this.isUploadStep) {
        if (!this.fileInfo) return
        if (!this.templateFirstEnabled) {
          this.templateEntryMode = 'library'
          this.currentStep = this.templateStepIndex
          return
        }
        return this.handleOutlineEntryNext()
      }
      this.currentStep = Math.min(this.stepMeta.length, this.currentStep + 1)
    },
    handleOutlineEntryNext() {
      if (this.outlineSourceDirty && this.validOutlineItems.length >= 2) {
        uni.showModal({
          title: '资料已修改',
          content: '检测到上传资料或大纲来源已修改，是否重新生成大纲？',
          confirmText: '重新生成',
          cancelText: '返回原大纲',
          success: result => {
            if (result?.confirm) {
              this.prepareOutline()
            } else {
              this.outlineSourceDirty = false
              this.currentStep = 3
            }
          }
        })
        return
      }
      if (this.canReturnToOutline) {
        this.currentStep = 3
        return
      }
      return this.prepareOutline()
    },
    goPrevious() {
      if (this.apiBusy) return
      if (this.isTemplateStep && this.templateEntryMode === 'detail') {
        this.templateEntryMode = 'library'
        return
      }
      if (this.isUploadStep && this.templateFirstEnabled) {
        this.templateEntryMode = 'library'
        this.currentStep = this.templateStepIndex
        return
      }
      if (this.isTemplateStep && !this.templateFirstEnabled) {
        this.templateEntryMode = 'upload'
        this.currentStep = this.uploadStepIndex
        return
      }
      if (this.currentStep === 3) {
        this.templateEntryMode = this.templateFirstEnabled ? 'upload' : 'library'
      }
      this.currentStep = Math.max(1, this.currentStep - 1)
    },
    toggleSetting(key, event) {
      const value = Boolean(event?.detail?.value)
      if (this.settings[key] === value) return
      this.settings[key] = value
      if (this.slides.length) this.slidesDirty = true
      this.markEditorDirty()
    },
    async prepareOutline() {
      if (!this.fileInfo) return
      if (this.outlineMode === 'original_outline') {
        const detected = this.detectOutlineItems()
        // 章节标题不足 2 条说明资料没有可用的原结构（如短主题输入），
        // 硬塞"资料核心内容"等假条目会得到没有要点的空壳大纲，
        // 此时回退走 AI 生成分支
        if (detected.length >= 2) {
          this.outlineItems = detected
          this.outlineDocument = { title: this.resultName, items: this.outlineItems }
          this.outlineName = `${this.resultName}大纲`
          this.outlineSavedAt = ''
          this.outlineSourceDirty = false
          this.currentStep = 3
          return
        }
      }
      this.apiBusy = true
      this.modelConfigError = false
      this.lastPptError = ''
      this.startOperationFeedback()
      try {
        const runId = ++this.outlineGenerationRunId
        const response = await createPptOutlineTask({
          sourceName: this.fileInfo.name,
          sourceContent: this.fileContent,
          sourceFileId: this.sourceFileId,
          outlineMode: this.outlineMode,
          pageCount: this.pageCount,
           sourceSupplement: this.sourceFileId ? this.manualSourceContent : '',
          topic: this.initialTopic || this.resultName
        })
        const created = this.responseData(response)
        this.outlineGenerationTaskId = String(created.taskId || '')
        if (!this.outlineGenerationTaskId) throw new Error('服务端未返回大纲生成任务编号')
        this.applyOutlineGenerationSnapshot({
          ...created,
          taskId: this.outlineGenerationTaskId,
          status: created.status || 'queued',
          progress: Number.isFinite(Number(created.progress)) ? Number(created.progress) : 0,
          stage: created.stage || 'queued',
          message: created.message || '大纲生成已进入队列'
        }, runId)
        await this.followOutlineGenerationTask(runId)
        if (runId !== this.outlineGenerationRunId) return
        const task = this.responseData(await getPptTask(this.outlineGenerationTaskId))
        const extracted = this.extractOutlineItems(task)
        const items = extracted.items
        const outline = extracted.outline
        this.updateOperationFeedback(100, '正在整理可编辑大纲', '大纲生成已完成，正在转换为可编辑内容')
        if (!items.length) {
          console.error('[PPT outline] completed task has no editable items', {
            taskId: this.outlineGenerationTaskId,
            status: task?.status,
            stage: task?.stage,
            taskKeys: task && typeof task === 'object' ? Object.keys(task) : [],
            outlineKeys: outline && typeof outline === 'object' ? Object.keys(outline) : []
          })
          throw new Error(task?.error?.message || task?.message || '服务端未返回可编辑的大纲，请重试')
        }
        this.outlineItems = this.normalizeOutlineItems(items)
        this.outlineDocument = { ...outline, items: this.outlineItems }
        this.outlineName = `${outline.title || this.resultName}大纲`
        this.outlineSavedAt = ''
        this.outlineSourceDirty = false
        this.clearActiveTaskStorage()
        this.currentStep = 3
      } catch (error) {
        this.handlePptError(error, '大纲生成失败')
      } finally {
        this.apiBusy = false
        this.stopOperationFeedback()
      }
    },
    async followOutlineGenerationTask(runId) {
      try {
        this.outlineGenerationStream = streamPptTask(this.outlineGenerationTaskId, {
          onEvent: (eventName, payload) => this.applyOutlineGenerationSnapshot(payload, runId),
          onDone: payload => this.applyOutlineGenerationSnapshot(payload, runId),
          onError: payload => this.applyOutlineGenerationSnapshot(payload, runId)
        })
        await this.outlineGenerationStream
      } catch (error) {
        if (runId !== this.outlineGenerationRunId) return
        await this.pollOutlineGenerationTask(runId)
      } finally {
        this.outlineGenerationStream = null
      }
    },
    async pollOutlineGenerationTask(runId) {
      for (let attempt = 0; attempt < 900 && runId === this.outlineGenerationRunId; attempt += 1) {
        const response = await getPptTask(this.outlineGenerationTaskId)
        const task = this.responseData(response)
        this.applyOutlineGenerationSnapshot(task, runId)
        if (['completed', 'failed', 'cancelled', 'timed_out'].includes(String(task.status || ''))) return
        await new Promise(resolve => setTimeout(resolve, 1000))
      }
      if (runId === this.outlineGenerationRunId) throw new Error('大纲生成等待超时，可稍后重新进入查看')
    },
    applyOutlineGenerationSnapshot(task, runId) {
      if (!task || runId !== this.outlineGenerationRunId) return
      this.outlineGenerationSnapshot = task
      this.persistActiveTask('outline', this.outlineGenerationTaskId)
      if (task.status === 'failed' || task.status === 'timed_out') {
        this.clearActiveTaskStorage()
        throw new Error(task.error?.message || task.message || '大纲生成失败')
      }
      if (task.status === 'cancelled') {
        this.clearActiveTaskStorage()
        this.apiBusy = false
        throw new Error('大纲生成已取消')
      }
    },
    detectOutlineItems() {
      const source = [this.fileContent, this.manualSourceContent].map(value => String(value || '').trim()).filter(Boolean).join('\n')
      const lines = source.split(/\r?\n/).map(line => line.trim()).filter(Boolean)
      const candidates = lines.filter(line => {
        if (line.length > 42) return false
        return /^(第[一二三四五六七八九十百\d]+[章节篇部分]|[一二三四五六七八九十]+[、.．]|[\d]+[、.．])/.test(line)
      }).slice(0, 24)
      return candidates.map(line => {
        let level = 3
        if (/^第[一二三四五六七八九十百\d]+[章节篇部分]/.test(line)) level = 1
        else if (/^[一二三四五六七八九十]+[、.．]/.test(line)) level = 2
        return this.createOutlineItem(line, level)
      })
    },
    extractOutlineItems(task) {
      const root = this.responseData(task)
      const nestedOutline = this.responseData(root?.outline)
      const nestedResult = this.responseData(root?.result)
      const candidates = [
        root?.items,
        nestedOutline?.items,
        nestedResult?.items,
        root?.data?.items,
        root?.payload?.items
      ]
      const items = candidates.find(value => Array.isArray(value) && value.length) || []
      if (items.length) {
        const source = Array.isArray(nestedOutline?.items) && nestedOutline.items.length
          ? nestedOutline
          : (Array.isArray(nestedResult?.items) && nestedResult.items.length ? nestedResult : root)
        return { items, outline: { ...(source || {}), items } }
      }
      const markdown = String(
        root?.outlineMarkdown || nestedOutline?.outlineMarkdown || nestedResult?.outlineMarkdown || ''
      ).trim()
      const parsed = this.parseOutlineMarkdownItems(markdown)
      return { items: parsed, outline: { ...(nestedOutline || root || {}), items: parsed } }
    },
    parseOutlineMarkdownItems(markdown) {
      const text = String(markdown || '')
      if (!text) return []
      const matches = []
      const headingPattern = /^###\s+(?!大纲信息\s*$)(.+)$/gm
      let match
      while ((match = headingPattern.exec(text))) matches.push({ title: match[1].trim(), start: match.index, end: headingPattern.lastIndex })
      return matches.map((heading, index) => {
        const block = text.slice(heading.end, matches[index + 1]?.start || text.length)
        const title = block.match(/^\s*-\s*页标题[：:]\s*(.+)$/m)?.[1]?.trim() || heading.title
        const contentStart = block.search(/^\s*-\s*核心内容[：:]?.*$/m)
        const contentBlock = contentStart >= 0 ? block.slice(contentStart) : block
        const points = [...contentBlock.matchAll(/^\s*-\s+(.+)$/gm)]
          .map(item => item[1].trim())
          .filter(item => item && !/^(页标题|页面类型|本页目标|核心内容|展示建议|素材建议)[：:]?/.test(item))
        const rawLevel = block.match(/^\s*-\s*(?:大纲层级|层级)[：:]\s*(.+)$/m)?.[1]?.trim()
        return {
          id: `slide_${index + 1}`,
          level: this.normalizeOutlineLevel(rawLevel, title),
          title,
          type: block.match(/^\s*-\s*页面类型[：:]\s*(.+)$/m)?.[1]?.trim() || 'content',
          objective: block.match(/^\s*-\s*本页目标[：:]\s*(.+)$/m)?.[1]?.trim() || '',
          keyPoints: points
        }
      }).filter(item => item.title || item.keyPoints.length)
    },
    createOutlineItem(title = '', level = 2) {
      return { id: `outline-${Date.now()}-${Math.random().toString(16).slice(2)}`, title, level }
    },
    addOutlineItem() {
      this.outlineItems.push(this.createOutlineItem('', 2))
    },
    normalizeOutlineLevel(value, title = '') {
      const raw = value && typeof value === 'object'
        ? (value.level ?? value.outlineLevel ?? value['大纲层级'] ?? value['层级'])
        : value
      const text = String(raw ?? '').replace(/\s+/g, '').toLowerCase()
      if (/^[123]$/.test(text)) return Number(text)
      const aliases = {
        '章节': 1, '章': 1, 'chapter': 1, 'section': 1, '一级': 1,
        '小节': 2, '节': 2, '节点': 2, 'node': 2, 'subsection': 2, '二级': 2,
        '知识点': 3, '知识节点': 3, '要点': 3, 'knowledgepoint': 3, '三级': 3
      }
      if (Object.prototype.hasOwnProperty.call(aliases, text)) return aliases[text]
      return this.inferOutlineLevelFromTitle(title)
    },
    normalizeOutlineItems(items) {
      const normalized = (Array.isArray(items) ? items : []).map((item, index) => ({
        ...item,
        id: item.id || `outline-${Date.now()}-${index}`,
        level: this.normalizeOutlineLevel(
          item.level ?? item.outlineLevel ?? item['大纲层级'] ?? item['层级']
            ?? (['章节', '小节', '节点', '知识点'].includes(String(item.type || '').trim()) ? item.type : ''),
          item.title
        ),
        title: String(item.title || '')
      }))
      if (normalized.length < 2 || !normalized.every(item => item.level === 1)) return normalized

      const titles = normalized.map(item => String(item.title || '').trim())
      const allChapterTitles = titles.every(title => /^(?:第[一二三四五六七八九十百\d]+[篇章节部分]|\d+[、.．)）])/.test(title))
      if (allChapterTitles) return normalized
      normalized.forEach((item, index) => {
        const pageType = String(item.type || '').toLowerCase()
        const title = titles[index]
        if (index === 0 || ['封面页', '目录页', 'cover', 'catalog'].includes(pageType)) item.level = 1
        else if (index === normalized.length - 1 && ['总结页', 'summary'].includes(pageType)) item.level = 1
        else if (/知识点|知识节点|考点|要点|细节|关键概念|机制|方法|应用|案例|辨析/.test(title)) item.level = 3
        else item.level = 2
      })
      return normalized
    },
    outlineLevelIndex(level) {
      const index = this.outlineLevels.findIndex(item => item.value === this.normalizeOutlineLevel(level))
      return index >= 0 ? index : 1
    },
    inferOutlineLevelFromTitle(title) {
      const value = String(title || '').replace(/\s+/g, '')
      if (/^第[一二三四五六七八九十百\d]+[篇章节部分]/.test(value)) return 1
      if (/^\d+\.\d+\.\d+(?:[.、．)）]|$|(?=[\u4e00-\u9fff]))/.test(value)) return 3
      if (/^\d+\.\d+(?:[.、．)）]|$|(?=[\u4e00-\u9fff]))/.test(value)) return 2
      if (/^\d+(?:[、.．)）]|(?=[\u4e00-\u9fff]))/.test(value)) return 2
      if (/^[一二三四五六七八九十百]+[、.．)）]/.test(value)) return 2
      return 1
    },
    outlineLevelLabel(level) {
      return this.outlineLevels[this.outlineLevelIndex(level)]?.label || '小节'
    },
    updateOutlineItemLevel(index, event) {
      const selected = this.outlineLevels[Number(event?.detail?.value || 0)] || this.outlineLevels[1]
      if (!this.outlineItems[index] || !selected) return
      this.outlineItems[index].level = selected.value
    },
    removeOutlineItem(index) {
      if (this.outlineItems.length <= 1) {
        uni.showToast({ title: '至少保留一个大纲项', icon: 'none' })
        return
      }
      this.outlineItems.splice(index, 1)
    },
    moveOutlineItem(index, direction) {
      const target = index + direction
      if (target < 0 || target >= this.outlineItems.length) return
      const items = [...this.outlineItems]
      const current = items[index]
      items.splice(index, 1)
      items.splice(target, 0, current)
      this.outlineItems = items
    },
    confirmOutline() {
      if (this.validOutlineItems.length < 2) {
        uni.showToast({ title: '至少保留两页大纲内容', icon: 'none' })
        return
      }
      this.saveOutlineSnapshot(false)
      if (this.slides.length) this.slidesDirty = true
      this.currentStep = 4
    },
    handleSettingsNext() {
      if (this.canReturnToEditor) {
        this.currentStep = 5
        this.enterEditorPreview()
        return
      }
      return this.prepareSlides()
    },
    saveOutlineSnapshot(showFeedback = true) {
      if (!this.validOutlineItems.length) return
      const snapshot = {
        id: `outline-history-${Date.now()}`,
        name: String(this.outlineName || `${this.resultName}大纲`).trim(),
        source: this.outlineMode,
        createdAt: this.formatNow(),
        items: this.validOutlineItems.map(item => ({ ...item }))
      }
      const previous = this.outlineHistory[0]
      const sameAsPrevious = previous &&
        previous.name === snapshot.name &&
        previous.source === snapshot.source &&
        JSON.stringify(previous.items.map(({ title, level }) => ({ title, level }))) === JSON.stringify(snapshot.items.map(({ title, level }) => ({ title, level })))
      if (!sameAsPrevious) {
        this.outlineHistory = [snapshot, ...this.outlineHistory].slice(0, 30)
        this.persistHistories()
      }
      this.outlineSavedAt = this.formatTime()
      if (showFeedback) uni.showToast({ title: sameAsPrevious ? '大纲已是最新' : '大纲已保存', icon: 'none' })
    },
    async prepareSlides() {
      const outlines = this.validOutlineItems
      if (outlines.length < 2) {
        uni.showToast({ title: '至少保留两页大纲内容', icon: 'none' })
        this.currentStep = 3
        return
      }
      const runId = ++this.slideGenerationRunId
      this.apiBusy = true
      this.modelConfigError = false
      this.lastPptError = ''
      this.slideGenerationSnapshot = null
      this.contentQuality = null
      try {
        const outline = {
          ...(this.outlineDocument || {}),
          title: this.outlineName || this.resultName,
          items: outlines.map(item => ({ ...item }))
        }
        const response = await createPptSlidesTask({
          outline,
          sourceContent: this.fileContent,
          sourceFileId: this.sourceFileId,
          sourceSupplement: this.sourceFileId ? this.manualSourceContent : '',
          settings: this.buildSettings(),
          sharedPrompt: this.sharedPrompt
        })
        const created = this.responseData(response)
        this.slideGenerationTaskId = String(created.taskId || '')
        if (!this.slideGenerationTaskId) throw new Error('服务端未返回逐页生成任务编号')
        this.persistActiveTask('slides', this.slideGenerationTaskId)
        await this.followSlideGenerationTask(runId)
        if (runId !== this.slideGenerationRunId) return
        const result = this.responseData(await getPptTask(this.slideGenerationTaskId))
        this.generationWarnings = Array.isArray(result.warnings) ? result.warnings : []
        this.contentQuality = result.contentQuality && typeof result.contentQuality === 'object'
          ? this.clonePptValue(result.contentQuality)
          : null
        const slides = Array.isArray(result.slides) ? result.slides : []
        if (result.presentationId) {
          this.outlineDocument = {
            ...(this.outlineDocument || {}),
            presentationId: String(result.presentationId)
          }
        }
        if (slides.length < 2) throw new Error('生成的页面数量不足，请调整大纲后重试')
        this.slides = this.normalizeEditorSlides(slides)
        this.layoutMarkdown = String(result.layoutMarkdown || '')
        if (result.sharedPrompt) this.sharedPrompt = String(result.sharedPrompt)
        this.pageCount = this.slides.length
        this.activeSlideIndex = 0
        this.slidesDirty = false
        this.clearActiveTaskStorage()
        this.resetEditorPreviewSession()
        this.currentStep = 5
        this.enterEditorPreview(0)
      } catch (error) {
        this.handlePptError(error, '页面内容生成失败')
      } finally {
        this.apiBusy = false
        this.stopOperationFeedback()
      }
    },
    async followSlideGenerationTask(runId) {
      try {
        this.slideGenerationStream = streamPptTask(this.slideGenerationTaskId, {
          onEvent: (eventName, payload) => this.applySlideGenerationSnapshot(payload, runId),
          onDone: payload => this.applySlideGenerationSnapshot(payload, runId),
          onError: payload => this.applySlideGenerationSnapshot(payload, runId)
        })
        await this.slideGenerationStream
      } catch (error) {
        if (runId !== this.slideGenerationRunId) return
        await this.pollSlideGenerationTask(runId)
      } finally {
        this.slideGenerationStream = null
      }
    },
    async pollSlideGenerationTask(runId) {
      for (let attempt = 0; attempt < 1200 && runId === this.slideGenerationRunId; attempt += 1) {
        const response = await getPptTask(this.slideGenerationTaskId)
        const task = this.responseData(response)
        this.applySlideGenerationSnapshot(task, runId)
        if (['completed', 'failed', 'cancelled', 'timed_out'].includes(String(task.status || ''))) return
        await new Promise(resolve => setTimeout(resolve, 1000))
      }
      if (runId === this.slideGenerationRunId) throw new Error('逐页生成等待超时，可稍后重新进入查看')
    },
    applySlideGenerationSnapshot(task, runId) {
      if (!task || runId !== this.slideGenerationRunId) return
      this.slideGenerationSnapshot = task
      this.persistActiveTask('slides', this.slideGenerationTaskId)
      if (task.status === 'failed') {
        this.clearActiveTaskStorage()
        throw new Error(task.error?.message || task.message || '逐页内容生成失败')
      }
      if (task.status === 'timed_out') {
        this.clearActiveTaskStorage()
        throw new Error(task.error?.message || task.message || '逐页内容生成超时，请重试')
      }
      if (task.status === 'cancelled') {
        this.clearActiveTaskStorage()
        this.apiBusy = false
        throw new Error('逐页内容生成已取消')
      }
    },
    buildTaskPayload() {
      return {
        sourceName: this.fileInfo?.name || `${this.resultName}.txt`,
        outline: {
          ...(this.outlineDocument || {}),
          title: this.outlineName || this.resultName,
          items: this.validOutlineItems.map(item => ({ ...item }))
        },
        slides: this.slides.map(slide => ({
          ...slide,
          layoutLocked: Boolean(slide.layoutLocked),
          content: String(slide.content || '').split(/\r?\n/).map(line => line.trim()).filter(Boolean)
        })),
        sharedPrompt: this.sharedPrompt,
        settings: this.buildSettings(),
        exportFormats: ['pptx'],
        generationWarnings: [...this.generationWarnings],
        contentQuality: this.slidesDirty ? null : this.clonePptValue(this.contentQuality)
      }
    },
    normalizeEditorSlides(slides) {
      if (!Array.isArray(slides)) return []
      return slides.map((slide, index) => {
        const source = slide && typeof slide === 'object' ? slide : {}
        return {
          ...source,
          id: source.id || `slide-${index + 1}`,
          title: String(source.title || `第 ${index + 1} 页`),
          layoutLocked: Boolean(source.layoutLocked),
          content: Array.isArray(source.content)
            ? source.content.map(item => String(item || '').trim()).filter(Boolean).join('\n')
            : String(source.content || ''),
          privatePrompt: String(source.privatePrompt || '')
        }
      })
    },
    clonePptValue(value) {
      if (value == null) return value
      try {
        return JSON.parse(JSON.stringify(value))
      } catch (error) {
        return value
      }
    },
    captureSuccessfulResult() {
      if (!this.taskId || String(this.taskResult?.status || '') !== 'completed') return null
      return this.clonePptValue({
        taskId: this.taskId,
        taskResult: this.taskResult,
        slides: this.slides,
        pageCount: this.pageCount,
        pptStyle: this.pptStyle,
        settings: this.settings,
        sharedPrompt: this.sharedPrompt,
        outlineDocument: this.outlineDocument,
        outlineItems: this.outlineItems,
        outlineName: this.outlineName,
        generationWarnings: this.generationWarnings,
        contentQuality: this.contentQuality,
        exportFormat: this.exportFormat,
        savedAt: Date.now()
      })
    },
    saveSuccessfulResult() {
      const result = this.captureSuccessfulResult()
      if (!result) return
      this.lastSuccessfulResult = result
      this.editorDirty = false
    },
    restoreLastSuccessfulResult() {
      const saved = this.lastSuccessfulResult
      if (!saved?.taskId || !saved.taskResult) return false
      this.taskId = String(saved.taskId)
      this.taskResult = this.clonePptValue(saved.taskResult)
      this.slides = this.normalizeEditorSlides(this.clonePptValue(saved.slides || []))
      this.pageCount = Number(saved.pageCount || this.slides.length || this.pageCount)
      this.pptStyle = saved.pptStyle || this.pptStyle
      this.settings = { ...this.settings, ...(saved.settings || {}) }
      this.sharedPrompt = saved.sharedPrompt || this.sharedPrompt
      this.outlineItems = this.normalizeOutlineItems(
        Array.isArray(saved.outlineItems) ? this.clonePptValue(saved.outlineItems) : this.outlineItems
      )
      this.outlineDocument = saved.outlineDocument
        ? { ...saved.outlineDocument, items: this.outlineItems }
        : this.outlineDocument
      this.outlineName = saved.outlineName || this.outlineName
      this.generationWarnings = Array.isArray(saved.generationWarnings) ? [...saved.generationWarnings] : []
      this.contentQuality = saved.contentQuality && typeof saved.contentQuality === 'object'
        ? this.clonePptValue(saved.contentQuality)
        : null
      if (saved.exportFormat) this.exportFormat = saved.exportFormat
      this.activeSlideIndex = Math.min(Math.max(0, this.activeSlideIndex), Math.max(0, this.slides.length - 1))
      this.progress = 100
      this.editorDirty = false
      this.currentStep = 7
      this.exportReady = false
      this.previewImages = {}
      this.loadPreviewImages()
      return true
    },
    canReuseCompletedTask() {
      return this.hasLastSuccessfulResult && !this.editorDirty
    },
    async startGeneration() {
      if (this.apiBusy || this.slides.length < 2) return
      // 任务已完成且内容未改：直接回看结果，不重复创建任务
      if (this.canReuseCompletedTask()) {
        this.restoreLastSuccessfulResult()
        return
      }
      if (this.hasLastSuccessfulResult && this.editorDirty) {
        uni.showModal({
          title: '确认重新生成',
          content: '当前页面内容已修改，确认重新生成 PPT 吗？',
          confirmText: '确认生成',
          cancelText: '继续编辑',
          success: result => {
            if (result?.confirm) this.runGeneration()
          }
        })
        return
      }
      await this.runGeneration()
    },
    async runGeneration() {
      if (this.apiBusy || this.slides.length < 2) return
      this.clearTimers()
      const runId = ++this.generationRunId
      this.apiBusy = true
      this.modelConfigError = false
      this.lastPptError = ''
      this.currentStep = 6
      this.progress = 2
      this.taskId = ''
      this.taskResult = null
      this.completedTaskFingerprint = ''
      this.previewImages = {}
      try {
        const payload = this.buildTaskPayload()
        this.pendingTaskFingerprint = JSON.stringify(payload)
        const response = await createPptTask(payload)
        const created = this.responseData(response)
        this.taskId = String(created.taskId || '')
        if (!this.taskId) throw new Error('服务端未返回 PPT 任务编号')
        this.persistActiveTask('final', this.taskId)
        this.apiBusy = false
        await this.followGenerationTask(runId)
      } catch (error) {
        if (runId !== this.generationRunId) return
        this.apiBusy = false
        this.currentStep = 5
        this.progress = 0
        this.handlePptError(error, 'PPT 生成失败')
      }
    },
    async retryGenerationTask() {
      if (!this.taskId || this.apiBusy) return
      this.clearTimers()
      const runId = ++this.generationRunId
      this.apiBusy = true
      this.modelConfigError = false
      this.lastPptError = ''
      this.currentStep = 6
      this.progress = 2
      this.previewImages = {}
      try {
        const response = await retryPptTask(this.taskId)
        const created = this.responseData(response)
        const nextTaskId = String(created.taskId || '')
        if (!nextTaskId) throw new Error('服务端未返回重试任务编号')
        this.taskId = nextTaskId
        this.persistActiveTask('final', this.taskId)
        this.taskResult = {
          ...created,
          status: created.status || 'queued',
          message: created.message || 'PPT 重试任务已进入队列'
        }
        this.apiBusy = false
        await this.followGenerationTask(runId)
      } catch (error) {
        if (runId !== this.generationRunId) return
        this.apiBusy = false
        this.currentStep = 5
        this.progress = 0
        this.handlePptError(error, 'PPT 重试失败')
      }
    },
    async followGenerationTask(runId) {
      try {
        this.generationStream = streamPptTask(this.taskId, {
          onEvent: (eventName, payload) => this.applyTaskSnapshot(payload, runId),
          onDone: payload => this.applyTaskSnapshot(payload, runId),
          onError: payload => this.applyTaskSnapshot(payload, runId)
        })
        await this.generationStream
        const response = await getPptTask(this.taskId)
        this.applyTaskSnapshot(this.responseData(response), runId)
      } catch (error) {
        if (runId !== this.generationRunId) return
        await this.pollGenerationTask(runId)
      } finally {
        this.generationStream = null
      }
    },
    async pollGenerationTask(runId) {
      for (let attempt = 0; attempt < 300 && runId === this.generationRunId; attempt += 1) {
        const response = await getPptTask(this.taskId)
        const task = this.responseData(response)
        this.applyTaskSnapshot(task, runId)
        if (['completed', 'failed', 'cancelled', 'timed_out'].includes(String(task.status || ''))) return
        await new Promise(resolve => setTimeout(resolve, 1000))
      }
      if (runId === this.generationRunId) throw new Error('PPT 生成等待超时，可稍后重新进入查看')
    },
    applyTaskSnapshot(task, runId) {
      if (!task || runId !== this.generationRunId) return
      this.taskResult = task
      this.generationWarnings = Array.isArray(task.warnings) ? task.warnings : []
      this.persistActiveTask('final', this.taskId)
      // 任务完成即固化本次提交内容指纹（含后台完成：用户已提前返回编辑页）
      if (String(task.status || '') === 'completed' && this.pendingTaskFingerprint) {
        this.completedTaskFingerprint = this.pendingTaskFingerprint
      }
      this.progress = Math.max(this.progress, Math.min(100, Number(task.progress || 0)))
      if (task.status === 'failed') {
        this.clearActiveTaskStorage()
        const message = task.error?.message || task.message || 'PPT 生成失败'
        this.currentStep = 5
        this.progress = 0
        throw new Error(message)
      }
      if (task.status === 'timed_out') {
        this.clearActiveTaskStorage()
        const message = task.error?.message || task.message || 'PPT 生成超时，请重试'
        this.currentStep = 5
        this.progress = 0
        this.apiBusy = false
        throw new Error(message)
      }
      if (task.status === 'cancelled') {
        this.clearActiveTaskStorage()
        this.currentStep = 5
        this.progress = 0
        this.apiBusy = false
        return
      }
      if (task.status === 'completed' && this.currentStep === 6) {
        this.progress = 100
        const availableFormat = (task.attachments || []).some(item => item?.type === this.exportFormat)
          ? this.exportFormat
          : (task.attachments || []).find(item => item?.type === 'pptx')?.type
        if (availableFormat) this.exportFormat = availableFormat
        this.loadPreviewImages()
        this.saveSuccessfulResult()
        this.recordGenerationHistory()
        this.clearActiveTaskStorage()
        this.currentStep = 7
      }
    },
    generationStatusClass(index) {
      if (index < this.activeGenerationIndex) return 'generation-item__dot--done'
      if (index === this.activeGenerationIndex) return 'generation-item__dot--active'
      return 'generation-item__dot--waiting'
    },
    generationStepTitle(item, index) {
      if (index < this.activeGenerationIndex) return item.doneText
      return item.activeText
    },
    async cancelGeneration(options = {}) {
      const hadPreviousResult = this.hasLastSuccessfulResult
      const targetStep = this.currentStep === 3
        ? 3
        : (this.currentStep === 4 ? 4 : (this.currentStep === 6 ? 5 : this.currentStep))
      if (this.taskId) {
        try {
          await cancelPptTask(this.taskId)
        } catch (error) {}
      }
      if (this.slideGenerationTaskId) {
        try {
          await cancelPptTask(this.slideGenerationTaskId)
        } catch (error) {}
      }
      if (this.outlineGenerationTaskId) {
        try {
          await cancelPptTask(this.outlineGenerationTaskId)
        } catch (error) {}
      }
      this.clearTimers()
      this.generationRunId += 1
      this.outlineGenerationRunId += 1
      this.slideGenerationRunId += 1
      this.progress = 0
      this.apiBusy = false
      this.clearActiveTaskStorage()
      this.currentStep = targetStep
      if (targetStep === 5) {
        this.taskId = ''
        this.taskResult = null
        this.pendingTaskFingerprint = ''
        this.completedTaskFingerprint = ''
      }
      if (targetStep === 3) {
        this.outlineGenerationTaskId = ''
        this.outlineGenerationSnapshot = null
      }
      if (targetStep === 4) {
        this.slideGenerationTaskId = ''
        this.slideGenerationSnapshot = null
      }
      if (hadPreviousResult && targetStep === 5 && !options?.silent) {
        uni.showModal({
          title: '生成已取消',
          content: '是否返回上次成功生成的 PPT？',
          confirmText: '返回成品',
          cancelText: '继续编辑',
          success: result => {
            if (result?.confirm) this.restoreLastSuccessfulResult()
          }
        })
      } else {
        uni.showToast({ title: '已取消生成，可继续编辑', icon: 'none' })
      }
    },
    restartFromSettings() {
      uni.showModal({
        title: '确认重新生成',
        content: '重新生成会使用当前设置创建一份新的 PPT，是否继续？',
        confirmText: '继续生成',
        cancelText: '取消',
        success: result => {
          if (result?.confirm) this.goToSettingsForRegeneration()
        }
      })
    },
    goToSettingsForRegeneration() {
      // 将编辑页里的单页提示词带回大纲，下一次页面内容生成时由后端明确传给模型。
      this.copyEditorPromptsToOutline()
      this.markEditorDirty()
      this.slidesDirty = true
      this.progress = 0
      this.exportReady = false
      this.currentStep = 4
    },
    copyEditorPromptsToOutline() {
      if (!this.slides.length) return
      this.outlineItems = this.outlineItems.map((item, index) => ({
        ...item,
        privatePrompt: String(this.slides[index]?.privatePrompt || item.privatePrompt || '')
      }))
    },
    slideTitle(slide) {
      return this.slides[slide - 1]?.title || `第 ${slide} 页`
    },
    async openSlidePreview(slide) {
      const preview = (this.taskResult?.previews || []).find(item => Number(item.slideIndex) === Number(slide))
      if (!this.taskId || !preview) {
        uni.showToast({ title: '当前环境未生成页面预览图', icon: 'none' })
        return
      }
      try {
        uni.showLoading({ title: '正在读取预览' })
        const path = await downloadPptPreview(this.taskId, slide)
        uni.previewImage({ urls: [path], current: path })
      } catch (error) {
        uni.showToast({ title: this.errorMessage(error, '预览读取失败'), icon: 'none' })
      } finally {
        uni.hideLoading()
      }
    },
    openEditorPreview() {
      const path = this.editorPreviewImage && this.editorPreviewSlideIndex === this.activeSlideIndex
        ? this.editorPreviewImage
        : ''
      if (!path) {
        uni.showToast({ title: '当前页面还没有可放大的预览图', icon: 'none' })
        return
      }
      uni.previewImage({ urls: [path], current: path })
    },
    resetEditorPreviewState() {
      if (this.editorPreviewTimer) {
        clearTimeout(this.editorPreviewTimer)
        this.editorPreviewTimer = null
      }
      this.editorPreviewImage = ''
      this.editorPreviewSlideIndex = -1
      this.editorPreviewLoading = false
      this.editorPreviewQueued = false
      this.editorPreviewError = ''
      this.editorPreviewRequestId += 1
    },
    resetEditorPreviewSession() {
      this.editorPreviewSession += 1
      this.editorPreviewBatchRunId += 1
      this.editorPreviewCache = {}
      this.editorPreviewInFlight = {}
      this.editorPreviewRevisions = {}
    },
    refreshEditorPreview(index = this.activeSlideIndex) {
      const cachedPreview = this.editorPreviewCache[this.editorPreviewCacheKey(index)] || ''
      if (cachedPreview) {
        this.editorPreviewImage = cachedPreview
        this.editorPreviewSlideIndex = index
        this.editorPreviewLoading = false
        this.editorPreviewQueued = false
        this.editorPreviewError = ''
        this.editorPreviewRequestId += 1
        return
      }
      this.resetEditorPreviewState()
      this.$nextTick(() => this.scheduleEditorPreview(true))
    },
    enterEditorPreview(index = this.activeSlideIndex) {
      const nextIndex = Number(index)
      if (Number.isInteger(nextIndex) && nextIndex >= 0 && nextIndex < this.slides.length) {
        this.activeSlideIndex = nextIndex
      }
      this.startEditorPreviewBatch(0)
      this.refreshEditorPreview(this.activeSlideIndex)
    },
    startEditorPreviewBatch(startIndex = 0) {
      const runId = ++this.editorPreviewBatchRunId
      const firstIndex = Math.max(0, Number(startIndex) || 0)
      const render = async () => {
        for (let index = firstIndex; index < this.slides.length; index += 1) {
          if (runId !== this.editorPreviewBatchRunId) return
          const slide = this.slides[index]
          if (!slide || !slide.ui || typeof slide.ui !== 'object') continue
          const cacheKey = this.editorPreviewCacheKey(index)
          if (this.editorPreviewCache[cacheKey]) continue
          try {
            await this.renderEditorPreviewSlide(index)
          } catch (error) {
            // A failed page must not block the following independent pages.
          }
        }
      }
      this.editorPreviewBatchPromise = render()
      return this.editorPreviewBatchPromise
    },
    renderEditorPreviewSlide(index) {
      const slide = this.slides[index]
      if (!slide || !slide.ui || typeof slide.ui !== 'object') return Promise.resolve('')
      const cacheKey = this.editorPreviewCacheKey(index)
      const cachedPreview = this.editorPreviewCache[cacheKey] || ''
      if (cachedPreview) return Promise.resolve(cachedPreview)
      if (this.editorPreviewInFlight[cacheKey]) return this.editorPreviewInFlight[cacheKey]

      const session = this.editorPreviewSession
      const revision = this.editorPreviewRevisions[cacheKey] || 0
      const previous = this.editorPreviewRenderTail || Promise.resolve()
      const request = previous.then(async () => {
        if (session !== this.editorPreviewSession || revision !== (this.editorPreviewRevisions[cacheKey] || 0)) return ''
        const currentSlide = this.slides[index]
        if (!currentSlide || !currentSlide.ui || typeof currentSlide.ui !== 'object') return ''
        const snapshot = JSON.parse(JSON.stringify(currentSlide))
        snapshot.index = index + 1
        const response = await renderPptPreview({
          templateId: this.pptStyle || 'general',
          title: this.outlineName || this.resultName || '演示文稿',
          settings: this.buildSettings(),
          slide: snapshot
        })
        const result = this.responseData(response)
        if (!result.imageBase64) throw new Error('服务端未返回预览图')
        const image = `data:${result.mimeType || 'image/png'};base64,${result.imageBase64}`
        if (session === this.editorPreviewSession && revision === (this.editorPreviewRevisions[cacheKey] || 0)) {
          this.editorPreviewCache[cacheKey] = image
        }
        return image
      }).finally(() => {
        if (this.editorPreviewInFlight[cacheKey] === request) delete this.editorPreviewInFlight[cacheKey]
      })
      this.editorPreviewInFlight[cacheKey] = request
      this.editorPreviewRenderTail = request.catch(() => {})
      return request
    },
    async loadPreviewImages() {
      if (!this.taskId || !Array.isArray(this.taskResult?.previews)) return
      const previews = this.taskResult.previews
      for (const item of previews) {
        const index = Number(item?.slideIndex || 0)
        if (!index || this.previewImages[index]) continue
        try {
          this.previewImages = { ...this.previewImages, [index]: await downloadPptPreview(this.taskId, index) }
        } catch (error) {
          // Keep the lightweight fallback thumbnail when a single preview fails.
        }
      }
    },
    async uploadSlideImage() {
      if (!this.taskId || !this.activeSlide) return
      try {
        const chosen = await new Promise((resolve, reject) => {
          uni.chooseImage({ count: 1, sizeType: ['compressed'], sourceType: ['album', 'camera'], success: resolve, fail: reject })
        })
        const filePath = chosen?.tempFilePaths?.[0]
        if (!filePath) return
        const base64 = await new Promise((resolve, reject) => {
          const fs = uni.getFileSystemManager ? uni.getFileSystemManager() : (typeof wx !== 'undefined' ? wx.getFileSystemManager() : null)
          if (!fs) return reject(new Error('当前平台不支持读取图片'))
          fs.readFile({ filePath, encoding: 'base64', success: result => resolve(result.data), fail: reject })
        })
        this.apiBusy = true
        const extension = (filePath.match(/\.([a-z0-9]+)(?:\?|$)/i) || [])[1] || 'png'
        const response = await replacePptSlideImage(this.taskId, this.activeSlideIndex + 1, base64, extension)
        this.taskResult = this.responseData(response)
        this.previewImages = {}
        this.activeSlide.imageStatus = 'uploaded'
        this.generationRunId += 1
        this.currentStep = 6
        this.progress = 0
        await this.followGenerationTask(this.generationRunId)
      } catch (error) {
        uni.showToast({ title: this.errorMessage(error, '图片替换失败'), icon: 'none' })
      } finally {
        this.apiBusy = false
      }
    },
    selectExportFormat(format) {
      if (!this.isExportAvailable(format)) {
        uni.showToast({ title: this.formatErrorMessage(format), icon: 'none' })
        return
      }
      if (this.exportFormat !== format) {
        this.exportFormat = format
        this.exportReady = false
      }
    },
    isExportAvailable(format) {
      return this.exportAttachmentTypes.includes(String(format || '').toLowerCase())
    },
    formatErrorMessage(format) {
      const id = String(format || '').toLowerCase()
      const detail = this.taskResult?.formatErrors?.[id]
      if (detail) return String(detail)
      return `当前任务未生成 ${id.toUpperCase()} 文件`
    },
    switchToPrimaryExportFormat() {
      if (!this.primaryExportFormat) return
      this.exportFormat = this.primaryExportFormat
      this.exportReady = false
    },
    goExportStep() {
      if (!this.availableExportFormats.length) {
        uni.showToast({ title: '本次任务没有可下载文件，请重新生成', icon: 'none' })
        return
      }
      if (!this.isExportAvailable(this.exportFormat)) this.switchToPrimaryExportFormat()
      this.currentStep = 8
      this.exportReady = false
    },
    returnToEditor() {
      const status = String(this.taskResult?.status || '')
      if (this.currentStep === 6 && !['completed', 'failed', 'cancelled', 'timed_out'].includes(status)) {
        uni.showToast({ title: '生成仍在进行，请等待完成或取消生成', icon: 'none' })
        return
      }
      this.currentStep = 5
      this.exportReady = false
      this.enterEditorPreview()
    },
    returnToLastSuccessfulResult() {
      if (!this.hasLastSuccessfulResult) return
      if (!this.editorDirty) {
        this.restoreLastSuccessfulResult()
        return
      }
      uni.showModal({
        title: '返回上次成品',
        content: '当前修改还没有重新生成，返回后将放弃这些修改，是否继续？',
        confirmText: '返回成品',
        cancelText: '继续编辑',
        success: result => {
          if (result?.confirm) this.restoreLastSuccessfulResult()
        }
      })
    },
    selectEditorSlide(index) {
      const nextIndex = Number(index)
      if (!Number.isInteger(nextIndex) || nextIndex < 0 || nextIndex >= this.slides.length) return
      this.activeSlideIndex = nextIndex
      this.refreshEditorPreview(nextIndex)
    },
    toggleActiveSlideLock() {
      const slide = this.activeSlide
      if (!slide) return
      slide.layoutLocked = !slide.layoutLocked
      this.markEditorDirty()
      this.persistDraft()
      uni.showToast({
        title: slide.layoutLocked ? '已锁定当前页面版式' : '已解除当前页面锁定',
        icon: 'none'
      })
    },
    onEditorContentInput() {
      this.markEditorDirty()
      this.applyManualTextOverride()
      const cacheKey = this.editorPreviewCacheKey()
      this.editorPreviewRevisions[cacheKey] = (this.editorPreviewRevisions[cacheKey] || 0) + 1
      delete this.editorPreviewCache[cacheKey]
      delete this.editorPreviewInFlight[cacheKey]
      this.editorPreviewRequestId += 1
      this.editorPreviewImage = ''
      this.editorPreviewSlideIndex = -1
      this.editorPreviewError = ''
      this.scheduleEditorPreview()
    },
    scheduleEditorPreview(immediate = false) {
      if (this.editorPreviewTimer) {
        clearTimeout(this.editorPreviewTimer)
        this.editorPreviewTimer = null
      }
      if (this.editorPreviewLoading) {
        this.editorPreviewQueued = true
        return
      }
      const requestId = ++this.editorPreviewRequestId
      const render = async () => {
        this.editorPreviewTimer = null
        const slide = this.activeSlide
        if (!slide || !slide.ui || typeof slide.ui !== 'object') {
          this.editorPreviewLoading = false
          this.editorPreviewError = '当前页面暂无可视化预览'
          return
        }
        const previewSlideIndex = this.activeSlideIndex
        this.editorPreviewLoading = true
        this.editorPreviewError = ''
        try {
          const image = await this.renderEditorPreviewSlide(previewSlideIndex)
          if (requestId !== this.editorPreviewRequestId) return
          if (!image) throw new Error('服务端未返回预览图')
          this.editorPreviewImage = image
          this.editorPreviewSlideIndex = previewSlideIndex
        } catch (error) {
          if (requestId !== this.editorPreviewRequestId) return
          this.editorPreviewError = this.errorMessage(error, '最终预览暂不可用')
        } finally {
          if (requestId === this.editorPreviewRequestId) {
            this.editorPreviewLoading = false
            if (this.editorPreviewQueued) {
              this.editorPreviewQueued = false
              this.scheduleEditorPreview(true)
            }
          }
        }
      }
      if (immediate) return render()
      this.editorPreviewLoading = true
      this.editorPreviewTimer = setTimeout(render, 500)
    },
    async prepareExport() {
      if (!this.taskId) {
        uni.showToast({ title: 'PPT 任务不存在，请重新生成', icon: 'none' })
        return
      }
      if (!this.isExportAvailable(this.exportFormat) && this.primaryExportFormat) this.switchToPrimaryExportFormat()
      const attachment = (this.taskResult?.attachments || []).find(item => String(item?.type || '').toLowerCase() === this.exportFormat)
      if (!attachment) {
        uni.showToast({ title: this.formatErrorMessage(this.exportFormat), icon: 'none' })
        return
      }
      this.exportPreparing = true
      try {
        const path = await downloadPptTaskFile(this.taskId, this.exportFormat)
        this.exportReady = true
        uni.openDocument({
          filePath: path,
          showMenu: true,
          fail: () => uni.showToast({ title: '文件已下载，请从下载列表打开', icon: 'none' })
        })
      } catch (error) {
        uni.showToast({ title: this.errorMessage(error, '文件下载失败'), icon: 'none' })
      } finally {
        this.exportPreparing = false
      }
    },
    applyManualTextOverride() {
      // Manual editing updates only existing text runs; the Presenton tree,
      // geometry, styles, SVGs and assets remain untouched. The preview and
      // final export both consume this same UI tree, so the lower fields are
      // the single editing source of truth.
      const slide = this.activeSlide
      const ui = slide?.ui
      if (!ui || typeof ui !== 'object') return

      const textNodes = []
      const visit = value => {
        if (Array.isArray(value)) {
          value.forEach(visit)
          return
        }
        if (!value || typeof value !== 'object') return
        if (value.type === 'text' && Array.isArray(value.runs)) textNodes.push(value)
        Object.keys(value).forEach(key => {
          if (key !== 'runs') visit(value[key])
        })
      }
      visit(ui)
      if (!textNodes.length) return

      const setText = (node, text) => {
        const next = String(text || '')
        node.text = next
        node.runs = node.runs.map((run, index) => ({
          ...(run && typeof run === 'object' ? run : {}),
          text: index === 0 ? next : ''
        }))
      }
      const titleNode = textNodes.find(node => {
        const name = String(node.name || '').toLowerCase()
        return /(headline|heading|title|main_title|main_heading|slide_headline|primary_heading)/.test(name)
          && !/(subtitle|section|item|card|feature|milestone|step|metric|label|number|page|footer|badge|caption)/.test(name)
      })
      if (titleNode) setText(titleNode, slide.title)

      const bodyNodes = textNodes.filter(node => {
        const name = String(node.name || '').toLowerCase()
        return /(body|paragraph|description|supporting|summary|copy|intro|detail|content)/.test(name)
          && !/(subtitle|footer|page|label|number|caption|badge|author|date)/.test(name)
      })
      const headingNodes = textNodes.filter(node => {
        const name = String(node.name || '').toLowerCase()
        return /(section_heading|card_title|milestone_title|feature_title|step_title|item_title)/.test(name)
          && !/(subtitle|footer|page|label|number|caption|badge)/.test(name)
      })
      const lines = String(slide.content || '')
        .split(/\r?\n/)
        .map(line => line.replace(/^\s*[-*•]\s*/, '').trim())
        .filter(Boolean)

      const contentChunks = (count) => {
        if (!count) return []
        if (count === 1) return [lines.join('\n')]
        const chunks = Array.from({ length: count }, () => [])
        lines.forEach((line, index) => chunks[Math.min(index, count - 1)].push(line))
        return chunks.map(chunk => chunk.join('\n'))
      }
      const compactLabel = value => {
        const text = String(value || '').trim()
        const delimiter = text.search(/[：:]/)
        const label = delimiter > 0 && delimiter <= 32 ? text.slice(0, delimiter).trim() : text
        return label.length > 28 ? `${label.slice(0, 28)}…` : label
      }

      if (bodyNodes.length) {
        contentChunks(bodyNodes.length).forEach((value, index) => setText(bodyNodes[index], value))
      }
      if (headingNodes.length) {
        lines.forEach((line, index) => {
          if (headingNodes[index]) setText(headingNodes[index], compactLabel(line))
        })
        headingNodes.slice(lines.length).forEach(node => setText(node, ''))
      } else if (!bodyNodes.length && textNodes.length) {
        // Older/fallback layouts may expose only generic text slots. Keep the
        // edit contract useful without touching structural labels.
        const fallbackNodes = textNodes.filter(node => node !== titleNode && !/(footer|page|label|number|caption|badge|author|date)/.test(String(node.name || '').toLowerCase()))
        contentChunks(fallbackNodes.length).forEach((value, index) => setText(fallbackNodes[index], value))
      }
    },
    persistActiveTask(kind, taskId) {
      if (!taskId) return
      try {
        uni.removeStorageSync('aiPptDraft')
        uni.setStorageSync('aiPptActiveTask', {
          savedAt: Date.now(),
          kind,
          taskId: String(taskId),
          status: String(this.taskResult?.status || this[`${kind}GenerationSnapshot`]?.status || ''),
          currentStep: this.currentStep,
          fileInfo: this.fileInfo,
          fileContent: this.sourceFileId ? '' : this.fileContent,
          sourceFileId: this.sourceFileId,
          manualSourceContent: this.manualSourceContent,
          outlineMode: this.outlineMode,
          outlineName: this.outlineName,
          outlineItems: this.outlineItems,
          outlineDocument: this.outlineDocument,
          pageCount: this.pageCount,
          pptStyle: this.pptStyle,
          settings: this.settings,
          contentLevel: this.contentLevel,
          sharedPrompt: this.sharedPrompt,
          slides: this.slides,
          contentQuality: this.contentQuality,
          activeSlideIndex: this.activeSlideIndex,
          slidesDirty: this.slidesDirty,
          outlineSourceDirty: this.outlineSourceDirty,
          editorDirty: this.editorDirty,
          lastSuccessfulResult: this.lastSuccessfulResult,
          templateThumbnailState: this.templateThumbnailState,
          templateEntryMode: this.templateEntryMode,
          templateCategory: this.templateCategory
        })
      } catch (error) {}
    },
    clearActiveTaskStorage() {
      try { uni.removeStorageSync('aiPptActiveTask') } catch (error) {}
    },
    hasDraftContent(snapshot = {}) {
      return Boolean(
        snapshot.fileInfo
        || String(snapshot.fileContent || '').trim()
        || String(snapshot.manualSourceContent || '').trim()
        || String(snapshot.sourceFileId || '').trim()
        || (Array.isArray(snapshot.outlineItems) && snapshot.outlineItems.some(item => String(item?.title || '').trim()))
        || (Array.isArray(snapshot.slides) && snapshot.slides.length)
      )
    },
    readSavedWork() {
      let activeTask = null
      let draft = null
      try {
        activeTask = uni.getStorageSync('aiPptActiveTask') || null
        draft = uni.getStorageSync('aiPptDraft') || null
      } catch (error) {}
      const terminal = ['completed', 'failed', 'cancelled', 'timed_out']
      if (activeTask?.taskId && terminal.includes(String(activeTask.status || ''))) activeTask = null
      return {
        activeTask: activeTask?.taskId ? activeTask : null,
        draft: this.hasDraftContent(draft) ? draft : null
      }
    },
    scheduleDraftPersistence() {
      if (this.restoringSavedWork) return
      if (this.draftPersistTimer) clearTimeout(this.draftPersistTimer)
      this.draftPersistTimer = setTimeout(() => {
        this.draftPersistTimer = null
        if (this.hasActivePptTask()) this.persistCurrentTaskSnapshot()
        else this.persistDraft()
      }, 180)
    },
    hasActivePptTask() {
      const terminal = ['completed', 'failed', 'cancelled', 'timed_out']
      const finalActive = this.taskId && !terminal.includes(String(this.taskResult?.status || ''))
      const outlineActive = this.outlineGenerationTaskId && !terminal.includes(String(this.outlineGenerationSnapshot?.status || ''))
      const slidesActive = this.slideGenerationTaskId && !terminal.includes(String(this.slideGenerationSnapshot?.status || ''))
      return Boolean(finalActive || outlineActive || slidesActive)
    },
    persistCurrentTaskSnapshot() {
      if (this.taskId) return this.persistActiveTask('final', this.taskId)
      if (this.slideGenerationTaskId) return this.persistActiveTask('slides', this.slideGenerationTaskId)
      if (this.outlineGenerationTaskId) return this.persistActiveTask('outline', this.outlineGenerationTaskId)
    },
    persistDraft() {
      if (this.hasActivePptTask()) return
      const snapshot = {
        savedAt: Date.now(),
        currentStep: this.currentStep,
        fileInfo: this.fileInfo,
        fileContent: this.sourceFileId ? '' : this.fileContent,
        sourceFileId: this.sourceFileId,
        manualSourceContent: this.manualSourceContent,
        outlineMode: this.outlineMode,
        outlineName: this.outlineName,
        outlineItems: this.outlineItems,
        outlineDocument: this.outlineDocument,
        pageCount: this.pageCount,
        pptStyle: this.pptStyle,
        contentLevel: this.contentLevel,
        settings: this.settings,
        sharedPrompt: this.sharedPrompt,
        slides: this.slides,
        activeSlideIndex: this.activeSlideIndex,
        slidesDirty: this.slidesDirty,
        outlineSourceDirty: this.outlineSourceDirty,
        taskId: this.taskId,
        taskResult: this.taskResult,
        generationWarnings: this.generationWarnings,
        contentQuality: this.contentQuality,
        editorDirty: this.editorDirty,
        lastSuccessfulResult: this.lastSuccessfulResult,
        templateThumbnailState: this.templateThumbnailState,
        templateEntryMode: this.templateEntryMode,
        templateCategory: this.templateCategory
      }
      try {
        if (this.hasDraftContent(snapshot)) uni.setStorageSync('aiPptDraft', snapshot)
        else uni.removeStorageSync('aiPptDraft')
      } catch (error) {}
    },
    restoreDraft(snapshot) {
      if (!snapshot) return
      this.restoringSavedWork = true
      this.fileInfo = snapshot.fileInfo || null
      this.fileContent = String(snapshot.fileContent || '')
      this.sourceFileId = String(snapshot.sourceFileId || '')
      this.manualSourceContent = String(snapshot.manualSourceContent || '')
      this.outlineMode = snapshot.outlineMode || this.outlineMode
      this.outlineName = snapshot.outlineName || ''
      this.outlineItems = this.normalizeOutlineItems(
        Array.isArray(snapshot.outlineItems) ? snapshot.outlineItems : []
      )
      this.outlineDocument = snapshot.outlineDocument
        ? { ...snapshot.outlineDocument, items: this.outlineItems }
        : null
      // 兼容升级前缓存：旧版本把默认大纲上限持久化为 15；没有已生成页面时，
      // 这只是旧默认值，不应继续把新的大纲任务锁回 15 页。
      const draftPageCount = Number(snapshot.pageCount || 0)
      this.pageCount = draftPageCount === 15 && !snapshot.slides?.length
        ? DEFAULT_PPT_PAGE_COUNT
        : (draftPageCount || this.pageCount)
      this.pptStyle = snapshot.pptStyle || this.pptStyle
      this.contentLevel = snapshot.contentLevel || this.contentLevel
      this.settings = { ...this.settings, ...(snapshot.settings || {}) }
      this.sharedPrompt = snapshot.sharedPrompt || this.sharedPrompt
      this.slides = this.normalizeEditorSlides(snapshot.slides)
      this.activeSlideIndex = Math.min(Math.max(0, Number(snapshot.activeSlideIndex || 0)), Math.max(0, this.slides.length - 1))
      this.slidesDirty = Boolean(snapshot.slidesDirty)
      this.outlineSourceDirty = Boolean(snapshot.outlineSourceDirty)
      this.taskId = String(snapshot.taskId || '')
      this.taskResult = snapshot.taskResult || null
      this.generationWarnings = Array.isArray(snapshot.generationWarnings) ? snapshot.generationWarnings : []
      this.contentQuality = snapshot.contentQuality && typeof snapshot.contentQuality === 'object'
        ? this.clonePptValue(snapshot.contentQuality)
        : null
      this.editorDirty = Boolean(snapshot.editorDirty)
      this.lastSuccessfulResult = snapshot.lastSuccessfulResult || null
      this.templateThumbnailState = snapshot.templateThumbnailState && typeof snapshot.templateThumbnailState === 'object'
        ? { ...snapshot.templateThumbnailState }
        : {}
      if (snapshot.templateEntryMode) this.templateEntryMode = snapshot.templateEntryMode
      if (snapshot.templateCategory) this.templateCategory = snapshot.templateCategory
      const savedStep = Number(snapshot.currentStep)
      if (Number.isInteger(savedStep) && savedStep >= 1 && savedStep <= this.stepMeta.length) this.currentStep = savedStep
      this.restoringSavedWork = false
    },
    async promptSavedWork() {
      const { activeTask, draft } = this.readSavedWork()
      if (!activeTask && !draft) return
      const savedAt = Number(activeTask?.savedAt || draft?.savedAt || 0)
      const timeHint = savedAt ? `（保存于 ${new Date(savedAt).toLocaleString('zh-CN', { hour12: false })}）` : ''
      const content = activeTask
        ? `检测到上次有未完成的 PPT 任务${timeHint}。是否继续上次任务？`
        : `检测到上次填写的资料和设置${timeHint}。是否继续上次编辑？`
      const result = await new Promise(resolve => {
        uni.showModal({
          title: '发现未完成内容',
          content,
          confirmText: '继续',
          cancelText: '重新输入',
          success: resolve,
          fail: () => resolve({ confirm: false })
        })
      })
      if (result?.confirm) {
        if (activeTask) await this.restoreActiveTask(activeTask)
        else this.restoreDraft(draft)
      } else {
        await this.resetFlowState(true)
      }
    },
    requestRestartFlow() {
      const step = this.currentStep
      const messages = {
        1: {
          title: '确认重新生成',
          content: '将清空当前资料、本次大纲和页面内容，返回输入页面。历史记录不会删除，是否继续？',
          confirmText: '清空并重来'
        },
        2: {
          title: '确认重新生成',
          content: '将清空当前资料、本次大纲和页面内容，返回输入页面。历史记录不会删除，是否继续？',
          confirmText: '清空并重来'
        },
        3: {
          title: '确认重新生成大纲',
          content: '将根据当前资料重新生成一份大纲，当前未保存的大纲修改会被覆盖。是否继续？',
          confirmText: '重新生成大纲'
        },
        4: {
          title: '确认重新生成页面',
          content: '将根据当前大纲和设置重新生成页面内容，当前页面内容会被覆盖。是否继续？',
          confirmText: '重新生成页面'
        },
        5: {
          title: '确认重新生成页面',
          content: '将根据当前大纲和设置重新生成全部页面，当前逐页编辑内容会被覆盖。是否继续？',
          confirmText: '覆盖并重新生成'
        },
        6: {
          title: '确认重新生成',
          content: '当前 PPT 正在生成，继续操作会取消当前任务并重新开始。是否继续？',
          confirmText: '取消并重新生成'
        },
        7: {
          title: '确认重新生成',
          content: '将返回设置页重新生成页面内容，当前成品会保留在历史记录中。是否继续？',
          confirmText: '返回设置'
        },
        8: {
          title: '确认重新生成',
          content: '将返回设置页重新生成页面内容，当前成品会保留在历史记录中。是否继续？',
          confirmText: '返回设置'
        }
      }
      const message = messages[step] || messages[1]
      uni.showModal({
        title: message.title,
        content: message.content,
        confirmText: message.confirmText,
        cancelText: '取消',
        success: result => {
          if (result?.confirm) this.executeRestartFlow(step)
        }
      })
    },
    async executeRestartFlow(step) {
      if (step === 1 || step === 2) {
        await this.resetFlowState()
        return
      }
      if (step === 3) {
        if (this.hasActivePptTask()) await this.cancelGeneration({ silent: true })
        await this.prepareOutline()
        return
      }
      if (step === 4) {
        if (this.hasActivePptTask()) await this.cancelGeneration({ silent: true })
        await this.prepareSlides()
        return
      }
      if (step === 5) {
        this.copyEditorPromptsToOutline()
        this.markEditorDirty()
        this.slidesDirty = true
        this.progress = 0
        this.exportReady = false
        await this.prepareSlides()
        return
      }
      if (step === 6) {
        if (this.hasActivePptTask()) await this.cancelGeneration({ silent: true })
        if (this.slides.length >= 2) await this.runGeneration()
        else this.returnToEditor()
        return
      }
      if (step === 7 || step === 8) {
        this.goToSettingsForRegeneration()
        return
      }
      await this.resetFlowState()
    },
    async resetFlowState(silent = false) {
      const savedActiveTask = this.readSavedWork().activeTask
      if (savedActiveTask?.taskId && !this.hasActivePptTask()) {
        try { await cancelPptTask(String(savedActiveTask.taskId)) } catch (error) {}
      }
      if (this.hasActivePptTask()) await this.cancelGeneration({ silent: true })
      this.clearTimers()
      this.generationRunId += 1
      this.outlineGenerationRunId += 1
      this.slideGenerationRunId += 1
      this.restoringSavedWork = true
      this.clearTaskContext(false)
      this.currentStep = this.templateFirstEnabled ? this.templateStepIndex : this.uploadStepIndex
      this.templateEntryMode = this.templateFirstEnabled ? 'library' : 'upload'
      this.fileInfo = null
      this.fileContent = ''
      this.manualSourceContent = ''
      this.sourceFileId = ''
      this.previewExpanded = false
      this.outlineMode = 'ai_outline'
      this.outlineName = ''
      this.outlineItems = []
      this.outlineDocument = null
      this.outlineSourceDirty = false
      this.layoutMarkdown = ''
      this.outlineSavedAt = ''
      this.contentLevel = 'standard'
      this.slides = []
      this.activeSlideIndex = 0
      this.sharedPrompt = '保持资料内容准确，版面简洁清晰，突出核心知识点，使用清晰易读的视觉层级。'
      this.settings = {
        includeCover: true,
        includeCatalog: true,
        includeSection: true,
        includeSummary: true,
        includeVisuals: true,
        imageMode: 'placeholder'
      }
      this.progress = 0
      this.taskId = ''
      this.taskResult = null
      this.slideGenerationTaskId = ''
      this.outlineGenerationTaskId = ''
      this.slideGenerationSnapshot = null
      this.outlineGenerationSnapshot = null
      this.previewImages = {}
      this.generationWarnings = []
      this.lastPptError = ''
      this.modelConfigError = false
      this.exportReady = false
      this.apiBusy = false
      this.restoringSavedWork = false
      try {
        uni.removeStorageSync('aiPptActiveTask')
        uni.removeStorageSync('aiPptDraft')
      } catch (error) {}
      if (!silent) uni.showToast({ title: '已清空，可重新输入', icon: 'none' })
    },
    async restoreActiveTask(savedState = null) {
      let saved
      try {
        saved = savedState || uni.getStorageSync('aiPptActiveTask')
      } catch (error) {
        return
      }
      if (!saved?.taskId) return
      this.fileInfo = saved.fileInfo || this.fileInfo
      this.fileContent = String(saved.fileContent || '')
      this.sourceFileId = String(saved.sourceFileId || '')
      this.manualSourceContent = String(saved.manualSourceContent || '')
      this.outlineMode = saved.outlineMode || this.outlineMode
      this.outlineName = saved.outlineName || this.outlineName
      this.outlineItems = this.normalizeOutlineItems(
        Array.isArray(saved.outlineItems) ? saved.outlineItems : this.outlineItems
      )
      this.outlineDocument = saved.outlineDocument
        ? { ...saved.outlineDocument, items: this.outlineItems }
        : this.outlineDocument
      const savedPageCount = Number(saved.pageCount || 0)
      this.pageCount = saved.kind === 'outline' && savedPageCount === 15
        ? DEFAULT_PPT_PAGE_COUNT
        : (savedPageCount || this.pageCount)
      this.pptStyle = saved.pptStyle || this.pptStyle
      this.templateThumbnailState = saved.templateThumbnailState && typeof saved.templateThumbnailState === 'object'
        ? { ...saved.templateThumbnailState }
        : {}
      if (saved.templateCategory) this.templateCategory = saved.templateCategory
      this.settings = { ...this.settings, ...(saved.settings || {}) }
      this.sharedPrompt = saved.sharedPrompt || this.sharedPrompt
      this.slides = Array.isArray(saved.slides) ? this.normalizeEditorSlides(saved.slides) : this.slides
      this.activeSlideIndex = Math.min(Math.max(0, Number(saved.activeSlideIndex || 0)), Math.max(0, this.slides.length - 1))
      this.slidesDirty = Boolean(saved.slidesDirty)
      this.outlineSourceDirty = Boolean(saved.outlineSourceDirty)
      this.editorDirty = Boolean(saved.editorDirty)
      this.lastSuccessfulResult = saved.lastSuccessfulResult || this.lastSuccessfulResult
      if (saved.templateEntryMode) this.templateEntryMode = saved.templateEntryMode
      const savedStep = Number(saved.currentStep)
      const hasSavedStep = Number.isInteger(savedStep) && savedStep >= 1 && savedStep <= this.stepMeta.length
      try {
        const task = this.responseData(await getPptTask(String(saved.taskId)))
        // 旧版本缓存可能把 pptStyle 留成 general，但任务结果记录了实际使用的模板。
        // 以服务端任务结果为准，避免历史缓存把模板再次覆盖成默认值。
        const taskTemplateId = String(task?.templateId || task?.settings?.templateId || '').trim()
        if (taskTemplateId) this.pptStyle = taskTemplateId
        const status = String(task?.status || '')
        if (saved.kind === 'outline') {
          this.outlineGenerationTaskId = String(saved.taskId)
          this.outlineGenerationSnapshot = task
          if (status === 'completed') {
            const extracted = this.extractOutlineItems(task)
            this.outlineItems = this.normalizeOutlineItems(extracted.items)
            this.outlineDocument = { ...extracted.outline, items: this.outlineItems }
            this.currentStep = hasSavedStep ? savedStep : 3
            this.clearActiveTaskStorage()
          } else if (!['failed', 'cancelled', 'timed_out'].includes(status)) {
            this.currentStep = 3
            const runId = ++this.outlineGenerationRunId
            this.apiBusy = true
            await this.followOutlineGenerationTask(runId)
          }
          return
        }
        if (saved.kind === 'slides') {
          this.slideGenerationTaskId = String(saved.taskId)
          this.slideGenerationSnapshot = task
          const restoredSlides = Array.isArray(task.slides) && task.slides.length ? task.slides : saved.slides
          if (status === 'completed' && Array.isArray(restoredSlides) && restoredSlides.length >= 2) {
            this.slides = this.normalizeEditorSlides(restoredSlides)
            this.pageCount = this.slides.length
            this.activeSlideIndex = Math.min(this.activeSlideIndex, Math.max(0, this.slides.length - 1))
            this.resetEditorPreviewSession()
            this.currentStep = hasSavedStep ? savedStep : 5
            if (this.currentStep === 5) {
              this.enterEditorPreview()
            }
            this.generationWarnings = Array.isArray(task.warnings) ? task.warnings : []
            this.contentQuality = task.contentQuality && typeof task.contentQuality === 'object'
              ? this.clonePptValue(task.contentQuality)
              : null
            this.clearActiveTaskStorage()
          } else if (!['failed', 'cancelled', 'timed_out'].includes(status)) {
            this.currentStep = 4
            const runId = ++this.slideGenerationRunId
            this.apiBusy = true
            await this.followSlideGenerationTask(runId)
          }
          return
        }
        this.taskId = String(saved.taskId)
        this.taskResult = task
        this.generationWarnings = Array.isArray(task.warnings) ? task.warnings : []
        this.contentQuality = task.contentQuality && typeof task.contentQuality === 'object'
          ? this.clonePptValue(task.contentQuality)
          : null
        this.progress = Number(task?.progress || 0)
        if (status === 'completed') {
          if (Array.isArray(task.slides) && task.slides.length) this.slides = this.normalizeEditorSlides(task.slides)
          this.progress = 100
          this.currentStep = hasSavedStep ? savedStep : 7
          this.saveSuccessfulResult()
          this.completedTaskFingerprint = JSON.stringify(this.buildTaskPayload())
          this.clearActiveTaskStorage()
          this.loadPreviewImages()
        } else if (!['failed', 'cancelled', 'timed_out'].includes(status)) {
          this.currentStep = 6
          const runId = ++this.generationRunId
          await this.followGenerationTask(runId)
        } else {
          this.currentStep = 5
        }
      } catch (error) {
        this.apiBusy = false
        this.lastPptError = this.errorMessage(error, '暂时无法读取上次任务，已保留本地内容，请稍后重试')
        uni.showToast({ title: '暂时无法读取上次任务，缓存仍已保留', icon: 'none' })
      }
    },
    openHistory(tab) {
      this.historyTab = tab
      this.historyOpen = true
    },
    recordGenerationHistory() {
      const item = {
        id: `generation-history-${Date.now()}`,
        name: this.resultName,
        createdAt: this.formatNow(),
        pageCount: this.pageCount,
        pptStyle: this.pptStyle,
        contentLevel: this.contentLevel,
        outlineMode: this.outlineMode,
        fileInfo: this.fileInfo,
        sourceContent: this.sourceFileId ? '' : this.fileContent,
        outlineName: this.outlineName,
        outlineItems: this.validOutlineItems.map(item => ({ ...item })),
        outlineDocument: this.outlineDocument,
        settings: { ...this.settings },
        sharedPrompt: this.sharedPrompt,
        taskId: this.taskId,
        lastSuccessfulResult: this.lastSuccessfulResult
      }
      this.generationHistory = [item, ...this.generationHistory].slice(0, 30)
      this.persistHistories()
    },
    reuseHistory(item) {
      this.clearTimers()
      this.clearTaskContext()
      if (this.historyTab === 'outline') {
        this.outlineName = item.name
        this.outlineMode = item.source
        this.outlineItems = this.normalizeOutlineItems(
          (item.items || []).map(entry => ({ ...entry, id: this.createOutlineItem().id }))
        )
        this.currentStep = 3
      } else {
        this.pageCount = Number(item.pageCount || DEFAULT_PPT_PAGE_COUNT)
        this.fileInfo = item.fileInfo || this.fileInfo
        this.fileContent = String(item.sourceContent || '')
        this.sourceFileId = ''
        this.manualSourceContent = this.fileContent
        this.outlineName = item.outlineName || this.outlineName
        this.outlineItems = Array.isArray(item.outlineItems)
          ? this.normalizeOutlineItems(item.outlineItems.map(entry => ({ ...entry, id: this.createOutlineItem().id })))
          : this.outlineItems
        this.outlineDocument = item.outlineDocument
          ? { ...item.outlineDocument, items: this.outlineItems }
          : this.outlineDocument
        this.pptStyle = item.pptStyle || 'general'
        this.contentLevel = item.contentLevel || 'standard'
        this.outlineMode = item.outlineMode || this.outlineMode
        this.settings = { ...this.settings, ...(item.settings || {}) }
        this.sharedPrompt = item.sharedPrompt || this.sharedPrompt
        this.lastSuccessfulResult = item.lastSuccessfulResult || null
        if (this.hasLastSuccessfulResult && this.restoreLastSuccessfulResult()) {
          this.currentStep = 7
        } else if (!this.fileInfo || (!this.fileContent && !this.sourceFileId)) {
          this.currentStep = this.uploadStepIndex
          uni.showToast({ title: '历史记录未保留原始文件，请重新上传资料', icon: 'none' })
        } else {
          this.currentStep = this.validOutlineItems.length >= 2 ? 4 : 3
        }
      }
      this.historyOpen = false
    },
    deleteHistory(id) {
      if (this.historyTab === 'outline') this.outlineHistory = this.outlineHistory.filter(item => item.id !== id)
      else this.generationHistory = this.generationHistory.filter(item => item.id !== id)
      this.persistHistories()
    },
    styleName(id) {
      return this.pptStyles.find(item => item.id === id)?.name || 'PPT 模板'
    },
    restoreHistories() {
      try {
        const stored = uni.getStorageSync('aiPptFrontendHistory') || {}
        this.generationHistory = Array.isArray(stored.generation) ? stored.generation : []
        this.outlineHistory = Array.isArray(stored.outlines) ? stored.outlines : []
      } catch (error) {
        this.generationHistory = []
        this.outlineHistory = []
      }
    },
    persistHistories() {
      try {
        uni.setStorageSync('aiPptFrontendHistory', {
          generation: this.generationHistory,
          outlines: this.outlineHistory
        })
      } catch (error) {}
    },
    buildSettings() {
      return {
        ...this.settings,
        pageCount: this.pageCount,
        pptStyle: this.pptStyle,
        templateId: this.pptStyle,
        contentLevel: this.contentLevel
      }
    },
    editorPreviewCacheKey(index = this.activeSlideIndex) {
      const slide = this.slides[index]
      return `${this.pptStyle}:${slide?.id || index}`
    },
    responseData(response) {
      let value = response || {}
      for (let depth = 0; depth < 4; depth += 1) {
        if (!value || typeof value !== 'object' || Array.isArray(value)) break
        if (!Object.prototype.hasOwnProperty.call(value, 'data') || value.data == null || value.data === value) break
        value = value.data
      }
      return value || {}
    },
    errorMessage(error, fallback) {
      return String(error?.msg || error?.message || error?.data?.msg || fallback || '操作失败').slice(0, 80)
    },
    isModelConfigError(error) {
      const message = this.errorMessage(error, '')
      return /模型|API key|Api Key|apikey|api_key|provider|配置|测试成功/.test(message)
    },
    handlePptError(error, fallback) {
      const message = this.errorMessage(error, fallback)
      this.lastPptError = message
      if (this.isModelConfigError(error)) {
        this.modelConfigError = true
      }
      uni.showToast({ title: message, icon: 'none' })
    },
    openModelHelp() {
      uni.showModal({
        title: '模型配置说明',
        content: 'PPT 生成复用 AI 创作中已测试成功的模型配置。请先在后端模型配置中完成 API key 测试，再回到本页重新生成。',
        showCancel: false,
        confirmText: '知道了'
      })
    },
    startOperationFeedback() {
      this.stopOperationFeedback()
      const phases = [
        { until: 12, message: '正在读取学习资料', detail: '准备文本与生成参数' },
        { until: 30, message: '正在拆解文本结构', detail: '识别主题、章节和核心知识点' },
        { until: 54, message: '正在组织复习大纲', detail: '重新整理适合 PPT 的知识结构' },
        { until: 76, message: 'AI 正在生成页面计划', detail: '为每一页安排标题、要点和讲解重点' },
        { until: 91, message: '正在检查大纲完整性', detail: '检查页数、内容覆盖和页面层级' },
        { until: 92, message: '正在等待大纲生成结果', detail: '资料较长时会在此阶段等待模型完成' }
      ]
      let simulatedProgress = 3
      const applySimulatedProgress = () => {
        const phase = phases.find(item => simulatedProgress <= item.until) || phases[phases.length - 1]
        this.operationFeedback = {
          active: true,
          progress: simulatedProgress,
          message: phase.message,
          detail: phase.detail
        }
      }
      applySimulatedProgress()
      this.operationFeedbackTimer = setInterval(() => {
        if (simulatedProgress >= 92) return
        const phase = phases.find(item => simulatedProgress <= item.until) || phases[phases.length - 1]
        const remaining = phase.until - simulatedProgress
        const step = remaining > 10 ? 2 : 1
        simulatedProgress = Math.min(92, simulatedProgress + step)
        applySimulatedProgress()
      }, 900)
    },
    updateOperationFeedback(progress, message, detail) {
      if (this.operationFeedbackTimer) clearInterval(this.operationFeedbackTimer)
      this.operationFeedbackTimer = null
      const numericProgress = Number(progress)
      this.operationFeedback = {
        active: true,
        progress: Number.isFinite(numericProgress) ? Math.max(0, Math.min(100, numericProgress)) : 0,
        message: String(message || '正在处理'),
        detail: String(detail || '')
      }
    },
    stopOperationFeedback() {
      if (this.operationFeedbackTimer) clearInterval(this.operationFeedbackTimer)
      this.operationFeedbackTimer = null
      this.operationFeedback = { active: false, progress: 0, message: '', detail: '' }
    },
    formatNow() {
      const date = new Date()
      const pad = value => String(value).padStart(2, '0')
      return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
    },
    formatTime() {
      const date = new Date()
      return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
    },
    clearTimers() {
      this.stopOperationFeedback()
      if (this.draftPersistTimer) {
        clearTimeout(this.draftPersistTimer)
        this.draftPersistTimer = null
      }
      if (this.generationStream?.abort) this.generationStream.abort('ppt_generation_cancelled')
      this.generationStream = null
      if (this.slideGenerationStream?.abort) this.slideGenerationStream.abort('ppt_slide_generation_cancelled')
      this.slideGenerationStream = null
      if (this.outlineGenerationStream?.abort) this.outlineGenerationStream.abort('ppt_outline_generation_cancelled')
      this.outlineGenerationStream = null
      if (this.generationTimer) {
        clearInterval(this.generationTimer)
        clearTimeout(this.generationTimer)
        this.generationTimer = null
      }
      if (this.exportTimer) {
        clearTimeout(this.exportTimer)
        this.exportTimer = null
      }
      if (this.editorPreviewTimer) {
        clearTimeout(this.editorPreviewTimer)
        this.editorPreviewTimer = null
      }
      this.editorPreviewRequestId += 1
      this.editorPreviewSession += 1
      this.editorPreviewBatchRunId += 1
      this.editorPreviewLoading = false
      this.editorPreviewQueued = false
    }
  }
}
</script>

<style scoped>
.ppt-flow{color:#182033}.flow-heading{padding:32rpx 4rpx 22rpx}.flow-heading__eyebrow,.flow-heading__title,.flow-heading__desc{display:block}.flow-heading__eyebrow{color:#5265f5;font-size:22rpx;font-weight:700;letter-spacing:1rpx}.flow-heading__title{margin-top:8rpx;font-size:40rpx;font-weight:800;line-height:1.25}.flow-heading__desc{margin-top:10rpx;color:#7b8498;font-size:24rpx;line-height:1.55}.step-scroll{width:100%;margin-bottom:22rpx;white-space:nowrap}.stepper{display:inline-flex;min-width:100%;padding:5rpx 2rpx 10rpx;box-sizing:border-box}.stepper__item{position:relative;display:flex;flex:1;min-width:114rpx;flex-direction:column;align-items:center;color:#a1a9b8}.stepper__item:not(:last-child)::after{position:absolute;top:23rpx;left:calc(50% + 26rpx);right:calc(-50% + 26rpx);height:2rpx;background:#dfe4ec;content:''}.stepper__item--done:not(:last-child)::after{background:#5265f5}.stepper__number{position:relative;z-index:1;display:flex;width:46rpx;height:46rpx;align-items:center;justify-content:center;border:2rpx solid #d9dee8;border-radius:50%;background:#f5f7fb;font-size:22rpx;font-weight:700}.stepper__item--active,.stepper__item--done{color:#4a5ae8}.stepper__item--active .stepper__number,.stepper__item--done .stepper__number{border-color:#5265f5;background:#5265f5;color:#fff;box-shadow:0 7rpx 16rpx rgba(82,101,245,.2)}.stepper__label{margin-top:10rpx;font-size:20rpx}.stepper__item--active .stepper__label{font-weight:700}.stepper__check{font-size:20rpx}.panel{padding:28rpx;border:1px solid #edf0f5;border-radius:20rpx;background:#fff;box-shadow:0 6rpx 24rpx rgba(30,50,90,.04)}.field+.field{margin-top:28rpx}.field__label,.settings-section__title{display:block;margin-bottom:15rpx;font-size:27rpx;font-weight:700}.select-field{display:flex;align-items:center;justify-content:space-between;padding:21rpx 22rpx;border:1px solid #dce2ec;border-radius:16rpx;background:#fff}.select-field__value,.select-field__hint{display:block}.select-field__value{font-size:26rpx;font-weight:600}.select-field__hint{margin-top:6rpx;color:#929bad;font-size:20rpx}.select-field__arrow{color:#697386;font-size:30rpx}.upload-box{display:flex;min-height:236rpx;align-items:center;justify-content:center;flex-direction:column;border:2rpx dashed #cfd7e6;border-radius:16rpx;background:#fafbfe}.file-icon{position:relative;display:flex;width:66rpx;height:78rpx;align-items:flex-end;justify-content:center;padding-bottom:10rpx;border-radius:8rpx;background:#5265f5;box-sizing:border-box;box-shadow:0 10rpx 18rpx rgba(82,101,245,.18)}.file-icon::after{position:absolute;right:0;top:0;border-top:18rpx solid #fff;border-left:18rpx solid transparent;content:''}.file-icon text{color:#fff;font-size:18rpx;font-weight:700}.upload-box__title{margin-top:18rpx;font-size:27rpx;font-weight:700}.upload-box__hint{margin-top:8rpx;color:#98a1b2;font-size:21rpx}.file-row{display:flex;align-items:center;gap:18rpx;padding:20rpx;border:1px solid #dfe4ec;border-radius:16rpx}.file-row__icon{display:flex;width:60rpx;height:66rpx;align-items:center;justify-content:center;border-radius:12rpx;background:#5265f5;color:#fff;font-size:17rpx;font-weight:800}.file-row__main{min-width:0;flex:1}.file-row__name,.file-row__meta{display:block}.file-row__name{overflow:hidden;font-size:25rpx;font-weight:700;text-overflow:ellipsis;white-space:nowrap}.file-row__meta{display:flex;gap:16rpx;margin-top:9rpx;color:#8992a4;font-size:20rpx}.file-row__success{color:#20a966}.file-row__success::before{content:'✓ ';font-weight:700}.file-row__actions{display:flex;gap:17rpx;color:#5265f5;font-size:21rpx}.file-row__delete{color:#929bad}.preview-card{margin-top:26rpx;padding:22rpx;border-radius:16rpx;background:#f7f8fc}.preview-card__head{display:flex;align-items:center;justify-content:space-between}.preview-card__title{font-size:26rpx;font-weight:700}.preview-card__count{color:#7c8699;font-size:20rpx}.preview-card__content{display:-webkit-box;margin-top:17rpx;overflow:hidden;color:#414a5b;font-size:22rpx;line-height:1.75;white-space:pre-wrap;-webkit-box-orient:vertical;-webkit-line-clamp:8}.preview-card__more{display:block;margin-top:12rpx;color:#5265f5;font-size:20rpx}.single-action{margin-top:28rpx}.bottom-actions{display:flex;gap:20rpx;margin-top:30rpx}.primary-button,.secondary-button{height:84rpx;margin:0;border-radius:16rpx;font-size:27rpx;line-height:84rpx}.primary-button{flex:1;border:0;background:#5265f5;color:#fff;font-weight:700;box-shadow:0 10rpx 20rpx rgba(82,101,245,.15)}.primary-button[disabled]{opacity:.45}.secondary-button{flex:1;border:1px solid #d8deea;background:#fff;color:#5265f5}.secondary-button::after,.primary-button::after{border:0}.choice-card{position:relative;display:flex;align-items:center;border:2rpx solid #e1e5ed;border-radius:20rpx;background:#fff}.choice-card+.choice-card{margin-top:22rpx}.choice-card--large{min-height:192rpx;padding:25rpx 52rpx 25rpx 22rpx}.choice-card--selected{border-color:#5265f5;background:#f7f8ff;box-shadow:0 10rpx 25rpx rgba(82,101,245,.08)}.choice-card__icon{display:flex;width:78rpx;height:92rpx;flex:none;align-items:center;justify-content:center;border:2rpx solid #5265f5;border-radius:12rpx;color:#5265f5}.choice-card__icon--original_outline{border-color:#9aa4b6;color:#7e899d}.line-icon{display:flex;width:42rpx;gap:9rpx;flex-direction:column}.line-icon text{height:5rpx;border-radius:99rpx;background:currentColor}.line-icon text:nth-child(2){width:75%}.line-icon text:nth-child(3){width:88%}.choice-card__body{min-width:0;margin-left:22rpx}.choice-card__title,.choice-card__desc,.choice-card__fit{display:block}.choice-card__title{font-size:29rpx;font-weight:700}.choice-card__desc{margin-top:9rpx;color:#667086;font-size:22rpx;line-height:1.55}.choice-card__fit{margin-top:9rpx;color:#8b94a6;font-size:19rpx}.radio-dot{position:absolute;right:18rpx;top:18rpx;display:flex;width:30rpx;height:30rpx;align-items:center;justify-content:center;border:2rpx solid #bcc4d2;border-radius:50%;box-sizing:border-box}.radio-dot--selected{border-color:#5265f5;background:#5265f5;color:#fff;font-size:18rpx}.scene-summary{display:flex;align-items:center;gap:22rpx;padding:20rpx 22rpx;border:1px solid #dce2fb;border-radius:16rpx;background:#f6f7ff}.scene-summary__label{color:#7b8497;font-size:21rpx}.scene-summary__value{color:#5265f5;font-size:24rpx;font-weight:700}.settings-section{margin-top:29rpx;padding-top:2rpx}.settings-section+.settings-section{padding-top:28rpx;border-top:1px solid #eef0f4}.settings-section__title{margin:0}.settings-section__title--block{margin-bottom:17rpx}.style-scroll{width:100%;white-space:nowrap}.style-list{display:inline-flex;gap:16rpx;padding:2rpx}.style-card{position:relative;width:226rpx;padding:10rpx;border:2rpx solid #e1e5ed;border-radius:16rpx;box-sizing:border-box}.style-card--selected{border-color:#5265f5;background:#fafaff}.style-card__preview{position:relative;height:118rpx;overflow:hidden;padding:20rpx;border-radius:12rpx;background:#f7f9ff;box-sizing:border-box}.style-card__preview--campus{background:#fff5df}.style-card__preview--focus{background:#151c30}.mini-slide__title{width:60%;height:9rpx;border-radius:99rpx;background:#5265f5}.mini-slide__line{width:52%;height:5rpx;margin-top:10rpx;border-radius:99rpx;background:#b7c0d7}.mini-slide__line--long{width:72%;margin-top:16rpx}.mini-slide__shape{position:absolute;right:-16rpx;bottom:-23rpx;width:90rpx;height:90rpx;border-radius:28rpx;background:rgba(82,101,245,.2);transform:rotate(20deg)}.style-card__preview--campus .mini-slide__title{background:#f59f42}.style-card__preview--campus .mini-slide__shape{background:#dcefdc}.style-card__preview--focus .mini-slide__title{background:#fff}.style-card__preview--focus .mini-slide__line{background:#66728e}.style-card__preview--focus .mini-slide__shape{background:#214d61}.style-card__name,.style-card__desc{display:block}.style-card__name{margin-top:12rpx;font-size:23rpx;font-weight:700}.style-card__desc{margin-top:4rpx;color:#8a93a4;font-size:18rpx}.style-card__check{position:absolute;right:16rpx;top:16rpx;display:flex;width:28rpx;height:28rpx;align-items:center;justify-content:center;border-radius:50%;background:#5265f5;color:#fff;font-size:17rpx}.segmented{display:flex;gap:6rpx;padding:6rpx;border-radius:12rpx;background:#f2f4f8}.segmented__item{display:flex;height:64rpx;flex:1;align-items:center;justify-content:center;border-radius:8rpx;color:#727c8f;font-size:23rpx;transition:background-color .2s,color .2s}.segmented__item--active{background:#fff;color:#4a5ae8;font-weight:700;box-shadow:0 2rpx 8rpx rgba(30,50,90,.08)}.switch-row{display:flex;min-height:68rpx;align-items:center;justify-content:space-between;color:#343d4f;font-size:23rpx}.switch-row switch{transform:scale(.76);transform-origin:right center}.switch-row--visuals{margin-top:12rpx;padding-top:18rpx;border-top:1px solid #eef0f4}.switch-row__title,.switch-row__desc{display:block}.switch-row__desc{margin-top:5rpx;color:#939bad;font-size:18rpx}.progress-panel{padding-top:36rpx}.progress-hero{display:flex;align-items:center;flex-direction:column;text-align:center}.progress-ring{display:flex;width:150rpx;height:150rpx;align-items:center;justify-content:center;border-radius:50%;background:conic-gradient(#5265f5 var(--progress),#e7eaf2 0);box-shadow:0 10rpx 28rpx rgba(82,101,245,.12)}.progress-ring__inner{display:flex;width:122rpx;height:122rpx;align-items:baseline;justify-content:center;border-radius:50%;background:#fff}.progress-ring__inner text:first-child{align-self:center;font-size:38rpx;font-weight:800}.progress-ring__inner text:last-child{align-self:center;color:#748096;font-size:20rpx}.progress-hero__stage{margin-top:22rpx;font-size:29rpx;font-weight:700}.progress-hero__message{margin-top:8rpx;color:#7b8598;font-size:21rpx}.generation-list{margin-top:34rpx}.generation-item{display:flex;min-height:95rpx}.generation-item__rail{position:relative;width:55rpx;flex:none}.generation-item__dot{position:relative;z-index:1;display:flex;width:39rpx;height:39rpx;align-items:center;justify-content:center;border-radius:50%;box-sizing:border-box;font-size:19rpx;font-weight:700}.generation-item__dot--done{background:#2fbd76;color:#fff}.generation-item__dot--active{background:#5265f5;color:#fff;box-shadow:0 0 0 7rpx #eef0ff}.generation-item__dot--waiting{background:#e3e7ee;color:#9aa3b4}.generation-item__line{position:absolute;left:19rpx;top:39rpx;width:2rpx;height:58rpx;background:#e3e7ee}.generation-item__line--done{background:#81d7aa}.generation-item__body{min-width:0;flex:1}.generation-item__title,.generation-item__desc{display:block}.generation-item__title{font-size:24rpx;font-weight:700}.generation-item__desc{margin-top:6rpx;color:#8a93a4;font-size:20rpx}.loading-dots{display:flex;gap:5rpx;margin-top:12rpx}.loading-dots text{display:block;width:7rpx;height:7rpx;border-radius:50%;background:#5265f5;animation:pulse 1s infinite}.loading-dots text:nth-child(2){animation-delay:.15s}.loading-dots text:nth-child(3){animation-delay:.3s}.overall-progress{padding:20rpx;border-radius:16rpx;background:#f7f8fc}.overall-progress__head{display:flex;justify-content:space-between;font-size:22rpx;font-weight:700}.overall-progress__track{height:10rpx;margin-top:14rpx;overflow:hidden;border-radius:99rpx;background:#dde2ec}.overall-progress__value{height:100%;border-radius:inherit;background:#5265f5;transition:width .25s}.overall-progress__time{display:block;margin-top:11rpx;color:#8993a5;font-size:19rpx}.secondary-button--full,.primary-button--full{width:100%;margin-top:22rpx}.success-hero{display:flex;align-items:center;padding:9rpx 0 28rpx;flex-direction:column;text-align:center}.success-icon{display:flex;width:94rpx;height:94rpx;align-items:center;justify-content:center;border-radius:50%;background:#35bd7d;color:#fff;font-size:48rpx;box-shadow:0 13rpx 30rpx rgba(53,189,125,.22)}.success-hero__title{margin-top:18rpx;font-size:32rpx;font-weight:800}.success-hero__desc{margin-top:8rpx;color:#8790a2;font-size:21rpx}.result-summary{display:flex;align-items:center;justify-content:space-between;padding:20rpx;border:1px solid #e2e6ee;border-radius:16rpx}.result-summary__name{display:flex;min-width:0;align-items:center;gap:15rpx}.result-summary__file-icon,.export-choice__icon{display:flex;width:52rpx;height:52rpx;flex:none;align-items:center;justify-content:center;border-radius:12rpx;background:#f07032;color:#fff;font-size:24rpx;font-weight:800}.result-summary__name view text{display:block;max-width:330rpx;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.result-summary__name view text:first-child{font-size:23rpx;font-weight:700}.result-summary__name view text:last-child{margin-top:5rpx;color:#8d96a7;font-size:18rpx}.result-summary__meta{text-align:right}.result-summary__meta text{display:block;color:#32ac73;font-size:19rpx}.result-summary__meta text:last-child{margin-top:5rpx;color:#a0a7b5}.preview-section{margin-top:27rpx}.preview-section__head{display:flex;justify-content:space-between;margin-bottom:17rpx;font-size:25rpx;font-weight:700}.preview-section__head text:last-child{color:#8992a4;font-size:19rpx;font-weight:400}.slide-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:17rpx 14rpx}.slide-thumb{text-align:center}.slide-thumb__canvas{position:relative;display:flex;aspect-ratio:16/9;overflow:hidden;padding:18rpx;flex-direction:column;border:1px solid #dfe4ed;border-radius:12rpx;background:#f8faff;text-align:left;box-sizing:border-box}.slide-thumb__canvas--campus{background:#fff7e9}.slide-thumb__canvas--focus{background:#172034;color:#fff}.slide-thumb__canvas--cover{justify-content:center;background:#1f2b4d;color:#fff}.slide-thumb__number{color:#5265f5;font-size:12rpx;font-weight:800}.slide-thumb__canvas--cover .slide-thumb__number{color:#87a0ff}.slide-thumb__title{z-index:1;max-width:74%;margin-top:8rpx;font-size:18rpx;font-weight:800}.slide-thumb__lines{display:flex;gap:5rpx;margin-top:10rpx;flex-direction:column}.slide-thumb__lines text{display:block;width:54%;height:3rpx;border-radius:99rpx;background:#bdc5d6}.slide-thumb__lines text:nth-child(2){width:70%}.slide-thumb__lines text:nth-child(3){width:43%}.slide-thumb__decor{position:absolute;right:-13rpx;bottom:-22rpx;width:88rpx;height:88rpx;border-radius:20rpx;background:rgba(82,101,245,.17);transform:rotate(18deg)}.slide-thumb__page{display:block;margin-top:6rpx;color:#8a93a5;font-size:17rpx}.text-button{height:66rpx;margin-top:18rpx;border:1px solid #dbe0eb;border-radius:12rpx;background:#fff;color:#5265f5;font-size:21rpx;line-height:66rpx}.text-button::after{border:0}.export-choice{position:relative;display:flex;min-height:126rpx;align-items:center;padding:22rpx 54rpx 22rpx 20rpx;border:2rpx solid #e0e4ec;border-radius:16rpx;box-sizing:border-box}.export-choice+.export-choice{margin-top:18rpx}.export-choice--selected{border-color:#5265f5;background:#fafaff}.export-choice__icon--pdf{background:#ed4d4d;font-size:15rpx}.export-choice__body{min-width:0;margin-left:17rpx}.export-choice__title,.export-choice__desc{display:block}.export-choice__title{color:#4a5ae8;font-size:24rpx;font-weight:700}.export-choice__title text{color:#7c8698;font-weight:400}.export-choice__desc{margin-top:7rpx;color:#808a9d;font-size:20rpx;line-height:1.5}.download-ready{display:flex;margin-top:24rpx;padding:28rpx 18rpx;align-items:center;flex-direction:column;border:1px solid #dfe4fb;border-radius:16rpx;background:#f7f8ff;text-align:center}.download-ready__illustration{position:relative}.download-ready__file{display:flex;width:78rpx;height:92rpx;align-items:center;justify-content:center;border-radius:12rpx;background:#fff;color:#f06d31;font-size:22rpx;font-weight:800;box-shadow:0 8rpx 22rpx rgba(55,67,130,.12)}.download-ready__arrow{position:absolute;right:-23rpx;bottom:-8rpx;display:flex;width:43rpx;height:43rpx;align-items:center;justify-content:center;border-radius:50%;background:#5265f5;color:#fff;font-size:24rpx}.download-ready__title{margin-top:18rpx;font-size:26rpx;font-weight:700}.download-ready__name{max-width:100%;margin-top:8rpx;overflow:hidden;color:#5b6578;font-size:20rpx;text-overflow:ellipsis;white-space:nowrap}.download-ready__hint{margin-top:12rpx;color:#969eae;font-size:18rpx}.back-result-button{height:74rpx;margin-top:20rpx;border:1px solid #dfe3ec;border-radius:12rpx;background:#fff;color:#5265f5;font-size:22rpx;line-height:74rpx}.back-result-button::after{border:0}@keyframes pulse{0%,100%{opacity:.25;transform:translateY(0)}50%{opacity:1;transform:translateY(-4rpx)}}@media(min-width:700px){.slide-grid{grid-template-columns:repeat(3,1fr)}}
</style>

<style scoped>
.ppt-flow--floating-actions{padding-bottom:calc(128rpx + env(safe-area-inset-bottom));box-sizing:border-box}
.preview-card__content--expanded{display:block;overflow:visible;-webkit-line-clamp:unset}
.preview-card__toggle{display:flex;align-items:center;justify-content:center;gap:8rpx;margin-top:14rpx;padding-top:14rpx;border-top:1px solid #e4e8f0;color:#5265f5;font-size:21rpx;font-weight:600}
.preview-card__toggle-arrow{display:inline-block;font-size:24rpx;line-height:1;transition:transform .2s ease}
.preview-card__toggle-arrow--expanded{transform:rotate(180deg)}
.single-action--floating,.bottom-actions{position:fixed;z-index:40;left:24rpx;right:24rpx;bottom:calc(18rpx + env(safe-area-inset-bottom));margin:0;padding:12rpx;border:1px solid rgba(222,227,239,.9);border-radius:20rpx;background:rgba(255,255,255,.94);box-shadow:0 14rpx 42rpx rgba(35,50,92,.16);box-sizing:border-box;backdrop-filter:blur(12px)}
.single-action--floating .primary-button{width:100%}
.bottom-actions{display:block}
.bottom-actions__buttons{display:flex;gap:20rpx}
.operation-feedback{margin-top:20rpx;padding:22rpx;border:1px solid #e2e7f1;border-radius:16rpx;background:#fff;box-shadow:0 10rpx 28rpx rgba(35,50,92,.07)}
.operation-feedback__head{display:flex;align-items:center;justify-content:space-between;color:#445168;font-size:20rpx;font-weight:700}
.operation-feedback__head text:last-child{color:#5265f5}
.operation-feedback__track{height:7rpx;margin-top:10rpx;overflow:hidden;border-radius:99rpx;background:#e8ebf3}
.operation-feedback__value{height:100%;border-radius:inherit;background:#5265f5;transition:width .35s ease}
.operation-feedback__detail{display:block;margin-top:8rpx;color:#8a94a6;font-size:18rpx}
.visual-mode-row{margin-top:12rpx;padding-top:18rpx;border-top:1px solid #eef0f4}
.visual-mode-row .switch-row__title{color:#343d4f;font-size:23rpx}
.visual-mode-segmented{margin-top:14rpx}
.visual-mode-row .switch-row__desc{margin-top:10rpx;color:#939bad;font-size:18rpx;line-height:1.5}
.slide-layout-lock-card{display:flex;align-items:center;gap:16rpx;margin-top:18rpx;padding:18rpx;border:1px solid #e0e7ef;border-radius:18rpx;background:linear-gradient(135deg,#f8fafc,#fff);box-shadow:0 8rpx 20rpx rgba(44,67,91,.05)}
.slide-layout-lock-card--locked{border-color:#b8cde0;background:linear-gradient(135deg,#f1f7fb,#fff)}
.slide-layout-lock-card__icon{display:flex;width:52rpx;height:52rpx;flex:none;align-items:center;justify-content:center;border-radius:15rpx;background:#e8eef4;color:#58718a;font-size:22rpx;font-weight:800}
.slide-layout-lock-card__icon--locked{background:#5c7b98;color:#fff}
.slide-layout-lock-card__copy{min-width:0;flex:1}
.slide-layout-lock-card__title,.slide-layout-lock-card__desc,.slide-layout-lock-card__layout{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.slide-layout-lock-card__title{color:#263c51;font-size:22rpx;font-weight:800}
.slide-layout-lock-card__desc{margin-top:5rpx;color:#77889a;font-size:18rpx}
.slide-layout-lock-card__layout{margin-top:6rpx;color:#9aa8b5;font-size:17rpx}
.slide-layout-lock-card__action{height:58rpx;flex:none;margin:0;padding:0 17rpx;border:1px solid #9fb6ca;border-radius:12rpx;background:#fff;color:#4e718f;font-size:19rpx;font-weight:700;line-height:56rpx}
.slide-layout-lock-card__action--locked{border-color:#5c7b98;background:#5c7b98;color:#fff}
.slide-layout-lock-card__action::after{border:0}
.flow-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:22rpx}
.flow-heading{padding:18rpx 4rpx 10rpx}
.flow-heading__copy{min-width:0;flex:1}
.flow-heading__title{margin-top:0;font-size:36rpx;line-height:1.18}
.editor-toolbar{display:flex;align-items:flex-start;justify-content:space-between;gap:18rpx;padding-bottom:23rpx;border-bottom:1px solid #eef0f4}
.editor-toolbar>view:first-child{min-width:0;flex:1}
.editor-toolbar__title,.editor-toolbar__desc{display:block}
.editor-toolbar__title{font-size:29rpx;font-weight:800}
.editor-toolbar__desc{margin-top:7rpx;color:#8992a4;font-size:20rpx;line-height:1.45}
.editor-toolbar__aside{display:flex;flex:none;align-items:flex-end;gap:10rpx;flex-direction:column}
.editor-toolbar__result-link{color:#5c7a99;font-size:18rpx;font-weight:700}
.outline-toolbar-actions{display:flex;flex:none;align-items:center;gap:10rpx}
.outline-page-count{display:flex;height:48rpx;align-items:center;padding:0 15rpx;border-radius:999rpx;background:#eef0ff;color:#5062e8;font-size:18rpx;font-weight:800}
.outline-history-button{display:flex;height:48rpx;align-items:center;padding:0 17rpx;border:1px solid #d7def4;border-radius:12rpx;color:#5062e9;font-size:20rpx;font-weight:700}
.outline-name-field{display:flex;align-items:center;gap:14rpx;margin-top:20rpx;padding:12rpx;border:1px solid #dfe6ef;border-radius:16rpx;background:#f9fbfe}
.outline-name-field>text{display:flex;height:58rpx;flex:none;align-items:center;padding:0 18rpx;border-radius:12rpx;background:#eef2f7;color:#44556b;font-size:21rpx;font-weight:700}
.outline-name-field input,.outline-item input,.edit-field input,.edit-field textarea,.prompt-field textarea{width:100%;border:1px solid #dfe4ed;border-radius:12rpx;background:#fff;box-sizing:border-box}
.outline-name-field input{min-width:0;height:58rpx;padding:0 8rpx;border:0;background:transparent;font-size:23rpx}
.outline-list{margin-top:20rpx}
.outline-item{padding:18rpx;border:1px solid #e2e6ee;border-radius:16rpx;background:#fafbfe}
.outline-item+.outline-item{margin-top:13rpx}
.outline-item__order{display:flex;width:39rpx;height:39rpx;flex:none;align-items:center;justify-content:center;border-radius:12rpx;background:#eef0ff;color:#5062e8;font-size:19rpx;font-weight:700}
.outline-item__head{display:flex;align-items:center;gap:14rpx}
.outline-item input{height:63rpx;padding:0 15rpx;font-size:22rpx}
.outline-item__meta{display:flex;align-items:center;justify-content:space-between;gap:16rpx;margin-top:14rpx;padding-left:53rpx}
.outline-level-picker{display:flex;height:40rpx;align-items:center;justify-content:center;gap:8rpx;padding:0 17rpx;border-radius:999rpx;background:#eef2f7;color:#526176;font-size:18rpx;font-weight:700;line-height:40rpx}
.outline-level-picker__chevron{display:block;width:9rpx;height:9rpx;margin-top:-3rpx;border-right:2rpx solid #526176;border-bottom:2rpx solid #526176;transform:rotate(45deg)}
.outline-item__actions{display:flex;align-items:center;justify-content:flex-end;gap:22rpx}
.outline-item__actions text{color:#6675e9;font-size:19rpx;font-weight:700;line-height:40rpx}
.outline-item__actions .outline-item__delete{color:#9aa3b3}
.add-outline-button{height:72rpx;margin-top:16rpx;border:1px dashed #bfc8e9;border-radius:12rpx;background:#f9faff;color:#5264eb;font-size:21rpx;line-height:72rpx}
.add-outline-button::after{border:0}
.outline-save-tip{display:flex;justify-content:space-between;gap:12rpx;margin-top:15rpx;color:#929bad;font-size:18rpx;line-height:1.45}
.outline-save-tip text:last-child{flex:none;color:#31a971}
.bottom-actions--three .bottom-actions__buttons{gap:12rpx}
.bottom-actions--three button{font-size:22rpx}
.secondary-button[disabled]{opacity:.4}
.page-editor-count{flex:none;padding:8rpx 13rpx;border-radius:99rpx;background:#eef0ff;color:#5061e7;font-size:20rpx;font-weight:700}
.slide-tabs{width:100%;margin:22rpx 0;white-space:nowrap}
.slide-tabs__inner{display:inline-flex;gap:10rpx;padding:2rpx}
.slide-tab{display:flex;width:128rpx;height:76rpx;padding:10rpx 12rpx;justify-content:center;flex-direction:column;border:1px solid #e0e4ed;border-radius:12rpx;background:#fafbfe;box-sizing:border-box}
.slide-tab text{display:block;overflow:hidden;color:#929bad;font-size:16rpx;text-overflow:ellipsis;white-space:nowrap}
.slide-tab text:last-child{margin-top:5rpx;color:#4c5669;font-size:18rpx}
.slide-tab__lock{margin-top:4rpx;color:#557793!important;font-size:15rpx!important;font-weight:700}
.slide-tab--active{border-color:#586af3;background:#f2f3ff}
.slide-tab--active text{color:#5263eb}
.slide-editor__preview{position:relative;display:flex;min-height:270rpx;overflow:hidden;padding:32rpx;justify-content:center;flex-direction:column;border-left:7rpx solid #5265f5;border-radius:16rpx;background:#f6f8ff;box-sizing:border-box}
.slide-editor__preview{aspect-ratio:16 / 9;min-height:0;padding:0;border-left:0;background:#eef1f7}
.slide-editor__preview-image{display:block;width:100%;height:100%;background:#fff}
.slide-editor__preview-image--empty{padding:54rpx;box-sizing:border-box;opacity:.92}
.slide-editor__preview-fallback{position:relative;display:flex;width:100%;height:100%;padding:32rpx;justify-content:center;flex-direction:column;box-sizing:border-box}
.slide-editor__preview-status{position:absolute;z-index:3;right:12rpx;bottom:12rpx;max-width:76%;padding:8rpx 13rpx;border-radius:99rpx;background:rgba(24,32,51,.76);color:#fff;font-size:17rpx;line-height:1.35}
.slide-editor__preview-status--loading{top:50%;right:auto;bottom:auto;left:50%;display:flex;align-items:center;gap:10rpx;max-width:none;padding:13rpx 18rpx;border-radius:14rpx;background:rgba(24,32,51,.84);font-size:20rpx;transform:translate(-50%,-50%)}
.slide-editor__preview-spinner{width:22rpx;height:22rpx;border:3rpx solid rgba(255,255,255,.42);border-top-color:#fff;border-radius:50%;box-sizing:border-box;animation:ppt-preview-spin .8s linear infinite}
.slide-editor__preview--campus{border-left-color:#f1a653;background:#fff6e8}
.slide-editor__preview--focus{border-left-color:#3eabbc;background:#172034;color:#fff}
.slide-editor__preview-index,.slide-editor__preview-title,.slide-editor__preview-content{position:relative;z-index:1;display:block}
.slide-editor__preview-index{color:#5365eb;font-size:18rpx;font-weight:800}
.slide-editor__preview-title{max-width:75%;margin-top:13rpx;font-size:33rpx;font-weight:800}
.slide-editor__preview-content{display:-webkit-box;max-width:78%;margin-top:14rpx;overflow:hidden;color:#687386;font-size:19rpx;line-height:1.55;white-space:pre-wrap;-webkit-box-orient:vertical;-webkit-line-clamp:3}
.slide-editor__preview--focus .slide-editor__preview-content{color:#aeb8cb}
.slide-editor__preview-decor{position:absolute;right:-35rpx;bottom:-50rpx;width:190rpx;height:190rpx;border-radius:55rpx;background:rgba(82,102,239,.15);transform:rotate(20deg)}
.edit-field,.prompt-field{margin-top:22rpx}
.edit-field__label{display:flex;justify-content:space-between;margin-bottom:10rpx;font-size:22rpx;font-weight:700}
.edit-field__label text:last-child{color:#929bad;font-size:18rpx;font-weight:400}
.edit-field input{height:72rpx;padding:0 18rpx;font-size:22rpx}
.edit-field textarea,.prompt-field textarea{min-height:135rpx;padding:16rpx 18rpx;font-size:21rpx;line-height:1.6}
.prompt-field{padding:19rpx;border:1px solid #e0e5ee;border-radius:16rpx;background:#fbfcfe}
.prompt-field--shared{border-color:#dce1fb;background:#f8f9ff}
.prompt-field__head{display:flex;align-items:center;justify-content:space-between;margin-bottom:13rpx}
.prompt-field__head view:first-child text{display:block;font-size:23rpx;font-weight:700}
.prompt-field__head view:first-child text:last-child{margin-top:5rpx;color:#8c95a6;font-size:18rpx;font-weight:400}
.prompt-badge{padding:6rpx 12rpx;border-radius:99rpx;background:#e9edff;color:#5264eb;font-size:17rpx}
.prompt-badge--private{background:#eef1f5;color:#657084}
.prompt-field__hint{display:block;margin-top:9rpx;color:#929bad;font-size:17rpx}
.slide-editor__navigation{display:flex;gap:13rpx;margin-top:20rpx}
.slide-editor__navigation .secondary-button{height:68rpx;font-size:21rpx;line-height:68rpx}
.image-replace-row{display:flex;align-items:center;justify-content:space-between;gap:20rpx;margin-top:20rpx;padding:18rpx 20rpx;border:1px solid #e1e5ef;border-radius:16rpx;background:#fafbfe}.image-replace-row__title,.image-replace-row__desc{display:block}.image-replace-row__title{font-size:23rpx;font-weight:700}.image-replace-row__desc{margin-top:6rpx;color:#8a93a5;font-size:18rpx}.image-replace-row__button{flex:none;margin:0;padding:0 18rpx}
.history-mask{position:fixed;z-index:1300;inset:0;background:rgba(20,28,48,.32);backdrop-filter:blur(3rpx)}
.history-drawer{position:absolute;right:0;top:0;bottom:0;width:min(86vw,660rpx);padding:34rpx 27rpx;background:#f6f8fc;box-sizing:border-box;box-shadow:-18rpx 0 45rpx rgba(27,37,72,.15)}
.history-drawer__head{display:flex;align-items:flex-start;justify-content:space-between}
.history-drawer__title,.history-drawer__desc{display:block}
.history-drawer__title{font-size:34rpx;font-weight:800}
.history-drawer__desc{margin-top:8rpx;color:#8a93a5;font-size:19rpx}
.history-drawer__close{display:flex;width:56rpx;height:56rpx;align-items:center;justify-content:center;border-radius:50%;background:#fff;color:#697386;font-size:34rpx}
.history-tabs{display:flex;margin-top:27rpx;padding:6rpx;border-radius:12rpx;background:#e9edf4}
.history-tabs__item{display:flex;height:58rpx;flex:1;align-items:center;justify-content:center;border-radius:12rpx;color:#788295;font-size:21rpx}
.history-tabs__item--active{background:#fff;color:#4e60e7;font-weight:700;box-shadow:0 4rpx 12rpx rgba(38,51,95,.07)}
.history-list{height:calc(100vh - 220rpx);margin-top:20rpx}
.history-card{padding:20rpx;border:1px solid #e1e5ed;border-radius:16rpx;background:#fff}
.history-card+.history-card{margin-top:13rpx}
.history-card__head{display:flex;align-items:center;justify-content:space-between;color:#99a1af;font-size:17rpx}
.history-card__type{padding:5rpx 10rpx;border-radius:99rpx;background:#e9edff;color:#5062e8}
.history-card__type--upload{background:#edf1f4;color:#667386}
.history-card__title,.history-card__meta{display:block}
.history-card__title{margin-top:13rpx;font-size:23rpx;font-weight:700}
.history-card__meta{margin-top:7rpx;color:#8a93a5;font-size:18rpx}
.history-card__actions{display:flex;gap:22rpx;margin-top:16rpx;padding-top:13rpx;border-top:1px solid #eef0f4;color:#5264e9;font-size:19rpx}
.history-card__delete{color:#999faa}
.history-empty{display:flex;padding:100rpx 20rpx;align-items:center;flex-direction:column;text-align:center}
.history-empty__icon{display:flex;width:70rpx;gap:10rpx;padding:18rpx;flex-direction:column;border:2rpx solid #ccd3e1;border-radius:16rpx;box-sizing:border-box}
.history-empty__icon text{height:4rpx;border-radius:99rpx;background:#aab3c2}
.history-empty__title{margin-top:22rpx;font-size:24rpx;font-weight:700}
.history-empty__desc{margin-top:8rpx;color:#929bad;font-size:19rpx}
.style-card__preview-image{display:block;width:100%;padding:0;background:#f7f9ff}
.template-section-head{display:flex;align-items:flex-start;justify-content:space-between;gap:20rpx;margin-bottom:17rpx}
.template-section-head__selected{display:block;margin-top:7rpx;color:#8a93a5;font-size:18rpx}
.template-section-head__action{display:flex;flex:none;align-items:center;gap:13rpx;padding:8rpx 12rpx;border-radius:12rpx;background:#f2f4ff;color:#5365eb;font-size:18rpx}
.template-section-head__action text:first-child{color:#7d8799}
.style-scroll--expanded{overflow:visible;white-space:normal}
.style-scroll--expanded .style-list{display:grid;width:100%;grid-template-columns:repeat(2,minmax(0,1fr));gap:16rpx;box-sizing:border-box}
.style-scroll--expanded .style-card{width:auto;min-width:0}
.style-card__layouts{display:block;margin-top:5rpx;color:#6f7de0;font-size:17rpx}
.template-scroll-hint{display:flex;align-items:center;justify-content:space-between;margin-top:12rpx;color:#919aab;font-size:18rpx}
.template-scroll-hint text:last-child{color:#5265f5}
.template-empty{display:flex;min-height:130rpx;align-items:center;justify-content:center;gap:12rpx;flex-direction:column;border:1px dashed #d9deea;border-radius:12rpx;background:#fafbfe;color:#8b94a5;font-size:20rpx}
.template-empty__retry{color:#5265f5;font-weight:600}
.slide-thumb__image{position:absolute;inset:0;z-index:0;width:100%;height:100%}
.export-choice--disabled{opacity:.52}
.product-hero{position:relative;margin-bottom:28rpx;padding:28rpx 28rpx 30rpx;overflow:hidden;border:1px solid #dfe7ef;border-radius:20rpx;background:#f6f8fb}
.product-hero__copy{position:relative;z-index:1;max-width:470rpx}
.product-hero__eyebrow,.product-hero__title,.product-hero__desc{display:block}
.product-hero__eyebrow{color:#526f88;font-size:19rpx;font-weight:700}
.product-hero__title{margin-top:10rpx;color:#172033;font-size:38rpx;font-weight:800;line-height:1.22}
.product-hero__desc{margin-top:12rpx;color:#667386;font-size:21rpx;line-height:1.55}
.product-hero__slide{position:absolute;right:22rpx;bottom:24rpx;width:140rpx;height:86rpx;padding:17rpx 16rpx;border:1px solid #cfdbe7;border-radius:12rpx;background:#fff;box-shadow:0 10rpx 26rpx rgba(49,75,99,.12);box-sizing:border-box;transform:rotate(-4deg)}
.product-hero__slide text{display:block;height:6rpx;border-radius:99rpx;background:#526f88}
.product-hero__slide text+text{margin-top:10rpx;background:#b8c6d4}
.product-hero__slide text:nth-child(2){width:75%}
.product-hero__slide text:nth-child(3){width:52%}
.recover-card{display:flex;align-items:center;justify-content:space-between;gap:20rpx;margin-bottom:22rpx;padding:20rpx;border:1px solid #e1e7ef;border-radius:16rpx;background:#fff}
.recover-card--warning{border-color:#ecd8b4;background:#fff8ed}
.recover-card__title,.recover-card__desc{display:block}
.recover-card__title{color:#7b541d;font-size:24rpx;font-weight:700}
.recover-card__desc{margin-top:7rpx;color:#9b6b24;font-size:19rpx;line-height:1.45}
.recover-card__button{flex:none;height:58rpx;margin:0;padding:0 18rpx;border:1px solid #d8b06f;border-radius:12rpx;background:#fff;color:#8b5f23;font-size:20rpx;line-height:58rpx}
.recover-card__button::after{border:0}
.source-input-card{overflow:hidden;border:1px solid #dce2ec;border-radius:16rpx;background:#fff}
.source-input-card__head{display:flex;align-items:center;justify-content:space-between;gap:16rpx;padding:16rpx 18rpx;border-bottom:1px solid #edf1f5;background:#f8fafc}
.source-input-card__status{display:flex;min-width:0;align-items:center;gap:14rpx}
.source-input-card__icon{position:relative;display:flex;width:50rpx;height:58rpx;flex:none;align-items:center;justify-content:center;overflow:hidden;border-radius:8rpx;background:#5265f5;color:#fff;box-sizing:border-box;box-shadow:0 6rpx 16rpx rgba(82,101,245,.18)}
.source-input-card__icon::after{position:absolute;right:0;top:0;border-top:15rpx solid #eef2ff;border-left:15rpx solid rgba(255,255,255,.5);content:''}
.source-input-card__icon--empty{background:#5265f5}
.source-input-card__icon text{position:relative;z-index:1;width:100%;color:#fff;font-size:13rpx;font-weight:800;line-height:1;text-align:center;letter-spacing:0}
.source-input-card__copy text{display:block}
.source-input-card__copy text:first-child{max-width:330rpx;overflow:hidden;color:#26384a;font-size:22rpx;font-weight:700;text-overflow:ellipsis;white-space:nowrap}
.source-input-card__copy text:last-child{max-width:360rpx;margin-top:5rpx;overflow:hidden;color:#7b8798;font-size:17rpx;text-overflow:ellipsis;white-space:nowrap}
.source-input-card__actions{display:flex;flex:none;align-items:center;gap:16rpx;color:#5265f5;font-size:20rpx;font-weight:700}
.source-input-card__delete{color:#98a1b2}
.source-textarea{width:100%;min-height:210rpx;padding:18rpx;border:0;background:#fff;color:#26384a;font-size:22rpx;line-height:1.55;box-sizing:border-box}
.outline-mode-selector{margin-bottom:22rpx}
.outline-mode-selector__head{display:flex;align-items:baseline;margin-bottom:12rpx}
.outline-mode-selector__title{color:#26384a;font-size:25rpx;font-weight:800}
.outline-mode-selector__options{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12rpx}
.outline-mode-card{display:flex;min-width:0;min-height:150rpx;align-items:flex-start;gap:11rpx;padding:18rpx 15rpx;border:1px solid #dfe5ee;border-radius:16rpx;background:#fff;box-sizing:border-box;transition:border-color .2s,background-color .2s,box-shadow .2s}
.outline-mode-card--selected{border-color:#6575e8;background:#f5f7ff;box-shadow:0 6rpx 18rpx rgba(82,101,245,.1)}
.outline-mode-card__radio{display:flex;width:28rpx;height:28rpx;flex:none;align-items:center;justify-content:center;margin-top:2rpx;border:2rpx solid #b7c2d1;border-radius:50%;box-sizing:border-box}
.outline-mode-card--selected .outline-mode-card__radio{border-color:#6575e8}
.outline-mode-card__radio-dot{width:12rpx;height:12rpx;border-radius:50%;background:#6575e8}
.outline-mode-card__copy{min-width:0;flex:1}
.outline-mode-card__title,.outline-mode-card__desc{display:block}
.outline-mode-card__title{color:#26384a;font-size:22rpx;font-weight:800;line-height:1.35}
.outline-mode-card--selected .outline-mode-card__title{color:#4f60d8}
.outline-mode-card__desc{margin-top:8rpx;color:#78869a;font-size:17rpx;line-height:1.5}
.slide-task-card{margin-top:26rpx;padding:20rpx;border:1px solid #dfe7ef;border-radius:16rpx;background:#fff}
.slide-task-card__head{display:flex;align-items:center;justify-content:space-between;color:#26384a;font-size:22rpx;font-weight:700}
.slide-task-card__head text:first-child{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.slide-task-card__head text:last-child{flex:none;margin-left:18rpx;color:#526f88}
.slide-task-card__track{height:8rpx;margin-top:14rpx;overflow:hidden;border-radius:99rpx;background:#e1e8ef}
.slide-task-card__track view{height:100%;border-radius:inherit;background:#526f88;transition:width .25s ease}
.slide-task-card__stats{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10rpx;margin-top:18rpx}
.slide-task-card__stats view{padding:12rpx;border-radius:12rpx;background:#f4f7fa;text-align:center}
.slide-task-card__stats text{display:block}
.slide-task-card__stats text:first-child{color:#172033;font-size:27rpx;font-weight:800}
.slide-task-card__stats text:last-child{margin-top:5rpx;color:#738195;font-size:17rpx}
.slide-task-card__processing{display:block;margin-top:14rpx;color:#526f88;font-size:19rpx;line-height:1.45}
.format-status-panel{margin-top:22rpx;padding:20rpx;border:1px solid #dfe7ef;border-radius:16rpx;background:#f8fafc}
.format-status-panel__head{display:flex;align-items:flex-start;justify-content:space-between;gap:18rpx;margin-bottom:16rpx}
.format-status-panel__head>text{flex:none;padding:7rpx 12rpx;border-radius:99rpx;background:#e7eef5;color:#314b63;font-size:18rpx;font-weight:700}
.format-status-panel__title,.format-status-panel__desc{display:block}
.format-status-panel__title{font-size:25rpx;font-weight:800}
.format-status-panel__desc{margin-top:7rpx;color:#718094;font-size:19rpx;line-height:1.45}
.format-status-row{display:flex;align-items:center;gap:15rpx;padding:16rpx;border:1px solid #e4ebf2;border-radius:12rpx;background:#fff}
.format-status-row+.format-status-row{margin-top:12rpx}
.format-status-row--disabled{background:#fbfcfe;opacity:.72}
.format-status-row__icon{display:flex;width:50rpx;height:50rpx;flex:none;align-items:center;justify-content:center;border-radius:12rpx;background:#f07032;color:#fff;font-size:22rpx;font-weight:800}
.format-status-row__body{min-width:0;flex:1}
.format-status-row__body text{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.format-status-row__body text:first-child{color:#26384a;font-size:22rpx;font-weight:700}
.format-status-row__body text:last-child{margin-top:5rpx;color:#748196;font-size:18rpx}
.format-status-row__state{flex:none;color:#32ac73;font-size:19rpx;font-weight:700}
.format-status-row--disabled .format-status-row__state{color:#a16b24}
.export-status-card{display:flex;align-items:flex-start;justify-content:space-between;gap:18rpx;margin-bottom:20rpx;padding:20rpx;border:1px solid #dfe7ef;border-radius:16rpx;background:#f8fafc}
.export-status-card--warning{border-color:#ecd8b4;background:#fff8ed}
.export-status-card__title,.export-status-card__desc{display:block}
.export-status-card__title{color:#26384a;font-size:25rpx;font-weight:800}
.export-status-card__desc{margin-top:7rpx;color:#718094;font-size:19rpx;line-height:1.45}
.export-status-card--warning .export-status-card__title{color:#7b541d}
.export-status-card--warning .export-status-card__desc{color:#9b6b24}
.export-status-card__button{flex:none;height:58rpx;margin:0;padding:0 18rpx;border:1px solid #d8b06f;border-radius:12rpx;background:#fff;color:#8b5f23;font-size:20rpx;line-height:58rpx}
.export-status-card__button::after{border:0}
.export-choice__ready,.export-choice__issue{display:block;margin-top:7rpx;font-size:18rpx;line-height:1.35}
.export-choice__ready{color:#32ac73}
.export-choice__issue{color:#a16b24}
.bottom-actions__buttons{flex-wrap:wrap}
.bottom-actions__buttons button{min-width:0}
.template-library-entry,.template-detail-entry,.template-upload-entry{min-height:720rpx}
.template-library-hero{position:relative;min-height:200rpx;display:flex;align-items:center;margin-bottom:24rpx;padding:28rpx;overflow:hidden;border:1px solid #dfe7ef;border-radius:20rpx;background:#f8fafc;box-sizing:border-box}
.template-library-hero__copy{position:relative;z-index:1;max-width:470rpx}
.template-library-hero__title,.template-library-hero__desc{display:block}
.template-library-hero__title{color:#172033;font-size:38rpx;font-weight:800;line-height:1.22}
.template-library-hero__desc{margin-top:12rpx;color:#667386;font-size:21rpx;line-height:1.55}
.template-library-hero__stack{position:absolute;right:22rpx;bottom:24rpx;width:160rpx;height:112rpx}
.template-library-hero__stack view{position:absolute;width:126rpx;height:78rpx;border:1px solid #cfdbe7;border-radius:12rpx;background:#fff;box-shadow:0 10rpx 24rpx rgba(49,75,99,.12)}
.template-library-hero__stack view:nth-child(1){right:18rpx;top:0;transform:rotate(6deg)}
.template-library-hero__stack view:nth-child(2){right:8rpx;top:18rpx;transform:rotate(-3deg)}
.template-library-hero__stack view:nth-child(3){right:0;top:36rpx}
.template-category-scroll{width:100%;margin-bottom:18rpx;white-space:nowrap}
.template-category-tabs{display:inline-flex;gap:12rpx;padding:2rpx}
.template-category-tab{display:flex;height:58rpx;align-items:center;justify-content:center;padding:0 24rpx;border:1px solid #dce3ee;border-radius:999rpx;background:#fff;color:#6f7b8f;font-size:21rpx;box-sizing:border-box}
.template-category-tab--active{border-color:#5265f5;background:#eef1ff;color:#4a5ae8;font-weight:700}
.template-library-list{display:flex;gap:16rpx;flex-direction:column;padding-bottom:4rpx}
.template-library-card{position:relative;display:flex;gap:18rpx;padding:14rpx;border:2rpx solid #e1e7ef;border-radius:16rpx;background:#fff;box-sizing:border-box}
.template-library-card--selected{border-color:#5265f5;background:#fbfcff;box-shadow:0 10rpx 24rpx rgba(62,78,150,.07)}
.template-library-card__thumb{display:block;width:178rpx;height:100rpx;flex:none;overflow:hidden;border:1px solid #dfe6ef;border-radius:12rpx;background:#f4f7fb;box-sizing:border-box}
.template-library-card__thumb--fallback{position:relative;padding:20rpx 18rpx}
.template-library-card__thumb--fallback text{display:block;height:7rpx;border-radius:99rpx;background:#526f88}
.template-library-card__thumb--fallback text+text{margin-top:10rpx;background:#c0ccd9}
.template-library-card__thumb--fallback text:nth-child(2){width:72%}
.template-library-card__thumb--fallback text:nth-child(3){width:52%}
.template-library-card__body{min-width:0;flex:1}
.template-library-card__head{display:flex;align-items:center;justify-content:space-between;gap:12rpx}
.template-library-card__name{min-width:0;overflow:hidden;color:#172033;font-size:25rpx;font-weight:800;text-overflow:ellipsis;white-space:nowrap}
.template-library-card__tag{flex:none;padding:5rpx 10rpx;border-radius:99rpx;background:#eef2f6;color:#526174;font-size:16rpx}
.template-library-card__desc{display:-webkit-box;margin-top:8rpx;overflow:hidden;color:#6b7889;font-size:19rpx;line-height:1.45;-webkit-box-orient:vertical;-webkit-line-clamp:2}
.template-library-card__meta{display:flex;align-items:center;justify-content:space-between;gap:12rpx;margin-top:12rpx;color:#8190a4;font-size:18rpx}
.template-library-card__actions{display:flex;flex:none;align-items:center;gap:14rpx;color:#5265f5;font-weight:700}
.template-library-card__selected{color:#718094}
.template-loading-card{display:flex;gap:18rpx;padding:14rpx;border:1px solid #e1e7ef;border-radius:16rpx;background:#fff}
.template-loading-card__thumb{width:178rpx;height:100rpx;flex:none;border-radius:12rpx;background:#edf2f7}
.template-loading-card__lines{display:flex;flex:1;justify-content:center;flex-direction:column;gap:12rpx}
.template-loading-card__lines text{display:block;height:12rpx;border-radius:99rpx;background:#edf2f7}
.template-loading-card__lines text:nth-child(2){width:82%}
.template-loading-card__lines text:nth-child(3){width:58%}
.template-detail-card{display:grid;grid-template-columns:1fr;gap:18rpx;padding:18rpx;border:1px solid #dfe7ef;border-radius:20rpx;background:#f8fafc}
.template-detail-card__eyebrow,.template-detail-card__title,.template-detail-card__desc{display:block}
.template-detail-card__eyebrow{color:#5265f5;font-size:19rpx;font-weight:700}
.template-detail-card__title{margin-top:8rpx;color:#172033;font-size:34rpx;font-weight:800;line-height:1.22}
.template-detail-card__desc{margin-top:10rpx;color:#667386;font-size:21rpx;line-height:1.5}
.template-detail-card__stats{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12rpx;margin-top:18rpx}
.template-detail-card__stats view{padding:15rpx;border:1px solid #e1e8ef;border-radius:12rpx;background:#fff}
.template-detail-card__stats text{display:block}
.template-detail-card__stats text:first-child{overflow:hidden;color:#172033;font-size:26rpx;font-weight:800;text-overflow:ellipsis;white-space:nowrap}
.template-detail-card__stats text:last-child{margin-top:5rpx;color:#718094;font-size:17rpx}
.layout-cover{overflow:hidden;border:1px solid #d8e1ec;border-radius:16rpx;background:#fff}
.layout-cover__stage{display:flex;align-items:center;justify-content:center;aspect-ratio:16/9;background:#f5f8fb}
.layout-cover__image{width:100%;height:100%}
.layout-cover__placeholder{display:flex;align-items:center;justify-content:center;height:100%;color:#8a97a8;font-size:21rpx}
.layout-cover__bar{display:flex;align-items:center;justify-content:space-between;gap:16rpx;padding:16rpx 18rpx;border-top:1px solid #e7edf4}
.layout-cover__hint{min-width:0;flex:1}
.layout-cover__title{display:block;overflow:hidden;color:#172033;font-size:25rpx;font-weight:800;text-overflow:ellipsis;white-space:nowrap}
.layout-cover__desc{display:block;margin-top:5rpx;color:#667386;font-size:19rpx}
.layout-cover__action{flex:none;padding:12rpx 22rpx;border-radius:999rpx;background:#5265f5}
.layout-cover__action text{color:#fff;font-size:20rpx;font-weight:700}
.layout-fullscreen{position:fixed;z-index:1400;inset:0;display:flex;flex-direction:column;background:#eceff3}
.layout-fullscreen__bar{display:flex;align-items:center;gap:18rpx;padding:calc(20rpx + env(safe-area-inset-top)) 24rpx 20rpx}
.layout-fullscreen__back{position:relative;display:flex;width:64rpx;height:64rpx;flex:none;align-items:center;justify-content:center;color:#172033}
.layout-fullscreen__back::before{content:'';width:20rpx;height:20rpx;border-left:4rpx solid currentColor;border-bottom:4rpx solid currentColor;transform:rotate(45deg);border-radius:2rpx;box-sizing:border-box}
.layout-fullscreen__bar-main{min-width:0;flex:1}
.layout-fullscreen__title{display:block;overflow:hidden;color:#172033;font-size:27rpx;font-weight:800;text-overflow:ellipsis;white-space:nowrap}
.layout-fullscreen__count{display:block;margin-top:5rpx;color:#667386;font-size:19rpx}
.layout-fullscreen__scroll{flex:1;height:100%;overflow:hidden}
.layout-fullscreen__item{padding:0 16rpx}
.layout-fullscreen__item+.layout-fullscreen__item{margin-top:12rpx}
.layout-fullscreen__item:last-child{margin-bottom:calc(170rpx + env(safe-area-inset-bottom))}
.layout-fullscreen__image{display:block;width:100%;border-radius:8rpx;background:#fff}
.layout-fullscreen__placeholder{display:flex;align-items:center;justify-content:center;flex-direction:column;gap:12rpx;width:100%;aspect-ratio:16/9;border-radius:8rpx;background:#dfe5ec;color:#667386;font-size:21rpx;box-sizing:border-box}
.layout-fullscreen__name{color:#8a97a8;font-size:18rpx}
.layout-fullscreen__loading{flex:1;display:flex;align-items:center;justify-content:center;flex-direction:column;gap:18rpx}
.layout-fullscreen__loading-spinner{width:56rpx;height:56rpx;border:5rpx solid #d5dce5;border-top-color:#5265f5;border-radius:50%;animation:layout-loading-spin .8s linear infinite}
.layout-fullscreen__loading-text{color:#172033;font-size:22rpx;font-weight:600}
.layout-fullscreen__loading-hint{color:#8a97a8;font-size:19rpx}
@keyframes layout-loading-spin{to{transform:rotate(360deg)}}
.layout-fullscreen__footer{position:absolute;left:0;right:0;bottom:0;display:flex;align-items:center;justify-content:center;padding:0 24rpx calc(28rpx + env(safe-area-inset-bottom));pointer-events:none;background:transparent}
.layout-fullscreen__use{pointer-events:auto;flex:none;display:flex;align-items:center;justify-content:center;height:80rpx;padding:0 56rpx;border-radius:999rpx;background:#5265f5;box-shadow:0 8rpx 24rpx rgba(23,32,51,.22)}
.layout-fullscreen__use text{color:#fff;font-size:24rpx;font-weight:600}
.layout-fullscreen__use:active{opacity:.88}
.selected-template-strip{display:flex;align-items:center;gap:15rpx;margin-bottom:22rpx;padding:16rpx;border:1px solid #dfe7ef;border-radius:16rpx;background:#f8fafc}
.selected-template-strip__preview{display:flex;width:82rpx;height:52rpx;flex:none;align-items:center;justify-content:center;overflow:hidden;border:1px solid #d6e0ea;border-radius:12rpx;background:#fff;color:#5265f5;font-size:24rpx;font-weight:800}
.selected-template-strip__preview image{display:block;width:100%;height:100%}
.selected-template-strip__main{min-width:0;flex:1}
.selected-template-strip__label,.selected-template-strip__name{display:block}
.selected-template-strip__label{color:#718094;font-size:17rpx}
.selected-template-strip__name{margin-top:4rpx;overflow:hidden;color:#172033;font-size:23rpx;font-weight:800;text-overflow:ellipsis;white-space:nowrap}
.selected-template-strip__actions{display:flex;flex:none;gap:16rpx;color:#5265f5;font-size:20rpx;font-weight:700}
.product-hero--compact{margin-bottom:24rpx}
.upload-preference-card{margin-top:22rpx;padding:18rpx;border:1px solid #dfe7ef;border-radius:16rpx;background:#fff}
.upload-preference-card__title{display:block;color:#172033;font-size:25rpx;font-weight:800}
.upload-preference-list{display:flex;gap:12rpx;flex-direction:column;margin-top:16rpx}
.upload-preference-list view{display:grid;grid-template-columns:1fr auto;gap:6rpx 18rpx;padding:16rpx;border:1px solid #e1e8ef;border-radius:12rpx;background:#fbfcfe}
.upload-preference-list text:first-child{color:#26384a;font-size:22rpx;font-weight:700}
.upload-preference-list text:nth-child(2){color:#314b63;font-size:22rpx;font-weight:800;text-align:right}
.upload-preference-list text:last-child{grid-column:1/3;color:#718094;font-size:17rpx;line-height:1.4}
.template-usage-card{margin-top:22rpx;padding:18rpx;border:1px solid #dfe7ef;border-radius:16rpx;background:#f8fafc}
.template-usage-card__main{display:flex;align-items:flex-start;gap:16rpx}
.template-usage-card__preview{display:flex;width:150rpx;height:84rpx;flex:none;align-items:center;justify-content:center;overflow:hidden;border:1px solid #d6e0ea;border-radius:12rpx;background:#fff;color:#5265f5;font-size:32rpx;font-weight:800}
.template-usage-card__preview image{display:block;width:100%;height:100%}
.template-usage-card__copy{min-width:0;flex:1}
.template-usage-card__label,.template-usage-card__name,.template-usage-card__desc{display:block}
.template-usage-card__label{color:#718094;font-size:17rpx}
.template-usage-card__name{margin-top:4rpx;overflow:hidden;color:#172033;font-size:25rpx;font-weight:800;text-overflow:ellipsis;white-space:nowrap}
.template-usage-card__desc{display:-webkit-box;margin-top:7rpx;overflow:hidden;color:#667386;font-size:18rpx;line-height:1.42;-webkit-box-orient:vertical;-webkit-line-clamp:2}
.template-usage-card__metrics{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10rpx;margin-top:16rpx}
.template-usage-card__metrics view{padding:12rpx 8rpx;border:1px solid #e4ebf2;border-radius:12rpx;background:#fff;text-align:center}
.template-usage-card__metrics text{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.template-usage-card__metrics text:first-child{color:#172033;font-size:23rpx;font-weight:800}
.template-usage-card__metrics text:last-child{margin-top:4rpx;color:#718094;font-size:16rpx}
.template-usage-card__actions{display:flex;justify-content:flex-end;margin-top:14rpx}
.template-usage-card__actions text{display:flex;height:46rpx;align-items:center;padding:0 16rpx;border:1px solid #d7def4;border-radius:12rpx;background:#fff;color:#5265f5;font-size:18rpx;font-weight:700}
.template-match-preview{margin-top:18rpx;padding:16rpx;border:1px solid #e1e8ef;border-radius:16rpx;background:#f8fafc}
.template-match-preview__head{display:flex;align-items:center;justify-content:space-between;color:#26384a;font-size:22rpx;font-weight:700}
.template-match-preview__head text:last-child{color:#718094;font-size:17rpx;font-weight:400}
.template-match-preview__grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:9rpx;margin-top:14rpx}
.template-match-preview__item>text{display:block;margin-top:7rpx;overflow:hidden;color:#4a586b;font-size:16rpx;text-align:center;text-overflow:ellipsis;white-space:nowrap}
.template-match-preview__canvas{position:relative;aspect-ratio:16/9;overflow:hidden;padding:8rpx;border-radius:8rpx;background:#fff;box-sizing:border-box}
.template-match-preview__canvas text{display:block;height:4rpx;border-radius:99rpx;background:#526f88}
.template-match-preview__canvas text+text{margin-top:6rpx;background:#becadb}
.template-match-preview__canvas--cover{display:flex;justify-content:center;flex-direction:column;background:#edf2f7}
.template-match-preview__canvas--cover text:first-child{width:70%;height:6rpx}
.template-match-preview__canvas--focus{border-left:4rpx solid #5265f5;background:#f8f9ff}
.template-match-preview__canvas--visual::after{position:absolute;right:8rpx;bottom:8rpx;width:22rpx;height:18rpx;border-radius:5rpx;background:#d9e4ef;content:''}
.generation-actions{display:flex;gap:16rpx;margin-top:22rpx}
.generation-actions button{min-width:0;flex:1}
.retry-render-card{display:flex;align-items:center;justify-content:space-between;gap:18rpx;margin-bottom:22rpx;padding:18rpx;border:1px solid #ecd8b4;border-radius:16rpx;background:#fff8ed}
.retry-render-card__title,.retry-render-card__desc{display:block}
.retry-render-card__title{color:#7b541d;font-size:23rpx;font-weight:700}
.retry-render-card__desc{display:-webkit-box;margin-top:6rpx;overflow:hidden;color:#9b6b24;font-size:18rpx;line-height:1.45;-webkit-box-orient:vertical;-webkit-line-clamp:2}
.retry-render-card__button{flex:none;height:58rpx;margin:0;padding:0 18rpx;border:1px solid #d8b06f;border-radius:12rpx;background:#fff;color:#8b5f23;font-size:20rpx;line-height:58rpx}
.retry-render-card__button::after{border:0}
.retry-render-card__button[disabled]{opacity:.5}
@keyframes ppt-preview-spin{to{transform:rotate(360deg)}}
.stepper-card{margin-bottom:18rpx;padding:14rpx 18rpx 10rpx;border:1px solid #e2e8f0;border-radius:16rpx;background:#fff;box-shadow:0 8rpx 24rpx rgba(30,50,90,.04)}
.stepper-card__head{display:flex;min-height:34rpx;align-items:center;justify-content:flex-end;margin-bottom:2rpx}
.stepper-card__head>text{color:#5265f5;font-size:20rpx;font-weight:700}
.step-scroll{width:100%;white-space:nowrap}
.stepper{position:relative;display:inline-flex;min-width:1280rpx;padding:8rpx 0 10rpx;box-sizing:border-box}
.stepper__track{position:absolute;left:68rpx;right:68rpx;top:35rpx;height:6rpx;overflow:hidden;border-radius:99rpx;background:#dfe5ee}
.stepper__track-value{position:relative;height:100%;border-radius:inherit;background:#5265f5;transition:width .25s ease}
.stepper__track-pulse{position:absolute;right:-7rpx;top:50%;width:14rpx;height:14rpx;border-radius:50%;background:#5265f5;box-shadow:0 0 0 8rpx rgba(82,101,245,.14);transform:translateY(-50%);animation:stepper-pulse 1.6s ease-in-out infinite}
.stepper__item{z-index:1;min-width:160rpx;align-items:center}
.stepper__item:not(:last-child)::after{display:none}
.stepper__marker{display:flex;height:66rpx;align-items:flex-start;justify-content:center}
.stepper__number{width:48rpx;height:48rpx;border:2rpx solid #d7dfeb;background:#f8fafc;color:#96a1b2;font-size:21rpx;box-shadow:0 0 0 8rpx #fff}
.stepper__item--done .stepper__number{border-color:#5265f5;background:#5265f5;color:#fff;box-shadow:0 0 0 8rpx #eef1ff}
.stepper__item--active .stepper__number{width:54rpx;height:54rpx;border:3rpx solid #5265f5;background:#eef1ff;color:#5265f5;box-shadow:0 0 0 8rpx #fff,0 8rpx 18rpx rgba(82,101,245,.14);animation:stepper-active 1.8s ease-in-out infinite}
.stepper__copy{display:flex;align-items:center;flex-direction:column;gap:5rpx;text-align:center}
.stepper__label{width:148rpx;margin-top:0;color:#8b95a7;font-size:19rpx;line-height:1.15;white-space:nowrap}
.stepper__state{color:#b3bac8;font-size:15rpx;line-height:1.1}
.stepper__item--done .stepper__label,.stepper__item--active .stepper__label{color:#4055e8;font-weight:700}
.stepper__item--active .stepper__state{color:#5265f5;font-weight:700}
.stepper__item--done .stepper__state{color:#718094}
@keyframes stepper-pulse{0%,100%{opacity:.55;box-shadow:0 0 0 4rpx rgba(82,101,245,.12)}50%{opacity:1;box-shadow:0 0 0 12rpx rgba(82,101,245,.2)}}
@keyframes stepper-active{0%,100%{transform:scale(1)}50%{transform:scale(1.04)}}

/* 视觉微调：保留原有结构，只把高饱和蓝紫、重阴影和脉冲感收敛为
   校园应用统一的低饱和灰蓝层级。 */
.ppt-flow{color:#1d1d1f}
.flow-heading__eyebrow,.stepper-card__head>text{color:#5c7a99}
.flow-heading__title{font-weight:700}
.panel,.stepper-card{border-color:#e5e7eb;border-radius:16rpx;box-shadow:none}
.primary-button{background:#5c7a99;box-shadow:none}
.secondary-button{border-color:#d9dee3;color:#5c7a99}
.single-action--floating,.bottom-actions{border-color:#e1e5e8;background:rgba(255,255,255,.98);box-shadow:0 -2rpx 16rpx rgba(40,55,70,.08);backdrop-filter:none}
.stepper__track-value{background:#5c7a99}
.stepper__track-pulse{display:none}
.stepper__item--active,.stepper__item--done{color:#5c7a99}
.stepper__item--active .stepper__number,.stepper__item--done .stepper__number{border-color:#5c7a99;background:#5c7a99;box-shadow:0 0 0 8rpx #eef2f4}
.stepper__item--active .stepper__number{animation:none}
.stepper__item--done .stepper__label,.stepper__item--active .stepper__label,.stepper__item--active .stepper__state{color:#5c7a99}
.outline-page-count,.outline-history-button,.outline-level-picker{border-color:#dce2e6;background:#f7f9fa;color:#5c7a99}
.outline-item{border-color:#e5e8eb;background:#fff;border-radius:13rpx}
.outline-item__order{background:#eef2f4;color:#5c7a99}
.outline-item__actions text,.template-category-tab--active{color:#5c7a99}
.template-category-tab--active{border-color:#a9b8c3;background:#f1f4f5}
.template-library-hero,.template-detail-card,.template-usage-card,.selected-template-strip{border-color:#e1e6e9;background:#fff;box-shadow:none}
.template-library-hero__stack{opacity:.42}
.template-library-hero__stack view{box-shadow:none;border-color:#d7e0e5}
.template-library-hero__stack view:nth-child(1),.template-library-hero__stack view:nth-child(2),.template-library-hero__stack view:nth-child(3){transform:none}
.template-library-card,.choice-card,.style-card,.export-choice{border-color:#e1e5e8;box-shadow:none}
.template-library-card--selected,.choice-card--selected,.style-card--selected,.export-choice--selected{border-color:#8fa4b3;background:#f8fafb;box-shadow:none}
.template-library-card__tag{background:#f0f3f4;color:#526a7b}
.template-library-card__actions,.template-library-card__selected{color:#5c7a99}
.segmented__item--active{color:#5c7a99;box-shadow:none}
.operation-feedback,.slide-task-card,.progress-panel,.result-summary,.format-status-panel{box-shadow:none}
.progress-ring{background:conic-gradient(#5c7a99 var(--progress),#e5e8eb 0);box-shadow:none}
.generation-item__dot--active{background:#5c7a99;box-shadow:0 0 0 7rpx #eef2f4}
.generation-item__dot--done{background:#6b9b7a}
.loading-dots text{background:#5c7a99;animation:none;opacity:.7}
.success-icon{background:#6b9b7a;box-shadow:none}
.layout-cover__action,.layout-fullscreen__use{background:#5c7a99;box-shadow:none}
.layout-fullscreen__loading-spinner{border-top-color:#5c7a99}

/* 流程条配色：降低当前节点的视觉压迫感，统一为低饱和灰蓝层级。 */
.stepper-card__head>text{color:#6f8497}
.stepper__track{background:#e4e8eb}
.stepper__track-value{background:#6f8497}
.stepper__item{color:#a3adb5}
.stepper__number{border-color:#d8dfe4;background:#fafbfb;color:#9aa5ad;box-shadow:0 0 0 8rpx #fff}
.stepper__item--done{color:#78909f}
.stepper__item--done .stepper__number{border-color:#8ea2af;background:#8ea2af;color:#fff;box-shadow:0 0 0 8rpx #f1f4f5}
.stepper__item--active{color:#5f7587}
.stepper__item--active .stepper__number{border-color:#6f8497;background:#6f8497;color:#fff;box-shadow:0 0 0 8rpx #eef2f4}
.stepper__item--active .stepper__label,.stepper__item--done .stepper__label{color:#5f7587}
.stepper__item--active .stepper__state{color:#6f8497}
.stepper__item--done .stepper__state{color:#8a99a3}
.stepper__item--clickable{cursor:pointer}
.stepper__item--clickable:active{opacity:.72}
.slide-preview-feed{height:820rpx;overflow:hidden;padding:2rpx 2rpx 24rpx;box-sizing:border-box}
.slide-preview-feed__item{padding-bottom:24rpx}
.slide-preview-feed__image{display:block;width:100%;height:auto;border:1px solid #dfe4ed;border-radius:12rpx;background:#fff;box-sizing:border-box}
.slide-preview-feed__fallback{width:100%;aspect-ratio:16/9}
.slide-preview-feed__meta{display:flex;align-items:center;justify-content:space-between;margin-top:8rpx;color:#7b8798;font-size:18rpx}
.slide-preview-feed__meta text:last-child{min-width:0;margin-left:16rpx;overflow:hidden;color:#5265f5;font-weight:600;text-overflow:ellipsis;white-space:nowrap}
.flow-heading{display:flex;align-items:center;justify-content:space-between}.flow-heading__copy{min-width:0}.flow-heading__actions{flex:none;margin-left:18rpx}.restart-generation-button{display:flex;height:56rpx;align-items:center;padding:0 22rpx;border:1px solid #cbd6e3;border-radius:16rpx;background:#fff;color:#5c7a99;font-size:21rpx;font-weight:600;line-height:1;box-shadow:none}.restart-generation-button:active{background:#f3f6f8;color:#4f6b84;transform:scale(.98)}
</style>
