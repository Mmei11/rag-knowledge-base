# Day 1 环境检查记录

检查日期：2026-07-07

## 已完成

| 工具 | 状态 | 版本 / 说明 |
| --- | --- | --- |
| JDK | 已安装 | Eclipse Temurin JDK 21.0.11 |
| Maven | 已安装 | Apache Maven 3.9.16，安装路径：`D:\MAIN\tools\apache-maven-3.9.16` |
| Node.js | 已安装 | v22.19.0 |
| npm | 已安装 | 10.9.3，需要使用 `npm.cmd` 或调整 PowerShell 执行策略 |
| Git | 已安装 | git version 2.52.0.windows.1 |
| Docker Desktop | 已安装 | Docker CLI 29.6.1 |

## 环境变量

| 变量 | 值 |
| --- | --- |
| JAVA_HOME | `C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot` |
| MAVEN_HOME | `D:\MAIN\tools\apache-maven-3.9.16` |

## 当前遗留问题

Docker Desktop 已安装，但 Docker 引擎暂未验证通过。

当前错误：

```text
permission denied while trying to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine
```

2026-07-07 复测结果：

```text
Docker CLI 正常：Docker version 29.6.1
Docker Desktop Service 状态：Stopped
docker version 报错：failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine
wsl --status 报错：E_ACCESSDENIED
```

2026-07-07 再次复测结果：

```text
Docker Desktop 界面左下角显示：Engine running
docker compose version 正常：Docker Compose version v5.3.0
命名管道已存在：docker_engine、dockerDesktopLinuxEngine
docker desktop start 返回：Docker Desktop is already running
Codex 当前终端执行 docker version 仍返回：permission denied while trying to connect to the docker API
```

当前判断：Docker Desktop 本身已经启动，但当前 Codex/IDE 终端进程仍无法访问 Docker API 命名管道。优先尝试重启 IDE、重新打开终端，或在普通 PowerShell 中单独执行 `docker version` 验证。

2026-07-07 重启 Cursor 后复测结果：

```text
java -version 正常：OpenJDK 21.0.11
mvn -version 正常：Apache Maven 3.9.16，Java 21.0.11
node -v 正常：v22.19.0
git --version 正常：2.52.0.windows.1
docker compose version 正常：v5.3.0
docker desktop start 返回：Docker Desktop is already running
docker --context desktop-linux version 仍返回：permission denied while trying to connect to the docker API
Docker Desktop Service 状态仍为：Stopped
```

当前判断更新：JDK、Maven、Node、Git 已可用；Docker Desktop 已安装并能启动界面，但当前终端无法访问 Docker API。下一步建议重启 Windows，或用管理员 PowerShell 启动 Docker Desktop Service 后再验证。

2026-07-07 重启 Windows 后复测结果：

```text
docker compose version 正常：Docker Compose version v5.3.0
docker desktop start 正常：Starting Docker Desktop
docker version 仍失败：permission denied while trying to connect to the docker API
当前用户 token 包含 docker-users 组
Docker 命名管道已存在：docker_engine、dockerDesktopLinuxEngine
Docker Desktop Service 状态：Stopped
wsl --status 失败：E_ACCESSDENIED
LxssManager 状态：Stopped
vmcompute 状态：Running
当前终端无法启动 LxssManager：Cannot open LxssManager service
```

当前判断更新：Docker CLI、Docker Compose、Docker Desktop 已安装；问题集中在 WSL2 的 `LxssManager` 服务没有运行，且当前终端权限不足以启动该服务。需要在管理员 PowerShell 中启动 WSL 服务或修复 WSL 权限。

2026-07-07 管理员 PowerShell 修复后结果：

执行命令：

```powershell
Start-Service LxssManager
Start-Service com.docker.service
wsl --status
docker version
```

验证结果：

```text
wsl --status 正常：
默认分发: docker-desktop
默认版本: 2

docker version 正常：
Client: Docker 29.6.1
Server: Docker Desktop 4.80.0
Engine: 29.6.1
OS/Arch: linux/amd64
```

Codex 当前非管理员终端仍然无法直接访问 Docker API，返回 `permission denied while trying to connect to the docker API`。但管理员 PowerShell 已确认 Docker 引擎可用，Day 1 Docker 环境验收通过。后续如果 Codex 需要直接执行 `docker compose`，需要在能访问 Docker API 的终端中运行，或以合适权限重新启动 IDE/Codex。

已确认当前用户在 `docker-users` 用户组中。该问题通常需要以下操作之一：

1. 启动 Docker Desktop 并完成首次启动界面确认。
2. 注销并重新登录 Windows，让 `docker-users` 用户组权限生效。
3. 重启电脑后重新执行 `docker version`。
4. 如果仍失败，以管理员身份启动 Docker Desktop 或检查 Docker Desktop Service。

## 验证命令

新开一个 PowerShell 或终端后执行：

```powershell
java -version
mvn -version
node -v
npm.cmd -v
git --version
docker version
```

期望结果：

- `java -version` 显示 21.x。
- `mvn -version` 显示 Maven 3.9.16，并使用 Java 21。
- `docker version` 同时显示 Client 和 Server 信息。
