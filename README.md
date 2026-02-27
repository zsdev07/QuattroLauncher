<h1 align="center">QuattroLauncher</h1>

<p align="center">
  <strong>The Ultimate High-Performance Minecraft: Java Edition Launcher for Android</strong>
</p>

<div align="center">
  <img src="app_pojavlauncher/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="130" height="150" alt="QuattroLauncher logo">
</div>

---

## 📖 Introduction
**QuattroLauncher** is a specialized, community-driven fork designed to bring the complete Minecraft: Java Edition experience to the palm of your hand. By leveraging advanced wrapper technologies and a custom-tuned OpenJDK environment, QuattroLauncher allows users to run desktop-grade Minecraft on Android devices with high stability and optimized frame rates.

Unlike standard mobile versions, QuattroLauncher provides access to the true Java ecosystem, including the ability to play on major servers, use complex redstone mechanics, and experience the latest snapshots (up to 1.21+) as soon as they are released by Mojang.

---

## ✨ Key Features
* **Universal Version Support:** Play almost any version ever released, from the very first "RD" versions to the latest Experimental Snapshots.
* **Native Modding Support:** Seamlessly install and run popular mod loaders including **Forge**, **Fabric**, and **Quilt**. It also features built-in support for **OptiFine** to maximize performance on budget devices.
* **Customized Visual Identity:** Features a deep-black aesthetic with polished accents, designed for modern OLED screens and long gaming sessions.
* **Advanced Input System:** A completely rewritten virtual control system that allows for highly customizable layouts, multi-touch support, and full external gamepad/keyboard mapping.
* **Multi-Architecture Engine:** Full support for ARM32, ARM64, x86, and x86_64, ensuring compatibility across a vast range of smartphones and tablets.

---

## 🛠️ Building & Development
QuattroLauncher is built using the Gradle build system. If you wish to contribute or build your own custom version, follow the instructions below:

### Quick Start (Debug Build)
1. **Clone the Repo:**
   ```bash
   git clone [https://github.com/zsdev07/QuattroLauncher.git]

2. **Execute Build:**
   ```bash
   ./gradlew :app_pojavlauncher:assembleDebug

### Official Release 

For a production-ready APK that is optimized for speed and size, use the release task:

```./gradlew :app_pojavlauncher:assembleRelease```

The final APK will be located in: app_pojavlauncher/build/outputs/apk/release/

### 📥 Downloads & Installation

Official stable builds and experimental "bleeding-edge" updates are available in our GitHub Releases section.
1. Download the .apk file from the latest release.
2. Enable "Install from Unknown Sources" in your Android settings.
3. Install the APK and sign in with your Microsoft account to begin playing.

### 📜 Credits & License

QuattroLauncher is a fork based on the incredible foundational work of the PojavLauncher team. We owe a debt of gratitude to the original developers and the Boardwalk project.

``•License: This project is licensed under the GNU LGPLv3.``
``•Technologies: Built with OpenJDK, LWJGL 3, and GL4ES.``

<p align="center">
<strong>Maintained with passion by zdev07</strong>
</p>
