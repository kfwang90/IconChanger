# IconChanger

IconChanger 是一个安卓应用，用于为已安装应用创建带有自定义图标的桌面快捷方式。

## 功能
- 列出设备上所有可启动应用
- 搜索应用
- 选择图片作为新图标
- 自定义快捷方式名称
- 跳转系统设置处理权限问题

## 构建
环境：JDK 17、Android SDK 34、Gradle 8.9

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_SDK_ROOT=/path/to/android-sdk
./gradlew assembleRelease
```

APK 输出：`app/build/outputs/apk/release/IconChange.apk`

## 说明
本应用通过 Android 的 `ShortcutManager` 创建固定快捷方式，需要启动器支持。原始应用图标不会被修改。
