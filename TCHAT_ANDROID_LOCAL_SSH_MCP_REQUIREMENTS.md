# TChat Android 本地 ssh-mcp / SSH 工具需求文档

日期：2026-05-11

## 1. 背景与边界

TChat 当前 Android 开源客户端已经具备 MCP client 和本地工具执行体系：

- Android 端可配置 MCP server URL，并通过 SSE / Streamable HTTP 调用外部 MCP 工具。
- MCP 工具会被转换为 TChat 内部 `Tool`，再由聊天流程在 Android 本地执行。
- 文件系统、网页抓取、系统信息、sleep 等能力已经作为 Android 本地工具存在。

本需求重新定义 `mcp ssh` 的正确边界：

```text
t.naapi.cc
  - TChat 官方主服务
  - 负责账号、套餐、授权、模型 API、NAAPI 请求
  - 不代理 SSH，不保存 SSH 私钥，不连接用户目标服务器

tchatserver.wanxiaot.com
  - SSH 拓展下载与更新分发站
  - 负责安装包、版本清单、签名、校验和、说明文档
  - 不承载套餐、账号、业务主逻辑，不保存 SSH 配置

Android TChat
  - 用户端本地运行
  - 本地保存用户 SSH profile
  - 本地执行 SSH/SFTP/日志读取/只读命令
  - 模型只看到 profile alias，不看到密码、私钥、真实主机细节
```

## 2. 本文档执行规则

一个需求文档必须一次性闭环完成：

1. 先明确文档中的实现范围和验收标准。
2. 按文档完成代码实现。
3. 完成构建或测试验证。
4. 重新核对代码实现是否和本文档一致。
5. 在本文档中记录 progression。
6. 如果实现或验证中出现问题，在本文档中记录 fix。
7. 上述步骤完成前，不开始下一个需求文档的开发。

## 3. v1 交付范围

v1 只实现 Android 本地 SSH 只读工具闭环，不实现独立 SSH 拓展 APK，也不实现 Android 内嵌 MCP server。

### 3.1 必须实现

- 新增本地 SSH profile 数据模型。
- 使用 Android Keystore + AES-GCM 对 SSH 密码、私钥、私钥口令做本地加密存储。
- 新增本地 SSH profile 存储服务，基于 SharedPreferences，不引入 Room 迁移。
- 新增 Android 本地 SSH 工具集合。
- 新增 `LocalToolOption.SshReadOnly`。
- 当助手启用 SSH 只读工具时，聊天工具链可以拿到 SSH 工具。
- 模型调用 SSH 工具时，只通过 `profileAlias` 选择目标，不接触密码或私钥。
- SSH 工具输出必须做长度截断和基础敏感信息脱敏。
- 默认拦截敏感路径读取。
- 默认只允许低风险只读命令。

### 3.2 v1 工具列表

```text
ssh_list_profiles
ssh_test_connection
ssh_list_dir
ssh_read_file
ssh_tail_log
ssh_exec_readonly
```

### 3.3 不在 v1 实现

```text
独立 SSH 拓展 APK
Android 内嵌 MCP server
localhost MCP server 生命周期管理
SFTP 上传
远程文件写入
任意 ssh_exec
服务重启
后台长期 SSH session 池
服务端 SSH 审计
服务端 SSH 凭证托管
```

## 4. 安全要求

### 4.1 模型可见内容

模型可以看到：

```text
profile alias
工具名称
工具参数中的路径、行数、只读命令
截断和脱敏后的执行结果
```

模型不能看到：

```text
SSH 密码
SSH 私钥
私钥 passphrase
服务端 NewAPI admin key
支付密钥
官方服务器密钥
```

### 4.2 敏感路径拦截

默认拦截：

```text
~/.ssh
/.ssh
.env
/id_rsa
/id_ed25519
/secrets
/credentials
/private
```

### 4.3 只读命令控制

`ssh_exec_readonly` 只允许以下命令前缀：

```text
ls
cat
tail
head
grep
find
df
du
free
ps
whoami
uname
uptime
pwd
systemctl status
journalctl
docker ps
```

默认拦截：

```text
rm
mkfs
dd
shutdown
reboot
iptables
ufw
systemctl restart
systemctl stop
docker rm
docker system prune
kubectl delete
curl | sh
wget | sh
chmod
chown
sudo
su
```

## 5. 设计方案

### 5.1 本地工具链路

```text
Assistant 开启 SSH 只读工具
  -> MainActivity 创建 LocalTools(context)
  -> LocalTools.getToolsForOptions()
  -> SshLocalTools.getReadOnlyTools()
  -> ChatRepositoryImpl 接收模型 tool call
  -> Tool.execute(JSONObject)
  -> Android 本地通过 SSH/SFTP 执行
  -> 返回脱敏、截断后的 JSON
```

