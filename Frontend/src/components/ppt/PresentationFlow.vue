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

<script>
import '../../utils/uniShim.js'

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
} from '../../api/ppt.js'

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
      const value = Boolean(event?.detail?.value ?? event?.target?.checked)
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
.ppt-studio {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: calc(100vh - 140px);
  color: #1f3852;
}

.ppt-studio__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 4px;
  border-bottom: 1px solid #e3e9ef;
}

.ppt-studio__eyebrow {
  display: block;
  color: #6f8398;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 1.2px;
}

.ppt-studio__title {
  margin: 6px 0 4px;
  font-size: 24px;
  line-height: 1.2;
}

.ppt-studio__desc {
  margin: 0;
  color: #718096;
  font-size: 13px;
}

.ppt-studio__header-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.ppt-studio__steps {
  display: grid;
  grid-template-columns: repeat(8, minmax(0, 1fr));
  gap: 8px;
}

.ppt-step {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid #dfe6ed;
  border-radius: 8px;
  background: #fafcfd;
  text-align: left;
  transition: border-color .15s, background .15s;
}

.ppt-step--clickable:not(:disabled) {
  cursor: pointer;
}

.ppt-step--clickable:not(:disabled):hover {
  border-color: #b8cad8;
  background: #f3f7fa;
}

.ppt-step--active {
  border-color: #4f779e;
  background: #edf4fa;
}

.ppt-step--done {
  border-color: #c8d9e6;
}

.ppt-step:disabled {
  opacity: .72;
  cursor: default;
}

.ppt-step__index {
  display: inline-flex;
  width: 22px;
  height: 22px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #e8eef3;
  color: #516579;
  font-size: 11px;
  font-weight: 700;
}

.ppt-step--active .ppt-step__index,
.ppt-step--done .ppt-step__index {
  background: #4f779e;
  color: #fff;
}

.ppt-step__label {
  font-size: 12px;
  font-weight: 700;
}

.ppt-step__state {
  color: #8493a2;
  font-size: 11px;
}

.ppt-studio__workspace {
  flex: 1;
  min-height: 0;
}

.ppt-studio__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 14px;
  border-top: 1px solid #e3e9ef;
}

.ppt-studio__footer-right {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

.ppt-workspace {
  display: grid;
  gap: 16px;
  min-height: 520px;
}

.ppt-workspace--setup-library {
  grid-template-columns: 200px minmax(0, 1fr) 280px;
}

.ppt-workspace--setup-upload,
.ppt-workspace--setup-detail {
  grid-template-columns: minmax(0, 1fr);
}

.ppt-workspace--outline,
.ppt-workspace--settings,
.ppt-workspace--progress,
.ppt-workspace--result,
.ppt-workspace--export {
  grid-template-columns: minmax(0, 1fr);
}

.ppt-workspace--editor {
  grid-template-columns: 240px minmax(420px, 1fr) 340px;
  align-items: stretch;
}

.ppt-sidebar,
.ppt-inspector,
.ppt-panel,
.ppt-upload-preview__card,
.ppt-outline-preview,
.ppt-settings-aside,
.ppt-result-aside,
.ppt-export-panel {
  border: 1px solid #e0e7ed;
  border-radius: 10px;
  background: #fff;
}

.ppt-sidebar {
  padding: 14px;
  height: fit-content;
  position: sticky;
  top: 12px;
}

.ppt-sidebar h2,
.ppt-inspector h2,
.ppt-outline-preview h3,
.ppt-result-aside h3 {
  margin: 0 0 12px;
  font-size: 13px;
}

.ppt-sidebar__item {
  display: block;
  width: 100%;
  padding: 9px 10px;
  border-radius: 6px;
  color: #516579;
  background: transparent;
  text-align: left;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.ppt-sidebar__item--active,
.ppt-sidebar__item:hover {
  background: #edf4fa;
  color: #294f74;
}

.ppt-main {
  min-width: 0;
}

.ppt-panel {
  padding: 18px;
}

.ppt-panel--stretch {
  min-height: 560px;
}

.ppt-panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.ppt-panel__head h2 {
  margin: 0 0 4px;
  font-size: 18px;
}

.ppt-panel__head p {
  margin: 0;
  color: #718096;
  font-size: 13px;
}

.ppt-panel__tools {
  display: flex;
  gap: 8px;
  align-items: center;
}

.ppt-template-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 14px;
}

.ppt-template-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid #dfe6ed;
  border-radius: 10px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: border-color .15s, box-shadow .15s;
}

