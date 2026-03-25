<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading, MagicStick, Document, User, School, OfficeBuilding, Medal, ArrowRight } from '@element-plus/icons-vue'
import TaskSelector from '@/components/TaskSelector.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import JsonViewer from '@/components/JsonViewer.vue'
import { listResumeByTask, queryAnalyzeTask, submitAnalyzeTask } from '@/api'
import type { AnalyzeTaskStatus, TaskResumeMainView } from '@/api/types'

const taskId = ref('')
const analyzeTaskId = ref('')
const status = ref<AnalyzeTaskStatus | null>(null)
const analyzing = ref(false)
const resumeRows = ref<TaskResumeMainView[]>([])
const expandedRows = ref<number[]>([])
let timer: number | null = null

const progress = computed(() => {
  if (!status.value || !status.value.total) return 0
  return Math.round(((status.value.successCount + status.value.failedCount) / status.value.total) * 100)
})

const hasResult = computed(() => resumeRows.value.length > 0)

function stopPolling() {
  if (timer !== null) {
    window.clearInterval(timer)
    timer = null
  }
  analyzing.value = false
}

async function loadAnalyzeResult() {
  if (!taskId.value) return
  resumeRows.value = await listResumeByTask(taskId.value)
  expandedRows.value = resumeRows.value.slice(0, 3).map(r => r.resumeId)
}

async function pollAnalyze() {
  if (!analyzeTaskId.value) return
  try {
    status.value = await queryAnalyzeTask(analyzeTaskId.value)
    if (status.value.status === 'SUCCESS' || status.value.status === 'FAILED') {
      stopPolling()
      await loadAnalyzeResult()
      if (status.value.status === 'SUCCESS') {
        ElMessage.success('大模型分析任务已完成')
      } else {
        ElMessage.error(status.value.error || '大模型分析任务失败')
      }
    }
  } catch (error) {
    stopPolling()
    ElMessage.error((error as Error).message || '分析任务轮询失败')
  }
}

async function startAnalyze() {
  if (!taskId.value) {
    ElMessage.warning('请先选择 task')
    return
  }
  try {
    const response = await submitAnalyzeTask(taskId.value)
    analyzeTaskId.value = response.analyzeTaskId
    ElMessage.success(response.message || '提交成功')
    stopPolling()
    analyzing.value = true
    await pollAnalyze()
    timer = window.setInterval(pollAnalyze, 2000)
  } catch (error) {
    ElMessage.error((error as Error).message || '提交分析任务失败')
  }
}

function toggleExpand(resumeId: number) {
  const idx = expandedRows.value.indexOf(resumeId)
  if (idx > -1) {
    expandedRows.value.splice(idx, 1)
  } else {
    expandedRows.value.push(resumeId)
  }
}

function isExpanded(resumeId: number) {
  return expandedRows.value.includes(resumeId)
}

onBeforeUnmount(stopPolling)
</script>

