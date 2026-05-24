<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import message from 'ant-design-vue/es/message'
import { ArrowUpOutlined } from '@ant-design/icons-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { addApp, listMyAppVoByPage, listGoodAppVoByPage } from '@/api/appController'
import { getDeployUrl } from '@/config/env'
import AppCard from '@/components/AppCard.vue'

const router = useRouter()
const loginUserStore = useLoginUserStore()
const composerRef = ref<HTMLElement | null>(null)

const userPrompt = ref('')
const creating = ref(false)

const promptPresets = [
  {
    label: '品牌发布页',
    prompt:
      '打造一个高端科技感品牌发布页，包含品牌故事、核心能力、产品矩阵、案例展示和预约咨询入口。整体视觉偏深色与冷蓝高光，突出未来感与专业度。',
  },
  {
    label: '企业官网',
    prompt:
      '设计一个现代化企业官网，包含公司介绍、解决方案、客户案例、招聘信息和联系我们页面。要求信息结构清晰，适配移动端，并保留专业商务感。',
  },
  {
    label: '在线商城',
    prompt:
      '生成一个完整的在线商城应用，包含商品列表、详情页、购物车、订单结算、会员中心和评价模块，整体风格高级、简洁、易转化。',
  },
  {
    label: '作品展示站',
    prompt:
      '制作一个设计师作品展示站，包含精选作品、项目详情、服务介绍、客户评价和联系方式，突出视觉冲击力和图片浏览体验。',
  },
]

const signalItems = [
  {
    index: '01',
    title: '自然语言生成',
    description: '一句话定义目标用户、页面结构与视觉方向，系统自动生成首版应用。',
  },
  {
    index: '02',
    title: '连续对话迭代',
    description: '进入应用对话空间后继续细化需求，让页面、内容和交互逐轮进化。',
  },
  {
    index: '03',
    title: '部署与展示一体化',
    description: '生成完成后即可部署、查看作品，并在精选列表中持续沉淀可展示成果。',
  },
]

const myApps = ref<API.AppVO[]>([])
const myAppsPage = reactive({
  current: 1,
  pageSize: 6,
  total: 0,
})

const featuredApps = ref<API.AppVO[]>([])
const featuredAppsPage = reactive({
  current: 1,
  pageSize: 6,
  total: 0,
})

const scrollToComposer = () => {
  composerRef.value?.scrollIntoView({
    behavior: 'smooth',
    block: 'center',
  })
}

const setPrompt = (prompt: string) => {
  userPrompt.value = prompt
  scrollToComposer()
}

const createApp = async () => {
  if (!userPrompt.value.trim()) {
    message.warning('请输入应用描述')
    return
  }

  if (!loginUserStore.loginUser.id) {
    message.warning('请先登录')
    await router.push('/user/login')
    return
  }

  creating.value = true
  try {
    const res = await addApp({
      initPrompt: userPrompt.value.trim(),
    })

    if (res.data.code === 0 && res.data.data) {
      message.success('应用创建成功')
      const appId = String(res.data.data)
      await router.push(`/app/chat/${appId}`)
    } else {
      message.error('创建失败：' + res.data.message)
    }
  } catch (error) {
    console.error('创建应用失败：', error)
    message.error('创建失败，请重试')
  } finally {
    creating.value = false
  }
}

const loadMyApps = async () => {
  if (!loginUserStore.loginUser.id) {
    return
  }

  try {
    const res = await listMyAppVoByPage({
      pageNum: myAppsPage.current,
      pageSize: myAppsPage.pageSize,
      sortField: 'createTime',
      sortOrder: 'desc',
    })

    if (res.data.code === 0 && res.data.data) {
      myApps.value = res.data.data.records || []
      myAppsPage.total = Number(res.data.data.totalRow || 0)
    }
  } catch (error) {
    console.error('加载我的应用失败：', error)
  }
}

const loadFeaturedApps = async () => {
  try {
    const res = await listGoodAppVoByPage({
      pageNum: featuredAppsPage.current,
      pageSize: featuredAppsPage.pageSize,
      sortField: 'createTime',
      sortOrder: 'desc',
    })

    if (res.data.code === 0 && res.data.data) {
      featuredApps.value = res.data.data.records || []
      featuredAppsPage.total = Number(res.data.data.totalRow || 0)
    }
  } catch (error) {
    console.error('加载精选应用失败：', error)
  }
}