.ppt-template-card:hover,
.ppt-template-card--selected {
  border-color: #7a96ad;
  box-shadow: 0 8px 20px rgba(44, 62, 80, .08);
}

.ppt-template-card__thumb {
  aspect-ratio: 16 / 9;
  background: #f2f4f7;
}

.ppt-template-card__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.ppt-template-card__placeholder {
  display: grid;
  place-items: center;
  height: 100%;
  color: #8a98a7;
  font-size: 28px;
  font-weight: 700;
}

.ppt-template-card__body {
  display: grid;
  gap: 6px;
  padding: 12px;
}

.ppt-template-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.ppt-template-card__meta strong {
  font-size: 14px;
}

.ppt-template-card__meta span,
.ppt-template-card__body p,
.ppt-template-card__actions {
  color: #718096;
  font-size: 12px;
}

.ppt-template-card__body p {
  margin: 0;
  line-height: 1.5;
}

.ppt-template-card__actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.ppt-inspector {
  padding: 16px;
  height: fit-content;
  position: sticky;
  top: 12px;
}

.ppt-inspector__preview {
  aspect-ratio: 16 / 9;
  margin-bottom: 12px;
  border-radius: 8px;
  overflow: hidden;
  background: #f2f4f7;
}

.ppt-inspector__preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.ppt-inspector strong {
  display: block;
  margin-bottom: 6px;
}

.ppt-inspector p {
  margin: 0 0 12px;
  color: #718096;
  font-size: 12px;
  line-height: 1.5;
}

.ppt-detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(280px, .8fr);
  gap: 18px;
}

.ppt-detail-preview {
  position: relative;
  overflow: hidden;
  border-radius: 10px;
  background: #f3f6f9;
  cursor: pointer;
}

.ppt-detail-preview img {
  width: 100%;
  aspect-ratio: 16 / 9;
  object-fit: cover;
}

.ppt-detail-preview__empty {
  display: grid;
  place-items: center;
  min-height: 280px;
  color: #8493a2;
}

.ppt-detail-preview__bar {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  padding: 14px 16px;
  background: linear-gradient(transparent, rgba(20, 35, 50, .72));
  color: #fff;
}

.ppt-detail-preview__bar strong,
.ppt-detail-preview__bar span {
  display: block;
}

.ppt-detail-preview__bar span {
  margin-top: 4px;
  font-size: 12px;
  opacity: .88;
}

.ppt-detail-copy h2 {
  margin: 8px 0;
}

.ppt-detail-copy p {
  color: #718096;
  line-height: 1.6;
}

.ppt-selected-template {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding: 12px;
  border: 1px solid #d8e3ec;
  border-radius: 8px;
  background: #f8fbfd;
}

.ppt-selected-template img {
  width: 72px;
  height: 42px;
  object-fit: cover;
  border-radius: 6px;
}

.ppt-selected-template span {
  display: block;
  color: #718096;
  font-size: 11px;
}

.ppt-selected-template__actions {
  display: flex;
  gap: 10px;
  margin-left: auto;
}

.ppt-upload-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(280px, .8fr);
  gap: 16px;
}

.ppt-upload-form h2 {
  margin: 0 0 6px;
}

.ppt-upload-form > p {
  margin: 0 0 16px;
  color: #718096;
}

.ppt-mode-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.ppt-mode-card {
  display: grid;
  gap: 6px;
  padding: 12px;
  border: 1px solid #dfe6ed;
  border-radius: 8px;
  background: #fff;
  text-align: left;
  cursor: pointer;
}

.ppt-mode-card--active {
  border-color: #4f779e;
  background: #edf4fa;
}

.ppt-mode-card strong {
  font-size: 14px;
}

.ppt-mode-card span {
  color: #718096;
  font-size: 12px;
  line-height: 1.5;
}

.ppt-field {
  display: grid;
  gap: 8px;
}

.ppt-field span,
.ppt-field small {
  color: #42566b;
  font-size: 13px;
  font-weight: 700;
}

.ppt-field small {
  font-weight: 500;
  color: #8493a2;
}

.ppt-field input,
.ppt-field textarea,
.ppt-field select,
.ppt-outline-row input,
.ppt-outline-row select {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #d8e2ea;
  border-radius: 7px;
  outline: none;
  font: inherit;
}

.ppt-field input:focus,
.ppt-field textarea:focus,
.ppt-outline-row input:focus,
.ppt-outline-row select:focus {
  border-color: #6084a4;
}

