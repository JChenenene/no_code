<template>
  <a-layout class="basic-layout">
    <!-- 生成页使用沉浸式布局，避免全局头部打断作品预览 -->
    <GlobalHeader v-if="!hideGlobalChrome" />
    <!-- 主要内容区域 -->
    <a-layout-content class="main-content" :class="{ 'main-content--immersive': hideGlobalChrome }">
      <router-view />
    </a-layout-content>
    <!-- 底部版权信息 -->
    <GlobalFooter v-if="!hideGlobalChrome" />
  </a-layout>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalFooter from '@/components/GlobalFooter.vue'

const route = useRoute()
const hideGlobalChrome = computed(() => route.path.startsWith('/app/chat/'))
</script>

<style scoped>
.basic-layout {
  background: none;
}

.main-content {
  width: 100%;
  padding: 0;
  background: none;
  margin: 0;
}

.main-content--immersive {
  min-height: 100vh;
}
</style>
