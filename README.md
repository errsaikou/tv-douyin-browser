# TV 抖音专属浏览器

专为创维电视打造的抖音网页版浏览器，解决 4K 视频卡顿问题，支持手机扫码遥控。

## ✨ 核心功能

1. **防卡顿**：自动拦截 H.265/AV1 编码，强制使用 H.264 播放，告别创维电视硬解卡顿
2. **手机遥控**：手机扫码即可变身无线触摸板 + 键盘，零安装
3. **遥控器适配**：方向键刷视频、确认键暂停播放、返回键导航
4. **扫码登录**：直接在电视上用手机抖音扫码登录，Cookie 自动持久化

## 📦 编译与安装

### 环境要求

- [Android Studio](https://developer.android.google.cn/studio) (Arctic Fox 或更高版本)
- JDK 17
- Android SDK，compileSdk 34

### 编译步骤

1. 用 Android Studio 打开 `tv-douyin-browser` 目录
2. 等待 Gradle Sync 完成（首次可能需要下载依赖）
3. 点击 **Build → Build Bundle(s) / APK(s) → Build APK(s)**
4. 生成的 APK 在 `app/build/outputs/apk/debug/app-debug.apk`

### 安装到电视

**方法 1：ADB 无线安装（推荐）**
```bash
# 电视端：设置 → 关于 → 连续点击"版本号"开启开发者模式
# 电视端：设置 → 开发者选项 → 开启"USB 调试"和"网络调试"
# 电脑端：
adb connect 电视IP地址:5555
adb install app-debug.apk
```

**方法 2：U 盘安装**
- 将 APK 拷贝到 U 盘，插入电视，使用文件管理器打开安装

## 🎮 使用方式

### 遥控器操作
| 按键 | 功能 |
|:---|:---|
| ↑ / ↓ | 上一个 / 下一个视频 |
| ← / → | 快退 / 快进 |
| 确认键 | 暂停 / 播放 |
| 菜单键 | 显示 / 隐藏二维码 |
| 返回键 | 网页后退（双击退出应用）|

### 手机遥控
1. 确保手机与电视连接同一 WiFi
2. 用手机扫描电视右下角二维码
3. 在手机页面上：
   - **滑动触摸板** → 控制电视上的鼠标光标
   - **轻触一下** → 鼠标左键点击
   - **输入框打字** → 发送文字到电视搜索
   - **快捷按钮** → 切换视频 / 暂停 / 返回

## 🏗️ 项目结构

```
app/src/main/
├── assets/
│   ├── inject.js              # 防卡顿脚本（拦截 HEVC/AV1）
│   └── remote/                # 手机 H5 遥控页面
│       ├── index.html
│       ├── style.css
│       └── remote.js
├── java/com/example/tvdouyin/
│   ├── MainActivity.kt        # 主界面 + 遥控器按键处理
│   ├── WebViewSetup.kt        # WebView 配置 + JS 注入
│   ├── CursorOverlayView.kt   # 虚拟鼠标光标
│   ├── server/
│   │   ├── RemoteControlServer.kt  # HTTP 服务器
│   │   └── WebSocketHandler.kt     # WebSocket 服务器
│   └── util/
│       ├── NetworkUtils.kt    # WiFi IP 获取
│       └── QRCodeGenerator.kt # 二维码生成
└── res/
    ├── layout/activity_main.xml
    ├── drawable/bg_qr_container.xml
    └── values/
```

## ⚠️ 注意事项

- 本应用仅供个人学习和研究使用
- 视频内容来自抖音网页版，本应用不存储任何视频内容
- 首次使用需要在电视上用手机抖音扫码登录
