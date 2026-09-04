/**
 * README.md 自动生成脚本
 * 读取项目元数据，生成专业的仓库首页文档
 *
 * 运行方式: node scripts/generate-readme.js
 * 依赖: 无 (仅使用 Node.js 内置模块)
 */

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');

// 读取 pom.xml 提取项目信息
function readPomXml() {
  const pomPath = path.join(ROOT, 'pom.xml');
  const content = fs.readFileSync(pomPath, 'utf-8');

  // 提取项目自身的 artifactId（跳过 parent 中的）
  const afterParent = content.split('</parent>')[1] || content;
  const nameMatch = afterParent.match(/<artifactId>([^<]+)<\/artifactId>/);
  const name = nameMatch ? nameMatch[1] : 'hr-agent';

  const versionMatch = afterParent.match(/<version>([^<]+)<\/version>/);
  const version = versionMatch ? versionMatch[1] : '0.0.1-SNAPSHOT';

  const javaVersionMatch = content.match(/<java\.version>([^<]+)<\/java\.version>/);
  const javaVersion = javaVersionMatch ? javaVersionMatch[1] : '17';

  const descriptionMatch = content.match(/<description>([^<]*)<\/description>/);
  const description = descriptionMatch && descriptionMatch[1] ? descriptionMatch[1] : 'HR-Agent';

  return { name, version, javaVersion, description };
}

// 读取 package.json 提取前端信息
function readPackageJson() {
  const pkgPath = path.join(ROOT, 'frontend', 'package.json');
  if (!fs.existsSync(pkgPath)) return null;
  const pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf-8'));
  return {
    name: pkg.name,
    version: pkg.version,
    dependencies: Object.keys(pkg.dependencies || {}),
    devDependencies: Object.keys(pkg.devDependencies || {}),
  };
}

// 扫描后端模块目录
function scanBackendModules() {
  const srcDir = path.join(ROOT, 'src', 'main', 'java', 'org', 'example', 'hragent');
  if (!fs.existsSync(srcDir)) return [];
  return fs.readdirSync(srcDir, { withFileTypes: true })
    .filter(d => d.isDirectory())
    .map(d => d.name);
}

// 扫描前端页面目录
function scanFrontendViews() {
  const viewsDir = path.join(ROOT, 'frontend', 'src', 'views');
  if (!fs.existsSync(viewsDir)) return [];
  return fs.readdirSync(viewsDir, { withFileTypes: true })
    .filter(d => d.isDirectory())
    .map(d => d.name);
}

// 读取 OpenWiki 生成的最新文档摘要
function readOpenWikiSummary() {
  const indexPath = path.join(ROOT, 'openwiki', 'index.md');
  if (!fs.existsSync(indexPath)) return null;
  return fs.readFileSync(indexPath, 'utf-8');
}

