<template>
  <div id="userLoginPage">
    <div class="auth-shell">
      <section class="brand-panel">
        <p class="panel-kicker">LINGCHUANG AI</p>
        <h1>零创AI</h1>
        <p class="panel-title">零代码应用开发平台</p>
        <p class="panel-desc">
          回到你的创作工作台，继续生成、部署并打磨属于自己的应用作品。
        </p>
        <div class="panel-tags">
          <span>应用生成</span>
          <span>对话迭代</span>
          <span>部署展示</span>
        </div>
      </section>

      <section class="form-panel">
        <p class="panel-label">欢迎回来</p>
        <h2 class="title">登录零创AI工作台</h2>
        <div class="desc">输入账号和密码，继续你的零代码创作流程。</div>
        <a-form :model="formState" name="basic" autocomplete="off" @finish="handleSubmit">
          <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
            <a-input v-model:value="formState.userAccount" placeholder="请输入登录账号" />
          </a-form-item>
          <a-form-item
            name="userPassword"
            :rules="[
              { required: true, message: '请输入密码' },
              { min: 8, message: '密码长度不能小于 8 位' },
            ]"
          >
            <a-input-password v-model:value="formState.userPassword" placeholder="请输入登录密码" />
          </a-form-item>
          <div class="tips">
            还没有账号？
            <RouterLink to="/user/register">立即注册</RouterLink>
          </div>
          <a-form-item>
            <a-button type="primary" html-type="submit" class="submit-button">登录</a-button>
          </a-form-item>
        </a-form>
      </section>
    </div>
  </div>
</template>
<script lang="ts" setup>
import { reactive } from 'vue'
import { userLogin } from '@/api/userController.ts'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { useRouter } from 'vue-router'
import message from 'ant-design-vue/es/message'

const formState = reactive<API.UserLoginRequest>({
  userAccount: '',
  userPassword: '',
})

const router = useRouter()
const loginUserStore = useLoginUserStore()

const handleSubmit = async (values: any) => {
  const res = await userLogin(values)
  if (res.data.code === 0 && res.data.data) {
    await loginUserStore.fetchLoginUser()
    message.success('登录成功')
    router.push({
      path: '/',
      replace: true,
    })
  } else {
    message.error('登录失败，' + res.data.message)
  }
}
</script>

<style scoped>
#userLoginPage {
  min-height: calc(100vh - 180px);
  padding: 32px 24px 48px;
  background:
    radial-gradient(circle at 16% 18%, rgba(56, 189, 248, 0.14), transparent 28%),
    linear-gradient(180deg, #07111d 0%, #04111d 100%);
}

.auth-shell {
  max-width: 1180px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(380px, 460px);
  gap: 28px;
  align-items: stretch;
}

.brand-panel,
.form-panel {
  border-radius: 28px;
  border: 1px solid rgba(125, 211, 252, 0.14);
  box-shadow: 0 24px 64px rgba(2, 8, 23, 0.32);
  backdrop-filter: blur(20px);
}

.brand-panel {
  padding: 40px;
  background:
    linear-gradient(180deg, rgba(11, 34, 57, 0.88) 0%, rgba(3, 12, 24, 0.94) 100%);
  color: #f8fbff;
}

.panel-kicker,
.panel-label {
  margin: 0 0 14px;
  font-size: 12px;
  letter-spacing: 0.24em;
  color: rgba(125, 211, 252, 0.7);
}

.brand-panel h1 {
  margin: 0;
  font-size: 60px;
  line-height: 1;
  font-weight: 700;
}

.panel-title {
  margin: 16px 0 20px;
  font-size: 28px;
  color: rgba(224, 242, 254, 0.94);
}

.panel-desc {
  margin: 0;
  max-width: 440px;
  color: rgba(191, 219, 254, 0.74);
  line-height: 1.8;
}

.panel-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 32px;
}

.panel-tags span {
  padding: 9px 14px;
  border-radius: 999px;
  background: rgba(7, 20, 35, 0.76);
  border: 1px solid rgba(125, 211, 252, 0.14);
}

.form-panel {
  padding: 32px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98) 0%, rgba(241, 245, 249, 0.98) 100%);
}

.title {
  margin: 0 0 10px;
  font-size: 32px;
  color: #0f172a;
}

.desc {
  color: #64748b;
  margin-bottom: 24px;
  line-height: 1.7;
}

.tips {
  text-align: right;
  color: #64748b;
  font-size: 13px;
  margin-bottom: 16px;
}

.submit-button {
  width: 100%;
  height: 44px;
  border-radius: 999px;
  border: none;
  background: linear-gradient(135deg, #2563eb 0%, #0ea5e9 100%);
  box-shadow: 0 14px 30px rgba(14, 165, 233, 0.2);
}

@media (max-width: 900px) {
  .auth-shell {
    grid-template-columns: 1fr;
  }

  .brand-panel,
  .form-panel {
    padding: 24px;
  }

  .brand-panel h1 {
    font-size: 42px;
  }

  .title {
    font-size: 28px;
  }
}
</style>
