<template>
  <el-container class="app-container">
    <el-header class="app-header">
      <div class="brand">
        <div class="brand-dot" />
        <div class="brand-text">
          <div class="title">Resume Analyzer</div>
          <div class="subtitle">基于大语言模型的智能简历分析系统</div>
        </div>
      </div>
      <el-menu mode="horizontal" :ellipsis="false" router class="nav-menu">
        <el-menu-item index="/screen">简历筛选</el-menu-item>
        <el-menu-item index="/analyze">深度分析</el-menu-item>
      </el-menu>
    </el-header>
    <el-main class="app-main">
      <div class="page-shell">
        <!-- 配合 index.ts 监测路由切换 -->
        <RouterView />
      </div>
    </el-main>
  </el-container>
</template>

<style scoped>
.app-container {
  min-height: 100vh;
  background: radial-gradient(circle at 0% 0%, #eef4ff 0%, #f6f8fb 40%, #f7f9fc 100%);
}

.app-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  height: auto;
  padding: 16px 28px 14px;
  border-bottom: 1px solid rgba(120, 140, 180, 0.16);
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(8px);
  position: sticky;
  top: 0;
  z-index: 10;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-dot {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: linear-gradient(135deg, #5b8cff 0%, #7b5cff 100%);
  box-shadow: 0 0 0 6px rgba(91, 140, 255, 0.14);
}

.title {
  font-size: 22px;
  font-weight: 700;
  white-space: nowrap;
  color: #1d2a44;
}

.subtitle {
  font-size: 12px;
  color: #6b7691;
  margin-top: 2px;
}

.nav-menu {
  border-bottom: none;
  background: transparent;
}

.app-main {
  padding: 26px 26px 30px;
}

.page-shell {
  max-width: 1340px;
  margin: 0 auto;
  padding: 0 6px;
}

@media (max-width: 900px) {
  .app-header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
