# GuardianBackup

## 项目概述

GuardianBackup 是一个基于 Kotlin 开发的 Android 应用程序，旨在为 Android 设备提供安全、高效的数据备份功能。该项目采用模块化架构，包括主应用模块和核心逻辑模块，便于维护和扩展。目前项目处于早期开发阶段，已发布 v1.0.1 版本，并完成了用户界面的中文翻译。

## 主要特性

- **数据备份**：支持备份设备上的文件、应用数据等（具体功能根据代码实现）。
- **中文界面**：用户体验已翻译为中文，便于国内用户使用。
- **模块化设计**：使用 `app/` 和 `core/` 模块分离 UI 和核心逻辑。
- **文档支持**：包含分析报告等文档，便于理解项目结构。

（更多特性将随着项目开发逐步添加）

## 系统要求

- Android 最低版本：API 21（Android 5.0）或更高（根据标准 Android 项目推测）。
- 构建工具：Android Studio（推荐最新版本）。

## 安装与使用

### 用户安装
1. 从 [Releases](https://github.com/zhangpy0/GuardianBackup/releases) 页面下载最新 APK（如 v1.0.1）。
2. 在设备设置中启用“允许未知来源应用安装”。
3. 安装 APK 并授予必要的权限（存储等）。

### 开发者构建
1. 克隆仓库：
   ```
   git clone https://github.com/zhangpy0/GuardianBackup.git
   ```
2. 在 Android Studio 中打开项目。
3. 同步 Gradle 文件并构建项目。
4. 在模拟器或真实设备上运行。

## 项目结构

- **app/**：主应用模块，包含 UI 和入口。
- **core/**：核心逻辑、工具类和共享组件。
- **doc/**：文档目录，包含分析报告等。
- **gradle/**：Gradle 构建脚本。

## 从源代码构建

在项目根目录运行以下命令：

- 构建 Debug 版本：`./gradlew assembleDebug`
- 构建 Release 版本：`./gradlew assembleRelease`（需配置签名）

## 贡献代码

欢迎贡献！操作步骤：
1. Fork 本仓库。
2. 创建新分支进行功能开发或修复。
3. 提交清晰的 commit 消息。
4. 打开 Pull Request 并描述变更内容。

请遵守 Kotlin 编码规范，并进行充分测试。

## 许可证

本项目采用 MIT 许可证（若未指定，请参考标准开源实践）。详情见 [LICENSE](LICENSE) 文件（若不存在，可添加）。

## 联系方式

如有问题或建议，请在 GitHub 上打开 Issue，或联系维护者 [zhangpy0](https://github.com/zhangpy0)。

最后更新：2026 年 1 月 4 日
