# MusicPlayer

轻量级音视频播放器，专为 Android 4.0 (API 14) 及以上设计的车载播放器。

A lightweight audio/video player designed for Android 4.0+ (API 14) car head units.

---

## 功能 Features

- **完整 ID3 标签支持** — ID3v1 + ID3v2.2 / v2.3 / v2.4，支持专辑封面提取、歌词、多编码 (ISO-8859-1 / UTF-8 / UTF-16)
- **音频播放** — 后台 Service 播放，通知栏控制，支持 MP3 / WAV / OGG / FLAC / M4A / AAC / WMA
- **视频播放** — 全屏 VideoView 播放，支持 MP4 / MKV / AVI / MOV / WMV / WebM / 3GP
- **播放列表** — 随机播放、单曲循环、列表循环
- **媒体扫描** — MediaStore 快速扫描 + 手动目录遍历
- **Material Design UI** — 绿色主题，卡片式歌曲列表，底部迷你播放条
- **极简 APK** — 零外部依赖，手写 ID3 解析器，APK 体积小
- **CI/CD** — GitHub Actions 自动构建 Debug/Release APK，Tag 推送自动发版

## 截图 Preview

```
┌─────────────────────────────┐
│  🎵  MusicPlayer         🔄 │  ← 绿色顶栏 + 刷新按钮
├─────────────────────────────┤
│ ┌───┬─────────────────────┐ │
│ │🎵│ Song Title           │ │  ← 卡片式歌曲列表
│ │   │ Artist Name    4:23 │ │     带专辑封面缩略图
│ └───┴─────────────────────┘ │
│ ┌───┬─────────────────────┐ │
│ │🎬│ Video File      12:05│ │     视频文件带 🎬 标识
│ └───┴─────────────────────┘ │
├─────────────────────────────┤
│ ████████░░░░░░  1:23 / 4:23 │  ← 迷你播放条
│ 🎵 Title - Artist  ⏮▶⏭🔀🔁│     底部常驻控制条
└─────────────────────────────┘
```

## 系统要求 Requirements

- **Android 4.0 (API 14)** 或更高
- 存储权限（用于扫描媒体文件）

## 构建 Build — 仅需 GitHub，无需本地环境

**本项目的 CI/CD 工作流会自动安装 Android SDK + Gradle，你不需要在本地安装任何东西！**

### 快速开始

1. **Fork 或 Push 本仓库到 GitHub**
2. GitHub Actions 自动运行 → 在 Actions 页面下载 APK

### 手动触发构建

1. 打开 GitHub 仓库 → **Actions** 标签
2. 选择 **Build & Release APK**
3. 点击 **Run workflow** → 等待完成
4. 下载 **app-debug** artifact → 得到 APK

### 自动发版

```bash
git tag v1.0.0
git push origin v1.0.0
```

GitHub Actions 自动构建并在 **Releases** 页面发布 APK。

### 应用签名 Signing (可选)

若要发布到应用商店，编辑 `app/build.gradle`，添加签名配置：

```groovy
android {
    signingConfigs {
        release {
            storeFile file("keystore.jks")
            storePassword "your-store-password"
            keyAlias "your-key-alias"
            keyPassword "your-key-password"
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
        }
    }
}
```

然后在 GitHub 仓库 **Settings → Secrets and variables → Actions** 中添加 keystore 文件为 Secret。

## 项目结构 Project Structure

```
MusicPlayer/
├── app/
│   ├── build.gradle                         # 应用构建配置 (minSdk 14)
│   ├── proguard-rules.pro                   # R8 混淆规则
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/codewhale/musicplayer/
│       │   ├── App.java                     # Application 入口
│       │   ├── MainActivity.java            # 主界面 (歌曲列表 + 播放条)
│       │   ├── VideoPlayerActivity.java     # 全屏视频播放
│       │   ├── id3/
│       │   │   ├── ID3Parser.java           # 统一解析入口
│       │   │   ├── ID3v1Tag.java            # ID3v1 解析 (128字节)
│       │   │   ├── ID3v2Tag.java            # ID3v2 解析 (v2.2/2.3/2.4)
│       │   │   └── ID3Frame.java            # 帧解析 + 文本/图片/歌词解码
│       │   ├── model/
│       │   │   ├── Song.java                # 歌曲数据模型 (Parcelable)
│       │   │   └── Playlist.java            # 播放列表 (随机/循环)
│       │   ├── player/
│       │   │   ├── MusicService.java        # 后台播放 Service
│       │   │   └── PlayerController.java    # 播放控制器 (SeekBar 同步)
│       │   ├── adapter/
│       │   │   └── SongAdapter.java         # RecyclerView 适配器
│       │   └── util/
│       │       ├── FileScanner.java         # 媒体扫描 (MediaStore + 目录)
│       │       └── ImageLoader.java         # 异步专辑封面加载
│       └── res/
│           ├── layout/                      # 布局 XML
│           ├── drawable/                    # 矢量图标 + Shape
│           ├── values/                      # strings, colors, styles, dimens
│           └── menu/                        # 菜单
├── build.gradle                             # 根构建配置
├── settings.gradle
├── gradle.properties
└── .github/workflows/build.yml              # CI/CD
```

## 技术细节 Technical Notes

### ID3 解析器

- 纯 Java 手写，零外部依赖
- ID3v2.2 3字符帧ID + ID3v2.3/2.4 4字符帧ID
- Synchsafe 整数解析
- 全局 + 帧级别 unsynchronisation
- 扩展头部处理
- 4种编码: ISO-8859-1, UTF-16+BOM, UTF-16BE, UTF-8
- 损坏/非标准标签容错恢复

### 播放

- Android MediaPlayer 原生解码
- 前台 Service + 通知栏
- LocalBroadcastManager 状态同步
- VideoView 全屏视频 + MediaController

### 兼容性

- 最低 API 14 (Android 4.0 Ice Cream Sandwich)
- AndroidX 库选择 API 14 兼容版本
- `appcompat:1.3.1`, `material:1.2.1` 是最新的 API 14 兼容版本
- VectorDrawable 通过 `supportLibrary = true` 回退到 PNG

## License

MIT