.ppt-textarea {
  min-height: 120px;
  resize: vertical;
}

.ppt-textarea--tall {
  min-height: 180px;
}

.ppt-source-card {
  border: 1px solid #dfe6ed;
  border-radius: 8px;
  overflow: hidden;
}

.ppt-source-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border-bottom: 1px solid #edf1f5;
  background: #fafcfd;
}

.ppt-source-card__status {
  display: flex;
  gap: 10px;
  min-width: 0;
}

.ppt-source-card__icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: #4f779e;
  color: #fff;
  font-size: 11px;
  font-weight: 800;
}

.ppt-source-card__status strong,
.ppt-source-card__status small {
  display: block;
}

.ppt-source-card__status small {
  color: #8493a2;
  font-size: 12px;
}

.ppt-source-card__actions {
  display: flex;
  gap: 10px;
}

.ppt-source-card textarea {
  border: 0;
  border-radius: 0;
}

.ppt-upload-preview__card {
  padding: 16px;
  height: 100%;
}

.ppt-upload-preview__head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
}

.ppt-upload-preview__content {
  margin: 0;
  max-height: 420px;
  overflow: auto;
  color: #42566b;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.ppt-outline-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(260px, .6fr);
  gap: 16px;
}

.ppt-outline-table {
  display: grid;
  gap: 8px;
  margin: 14px 0;
}

.ppt-outline-table__head,
.ppt-outline-row {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) 120px 180px;
  gap: 10px;
  align-items: center;
}

.ppt-outline-table__head {
  padding: 0 4px;
  color: #8493a2;
  font-size: 12px;
  font-weight: 700;
}

.ppt-outline-row {
  padding: 8px;
  border: 1px solid #e3e9ef;
  border-radius: 8px;
  background: #fbfcfe;
}

.ppt-outline-row__index {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #edf4fa;
  color: #41617f;
  font-size: 12px;
  font-weight: 700;
}

.ppt-outline-row__actions {
  display: flex;
  gap: 8px;
}

.ppt-outline-preview {
  padding: 16px;
  max-height: 620px;
  overflow: auto;
}

.ppt-outline-preview ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 8px;
}

.ppt-outline-preview li {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  padding: 8px 10px;
  border-radius: 6px;
  background: #f7f9fb;
}

.ppt-outline-preview__level-1 {
  margin-left: 0;
}

.ppt-outline-preview__level-2 {
  margin-left: 12px;
}

.ppt-outline-preview__level-3 {
  margin-left: 24px;
}

.ppt-outline-preview small {
  color: #8493a2;
  font-size: 11px;
}

.ppt-settings-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 18px;
}

.ppt-settings-form h2 {
  margin: 0 0 12px;
}

.ppt-toggle-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 44px;
  padding: 8px 0;
  border-bottom: 1px solid #eef2f6;
}

.ppt-toggle-row strong,
.ppt-toggle-row small {
  display: block;
}

.ppt-toggle-row small {
  margin-top: 2px;
  color: #8493a2;
  font-size: 12px;
}

.ppt-segmented {
  display: flex;
  gap: 6px;
  padding: 4px;
  border-radius: 8px;
  background: #f2f4f7;
}

.ppt-segmented button {
  flex: 1;
  padding: 8px 10px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #62758a;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.ppt-segmented__item--active {
  background: #fff !important;
  color: #294f74 !important;
  box-shadow: 0 1px 4px rgba(30, 50, 90, .08);
}

.ppt-settings-aside {
  padding: 16px;
}

.ppt-template-summary {
  display: flex;
  gap: 12px;
  margin-bottom: 14px;
}

.ppt-template-summary img {
  width: 88px;
  height: 52px;
  object-fit: cover;
  border-radius: 6px;
}

.ppt-template-summary span,
.ppt-template-summary p {
  display: block;
  color: #718096;
  font-size: 12px;
}

.ppt-stats {
  display: flex;
  gap: 16px;
  margin: 0;
}

.ppt-stats--block {
  flex-direction: column;
  gap: 10px;
  margin-bottom: 14px;
}

.ppt-stats div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.ppt-stats dt {
  color: #8493a2;
  font-size: 12px;
}

.ppt-stats dd {
  margin: 0;
  font-weight: 700;
}

.ppt-slide-rail {
  border: 1px solid #e0e7ed;
  border-radius: 10px;
  background: #fff;
  overflow: hidden;
  max-height: calc(100vh - 280px);
  display: flex;
  flex-direction: column;
}

