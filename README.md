# 应用更新管理器（Updates Manager Extended Mod）

## 简介

**UpdatesManagerExtended-Mod** 是基于 Senliast 的 Updates Manager Extended v4.2 开发的增强版本，包含多语言完善、libxposed API 102 适配、热重载和使用体验优化。

这是一个 Xposed 模块，可禁止指定应用更新（包括自动更新），无论应用来自哪个应用商店。选中需要禁止更新的应用后，应用商店仍会检测到更新，但无法安装更新。

你可以创建应用分组，为各分组选择需要拦截的应用商店。模块和分组均支持定时暂停：临时停用后即可进行所需更新，到时会自动恢复，无需再次手动启用。

被禁止更新的应用仍可通过 Google Play 验证许可证并进行应用内购买。此模块不挂钩 Google Play 或 Google Play 服务，因此不会因这些挂钩而影响 SafetyNet / Play Integrity。

 Google Play 的自动更新策略可能忽略或重置用户为单个应用设置的自动更新选项。相关讨论：

- [关闭自动更新后，Play 商店仍悄悄更新应用](https://android.stackexchange.com/questions/216208/play-store-silently-updates-apps-although-auto-update-is-disabled)
- [禁用自动更新后，Google Play 商店仍更新应用](https://android.stackexchange.com/questions/246911/what-is-google-up-to-google-play-store-updating-apps-even-though-auto-update-is)
- [已禁用自动更新，应用仍自动更新](https://android.stackexchange.com/questions/162909/apps-auto-updating-even-if-disabled)
- [Google Play 商店自行更新全部应用](https://forums.androidcentral.com/threads/google-play-store-just-auto-updated-all-my-apps-on-its-own-wtf.273772/)
- [关闭自动更新后，Google Play 应用仍持续更新](https://eu.community.samsung.com/t5/galaxy-s22-series/google-play-apps-keep-automatically-updating-when-i-ve-turned/td-p/7338657)

## 兼容性

已迁移至现代 **libxposed API 102**，需要支持该 API 的 LSPosed 或兼容框架；不再使用旧版 Xposed API。

- 应用商店：所有应用商店。
- CPU：arm、arm64、x86、x64。
- Android：
  - 支持 AOSP Android 13–14，预计支持 AOSP Android 12、15、16。
  - 不支持 Android 11 及以下版本。
  - 对于三星、MIUI 等厂商系统，只要 Android 版本匹配，可能可以运行，但不作保证。

## 下载



## 安装与使用

1. 安装 APK。
2. 在 LSPosed 管理器中启用模块，并选择 “系统框架”作用域。
3. 首次启用，重启设备。
4. 打开应用，启用模块，进入“管理规则分组”。
5. 在分组中选择禁止更新的应用，以及要拦截的安装来源（应用商店）。启用“拦截所有安装来源的更新”可拦截该分组内应用来自所有来源的更新。
6. 需要临时更新时，可暂停模块或分组 15、30、45、60 分钟，也可选择停用直到手动启用。

**本版支持 API 102 热重载。正常更新后，框架会自动加载新代码；打开应用时也会检查运行状态并尝试热重载。只有首次启用、从不支持热重载的旧版升级或热重载失败时才需要重启。**

## 卸载

1. 卸载应用。
2. 重启设备。

## 常见问题

**模块如何工作？**

模块挂钩 Android 的软件包安装流程。当应用开始更新时，会检查目标包名是否在拦截列表中。如果在列表中，就模拟软件包验证失败，使安装无法完成。

**为什么作用域需要“系统框架”，而不是“软件包安装程序”？**

软件包安装程序主要提供安装 APK 的图形界面。真正处理安装逻辑和流程的组件位于 Android 系统的 com.android.server.pm 中，因此需要系统框架作用域。

**是否兼容 WSA？**

支持 WSA。

## 源码构建

Android 项目位于 source/，使用 Java 17 源码兼容级别、编译 SDK 37、目标 SDK 34、Android Gradle Plugin 8.10.1 和 Gradle 8.11.1。构建环境需要 JDK 17 或以上及 Android SDK 37。最低安装版本为 Android 8（API 26），实际拦截逻辑仍面向 Android 12 或以上。

依赖为 Maven Central 正式发布的 io.github.libxposed:api:102.0.0（仅编译时）和 io.github.libxposed:service:102.0.0（打包至应用）。编译 SDK 37 是新版库要求，不表示应用只能在 Android 17 上运行。现有构建工具可以完成编译，但 AGP 8.10.1 会提示其已测试的最高编译 SDK 为 36。

调试版可在 source/ 目录运行 `./gradlew assembleDebug`（Windows 使用 `gradlew.bat assembleDebug`）构建，无需发布签名配置。需要在本机配置 Android SDK 路径，例如设置 ANDROID_HOME。

发布版签名配置文件为 source/keystore.properties，其中包含 storeFile、storePassword、keyAlias 和 keyPassword。未配置时，`assembleRelease` 生成未签名 APK。原作者签名密钥未随源码提供，使用不同签名构建的 APK 通常不能直接覆盖原版，即使包名相同。

## 原作者

Senliast：[原项目](https://github.com/Senliast/xposed-modules/tree/main/Updates_Manager_Extended)。

UpdatesManagerExtended-Mod 保留原版 v4.2 的应用更新拦截、规则分组和定时暂停功能，并进行了以下增强：

- **完善多语言支持**：新增简体中文和繁体中文，补齐并润色英文、德文和俄文。应用名称、模块说明、界面和操作提示随系统语言显示。

- **适配 libxposed API 102**：将旧版 Xposed 接口迁移至现代模块入口、系统进程回调及 Hook 接口，使用新版系统框架作用域。

- **改进设置共享**：通过 LSPosed 服务将规则同步至系统进程，不再依赖公开可读的设置文件。修改规则无需重启，同时保留本地设置和原有备份格式。

- **支持更新后的热重载**：启用自动热重载，通过原子替换旧 Hook 加载新代码并保留规则；打开应用时也会检查状态并尝试重载。首次启用仍需重启。

- **修正模块状态识别**：分别显示热重载中、未检测到模块、状态查询失败及需要重启等情况。

- **优化列表默认排序**：应用列表和安装来源列表均默认选中“优先显示已拦截项”，方便查看和管理已有规则，仍可手动取消。

  

## 关键词

Google Play、禁止应用更新、禁止自动更新、应用更新拦截、Xposed、LSPosed、Root、Magisk。