<template>
  <div class="analyze-page">
    <!-- 上部控制区 -->
    <el-row :gutter="20" class="control-section">
      <el-col :xs="24" :sm="24" :md="12" :lg="12">
        <el-card class="panel-card action-card">
          <template #header>
            <div class="card-header">
              <div class="header-left">
                <el-icon class="header-icon"><MagicStick /></el-icon>
                <span class="panel-title">大模型深度分析</span>
              </div>
              <el-tag type="primary" effect="light" size="small">AI 驱动</el-tag>
            </div>
          </template>

          <div class="action-content">
            <TaskSelector v-model="taskId" />
            
            <el-divider />
            
            <div class="action-area">
              <el-button 
                type="primary" 
                size="large" 
                :loading="analyzing"
                @click="startAnalyze"
                class="analyze-btn"
              >
                <el-icon v-if="!analyzing"><MagicStick /></el-icon>
                启动 AI 分析
              </el-button>
              
              <div v-if="analyzeTaskId" class="task-info">
                <el-descriptions :column="1" border>
                  <el-descriptions-item label="分析任务ID">
                    <el-tag type="primary" effect="plain">{{ analyzeTaskId }}</el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item label="当前状态">
                    <StatusBadge :status="status?.status" />
                  </el-descriptions-item>
                </el-descriptions>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="24" :md="12" :lg="12">
        <el-card class="panel-card status-card" :class="{ 'is-running': analyzing }">
          <template #header>
            <div class="card-header">
              <div class="header-left">
                <el-icon class="header-icon" :class="{ 'is-loading': analyzing }">
                  <Loading v-if="analyzing" />
                  <Document v-else />
                </el-icon>
                <span class="panel-title">分析进度</span>
              </div>
              <el-tag v-if="status?.total" type="info" size="small">
                {{ status.successCount + status.failedCount }} / {{ status.total }}
              </el-tag>
            </div>
          </template>

          <div class="progress-area">
            <div class="progress-circle" v-if="!status?.total && !analyzing">
              <el-empty description="等待启动分析任务" :image-size="100" />
            </div>
            
            <template v-else>
              <div class="progress-stats">
                <div class="stat-item">
                  <div class="stat-value success">{{ status?.successCount || 0 }}</div>
                  <div class="stat-label">成功</div>
                </div>
                <div class="stat-item">
                  <div class="stat-value warning">{{ status?.failedCount || 0 }}</div>
                  <div class="stat-label">失败</div>
                </div>
                <div class="stat-item">
                  <div class="stat-value total">{{ status?.total || 0 }}</div>
                  <div class="stat-label">总计</div>
                </div>
              </div>
              
              <el-progress 
                :percentage="progress" 
                :stroke-width="20" 
                striped 
                striped-flow 
                :duration="10"
                :color="progress === 100 ? '#67C23A' : '#E6A23C'"
                class="main-progress"
              />
              
              <div class="progress-detail">
                <span>已完成 {{ progress }}%</span>
                <span v-if="status?.error" class="error-text">{{ status.error }}</span>
              </div>
            </template>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 结果展示区 -->
    <template v-if="hasResult">
      <el-card class="panel-card results-card">
        <template #header>
          <div class="card-header">
            <div class="header-left">
              <el-icon class="header-icon"><User /></el-icon>
              <span class="panel-title">AI 分析结果</span>
              <el-tag type="success" effect="light" size="small">
                {{ resumeRows.length }} 份简历
              </el-tag>
            </div>
            <el-button 
              text 
              type="primary" 
              size="small"
              @click="expandedRows = resumeRows.map(r => r.resumeId)"
            >
              全部展开
            </el-button>
          </div>
        </template>

        <div class="results-list">
          <div 
            v-for="row in resumeRows" 
            :key="row.resumeId"
            class="resume-card"
            :class="{ 'is-expanded': isExpanded(row.resumeId) }"
          >
            <!-- 卡片头部 -->
            <div class="resume-header" @click="toggleExpand(row.resumeId)">
              <div class="rank-badge" :class="{ 'top3': row.rankNo && row.rankNo <= 3 }">
                {{ row.rankNo || '-' }}
              </div>
              
              <div class="resume-main">
                <div class="name-row">
                  <span class="name">{{ row.resume?.personalInfo?.name || '未识别姓名' }}</span>
                  <el-tag v-if="row.finalScore" :type="row.finalScore > 0.7 ? 'success' : row.finalScore > 0.4 ? 'warning' : 'danger'" effect="light" size="small">
                    匹配度 {{ (row.finalScore * 100).toFixed(1) }}%
                  </el-tag>
                </div>
                <div class="contact-row">
                  <span v-if="row.resume?.personalInfo?.email">📧 {{ row.resume.personalInfo.email }}</span>
                  <span v-if="row.resume?.personalInfo?.contact">📱 {{ row.resume.personalInfo.contact }}</span>
                </div>
              </div>
              
              <div class="expand-icon">
                <el-icon><ArrowRight :class="{ 'is-expanded': isExpanded(row.resumeId) }" /></el-icon>
              </div>
            </div>
            
            <!-- 展开详情 -->
            <div v-show="isExpanded(row.resumeId)" class="resume-detail">
              <el-divider />
              
              <el-row :gutter="20">
                <!-- 技能 -->
                <el-col :xs="24" :sm="12" :md="8">
                  <div class="detail-section">
                    <div class="section-title">
                      <el-icon><Medal /></el-icon>
                      技能
                    </div>
                    <div class="skill-tags">
                      <el-tag 
                        v-for="skill in row.resume?.skills?.slice(0, 8)" 
                        :key="skill"
                        type="info"
                        effect="light"
                        size="small"
                        class="skill-tag"
                      >
                        {{ skill }}
                      </el-tag>
                    </div>
                  </div>
                </el-col>
                
                <!-- 教育 -->
                <el-col :xs="24" :sm="12" :md="8">
                  <div class="detail-section">
                    <div class="section-title">
                      <el-icon><School /></el-icon>
                      教育经历
                    </div>
                    <div v-if="row.resume?.education?.length" class="edu-list">
                      <div v-for="(edu, idx) in row.resume.education.slice(0, 2)" :key="idx" class="edu-item">
                        <div class="edu-school">{{ edu.school }}</div>
                        <div class="edu-major">{{ edu.major }} · {{ edu.degree }}</div>
                      </div>
                    </div>
                    <el-empty v-else description="暂无教育信息" :image-size="60" />
                  </div>
                </el-col>
                
                <!-- 工作 -->
                <el-col :xs="24" :sm="12" :md="8">
                  <div class="detail-section">
                    <div class="section-title">
                      <el-icon><OfficeBuilding /></el-icon>
                      工作经历
                    </div>
                    <div v-if="row.resume?.workExperience?.length" class="work-list">
                      <div v-for="(work, idx) in row.resume.workExperience.slice(0, 2)" :key="idx" class="work-item">
                        <div class="work-company">{{ work.company }}</div>
                        <div class="work-position">{{ work.position }}</div>
                        <div class="work-duration">{{ work.duration }}</div>
                      </div>
                    </div>
                    <el-empty v-else description="暂无工作经历" :image-size="60" />
                  </div>
                </el-col>
              </el-row>
            </div>
          </div>
        </div>
      </el-card>
    </template>

    <!-- 空状态 -->
    <el-card v-else-if="!analyzing" class="panel-card empty-card">
      <el-empty description="选择任务并启动 AI 分析后，结果将在此展示">
        <template #image>
          <el-icon class="empty-icon"><MagicStick /></el-icon>
        </template>
        <template #default>
          <div class="empty-tips">
            <p>1. 从上方选择已筛选过的 Task</p>
            <p>2. 点击"启动 AI 分析"按钮</p>
            <p>3. 等待分析完成后查看结果</p>
          </div>
        </template>
      </el-empty>
    </el-card>

    <!-- 原始 JSON -->
    <el-card v-if="status" class="panel-card json-card">
      <template #header>
        <div class="card-header">
          <span class="panel-title">原始数据</span>
          <el-button text type="primary" size="small" @click="status = null">
            隐藏
          </el-button>
        </div>
      </template>
      <JsonViewer :data="status" />
    </el-card>
  </div>