const viewChat = (appId: string | number | undefined) => {
  if (appId) {
    router.push(`/app/chat/${appId}?view=1`)
  }
}

const viewWork = (app: API.AppVO) => {
  if (app.deployKey) {
    const url = getDeployUrl(app.deployKey)
    window.open(url, '_blank')
  }
}

let handleMouseMove: ((event: MouseEvent) => void) | null = null

onMounted(() => {
  loadMyApps()
  loadFeaturedApps()

  handleMouseMove = (event: MouseEvent) => {
    const { clientX, clientY } = event
    const { innerWidth, innerHeight } = window
    const x = (clientX / innerWidth) * 100
    const y = (clientY / innerHeight) * 100

    document.documentElement.style.setProperty('--mouse-x', `${x}%`)
    document.documentElement.style.setProperty('--mouse-y', `${y}%`)
  }

  document.addEventListener('mousemove', handleMouseMove)
})

onBeforeUnmount(() => {
  if (handleMouseMove) {
    document.removeEventListener('mousemove', handleMouseMove)
  }
})
</script>

<template>
  <div id="homePage">
    <div class="ambient ambient--one"></div>
    <div class="ambient ambient--two"></div>
    <div class="container">
      <section class="hero-section">
        <div class="hero-copy">
          <p class="hero-label">LINGCHUANG AI</p>
          <h1 class="hero-title">零创AI</h1>
          <p class="hero-subtitle">零代码应用开发平台</p>
          <p class="hero-description">
            从一句自然语言开始，快速生成页面、对话与可访问作品，让创意直接进入可发布状态。
          </p>
          <div class="hero-tags">
            <span>自然语言建站</span>
            <span>连续对话迭代</span>
            <span>一键部署展示</span>
          </div>
        </div>

        <div ref="composerRef" class="composer-card">
          <div class="composer-head">
            <div>
              <p class="composer-kicker">创作工作台</p>
              <h2>描述你的想法，立即生成首个应用版本</h2>
            </div>
            <span class="live-indicator">实时生成</span>
          </div>
          <a-textarea
            v-model:value="userPrompt"
            placeholder="例如：创建一个高端科技感 AI 产品官网，包含能力介绍、案例展示、预约咨询和多端适配。"
            :rows="5"
            :maxlength="1000"
            class="prompt-input"
          />
          <div class="composer-foot">
            <p>建议写清目标用户、核心页面和视觉风格，零创AI 会自动进入应用构建流程。</p>
            <a-button
              type="primary"
              size="large"
              class="create-button"
              @click="createApp"
              :loading="creating"
            >
              <template #icon>
                <ArrowUpOutlined />
              </template>
              立即生成
            </a-button>
          </div>
        </div>
      </section>

      <section class="preset-section">
        <div class="section-head">
          <span class="section-kicker">高频模板</span>
          <h2 class="section-title">从成熟场景开始，加速你的零代码创作节奏</h2>
          <p class="section-description">
            先选一个基础模版，再继续通过对话微调信息结构、视觉气质和上线目标。
          </p>
        </div>
        <div class="quick-actions">
          <button
            v-for="preset in promptPresets"
            :key="preset.label"
            type="button"
            class="preset-chip"
            @click="setPrompt(preset.prompt)"
          >
            {{ preset.label }}
          </button>
        </div>
      </section>

      <section class="signal-strip">
        <article v-for="item in signalItems" :key="item.index" class="signal-item">
          <span class="signal-number">{{ item.index }}</span>
          <h3>{{ item.title }}</h3>
          <p>{{ item.description }}</p>
        </article>
      </section>

      <section class="section">
        <div class="section-head section-head--inline">
          <div>
            <span class="section-kicker">我的空间</span>
            <h2 class="section-title">我的应用</h2>
            <p class="section-description">
              回到已有项目，查看对话记录、继续迭代页面，或直接打开已经部署的应用作品。
            </p>
          </div>
          <a-button type="link" class="section-link" @click="scrollToComposer">
            继续创建
          </a-button>
        </div>
        <div class="app-grid">
          <AppCard
            v-for="app in myApps"
            :key="app.id"
            :app="app"
            @view-chat="viewChat"
            @view-work="viewWork"
          />
        </div>
        <div class="pagination-wrapper">
          <a-pagination
            v-model:current="myAppsPage.current"
            v-model:page-size="myAppsPage.pageSize"
            :total="myAppsPage.total"
            :show-size-changer="false"
            :show-total="(total: number) => `共 ${total} 个应用`"
            @change="loadMyApps"
          />
        </div>
      </section>

      <section class="section">
        <div class="section-head">
          <span class="section-kicker">精选作品</span>
          <h2 class="section-title">灵感精选</h2>
          <p class="section-description">
            查看已经完成部署的精选应用，感受零创AI 在不同场景下的生成能力与呈现质感。
          </p>
        </div>
        <div class="featured-grid">
          <AppCard
            v-for="app in featuredApps"
            :key="app.id"
            :app="app"
            :featured="true"
            @view-chat="viewChat"
            @view-work="viewWork"
          />
        </div>
        <div class="pagination-wrapper">
          <a-pagination
            v-model:current="featuredAppsPage.current"
            v-model:page-size="featuredAppsPage.pageSize"
            :total="featuredAppsPage.total"
            :show-size-changer="false"
            :show-total="(total: number) => `共 ${total} 个案例`"
            @change="loadFeaturedApps"
          />
        </div>
      </section>

      <section class="closing-panel">
        <span class="closing-kicker">READY TO BUILD</span>
        <h2>让你的下一个想法直接进入可访问状态</h2>
        <p>回到创作工作台，输入一句需求，零创AI 会为你生成首版应用并衔接后续部署流程。</p>
        <a-button type="primary" size="large" class="closing-button" @click="scrollToComposer">
          开始生成
        </a-button>
      </section>
    </div>
  </div>
