<template>
  <view class="ppt-flow" :class="{ 'ppt-flow--floating-actions': hasFloatingActions }">
    <view class="flow-heading">
      <view class="flow-heading__copy">
        <text class="flow-heading__eyebrow">AI 复习资料 PPT</text>
        <text class="flow-heading__title">{{ stepMeta[currentStep - 1].title }}</text>
        <text class="flow-heading__desc">{{ stepMeta[currentStep - 1].description }}</text>
      </view>
      <view class="history-entry" @tap="openHistory('generation')">
        <view class="history-entry__icon"><text></text><text></text><text></text></view>
        <text>生成历史</text>
      </view>
    </view>

    <scroll-view class="step-scroll" scroll-x :show-scrollbar="false">
      <view class="stepper">
        <view
          v-for="item in stepMeta"
          :key="item.id"
          class="stepper__item"
          :class="{ 'stepper__item--active': currentStep === item.id, 'stepper__item--done': currentStep > item.id }"
        >
          <view class="stepper__number">
            <text v-if="currentStep <= item.id">{{ item.id }}</text>
            <text v-else class="stepper__check">✓</text>
          </view>
          <text class="stepper__label">{{ item.shortTitle }}</text>
        </view>
      </view>
    </scroll-view>

    <view v-if="modelConfigError" class="recover-card recover-card--warning">
      <view>
        <text class="recover-card__title">模型还没有准备好</text>
        <text class="recover-card__desc">{{ lastPptError || 'PPT 生成和思维导图、活动图使用同一套已测试模型配置。请先确认 API key 已配置并测试成功。' }}</text>
      </view>
      <button class="recover-card__button" @tap="openModelHelp">查看说明</button>
    </view>

    <view v-if="currentStep === 1" class="panel">
      <view v-if="templateEntryMode === 'library'" class="template-library-entry">
        <view class="template-library-hero">
          <view class="template-library-hero__copy">
            <text class="template-library-hero__eyebrow">Presenton 内置模板库</text>
            <text class="template-library-hero__title">先选模板，再生成可编辑 PPT</text>
            <text class="template-library-hero__desc">选择一套现成模板后，系统会按它的版式生成 PPTX、PDF 和页面预览。</text>
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
            <image v-if="template.thumbnailUrl" class="template-library-card__thumb" :src="template.thumbnailUrl" mode="aspectFill" />
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
                <text>{{ pptStyle === template.id ? '已选择' : '查看详情' }}</text>
              </view>
            </view>
          </view>
        </view>
        <view v-else class="template-empty">
          <text>{{ templateOptionsLoading ? '正在加载模板…' : '当前分类暂无模板' }}</text>
          <text v-if="!templateOptionsLoading" class="template-empty__retry" @tap="loadPptScenes(true)">重新加载模板</text>
        </view>

        <view class="single-action single-action--floating">
          <button class="primary-button" :disabled="!selectedTemplate" @tap="showTemplateDetail">查看模板详情</button>
        </view>
      </view>

      <view v-else-if="templateEntryMode === 'detail'" class="template-detail-entry">
        <view class="template-detail-card">
          <view class="template-detail-card__preview">
            <image v-if="selectedTemplate && selectedTemplate.thumbnailUrl" :src="selectedTemplate.thumbnailUrl" mode="aspectFill" />
            <view v-else class="template-detail-card__fallback">
              <text></text><text></text><text></text>
            </view>
          </view>
          <view class="template-detail-card__body">
            <text class="template-detail-card__eyebrow">{{ selectedTemplateCategoryLabel }}</text>
            <text class="template-detail-card__title">{{ selectedTemplateName }}</text>
            <text class="template-detail-card__desc">{{ selectedTemplateDescription }}</text>
            <view class="template-detail-card__stats">
              <view><text>{{ selectedTemplateLayoutCount }}</text><text>版式</text></view>
              <view><text>{{ selectedScene.label }}</text><text>场景</text></view>
            </view>
          </view>
        </view>

        <view class="template-detail-head">
          <view>
            <text class="template-detail-head__title">版式预览</text>
            <text class="template-detail-head__desc">生成时会从这些页面类型中匹配合适结构</text>
          </view>
          <text @tap="showTemplateLibrary">更换模板</text>
        </view>

        <view class="template-layout-grid">
          <view v-for="layout in selectedTemplateLayouts" :key="layout.id" class="template-layout-card">
            <view class="template-layout-preview" :class="`template-layout-preview--${layout.type}`">
              <text></text><text></text><text></text>
            </view>
            <text>{{ layout.name }}</text>
          </view>
        </view>

        <view class="bottom-actions">
          <view class="bottom-actions__buttons">
            <button class="secondary-button" @tap="showTemplateLibrary">返回模板库</button>
            <button class="primary-button" @tap="showTemplateUpload">使用此模板</button>
          </view>
        </view>
      </view>

      <view v-else class="template-upload-entry">
        <view class="selected-template-strip">
          <view class="selected-template-strip__preview">
            <image v-if="selectedTemplate && selectedTemplate.thumbnailUrl" :src="selectedTemplate.thumbnailUrl" mode="aspectFill" />
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
            <text class="product-hero__eyebrow">资料生成 · 模板渲染</text>
            <text class="product-hero__title">上传学习资料</text>
            <text class="product-hero__desc">资料上传后会进入大纲生成，再按所选模板生成可编辑 PPT。</text>
          </view>
          <view class="product-hero__slide">
            <text></text><text></text><text></text>
          </view>
        </view>

        <view class="field">
          <text class="field__label">学习场景</text>
          <picker :range="pptScenes" range-key="label" :value="selectedSceneIndex" @change="selectScene">
            <view class="select-field">
              <view>
                <text class="select-field__value">{{ selectedScene.label }}</text>
                <text class="select-field__hint">{{ selectedScene.description }}</text>
              </view>
              <text class="select-field__arrow">⌄</text>
            </view>
          </picker>
        </view>

        <view class="field">
          <text class="field__label">上传学习资料</text>
          <view v-if="!fileInfo" class="upload-box" @tap="chooseTxtFile">
            <view class="file-icon"><text>{{ fileKindLabel }}</text></view>
            <text class="upload-box__title">点击上传学习资料</text>
            <text class="upload-box__hint">{{ supportedSourceHint }}</text>
          </view>
          <view v-else class="file-row">
            <view class="file-row__icon">{{ fileKindLabel }}</view>
            <view class="file-row__main">
              <text class="file-row__name">{{ fileInfo.name }}</text>
              <view class="file-row__meta">
                <text>{{ fileInfo.sizeLabel }}</text>
                <text class="file-row__success">{{ sourceFileId ? '上传完成' : '读取完成' }}</text>
              </view>
            </view>
            <view class="file-row__actions">
              <text @tap.stop="chooseTxtFile">重传</text>
              <text class="file-row__delete" @tap.stop="removeFile">删除</text>
            </view>
          </view>
        </view>

        <view class="capability-strip">
          <view v-for="item in capabilityCards" :key="item.title" class="capability-card">
            <text class="capability-card__title">{{ item.title }}</text>
            <text class="capability-card__desc">{{ item.desc }}</text>
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

        <view class="single-action single-action--floating">
          <button class="primary-button" :disabled="!fileInfo" @tap="goNext">下一步</button>
        </view>
      </view>
    </view>

    <view v-else-if="currentStep === 2" class="panel">
      <view class="mode-intro">
        <text class="mode-intro__title">先决定大纲来源</text>
        <text class="mode-intro__desc">这一步决定 AI 是重新组织资料，还是保留原始章节顺序。后面仍然可以手动调整每一页。</text>
      </view>
      <view
        v-for="mode in outlineModes"
        :key="mode.id"
        class="choice-card choice-card--large"
        :class="{ 'choice-card--selected': outlineMode === mode.id }"
        @tap="outlineMode = mode.id"
      >
        <view class="choice-card__icon" :class="`choice-card__icon--${mode.id}`">
          <view class="line-icon">
            <text></text><text></text><text></text>
          </view>
        </view>
        <view class="choice-card__body">
          <text class="choice-card__title">{{ mode.name }}</text>
          <text class="choice-card__desc">{{ mode.description }}</text>
          <text class="choice-card__fit">{{ mode.fit }}</text>
        </view>
        <view class="radio-dot" :class="{ 'radio-dot--selected': outlineMode === mode.id }">
          <text v-if="outlineMode === mode.id">✓</text>
        </view>
      </view>
      <view class="bottom-actions">
        <view class="bottom-actions__buttons">
          <button class="secondary-button" @tap="goPrevious">上一步</button>
          <button class="primary-button" :disabled="apiBusy" @tap="prepareOutline">{{ apiBusy ? '正在生成大纲…' : '下一步' }}</button>
        </view>
      </view>
    </view>

    <view v-else-if="currentStep === 3" class="panel outline-panel">
      <view class="editor-toolbar">
        <view>
          <text class="editor-toolbar__title">PPT 大纲</text>
          <text class="editor-toolbar__desc">{{ outlineMode === 'ai_outline' ? 'AI 生成大纲草稿，可继续调整' : '已按原资料层级识别，可继续调整' }}</text>
        </view>
        <view class="outline-history-button" @tap="openHistory('outline')">大纲记录</view>
      </view>

      <view class="outline-name-field">
        <text>大纲名称</text>
        <input v-model="outlineName" :maxlength="60" placeholder="请输入大纲名称" />
      </view>

      <view class="outline-list">
        <view v-for="(item, index) in outlineItems" :key="item.id" class="outline-item">
          <view class="outline-item__order">{{ index + 1 }}</view>
          <view class="outline-item__main">
            <view class="outline-item__level-row">
              <view
                v-for="level in outlineLevels"
                :key="level.value"
                class="outline-level"
                :class="{ 'outline-level--active': item.level === level.value }"
                @tap="item.level = level.value"
              >{{ level.label }}</view>
            </view>
            <input v-model="item.title" :maxlength="80" placeholder="输入大纲标题" />
          </view>
          <view class="outline-item__actions">
            <text :class="{ disabled: index === 0 }" @tap="moveOutlineItem(index, -1)">↑</text>
            <text :class="{ disabled: index === outlineItems.length - 1 }" @tap="moveOutlineItem(index, 1)">↓</text>
            <text class="outline-item__delete" @tap="removeOutlineItem(index)">×</text>
          </view>
        </view>
      </view>

      <button class="add-outline-button" @tap="addOutlineItem">＋ 添加大纲项</button>
      <view class="outline-save-tip">
        <text>当前大纲会独立保存，之后可从“大纲记录”再次使用</text>
        <text v-if="outlineSavedAt">已保存 {{ outlineSavedAt }}</text>
      </view>
      <view class="bottom-actions bottom-actions--three">
        <view class="bottom-actions__buttons">
          <button class="secondary-button" @tap="goPrevious">上一步</button>
          <button class="secondary-button" @tap="saveOutlineSnapshot(true)">保存大纲</button>
          <button class="primary-button" :disabled="!validOutlineItems.length" @tap="confirmOutline">下一步</button>
        </view>
      </view>
    </view>

    <view v-else-if="currentStep === 4" class="panel">
      <view class="scene-summary">
        <text class="scene-summary__label">学习场景</text>
        <text class="scene-summary__value">{{ selectedScene.label }}</text>
      </view>

      <view v-if="selectedTemplate" class="template-usage-card">
        <view class="template-usage-card__main">
          <view class="template-usage-card__preview">
            <image v-if="selectedTemplate.thumbnailUrl" :src="selectedTemplate.thumbnailUrl" mode="aspectFill" />
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
          <view><text>{{ pageCount }}</text><text>预计页数</text></view>
          <view><text>{{ selectedTemplateCategoryLabel }}</text><text>模板分类</text></view>
        </view>
        <view class="template-usage-plan">
          <view v-for="item in templateUsageItems" :key="item.label" class="template-usage-plan__item" :class="{ 'template-usage-plan__item--muted': !item.enabled }">
            <text>{{ item.label }}</text>
            <text>{{ item.enabled ? '使用' : '不使用' }}</text>
          </view>
        </view>
      </view>

      <view class="settings-section">
        <view class="settings-section__head">
          <text class="settings-section__title">预计页数</text>
          <view><text class="page-number">{{ pageCount }}</text><text class="settings-section__unit"> 页</text></view>
        </view>
        <slider :value="pageCount" :min="5" :max="30" activeColor="#4e61f6" backgroundColor="#dce2ef" block-color="#ffffff" :block-size="22" @changing="setPageCount" @change="setPageCount" />
        <view class="range-label"><text>5</text><text>30</text></view>
        <text class="settings-hint">最终页数可能根据资料内容进行小幅调整</text>
      </view>

      <view class="generation-plan-card">
        <view class="generation-plan-card__head">
          <view>
            <text class="generation-plan-card__title">生成计划</text>
            <text class="generation-plan-card__desc">确认后会先生成完整页面 JSON，再进入可编辑页面。</text>
          </view>
          <text>{{ validOutlineItems.length }} 页大纲</text>
        </view>
        <view class="generation-plan-card__grid">
          <view><text>模板</text><text>{{ selectedTemplateName }}</text></view>
          <view><text>内容</text><text>{{ currentContentLevel.name }}</text></view>
          <view><text>配图</text><text>{{ selectedImageModeLabel }}</text></view>
          <view><text>资料</text><text>{{ sourceFileId ? '服务端解析' : '本地文本' }}</text></view>
          <view><text>版式库</text><text>{{ selectedTemplateLayoutCount }} 种</text></view>
          <view><text>模板类型</text><text>{{ selectedTemplateCategoryLabel }}</text></view>
        </view>
      </view>

      <view class="settings-section">
        <view class="template-section-head">
          <view>
            <text class="settings-section__title">PPT 模板</text>
            <text v-if="selectedTemplate" class="template-section-head__selected">已选择：{{ selectedTemplate.name }}</text>
          </view>
          <view v-if="pptStyles.length" class="template-section-head__action" @tap="templateExpanded = !templateExpanded">
            <text>{{ pptStyles.length }} 套</text>
            <text>{{ templateExpanded ? '收起' : '展开全部' }}</text>
          </view>
        </view>
        <scroll-view
          v-if="pptStyles.length"
          class="style-scroll"
          :class="{ 'style-scroll--expanded': templateExpanded }"
          :scroll-x="!templateExpanded"
          :show-scrollbar="false"
        >
          <view class="style-list">
            <view
              v-for="style in pptStyles"
              :key="style.id"
              class="style-card"
              :class="{ 'style-card--selected': pptStyle === style.id }"
              @tap="pptStyle = style.id"
            >
              <image v-if="style.thumbnailUrl" class="style-card__preview style-card__preview-image" :src="style.thumbnailUrl" mode="aspectFill" />
              <view v-else class="style-card__preview" :class="`style-card__preview--${style.id}`">
                <view class="mini-slide__title"></view>
                <view class="mini-slide__line mini-slide__line--long"></view>
                <view class="mini-slide__line"></view>
                <view class="mini-slide__shape"></view>
              </view>
              <text class="style-card__name">{{ style.name }}</text>
              <text class="style-card__desc">{{ style.description }}</text>
              <text v-if="style.layoutCount" class="style-card__layouts">{{ style.layoutCount }} 种页面布局</text>
              <view v-if="pptStyle === style.id" class="style-card__check">✓</view>
            </view>
          </view>
        </scroll-view>
        <view v-if="!pptStyles.length" class="template-empty">
          <text>{{ templateOptionsLoading ? '正在加载模板…' : '模板加载失败，请检查 PPT 服务' }}</text>
          <text v-if="!templateOptionsLoading" class="template-empty__retry" @tap="loadPptScenes(true)">重新加载</text>
        </view>
        <view v-if="pptStyles.length && !templateExpanded" class="template-scroll-hint" @tap="templateExpanded = true">
          <text>左右滑动查看更多模板</text>
          <text>展开全部 ›</text>
        </view>
        <view v-if="selectedTemplateLayouts.length" class="template-match-preview">
          <view class="template-match-preview__head">
            <text>版式匹配范围</text>
            <text>{{ selectedTemplateLayouts.length }} 类页面</text>
          </view>
          <view class="template-match-preview__grid">
            <view v-for="layout in selectedTemplateLayouts.slice(0, 4)" :key="layout.id" class="template-match-preview__item">
              <view class="template-match-preview__canvas" :class="`template-match-preview__canvas--${layout.type}`">
                <text></text><text></text><text></text>
              </view>
              <text>{{ layout.name }}</text>
            </view>
          </view>
        </view>
      </view>

      <view class="settings-section">
        <text class="settings-section__title settings-section__title--block">内容详细程度</text>
        <view class="segmented">
          <view v-for="level in contentLevels" :key="level.id" class="segmented__item" :class="{ 'segmented__item--active': contentLevel === level.id }" @tap="contentLevel = level.id">
            <text>{{ level.name }}</text>
          </view>
        </view>
        <text class="settings-hint">{{ currentContentLevel.description }}</text>
      </view>

      <view class="settings-section settings-section--split">
        <view class="switch-list">
          <text class="settings-section__title settings-section__title--block">页面组成</text>
          <view v-for="option in pageOptions" :key="option.key" class="switch-row">
            <text>{{ option.label }}</text>
            <switch :checked="settings[option.key]" color="#4e61f6" @change="toggleSetting(option.key, $event)" />
          </view>
          <view class="switch-row switch-row--visuals">
            <view>
              <text class="switch-row__title">生成辅助配图</text>
              <text class="switch-row__desc">匹配图标、流程图和结构图</text>
            </view>
            <switch :checked="settings.includeVisuals" color="#4e61f6" @change="toggleSetting('includeVisuals', $event)" />
          </view>
          <view class="visual-mode-row">
            <text class="switch-row__title">配图生成方式</text>
            <view class="segmented visual-mode-segmented">
              <view v-for="mode in imageModes" :key="mode.id" class="segmented__item" :class="{ 'segmented__item--active': settings.imageMode === mode.id }" @tap="settings.imageMode = mode.id">
                <text>{{ mode.name }}</text>
              </view>
            </view>
            <text class="switch-row__desc">先留空可在生成后上传替换；选择 AI 才会调用图片模型。</text>
          </view>
        </view>
        <view class="effect-preview">
          <text class="effect-preview__label">效果预览</text>
          <view class="effect-slide" :class="`effect-slide--${pptStyle}`">
            <text class="effect-slide__eyebrow">REVIEW MATERIAL</text>
            <text class="effect-slide__title">复习资料 PPT</text>
            <view class="effect-slide__line"></view>
            <view class="effect-slide__blocks"><text></text><text></text><text></text></view>
          </view>
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
        <text v-if="slideGenerationProcessingLabel" class="slide-task-card__processing">并行处理中：{{ slideGenerationProcessingLabel }}</text>
      </view>

      <view class="bottom-actions">
        <view class="bottom-actions__buttons">
          <button class="secondary-button" @tap="goPrevious">上一步</button>
          <button class="primary-button" :disabled="apiBusy" @tap="prepareSlides">{{ apiBusy ? '正在生成页面…' : '编辑页面内容' }}</button>
        </view>
      </view>
    </view>

    <view v-else-if="currentStep === 5" class="panel page-editor-panel">
      <view class="editor-toolbar">
        <view>
          <text class="editor-toolbar__title">逐页编辑</text>
          <text class="editor-toolbar__desc">正式生成前，确认每页标题、内容和提示词</text>
        </view>
        <text class="page-editor-count">{{ activeSlideIndex + 1 }} / {{ slides.length }}</text>
      </view>

      <view class="slide-readiness">
        <view v-for="item in slideReadiness" :key="item.label" class="slide-readiness__item">
          <text>{{ item.value }}</text>
          <text>{{ item.label }}</text>
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
            @tap="activeSlideIndex = index"
          >
            <text>{{ index + 1 }}</text>
            <text>{{ slide.title || '未命名页面' }}</text>
          </view>
        </view>
      </scroll-view>

      <view v-if="activeSlide" class="slide-editor">
        <view class="slide-editor__preview" :class="`slide-editor__preview--${pptStyle}`">
          <text class="slide-editor__preview-index">{{ String(activeSlideIndex + 1).padStart(2, '0') }}</text>
          <text class="slide-editor__preview-title">{{ activeSlide.title || '未命名页面' }}</text>
          <text class="slide-editor__preview-content">{{ activeSlide.content || '暂未填写页面内容' }}</text>
          <view class="slide-editor__preview-decor"></view>
        </view>

        <view class="edit-field">
          <view class="edit-field__label"><text>页面标题</text><text>{{ activeSlide.title.length }}/80</text></view>
          <input v-model="activeSlide.title" :maxlength="80" placeholder="请输入页面标题" @input="applyManualTextOverride" />
        </view>
        <view class="edit-field">
          <view class="edit-field__label"><text>页面内容</text><text>支持修改单页内容</text></view>
          <textarea v-model="activeSlide.content" :maxlength="1200" auto-height placeholder="请输入本页需要展示的知识点和说明" @input="applyManualTextOverride" />
        </view>
        <view class="prompt-field prompt-field--shared">
          <view class="prompt-field__head">
            <view><text>公共提示词</text><text>所有页面共同生效</text></view>
            <view class="prompt-badge">全局</view>
          </view>
          <textarea v-model="sharedPrompt" :maxlength="800" auto-height placeholder="例如：保持学习资料准确，使用简洁排版，突出关键概念" />
          <text class="prompt-field__hint">在任意页面修改后，会同步到全部页面。</text>
        </view>
        <view class="prompt-field">
          <view class="prompt-field__head">
            <view><text>单页私有提示词</text><text>仅对第 {{ activeSlideIndex + 1 }} 页生效</text></view>
            <view class="prompt-badge prompt-badge--private">私有</view>
          </view>
          <textarea v-model="activeSlide.privatePrompt" :maxlength="800" auto-height placeholder="例如：本页使用左右对比布局，增加函数图像示意" />
          <text class="prompt-field__hint">私有提示词用于补充本页版式、配图和强调重点。</text>
        </view>

        <view class="slide-editor__navigation">
          <button class="secondary-button" :disabled="activeSlideIndex === 0" @tap="activeSlideIndex -= 1">上一页</button>
          <button class="secondary-button" :disabled="activeSlideIndex === slides.length - 1" @tap="activeSlideIndex += 1">下一页</button>
        </view>
      </view>

      <view class="bottom-actions">
        <view class="bottom-actions__buttons">
          <button class="secondary-button" @tap="goPrevious">返回设置</button>
          <button class="primary-button" :disabled="apiBusy" @tap="startGeneration">{{ apiBusy ? '正在创建任务…' : '确认并生成' }}</button>
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
      <view v-if="taskResult" class="task-runtime-card">
        <view class="task-runtime-card__row"><text>任务状态</text><text>{{ taskResult.status || 'running' }}</text></view>
        <view class="task-runtime-card__row"><text>当前阶段</text><text>{{ taskResult.stage || activeGenerationStep.id }}</text></view>
        <view v-if="taskResult.currentSlide || taskResult.totalSlides" class="task-runtime-card__row">
          <text>页面进度</text><text>{{ taskResult.currentSlide || 0 }} / {{ taskResult.totalSlides || pageCount }}</text>
        </view>
        <text class="task-runtime-card__message">{{ taskResult.message || activeGenerationStep.description }}</text>
      </view>
      <button class="secondary-button secondary-button--full" @tap="cancelGeneration">取消生成</button>
    </view>

    <view v-else-if="currentStep === 7" class="panel result-panel">
      <view class="success-hero">
        <view class="success-icon">✓</view>
        <text class="success-hero__title">PPT 生成完成</text>
        <text class="success-hero__desc">你的复习资料 PPT 已经准备好了</text>
      </view>

      <view class="result-summary">
        <view class="result-summary__name">
          <view class="result-summary__file-icon">P</view>
          <view><text>{{ resultName }}</text><text>复习资料 · {{ pageCount }} 页</text></view>
        </view>
        <view class="result-summary__meta"><text>生成完成</text><text>刚刚</text></view>
      </view>
      <view v-if="generationWarnings.length" class="generation-warning">
        <text>{{ generationWarnings[0] }}</text>
      </view>
      <view v-if="formatErrorList.length" class="generation-warning generation-warning--error">
        <text>{{ formatErrorList[0].label }}：{{ formatErrorList[0].message }}</text>
      </view>

      <view class="format-status-panel">
        <view class="format-status-panel__head">
          <view>
            <text class="format-status-panel__title">导出文件</text>
            <text class="format-status-panel__desc">{{ exportStatusCopy }}</text>
          </view>
          <text>{{ availableExportFormats.length }} / {{ exportFormats.length }}</text>
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
        <view class="slide-grid">
          <view v-for="slide in visibleSlides" :key="slide" class="slide-thumb" @tap="activeSlideIndex = slide - 1; openSlidePreview(slide)">
            <view class="slide-thumb__canvas" :class="[`slide-thumb__canvas--${pptStyle}`, { 'slide-thumb__canvas--cover': slide === 1 }]">
              <image v-if="previewImages[slide]" class="slide-thumb__image" :src="previewImages[slide]" mode="aspectFill" />
              <text class="slide-thumb__number">{{ String(slide).padStart(2, '0') }}</text>
              <template v-if="!previewImages[slide]">
                <text class="slide-thumb__title">{{ slideTitle(slide) }}</text>
                <view class="slide-thumb__lines"><text></text><text></text><text></text></view>
                <view class="slide-thumb__decor"></view>
              </template>
            </view>
            <text class="slide-thumb__page">{{ slide }}</text>
          </view>
        </view>
        <button v-if="pageCount > 6" class="text-button" @tap="showAllSlides = !showAllSlides">{{ showAllSlides ? '收起页面' : `查看全部 ${pageCount} 页` }}</button>
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
          <view class="download-ready__file">{{ exportFormat === 'pptx' ? 'P' : 'PDF' }}</view>
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
    </view>
    <view v-if="operationFeedback.active" class="operation-banter">
      <text :key="operationBanterIndex" class="operation-banter__text">{{ currentOperationBanter }}</text>
      <text class="operation-banter__status">请求仍在处理中，请不要关闭或刷新页面</text>
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
  createPptSlidesTask,
  downloadPptPreview,
  downloadPptTaskFile,
  downloadPptTemplateThumbnail,
  generatePptOutline,
  generatePptSlides,
  getPptOptions,
  getPptTask,
  replacePptSlideImage,
  streamPptTask,
  uploadPptSourceFile
} from '@/api/ppt.js'

