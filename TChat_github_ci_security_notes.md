# TChat 后端使用 GitHub CI 的源码泄露风险评估

## 背景

当前项目情况：

- Android 仓库：`C:\Users\Administrator\AndroidStudioProjects\TChat`
- 后端仓库：`C:\1Git\tchat对接naapi方案\naapi-tchat-backend`
- Android 端计划开源。
- 后端不计划开源，因为涉及计费、额度、供应商密钥、风控和内部路由。
- 希望使用 GitHub CI 构建后端 Docker 镜像，然后服务器只拉取镜像运行，避免服务器承担重型构建任务。

核心问题：

```text
如果使用 GitHub CI / GitHub Actions，是否会导致后端源码泄露？
```

## 简短结论

不会因为“使用 GitHub CI”就自动公开泄露源码。

如果后端仓库是 GitHub 私有仓库，GitHub Actions 构建不会把源码公开给外界。但需要注意：

```text
后端源码会进入 GitHub 私有仓库。
后端源码会在 GitHub-hosted runner 的临时构建环境中被 checkout 和构建。
```

所以这件事的本质是：

```text
你是否信任 GitHub 私有仓库和 GitHub-hosted runner。
```

如果安全要求是：

```text
后端不能开源，但可以放在 GitHub private repo。
```

那么 GitHub CI 可以使用。

如果安全要求是：

```text
后端源码绝对不能离开自己的机器或自己的服务器。
```

那么不应该使用 GitHub-hosted runner，也不应该把后端源码放到 GitHub。应该选择本地构建镜像、自建 Git 服务、自建 CI 或 self-hosted runner。

## 推荐判断

对 TChat 当前阶段来说，比较现实的建议是：

```text
可以使用 GitHub CI，但后端仓库必须 private。
CI 只负责构建 Docker 镜像并推送到私有镜像仓库。
不要让 CI 输出源码、上传源码 artifact、打印 secret 或直接暴露生产权限。
```

## GitHub CI 的真实风险点

### 1. 私有仓库误改为公开仓库

这是最直接的泄露风险。

防护建议：

- 后端仓库必须设置为 private。
- 后端仓库最好放在单独的 GitHub Organization 中。
- 只给必要成员权限。
- 开启 2FA。
- 限制谁可以修改仓库 visibility。

### 2. CI 日志打印了敏感信息

危险示例：

```bash
printenv
cat .env
echo $DATABASE_URL
echo $JWT_SECRET
set -x
```

这些命令可能把环境变量、数据库地址、token、secret 打到 GitHub Actions 日志里。

GitHub secrets 会尝试脱敏，但不能依赖它兜底。secret 被拼接、编码、截断、变形后，不一定能被完全遮盖。

防护建议：

- workflow 中不要打印环境变量。
- 不要 `cat .env`。
- 不要在生产 workflow 里使用 `set -x`。
- 不要把完整请求 header、数据库连接串、token 打到日志。

### 3. 上传 artifact 时误传源码或配置

危险示例：

```yaml
- uses: actions/upload-artifact@v4
  with:
    path: .
```

这可能上传整个工作区，包括源码、配置文件、临时文件等。

防护建议：

- 构建 Docker 镜像时通常不需要上传 artifact。
- 不要上传整个项目目录。
- 如果必须上传，只上传明确的构建产物，例如：

```yaml
path: dist/app.tar.gz
```

不要写：

```yaml
path: .
```

### 4. Docker 镜像打进了不该打的内容

危险 Dockerfile：

```dockerfile
COPY . .
```

如果没有 `.dockerignore`，可能把下面内容打进镜像：

```text
.git/
.env
.env.production
*.pem
*.key
local.properties
测试数据
临时 token
IDE 配置
```

防护建议：

后端仓库必须有 `.dockerignore`，例如：

```gitignore
.git
.github
.env
.env.*
*.pem
*.key
local.properties
node_modules
build
dist
coverage
.idea
.vscode
```

注意：

如果后端是 Node.js / Python / PHP 这类解释型语言，最终 Docker 镜像里通常仍然包含业务代码。镜像不是 Git 仓库，但拿到镜像的人理论上可以读取里面的代码。

所以：

```text
镜像仓库也必须是 private。
服务器镜像拉取凭证也要保护好。
```

### 5. 使用了不可信的第三方 GitHub Action

GitHub Actions 可以运行第三方 action。如果第三方 action 被攻破，或者你使用的 action 本身恶意，它可能读取源码、读取 token、读取 secrets，甚至操作仓库。

防护建议：