</template>

<style scoped>
#homePage {
  --bg-primary: #04111d;
  --line-color: rgba(125, 211, 252, 0.1);
  --text-primary: #f8fbff;
  --text-secondary: rgba(226, 232, 240, 0.76);
  width: 100%;
  margin: 0;
  padding: 0 0 88px;
  min-height: 100vh;
  background:
    radial-gradient(circle at 16% 20%, rgba(56, 189, 248, 0.14), transparent 32%),
    radial-gradient(circle at 82% 18%, rgba(59, 130, 246, 0.18), transparent 34%),
    linear-gradient(180deg, #07111d 0%, #04111d 42%, #020712 100%);
  position: relative;
  overflow: hidden;
  color: var(--text-primary);
}

#homePage::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(var(--line-color) 1px, transparent 1px),
    linear-gradient(90deg, var(--line-color) 1px, transparent 1px);
  background-size:
    96px 96px,
    96px 96px;
  pointer-events: none;
  mask-image: linear-gradient(180deg, rgba(0, 0, 0, 0.55), transparent 95%);
}

#homePage::after {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(
      560px circle at var(--mouse-x, 50%) var(--mouse-y, 50%),
      rgba(56, 189, 248, 0.12) 0%,
      rgba(37, 99, 235, 0.08) 42%,
      transparent 78%
    ),
    linear-gradient(135deg, transparent 0%, rgba(56, 189, 248, 0.04) 48%, transparent 100%);
  pointer-events: none;
  animation: lightPulse 10s ease-in-out infinite alternate;
}

.ambient {
  position: absolute;
  border-radius: 999px;
  filter: blur(18px);
  opacity: 0.5;
  pointer-events: none;
}

.ambient--one {
  top: 16%;
  right: -120px;
  width: 320px;
  height: 320px;
  background: radial-gradient(circle, rgba(56, 189, 248, 0.26), transparent 72%);
  animation: floatAmbient 12s ease-in-out infinite;
}

.ambient--two {
  bottom: 10%;
  left: -90px;
  width: 260px;
  height: 260px;
  background: radial-gradient(circle, rgba(59, 130, 246, 0.18), transparent 72%);
  animation: floatAmbient 16s ease-in-out infinite reverse;
}

@keyframes lightPulse {
  0% {
    opacity: 0.3;
  }

  100% {
    opacity: 0.7;
  }
}

@keyframes floatAmbient {
  0%,
  100% {
    transform: translate3d(0, 0, 0);
  }

  50% {
    transform: translate3d(0, -24px, 0);
  }
}

.container {
  max-width: 1240px;
  margin: 0 auto;
  padding: 36px 24px 0;
  position: relative;
  z-index: 2;
  width: 100%;
  box-sizing: border-box;
}