.ppt-slide-rail__head {
  display: flex;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 1px solid #edf1f5;
  background: #fafcfd;
  font-size: 12px;
}

.ppt-slide-rail__item {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
  width: 100%;
  padding: 10px 14px;
  border: 0;
  border-bottom: 1px solid #f0f3f6;
  background: #fff;
  text-align: left;
  cursor: pointer;
}

.ppt-slide-rail__item--active {
  background: #edf4fa;
}

.ppt-slide-rail__item > span {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #e8eef3;
  font-size: 11px;
  font-weight: 700;
}

.ppt-slide-rail__item strong,
.ppt-slide-rail__item small {
  display: block;
}

.ppt-slide-rail__item small {
  margin-top: 2px;
  color: #8493a2;
  font-size: 11px;
}

.ppt-editor-stage {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.ppt-editor-preview {
  position: relative;
  min-height: 420px;
  border: 1px solid #dfe6ed;
  border-radius: 10px;
  background: #f7f9fb;
  overflow: hidden;
}

.ppt-editor-preview img {
  width: 100%;
  height: 100%;
  min-height: 420px;
  object-fit: contain;
  cursor: zoom-in;
}

.ppt-editor-preview__status {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  padding: 10px 14px;
  border-radius: 8px;
  background: rgba(255, 255, 255, .92);
  color: #516579;
  font-size: 13px;
}

.ppt-editor-preview__status--loading {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ppt-editor-form {
  display: grid;
  gap: 12px;
  align-content: start;
  padding: 16px;
  border: 1px solid #e0e7ed;
  border-radius: 10px;
  background: #fff;
  max-height: calc(100vh - 280px);
  overflow: auto;
}

.ppt-lock-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border: 1px solid #dfe6ed;
  border-radius: 8px;
  background: #fafcfd;
}

.ppt-lock-card--locked {
  border-color: #b8cad8;
  background: #edf4fa;
}

.ppt-lock-card strong,
.ppt-lock-card p,
.ppt-lock-card small {
  display: block;
}

.ppt-lock-card p,
.ppt-lock-card small {
  margin: 4px 0 0;
  color: #718096;
  font-size: 12px;
}

.ppt-editor-nav {
  display: flex;
  gap: 8px;
}

.ppt-progress-layout {
  display: grid;
  grid-template-columns: minmax(280px, .8fr) minmax(0, 1.2fr);
  gap: 20px;
  padding: 24px;
  border: 1px solid #e0e7ed;
  border-radius: 10px;
  background: #fff;
}

.ppt-progress-main {
  display: grid;
  place-items: start;
  gap: 10px;
}

.ppt-progress-ring {
  display: grid;
  place-items: center;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  background: conic-gradient(#4f779e var(--progress), #e7edf2 0);
}

.ppt-progress-ring > div {
  display: flex;
  align-items: baseline;
  justify-content: center;
  width: 96px;
  height: 96px;
  border-radius: 50%;
  background: #fff;
}

.ppt-progress-ring strong {
  font-size: 28px;
}

.ppt-progress-track {
  width: 100%;
  max-width: 360px;
  height: 8px;
  border-radius: 999px;
  background: #e3e9ef;
  overflow: hidden;
}

.ppt-progress-track div {
  height: 100%;
  background: #4f779e;
  transition: width .25s;
}

.ppt-progress-steps {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 12px;
}

.ppt-progress-steps li {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  gap: 10px;
  padding: 12px;
  border: 1px solid #e3e9ef;
  border-radius: 8px;
}

.ppt-progress-steps li span {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #e8eef3;
  font-size: 11px;
  font-weight: 700;
}

.ppt-progress-steps .ppt-progress-step.generation-item__dot--active span,
.ppt-progress-steps .ppt-progress-step.generation-item__dot--done span {
  background: #4f779e;
  color: #fff;
}

.ppt-result-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 16px;
}

.ppt-result-hero {
  display: flex;
  gap: 14px;
  align-items: center;
  margin-bottom: 16px;
}

.ppt-result-hero__icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: #e8f5ee;
  color: #2f8f5b;
  font-size: 22px;
  font-weight: 700;
}

.ppt-result-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  padding: 12px 14px;
  border: 1px solid #e3e9ef;
  border-radius: 8px;
}

.ppt-result-summary > div {
  display: flex;
  gap: 10px;
  align-items: center;
}

