<template>
  <a-layout-footer class="footer">
    <div class="footer-content">
      <p class="brand-name">零创AI-零代码应用开发平台</p>
      <p class="brand-desc">让灵感更快成为可部署、可展示、可持续迭代的应用作品。</p>
    </div>
  </a-layout-footer>
</template>

<style scoped>
.footer {
  background: rgba(3, 12, 24, 0.88);
  backdrop-filter: blur(18px);
  text-align: center;
  padding: 28px 20px 36px;
  margin-top: 40px;
  border-top: 1px solid rgba(125, 211, 252, 0.12);
}

.footer-content {
  max-width: 960px;
  margin: 0 auto;
}

.brand-name {
  margin: 0 0 8px;
  color: #f8fbff;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.brand-desc {
  margin: 0;
  color: rgba(191, 219, 254, 0.72);
  font-size: 13px;
}
</style>