.hero-section {
  display: grid;
  grid-template-columns: minmax(0, 1.02fr) minmax(360px, 0.98fr);
  gap: 32px;
  align-items: end;
  min-height: calc(100vh - 170px);
  padding: 24px 0 52px;
}

.hero-copy {
  max-width: 640px;
}

.hero-label {
  margin: 0 0 20px;
  font-size: 12px;
  letter-spacing: 0.36em;
  color: rgba(125, 211, 252, 0.72);
}

.hero-title {
  margin: 0;
  font-size: clamp(60px, 9vw, 108px);
  font-weight: 700;
  line-height: 0.92;
  letter-spacing: -0.06em;
  color: #f8fbff;
  font-family:
    'Space Grotesk',
    'Segoe UI',
    'PingFang SC',
    sans-serif;
}

.hero-subtitle {
  margin: 18px 0 18px;
  font-size: clamp(26px, 3.8vw, 44px);
  font-weight: 500;
  line-height: 1.15;
  color: rgba(224, 242, 254, 0.94);
}

.hero-description {
  margin: 0;
  max-width: 560px;
  font-size: 18px;
  line-height: 1.8;
  color: var(--text-secondary);
}

.hero-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 32px;
}

.hero-tags span {
  padding: 9px 14px;
  border-radius: 999px;
  background: rgba(7, 20, 35, 0.72);
  border: 1px solid rgba(125, 211, 252, 0.16);
  color: #dbeafe;
  font-size: 13px;
  letter-spacing: 0.04em;
}

.composer-card {
  padding: 28px;
  border-radius: 28px;
  background:
    linear-gradient(180deg, rgba(11, 34, 57, 0.9) 0%, rgba(3, 12, 24, 0.92) 100%);
  border: 1px solid rgba(125, 211, 252, 0.16);
  box-shadow: 0 28px 80px rgba(2, 8, 23, 0.45);
  backdrop-filter: blur(24px);
}

.composer-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.composer-kicker {
  margin: 0 0 8px;
  font-size: 12px;
  letter-spacing: 0.2em;
  color: rgba(125, 211, 252, 0.68);
}

.composer-head h2 {
  margin: 0;
  font-size: 28px;
  line-height: 1.35;
  color: #f8fbff;
}

.live-indicator {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(7, 20, 35, 0.82);
  border: 1px solid rgba(56, 189, 248, 0.18);
  color: #dbeafe;
  font-size: 12px;
  white-space: nowrap;
}

.live-indicator::before {
  content: '';
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #22c55e;
  box-shadow: 0 0 0 6px rgba(34, 197, 94, 0.12);
}

.prompt-input {
  border-radius: 20px;
  border: 1px solid rgba(125, 211, 252, 0.12);
  font-size: 16px;
  padding: 16px 18px;
  background: rgba(2, 8, 23, 0.52);
  color: #f8fbff;
  caret-color: #f8fbff;
  backdrop-filter: blur(18px);
  box-shadow: inset 0 0 0 1px rgba(125, 211, 252, 0.04);
}

.prompt-input::placeholder {
  color: rgba(191, 219, 254, 0.46);
}

.composer-foot {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 20px;
  margin-top: 18px;
}

.composer-foot p {
  margin: 0;
  max-width: 420px;
  color: rgba(191, 219, 254, 0.66);
  line-height: 1.75;
}

.create-button {
  min-width: 148px;
  height: 48px;
  padding: 0 20px;
  border-radius: 999px;
  border: none;
  background: linear-gradient(135deg, #2563eb 0%, #0ea5e9 100%);
  box-shadow: 0 18px 38px rgba(14, 165, 233, 0.24);
}

.preset-section,
.section {
  margin-top: 28px;
}

.section-head {
  margin-bottom: 24px;
}

.section-head--inline {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 16px;
}

.section-kicker {
  display: inline-block;
  margin-bottom: 10px;
  font-size: 12px;
  letter-spacing: 0.24em;
  color: rgba(125, 211, 252, 0.64);
}

.section-title {
  margin: 0;
  font-size: 34px;
  font-weight: 600;
  color: #f8fbff;
}

.section-description {
  margin: 12px 0 0;
  max-width: 620px;
  color: var(--text-secondary);
  line-height: 1.8;
}

.section-link {
  padding: 0;
  color: #7dd3fc;
}

.quick-actions {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.preset-chip {
  position: relative;
  overflow: hidden;
  min-height: 96px;
  padding: 20px 18px;
  border-radius: 22px;
  border: 1px solid rgba(125, 211, 252, 0.12);
  background: linear-gradient(180deg, rgba(7, 20, 35, 0.78) 0%, rgba(3, 12, 24, 0.84) 100%);
  color: #eff6ff;
  font-size: 15px;
  text-align: left;
  transition:
    transform 0.3s,
    border-color 0.3s,
    box-shadow 0.3s;
  cursor: pointer;
}

.preset-chip::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(120deg, transparent, rgba(56, 189, 248, 0.14), transparent);
  transition: left 0.5s;
}

.preset-chip:hover::before {
  left: 100%;
}

.preset-chip:hover {
  transform: translateY(-4px);
  border-color: rgba(56, 189, 248, 0.36);
  box-shadow: 0 22px 40px rgba(2, 8, 23, 0.32);
}

.signal-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
  margin: 42px 0 64px;
}

