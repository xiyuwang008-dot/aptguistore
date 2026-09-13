# APT 图形软件管理器 (apt-gui)

一个基于 Java Swing 的 APT 软件包图形化管理工具，支持搜索 APT 源中的软件包并一键安装。

## 功能特性

- 🔍 **软件包搜索** — 通过关键词搜索 APT 源中的软件包（`apt-cache search`）
- 📋 **包详情查看** — 显示版本、架构、大小、依赖、描述等完整信息（`apt-cache show`）
- 📦 **一键安装** — 选中软件包后点击安装，通过 `pkexec` 弹出图形化密码认证
- 📝 **实时日志** — 安装过程实时输出终端日志
- 🔄 **刷新软件源** — 内置 `apt-get update` 功能
- ✅ **安装状态检测** — 自动识别软件包是否已安装

## 系统要求

- Java 11 或更高版本（JRE 即可运行，JDK 用于编译）

还没安装的请在终端执行这个指令
sudo apt install openjdk-21-jre

- Debian / Ubuntu 等基于 APT 的 Linux 发行版
- `pkexec`（通常随 PolicyKit 预装，用于图形化提权）
- `apt-cache`、`apt-get`、`dpkg`（APT 系统自带）

## 快速开始

### 方式一：直接运行（已打包好 JAR）

```bash
./run.sh
```

或手动执行：

```bash
java -jar apt-gui.jar
```

### 方式二：从源码构建

```bash
./build.sh    # 编译并打包
./run.sh      # 运行
```

## 使用说明

1. **搜索**：在顶部搜索框输入软件包名称（如 `firefox`、`vim`、`chrome`），按回车或点击「搜索」按钮
2. **查看详情**：在左侧结果列表中点击任意软件包，右侧上方显示详细信息
3. **安装**：点击底部「安装」按钮，确认后会弹出 `pkexec` 密码输入窗口，输入管理员密码开始安装
4. **查看日志**：右侧下方区域实时显示安装输出
5. **刷新源**：点击「刷新源」按钮执行 `apt-get update`

## 项目结构

```
apt-gui/
├── src/com/aptgui/
│   ├── AptGui.java        # 主界面（Swing GUI）
│   ├── AptManager.java    # APT 命令封装（搜索/查询/安装）
│   └── PackageInfo.java   # 软件包信息模型
├── out/                   # 编译输出（class 文件）
├── apt-gui.jar            # 可执行 JAR 包
├── MANIFEST.MF            # JAR 清单（指定主类）
├── build.sh               # 构建脚本
├── run.sh                 # 运行脚本
└── README.md              # 本文件
```

## 技术实现

- **GUI 框架**：Java Swing（JDK 标准库，无需额外依赖）
- **后台任务**：`SwingWorker` 确保搜索/安装不阻塞 UI 线程
- **命令执行**：`ProcessBuilder` 调用系统 APT 命令
- **提权方式**：`pkexec`（PolicyKit），替代 `sudo`，提供图形化密码对话框
- **实时输出**：独立线程读取进程输出流，通过 `SwingWorker.publish` 推送至 UI

## 注意事项

- 安装软件需要管理员权限，`pkexec` 会弹出系统认证窗口
- 如果系统没有 `pkexec`，可安装：`sudo apt install policykit-1`
- 本工具仅封装 APT 命令，不修改系统配置
- 搜索结果数量取决于本地 APT 缓存，建议先点击「刷新源」
