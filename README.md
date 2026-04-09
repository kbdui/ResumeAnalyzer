# ResumeAnalyzer

一个基于 LLM 的简历筛选与评估系统，支持从简历上传、信息提取、硬过滤、语义召回到大模型评估的完整流程闭环。

本项目旨在构建一个自动化简历筛选系统，帮助招聘流程从“人工初筛”转变为“规则 + 语义匹配 + 大模型评估”的混合模式。

系统支持：
- 批量上传简历
- 自动结构化信息提取
- 基于规则的硬过滤
- 基于 TF-IDF / Embedding 的候选人召回与排序
- 基于大模型的候选人评估与解释

## 功能说明

- 📁 简历上传：支持 zip 批量上传
- 🧠 信息提取：基于大模型将简历转为结构化数据
- 🚦 硬过滤：基于规则进行初筛（学历 / 技能 / 年限等）
- 🔍 召回筛选：结合 TF-IDF 与 Embedding 进行语义匹配
- 🤖 大模型评估：生成候选人匹配分析与推荐理由

## 系统流程

```
Upload
  ↓
Extract (LLM)
  ↓
Hard Filter (Rules)
  ↓
Recall (TF-IDF + Embedding)
  ↓
LLM Evaluation
  ↓
Frontend Display
```

## 技术架构

本项目采用三端协同架构，按职责分层如下：

**Python 筛选服务（计算层）**
- 基于 FastAPI 提供高性能异步 API
- 负责 TF-IDF 关键词召回与 Embedding 语义向量检索
- 接收 JD 关键词与简历文本列表，返回混合加权得分与 Top-K 排序

**Java 业务编排层（控制层）**
- 基于 Spring Boot 实现任务编排与数据落库
- 封装 LLM 调用逻辑，管理异步任务生命周期（QUEUED → RUNNING → SUCCESS/PARTIAL_SUCCESS/FAILED）
- 通过线程池 + CompletableFuture 实现批量 LLM 调用并发控制
- 数据持久化采用 MyBatis-Plus 操作 MySQL，事务保证多表写入一致性

**Vue 前端（展示层）**
- 基于 Vue 3 + Element Plus 构建流程化任务编排页面
- 支持拖拽上传、任务状态实时轮询、分页数据展示与详情弹窗
- 组件化设计：步骤卡片、状态徽章、JSON 查看器、任务选择器等

**数据流转**
```
zip 批量上传 → text 表（原始简历文本）
  ↓
Step 2 信息提取（LLM）→ resume 表（结构化信息）+ 关联明细表
  ↓
Step 3 硬过滤（LLM 四维度三态判断）→ task_resume 表（PASS/FAIL + 分析 JSON）
  ↓
Step 4 召回筛选（Python 混合打分）→ hybrid_result 表（召回得分）
  ↓
Step 5 最终评估（LLM 综合评估）→ analysis 表（推荐理由 JSON）
```

## 技术栈

| 层级 | 技术/框架 | 用途 |
|------|-----------|------|
| **前端** | Vue 3 + TypeScript | 组合式 API + 响应式状态管理 |
| | Element Plus | UI 组件库，表格/表单/分页/弹窗 |
| | Vite | 前端构建工具，支持热更新 |
| **Java 后端** | Spring Boot 3.x | 核心框架，REST API 与依赖注入 |
| | MyBatis-Plus | ORM 框架，简化 CRUD 与复杂查询 |
| | MySQL 8.x | 关系型数据库，存储简历与任务数据 |
| | Jackson | JSON 处理，支持动态节点与强类型 DTO |
| | OkHttp | HTTP 客户端，调用 Python 筛选服务 |
| **Python** | FastAPI | 高性能异步 Web 框架 |
| | scikit-learn | TF-IDF 向量计算与文本召回 |
| | sentence-transformers | 语义嵌入模型（可选） |
| | faiss | 向量检索加速（可选） |
| **大模型** | Deepseek API | 提供简历抽取、硬过滤、评估等 LLM 能力 |
| | SiliconFlow（可选） | 备选模型渠道 |
| **构建/部署** | Maven | Java 项目构建与依赖管理 |
| | npm | 前端包管理 |
| | Git | 版本控制 |

## 项目结构

