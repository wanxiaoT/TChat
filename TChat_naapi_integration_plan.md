# TChat 接入 naapi.cc 的开发与部署方案

## 背景

当前项目包含两个仓库：

- Android 仓库：`C:\Users\Administrator\AndroidStudioProjects\TChat`
- 后端仓库：`C:\1Git\tchat对接naapi方案\naapi-tchat-backend`

TChat 是一个 Android AI 聊天软件。Android 端计划开源，但后端不计划开源，因为后端涉及计费、额度、供应商密钥、风控和内部路由逻辑。

当前已有服务器部署了 `naapi.cc` 服务，希望把该能力内置到 TChat 中，让用户可以更简单地接入和使用。初步设想是使用 `t.naapi.cc` 作为 TChat 的包装层入口，然后通过容器内部网络与 `naapi.cc` 核心服务通信。

主要问题：

- Android 代码只能在 Windows 本地开发和交叉编译。
- 服务器性能较弱，不适合承担 Android 编译或重型构建任务。
- 后端代码量较大，每次修改后手动上传服务器效率很低。
- 希望本地开发和服务器部署保持同步。
- Android 端开源，但后端需要闭源。

## 域名注意事项

域名大小写不敏感，所以以下写法本质上是同一个域名：

```text
t.NApi.cc
T.naAPI.CC
t.naapi.cc
```

建议统一使用：

```text
https://t.naapi.cc
```

作为 TChat 内置服务的公网入口。

## 推荐总体架构

```text
TChat Android App
        |
        | HTTPS
        v
https://t.naapi.cc
        |
        v
tchat-wrapper 后端
        |
        | Docker 内部网络 / 内部服务地址 / 可选 mTLS
        v
naapi-core / naapi.cc 核心服务
        |
        v
数据库 / Redis / 计费系统
```

Android 端只访问 `https://t.naapi.cc`，不要直接调用 `naapi.cc` 的核心内部接口。

## 各层职责

### Android 端

Android 端负责：

- 聊天 UI
- 用户登录状态展示
- 调用 TChat 内置服务接口
- 显示模型列表、余额、套餐、错误提示
- 提供用户配置入口
- 默认内置 `https://t.naapi.cc`

Android 端不应该包含：

- naapi.cc 管理员 token
- 供应商 API key
- 后端签名 secret
- 数据库地址
- 内部 Docker 网络地址
- 计费核心逻辑
- 风控核心逻辑

原因是 APK 可以被反编译。客户端开源后，任何放在 Android 端的 secret 都应视为公开。

### tchat-wrapper 后端

`tchat-wrapper` 是 TChat 专用的后端包装层，负责：

- TChat 用户认证
- 请求鉴权
- 免费额度 / 会员 / 计费判断
- 请求限流
- 风控入口
- 模型列表包装
- 请求格式转换
- 调用内部 `naapi-core`
- 返回适合 TChat Android 使用的响应格式

### naapi-core / naapi.cc 核心服务

核心服务负责：

- 真正的模型供应商转发
- 供应商 API key 管理
- 管理后台
- 计费基础逻辑
- 用户、订单、余额、账单数据
- 内部路由策略

核心服务不应直接暴露给 TChat 客户端绕过 wrapper 调用。

## 仓库拆分建议

建议保持两个独立仓库：

```text
TChat Android 仓库：公开
naapi-tchat-backend 仓库：私有
```

Android 开源仓库中只保留：

- API client
- 请求 / 响应 DTO
- Provider 配置 UI
- 默认 endpoint：`https://t.naapi.cc`
- dev / staging / prod 构建配置
- 公开错误码处理

后端私有仓库中保留：

- wrapper 服务
- naapi.cc 内部通信逻辑
- 用户额度
- 计费
- 风控
- 数据库迁移
- Dockerfile
- docker-compose 生产配置模板
- 部署脚本

如果 Android 端需要接口文档，可以维护一个公开的 API contract，例如：

```text
openapi/tchat-public-api.yaml
```

该文件只描述 Android 需要调用的公开接口，不暴露内部计费、供应商、密钥和管理接口。

## 本地和服务器同步方案

不建议继续使用手动上传代码的方式。更推荐使用 Git 和 Docker 镜像发布。

### 方案 A：Git push 后服务器 pull 并构建

流程：

```text
Windows 本地开发后端
        |
        | git commit
        | git push
        v
私有 Git 仓库
        |
        | 服务器 git pull
        | docker compose up -d --build
        v
线上服务更新
```

优点：

- 简单
- 易上手
- 服务器代码和 Git 版本一致
- 可以通过 Git 回滚

缺点：

- 服务器需要构建
- 服务器性能差时会慢
- 后端源码会存在服务器上

适合短期快速落地。

### 方案 B：本地或 CI 构建 Docker 镜像，服务器只拉镜像

这是更推荐的方案。

流程：

```text
Windows 本地开发后端
        |
        | git commit
        | git push
        v
私有 Git 仓库
        |
        | CI 或本地 Docker 构建镜像
        | docker push
        v
私有镜像仓库
        |
        | 服务器 docker pull
        | docker compose up -d
        v
线上服务更新
```

优点：

- 服务器不需要编译
- 发布快
- 回滚容易
- 更适合闭源后端
- 服务器上可以不保留完整源码

缺点：

- 需要配置 Docker registry
- 初始配置比直接 pull 代码稍复杂

推荐镜像 tag：

```text
registry.example.com/tchat/naapi-tchat-backend:20260510-abcdef
registry.example.com/tchat/naapi-tchat-backend:latest
```

服务器更新时只需要：

```bash
docker compose pull
docker compose up -d
```

### 方案 C：自建 Gitea / GitLab / Forgejo + Runner

长期可以考虑自建：