- 优先使用官方 action 或可信 action。
- 避免冷门 action。
- 对生产 workflow，建议 pin 到完整 commit SHA，而不是只写 `@v4`。
- 限制 `GITHUB_TOKEN` 权限。

普通写法：

```yaml
uses: docker/login-action@v3
```

更严格写法：

```yaml
uses: docker/login-action@完整commit_sha
```

### 6. `GITHUB_TOKEN` 权限过大

不建议使用：

```yaml
permissions: write-all
```

构建并推送 GHCR 镜像，一般只需要：

```yaml
permissions:
  contents: read
  packages: write
```

如果只是测试构建：

```yaml
permissions:
  contents: read
```

防护建议：

- 每个 workflow 显式声明 permissions。
- 按需给最小权限。
- 不要给无关的 `actions: write`、`contents: write`、`deployments: write`。

### 7. PR 触发生产部署

生产部署不应该由普通 PR 直接触发。

危险事件：

```yaml
on:
  pull_request:
```

尤其需要谨慎：

```yaml
pull_request_target
```

如果配置错误，不可信 PR 代码可能接触高权限 token 或 secrets。

防护建议：

- 生产部署只允许 tag 或手动触发。
- staging 可以跟随 main 分支。
- 不要让 PR workflow 接触生产 secrets。
- 不要在 PR workflow 中执行生产部署脚本。

推荐触发方式：

```yaml
on:
  push:
    branches:
      - main
    tags:
      - "v*.*.*"
  workflow_dispatch:
```

### 8. 日志和 artifact 保留时间过长

GitHub Actions 日志和 artifact 有保留期。保留时间越长，泄露窗口越大。

防护建议：

- 后端私有仓库把 Actions 日志和 artifact 保留期调短。
- 如果 CI 不需要 artifact，就不要上传 artifact。
- 可以设置为 7 天或更短，视团队需要而定。

## 推荐 GitHub CI 流程

建议使用如下流程：

```text
Windows 本地开发后端
        |
        | git commit
        | git push
        v
GitHub 私有仓库
        |
        | GitHub Actions 构建 Docker 镜像
        v
私有 GHCR / 私有 Docker Registry
        |
        | 服务器 docker compose pull
        | 服务器 docker compose up -d
        v
线上 t.naapi.cc
```

其中：

```text
源码：只在 GitHub private repo 和 GitHub runner 中出现
镜像：进入 private container registry
服务器：只拉镜像，不需要保存完整源码
密钥：放 GitHub Secrets / 服务器 .env，不进 Git
```

## 推荐的最低安全配置清单

如果选择 GitHub CI，至少应该满足：

- 后端仓库是 private。
- GitHub 账号开启 2FA。
- 后端仓库单独放一个 private Organization。
- 仓库成员权限最小化。
- 不提交 `.env`、API key、数据库密码、私钥。
- `.gitignore` 和 `.dockerignore` 都配置好。
- workflow 中不使用 `printenv`、`cat .env`、`set -x`。
- 不上传整个源码目录作为 artifact。
- Docker 镜像推送到 private registry。
- 显式限制 `GITHUB_TOKEN` 权限。
- 生产部署只允许 tag 或手动触发。
- PR workflow 不接触生产 secrets。
- 第三方 action 尽量使用可信来源，生产环境最好 pin 到完整 commit SHA。
- Actions 日志和 artifact 保留期调短。
- 生产 secrets 使用 GitHub Environments，并开启 required reviewers。
- 服务器上的 `.env.production` 不进 Git，不进镜像。
- Docker 镜像不要打包 `.git`、`.env`、私钥、测试数据。

## 一个相对安全的 GitHub Actions 示例

假设使用 GitHub Container Registry，也就是 GHCR。

```yaml
name: Build backend image

on:
  push:
    branches:
      - main
    tags:
      - "v*.*.*"
  workflow_dispatch:

permissions:
  contents: read
  packages: write

jobs:
  build:
    runs-on: ubuntu-24.04

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Login to GHCR
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build image
        run: |
          docker build \
            -t ghcr.io/你的账号或组织/naapi-tchat-backend:${{ github.sha }} \
            -t ghcr.io/你的账号或组织/naapi-tchat-backend:latest \
            .

      - name: Push image
        run: |
          docker push ghcr.io/你的账号或组织/naapi-tchat-backend:${{ github.sha }}
          docker push ghcr.io/你的账号或组织/naapi-tchat-backend:latest
```

这个 workflow 的特点：