```
ResumeAnalyzer/
├── README.md                          # 项目说明文档
├── .gitignore                         # Git 忽略规则
│
├── backend/                           # 后端服务
│   ├── AnalyzerBack/                  # Java Spring Boot 主服务
│   │   ├── pom.xml                    # Maven 依赖配置
│   │   ├── mvnw / mvnw.cmd            # Maven Wrapper 脚本
│   │   └── src/
│   │       └── main/
│   │           ├── java/com/app/
│   │           │   ├── web/           # Controller 层
│   │           │   │   ├── DeepseekController.java      # LLM 任务接口
│   │           │   │   ├── ResumeSaveController.java    # 上传解析接口
│   │           │   │   └── repository/                # 纯查询 Controller
│   │           │   ├── service/
│   │           │   │   ├── DeepseekExtractService.java   # Step2 提取
│   │           │   │   ├── DeepseekFilterService.java    # Step3 硬过滤
│   │           │   │   ├── DeepseekAnalyzeService.java   # Step5 评估
│   │           │   │   ├── ResumeHybridService.java      # Step4 召回
│   │           │   │   └── repository/                 # 数据查询 Service
│   │           │   ├── entity/        # 数据库实体类
│   │           │   ├── dao/           # MyBatis-Plus Mapper
│   │           │   ├── dto/           # 数据传输对象
│   │           │   └── tool/          # 工具类
│   │           └── resources/
│   │               ├── application.yml           # 主配置（已提交）
│   │               ├── application-local.yml     # 本地敏感配置（需自建）
│   │               └── sql/schema.sql            # 数据库建表脚本
│   │
│   └── AnalyzerBack_python/           # Python FastAPI 筛选服务
│       ├── api.py                     # FastAPI 入口
│       ├── service.py                 # 混合召回打分逻辑
│       ├── schemas.py                 # Pydantic 请求模型
│       └── worker.py                  # 异步任务处理
│
├── frontend/                          # 前端应用
│   └── AnalyzerFront/                 # Vue 3 SPA
│       ├── package.json               # npm 依赖
│       ├── vite.config.ts             # Vite 构建配置
│       ├── index.html                 # 入口 HTML
│       └── src/
│           ├── main.ts                # 应用入口
│           ├── App.vue                # 根组件
│           ├── router/index.ts        # 路由配置
│           ├── api/                   # HTTP 接口封装
│           ├── views/                 # 页面组件
│           │   ├── UploadAndScreenPage.vue   # 流程编排页
│           │   └── HistoryPage.vue           # 历史记录页
│           ├── components/            # 可复用组件
│           │   ├── workflow/          # 流程步骤组件
│           │   ├── TaskSelector.vue
│           │   ├── JsonViewer.vue
│           │   └── StatusBadge.vue
│           └── composables/           # 组合式逻辑
│               └── useWorkflowRunner.ts
│
└── weekJournal/                     # 开发周记（已加入 .gitignore）
```

## 快速开始

### 1. 环境准备

- **Java 17+** 与 **Maven 3.8+**
- **Python 3.10+** 与 **pip**
- **Node.js 18+** 与 **npm**
- **MySQL 8.0+**

### 2. 数据库初始化

```bash
# 登录 MySQL 执行
CREATE DATABASE resume_analyzer DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE resume_analyzer;
SOURCE backend/AnalyzerBack/src/main/resources/sql/schema.sql;
```

### 3. 启动 Python 筛选服务

```bash
cd backend/AnalyzerBack_python
pip install -r requirements.txt  # 需包含 fastapi, uvicorn, scikit-learn
python api.py
```
服务默认监听 `http://127.0.0.1:8000`

建议使用 Pycharm ，编辑 run/debug configurations ，切换 script path 至 module name ，建议命名为 uvicorn ，使用参数 `api:app --reload --host 0.0.0.0 --port 8000`

### 4. 配置并启动 Java 后端

#### 配置说明

项目中涉及敏感信息的配置分离如下：

#### 步骤 1：修改 `application-local.yml`

检查并修改数据库连接配置（文件已存在，位于 `backend/AnalyzerBack/src/main/resources/application-local.yml`）：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/resume_analyzer
    username: root                           # <-- 修改为实际账号
    password: 123456                         # <-- 【必须修改】数据库密码
    driver-class-name: com.mysql.cj.jdbc.Driver
```

#### 步骤 2：创建 `application-key.yml`（可选，用于存放 API Key）

⚠️ **注意**：`application-key.yml` 已加入 `.gitignore`，需手动创建：

```bash
cd backend/AnalyzerBack/src/main/resources
touch application-key.yml
```

内容示例：

```yaml
deepseek:
  api:
    key: YOUR_DEEPSEEK_API_KEY            # <-- 替换为你的 API Key
```

启动服务：

```bash
cd backend/AnalyzerBack
./mvnw spring-boot:run
# 或在 Windows 下：mvnw.cmd spring-boot:run
```

建议使用 IntelliJ IDEA

### 5. 启动前端

```bash
cd frontend/AnalyzerFront
npm install
npm run dev
```

访问 `http://localhost:5173`，选择 **流程编排** 页面开始体验：
1. 上传简历 zip 包
2. 输入 JD 文本
3. 依次或一键执行 Step 2~5
4. 在 **历史记录** 页面查看完整结果
