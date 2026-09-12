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

- Java 11 或更高版本



- Debian / Ubuntu 等基于 APT 的 Linux 发行版
- `pkexec`（通常随 PolicyKit 预装，用于图形化提权）
- `apt-cache`、`apt-get`、`dpkg`（APT 系统自带