.signal-item {
  padding: 24px;
  border-radius: 24px;
  background: rgba(4, 16, 30, 0.72);
  border: 1px solid rgba(125, 211, 252, 0.1);
  box-shadow: 0 16px 38px rgba(2, 8, 23, 0.24);
}

.signal-number {
  display: inline-block;
  margin-bottom: 12px;
  color: rgba(125, 211, 252, 0.56);
  font-size: 12px;
  letter-spacing: 0.28em;
}

.signal-item h3 {
  margin: 0 0 12px;
  font-size: 22px;
  color: #f8fbff;
}

.signal-item p {
  margin: 0;
  color: var(--text-secondary);
  line-height: 1.75;
}

.app-grid,
.featured-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
  margin-bottom: 32px;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 32px;
}

:deep(.ant-pagination .ant-pagination-item),
:deep(.ant-pagination .ant-pagination-prev),
:deep(.ant-pagination .ant-pagination-next) {
  background: rgba(7, 20, 35, 0.7);
  border-color: rgba(125, 211, 252, 0.12);
}

:deep(.ant-pagination .ant-pagination-item a),
:deep(.ant-pagination .ant-pagination-prev button),
:deep(.ant-pagination .ant-pagination-next button) {
  color: #dbeafe;
}

:deep(.ant-pagination .ant-pagination-item-active) {
  border-color: rgba(56, 189, 248, 0.6);
}

:deep(.ant-pagination .ant-pagination-total-text) {
  color: rgba(191, 219, 254, 0.7);
}

.closing-panel {
  margin-top: 72px;
  padding: 40px 32px;
  border-radius: 32px;
  text-align: center;
  background:
    linear-gradient(180deg, rgba(11, 34, 57, 0.88) 0%, rgba(3, 12, 24, 0.96) 100%);
  border: 1px solid rgba(125, 211, 252, 0.14);
  box-shadow: 0 24px 64px rgba(2, 8, 23, 0.34);
}

.closing-kicker {
  display: inline-block;
  margin-bottom: 12px;
  font-size: 12px;
  letter-spacing: 0.34em;
  color: rgba(125, 211, 252, 0.62);
}

.closing-panel h2 {
  margin: 0 0 16px;
  font-size: 36px;
  color: #f8fbff;
}

.closing-panel p {
  max-width: 640px;
  margin: 0 auto 24px;
  color: var(--text-secondary);
  line-height: 1.8;
}

.closing-button {
  min-width: 140px;
  border-radius: 999px;
  border: none;
  background: linear-gradient(135deg, #2563eb 0%, #0ea5e9 100%);
}

@media (max-width: 768px) {
  .container {
    padding: 24px 16px 0;
  }

  .hero-section,
  .quick-actions,
  .signal-strip {
    grid-template-columns: 1fr;
  }

  .hero-section {
    min-height: auto;
    padding: 12px 0 32px;
  }

  .hero-title {
    font-size: 52px;
    line-height: 1;
  }

  .hero-subtitle {
    font-size: 26px;
  }

  .hero-description {
    font-size: 16px;
  }

  .composer-card {
    padding: 22px 18px;
  }

  .composer-head,
  .composer-foot,
  .section-head--inline {
    flex-direction: column;
    align-items: flex-start;
  }

  .section-title,
  .closing-panel h2 {
    font-size: 28px;
  }

  .app-grid,
  .featured-grid {
    grid-template-columns: 1fr;
  }

  .closing-panel {
    padding: 32px 20px;
  }
}
</style>