export default {
  name: 'AiPresentationFlow',
  props: {
    initialTopic: { type: String, default: '' }
  },
  data() {
    return {
      currentStep: 1,
      scene: 'review',
      pptScenes: [
        { value: 'review', label: '复习资料', description: '将学习资料整理成结构清晰的复习 PPT', enabled: true, default: true }
      ],
      fileInfo: null,
      fileContent: '',
      sourceFileId: '',
      enhancedEngineAvailable: false,
      previewExpanded: false,
      outlineMode: 'ai_outline',
      outlineName: '',
      outlineItems: [],
      outlineDocument: null,
      layoutMarkdown: '',
      outlineSavedAt: '',
      outlineLevels: [
        { value: 1, label: '章节' },
        { value: 2, label: '小节' },
        { value: 3, label: '知识点' }
      ],
      pageCount: 15,
      pptStyle: 'general',
      templateEntryMode: 'library',
      templateCategory: 'all',
      templateCategories: [
        { id: 'all', name: '全部模板' },
        { id: 'study', name: '课堂复习' },
        { id: 'report', name: '汇报展示' }
      ],
      templateExpanded: false,
      templateOptionsLoading: false,
      contentLevel: 'standard',
      slides: [],
      activeSlideIndex: 0,
      sharedPrompt: '保持学习资料内容准确，版面简洁清晰，突出核心知识点，使用适合学生复习的视觉层级。',
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
      generationRunId: 0,
      slideGenerationRunId: 0,
      taskId: '',
      taskResult: null,
      previewImages: {},
      generationWarnings: [],
      modelConfigError: false,
      lastPptError: '',
      apiBusy: false,
      operationFeedback: { active: false, progress: 0, message: '', detail: '' },
      operationFeedbackTimer: null,
      operationBanterIndex: 0,
      operationBanters: [
        '程序员的头发正在替你加班，模型也没闲着',
        '知识点正在排队进大纲，一个都不想落下',
        'AI 正在和长文本认真较劲，再给它一点时间',
        '大纲正在排兵布阵，很快就能交卷',
        '内容有点多，模型正在一页一页认真打包',
        '先喝口水，精彩的大纲还在路上'
      ],
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
        { id: 1, shortTitle: '选模板', title: '选择 PPT 模板', description: '先选择可直接套用的模板，再上传资料生成 PPT' },
        { id: 2, shortTitle: '大纲来源', title: '选择大纲来源', description: '选择 AI 重新整理，或沿用资料原有大纲' },
        { id: 3, shortTitle: '编辑大纲', title: '编辑 PPT 大纲', description: '确认内容结构，并将本次大纲独立保存' },
        { id: 4, shortTitle: '设置 PPT', title: '设置 PPT', description: '设置 PPT 的页数和展示效果' },
        { id: 5, shortTitle: '编辑页面', title: '编辑页面内容', description: '逐页调整内容、公共提示词和单页提示词' },
        { id: 6, shortTitle: '生成进度', title: '正在生成 PPT', description: 'AI 正在整理你的学习资料，请稍候' },
        { id: 7, shortTitle: '生成结果', title: 'PPT 生成完成', description: '预览生成效果并确认导出' },
        { id: 8, shortTitle: '导出下载', title: '导出下载', description: '选择需要导出的文件格式' }
      ],
      outlineModes: [
        { id: 'ai_outline', name: 'AI 生成复习大纲', description: 'AI 分析资料内容，重新整理知识结构，生成适合复习的 PPT 大纲。', fit: '适合内容零散或没有明确结构的资料' },
        { id: 'original_outline', name: '使用原内容作为大纲', description: '按照上传资料原有的内容顺序和标题层级生成 PPT。', fit: '适合已经整理好大纲的资料' }
      ],
      capabilityCards: [
        { title: '资料解析', desc: 'TXT 直读，Office 与 PDF 走服务端解析' },
        { title: '模板渲染', desc: '使用内置 Presenton 模板输出页面' },
        { title: '可导出', desc: '生成 PPTX、PDF 和页面预览' }
      ],
      pptStyles: [],
      contentLevels: [
        { id: 'concise', name: '精简', description: '每页展示较少文字，主要保留核心知识点。' },
        { id: 'standard', name: '标准', description: '同时展示知识点和必要说明，适合常规复习。' },
        { id: 'detailed', name: '详细', description: '保留更多原始资料内容和解释。' }
      ],
      pageOptions: [
        { key: 'includeCover', label: '包含封面' },
        { key: 'includeCatalog', label: '包含目录' },
        { key: 'includeSection', label: '包含章节页' },
        { key: 'includeSummary', label: '包含总结页' }
      ],
      generationSteps: [
        { id: 'analyzing', activeText: '正在分析资料', description: '识别资料主题、章节和核心知识点', doneText: '资料分析完成' },
        { id: 'outline', activeText: '正在生成大纲', description: '整理章节层级和页面结构', doneText: '大纲生成完成' },
        { id: 'writing', activeText: '正在撰写内容', description: '生成每页标题、知识点和必要说明', doneText: '页面内容生成完成' },
        { id: 'layout', activeText: '正在匹配版式', description: '为不同内容选择合适的页面布局', doneText: '版式匹配完成' },
        { id: 'visuals', activeText: '正在生成配图', description: '匹配图标、流程图和结构图', doneText: '配图生成完成' },
        { id: 'building', activeText: '正在制作文件', description: '生成页面预览和导出文件', doneText: 'PPT 文件制作完成' }
      ],
      exportFormats: [
        { id: 'pptx', icon: 'P', name: 'PowerPoint 格式', description: '可使用 PowerPoint 或 WPS 打开并继续编辑。' },
        { id: 'pdf', icon: 'PDF', name: 'PDF 格式', description: '适合查看和打印，不支持编辑。' }
      ]
    }
  },
  computed: {
    validOutlineItems() {
      return this.outlineItems.filter(item => String(item.title || '').trim())
    },
    activeSlide() {
      return this.slides[this.activeSlideIndex] || null
    },
    currentHistory() {
      return this.historyTab === 'outline' ? this.outlineHistory : this.generationHistory
    },
    selectedSceneIndex() {
      const index = this.pptScenes.findIndex(item => item.value === this.scene)
      return index >= 0 ? index : 0
    },
    selectedScene() {
      return this.pptScenes[this.selectedSceneIndex] || this.pptScenes[0]
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
      const total = this.selectedTemplateLayoutCount
      const baseLayouts = [
        { id: 'cover', name: '封面', type: 'cover' },
        { id: 'catalog', name: '目录', type: 'catalog' },
        { id: 'content', name: '正文', type: 'content' },
        { id: 'focus', name: '重点', type: 'focus' },
        { id: 'visual', name: '图文', type: 'visual' },
        { id: 'summary', name: '总结', type: 'summary' }
      ]
      if (!total) return baseLayouts
      return baseLayouts.slice(0, Math.min(baseLayouts.length, Math.max(4, total)))
    },
    templateUsageItems() {
      return [
        { label: '封面页', enabled: this.settings.includeCover },
        { label: '目录页', enabled: this.settings.includeCatalog },
        { label: '章节页', enabled: this.settings.includeSection },
        { label: '总结页', enabled: this.settings.includeSummary }
      ]
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
    slideReadiness() {
      const total = this.slides.length
      const withUi = this.slides.filter(slide => slide.ui && typeof slide.ui === 'object' && Object.keys(slide.ui).length).length
      const imageReady = this.slides.filter(slide => ['generated', 'uploaded', 'placeholder'].includes(String(slide.imageStatus || ''))).length
      return [
        { label: '页面', value: total },
        { label: '模板树', value: withUi },
        { label: '配图状态', value: imageReady }
      ]
    },
    supportedSourceHint() {
      return this.enhancedEngineAvailable
        ? '支持 TXT、PDF、Word、PPT 和表格文件，最大 25MB'
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
      return [1, 2, 3, 4, 5, 7].includes(this.currentStep)
    },
    currentOperationBanter() {
      return this.operationBanters[this.operationBanterIndex] || this.operationBanters[0]
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
    currentContentLevel() {
      return this.contentLevels.find(item => item.id === this.contentLevel) || this.contentLevels[1]
    },
    activeGenerationIndex() {
      if (this.progress >= 100) return this.generationSteps.length - 1
      return Math.min(Math.floor(this.progress / (100 / this.generationSteps.length)), this.generationSteps.length - 1)
    },
    activeGenerationStep() {
      return this.generationSteps[this.activeGenerationIndex]
    },
    progressMessage() {
      if (this.activeGenerationStep.id === 'writing') {
        const current = Math.max(1, Math.min(this.pageCount, Math.round((this.progress / 100) * this.pageCount)))
        return `正在生成第 ${current} 页，共 ${this.pageCount} 页`
      }
      return this.activeGenerationStep.description
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
      if (!this.availableExportFormats.length) return '本次任务未返回可下载文件'
      if (this.formatErrorList.length) return '部分格式已生成，可先下载可用文件'
      return 'PPTX 与 PDF 均已生成'
    },
    resultName() {
      const name = this.fileInfo?.name || '复习资料.txt'
      return name.replace(/\.(?:txt|pdf|docx?|pptx?|xlsx?)$/i, '') || '复习资料'
    },
    visibleSlides() {
      const length = this.showAllSlides ? this.pageCount : Math.min(6, this.pageCount)
      return Array.from({ length }, (_, index) => index + 1)
    },
    downloadFileName() {
      return `${this.resultName}_复习资料PPT.${this.exportFormat}`
    }
  },
  created() {
    this.restoreHistories()
    this.loadPptScenes()
  },
  beforeDestroy() {
    this.clearTimers()
  },
  methods: {
    async loadPptScenes(forceRefresh = false) {
      this.templateOptionsLoading = true
      try {
        const options = await getPptOptions({ forceRefresh })
        this.enhancedEngineAvailable = Boolean(options.enhancedEngineAvailable)
        const scenes = (options.scenes || []).filter(item => item?.enabled !== false && item?.value && item?.label)
        if (scenes.length) {
          this.pptScenes = scenes
          if (!scenes.some(item => item.value === this.scene)) {
            this.scene = (scenes.find(item => item.default || item.defaultOption) || scenes[0]).value
          }
        }
        const templates = (options.templates || []).filter(item => item?.id && item?.name)
        if (templates.length) {
          this.pptStyles = await Promise.all(templates.map(async item => {
            let thumbnailUrl = ''
            if (item.thumbnailUrl) {
              try {
                thumbnailUrl = await downloadPptTemplateThumbnail(item.id)
              } catch (error) {}
            }
            return {
              id: String(item.id),
              name: String(item.name),
              description: String(item.description || `${Number(item.layoutCount || 0)} 种页面布局`),
              layoutCount: Number(item.layoutCount || 0),
              thumbnailUrl,
              default: Boolean(item.default || item.defaultOption)
            }
          }))
          if (!this.pptStyles.some(item => item.id === this.pptStyle)) {
            this.pptStyle = (this.pptStyles.find(item => item.default) || this.pptStyles[0]).id
          }
        }
      } catch (error) {
        this.pptStyles = []
      } finally {
        this.templateOptionsLoading = false
      }
    },
    selectScene(event) {
      const selected = this.pptScenes[Number(event?.detail?.value || 0)]
      if (selected) this.scene = selected.value
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
    selectTemplateCategory(id) {
      this.templateCategory = id
    },
    selectPptTemplate(id) {
      if (!id) return
      this.pptStyle = id
      this.templateEntryMode = 'detail'
    },
    showTemplateLibrary() {
      this.templateEntryMode = 'library'
    },
    showTemplateDetail() {
      if (!this.selectedTemplate && this.pptStyles.length) {
        this.pptStyle = this.pptStyles[0].id
      }
      this.templateEntryMode = 'detail'
    },
    showTemplateUpload() {
      if (!this.selectedTemplate && this.pptStyles.length) {
        this.pptStyle = this.pptStyles[0].id
      }
      this.templateEntryMode = 'upload'
    },
    chooseTxtFile() {
      if (typeof uni.chooseFile === 'function') {
        uni.chooseFile({
          count: 1,
          extension: this.enhancedEngineAvailable
            ? ['txt', 'pdf', 'doc', 'docx', 'ppt', 'pptx', 'xls', 'xlsx']
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
            ? ['txt', 'pdf', 'doc', 'docx', 'ppt', 'pptx', 'xls', 'xlsx']
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
            this.sourceFileId = String(uploaded.fileId)
            this.fileContent = ''
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
        this.fileContent = content
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
      this.fileInfo = null
      this.fileContent = ''
      this.sourceFileId = ''
      this.previewExpanded = false
      this.outlineItems = []
      this.slides = []
    },
    goNext() {
      if (this.currentStep === 1 && (this.templateEntryMode !== 'upload' || !this.fileInfo)) return
      this.currentStep = Math.min(this.stepMeta.length, this.currentStep + 1)
    },
    goPrevious() {
      if (this.currentStep === 1 && this.templateEntryMode === 'upload') {
        this.templateEntryMode = 'detail'
        return
      }
      if (this.currentStep === 1 && this.templateEntryMode === 'detail') {
        this.templateEntryMode = 'library'
        return
      }
      this.currentStep = Math.max(1, this.currentStep - 1)
    },
    setPageCount(event) {
      this.pageCount = Number(event?.detail?.value || 15)
    },
    toggleSetting(key, event) {
      this.settings[key] = Boolean(event?.detail?.value)
    },
    async prepareOutline() {
      if (!this.fileInfo) return
      if (this.outlineMode === 'original_outline') {
        const detected = this.detectOutlineItems()
        this.outlineItems = detected.length ? detected : [
          this.createOutlineItem(this.resultName, 1),
          this.createOutlineItem('资料核心内容', 2),
          this.createOutlineItem('复习总结', 2)
        ]
        this.outlineDocument = { title: this.resultName, items: this.outlineItems }
        this.outlineName = `${this.resultName}大纲`
        this.outlineSavedAt = ''
        this.currentStep = 3
        return
      }
      this.apiBusy = true
      this.modelConfigError = false
      this.lastPptError = ''
      this.startOperationFeedback('outline')
      try {
        const response = await generatePptOutline({
          sourceName: this.fileInfo.name,
          sourceContent: this.fileContent,
          sourceFileId: this.sourceFileId,
          outlineMode: this.outlineMode,
          pageCount: this.pageCount,
          scene: this.scene,
          topic: this.initialTopic || this.resultName
        })
        this.updateOperationFeedback(92, '正在校验并转换大纲格式', '即将进入可编辑大纲')
        const outline = this.responseData(response)
        const items = Array.isArray(outline.items) ? outline.items : []
        if (!items.length) throw new Error('AI 未返回可编辑的大纲')
        this.outlineItems = items.map((item, index) => ({
          ...item,
          id: item.id || `outline-${Date.now()}-${index}`,
          level: Number(item.level || 1),
          title: String(item.title || '')
        }))
        this.outlineDocument = { ...outline, items: this.outlineItems }
        this.outlineName = `${outline.title || this.resultName}大纲`
        this.outlineSavedAt = ''
        this.currentStep = 3
      } catch (error) {
        this.handlePptError(error, '大纲生成失败')
      } finally {
        this.apiBusy = false
        this.stopOperationFeedback()
      }
    },
    detectOutlineItems() {
      const lines = String(this.fileContent || '').split(/\r?\n/).map(line => line.trim()).filter(Boolean)
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
    createOutlineItem(title = '', level = 2) {
      return { id: `outline-${Date.now()}-${Math.random().toString(16).slice(2)}`, title, level }
    },
    addOutlineItem() {
      this.outlineItems.push(this.createOutlineItem('', 2))
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
      if (!this.validOutlineItems.length) return
      this.saveOutlineSnapshot(false)
      this.currentStep = 4
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
      if (!outlines.length) {
        this.currentStep = 3
        return
      }
      const runId = ++this.slideGenerationRunId
      this.apiBusy = true
      this.modelConfigError = false
      this.lastPptError = ''
      this.slideGenerationSnapshot = null
      this.startOperationFeedback('slides')
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
          settings: this.buildSettings(),
          sharedPrompt: this.sharedPrompt
        })
        const created = this.responseData(response)
        this.slideGenerationTaskId = String(created.taskId || '')
        if (!this.slideGenerationTaskId) throw new Error('服务端未返回逐页生成任务编号')
        await this.followSlideGenerationTask(runId)
        if (runId !== this.slideGenerationRunId) return
        const result = this.responseData(await getPptTask(this.slideGenerationTaskId))
        this.generationWarnings = Array.isArray(result.warnings) ? result.warnings : []
        const slides = Array.isArray(result.slides) ? result.slides : []
        if (result.presentationId) {
          this.outlineDocument = {
            ...(this.outlineDocument || {}),
            presentationId: String(result.presentationId)
          }
        }
        if (slides.length < 2) throw new Error('生成的页面数量不足，请调整大纲后重试')
        this.slides = slides.map((slide, index) => ({
          ...slide,
          id: slide.id || `slide-${Date.now()}-${index}`,
          title: String(slide.title || `第 ${index + 1} 页`),
          content: Array.isArray(slide.content) ? slide.content.join('\n') : String(slide.content || ''),
          privatePrompt: String(slide.privatePrompt || '')
        }))
        this.layoutMarkdown = String(result.layoutMarkdown || '')
        if (result.sharedPrompt) this.sharedPrompt = String(result.sharedPrompt)
        this.pageCount = this.slides.length
        this.activeSlideIndex = 0
        this.currentStep = 5
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
      for (let attempt = 0; attempt < 7200 && runId === this.slideGenerationRunId; attempt += 1) {
        const response = await getPptTask(this.slideGenerationTaskId)
        const task = this.responseData(response)
        this.applySlideGenerationSnapshot(task, runId)
        if (['completed', 'failed', 'cancelled'].includes(String(task.status || ''))) return
        await new Promise(resolve => setTimeout(resolve, 1000))
      }
      if (runId === this.slideGenerationRunId) throw new Error('逐页生成等待超时，可稍后重新进入查看')
    },
    applySlideGenerationSnapshot(task, runId) {
      if (!task || runId !== this.slideGenerationRunId) return
      this.slideGenerationSnapshot = task
      const total = Number(task.totalSlides || 0)
      const completed = Number(task.completedSlides || 0)
      const remaining = Number(task.remainingSlides ?? Math.max(0, total - completed))
      const current = Number(task.currentSlide || 0)
      const progress = Number(task.progress || 0)
      this.updateOperationFeedback(
        Math.max(0, Math.min(99, progress)),
        task.message || (current ? `正在生成第 ${current} / ${total} 页` : '正在准备逐页生成'),
        total ? `已完成 ${completed} 页，剩余 ${remaining} 页${current ? `，当前处理第 ${current} 页` : ''}` : '正在排队'
      )
      if (task.status === 'failed') {
        throw new Error(task.error?.message || task.message || '逐页内容生成失败')
      }
      if (task.status === 'cancelled') {
        this.apiBusy = false
        throw new Error('逐页内容生成已取消')
      }
      if (task.status === 'completed') {
        this.updateOperationFeedback(100, '逐页内容生成完成', `共完成 ${total} 页`)
      }
    },
    async startGeneration() {
      if (this.apiBusy || this.slides.length < 2) return
      this.clearTimers()
      const runId = ++this.generationRunId
      this.apiBusy = true
      this.modelConfigError = false
      this.lastPptError = ''
      this.currentStep = 6
      this.progress = 2
      this.taskResult = null
      this.previewImages = {}
      try {
        const response = await createPptTask({
          sourceName: this.fileInfo?.name || `${this.resultName}.txt`,
          outline: {
            ...(this.outlineDocument || {}),
            title: this.outlineName || this.resultName,
            items: this.validOutlineItems.map(item => ({ ...item }))
          },
          slides: this.slides.map(slide => ({
            ...slide,
            content: String(slide.content || '').split(/\r?\n/).map(line => line.trim()).filter(Boolean)
          })),
          sharedPrompt: this.sharedPrompt,
          settings: this.buildSettings(),
          exportFormats: ['pptx', 'pdf']
        })
        const created = this.responseData(response)
        this.taskId = String(created.taskId || '')
        if (!this.taskId) throw new Error('服务端未返回 PPT 任务编号')
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
        if (['completed', 'failed', 'cancelled'].includes(String(task.status || ''))) return
        await new Promise(resolve => setTimeout(resolve, 1000))
      }
      if (runId === this.generationRunId) throw new Error('PPT 生成等待超时，可稍后重新进入查看')
    },
    applyTaskSnapshot(task, runId) {
      if (!task || runId !== this.generationRunId) return
      this.taskResult = task
      this.progress = Math.max(this.progress, Math.min(100, Number(task.progress || 0)))
      if (task.status === 'failed') {
        const message = task.error?.message || task.message || 'PPT 生成失败'
        this.currentStep = 5
        this.progress = 0
        throw new Error(message)
      }
      if (task.status === 'cancelled') {
        this.currentStep = 5
        this.progress = 0
        this.apiBusy = false
        return
      }
      if (task.status === 'completed' && this.currentStep === 6) {
        this.progress = 100
        const availableFormat = (task.attachments || []).some(item => item?.type === this.exportFormat)
          ? this.exportFormat
          : (task.attachments || []).find(item => item?.type === 'pptx' || item?.type === 'pdf')?.type
        if (availableFormat) this.exportFormat = availableFormat
        this.loadPreviewImages()
        this.recordGenerationHistory()
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
    async cancelGeneration() {
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
      this.clearTimers()
      this.generationRunId += 1
      this.slideGenerationRunId += 1
      this.progress = 0
      this.currentStep = 5
    },
    restartFromSettings() {
      this.progress = 0
      this.exportReady = false
      this.currentStep = 3
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
      this.currentStep = 5
      this.exportReady = false
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
      // geometry, styles, SVGs and assets remain untouched.
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
        return /(headline|heading|title)/.test(name)
          && !/(item|card|feature|metric|label|number|page|footer|badge|caption)/.test(name)
      })
      if (titleNode) setText(titleNode, slide.title)

      const bodyNodes = textNodes.filter(node => {
        const name = String(node.name || '').toLowerCase()
        return /(body|paragraph|description|supporting|summary|copy|intro|detail)/.test(name)
          && !/(footer|page|label|number|caption)/.test(name)
      })
      if (bodyNodes.length) {
        const lines = String(slide.content || '').split(/\r?\n/).map(line => line.trim()).filter(Boolean)
        if (bodyNodes.length === 1) setText(bodyNodes[0], lines.join('\n'))
        else bodyNodes.slice(0, lines.length).forEach((node, index) => setText(node, lines[index]))
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
        scene: this.scene,
        pptStyle: this.pptStyle,
        contentLevel: this.contentLevel,
        outlineMode: this.outlineMode,
        settings: { ...this.settings },
        sharedPrompt: this.sharedPrompt,
        taskId: this.taskId
      }
      this.generationHistory = [item, ...this.generationHistory].slice(0, 30)
      this.persistHistories()
    },
    reuseHistory(item) {
      if (this.historyTab === 'outline') {
        this.outlineName = item.name
        this.outlineMode = item.source
        this.outlineItems = (item.items || []).map(entry => ({ ...entry, id: this.createOutlineItem().id }))
        this.currentStep = 3
      } else {
        this.pageCount = Number(item.pageCount || 15)
        this.scene = this.pptScenes.some(scene => scene.value === item.scene) ? item.scene : this.scene
        this.pptStyle = item.pptStyle || 'general'
        this.contentLevel = item.contentLevel || 'standard'
        this.outlineMode = item.outlineMode || this.outlineMode
        this.settings = { ...this.settings, ...(item.settings || {}) }
        this.sharedPrompt = item.sharedPrompt || this.sharedPrompt
        this.currentStep = this.validOutlineItems.length ? 4 : 2
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
        scene: this.scene,
        contentLevel: this.contentLevel
      }
    },
    responseData(response) {
      return response && typeof response === 'object' && Object.prototype.hasOwnProperty.call(response, 'data')
        ? (response.data || {})
        : (response || {})
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
    startOperationFeedback(type) {
      this.stopOperationFeedback()
      const phases = type === 'slides'
        ? [
            { progress: 10, message: '正在读取大纲与页面设置', detail: '准备页面生成参数' },
            { progress: 32, message: 'AI 正在组织逐页内容', detail: '根据大纲补充标题与知识点' },
            { progress: 58, message: '正在匹配页面布局', detail: '为不同内容选择合适版式' },
            { progress: 78, message: '正在等待页面生成结果', detail: '内容较多时可能需要一些时间' }
          ]
        : [
            { progress: 10, message: '正在读取学习资料', detail: '准备文本与生成参数' },
            { progress: 30, message: 'AI 正在解析文本结构', detail: '识别主题、章节和核心知识点' },
            { progress: 56, message: '正在整理复习大纲', detail: '重新组织适合 PPT 的知识结构' },
            { progress: 78, message: '正在等待大纲生成结果', detail: '资料较长时可能需要一些时间' }
          ]
      let phaseIndex = 0
      let feedbackTicks = 0
      this.operationBanterIndex = 0
      this.operationFeedback = { active: true, ...phases[phaseIndex] }
      this.operationFeedbackTimer = setInterval(() => {
        feedbackTicks += 1
        if (feedbackTicks % 3 === 0) {
          this.operationBanterIndex = (this.operationBanterIndex + 1) % this.operationBanters.length
        }
        if (phaseIndex < phases.length - 1) {
          phaseIndex += 1
          this.operationFeedback = { active: true, ...phases[phaseIndex] }
        } else if (this.operationFeedback.progress < 88) {
          this.operationFeedback = { ...this.operationFeedback, progress: this.operationFeedback.progress + 1 }
        }
      }, 900)
    },
    updateOperationFeedback(progress, message, detail) {
      if (this.operationFeedbackTimer) clearInterval(this.operationFeedbackTimer)
      this.operationFeedbackTimer = null
      this.operationFeedback = { active: true, progress, message, detail }
    },
    stopOperationFeedback() {
      if (this.operationFeedbackTimer) clearInterval(this.operationFeedbackTimer)
      this.operationFeedbackTimer = null
      this.operationFeedback = { active: false, progress: 0, message: '', detail: '' }
      this.operationBanterIndex = 0
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
      if (this.generationStream?.abort) this.generationStream.abort('ppt_generation_cancelled')
      this.generationStream = null
      if (this.slideGenerationStream?.abort) this.slideGenerationStream.abort('ppt_slide_generation_cancelled')
      this.slideGenerationStream = null
      if (this.generationTimer) {
        clearInterval(this.generationTimer)
        clearTimeout(this.generationTimer)
        this.generationTimer = null
      }
      if (this.exportTimer) {
        clearTimeout(this.exportTimer)
        this.exportTimer = null
      }
    }
  }
}
</script>

<style scoped>
.ppt-flow{color:#182033}.flow-heading{padding:32rpx 4rpx 22rpx}.flow-heading__eyebrow,.flow-heading__title,.flow-heading__desc{display:block}.flow-heading__eyebrow{color:#4e61f6;font-size:22rpx;font-weight:700;letter-spacing:1rpx}.flow-heading__title{margin-top:8rpx;font-size:40rpx;font-weight:800;line-height:1.25}.flow-heading__desc{margin-top:10rpx;color:#7b8498;font-size:24rpx;line-height:1.55}.step-scroll{width:100%;margin-bottom:22rpx;white-space:nowrap}.stepper{display:inline-flex;min-width:100%;padding:5rpx 2rpx 10rpx;box-sizing:border-box}.stepper__item{position:relative;display:flex;flex:1;min-width:114rpx;flex-direction:column;align-items:center;color:#a1a9b8}.stepper__item:not(:last-child)::after{position:absolute;top:23rpx;left:calc(50% + 26rpx);right:calc(-50% + 26rpx);height:2rpx;background:#dfe4ec;content:''}.stepper__item--done:not(:last-child)::after{background:#7584f7}.stepper__number{position:relative;z-index:1;display:flex;width:46rpx;height:46rpx;align-items:center;justify-content:center;border:2rpx solid #d9dee8;border-radius:50%;background:#f5f7fb;font-size:22rpx;font-weight:700}.stepper__item--active,.stepper__item--done{color:#4156eb}.stepper__item--active .stepper__number,.stepper__item--done .stepper__number{border-color:#5265f5;background:#5265f5;color:#fff;box-shadow:0 7rpx 16rpx rgba(78,97,246,.2)}.stepper__label{margin-top:10rpx;font-size:20rpx}.stepper__item--active .stepper__label{font-weight:700}.stepper__check{font-size:20rpx}.panel{padding:28rpx;border:1px solid #edf0f5;border-radius:24rpx;background:#fff;box-shadow:0 12rpx 38rpx rgba(30,50,90,.05)}.field+.field{margin-top:28rpx}.field__label,.settings-section__title{display:block;margin-bottom:15rpx;font-size:27rpx;font-weight:750}.select-field{display:flex;align-items:center;justify-content:space-between;padding:21rpx 22rpx;border:1px solid #dce2ec;border-radius:16rpx;background:#fff}.select-field__value,.select-field__hint{display:block}.select-field__value{font-size:26rpx;font-weight:650}.select-field__hint{margin-top:6rpx;color:#929bad;font-size:20rpx}.select-field__arrow{color:#697386;font-size:30rpx}.upload-box{display:flex;min-height:236rpx;align-items:center;justify-content:center;flex-direction:column;border:2rpx dashed #cfd7e6;border-radius:18rpx;background:#fafbfe}.file-icon{position:relative;display:flex;width:66rpx;height:78rpx;align-items:flex-end;justify-content:center;padding-bottom:10rpx;border-radius:8rpx;background:linear-gradient(145deg,#6878fb,#4c5eea);box-sizing:border-box;box-shadow:0 10rpx 18rpx rgba(78,97,246,.18)}.file-icon::after{position:absolute;right:0;top:0;border-top:18rpx solid #fff;border-left:18rpx solid transparent;content:''}.file-icon text{color:#fff;font-size:18rpx;font-weight:750}.upload-box__title{margin-top:18rpx;font-size:27rpx;font-weight:700}.upload-box__hint{margin-top:8rpx;color:#98a1b2;font-size:21rpx}.file-row{display:flex;align-items:center;gap:18rpx;padding:20rpx;border:1px solid #dfe4ec;border-radius:16rpx}.file-row__icon{display:flex;width:60rpx;height:66rpx;align-items:center;justify-content:center;border-radius:10rpx;background:#5164f3;color:#fff;font-size:17rpx;font-weight:800}.file-row__main{min-width:0;flex:1}.file-row__name,.file-row__meta{display:block}.file-row__name{overflow:hidden;font-size:25rpx;font-weight:700;text-overflow:ellipsis;white-space:nowrap}.file-row__meta{display:flex;gap:16rpx;margin-top:9rpx;color:#8992a4;font-size:20rpx}.file-row__success{color:#20a966}.file-row__success::before{content:'✓ ';font-weight:700}.file-row__actions{display:flex;gap:17rpx;color:#4e61f6;font-size:21rpx}.file-row__delete{color:#929bad}.preview-card{margin-top:26rpx;padding:22rpx;border-radius:17rpx;background:#f7f8fc}.preview-card__head{display:flex;align-items:center;justify-content:space-between}.preview-card__title{font-size:26rpx;font-weight:750}.preview-card__count{color:#7c8699;font-size:20rpx}.preview-card__content{display:-webkit-box;margin-top:17rpx;overflow:hidden;color:#414a5b;font-size:22rpx;line-height:1.75;white-space:pre-wrap;-webkit-box-orient:vertical;-webkit-line-clamp:8}.preview-card__more{display:block;margin-top:12rpx;color:#5265f5;font-size:20rpx}.single-action{margin-top:28rpx}.bottom-actions{display:flex;gap:20rpx;margin-top:30rpx}.primary-button,.secondary-button{height:84rpx;margin:0;border-radius:15rpx;font-size:27rpx;line-height:84rpx}.primary-button{flex:1;border:0;background:#4e61f6;color:#fff;font-weight:700;box-shadow:0 10rpx 20rpx rgba(78,97,246,.15)}.primary-button[disabled]{opacity:.45}.secondary-button{flex:1;border:1px solid #d8deea;background:#fff;color:#5265f5}.secondary-button::after,.primary-button::after{border:0}.choice-card{position:relative;display:flex;align-items:center;border:2rpx solid #e1e5ed;border-radius:20rpx;background:#fff}.choice-card+.choice-card{margin-top:22rpx}.choice-card--large{min-height:192rpx;padding:25rpx 52rpx 25rpx 22rpx}.choice-card--selected{border-color:#5b6df7;background:linear-gradient(135deg,#fff,#f8f9ff);box-shadow:0 10rpx 25rpx rgba(80,99,235,.08)}.choice-card__icon{display:flex;width:78rpx;height:92rpx;flex:none;align-items:center;justify-content:center;border:2rpx solid #6172f6;border-radius:12rpx;color:#5366f2}.choice-card__icon--original_outline{border-color:#9aa4b6;color:#7e899d}.line-icon{display:flex;width:42rpx;gap:9rpx;flex-direction:column}.line-icon text{height:5rpx;border-radius:99rpx;background:currentColor}.line-icon text:nth-child(2){width:75%}.line-icon text:nth-child(3){width:88%}.choice-card__body{min-width:0;margin-left:22rpx}.choice-card__title,.choice-card__desc,.choice-card__fit{display:block}.choice-card__title{font-size:29rpx;font-weight:750}.choice-card__desc{margin-top:9rpx;color:#667086;font-size:22rpx;line-height:1.55}.choice-card__fit{margin-top:9rpx;color:#8b94a6;font-size:19rpx}.radio-dot{position:absolute;right:18rpx;top:18rpx;display:flex;width:30rpx;height:30rpx;align-items:center;justify-content:center;border:2rpx solid #bcc4d2;border-radius:50%;box-sizing:border-box}.radio-dot--selected{border-color:#5265f5;background:#5265f5;color:#fff;font-size:18rpx}.scene-summary{display:flex;align-items:center;gap:22rpx;padding:20rpx 22rpx;border:1px solid #dce2fb;border-radius:15rpx;background:#f6f7ff}.scene-summary__label{color:#7b8497;font-size:21rpx}.scene-summary__value{color:#5063ee;font-size:24rpx;font-weight:700}.settings-section{margin-top:29rpx;padding-top:2rpx}.settings-section+.settings-section{padding-top:28rpx;border-top:1px solid #eef0f4}.settings-section__head{display:flex;align-items:center;justify-content:space-between}.settings-section__title{margin:0}.settings-section__title--block{margin-bottom:17rpx}.page-number{color:#182033;font-size:32rpx;font-weight:800}.settings-section__unit{color:#7e8798;font-size:21rpx}.range-label{display:flex;justify-content:space-between;margin-top:-9rpx;color:#8a93a5;font-size:20rpx}.settings-hint{display:block;margin-top:10rpx;color:#8c95a7;font-size:20rpx;line-height:1.5}.style-scroll{width:100%;white-space:nowrap}.style-list{display:inline-flex;gap:16rpx;padding:2rpx}.style-card{position:relative;width:226rpx;padding:10rpx;border:2rpx solid #e1e5ed;border-radius:16rpx;box-sizing:border-box}.style-card--selected{border-color:#5265f5;background:#fafaff}.style-card__preview{position:relative;height:118rpx;overflow:hidden;padding:20rpx;border-radius:11rpx;background:#f7f9ff;box-sizing:border-box}.style-card__preview--campus{background:#fff5df}.style-card__preview--focus{background:#151c30}.mini-slide__title{width:60%;height:9rpx;border-radius:99rpx;background:#4154d9}.mini-slide__line{width:52%;height:5rpx;margin-top:10rpx;border-radius:99rpx;background:#b7c0d7}.mini-slide__line--long{width:72%;margin-top:16rpx}.mini-slide__shape{position:absolute;right:-16rpx;bottom:-23rpx;width:90rpx;height:90rpx;border-radius:28rpx;background:rgba(77,98,241,.2);transform:rotate(20deg)}.style-card__preview--campus .mini-slide__title{background:#f59f42}.style-card__preview--campus .mini-slide__shape{background:#dcefdc}.style-card__preview--focus .mini-slide__title{background:#fff}.style-card__preview--focus .mini-slide__line{background:#66728e}.style-card__preview--focus .mini-slide__shape{background:#214d61}.style-card__name,.style-card__desc{display:block}.style-card__name{margin-top:12rpx;font-size:23rpx;font-weight:700}.style-card__desc{margin-top:4rpx;color:#8a93a4;font-size:18rpx}.style-card__check{position:absolute;right:16rpx;top:16rpx;display:flex;width:28rpx;height:28rpx;align-items:center;justify-content:center;border-radius:50%;background:#5265f5;color:#fff;font-size:17rpx}.segmented{display:flex;gap:10rpx}.segmented__item{display:flex;height:62rpx;flex:1;align-items:center;justify-content:center;border:1px solid #dfe4ec;border-radius:999rpx;color:#727c8f;font-size:23rpx}.segmented__item--active{border-color:#5466f4;background:#f0f2ff;color:#4154dc;font-weight:700}.settings-section--split{display:grid;grid-template-columns:1fr;gap:27rpx}.switch-row{display:flex;min-height:68rpx;align-items:center;justify-content:space-between;color:#343d4f;font-size:23rpx}.switch-row switch{transform:scale(.76);transform-origin:right center}.switch-row--visuals{margin-top:12rpx;padding-top:18rpx;border-top:1px solid #eef0f4}.switch-row__title,.switch-row__desc{display:block}.switch-row__desc{margin-top:5rpx;color:#939bad;font-size:18rpx}.effect-preview__label{display:block;margin-bottom:12rpx;font-size:22rpx;font-weight:700}.effect-slide{position:relative;height:230rpx;overflow:hidden;padding:31rpx;border-radius:15rpx;background:#f5f8ff;border-left:6rpx solid #5270f5;box-sizing:border-box}.effect-slide--campus{border-left-color:#ffae56;background:#fff7e8}.effect-slide--focus{border-left-color:#47b5c9;background:#172033;color:#fff}.effect-slide__eyebrow,.effect-slide__title{display:block}.effect-slide__eyebrow{color:#6673a7;font-size:13rpx;letter-spacing:2rpx}.effect-slide--focus .effect-slide__eyebrow{color:#75a4b0}.effect-slide__title{margin-top:20rpx;font-size:30rpx;font-weight:800}.effect-slide__line{width:45%;height:5rpx;margin-top:20rpx;border-radius:99rpx;background:#5265f5}.effect-slide__blocks{position:absolute;right:25rpx;bottom:24rpx;display:flex;align-items:flex-end;gap:8rpx}.effect-slide__blocks text{display:block;width:27rpx;height:40rpx;border-radius:5rpx;background:rgba(82,101,245,.17)}.effect-slide__blocks text:nth-child(2){height:65rpx}.effect-slide__blocks text:nth-child(3){height:89rpx}.progress-panel{padding-top:36rpx}.progress-hero{display:flex;align-items:center;flex-direction:column;text-align:center}.progress-ring{display:flex;width:150rpx;height:150rpx;align-items:center;justify-content:center;border-radius:50%;background:conic-gradient(#5265f5 var(--progress),#e7eaf2 0);box-shadow:0 10rpx 28rpx rgba(78,97,246,.12)}.progress-ring__inner{display:flex;width:122rpx;height:122rpx;align-items:baseline;justify-content:center;border-radius:50%;background:#fff}.progress-ring__inner text:first-child{align-self:center;font-size:38rpx;font-weight:800}.progress-ring__inner text:last-child{align-self:center;color:#748096;font-size:20rpx}.progress-hero__stage{margin-top:22rpx;font-size:29rpx;font-weight:750}.progress-hero__message{margin-top:8rpx;color:#7b8598;font-size:21rpx}.generation-list{margin-top:34rpx}.generation-item{display:flex;min-height:95rpx}.generation-item__rail{position:relative;width:55rpx;flex:none}.generation-item__dot{position:relative;z-index:1;display:flex;width:39rpx;height:39rpx;align-items:center;justify-content:center;border-radius:50%;box-sizing:border-box;font-size:19rpx;font-weight:700}.generation-item__dot--done{background:#2fbd76;color:#fff}.generation-item__dot--active{background:#5265f5;color:#fff;box-shadow:0 0 0 7rpx #eef0ff}.generation-item__dot--waiting{background:#e3e7ee;color:#9aa3b4}.generation-item__line{position:absolute;left:19rpx;top:39rpx;width:2rpx;height:58rpx;background:#e3e7ee}.generation-item__line--done{background:#81d7aa}.generation-item__body{min-width:0;flex:1}.generation-item__title,.generation-item__desc{display:block}.generation-item__title{font-size:24rpx;font-weight:700}.generation-item__desc{margin-top:6rpx;color:#8a93a4;font-size:20rpx}.loading-dots{display:flex;gap:5rpx;margin-top:12rpx}.loading-dots text{display:block;width:7rpx;height:7rpx;border-radius:50%;background:#5265f5;animation:pulse 1s infinite}.loading-dots text:nth-child(2){animation-delay:.15s}.loading-dots text:nth-child(3){animation-delay:.3s}.overall-progress{padding:20rpx;border-radius:15rpx;background:#f7f8fc}.overall-progress__head{display:flex;justify-content:space-between;font-size:22rpx;font-weight:700}.overall-progress__track{height:10rpx;margin-top:14rpx;overflow:hidden;border-radius:99rpx;background:#dde2ec}.overall-progress__value{height:100%;border-radius:inherit;background:#5265f5;transition:width .25s}.overall-progress__time{display:block;margin-top:11rpx;color:#8993a5;font-size:19rpx}.secondary-button--full,.primary-button--full{width:100%;margin-top:22rpx}.success-hero{display:flex;align-items:center;padding:9rpx 0 28rpx;flex-direction:column;text-align:center}.success-icon{display:flex;width:94rpx;height:94rpx;align-items:center;justify-content:center;border-radius:50%;background:#35bd7d;color:#fff;font-size:48rpx;box-shadow:0 13rpx 30rpx rgba(53,189,125,.22)}.success-hero__title{margin-top:18rpx;font-size:32rpx;font-weight:800}.success-hero__desc{margin-top:8rpx;color:#8790a2;font-size:21rpx}.result-summary{display:flex;align-items:center;justify-content:space-between;padding:20rpx;border:1px solid #e2e6ee;border-radius:16rpx}.result-summary__name{display:flex;min-width:0;align-items:center;gap:15rpx}.result-summary__file-icon,.export-choice__icon{display:flex;width:52rpx;height:52rpx;flex:none;align-items:center;justify-content:center;border-radius:10rpx;background:#f07032;color:#fff;font-size:24rpx;font-weight:800}.result-summary__name view text{display:block;max-width:330rpx;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.result-summary__name view text:first-child{font-size:23rpx;font-weight:700}.result-summary__name view text:last-child{margin-top:5rpx;color:#8d96a7;font-size:18rpx}.result-summary__meta{text-align:right}.result-summary__meta text{display:block;color:#32ac73;font-size:19rpx}.result-summary__meta text:last-child{margin-top:5rpx;color:#a0a7b5}.preview-section{margin-top:27rpx}.preview-section__head{display:flex;justify-content:space-between;margin-bottom:17rpx;font-size:25rpx;font-weight:750}.preview-section__head text:last-child{color:#8992a4;font-size:19rpx;font-weight:500}.slide-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:17rpx 14rpx}.slide-thumb{text-align:center}.slide-thumb__canvas{position:relative;display:flex;aspect-ratio:16/9;overflow:hidden;padding:18rpx;flex-direction:column;border:1px solid #dfe4ed;border-radius:10rpx;background:#f8faff;text-align:left;box-sizing:border-box}.slide-thumb__canvas--campus{background:#fff7e9}.slide-thumb__canvas--focus{background:#172034;color:#fff}.slide-thumb__canvas--cover{justify-content:center;background:linear-gradient(135deg,#18213a,#283862);color:#fff}.slide-thumb__number{color:#5365f2;font-size:12rpx;font-weight:800}.slide-thumb__canvas--cover .slide-thumb__number{color:#87a0ff}.slide-thumb__title{z-index:1;max-width:74%;margin-top:8rpx;font-size:18rpx;font-weight:800}.slide-thumb__lines{display:flex;gap:5rpx;margin-top:10rpx;flex-direction:column}.slide-thumb__lines text{display:block;width:54%;height:3rpx;border-radius:99rpx;background:#bdc5d6}.slide-thumb__lines text:nth-child(2){width:70%}.slide-thumb__lines text:nth-child(3){width:43%}.slide-thumb__decor{position:absolute;right:-13rpx;bottom:-22rpx;width:88rpx;height:88rpx;border-radius:24rpx;background:rgba(83,102,242,.17);transform:rotate(18deg)}.slide-thumb__page{display:block;margin-top:6rpx;color:#8a93a5;font-size:17rpx}.text-button{height:66rpx;margin-top:18rpx;border:1px solid #dbe0eb;border-radius:13rpx;background:#fff;color:#5365ed;font-size:21rpx;line-height:66rpx}.text-button::after{border:0}.export-choice{position:relative;display:flex;min-height:126rpx;align-items:center;padding:22rpx 54rpx 22rpx 20rpx;border:2rpx solid #e0e4ec;border-radius:17rpx;box-sizing:border-box}.export-choice+.export-choice{margin-top:18rpx}.export-choice--selected{border-color:#5366f4;background:#fafaff}.export-choice__icon--pdf{background:#ed4d4d;font-size:15rpx}.export-choice__body{min-width:0;margin-left:17rpx}.export-choice__title,.export-choice__desc{display:block}.export-choice__title{color:#4659e9;font-size:24rpx;font-weight:750}.export-choice__title text{color:#7c8698;font-weight:500}.export-choice__desc{margin-top:7rpx;color:#808a9d;font-size:20rpx;line-height:1.5}.download-ready{display:flex;margin-top:24rpx;padding:28rpx 18rpx;align-items:center;flex-direction:column;border:1px solid #dfe4fb;border-radius:18rpx;background:#f7f8ff;text-align:center}.download-ready__illustration{position:relative}.download-ready__file{display:flex;width:78rpx;height:92rpx;align-items:center;justify-content:center;border-radius:12rpx;background:#fff;color:#f06d31;font-size:22rpx;font-weight:800;box-shadow:0 8rpx 22rpx rgba(55,67,130,.12)}.download-ready__arrow{position:absolute;right:-23rpx;bottom:-8rpx;display:flex;width:43rpx;height:43rpx;align-items:center;justify-content:center;border-radius:50%;background:#5265f5;color:#fff;font-size:24rpx}.download-ready__title{margin-top:18rpx;font-size:26rpx;font-weight:750}.download-ready__name{max-width:100%;margin-top:8rpx;overflow:hidden;color:#5b6578;font-size:20rpx;text-overflow:ellipsis;white-space:nowrap}.download-ready__hint{margin-top:12rpx;color:#969eae;font-size:18rpx}.back-result-button{height:74rpx;margin-top:20rpx;border:1px solid #dfe3ec;border-radius:14rpx;background:#fff;color:#5365ed;font-size:22rpx;line-height:74rpx}.back-result-button::after{border:0}@keyframes pulse{0%,100%{opacity:.25;transform:translateY(0)}50%{opacity:1;transform:translateY(-4rpx)}}@media(min-width:700px){.settings-section--split{grid-template-columns:1fr 1fr}.slide-grid{grid-template-columns:repeat(3,1fr)}}
</style>

<style scoped>
.ppt-flow--floating-actions{padding-bottom:calc(128rpx + env(safe-area-inset-bottom));box-sizing:border-box}
.preview-card__content--expanded{display:block;overflow:visible;-webkit-line-clamp:unset}
.preview-card__toggle{display:flex;align-items:center;justify-content:center;gap:8rpx;margin-top:14rpx;padding-top:14rpx;border-top:1px solid #e4e8f0;color:#5265f5;font-size:21rpx;font-weight:650}
.preview-card__toggle-arrow{display:inline-block;font-size:24rpx;line-height:1;transition:transform .2s ease}
.preview-card__toggle-arrow--expanded{transform:rotate(180deg)}
.single-action--floating,.bottom-actions{position:fixed;z-index:40;left:24rpx;right:24rpx;bottom:calc(18rpx + env(safe-area-inset-bottom));margin:0;padding:12rpx;border:1px solid rgba(222,227,239,.9);border-radius:20rpx;background:rgba(255,255,255,.94);box-shadow:0 14rpx 42rpx rgba(35,50,92,.16);box-sizing:border-box;backdrop-filter:blur(12px)}
.single-action--floating .primary-button{width:100%}
.bottom-actions{display:block}
.bottom-actions__buttons{display:flex;gap:20rpx}
.operation-feedback{margin-top:20rpx;padding:22rpx;border:1px solid #e2e7f1;border-radius:18rpx;background:#fff;box-shadow:0 10rpx 28rpx rgba(35,50,92,.07)}
.operation-feedback__head{display:flex;align-items:center;justify-content:space-between;color:#445168;font-size:20rpx;font-weight:700}
.operation-feedback__head text:last-child{color:#5265f5}
.operation-feedback__track{height:7rpx;margin-top:10rpx;overflow:hidden;border-radius:99rpx;background:#e8ebf3}
.operation-feedback__value{height:100%;border-radius:inherit;background:#5265f5;transition:width .35s ease}
.operation-feedback__detail{display:block;margin-top:8rpx;color:#8a94a6;font-size:18rpx}
.operation-banter{margin-top:12rpx;padding:18rpx 20rpx;border:1px solid #e5e9f3;border-radius:16rpx;background:rgba(255,255,255,.78);text-align:center}
.operation-banter__text,.operation-banter__status{display:block}
.operation-banter__text{color:#566176;font-size:20rpx;line-height:1.5;animation:banter-in .32s ease both}
.operation-banter__status{margin-top:7rpx;color:#9aa2b1;font-size:17rpx}
.flow-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:22rpx}
.flow-heading__copy{min-width:0;flex:1}
.history-entry{display:flex;min-width:112rpx;height:62rpx;align-items:center;justify-content:center;gap:9rpx;margin-top:10rpx;border:1px solid #d9dff0;border-radius:14rpx;background:#fff;color:#4b5de7;font-size:20rpx;font-weight:650;box-shadow:0 7rpx 18rpx rgba(42,58,120,.05)}
.history-entry__icon{display:flex;width:24rpx;gap:4rpx;flex-direction:column}
.history-entry__icon text{display:block;height:3rpx;border-radius:99rpx;background:#5869ed}
.history-entry__icon text:nth-child(2){width:75%}
.history-entry__icon text:nth-child(3){width:88%}
.editor-toolbar{display:flex;align-items:flex-start;justify-content:space-between;gap:18rpx;padding-bottom:23rpx;border-bottom:1px solid #eef0f4}
.editor-toolbar__title,.editor-toolbar__desc{display:block}
.editor-toolbar__title{font-size:29rpx;font-weight:800}
.editor-toolbar__desc{margin-top:7rpx;color:#8992a4;font-size:20rpx;line-height:1.45}
.outline-history-button{flex:none;padding:12rpx 17rpx;border:1px solid #d7def4;border-radius:12rpx;color:#5062e9;font-size:20rpx}
.outline-name-field{margin-top:23rpx}
.outline-name-field>text{display:block;margin-bottom:11rpx;font-size:23rpx;font-weight:700}
.outline-name-field input,.outline-item input,.edit-field input,.edit-field textarea,.prompt-field textarea{width:100%;border:1px solid #dfe4ed;border-radius:13rpx;background:#fff;box-sizing:border-box}
.outline-name-field input{height:72rpx;padding:0 19rpx;font-size:23rpx}
.outline-list{margin-top:22rpx}
.outline-item{display:flex;align-items:center;gap:14rpx;padding:18rpx 12rpx;border:1px solid #e2e6ee;border-radius:15rpx;background:#fafbfe}
.outline-item+.outline-item{margin-top:13rpx}
.outline-item__order{display:flex;width:39rpx;height:39rpx;flex:none;align-items:center;justify-content:center;border-radius:11rpx;background:#eef0ff;color:#5062e8;font-size:19rpx;font-weight:750}
.outline-item__main{min-width:0;flex:1}
.outline-item__level-row{display:flex;gap:7rpx;margin-bottom:10rpx}
.outline-level{padding:6rpx 12rpx;border-radius:99rpx;background:#eef1f5;color:#7f899b;font-size:17rpx}
.outline-level--active{background:#5668f1;color:#fff}
.outline-item input{height:63rpx;padding:0 15rpx;font-size:22rpx}
.outline-item__actions{display:flex;width:36rpx;gap:7rpx;flex-direction:column;text-align:center}
.outline-item__actions text{color:#6675e9;font-size:22rpx;line-height:27rpx}
.outline-item__actions .disabled{color:#c9ced8}
.outline-item__actions .outline-item__delete{color:#a1a8b5;font-size:27rpx}
.add-outline-button{height:72rpx;margin-top:15rpx;border:1px dashed #bfc8e9;border-radius:13rpx;background:#f9faff;color:#5264eb;font-size:21rpx;line-height:72rpx}
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
.slide-tab--active{border-color:#586af3;background:#f2f3ff}
.slide-tab--active text{color:#5263eb}
.slide-editor__preview{position:relative;display:flex;min-height:270rpx;overflow:hidden;padding:32rpx;justify-content:center;flex-direction:column;border-left:7rpx solid #5367f1;border-radius:17rpx;background:#f6f8ff;box-sizing:border-box}
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
.edit-field__label text:last-child{color:#929bad;font-size:18rpx;font-weight:500}
.edit-field input{height:72rpx;padding:0 18rpx;font-size:22rpx}
.edit-field textarea,.prompt-field textarea{min-height:135rpx;padding:16rpx 18rpx;font-size:21rpx;line-height:1.6}
.prompt-field{padding:19rpx;border:1px solid #e0e5ee;border-radius:16rpx;background:#fbfcfe}
.prompt-field--shared{border-color:#dce1fb;background:#f8f9ff}
.prompt-field__head{display:flex;align-items:center;justify-content:space-between;margin-bottom:13rpx}
.prompt-field__head view:first-child text{display:block;font-size:23rpx;font-weight:750}
.prompt-field__head view:first-child text:last-child{margin-top:5rpx;color:#8c95a6;font-size:18rpx;font-weight:500}
.prompt-badge{padding:6rpx 12rpx;border-radius:99rpx;background:#e9edff;color:#5264eb;font-size:17rpx}
.prompt-badge--private{background:#eef1f5;color:#657084}
.prompt-field__hint{display:block;margin-top:9rpx;color:#929bad;font-size:17rpx}
.slide-editor__navigation{display:flex;gap:13rpx;margin-top:20rpx}
.slide-editor__navigation .secondary-button{height:68rpx;font-size:21rpx;line-height:68rpx}
.image-replace-row{display:flex;align-items:center;justify-content:space-between;gap:20rpx;margin-top:20rpx;padding:18rpx 20rpx;border:1px solid #e1e5ef;border-radius:15rpx;background:#fafbfe}.image-replace-row__title,.image-replace-row__desc{display:block}.image-replace-row__title{font-size:23rpx;font-weight:700}.image-replace-row__desc{margin-top:6rpx;color:#8a93a5;font-size:18rpx}.image-replace-row__button{flex:none;margin:0;padding:0 18rpx}
.history-mask{position:fixed;z-index:1300;inset:0;background:rgba(20,28,48,.32);backdrop-filter:blur(3rpx)}
.history-drawer{position:absolute;right:0;top:0;bottom:0;width:min(86vw,660rpx);padding:34rpx 27rpx;background:#f6f8fc;box-sizing:border-box;box-shadow:-18rpx 0 45rpx rgba(27,37,72,.15)}
.history-drawer__head{display:flex;align-items:flex-start;justify-content:space-between}
.history-drawer__title,.history-drawer__desc{display:block}
.history-drawer__title{font-size:34rpx;font-weight:800}
.history-drawer__desc{margin-top:8rpx;color:#8a93a5;font-size:19rpx}
.history-drawer__close{display:flex;width:56rpx;height:56rpx;align-items:center;justify-content:center;border-radius:50%;background:#fff;color:#697386;font-size:34rpx}
.history-tabs{display:flex;margin-top:27rpx;padding:6rpx;border-radius:14rpx;background:#e9edf4}
.history-tabs__item{display:flex;height:58rpx;flex:1;align-items:center;justify-content:center;border-radius:10rpx;color:#788295;font-size:21rpx}
.history-tabs__item--active{background:#fff;color:#4e60e7;font-weight:700;box-shadow:0 4rpx 12rpx rgba(38,51,95,.07)}
.history-list{height:calc(100vh - 220rpx);margin-top:20rpx}
.history-card{padding:20rpx;border:1px solid #e1e5ed;border-radius:16rpx;background:#fff}
.history-card+.history-card{margin-top:13rpx}
.history-card__head{display:flex;align-items:center;justify-content:space-between;color:#99a1af;font-size:17rpx}
.history-card__type{padding:5rpx 10rpx;border-radius:99rpx;background:#e9edff;color:#5062e8}
.history-card__type--upload{background:#edf1f4;color:#667386}
.history-card__title,.history-card__meta{display:block}
.history-card__title{margin-top:13rpx;font-size:23rpx;font-weight:750}
.history-card__meta{margin-top:7rpx;color:#8a93a5;font-size:18rpx}
.history-card__actions{display:flex;gap:22rpx;margin-top:16rpx;padding-top:13rpx;border-top:1px solid #eef0f4;color:#5264e9;font-size:19rpx}
.history-card__delete{color:#999faa}
.history-empty{display:flex;padding:100rpx 20rpx;align-items:center;flex-direction:column;text-align:center}
.history-empty__icon{display:flex;width:70rpx;gap:10rpx;padding:18rpx;flex-direction:column;border:2rpx solid #ccd3e1;border-radius:16rpx;box-sizing:border-box}
.history-empty__icon text{height:4rpx;border-radius:99rpx;background:#aab3c2}
.history-empty__title{margin-top:22rpx;font-size:24rpx;font-weight:750}
.history-empty__desc{margin-top:8rpx;color:#929bad;font-size:19rpx}
.style-card__preview-image{display:block;width:100%;padding:0;background:#f7f9ff}
.template-section-head{display:flex;align-items:flex-start;justify-content:space-between;gap:20rpx;margin-bottom:17rpx}
.template-section-head__selected{display:block;margin-top:7rpx;color:#8a93a5;font-size:18rpx}
.template-section-head__action{display:flex;flex:none;align-items:center;gap:13rpx;padding:8rpx 12rpx;border-radius:10rpx;background:#f2f4ff;color:#5365eb;font-size:18rpx}
.template-section-head__action text:first-child{color:#7d8799}
.style-scroll--expanded{overflow:visible;white-space:normal}
.style-scroll--expanded .style-list{display:grid;width:100%;grid-template-columns:repeat(2,minmax(0,1fr));gap:16rpx;box-sizing:border-box}
.style-scroll--expanded .style-card{width:auto;min-width:0}
.style-card__layouts{display:block;margin-top:5rpx;color:#6f7de0;font-size:17rpx}
.template-scroll-hint{display:flex;align-items:center;justify-content:space-between;margin-top:12rpx;color:#919aab;font-size:18rpx}
.template-scroll-hint text:last-child{color:#5265f5}
.template-empty{display:flex;min-height:130rpx;align-items:center;justify-content:center;gap:12rpx;flex-direction:column;border:1px dashed #d9deea;border-radius:14rpx;background:#fafbfe;color:#8b94a5;font-size:20rpx}
.template-empty__retry{color:#5265f5;font-weight:650}
@keyframes banter-in{from{opacity:0;transform:translateY(5rpx)}to{opacity:1;transform:translateY(0)}}
.slide-thumb__image{position:absolute;inset:0;z-index:0;width:100%;height:100%}
.generation-warning{margin-top:16rpx;padding:14rpx 18rpx;border:1px solid #f1d9a6;border-radius:12rpx;background:#fff9eb;color:#9a6a18;font-size:19rpx;line-height:1.5}
.generation-warning--error{border-color:#efc7c2;background:#fff5f4;color:#9d4f49}
.export-choice--disabled{opacity:.52}
.product-hero{position:relative;margin-bottom:28rpx;padding:28rpx 28rpx 30rpx;overflow:hidden;border:1px solid #dfe7ef;border-radius:20rpx;background:linear-gradient(135deg,#fff,#f1f5f9)}
.product-hero__copy{position:relative;z-index:1;max-width:470rpx}
.product-hero__eyebrow,.product-hero__title,.product-hero__desc{display:block}
.product-hero__eyebrow{color:#526f88;font-size:19rpx;font-weight:760}
.product-hero__title{margin-top:10rpx;color:#172033;font-size:38rpx;font-weight:820;line-height:1.22}
.product-hero__desc{margin-top:12rpx;color:#667386;font-size:21rpx;line-height:1.55}
.product-hero__slide{position:absolute;right:22rpx;bottom:24rpx;width:140rpx;height:86rpx;padding:17rpx 16rpx;border:1px solid #cfdbe7;border-radius:13rpx;background:#fff;box-shadow:0 10rpx 26rpx rgba(49,75,99,.12);box-sizing:border-box;transform:rotate(-4deg)}
.product-hero__slide text{display:block;height:6rpx;border-radius:99rpx;background:#526f88}
.product-hero__slide text+text{margin-top:10rpx;background:#b8c6d4}
.product-hero__slide text:nth-child(2){width:75%}
.product-hero__slide text:nth-child(3){width:52%}
.recover-card{display:flex;align-items:center;justify-content:space-between;gap:20rpx;margin-bottom:22rpx;padding:20rpx;border:1px solid #e1e7ef;border-radius:17rpx;background:#fff}
.recover-card--warning{border-color:#ecd8b4;background:#fff8ed}
.recover-card__title,.recover-card__desc{display:block}
.recover-card__title{color:#7b541d;font-size:24rpx;font-weight:760}
.recover-card__desc{margin-top:7rpx;color:#9b6b24;font-size:19rpx;line-height:1.45}
.recover-card__button{flex:none;height:58rpx;margin:0;padding:0 18rpx;border:1px solid #d8b06f;border-radius:12rpx;background:#fff;color:#8b5f23;font-size:20rpx;line-height:58rpx}
.recover-card__button::after{border:0}
.capability-strip{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12rpx;margin-top:24rpx}
.capability-card{min-height:112rpx;padding:16rpx 14rpx;border:1px solid #e1e8ef;border-radius:15rpx;background:#f8fafc;box-sizing:border-box}
.capability-card__title,.capability-card__desc{display:block}
.capability-card__title{color:#314b63;font-size:21rpx;font-weight:760}
.capability-card__desc{margin-top:8rpx;color:#718094;font-size:17rpx;line-height:1.42}
.mode-intro{margin-bottom:22rpx;padding:22rpx;border:1px solid #dfe7ef;border-radius:17rpx;background:#f8fafc}
.mode-intro__title,.mode-intro__desc{display:block}
.mode-intro__title{font-size:27rpx;font-weight:800}
.mode-intro__desc{margin-top:9rpx;color:#6b7889;font-size:21rpx;line-height:1.5}
.task-runtime-card{margin-top:22rpx;padding:20rpx;border:1px solid #e1e7ef;border-radius:16rpx;background:#fafbfd}
.task-runtime-card__row{display:flex;align-items:center;justify-content:space-between;padding:8rpx 0;color:#697587;font-size:20rpx}
.task-runtime-card__row text:last-child{color:#314b63;font-weight:700}
.task-runtime-card__message{display:block;margin-top:10rpx;padding-top:14rpx;border-top:1px solid #e8edf3;color:#526174;font-size:20rpx;line-height:1.5}
.generation-plan-card{margin-top:28rpx;padding:20rpx;border:1px solid #dfe7ef;border-radius:17rpx;background:#f8fafc}
.generation-plan-card__head{display:flex;align-items:flex-start;justify-content:space-between;gap:18rpx}
.generation-plan-card__head>text{flex:none;padding:7rpx 12rpx;border-radius:99rpx;background:#e7eef5;color:#314b63;font-size:18rpx;font-weight:720}
.generation-plan-card__title,.generation-plan-card__desc{display:block}
.generation-plan-card__title{font-size:25rpx;font-weight:800}
.generation-plan-card__desc{margin-top:7rpx;color:#718094;font-size:19rpx;line-height:1.45}
.generation-plan-card__grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12rpx;margin-top:18rpx}
.generation-plan-card__grid view{padding:15rpx;border:1px solid #e4ebf2;border-radius:13rpx;background:#fff}
.generation-plan-card__grid text{display:block}
.generation-plan-card__grid text:first-child{color:#7a8798;font-size:18rpx}
.generation-plan-card__grid text:last-child{margin-top:7rpx;color:#26384a;font-size:22rpx;font-weight:760}
.slide-task-card{margin-top:26rpx;padding:20rpx;border:1px solid #dfe7ef;border-radius:17rpx;background:#fff}
.slide-task-card__head{display:flex;align-items:center;justify-content:space-between;color:#26384a;font-size:22rpx;font-weight:760}
.slide-task-card__head text:first-child{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.slide-task-card__head text:last-child{flex:none;margin-left:18rpx;color:#526f88}
.slide-task-card__track{height:8rpx;margin-top:14rpx;overflow:hidden;border-radius:99rpx;background:#e1e8ef}
.slide-task-card__track view{height:100%;border-radius:inherit;background:#526f88;transition:width .25s ease}
.slide-task-card__stats{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10rpx;margin-top:18rpx}
.slide-task-card__stats view{padding:12rpx;border-radius:12rpx;background:#f4f7fa;text-align:center}
.slide-task-card__stats text{display:block}
.slide-task-card__stats text:first-child{color:#172033;font-size:27rpx;font-weight:820}
.slide-task-card__stats text:last-child{margin-top:5rpx;color:#738195;font-size:17rpx}
.slide-task-card__processing{display:block;margin-top:14rpx;color:#526f88;font-size:19rpx;line-height:1.45}
.slide-readiness{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12rpx;margin-top:20rpx;padding-bottom:20rpx;border-bottom:1px solid #eef0f4}
.slide-readiness__item{padding:15rpx;border:1px solid #e2e8f0;border-radius:13rpx;background:#f8fafc;text-align:center}
.slide-readiness__item text{display:block}
.slide-readiness__item text:first-child{color:#314b63;font-size:28rpx;font-weight:820}
.slide-readiness__item text:last-child{margin-top:5rpx;color:#718094;font-size:17rpx}
.format-status-panel{margin-top:22rpx;padding:20rpx;border:1px solid #dfe7ef;border-radius:17rpx;background:#f8fafc}
.format-status-panel__head{display:flex;align-items:flex-start;justify-content:space-between;gap:18rpx;margin-bottom:16rpx}
.format-status-panel__head>text{flex:none;padding:7rpx 12rpx;border-radius:99rpx;background:#e7eef5;color:#314b63;font-size:18rpx;font-weight:720}
.format-status-panel__title,.format-status-panel__desc{display:block}
.format-status-panel__title{font-size:25rpx;font-weight:800}
.format-status-panel__desc{margin-top:7rpx;color:#718094;font-size:19rpx;line-height:1.45}
.format-status-row{display:flex;align-items:center;gap:15rpx;padding:16rpx;border:1px solid #e4ebf2;border-radius:13rpx;background:#fff}
.format-status-row+.format-status-row{margin-top:12rpx}
.format-status-row--disabled{background:#fbfcfe;opacity:.72}
.format-status-row__icon{display:flex;width:50rpx;height:50rpx;flex:none;align-items:center;justify-content:center;border-radius:10rpx;background:#f07032;color:#fff;font-size:22rpx;font-weight:800}
.format-status-row__icon--pdf{background:#ed4d4d;font-size:15rpx}
.format-status-row__body{min-width:0;flex:1}
.format-status-row__body text{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.format-status-row__body text:first-child{color:#26384a;font-size:22rpx;font-weight:760}
.format-status-row__body text:last-child{margin-top:5rpx;color:#748196;font-size:18rpx}
.format-status-row__state{flex:none;color:#32ac73;font-size:19rpx;font-weight:720}
.format-status-row--disabled .format-status-row__state{color:#a16b24}
.export-status-card{display:flex;align-items:flex-start;justify-content:space-between;gap:18rpx;margin-bottom:20rpx;padding:20rpx;border:1px solid #dfe7ef;border-radius:17rpx;background:#f8fafc}
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
.template-library-hero{position:relative;min-height:238rpx;margin-bottom:24rpx;padding:30rpx 28rpx;overflow:hidden;border:1px solid #dfe7ef;border-radius:20rpx;background:#f8fafc;box-sizing:border-box}
.template-library-hero__copy{position:relative;z-index:1;max-width:470rpx}
.template-library-hero__eyebrow,.template-library-hero__title,.template-library-hero__desc{display:block}
.template-library-hero__eyebrow{color:#526f88;font-size:19rpx;font-weight:760}
.template-library-hero__title{margin-top:10rpx;color:#172033;font-size:38rpx;font-weight:820;line-height:1.22}
.template-library-hero__desc{margin-top:12rpx;color:#667386;font-size:21rpx;line-height:1.55}
.template-library-hero__stack{position:absolute;right:22rpx;bottom:24rpx;width:160rpx;height:112rpx}
.template-library-hero__stack view{position:absolute;width:126rpx;height:78rpx;border:1px solid #cfdbe7;border-radius:12rpx;background:#fff;box-shadow:0 10rpx 24rpx rgba(49,75,99,.12)}
.template-library-hero__stack view:nth-child(1){right:18rpx;top:0;transform:rotate(6deg)}
.template-library-hero__stack view:nth-child(2){right:8rpx;top:18rpx;transform:rotate(-3deg)}
.template-library-hero__stack view:nth-child(3){right:0;top:36rpx}
.template-category-scroll{width:100%;margin-bottom:18rpx;white-space:nowrap}
.template-category-tabs{display:inline-flex;gap:12rpx;padding:2rpx}
.template-category-tab{display:flex;height:58rpx;align-items:center;justify-content:center;padding:0 24rpx;border:1px solid #dce3ee;border-radius:999rpx;background:#fff;color:#6f7b8f;font-size:21rpx;box-sizing:border-box}
.template-category-tab--active{border-color:#5265f5;background:#eef1ff;color:#4154dc;font-weight:720}
.template-library-list{display:flex;gap:16rpx;flex-direction:column;padding-bottom:4rpx}
.template-library-card{position:relative;display:flex;gap:18rpx;padding:14rpx;border:2rpx solid #e1e7ef;border-radius:18rpx;background:#fff;box-sizing:border-box}
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
.template-library-card__meta text:last-child{flex:none;color:#5265f5;font-weight:700}
.template-loading-card{display:flex;gap:18rpx;padding:14rpx;border:1px solid #e1e7ef;border-radius:18rpx;background:#fff}
.template-loading-card__thumb{width:178rpx;height:100rpx;flex:none;border-radius:12rpx;background:#edf2f7}
.template-loading-card__lines{display:flex;flex:1;justify-content:center;flex-direction:column;gap:12rpx}
.template-loading-card__lines text{display:block;height:12rpx;border-radius:99rpx;background:#edf2f7}
.template-loading-card__lines text:nth-child(2){width:82%}
.template-loading-card__lines text:nth-child(3){width:58%}
.template-detail-card{display:grid;grid-template-columns:1fr;gap:18rpx;padding:18rpx;border:1px solid #dfe7ef;border-radius:20rpx;background:#f8fafc}
.template-detail-card__preview{position:relative;aspect-ratio:16/9;overflow:hidden;border:1px solid #d8e1ec;border-radius:16rpx;background:#fff}
.template-detail-card__preview image{display:block;width:100%;height:100%}
.template-detail-card__fallback{padding:42rpx}
.template-detail-card__fallback text{display:block;height:10rpx;border-radius:99rpx;background:#526f88}
.template-detail-card__fallback text+text{margin-top:16rpx;background:#bdc9d7}
.template-detail-card__fallback text:nth-child(2){width:72%}
.template-detail-card__fallback text:nth-child(3){width:48%}
.template-detail-card__eyebrow,.template-detail-card__title,.template-detail-card__desc{display:block}
.template-detail-card__eyebrow{color:#5265f5;font-size:19rpx;font-weight:760}
.template-detail-card__title{margin-top:8rpx;color:#172033;font-size:34rpx;font-weight:820;line-height:1.22}
.template-detail-card__desc{margin-top:10rpx;color:#667386;font-size:21rpx;line-height:1.5}
.template-detail-card__stats{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12rpx;margin-top:18rpx}
.template-detail-card__stats view{padding:15rpx;border:1px solid #e1e8ef;border-radius:13rpx;background:#fff}
.template-detail-card__stats text{display:block}
.template-detail-card__stats text:first-child{overflow:hidden;color:#172033;font-size:26rpx;font-weight:820;text-overflow:ellipsis;white-space:nowrap}
.template-detail-card__stats text:last-child{margin-top:5rpx;color:#718094;font-size:17rpx}
.template-detail-head{display:flex;align-items:flex-start;justify-content:space-between;gap:18rpx;margin-top:28rpx}
.template-detail-head__title,.template-detail-head__desc{display:block}
.template-detail-head__title{font-size:27rpx;font-weight:800}
.template-detail-head__desc{margin-top:7rpx;color:#718094;font-size:19rpx;line-height:1.45}
.template-detail-head>text{flex:none;padding:8rpx 12rpx;border-radius:10rpx;background:#eef1ff;color:#5265f5;font-size:19rpx;font-weight:720}
.template-layout-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14rpx;margin-top:18rpx}
.template-layout-card{padding:12rpx;border:1px solid #e1e7ef;border-radius:15rpx;background:#fff;box-sizing:border-box}
.template-layout-card>text{display:block;margin-top:10rpx;color:#26384a;font-size:20rpx;font-weight:720;text-align:center}
.template-layout-preview{position:relative;aspect-ratio:16/9;overflow:hidden;padding:14rpx;border-radius:11rpx;background:#f5f8fb;box-sizing:border-box}
.template-layout-preview text{display:block;height:6rpx;border-radius:99rpx;background:#526f88}
.template-layout-preview text+text{margin-top:9rpx;background:#becadb}
.template-layout-preview--cover{display:flex;justify-content:center;flex-direction:column;background:#edf2f7}
.template-layout-preview--cover text:first-child{width:68%;height:10rpx}
.template-layout-preview--catalog text{width:82%}
.template-layout-preview--catalog text:nth-child(2){width:66%}
.template-layout-preview--catalog text:nth-child(3){width:74%}
.template-layout-preview--content text:first-child{width:44%;height:9rpx}
.template-layout-preview--focus{border-left:6rpx solid #5265f5;background:#f8f9ff}
.template-layout-preview--visual::after{position:absolute;right:14rpx;bottom:14rpx;width:50rpx;height:42rpx;border-radius:8rpx;background:#d9e4ef;content:''}
.template-layout-preview--summary{text-align:center}
.selected-template-strip{display:flex;align-items:center;gap:15rpx;margin-bottom:22rpx;padding:16rpx;border:1px solid #dfe7ef;border-radius:17rpx;background:#f8fafc}
.selected-template-strip__preview{display:flex;width:82rpx;height:52rpx;flex:none;align-items:center;justify-content:center;overflow:hidden;border:1px solid #d6e0ea;border-radius:10rpx;background:#fff;color:#5265f5;font-size:24rpx;font-weight:820}
.selected-template-strip__preview image{display:block;width:100%;height:100%}
.selected-template-strip__main{min-width:0;flex:1}
.selected-template-strip__label,.selected-template-strip__name{display:block}
.selected-template-strip__label{color:#718094;font-size:17rpx}
.selected-template-strip__name{margin-top:4rpx;overflow:hidden;color:#172033;font-size:23rpx;font-weight:800;text-overflow:ellipsis;white-space:nowrap}
.selected-template-strip__actions{display:flex;flex:none;gap:16rpx;color:#5265f5;font-size:20rpx;font-weight:720}
.product-hero--compact{margin-bottom:24rpx}
.template-usage-card{margin-top:22rpx;padding:18rpx;border:1px solid #dfe7ef;border-radius:18rpx;background:#f8fafc}
.template-usage-card__main{display:flex;align-items:flex-start;gap:16rpx}
.template-usage-card__preview{display:flex;width:150rpx;height:84rpx;flex:none;align-items:center;justify-content:center;overflow:hidden;border:1px solid #d6e0ea;border-radius:12rpx;background:#fff;color:#5265f5;font-size:32rpx;font-weight:820}
.template-usage-card__preview image{display:block;width:100%;height:100%}
.template-usage-card__copy{min-width:0;flex:1}
.template-usage-card__label,.template-usage-card__name,.template-usage-card__desc{display:block}
.template-usage-card__label{color:#718094;font-size:17rpx}
.template-usage-card__name{margin-top:4rpx;overflow:hidden;color:#172033;font-size:25rpx;font-weight:820;text-overflow:ellipsis;white-space:nowrap}
.template-usage-card__desc{display:-webkit-box;margin-top:7rpx;overflow:hidden;color:#667386;font-size:18rpx;line-height:1.42;-webkit-box-orient:vertical;-webkit-line-clamp:2}
.template-usage-card__metrics{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10rpx;margin-top:16rpx}
.template-usage-card__metrics view{padding:12rpx 8rpx;border:1px solid #e4ebf2;border-radius:12rpx;background:#fff;text-align:center}
.template-usage-card__metrics text{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.template-usage-card__metrics text:first-child{color:#172033;font-size:23rpx;font-weight:820}
.template-usage-card__metrics text:last-child{margin-top:4rpx;color:#718094;font-size:16rpx}
.template-usage-plan{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:8rpx;margin-top:14rpx}
.template-usage-plan__item{padding:10rpx 6rpx;border-radius:11rpx;background:#eef1ff;text-align:center}
.template-usage-plan__item--muted{background:#edf1f5}
.template-usage-plan__item text{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.template-usage-plan__item text:first-child{color:#4a586b;font-size:16rpx}
.template-usage-plan__item text:last-child{margin-top:4rpx;color:#5265f5;font-size:17rpx;font-weight:760}
.template-usage-plan__item--muted text:last-child{color:#8994a6}
.template-match-preview{margin-top:18rpx;padding:16rpx;border:1px solid #e1e8ef;border-radius:15rpx;background:#f8fafc}
.template-match-preview__head{display:flex;align-items:center;justify-content:space-between;color:#26384a;font-size:22rpx;font-weight:760}
.template-match-preview__head text:last-child{color:#718094;font-size:17rpx;font-weight:500}
.template-match-preview__grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:9rpx;margin-top:14rpx}
.template-match-preview__item>text{display:block;margin-top:7rpx;overflow:hidden;color:#4a586b;font-size:16rpx;text-align:center;text-overflow:ellipsis;white-space:nowrap}
.template-match-preview__canvas{position:relative;aspect-ratio:16/9;overflow:hidden;padding:8rpx;border-radius:8rpx;background:#fff;box-sizing:border-box}
.template-match-preview__canvas text{display:block;height:4rpx;border-radius:99rpx;background:#526f88}
.template-match-preview__canvas text+text{margin-top:6rpx;background:#becadb}
.template-match-preview__canvas--cover{display:flex;justify-content:center;flex-direction:column;background:#edf2f7}
.template-match-preview__canvas--cover text:first-child{width:70%;height:6rpx}
.template-match-preview__canvas--focus{border-left:4rpx solid #5265f5;background:#f8f9ff}
.template-match-preview__canvas--visual::after{position:absolute;right:8rpx;bottom:8rpx;width:22rpx;height:18rpx;border-radius:5rpx;background:#d9e4ef;content:''}
</style>
