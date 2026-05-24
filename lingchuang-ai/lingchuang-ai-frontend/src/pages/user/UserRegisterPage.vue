<template>
  <div id="userRegisterPage">
    <div class="auth-shell">
      <section class="brand-panel">
        <p class="panel-kicker">START WITH LINGCHUANG AI</p>
        <h1>创建你的零创AI账号</h1>
        <p class="panel-desc">
          开启专属创作空间，快速生成应用、沉淀作品，并把每一次灵感落到真实页面。
        </p>
        <div class="panel-values">
          <div>
            <strong>01</strong>
            <span>自然语言生成</span>
          </div>
          <div>
            <strong>02</strong>
            <span>对话式持续迭代</span>
          </div>
          <div>
            <strong>03</strong>
            <span>部署与展示一体化</span>
          </div>
        </div>
      </section>

      <section class="form-panel">
        <p class="panel-label">创建账号</p>
        <h2 class="title">注册零创AI工作台</h2>
        <div class="desc">完成账号注册后，即可开始构建属于你的零代码应用。</div>
        <a-form :model="formState" name="basic" autocomplete="off" @finish="handleSubmit">
          <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
            <a-input v-model:value="formState.userAccount" placeholder="请输入注册账号" />
          </a-form-item>
          <a-form-item
            name="userPassword"
            :rules="[
              { required: true, message: '请输入密码' },
              { min: 8, message: '密码不能小于 8 位' },
            ]"
          >
            <a-input-password v-model:value="formState.userPassword" placeholder="请输入登录密码" />
          </a-form-item>
          <a-form-item
            name="checkPassword"
            :rules="[
              { required: true, message: '请确认密码' },
              { min: 8, message: '密码不能小于 8 位' },
              { validator: validateCheckPassword },
            ]"
          >
            <a-input-password v-model:value="formState.checkPassword" placeholder="请再次输入密码" />
          </a-form-item>
          <div class="tips">
            已有账号？
            <RouterLink to="/user/login">直接登录</RouterLink>
          </div>
          <a-form-item>
            <a-button type="primary" html-type="submit" class="submit-button">注册</a-button>
          </a-form-item>
        </a-form>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { userRegister } from '@/api/userController.ts'
import message from 'ant-design-vue/es/message'
import { reactive } from 'vue'

const router = useRouter()

const formState = reactive<API.UserRegisterRequest>({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})

const validateCheckPassword = (rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (value && value !== formState.userPassword) {
    callback(new Error('两次输入密码不一致'))
  } else {
    callback()
  }
}

const handleSubmit = async (values: API.UserRegisterRequest) => {
  const res = await userRegister(values)
  if (res.data.code === 0) {
    message.success('注册成功')
    router.push({
      path: '/user/login',
      replace: true,
    })
  } else {
    message.error('注册失败，' + res.data.message)
  }
}
</script>

<style scoped>
#userRegisterPage {
  min-height: calc(100vh - 180px);
  padding: 32px 24px 48px;
  background:
    radial-gradient(circle at 84% 14%, rgba(37, 99, 235, 0.16), transparent 26%),
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
  font-size: 48px;
  line-height: 1.12;
  font-weight: 700;
}

.panel-desc {
  margin: 18px 0 0;
  max-width: 460px;
  color: rgba(191, 219, 254, 0.74);
  line-height: 1.8;
}

.panel-values {
  display: grid;
  gap: 16px;
  margin-top: 32px;
}

.panel-values div {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border-radius: 20px;
  background: rgba(7, 20, 35, 0.72);
  border: 1px solid rgba(125, 211, 252, 0.1);
}

.panel-values strong {
  color: #7dd3fc;
  font-size: 14px;
  letter-spacing: 0.16em;
}

.panel-values span {
  color: #dbeafe;
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
  margin-bottom: 16px;
  color: #64748b;
  font-size: 13px;
  text-align: right;
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
    font-size: 38px;
  }

  .title {
    font-size: 28px;
  }
}
</style>