// 生成 README 内容
function generateReadme(project, frontend, backendModules, frontendViews, openwikiSummary) {
  const features = [
    { icon: '🤖', title: 'AI 智能助手', desc: '自然语言驱动的 HR 操作助手，支持对话式查询员工、简历、审批进度等，动态工具加载节省 Token' },
    { icon: '👤', title: '员工管理', desc: '员工信息 CRUD，支持多角色权限体系（员工/主管/HR/HRBP/管理员）' },
    { icon: '📄', title: '简历管理', desc: '候选人简历管理，AI 解析结构化 JSON，智能标签匹配与评分' },
    { icon: '📋', title: '流程审批', desc: 'Flowable 工作流引擎驱动入职/离职等审批流程，全程轨迹追踪' },
    { icon: '📊', title: '绩效管理', desc: '员工绩效记录与评估管理' },
    { icon: '📚', title: '培训管理', desc: '培训课程目录管理与能力标签匹配推荐' },
    { icon: '📈', title: '数据看板', desc: '工作台仪表盘，含指标卡片、Token 消耗趋势图、活动日志' },
  ];

  const techStack = [
    ['Java 17', 'https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&logo=openjdk&logoColor=white'],
    ['Spring Boot 3', 'https://img.shields.io/badge/Spring_Boot-3.3-6DB33F?style=flat-square&logo=spring-boot&logoColor=white'],
    ['Vue 3', 'https://img.shields.io/badge/Vue_3-3.5-4FC08D?style=flat-square&logo=vue.js&logoColor=white'],
    ['TypeScript', 'https://img.shields.io/badge/TypeScript-5.6-3178C6?style=flat-square&logo=typescript&logoColor=white'],
    ['MySQL', 'https://img.shields.io/badge/MySQL-5.7-4479A1?style=flat-square&logo=mysql&logoColor=white'],
    ['Redis', 'https://img.shields.io/badge/Redis-6.2-DC382D?style=flat-square&logo=redis&logoColor=white'],
    ['MyBatis-Plus', 'https://img.shields.io/badge/MyBatis_Plus-3.5-0899F0?style=flat-square'],
    ['Flowable', 'https://img.shields.io/badge/Flowable-7.0-00B4E6?style=flat-square'],
    ['Ant Design Vue', 'https://img.shields.io/badge/Ant_Design_Vue-4.2-0170FE?style=flat-square&logo=ant-design&logoColor=white'],
    ['ECharts', 'https://img.shields.io/badge/ECharts-6.1-AA344D?style=flat-square'],
  ];

  const docsBadge = 'https://img.shields.io/badge/docs-hragent-blue?style=flat-square&logo=readthedocs';
  const docsUrl = 'https://hyyxhwy1314.github.io/hragent/';

  return `# HR-Agent

> 智能人力资源管理平台 — AI 驱动的全栈 HR 管理系统

[![文档站点](${docsBadge})](${docsUrl})
[![GitHub last commit](https://img.shields.io/github/last-commit/hyyxhwy1314/hragent?style=flat-square)](${docsUrl})
[![Java 17](${techStack[0][1]})](${docsUrl})
[![Spring Boot 3](${techStack[1][1]})](${docsUrl})
[![Vue 3](${techStack[2][1]})](${docsUrl})
[![MySQL](${techStack[4][1]})](${docsUrl})
[![Redis](${techStack[5][1]})](${docsUrl})

---

## 项目简介

HR-Agent 是一个**AI 驱动的智能人力资源管理平台**，后端采用 Spring Boot 3 + Java 17，前端采用 Vue 3 + TypeScript + Ant Design Vue。系统深度融合 AI 大模型能力，提供自然语言交互的 HR 操作体验，并集成 Flowable 工作流引擎实现流程自动化审批。

## 核心功能

${features.map(f => `### ${f.icon} ${f.title}

${f.desc}
`).join('\n')}

## 技术栈

| 类别 | 技术 |
|------|------|
| **后端** | ${['Java 17', 'Spring Boot 3.3', 'MyBatis-Plus 3.5', 'Flowable 7.0', 'Redis + Redisson'].join('、')} |
| **前端** | ${['Vue 3.5', 'TypeScript 5.6', 'Ant Design Vue 4', 'ECharts 6', 'Pinia'].join('、')} |
| **数据库** | MySQL 5.7+、Redis 6.2+ |
| **AI** | 对接阿里云 MaaS / OpenRouter 大模型 API，SSE 流式对话 |
| **构建** | Maven、Vite、Docker Compose |
| **其他** | JWT 鉴权、AOP 切面、阿里云 OCR、邮件通知 |

## 快速开始

### 前置要求

- JDK 17+
- Node.js 22+
- Docker & Docker Compose
- Maven 3.8+

### 启动基础设施

\`\`\`bash
docker-compose up -d
\`\`\`

### 启动后端

\`\`\`bash
# 构建
mvn clean package -DskipTests

# 启动（需要配置 AI_API_KEY 环境变量）
java -jar target/${project.name}-${project.version}.jar
\`\`\`

### 启动前端

\`\`\`bash
cd frontend
npm install
npm run dev
\`\`\`

> 默认登录：工号 + 密码 \`123456\`
> 详细部署文档请参考 [部署指南](${docsUrl}#/deploy/guide) 和 [快速开始](${docsUrl}#/guide/quickstart)

## 项目结构

\`\`\`
${project.name}/
├── src/main/java/org/example/hragent/
│   ├── agent/          # AI 智能体（模型调用、工具管理、对话）
│   ├── controller/     # REST API 控制器
│   ├── service/        # 业务逻辑层
│   ├── mapper/         # MyBatis 数据访问
│   ├── entity/         # 数据实体
│   ├── config/         # 配置类
│   ├── annotation/     # 自定义注解
│   ├── aspect/         # AOP 切面（缓存/分布式锁/限流）
│   └── utils/          # 工具类
├── frontend/           # Vue 3 + TypeScript 前端
│   └── src/
│       ├── views/      # 页面组件
│       ├── api/        # API 接口封装
│       ├── router/     # 路由配置
│       └── layouts/    # 布局组件
├── docs/               # Docsify 文档站点
├── openwiki/           # OpenWiki 自动生成文档索引
└── scripts/            # 辅助脚本
\`\`\`

## 后端模块

${backendModules.map(m => `- \`${m}/\``).join('\n')}

## 前端页面

${frontendViews.map(v => `- \`${v}/\``).join('\n')}

## 文档

- [📖 文档中心](${docsUrl}) — 完整的用户手册与 API 文档
- [🚀 快速开始](${docsUrl}#/guide/quickstart) — 环境搭建与运行指南
- [🏗️ 系统架构](${docsUrl}#/guide/architecture) — 整体架构设计
- [🔌 API 接口](${docsUrl}#/api/overview) — REST API 参考

## 环境变量

| 变量 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| \`AI_API_KEY\` | 是 | — | AI 模型 API 密钥 |
| \`AI_BASE_URL\` | 否 | 阿里云 MaaS 地址 | AI 服务地址 |
| \`AI_MODEL_NAME\` | 否 | deepseek-v4-flash-0731 | 模型名称 |
| \`MAIL_PASSWORD\` | 否 | — | QQ SMTP 授权码 |
| \`COS_ACCESS_KEY\` | 否 | — | 对象存储密钥 |

## 许可证

[MIT](LICENSE)

---

> 文档自动生成于 ${new Date().toISOString().split('T')[0]} | [OpenWiki](${docsUrl}) 驱动
`;
}

// --- 主流程 ---
function main() {
  const project = readPomXml();
  const frontend = readPackageJson();
  const backendModules = scanBackendModules();
  const frontendViews = scanFrontendViews();
  const openwikiSummary = readOpenWikiSummary();

  const readme = generateReadme(project, frontend, backendModules, frontendViews, openwikiSummary);

  const readmePath = path.join(ROOT, 'README.md');
  fs.writeFileSync(readmePath, readme, 'utf-8');
  console.log(`README.md generated successfully at ${readmePath}`);
}

main();