.ppt-result-summary strong,
.ppt-result-summary small {
  display: block;
}

.ppt-result-summary small {
  color: #8493a2;
  font-size: 12px;
}

.ppt-slide-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
}

.ppt-slide-thumb {
  display: grid;
  gap: 6px;
  padding: 0;
  border: 1px solid #dfe6ed;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
  text-align: left;
  cursor: pointer;
}

.ppt-slide-thumb img {
  width: 100%;
  aspect-ratio: 16 / 9;
  object-fit: cover;
}

.ppt-slide-thumb__fallback {
  display: grid;
  gap: 6px;
  aspect-ratio: 16 / 9;
  padding: 12px;
  background: #f3f6f9;
}

.ppt-slide-thumb > span:last-child {
  padding: 0 10px 10px;
  color: #718096;
  font-size: 11px;
}

.ppt-replace-row,
.ppt-task-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
  padding: 14px;
  border: 1px solid #e3e9ef;
  border-radius: 8px;
  background: #fafcfd;
}

.ppt-task-card {
  flex-direction: column;
  align-items: stretch;
  margin-top: 16px;
}

.ppt-task-card__head,
.ppt-task-card__stats {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.ppt-task-card__stats div {
  display: grid;
  gap: 2px;
  text-align: center;
}

.ppt-task-card__stats span {
  color: #8493a2;
  font-size: 11px;
}

.ppt-task-card__track {
  height: 6px;
  border-radius: 999px;
  background: #e3e9ef;
  overflow: hidden;
}

.ppt-task-card__track div {
  height: 100%;
  background: #4f779e;
}

.ppt-result-aside,
.ppt-export-panel {
  padding: 16px;
}

.ppt-export-row,
.ppt-export-choice {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  margin-top: 10px;
  padding: 12px;
  border: 1px solid #dfe6ed;
  border-radius: 8px;
  background: #fff;
  text-align: left;
  cursor: pointer;
}

.ppt-export-row--active,
.ppt-export-choice--selected {
  border-color: #4f779e;
  background: #edf4fa;
}

.ppt-export-row--disabled,
.ppt-export-choice--disabled {
  opacity: .55;
  cursor: not-allowed;
}

.ppt-export-panel {
  max-width: 560px;
  margin: 0 auto;
}

.ppt-download-ready {
  margin-top: 14px;
  padding: 16px;
  border: 1px solid #dfe6ed;
  border-radius: 8px;
  background: #f8fbfd;
  text-align: center;
}

.ppt-btn {
  min-height: 38px;
  padding: 0 14px;
  border: 1px solid #d6e0e8;
  border-radius: 7px;
  background: #fff;
  color: #3e6180;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.ppt-btn:disabled {
  opacity: .5;
  cursor: not-allowed;
}

.ppt-btn--primary {
  border-color: #326994;
  background: #326994;
  color: #fff;
}

.ppt-btn--ghost {
  background: #f8fbfd;
}

.ppt-btn--block {
  width: 100%;
}

.ppt-link {
  border: 0;
  background: transparent;
  color: #3d6d98;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.ppt-link--danger {
  color: #a54239;
}

.ppt-badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 8px;
  border-radius: 999px;
  background: #edf4fa;
  color: #41617f;
  font-size: 11px;
  font-weight: 700;
}

.ppt-badge--success {
  background: #e8f5ee;
  color: #2f8f5b;
}

.ppt-file-icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 7px;
  background: #f07032;
  color: #fff;
  font-size: 13px;
  font-weight: 800;
}

.ppt-switch {
  width: 18px;
  height: 18px;
  accent-color: #4f779e;
  cursor: pointer;
}

.ppt-alert {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid #dfe6ed;
  border-radius: 8px;
  background: #fafcfd;
}

.ppt-alert--warning {
  border-color: #ead7bc;
  background: #fff9f0;
}

.ppt-alert p,
.ppt-empty p,
.ppt-hint,
.ppt-feedback p {
  margin: 4px 0 0;
  color: #718096;
  font-size: 12px;
}

.ppt-feedback {
  padding: 12px 14px;
  border: 1px solid #dfe6ed;
  border-radius: 8px;
  background: #fff;
}

.ppt-feedback__head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.ppt-feedback__track {
  height: 6px;
  margin-bottom: 8px;
  border-radius: 999px;
  background: #e8eef3;
  overflow: hidden;
}

.ppt-feedback__track div {
  height: 100%;
  background: #4f779e;
}