- 不上传源码 artifact。
- 不打印 secrets。
- `GITHUB_TOKEN` 只读源码、只写 package。
- 不直接登录生产服务器。
- 只负责构建和推送镜像。

## 是否要让 GitHub Actions 直接 SSH 到服务器部署

可以，但第一版不建议。

更稳妥的第一阶段是：

```text
GitHub Actions 只负责构建并推送镜像。
服务器由人工或服务器侧脚本执行 docker compose pull + up。
```

服务器更新：

```bash
docker compose pull
docker compose up -d
```

等流程稳定后，再考虑 GitHub Actions 自动 SSH 到服务器。

如果必须自动 SSH 部署：

- 使用专用 deploy key。
- 不要使用 root 私钥。
- 服务器上创建低权限 deploy 用户。
- 该用户最好只能执行固定部署脚本。
- SSH key 只用于该项目。
- 不要让该 key 拥有服务器全权限。

## 更高安全等级的替代方案

### 方案 1：GitHub private repo + self-hosted runner

```text
GitHub private repo
        |
        v
自己的 Windows / Linux 机器上的 self-hosted runner 构建
        |
        v
私有镜像仓库
```

优点：

- 仍然可以使用 GitHub 的仓库和 workflow。
- 构建不在 GitHub-hosted runner 上跑。

缺点：

- 源码仍然托管在 GitHub private repo。
- 需要维护 runner 机器。

### 方案 2：本地构建镜像，不使用 GitHub CI

```text
Windows 本地
docker build
docker push
        |
        v
服务器 docker pull
```

优点：

- 源码泄露面更小。
- 不需要把构建交给 GitHub runner。

缺点：

- 自动化程度低一些。
- 需要本地 Docker 环境稳定。

### 方案 3：完全自建 Git + CI

```text
Forgejo / Gitea / GitLab 私有部署
        |
        v
自建 Runner
        |
        v
私有镜像仓库
```

优点：

- 源码控制最强。
- 不依赖 GitHub 托管。

缺点：

- 维护成本最高。
- 需要自己负责备份、安全、升级、权限管理。

## 适合 TChat 当前阶段的建议

推荐路线：

```text
GitHub private repo
        |
        v
GitHub Actions 构建 Docker 镜像
        |
        v
Private GHCR / private registry
        |
        v
服务器 docker compose pull + up
```

不要第一版就让 GitHub Actions 直接持有生产服务器 root 权限。

第一阶段可以这样做：

```text
CI 只构建镜像和推送镜像。
服务器部署先手动执行。
```

第二阶段再考虑：

```text
tag v1.2.3 后自动部署 production。
main 分支 push 后自动部署 staging。
```

## 可以接受和不能接受的边界

可以接受：

```text
后端源码放 GitHub private repo。
GitHub-hosted runner 临时 clone 源码构建。
Docker 镜像推送到 private registry。
服务器只运行镜像，不保存完整源码。
```

不能接受：

```text
后端仓库 public。
secret 进入 Git。
.env 被打进 Docker 镜像。
CI 日志打印密钥。
上传整个源码目录作为 artifact。
PR 触发生产部署。
第三方 action 权限过大且未审计。
Android 开源仓库包含任何后端 secret。
```

## 给其他模型继续评估的问题

可以把这份文档发给其他模型，然后继续问：

1. 这个 GitHub CI 方案是否适合闭源后端？
2. 还有哪些容易被忽略的源码泄露点？
3. 是否应该使用 GitHub-hosted runner，还是 self-hosted runner？
4. GHCR 私有镜像是否足够，还是应该自建 registry？
5. Dockerfile 和 `.dockerignore` 应该怎么写更安全？
6. GitHub Actions 是否应该直接 SSH 到生产服务器？
7. 生产部署应该用 tag、manual dispatch，还是 main 分支自动发布？
8. 如何避免 PR workflow 接触生产 secrets？
9. 后端是解释型语言时，镜像中包含源码是否可接受？
10. 如果后端极度敏感，是否应该完全放弃 GitHub CI？

## 参考链接

- GitHub-hosted runners 官方说明：<https://docs.github.com/en/actions/concepts/runners/github-hosted-runners>
- GitHub Actions secrets 官方说明：<https://docs.github.com/en/actions/concepts/security/secrets>
- GitHub Actions 安全加固官方说明：<https://docs.github.com/en/actions/reference/security/secure-use>
- GitHub Actions 日志和 artifact 保留期：<https://docs.github.com/en/organizations/managing-organization-settings/configuring-the-retention-period-for-github-actions-artifacts-and-logs-in-your-organization>