- Gitea
- Forgejo
- GitLab CE
- Drone CI
- Woodpecker CI

但如果当前目标是先提升开发效率，不建议一开始就引入太复杂的自建 CI 系统。

可以先使用：

```text
私有 GitHub/GitLab 仓库 + GitHub Actions/GitLab CI + Docker 镜像发布
```

后续再迁移到自建。

## 为什么不建议用文件同步工具做正式发布

Syncthing、rsync、SFTP 自动同步等工具可以临时辅助开发，但不建议作为正式发布方案。

原因：

- 没有清晰版本记录
- 不方便回滚
- 容易同步半成品代码
- 容易误传 `.env`、密钥、临时文件
- 出问题后难以定位是哪次变更导致的

正式发布更建议走：

```text
Git commit -> build image -> deploy image
```

## Android 环境配置建议

Android 端建议区分三个环境：

```text
dev     -> 本地后端
staging -> 测试服务器
prod    -> https://t.naapi.cc
```

例如：

```kotlin
buildConfigField("String", "TCHAT_API_BASE_URL", "\"https://t.naapi.cc\"")
```

本地调试方式：

- Android 模拟器访问 Windows 本机后端：`http://10.0.2.2:端口`
- 真机 USB 调试：`adb reverse tcp:端口 tcp:端口`
- 真机远程测试：Tailscale、Cloudflare Tunnel、局域网 IP 或 staging 域名

注意：

- `BuildConfig` 中不能放 secret。
- Android 客户端中的任何内容都可能被反编译。
- 客户端只保存公开 endpoint 和非敏感配置。

## 服务器部署建议

推荐服务器部署拓扑：

```text
Caddy / Nginx / Traefik
        |
        | expose 80/443
        v
tchat-wrapper
        |
        | Docker internal network
        v
naapi-core
        |
        v
PostgreSQL / MySQL / Redis
```

公网只开放 HTTPS。

`tchat-wrapper` 和 `naapi-core` 之间优先使用 Docker service name 通信：

```text
http://naapi-core:8080
```

不要让 wrapper 通过公网域名绕一圈访问核心服务：

```text
https://naapi.cc
```

除非有明确需求。

## 安全边界

由于 Android 端开源，所有安全边界必须放在后端。

后端至少需要：

- 用户登录鉴权
- session token 或 access token
- 服务端额度校验
- 服务端计费
- 速率限制
- device id / user id / session id 绑定
- 异常用量检测
- 风控策略
- wrapper 到 core 的内部鉴权

如果 `naapi-core` 必须暴露公网接口，则需要：

- service token
- IP allowlist
- 请求签名
- 可选 mTLS
- 内部接口和公开接口隔离

## 推荐最终形态

```text
本地 Windows：
- Android Studio 编译 TChat
- 后端本地调试
- Docker Desktop 可选

Git：
- TChat Android public repo
- naapi-tchat-backend private repo

CI：
- 后端私有仓库 push 后自动构建 Docker 镜像
- 镜像推送到私有 registry

服务器：
- 只运行 Docker Compose
- Caddy/Nginx/Traefik 负责 HTTPS
- t.naapi.cc -> tchat-wrapper
- wrapper -> naapi-core 走内部网络
- .env.production 只存在服务器或 CI secret 中
```

## 推荐落地步骤

第一阶段：先替代手动上传。

1. 给 `naapi-tchat-backend` 建立私有 Git 仓库。
2. 服务器准备 `docker-compose.yml` 和 `.env.production`。
3. 本地后端改完后：

```powershell
git add .
git commit -m "更新TChat内置服务包装层"
git push
```

4. 服务器执行：

```bash
git pull --ff-only
docker compose up -d --build
```

第二阶段：解决服务器构建慢。

1. 后端增加 `Dockerfile`。
2. 本地或 CI 构建 Docker 镜像。
3. 推送到私有 registry。
4. 服务器只执行：

```bash
docker compose pull
docker compose up -d
```

第三阶段：自动化发布。

```text
push main        -> deploy staging
tag v1.2.3       -> deploy production
rollback v1.2.2  -> 回滚生产
```

## 建议给 AI 进一步追问的问题

可以把这份方案发给另一个 AI，然后继续问：

1. 这个架构有没有明显安全漏洞？
2. Android 开源、后端闭源的边界是否合理？
3. `tchat-wrapper` 和 `naapi-core` 应该如何拆分接口？
4. Docker Compose 网络应该怎么设计？
5. 服务器性能较弱时，应该选本地构建镜像还是 CI 构建镜像？
6. 如何设计 staging / production 发布流程？
7. 如何避免用户绕过 TChat 客户端直接刷接口？
8. 是否需要 mTLS，还是内部 service token 足够？
9. Android 端应该如何设计 dev / staging / prod 的 endpoint 切换？
10. 后端私有仓库和 Android 开源仓库之间是否需要 OpenAPI contract？

## 当前最推荐结论

当前最适合的路线是：

```text
Android：
继续在 Windows 本地开发和编译。
仓库可以开源。
只接入 https://t.naapi.cc。
不放任何后端 secret。

后端：
naapi-tchat-backend 放私有 Git 仓库。
用 Docker 封装。
本地或 CI 构建镜像。
服务器只 pull 镜像并重启容器。

服务器：
naapi.cc 保持核心服务。
t.naapi.cc 作为 TChat wrapper 入口。
wrapper 和 core 走 Docker 内部网络。
公网只开放 HTTPS。
```

这套方案可以同时满足：

- Android 继续开源
- 后端继续闭源
- Windows 本地继续负责 Android 编译
- 服务器不用承担 Android 编译或重型构建压力
- 本地和线上通过 Git / 镜像保持同步
- 支持后续 staging、回滚、灰度发布
