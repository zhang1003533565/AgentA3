<template>
  <div class="ppt-studio">
    <header class="ppt-studio__header">
      <div class="ppt-studio__intro">
        <span class="ppt-studio__eyebrow">AI STUDIO</span>
        <h1 class="ppt-studio__title">{{ stepMeta[currentStep - 1].title }}</h1>
        <p class="ppt-studio__desc">{{ stepMeta[currentStep - 1].description }}</p>
      </div>
      <div class="ppt-studio__header-actions">
        <button type="button" class="ppt-btn ppt-btn--ghost" @click="openHistory(historyTab)">历史记录</button>
        <button v-if="canRestartFlow" type="button" class="ppt-btn ppt-btn--ghost" @click="requestRestartFlow">重新生成</button>
      </div>
    </header>

    <nav class="ppt-studio__steps" aria-label="PPT 生成步骤">
      <button
        v-for="item in stepMeta"
        :key="item.id"
        type="button"
        class="ppt-step"
        :class="{
          'ppt-step--active': currentStep === item.id,
          'ppt-step--done': currentStep > item.id,
          'ppt-step--clickable': isStepNavigable(item),
        }"
        :disabled="!isStepNavigable(item)"
        @click="navigateToStep(item.id)"
      >
        <span class="ppt-step__index">{{ currentStep > item.id ? '✓' : item.id }}</span>
        <span class="ppt-step__label">{{ item.shortTitle }}</span>
        <span class="ppt-step__state">{{ stepStateLabel(item) }}</span>
      </button>
    </nav>

    <div v-if="modelConfigError" class="ppt-alert ppt-alert--warning">
      <div>
        <strong>模型还没有准备好</strong>
        <p>{{ lastPptError || 'PPT 生成使用已测试的模型配置。请先确认 API key 已配置并测试成功。' }}</p>
      </div>
      <button type="button" class="ppt-btn ppt-btn--ghost" @click="openModelHelp">查看说明</button>
    </div>

    <div v-if="operationFeedback.active" class="ppt-feedback">
      <div class="ppt-feedback__head">
        <strong>{{ operationFeedback.message }}</strong>
        <span>{{ operationFeedback.progress }}%</span>
      </div>
      <div class="ppt-feedback__track"><div :style="{ width: `${operationFeedback.progress}%` }" /></div>
      <p>{{ operationFeedback.detail }}</p>
      <button type="button" class="ppt-btn ppt-btn--ghost" @click="cancelGeneration">取消生成</button>
    </div>

    <section class="ppt-studio__workspace">
      <!-- Step 1 & 2: Template / Upload -->
      <div v-if="isTemplateStep || isUploadStep" class="ppt-workspace ppt-workspace--setup" :class="{
        'ppt-workspace--setup-library': isTemplateStep && templateEntryMode === 'library',
        'ppt-workspace--setup-upload': isUploadStep,
        'ppt-workspace--setup-detail': isTemplateStep && templateEntryMode === 'detail',
      }">
        <aside v-if="isTemplateStep && templateEntryMode === 'library'" class="ppt-sidebar">
          <h2>模板分类</h2>
          <button
            v-for="category in templateCategories"
            :key="category.id"
            type="button"
            class="ppt-sidebar__item"
            :class="{ 'ppt-sidebar__item--active': templateCategory === category.id }"
            @click="selectTemplateCategory(category.id)"
          >
            {{ category.name }}
          </button>
        </aside>

        <div class="ppt-main">
          <div v-if="isTemplateStep && templateEntryMode === 'library'" class="ppt-panel">
            <div class="ppt-panel__head">
              <div>
                <h2>{{ templateHeroTitle }}</h2>
                <p>{{ templateHeroDescription }}</p>
              </div>
              <span class="ppt-badge">{{ filteredPptTemplates.length }} 个模板</span>
            </div>

            <div v-if="templateCatalogLoading" class="ppt-empty">正在加载模板…</div>
            <div v-else-if="filteredPptTemplates.length" class="ppt-template-grid">
              <button
                v-for="template in filteredPptTemplates"
                :key="template.id"
                type="button"
                class="ppt-template-card"
                :class="{ 'ppt-template-card--selected': pptStyle === template.id }"
                @click="selectPptTemplate(template.id)"
              >
                <div class="ppt-template-card__thumb">
                  <img v-if="template.thumbnailUrl" :src="template.thumbnailUrl" :alt="template.name" @error="onTemplateThumbnailError(template.id)" />
                  <span v-else class="ppt-template-card__placeholder">{{ template.name.slice(0, 1) }}</span>
                </div>
                <div class="ppt-template-card__body">
                  <div class="ppt-template-card__meta">
                    <strong>{{ template.name }}</strong>
                    <span>{{ template.categoryLabel }}</span>
                  </div>
                  <p>{{ template.description }}</p>
                  <div class="ppt-template-card__actions">
                    <span>{{ template.layoutCount || 0 }} 种布局</span>
                    <span class="ppt-link" @click.stop="showTemplateDetail(template.id)">查看详情</span>
                  </div>
                </div>
              </button>
            </div>
            <div v-else class="ppt-empty">
              <p>{{ templateOptionsLoading ? '正在加载模板…' : (templateCatalogAvailable ? '当前分类暂无模板' : '模板目录暂不可用') }}</p>
              <button v-if="!templateOptionsLoading" type="button" class="ppt-btn ppt-btn--ghost" @click="loadPptOptions(true)">重新加载</button>
            </div>
          </div>

          <div v-else-if="isTemplateStep && templateEntryMode === 'detail'" class="ppt-panel ppt-panel--detail">
            <div class="ppt-detail-layout">
              <div class="ppt-detail-preview" @click="openLayoutViewer">
                <img v-if="layoutPreviewImages[0]" :src="layoutPreviewImages[0]" alt="模板版式预览" />
                <div v-else class="ppt-detail-preview__empty">
                  {{ layoutPreviewFailed[`${selectedTemplate?.id}:0`] ? '版式图加载失败，点击重试' : '正在加载版式图…' }}
                </div>
                <div class="ppt-detail-preview__bar">
                  <strong>{{ selectedTemplateName }}</strong>
                  <span>共 {{ selectedTemplateLayouts.length }} 页版式 · 点击全屏浏览</span>
                </div>
              </div>
              <div class="ppt-detail-copy">
                <span class="ppt-badge">{{ selectedTemplateCategoryLabel }}</span>
                <h2>{{ selectedTemplateName }}</h2>
                <p>{{ selectedTemplateDescription }}</p>
                <dl class="ppt-stats">
                  <div><dt>版式</dt><dd>{{ selectedTemplateLayoutCount }}</dd></div>
                </dl>
              </div>
            </div>
          </div>

          <div v-else class="ppt-panel">
            <div v-if="currentStep > 1 && selectedTemplate" class="ppt-selected-template">
              <img v-if="selectedTemplate.thumbnailUrl" :src="selectedTemplate.thumbnailUrl" :alt="selectedTemplateName" @error="onTemplateThumbnailError(selectedTemplate.id)" />
              <div>
                <span>已选模板</span>
                <strong>{{ selectedTemplateName }}</strong>
              </div>
              <div class="ppt-selected-template__actions">
                <button type="button" class="ppt-link" @click="showTemplateDetail">查看</button>
                <button type="button" class="ppt-link" @click="showTemplateLibrary">更换</button>
              </div>
            </div>

            <div class="ppt-upload-grid">
              <div class="ppt-upload-form">
                <h2>{{ uploadHeroTitle }}</h2>
                <p>{{ uploadHeroDescription }}</p>

                <div class="ppt-mode-grid">
                  <button
                    v-for="mode in outlineModes"
                    :key="mode.id"
                    type="button"
                    class="ppt-mode-card"
                    :class="{ 'ppt-mode-card--active': outlineMode === mode.id }"
                    @click="selectOutlineMode(mode.id)"
                  >
                    <strong>{{ mode.shortName || mode.name }}</strong>
                    <span>{{ mode.description }}</span>
                  </button>
                </div>

                <label class="ppt-field">
                  <span>学习资料</span>
                  <div class="ppt-source-card">
                    <div class="ppt-source-card__head">
                      <div class="ppt-source-card__status">
                        <div class="ppt-source-card__icon">{{ fileInfo ? fileKindLabel : 'FILE' }}</div>
                        <div>
                          <strong>{{ fileInfo ? fileInfo.name : '上传文件或直接粘贴内容' }}</strong>
                          <small>{{ fileInfo ? `${fileInfo.sizeLabel} · ${sourceFileId ? '上传完成' : '读取完成'}` : supportedSourceHint }}</small>
                        </div>
                      </div>
                      <div class="ppt-source-card__actions">
                        <button type="button" class="ppt-link" @click.stop="chooseTxtFile">{{ fileInfo && !fileInfo.manual ? '重传' : '上传文件' }}</button>
                        <button v-if="fileInfo" type="button" class="ppt-link ppt-link--danger" @click.stop="removeFile">删除</button>
                      </div>
                    </div>
                    <textarea
                      v-model="manualSourceContent"
                      class="ppt-textarea ppt-textarea--tall"
                      maxlength="20000"
                      :placeholder="fileInfo && !fileInfo.manual && !fileContent ? '文件已上传，也可以在这里补充生成要求或粘贴额外资料' : '可以直接粘贴课堂笔记、复习提纲或老师给的资料内容'"
                      @input="applyManualSourceInput"
                    />
                  </div>
                </label>
              </div>

              <aside class="ppt-upload-preview">
                <div class="ppt-upload-preview__card">
                  <div class="ppt-upload-preview__head">
                    <strong>资料预览</strong>
                    <span v-if="fileInfo">{{ formattedCharacterCount }} 字</span>
                  </div>
                  <p v-if="fileInfo && fileContent" class="ppt-upload-preview__content">{{ previewExpanded ? previewContent : previewContent.slice(0, 1200) }}</p>
                  <p v-else class="ppt-empty">上传或粘贴资料后，这里会显示摘要预览。</p>
                  <button v-if="hasPreviewOverflow" type="button" class="ppt-link" @click="previewExpanded = !previewExpanded">
                    {{ previewExpanded ? '收起内容' : '显示全部' }}
                  </button>
                </div>
              </aside>
            </div>
          </div>
        </div>

        <aside v-if="isTemplateStep && templateEntryMode === 'library' && selectedTemplate" class="ppt-inspector">
          <h2>已选模板</h2>
          <div class="ppt-inspector__preview">
            <img v-if="selectedTemplate.thumbnailUrl" :src="selectedTemplate.thumbnailUrl" :alt="selectedTemplateName" />
            <span v-else>{{ selectedTemplateName.slice(0, 1) }}</span>
          </div>
          <strong>{{ selectedTemplateName }}</strong>
          <p>{{ selectedTemplateDescription }}</p>
          <button type="button" class="ppt-btn ppt-btn--ghost ppt-btn--block" @click="showTemplateDetail(selectedTemplate.id)">查看版式详情</button>
        </aside>
      </div>

      <!-- Step 3: Outline -->
      <div v-else-if="currentStep === 3" class="ppt-workspace ppt-workspace--outline">
        <div class="ppt-panel ppt-panel--stretch">
          <div class="ppt-panel__head">
            <div>
              <h2>PPT 大纲</h2>
              <p>确认页面顺序和标题，生成时会写入已选模板</p>
            </div>
            <div class="ppt-panel__tools">
              <span class="ppt-badge">{{ validOutlineItems.length || outlineItems.length }} 页</span>
              <button type="button" class="ppt-btn ppt-btn--ghost" @click="openHistory('outline')">大纲记录</button>
            </div>
          </div>

          <div class="ppt-outline-layout">
            <div class="ppt-outline-editor">
              <label class="ppt-field ppt-field--inline">
                <span>大纲名称</span>
                <input v-model="outlineName" maxlength="60" placeholder="请输入大纲名称" spellcheck="false" />
              </label>

              <div class="ppt-outline-table">
                <div class="ppt-outline-table__head">
                  <span>#</span><span>标题</span><span>层级</span><span>操作</span>
                </div>
                <div v-for="(item, index) in outlineItems" :key="item.id" class="ppt-outline-row">
                  <span class="ppt-outline-row__index">{{ index + 1 }}</span>
                  <input v-model="item.title" maxlength="80" placeholder="输入大纲标题" spellcheck="false" />
                  <select :value="outlineLevelIndex(item.level)" @change="updateOutlineItemLevel(index, $event)">
                    <option v-for="(label, levelIndex) in outlineLevelLabels" :key="label" :value="levelIndex">{{ label }}</option>
                  </select>
                  <div class="ppt-outline-row__actions">
                    <button v-if="index > 0" type="button" class="ppt-link" @click="moveOutlineItem(index, -1)">上移</button>
                    <button v-if="index < outlineItems.length - 1" type="button" class="ppt-link" @click="moveOutlineItem(index, 1)">下移</button>
                    <button type="button" class="ppt-link ppt-link--danger" @click="removeOutlineItem(index)">删除</button>
                  </div>
                </div>
              </div>

              <button type="button" class="ppt-btn ppt-btn--ghost" @click="addOutlineItem">+ 添加页面</button>
              <p class="ppt-hint">大纲可保存到记录，之后可再次使用<span v-if="outlineSavedAt"> · 已保存 {{ outlineSavedAt }}</span></p>
            </div>

            <aside class="ppt-outline-preview">
              <h3>结构预览</h3>
              <ul>
                <li v-for="(item, index) in outlineItems" :key="item.id" :class="`ppt-outline-preview__level-${item.level || 2}`">
                  <span>{{ index + 1 }}</span>
                  <strong>{{ item.title || '未命名页面' }}</strong>
                  <small>{{ outlineLevelLabel(item.level) }}</small>
                </li>
              </ul>
            </aside>
          </div>
        </div>
      </div>

      <!-- Step 4: Settings -->
      <div v-else-if="currentStep === 4" class="ppt-workspace ppt-workspace--settings">
        <div class="ppt-panel">
          <div class="ppt-settings-grid">
            <div class="ppt-settings-form">
              <h2>页面组成</h2>
              <div v-for="option in pageOptions" :key="option.key" class="ppt-toggle-row">
                <span>{{ option.label }}</span>
                <input type="checkbox" class="ppt-switch" :checked="settings[option.key]" @change="toggleSetting(option.key, $event)" />
              </div>
              <div class="ppt-toggle-row">
                <div>
                  <strong>生成辅助配图</strong>
                  <small>匹配图标、流程图和结构图</small>
                </div>
                <input type="checkbox" class="ppt-switch" :checked="settings.includeVisuals" @change="toggleSetting('includeVisuals', $event)" />
              </div>
              <div class="ppt-field">
                <span>配图生成方式</span>
                <div class="ppt-segmented">
                  <button
                    v-for="mode in imageModes"
                    :key="mode.id"
                    type="button"
                    :class="{ 'ppt-segmented__item--active': settings.imageMode === mode.id }"
                    @click="setImageMode(mode.id)"
                  >
                    {{ mode.name }}
                  </button>
                </div>
                <small>先留空可在生成后上传替换；选择 AI 才会调用图片模型。</small>
              </div>
            </div>

            <aside v-if="selectedTemplate" class="ppt-settings-aside">
              <div class="ppt-template-summary">
                <img v-if="selectedTemplate.thumbnailUrl" :src="selectedTemplate.thumbnailUrl" :alt="selectedTemplateName" @error="onTemplateThumbnailError(selectedTemplate.id)" />
                <div>
                  <span>当前模板</span>
                  <strong>{{ selectedTemplateName }}</strong>
                  <p>{{ selectedTemplateDescription }}</p>
                </div>
              </div>
              <dl class="ppt-stats ppt-stats--block">
                <div><dt>可用版式</dt><dd>{{ selectedTemplateLayoutCount }}</dd></div>
                <div><dt>预计页数</dt><dd>{{ validOutlineItems.length || outlineItems.length }}</dd></div>
              </dl>
              <button type="button" class="ppt-btn ppt-btn--ghost ppt-btn--block" @click="showTemplateLibrary">更换模板</button>
            </aside>
          </div>

          <div v-if="slideGenerationSnapshot" class="ppt-task-card">
            <div class="ppt-task-card__head">
              <strong>{{ slideGenerationSnapshot.message || '正在生成逐页内容' }}</strong>
              <span>{{ Math.min(99, Number(slideGenerationSnapshot.progress || 0)) }}%</span>
            </div>
            <div class="ppt-task-card__track"><div :style="{ width: `${Math.min(99, Number(slideGenerationSnapshot.progress || 0))}%` }" /></div>
            <div class="ppt-task-card__stats">
              <div><strong>{{ slideGenerationSnapshot.completedSlides || 0 }}</strong><span>已完成</span></div>
              <div><strong>{{ slideGenerationSnapshot.remainingSlides || 0 }}</strong><span>剩余</span></div>
              <div><strong>{{ slideGenerationCurrentLabel }}</strong><span>当前页</span></div>
            </div>
            <p v-if="slideGenerationProcessingLabel">正在生成：{{ slideGenerationProcessingLabel }}</p>
            <button v-if="apiBusy" type="button" class="ppt-btn ppt-btn--ghost" @click="cancelGeneration">取消生成</button>
          </div>
        </div>
      </div>

      <!-- Step 5: Slide editor -->
      <div v-else-if="currentStep === 5" class="ppt-workspace ppt-workspace--editor">
        <aside class="ppt-slide-rail">
          <div class="ppt-slide-rail__head">
            <strong>页面列表</strong>
            <span>{{ activeSlideIndex + 1 }} / {{ slides.length }}</span>
          </div>
          <button
            v-for="(slide, index) in slides"
            :key="slide.id"
            type="button"
            class="ppt-slide-rail__item"
            :class="{ 'ppt-slide-rail__item--active': activeSlideIndex === index }"
            @click="selectEditorSlide(index)"
          >
            <span>{{ index + 1 }}</span>
            <div>
              <strong>{{ slide.title || '未命名页面' }}</strong>
              <small v-if="slide.layoutLocked">已锁定版式</small>
            </div>
          </button>
        </aside>

        <div v-if="activeSlide" class="ppt-editor-stage">
          <div v-if="canRetryGeneration" class="ppt-alert ppt-alert--warning">
            <div>
              <strong>上次渲染没有完成</strong>
              <p>{{ renderFailureMessage }}</p>
            </div>
            <button type="button" class="ppt-btn ppt-btn--primary" :disabled="apiBusy" @click="retryGenerationTask">{{ apiBusy ? '重试中…' : '重试渲染' }}</button>
          </div>

          <div class="ppt-editor-preview" :class="`ppt-editor-preview--${pptStyle}`" :style="editorPreviewFrameStyle">
            <img
              v-if="editorPreviewImage && editorPreviewSlideIndex === activeSlideIndex"
              :src="editorPreviewImage"
              alt="页面预览"
              @click="openEditorPreview"
            />
            <img v-else src="/static/images/ppt-preview-empty.svg" alt="预览占位" />
            <div v-if="editorPreviewError" class="ppt-editor-preview__status">{{ editorPreviewError }}</div>
            <div v-if="editorPreviewLoading" class="ppt-editor-preview__status ppt-editor-preview__status--loading">
              <span class="ppt-spinner" />正在渲染中
            </div>
          </div>
        </div>

        <aside v-if="activeSlide" class="ppt-editor-form">
          <div class="ppt-lock-card" :class="{ 'ppt-lock-card--locked': activeSlide.layoutLocked }">
            <div>
              <strong>{{ activeSlide.layoutLocked ? '当前页面已锁定' : '锁定当前页面' }}</strong>
              <p>{{ activeSlide.layoutLocked ? '生成时保留当前预览版式，仍可修改文字' : '生成时保留当前预览版式，不自动更换布局' }}</p>
              <small>{{ selectedTemplateName }} · {{ activeSlideLayoutLabel }}</small>
            </div>
            <button type="button" class="ppt-btn ppt-btn--ghost" @click="toggleActiveSlideLock">
              {{ activeSlide.layoutLocked ? '解除锁定' : '锁定页面' }}
            </button>
          </div>

          <label class="ppt-field">
            <span>页面标题 <small>{{ activeSlide.title.length }}/80</small></span>
            <input v-model="activeSlide.title" maxlength="80" placeholder="请输入页面标题" @input="onEditorContentInput" />
          </label>
          <label class="ppt-field">
            <span>页面内容</span>
            <textarea v-model="activeSlide.content" maxlength="1200" rows="6" placeholder="请输入本页需要展示的知识点和说明" @input="onEditorContentInput" />
          </label>
          <label class="ppt-field">
            <span>公共提示词 <small>所有页面共同生效</small></span>
            <textarea v-model="sharedPrompt" maxlength="800" rows="4" placeholder="例如：保持学习资料准确，使用简洁排版，突出关键概念" @input="markEditorDirty" />
          </label>
          <label class="ppt-field">
            <span>单页私有提示词 <small>仅对第 {{ activeSlideIndex + 1 }} 页生效</small></span>
            <textarea v-model="activeSlide.privatePrompt" maxlength="800" rows="4" placeholder="例如：本页使用左右对比布局，增加函数图像示意" @input="onEditorContentInput" />
          </label>

          <div class="ppt-editor-nav">
            <button type="button" class="ppt-btn ppt-btn--ghost" :disabled="activeSlideIndex === 0" @click="selectEditorSlide(activeSlideIndex - 1)">上一页</button>
            <button type="button" class="ppt-btn ppt-btn--ghost" :disabled="activeSlideIndex === slides.length - 1" @click="selectEditorSlide(activeSlideIndex + 1)">下一页</button>
          </div>
        </aside>
      </div>

      <!-- Step 6: Progress -->
      <div v-else-if="currentStep === 6" class="ppt-workspace ppt-workspace--progress">
        <div class="ppt-progress-layout">
          <div class="ppt-progress-main">
            <div class="ppt-progress-ring" :style="{ '--progress': `${progress * 3.6}deg` }">
              <div><strong>{{ progress }}</strong><span>%</span></div>
            </div>
            <h2>{{ activeGenerationStep.activeText }}</h2>
            <p>{{ progressMessage }}</p>
            <div class="ppt-progress-track">
              <div :style="{ width: `${progress}%` }" />
            </div>
            <small>{{ generationRuntimeHint }}</small>
          </div>

          <ol class="ppt-progress-steps">
            <li v-for="(item, index) in generationSteps" :key="item.id" class="ppt-progress-step" :class="generationStatusClass(index)">
              <span>{{ index < activeGenerationIndex ? '✓' : index + 1 }}</span>
              <div>
                <strong>{{ generationStepTitle(item, index) }}</strong>
                <p>{{ index < activeGenerationIndex ? item.doneText : index === activeGenerationIndex ? item.description : '等待中' }}</p>
              </div>
            </li>
          </ol>
        </div>
      </div>

      <!-- Step 7: Result -->
      <div v-else-if="currentStep === 7" class="ppt-workspace ppt-workspace--result">
        <div class="ppt-result-layout">
          <div class="ppt-result-main">
            <div class="ppt-result-hero">
              <div class="ppt-result-hero__icon">✓</div>
              <div>
                <h2>{{ qualityStatus === 'partial' ? 'PPT 已生成，部分页面需复核' : 'PPT 已生成' }}</h2>
                <p>共 {{ pageCount }} 页，使用 {{ selectedTemplateName }} 模板，PPTX 已生成。</p>
              </div>
            </div>

            <div class="ppt-result-summary">
              <div>
                <span class="ppt-file-icon">P</span>
                <div>
                  <strong>{{ resultName }}</strong>
                  <small>演示文稿 · {{ pageCount }} 页</small>
                </div>
              </div>
              <span class="ppt-badge ppt-badge--success">生成完成</span>
            </div>

            <div class="ppt-slide-grid">
              <button
                v-for="slide in visibleSlides"
                :key="slide"
                type="button"
                class="ppt-slide-thumb"
                @click="activeSlideIndex = slide - 1; openSlidePreview(slide)"
              >
                <img v-if="previewImages[slide]" :src="previewImages[slide]" :alt="`第 ${slide} 页`" />
                <div v-else class="ppt-slide-thumb__fallback">
                  <span>{{ String(slide).padStart(2, '0') }}</span>
                  <strong>{{ slideTitle(slide) }}</strong>
                </div>
                <span>第 {{ slide }} 页 · {{ slideTitle(slide) }}</span>
              </button>
            </div>

            <div class="ppt-replace-row">
              <div>
                <strong>替换第 {{ activeSlideIndex + 1 }} 页配图</strong>
                <p>模板图片可留空，也可以在生成后上传自己的图片</p>
              </div>
              <button type="button" class="ppt-btn ppt-btn--ghost" :disabled="apiBusy || !taskId" @click="uploadSlideImage">上传替换</button>
            </div>
          </div>

          <aside class="ppt-result-aside">
            <h3>导出文件</h3>
            <p>{{ exportStatusCopy }}</p>
            <button
              v-for="format in exportFormats"
              :key="format.id"
              type="button"
              class="ppt-export-row"
              :class="{ 'ppt-export-row--disabled': !isExportAvailable(format.id), 'ppt-export-row--active': exportFormat === format.id }"
              @click="selectExportFormat(format.id)"
            >
              <span class="ppt-file-icon">{{ format.icon }}</span>
              <div>
                <strong>{{ format.name }}</strong>
                <small>{{ isExportAvailable(format.id) ? '文件已生成，可下载' : formatErrorMessage(format.id) }}</small>
              </div>
              <span>{{ isExportAvailable(format.id) ? '已生成' : '不可用' }}</span>
            </button>
          </aside>
        </div>
      </div>

      <!-- Step 8: Export -->
      <div v-else class="ppt-workspace ppt-workspace--export">
        <div class="ppt-export-panel">
          <div class="ppt-alert" :class="{ 'ppt-alert--warning': !isExportAvailable(exportFormat) }">
            <div>
              <strong>{{ exportStatusCopy }}</strong>
              <p>{{ isExportAvailable(exportFormat) ? `当前选择 ${selectedExportFormatName}，可直接下载。` : selectedExportIssue }}</p>
            </div>
            <button v-if="!isExportAvailable(exportFormat) && primaryExportFormat" type="button" class="ppt-btn ppt-btn--ghost" @click="switchToPrimaryExportFormat">切换可用格式</button>
          </div>

          <button
            v-for="format in exportFormats"
            :key="format.id"
            type="button"
            class="ppt-export-choice"
            :class="{ 'ppt-export-choice--selected': exportFormat === format.id, 'ppt-export-choice--disabled': !isExportAvailable(format.id) }"
            @click="selectExportFormat(format.id)"
          >
            <span class="ppt-file-icon">{{ format.icon }}</span>
            <div>
              <strong>{{ format.name }} (.{{ format.id }})</strong>
              <p>{{ format.description }}</p>
            </div>
          </button>

          <button type="button" class="ppt-btn ppt-btn--primary ppt-btn--block" :disabled="exportPreparing || !isExportAvailable(exportFormat)" @click="prepareExport">
            {{ exportPreparing ? '正在下载文件…' : '下载文件' }}
          </button>

          <div v-if="exportReady" class="ppt-download-ready">
            <strong>文件已生成</strong>
            <p>{{ downloadFileName }}</p>
            <small>文件已下载，可使用 PowerPoint、WPS 或系统阅读器打开</small>
          </div>

          <button type="button" class="ppt-btn ppt-btn--ghost ppt-btn--block" @click="currentStep = 7">返回生成结果</button>
        </div>
      </div>
    </section>

    <footer class="ppt-studio__footer">
      <div class="ppt-studio__footer-left">
        <button v-if="currentStep > 1 && (isTemplateStep || isUploadStep || currentStep === 3 || currentStep === 4 || currentStep === 5)" type="button" class="ppt-btn ppt-btn--ghost" :disabled="apiBusy" @click="goPrevious">上一步</button>
      </div>
      <div class="ppt-studio__footer-right">
        <template v-if="isTemplateStep && templateEntryMode === 'library'">
          <button type="button" class="ppt-btn ppt-btn--primary" :disabled="!selectedTemplate || apiBusy" @click="goNext">{{ apiBusy ? '正在生成大纲…' : templateNextLabel }}</button>
        </template>
        <template v-else-if="isTemplateStep && templateEntryMode === 'detail'">
          <button type="button" class="ppt-btn ppt-btn--ghost" :disabled="apiBusy" @click="showTemplateLibrary">返回模板库</button>
          <button type="button" class="ppt-btn ppt-btn--primary" :disabled="apiBusy" @click="goNext">{{ apiBusy ? '正在生成大纲…' : templateDetailActionLabel }}</button>
        </template>
        <template v-else-if="isUploadStep">
          <button type="button" class="ppt-btn ppt-btn--primary" :disabled="!fileInfo || apiBusy" @click="goNext">{{ apiBusy ? '正在生成大纲…' : uploadNextLabel }}</button>
        </template>
        <template v-else-if="currentStep === 3">
          <button type="button" class="ppt-btn ppt-btn--ghost" :disabled="apiBusy || !validOutlineItems.length" @click="saveOutlineSnapshot(true)">保存大纲</button>
          <button type="button" class="ppt-btn ppt-btn--primary" :disabled="validOutlineItems.length < 2" @click="confirmOutline">下一步</button>
        </template>
        <template v-else-if="currentStep === 4">
          <button type="button" class="ppt-btn ppt-btn--primary" :disabled="apiBusy" @click="handleSettingsNext">{{ apiBusy ? '正在生成页面…' : settingsNextLabel }}</button>
        </template>
        <template v-else-if="currentStep === 5">
          <button v-if="hasLastSuccessfulResult" type="button" class="ppt-btn ppt-btn--ghost" :disabled="apiBusy" @click="returnToLastSuccessfulResult">返回上次成品</button>
          <button type="button" class="ppt-btn ppt-btn--primary" :disabled="apiBusy" @click="startGeneration">{{ apiBusy ? '正在创建任务…' : editorPrimaryLabel }}</button>
        </template>
        <template v-else-if="currentStep === 6">
          <button type="button" class="ppt-btn ppt-btn--ghost" @click="returnToEditor">返回编辑</button>
          <button type="button" class="ppt-btn ppt-btn--ghost" @click="cancelGeneration">取消生成</button>
        </template>
        <template v-else-if="currentStep === 7">
          <button type="button" class="ppt-btn ppt-btn--ghost" @click="returnToEditor">继续修改</button>
          <button type="button" class="ppt-btn ppt-btn--ghost" @click="restartFromSettings">重新生成</button>
          <button type="button" class="ppt-btn ppt-btn--primary" :disabled="!availableExportFormats.length" @click="goExportStep">导出下载</button>
        </template>
      </div>
    </footer>

    <!-- Layout viewer modal -->
    <div v-if="layoutViewerVisible" class="ppt-modal ppt-modal--layout">
      <div class="ppt-modal__backdrop" @click="closeLayoutViewer" />
      <div class="ppt-modal__panel">
        <header class="ppt-modal__head">
          <div>
            <strong>{{ selectedTemplateName }}</strong>
            <span>{{ activeLayoutIndex + 1 }} / {{ selectedTemplateLayouts.length }} 页版式</span>
          </div>
          <button type="button" class="ppt-modal__close" @click="closeLayoutViewer">×</button>
        </header>
        <div v-if="!layoutViewerReady" class="ppt-modal__loading">
          <span class="ppt-spinner" />
          <p>正在加载模板版式 {{ layoutLoadedCount }} / {{ selectedTemplateLayouts.length }}</p>
        </div>
        <div v-else class="ppt-layout-grid">
          <button
            v-for="(layout, index) in selectedTemplateLayouts"
            :key="layout.id || index"
            type="button"
            class="ppt-layout-item"
            @click="retryLayoutPreview(index)"
          >
            <img v-if="layoutPreviewImages[index]" :src="layoutPreviewImages[index]" :alt="layout?.name" />
            <div v-else class="ppt-layout-item__empty">
              {{ layoutPreviewFailed[`${selectedTemplate?.id}:${index}`] ? '加载失败，点击重试' : '加载中…' }}
            </div>
            <span>{{ layout?.name || `版式 ${index + 1}` }}</span>
          </button>
        </div>
        <footer class="ppt-modal__foot">
          <button type="button" class="ppt-btn ppt-btn--primary" @click="useTemplateFromViewer">使用该模板</button>
        </footer>
      </div>
    </div>

    <!-- History modal -->
    <div v-if="historyOpen" class="ppt-modal">
      <div class="ppt-modal__backdrop" @click="historyOpen = false" />
      <div class="ppt-modal__panel ppt-modal__panel--history">
        <header class="ppt-modal__head">
          <div>
            <strong>历史记录</strong>
            <span>大纲和生成配置保存在当前设备</span>
          </div>
          <button type="button" class="ppt-modal__close" @click="historyOpen = false">×</button>
        </header>
        <div class="ppt-history-tabs">
          <button type="button" :class="{ 'ppt-history-tabs__item--active': historyTab === 'generation' }" @click="historyTab = 'generation'">生成记录</button>
          <button type="button" :class="{ 'ppt-history-tabs__item--active': historyTab === 'outline' }" @click="historyTab = 'outline'">大纲记录</button>
        </div>
        <div class="ppt-history-list">
          <article v-for="item in currentHistory" :key="item.id" class="ppt-history-card">
            <div class="ppt-history-card__head">
              <span class="ppt-badge">{{ historyTab === 'outline' ? (item.source === 'ai_outline' ? 'AI 大纲' : '原文大纲') : 'PPT' }}</span>
              <time>{{ item.createdAt }}</time>
            </div>
            <strong>{{ item.name }}</strong>
            <p>{{ historyTab === 'outline' ? `${item.items.length} 个大纲项` : `${item.pageCount} 页 · ${styleName(item.pptStyle)}` }}</p>
            <div class="ppt-history-card__actions">
              <button type="button" class="ppt-link" @click="reuseHistory(item)">{{ historyTab === 'outline' ? '载入并编辑' : '使用此配置' }}</button>
              <button type="button" class="ppt-link ppt-link--danger" @click="deleteHistory(item.id)">删除</button>
            </div>
          </article>
          <div v-if="!currentHistory.length" class="ppt-empty">
            <strong>{{ historyTab === 'outline' ? '暂无大纲记录' : '暂无生成记录' }}</strong>
            <p>{{ historyTab === 'outline' ? '保存过的大纲会显示在这里' : '完成一次 PPT 生成后会显示在这里' }}</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
