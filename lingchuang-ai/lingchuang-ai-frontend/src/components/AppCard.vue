<template>
  <div class="app-card" :class="{ 'app-card--featured': featured }">
    <div class="app-preview">
      <img v-if="shouldShowCover" :src="coverSrc" :alt="app.appName" @error="handleCoverError" />
      <div v-else class="app-placeholder">
        <span class="placeholder-mark">LC</span>
        <span class="placeholder-text">零创AI 预览</span>
      </div>
      <div class="preview-badge">{{ featured ? '精选作品' : '我的应用' }}</div>
      <div class="app-overlay">
        <a-space>
          <a-button type="primary" @click="handleViewChat">查看对话</a-button>
          <a-button v-if="app.deployKey" type="default" @click="handleViewWork">查看作品</a-button>
        </a-space>
      </div>
    </div>
    <div class="app-info">
      <div class="app-info-left">
        <a-avatar :src="app.user?.userAvatar" :size="40">
          {{ app.user?.userName?.charAt(0) || 'U' }}
        </a-avatar>
      </div>
      <div class="app-info-right">
        <h3 class="app-title">{{ app.appName || '未命名应用' }}</h3>
        <p class="app-author">
          {{ app.user?.userName || (featured ? '零创AI精选' : '待命名创作者') }}
        </p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { API_BASE_URL } from '@/config/env'

interface Props {
  app: API.AppVO
  featured?: boolean
}

interface Emits {
  (e: 'view-chat', appId: string | number | undefined): void
  (e: 'view-work', app: API.AppVO): void
}

const props = withDefaults(defineProps<Props>(), {
  featured: false,
})

const emit = defineEmits<Emits>()
const coverLoadFailed = ref(false)
const shouldShowCover = ref(Boolean(props.app.cover))
const coverSrc = computed(() => {
  if (!props.app.cover || !props.app.id || coverLoadFailed.value) {
    return ''
  }
  return `${API_BASE_URL}/app/cover/${props.app.id}`
})

watch(
  () => props.app.cover,
  (cover) => {
    coverLoadFailed.value = false
    shouldShowCover.value = Boolean(cover)
  },
  { immediate: true },
)

const handleViewChat = () => {
  emit('view-chat', props.app.id)
}

const handleViewWork = () => {
  emit('view-work', props.app)
}

const handleCoverError = () => {
  coverLoadFailed.value = true
  shouldShowCover.value = false
}
</script>

<style scoped>
.app-card {
  background: linear-gradient(180deg, rgba(7, 20, 35, 0.92) 0%, rgba(3, 12, 24, 0.92) 100%);
  border-radius: 24px;
  overflow: hidden;
  box-shadow: 0 18px 40px rgba(2, 8, 23, 0.38);
  backdrop-filter: blur(18px);
  border: 1px solid rgba(125, 211, 252, 0.12);
  transition:
    transform 0.3s,
    box-shadow 0.3s,
    border-color 0.3s;
  cursor: pointer;
}

.app-card:hover {
  transform: translateY(-8px);
  box-shadow: 0 24px 56px rgba(2, 8, 23, 0.48);
  border-color: rgba(56, 189, 248, 0.36);
}

.app-card--featured {
  border-color: rgba(96, 165, 250, 0.28);
}

.app-preview {
  height: 180px;
  background:
    radial-gradient(circle at 25% 20%, rgba(56, 189, 248, 0.3), transparent 40%),
    linear-gradient(135deg, #0f172a 0%, #0b2239 50%, #07111d 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  position: relative;
}

.app-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.app-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  color: rgba(191, 219, 254, 0.85);
}

.placeholder-mark {
  font-size: 42px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.placeholder-text {
  font-size: 13px;
  color: rgba(191, 219, 254, 0.62);
}

.preview-badge {
  position: absolute;
  top: 14px;
  left: 14px;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(2, 8, 23, 0.6);
  border: 1px solid rgba(125, 211, 252, 0.16);
  color: #e0f2fe;
  font-size: 12px;
  letter-spacing: 0.08em;
}

.app-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(2, 8, 23, 0.56);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.3s;
}

.app-card:hover .app-overlay {
  opacity: 1;
}

.app-info {
  padding: 18px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.app-info-left {
  flex-shrink: 0;
}

.app-info-right {
  flex: 1;
  min-width: 0;
}

.app-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 4px;
  color: #f8fbff;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.app-author {
  font-size: 14px;
  color: rgba(191, 219, 254, 0.68);
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