.ppt-empty {
  padding: 28px;
  color: #8493a2;
  text-align: center;
}

.ppt-spinner {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid #d8e2ea;
  border-top-color: #4f779e;
  border-radius: 50%;
  animation: ppt-spin .8s linear infinite;
}

.ppt-modal {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: grid;
  place-items: center;
  padding: 24px;
}

.ppt-modal__backdrop {
  position: absolute;
  inset: 0;
  background: rgba(15, 24, 36, .42);
}

.ppt-modal__panel {
  position: relative;
  width: min(920px, calc(100vw - 48px));
  max-height: calc(100vh - 48px);
  display: flex;
  flex-direction: column;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 18px 48px rgba(20, 35, 50, .18);
  overflow: hidden;
}

.ppt-modal__panel--history {
  width: min(680px, calc(100vw - 48px));
}

.ppt-modal__head,
.ppt-modal__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid #edf1f5;
}

.ppt-modal__foot {
  border-bottom: 0;
  border-top: 1px solid #edf1f5;
}

.ppt-modal__head span {
  display: block;
  color: #8493a2;
  font-size: 12px;
}

.ppt-modal__close {
  border: 0;
  background: transparent;
  color: #62758a;
  font-size: 24px;
  line-height: 1;
  cursor: pointer;
}

.ppt-layout-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
  padding: 16px;
  overflow: auto;
}

.ppt-layout-item {
  display: grid;
  gap: 8px;
  padding: 0;
  border: 1px solid #dfe6ed;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
  cursor: pointer;
}

.ppt-layout-item img {
  width: 100%;
  aspect-ratio: 16 / 9;
  object-fit: cover;
}

.ppt-layout-item__empty {
  display: grid;
  place-items: center;
  aspect-ratio: 16 / 9;
  background: #f3f6f9;
  color: #8493a2;
  font-size: 12px;
}

.ppt-layout-item span {
  padding: 0 10px 10px;
  font-size: 12px;
}

.ppt-modal__loading {
  display: grid;
  place-items: center;
  gap: 10px;
  padding: 48px 16px;
  color: #718096;
}

.ppt-history-tabs {
  display: flex;
  gap: 8px;
  padding: 0 16px 12px;
}

.ppt-history-tabs button {
  padding: 8px 12px;
  border: 1px solid #dfe6ed;
  border-radius: 999px;
  background: #fff;
  cursor: pointer;
}

.ppt-history-tabs__item--active {
  border-color: #4f779e !important;
  background: #edf4fa !important;
  color: #294f74;
}

.ppt-history-list {
  padding: 0 16px 16px;
  overflow: auto;
  display: grid;
  gap: 10px;
}

.ppt-history-card {
  padding: 12px;
  border: 1px solid #e3e9ef;
  border-radius: 8px;
}

.ppt-history-card__head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.ppt-history-card__actions {
  display: flex;
  gap: 10px;
  margin-top: 10px;
}

@keyframes ppt-spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 1180px) {
  .ppt-workspace--setup-library {
    grid-template-columns: 180px minmax(0, 1fr);
  }

  .ppt-inspector {
    display: none;
  }

  .ppt-workspace--editor {
    grid-template-columns: 200px minmax(0, 1fr);
  }

  .ppt-editor-form {
    grid-column: 1 / -1;
  }

  .ppt-result-layout,
  .ppt-settings-grid,
  .ppt-upload-grid,
  .ppt-outline-layout,
  .ppt-progress-layout,
  .ppt-detail-layout {
    grid-template-columns: 1fr;
  }

  .ppt-studio__steps {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .ppt-studio__header,
  .ppt-studio__footer,
  .ppt-panel__head,
  .ppt-result-summary,
  .ppt-replace-row,
  .ppt-lock-card {
    flex-direction: column;
    align-items: stretch;
  }

  .ppt-workspace--setup-library,
  .ppt-workspace--setup-upload,
  .ppt-workspace--setup-detail,
  .ppt-workspace--editor {
    grid-template-columns: 1fr;
  }

  .ppt-sidebar {
    position: static;
    display: flex;
    gap: 8px;
    overflow: auto;
  }

  .ppt-sidebar h2 {
    display: none;
  }

  .ppt-sidebar__item {
    width: auto;
    white-space: nowrap;
  }

  .ppt-outline-table__head,
  .ppt-outline-row {
    grid-template-columns: 1fr;
  }

  .ppt-studio__steps {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