</template>

<style scoped>
.analyze-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.control-section {
  margin-bottom: 0;
}

/* 卡片头部 */
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-icon {
  font-size: 20px;
  color: #5b8cff;
}

.header-icon.is-loading {
  animation: rotating 2s linear infinite;
}

/* 操作卡片 */
.action-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.action-area {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.analyze-btn {
  width: 100%;
  font-size: 16px;
  height: 48px;
}

.task-info {
  margin-top: 8px;
}

/* 进度卡片 */
.status-card.is-running {
  border-color: #5b8cff;
  box-shadow: 0 0 0 4px rgba(91, 140, 255, 0.1);
}

.progress-area {
  padding: 20px 0;
}

.progress-stats {
  display: flex;
  justify-content: space-around;
  margin-bottom: 24px;
}

.stat-item {
  text-align: center;
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  line-height: 1;
}

.stat-value.success {
  color: #67c23a;
}

.stat-value.warning {
  color: #f56c6c;
}

.stat-value.total {
  color: #606266;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 8px;
}

.main-progress {
  margin: 16px 0;
}

.progress-detail {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #606266;
}

.error-text {
  color: #f56c6c;
}

/* 结果卡片 */
.results-card {
  flex: 1;
}

.results-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.resume-card {
  border: 1px solid #e4e7ed;
  border-radius: 12px;
  overflow: hidden;
  transition: all 0.3s;
}

.resume-card:hover {
  border-color: #c0c4cc;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.resume-card.is-expanded {
  border-color: #5b8cff;
  box-shadow: 0 4px 16px rgba(91, 140, 255, 0.1);
}

/* 卡片头部 */
.resume-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 20px;
  cursor: pointer;
  background: #fafafa;
  transition: background 0.3s;
}

.resume-header:hover {
  background: #f5f5f5;
}

.rank-badge {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 16px;
  color: #909399;
  background: #f0f0f0;
  flex-shrink: 0;
}

.rank-badge.top3 {
  background: linear-gradient(135deg, #5b8cff 0%, #7b5cff 100%);
  color: white;
  box-shadow: 0 2px 8px rgba(91, 140, 255, 0.3);
}

.resume-main {
  flex: 1;
  min-width: 0;
}

.name-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
}

.name {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.contact-row {
  display: flex;
  gap: 20px;
  font-size: 13px;
  color: #606266;
}

.expand-icon {
  font-size: 20px;
  color: #909399;
  transition: transform 0.3s;
}

.expand-icon :deep(.is-expanded) {
  transform: rotate(90deg);
}

/* 详情区 */
.resume-detail {
  padding: 0 20px 20px;
  background: white;
}

.detail-section {
  padding: 16px 0;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 12px;
}

.skill-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.skill-tag {
  margin: 0;
}

.edu-list, .work-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.edu-item, .work-item {
  padding: 12px;
  background: #f5f7fa;
  border-radius: 8px;
}

.edu-school, .work-company {
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
}

.edu-major, .work-position {
  font-size: 13px;
  color: #606266;
}

.work-duration {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

/* 空状态 */
.empty-card {
  min-height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-icon {
  font-size: 80px;
  color: #dcdfe6;
}

.empty-tips {
  text-align: center;
  color: #909399;
  font-size: 14px;
  line-height: 2;
  margin-top: 20px;
}

.empty-tips p {
  margin: 0;
}

/* JSON 卡片 */
.json-card {
  margin-top: 8px;
}

/* 响应式 */
@media (max-width: 768px) {
  .control-section :deep(.el-col) {
    margin-bottom: 20px;
  }
  
  .resume-header {
    flex-wrap: wrap;
  }
  
  .contact-row {
    flex-direction: column;
    gap: 4px;
  }
  
  .progress-stats {
    gap: 20px;
  }
  
  .stat-value {
    font-size: 24px;
  }
}

@keyframes rotating {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
