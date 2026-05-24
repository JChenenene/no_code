<template>
  <a-layout-header class="header">
    <div class="header-inner">
      <RouterLink to="/" class="brand-link">
        <div class="header-left">
          <div class="brand-emblem" aria-hidden="true">
            <span>LC</span>
          </div>
          <div class="brand-copy">
            <span class="brand-mark">LINGCHUANG AI</span>
            <h1 class="site-title">零创AI</h1>
          </div>
        </div>
      </RouterLink>
      <a-menu
        v-model:selectedKeys="selectedKeys"
        mode="horizontal"
        :items="menuItems"
        class="nav-menu"
        @click="handleMenuClick"
      />
      <div class="user-login-status">
        <div v-if="loginUserStore.loginUser.id">
          <a-dropdown>
            <a-space class="user-profile">
              <a-avatar :src="loginUserStore.loginUser.userAvatar" />
              {{ loginUserStore.loginUser.userName ?? '无名' }}
            </a-space>
            <template #overlay>
              <a-menu>
                <a-menu-item @click="doLogout">
                  <LogoutOutlined />
                  退出登录
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>
        <div v-else>
          <a-button type="primary" href="/user/login" class="login-button">登录工作台</a-button>
        </div>
      </div>
    </div>
  </a-layout-header>
</template>

<script setup lang="ts">
import { computed, h } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { MenuProps } from 'ant-design-vue/es/menu'
import message from 'ant-design-vue/es/message'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { userLogout } from '@/api/userController.ts'
import { LogoutOutlined, HomeOutlined } from '@ant-design/icons-vue'

const loginUserStore = useLoginUserStore()
const router = useRouter()
const route = useRoute()
const selectedKeys = computed(() => [route.path])

const originItems = [
  {
    key: '/',
    icon: () => h(HomeOutlined),
    label: '灵创首页',
    title: '灵创首页',
  },
  {
    key: '/admin/userManage',
    label: '用户管理',
    title: '用户管理',
  },
  {
    key: '/admin/appManage',
    label: '应用管理',
    title: '应用管理',
  },
]

const filterMenus = (menus = [] as MenuProps['items']) => {
  return menus?.filter((menu) => {
    const menuKey = menu?.key as string
    if (menuKey?.startsWith('/admin')) {
      const loginUser = loginUserStore.loginUser
      if (!loginUser || loginUser.userRole !== 'admin') {
        return false
      }
    }
    return true
  })
}

const menuItems = computed<MenuProps['items']>(() => filterMenus(originItems))

const handleMenuClick: MenuProps['onClick'] = (e) => {
  const key = e.key as string
  if (key.startsWith('/')) {
    router.push(key)
  }
}

const doLogout = async () => {
  const res = await userLogout()
  if (res.data.code === 0) {
    loginUserStore.setLoginUser({
      userName: '未登录',
    })
    message.success('退出登录成功')
    await router.push('/user/login')
  } else {
    message.error('退出登录失败，' + res.data.message)
  }
}
</script>

<style scoped>
.header {
  position: sticky;
  top: 0;
  z-index: 100;
  height: auto;
  padding: 0;
  background: rgba(3, 12, 24, 0.76);
  backdrop-filter: blur(18px);
  border-bottom: 1px solid rgba(96, 165, 250, 0.14);
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.2);
}

.header-inner {
  display: flex;
  align-items: center;
  gap: 28px;
  min-height: 74px;
  padding: 0 28px;
}

.brand-link {
  color: inherit;
  min-width: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.brand-emblem {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 44px;
  width: 44px;
  border-radius: 14px;
  background:
    radial-gradient(circle at 30% 25%, rgba(125, 211, 252, 0.95), transparent 45%),
    linear-gradient(135deg, #0f172a 0%, #1d4ed8 52%, #0ea5e9 100%);
  border: 1px solid rgba(125, 211, 252, 0.32);
  box-shadow:
    0 12px 28px rgba(37, 99, 235, 0.25),
    inset 0 1px 0 rgba(255, 255, 255, 0.18);
}

.brand-emblem span {
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: #f8fbff;
}

.brand-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.brand-mark {
  font-size: 11px;
  letter-spacing: 0.32em;
  color: rgba(125, 211, 252, 0.72);
}

.site-title {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: #f8fbff;
  font-family:
    'Space Grotesk',
    'Segoe UI',
    'PingFang SC',
    sans-serif;
}

.nav-menu {
  flex: 1;
  min-width: 0;
  background: transparent;
  color: rgba(226, 232, 240, 0.9);
  border-bottom: none !important;
}

.user-login-status {
  margin-left: auto;
  display: flex;
  align-items: center;
}

.user-profile {
  color: #e2e8f0;
  cursor: pointer;
}

.login-button {
  height: 42px;
  padding: 0 18px;
  border-radius: 999px;
  border: 1px solid rgba(125, 211, 252, 0.18);
  background: linear-gradient(135deg, #2563eb 0%, #0ea5e9 100%);
  box-shadow: 0 12px 28px rgba(14, 165, 233, 0.26);
}

:deep(.ant-menu-horizontal) {
  line-height: 74px;
  background: transparent;
  border-bottom: none !important;
}

:deep(.ant-menu-horizontal > .ant-menu-item) {
  color: rgba(226, 232, 240, 0.78);
  border-bottom: 2px solid transparent;
}

:deep(.ant-menu-horizontal > .ant-menu-item:hover) {
  color: #f8fbff;
}

:deep(.ant-menu-horizontal > .ant-menu-item-selected) {
  color: #7dd3fc;
  border-bottom-color: #38bdf8;
  background: transparent;
}

@media (max-width: 768px) {
  .header-inner {
    gap: 12px;
    padding: 0 16px;
  }

  .brand-mark {
    display: none;
  }

  .site-title {
    font-size: 18px;
  }

  :deep(.ant-menu-horizontal > .ant-menu-item) {
    padding: 0 10px;
  }

  .login-button {
    padding: 0 14px;
  }
}
</style>