### 5.2 本地 SSH profile 存储

使用 SharedPreferences 存储 profile JSON：

```text
profile id
alias
host
port
username
authType
strictHostKeyChecking
createdAt
updatedAt
encryptedPassword
encryptedPrivateKey
encryptedPassphrase
```

密钥材料用 Android Keystore 生成 AES key，再用 AES-GCM 加密。

### 5.3 依赖选择

v1 使用纯 Java SSH 库：

```text
com.github.mwiede:jsch
```

选择理由：

- 适合 Android 端轻量接入。
- 能支持 password 和 private key。
- 不需要服务端代理。

## 6. 验收标准

代码完成后必须满足：

- Gradle 能完成 `:app:assembleDebug`。
- `LocalToolOption` 中存在 SSH 只读工具选项。
- 启用 SSH 只读工具后，工具链中包含 `ssh_list_profiles` 等 v1 工具。
- SSH profile secret 不以明文保存在 profile JSON 之外。
- SSH 工具调用不会要求模型传入密码或私钥。
- SSH 读取和命令执行结果会进行截断和脱敏。
- 文档 progression/fix 已更新。

## 7. Progression

- 2026-05-11：完成需求文档重设，确认 `mcp ssh` 为 Android 用户端本地工具，不是服务端 SSH 网关。
- 2026-05-11：新增 `com.github.mwiede:jsch` 依赖，用于 Android 本地 SSH/SFTP 连接。
- 2026-05-11：新增本地 SSH profile 模型：
  - `data/src/main/java/com/tchat/data/ssh/SshProfile.kt`
  - 支持 password 和 private key 两种认证方式。
- 2026-05-11：新增 Android Keystore + AES-GCM 本地加密：
  - `data/src/main/java/com/tchat/data/ssh/SshSecretCrypto.kt`
  - SSH 密码、私钥、passphrase 均通过本机 Keystore 派生密钥加密后保存。
- 2026-05-11：新增 SharedPreferences profile store：
  - `data/src/main/java/com/tchat/data/ssh/SshProfileStore.kt`
  - 未引入 Room migration，符合 v1 范围。
- 2026-05-11：新增 SSH 只读本地工具：
  - `data/src/main/java/com/tchat/data/ssh/SshLocalTools.kt`
  - 已实现 `ssh_list_profiles`、`ssh_test_connection`、`ssh_list_dir`、`ssh_read_file`、`ssh_tail_log`、`ssh_exec_readonly`。
- 2026-05-11：新增 `LocalToolOption.SshReadOnly`，并接入 `LocalTools.getToolsForOptions()`。
- 2026-05-11：新增设置页 SSH profile 管理入口：
  - `app/src/main/java/com/tchat/wanxiaot/ui/ssh/SshProfilesScreen.kt`
  - `app/src/main/java/com/tchat/wanxiaot/ui/settings/SettingsScreen.kt`
- 2026-05-11：补齐聊天工具选择面板的 `SshReadOnly` 图标分支：
  - `feature-chat/src/main/java/com/tchat/feature/chat/ToolSelectorSheet.kt`
- 2026-05-11：完成一致性核对：
  - SSH 执行发生在 Android 本地。
  - `t.naapi.cc` 未新增 SSH 代理逻辑。
  - `tchatserver.wanxiaot.com` 未进入业务逻辑，仅保留为后续下载分发定位。
  - 工具 schema 不要求模型传入密码或私钥。
  - 只读工具具备敏感路径拦截、只读命令 allowlist、危险命令 denylist、输出截断和基础脱敏。
- 2026-05-11：完成验证：
  - `./gradlew.bat :app:assembleDebug`
  - `./gradlew.bat testDebugUnitTest`
  - `./gradlew.bat :app:assembleDebug testDebugUnitTest`
  - 三次最终验证均通过。

## 8. Fix

- 2026-05-11：首次构建发现 `LocalToolOption.SshReadOnly` 新增后，`feature-chat/src/main/java/com/tchat/feature/chat/ToolSelectorSheet.kt` 的 `when` 分支不完整。已补充 `SshReadOnly -> Lucide.ShieldCheck`。
- 2026-05-11：首次实现中 `SshProfileStore` 使用 `JSONObject.optString(key, null)` 触发 Kotlin/Java nullable 警告。已改为 `encryptedOrNull()` 统一读取可空密文字段。
- 2026-05-11：一致性核对时发现 `ssh_read_file` 初版会先读取完整远程文件再截断，不符合输出限制的安全意图。已改为流式限制读取，达到上限即停止。
- 2026-05-11：一致性核对时发现 SSH profile 编辑弹窗内容较长，在小屏上存在溢出风险。已为弹窗内容增加滚动容器。
