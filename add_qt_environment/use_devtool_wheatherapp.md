# devtool Workflow - Adding Qt Weather App Recipe

## Table of Contents

- [Overview](#overview)
- [devtool Workflow Diagram](#devtool-workflow-diagram)
- [Step 1: devtool add](#step-1-devtool-add)
- [Step 2: Inspect Generated Recipe](#step-2-inspect-generated-recipe)
- [Step 3: Fix Source Path (bbappend)](#step-3-fix-source-path-bbappend)
- [Step 4: Modify the Recipe](#step-4-modify-the-recipe)
- [Step 5: devtool build](#step-5-devtool-build)
- [Step 6: Add Config Files](#step-6-add-config-files)
- [Step 7: devtool finish](#step-7-devtool-finish)
- [Step 8: Add to Image](#step-8-add-to-image)
- [Understanding Key Concepts](#understanding-key-concepts)

---

## Overview

This guide documents how we used `devtool` to add a Qt6 Weather App
from GitHub into our Yocto build.

```
  GitHub Repo: https://github.com/ayman4105/Qt
  App Location: Qt/WeatherApp/weatherapp/
  Target: Raspberry Pi 5
  Build System: CMake + Qt6
```

---

## devtool Workflow Diagram

```
  The complete flow:
  ══════════════════
  
  ┌──────────────────┐
  │  1. devtool add  │  ← clone repo + create recipe
  └────────┬─────────┘
           │
  ┌────────▼─────────┐
  │  2. inspect      │  ← check what devtool generated
  └────────┬─────────┘
           │
  ┌────────▼─────────┐
  │  3. fix paths    │  ← fix bbappend for subfolder
  └────────┬─────────┘
           │
  ┌────────▼─────────┐
  │  4. modify recipe│  ← add inherit qt6-cmake, DEPENDS
  └────────┬─────────┘
           │
  ┌────────▼──────────┐
  │  5. devtool build │  ← compile and test
  └────────┬──────────┘
           │
  ┌────────▼──────────┐
  │  6. add configs   │  ← service file, env file
  └────────┬──────────┘
           │
  ┌────────▼───────────┐
  │  7. devtool finish │  ← move to meta-test (permanent)
  └────────┬───────────┘
           │
  ┌────────▼──────────┐
  │  8. bitbake image │  ← build final image
  └───────────────────┘
```

---

## Step 1: devtool add

```bash
devtool add qt-weather-app https://github.com/ayman4105/Qt.git --srcrev main
```

```
  devtool add does 3 things:
  ══════════════════════════
  
  1️⃣  git clone
      Downloads the code from GitHub
      ──► workspace/sources/qt-weather-app/
  
  2️⃣  creates recipe automatically
      Tries to detect build system (cmake? make? meson?)
      ──► workspace/recipes/qt-weather-app/qt-weather-app_git.bb
  
  3️⃣  creates bbappend
      Links recipe to local source code
      ──► workspace/appends/qt-weather-app_git.bbappend
```

```
  GitHub                    workspace/
  ┌──────────┐    clone     ├── sources/qt-weather-app/         ← code
  │ Qt repo  │ ──────────► ├── recipes/.../qt-weather-app_git.bb ← recipe
  └──────────┘              └── appends/.../qt-weather-app_git.bbappend ← link
```

```
  Without devtool:
  ════════════════
  1. Write recipe manually
  2. Set SRC_URI manually
  3. Write do_configure/compile/install manually
  4. bitbake qt-weather-app
  
  With devtool:
  ═════════════
  1. devtool add ← one command!
  2. Modify recipe if needed
  3. devtool build ← one command!
  
  Much faster! 🚀
```

---

## Step 2: Inspect Generated Recipe

### The auto-generated recipe

```bash
cat workspace/recipes/qt-weather-app/qt-weather-app_git.bb
```

```bash
LICENSE = "CLOSED"
SRC_URI = "git://github.com/ayman4105/Qt.git;protocol=https;branch=main"
SRCREV = "main"
S = "${WORKDIR}/git"

# NOTE: no Makefile found, unable to determine what needs to be done

do_configure () {
    :    # empty!
}

do_compile () {
    :    # empty!
}

do_install () {
    :    # empty!
}
```

```
  Problem: devtool couldn't detect the build system!
  ═══════════════════════════════════════════════════
  
  Why? Because CMakeLists.txt is NOT in the repo root:
  
  Qt/                              ← repo root (no CMakeLists.txt!)
  ├── LED_RPI/
  ├── Task1_gallery/
  ├── task2_calc/
  ├── task3_NetworkManager/
  └── WeatherApp/
      └── weatherapp/
          └── CMakeLists.txt       ← it's here!
```

### The auto-generated bbappend

```bash
cat workspace/appends/qt-weather-app_git.bbappend
```

```bash
inherit externalsrc
EXTERNALSRC = "/path/to/workspace/sources/qt-weather-app"
EXTERNALSRC_BUILD = "/path/to/workspace/sources/qt-weather-app"
```

```
  What is externalsrc?
  ════════════════════
  
  Normal build:
  Recipe says: "download from GitHub"
  SRC_URI = "git://github.com/..."
  
  With externalsrc:
  bbappend says: "DON'T download! Use local files instead"
  EXTERNALSRC = "/local/path/..."
  
  This is how devtool works:
  - Downloads once
  - Builds from local copy
  - You can edit files and rebuild quickly
```

---

## Step 3: Fix Source Path (bbappend)

```
  Problem:
  ════════
  EXTERNALSRC points to repo root
  But CMakeLists.txt is in WeatherApp/weatherapp/
  
  EXTERNALSRC ──► qt-weather-app/
                  ├── LED_RPI/
                  ├── WeatherApp/
                  └── ...
                  ❌ No CMakeLists.txt here!
```

### Fix the bbappend

```bash
cat > workspace/appends/qt-weather-app_git.bbappend << 'EOF'
inherit externalsrc
EXTERNALSRC = "/path/to/workspace/sources/qt-weather-app/WeatherApp/weatherapp"
EXTERNALSRC_BUILD = "/path/to/workspace/sources/qt-weather-app/WeatherApp/weatherapp"
EOF
```

```
  After fix:
  ══════════
  EXTERNALSRC ──► qt-weather-app/WeatherApp/weatherapp/
                  ├── CMakeLists.txt  ✅
                  ├── main.cpp        ✅
                  ├── Main.qml        ✅
                  ├── WeatherAPI.cpp  ✅
                  └── WeatherAPI.h    ✅
```

---

## Step 4: Modify the Recipe

We read CMakeLists.txt to understand what the app needs:

```
  CMakeLists.txt says:
  ═════════════════════
  
  find_package(Qt6 REQUIRED COMPONENTS Quick)    → needs qtdeclarative
  find_package(Qt6 REQUIRED COMPONENTS Network)  → needs qtbase
  qt_add_qml_module(...)                         → needs qtdeclarative-native
  qt_add_executable(appweatherapp ...)           → binary name = appweatherapp
  install(TARGETS appweatherapp RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR})
                                                 → installs to /usr/bin/
```

### Updated recipe

```bash
SUMMARY = "Weather App - Qt6 QML Application"
LICENSE = "CLOSED"
LIC_FILES_CHKSUM = ""

SRC_URI = "git://github.com/ayman4105/Qt.git;protocol=https;branch=main"

PV = "1.0+git"
SRCREV = "main"

S = "${WORKDIR}/git/WeatherApp/weatherapp"

inherit cmake qt6-cmake

DEPENDS = " \
    qtbase \
    qtdeclarative \
    qtdeclarative-native \
"

FILES:${PN} = "${bindir}/appweatherapp"
```

```
  Key lines explained:
  ═════════════════════
  
  S = ".../git/WeatherApp/weatherapp"
  ───────────────────────────────────
  Tells Yocto: "source code is in this subfolder,
  that's where CMakeLists.txt is"
  
  
  inherit cmake qt6-cmake
  ───────────────────────
  cmake     → use cmake to build
  qt6-cmake → configure cmake to find Qt6
  Without qt6-cmake: cmake can't find Qt6! ❌
  
  
  DEPENDS
  ───────
  qtbase              → Qt Core + GUI + Network
  qtdeclarative       → Qt Quick (QML engine)
  qtdeclarative-native → build tools for qt_add_qml_module
  
  ⚠️ DEPENDS = "need these to BUILD"
     NOT "install these on the image"
     Image packages go in IMAGE_INSTALL
  
  
  FILES:${PN} = "${bindir}/appweatherapp"
  ────────────────────────────────────────
  Tells Yocto: "this package contains one file:
  /usr/bin/appweatherapp"
  
  Name "appweatherapp" comes from CMakeLists.txt:
  qt_add_executable(appweatherapp ...)
```

---

## Step 5: devtool build

```bash
devtool build qt-weather-app
```

```
  What happens during build:
  ══════════════════════════
  
  1. do_configure
     ├── cmake reads CMakeLists.txt
     ├── finds Qt6 (thanks to qt6-cmake)
     └── generates Makefile
  
  2. do_compile
     ├── compiles main.cpp
     ├── compiles WeatherAPI.cpp
     ├── compiles Main.qml (QML cache)
     └── produces binary: appweatherapp
  
  3. do_install
     └── copies appweatherapp → /usr/bin/
  
  4. do_package
     └── puts binary in package
```

---

## Step 6: Add Config Files

We added two files to the source tree:

### weatherapp.service

```ini
[Unit]
Description=Weather App Qt6 (30 seconds demo)
After=weston.service
Requires=weston.service

[Service]
Type=simple
Environment=WAYLAND_DISPLAY=wayland-1
Environment=XDG_RUNTIME_DIR=/run/user/0
ExecStartPre=/bin/sleep 3
ExecStart=/bin/sh -c '/usr/bin/appweatherapp & sleep 30 && kill $!'
Restart=no

[Install]
WantedBy=multi-user.target
```

### wayland-env.sh

```bash
export WAYLAND_DISPLAY=wayland-1
export XDG_RUNTIME_DIR=/run/user/0
```

### Then committed them

```bash
cd workspace/sources/qt-weather-app
git add WeatherApp/weatherapp/weatherapp.service
git add WeatherApp/weatherapp/wayland-env.sh
git commit -m "Add weatherapp service and wayland env"
```

---

## Step 7: devtool finish

```bash
devtool finish -f qt-weather-app meta-test
```

```
  What devtool finish does:
  ═════════════════════════
  
  1. Detects our new files (service + env)
  2. Creates a patch automatically:
     0001-Add-weatherapp-service-and-wayland-env.patch
  3. Moves recipe to meta-test/
  4. Removes from workspace/
  
  
  workspace/ (temporary)           meta-test/ (permanent)
  ┌────────────────┐   finish -f   ┌──────────────────────────┐
  │ qt-weather-app │ ────────────► │ recipes-qt-weather-app/  │
  │ _git.bb        │               │   qt-weather-app/        │
  └────────────────┘               │     qt-weather-app_git.bb│
       ❌ deleted                   │     0001-Add-...patch    │
                                   └──────────────────────────┘
                                        ✅ permanent
```

```
  What is a patch?
  ════════════════
  
  Original repo on GitHub:
  ┌─────────────────────────┐
  │  main.cpp               │
  │  Main.qml               │  ← no service or env!
  │  WeatherAPI.cpp         │
  │  CMakeLists.txt         │
  └─────────────────────────┘
  
  Patch says:
  "After downloading from GitHub, ADD these files"
  
  ┌─────────────────────────┐
  │  main.cpp               │
  │  Main.qml               │
  │  WeatherAPI.cpp         │
  │  CMakeLists.txt         │
  │  weatherapp.service  ✨  │  ← patch adds these
  │  wayland-env.sh      ✨  │
  └─────────────────────────┘
```

```
  Why -f (force)?
  ═══════════════
  
  devtool said: "Source tree is not clean!"
  
  Because cmake build files exist:
  .ninja_deps, CMakeCache.txt, CMakeFiles/ ...
  
  These are build artifacts, not real changes.
  -f tells devtool: "I know, ignore them"
```

---

## Step 8: Add to Image

```bash
# In hyper-nova-cockpit-image.bb add:
IMAGE_INSTALL:append = " qt-weather-app"
```

```bash
# Build the image
bitbake hyper-nova-cockpit-image
```

---

## Understanding Key Concepts

### What is bbappend?

```
  Imagine a recipe (pizza recipe 🍕):
  ═══════════════════════════════════
  
  pizza.bb (original recipe):
  "Make dough + sauce + cheese"
  
  Want to add peppers but NOT modify the original?
  
  pizza.bbappend:
  "Add green peppers 🌶️"
  
  Result: original pizza + peppers
  Without touching the original recipe! ✅
```

```
  Real example:
  ═════════════
  
  meta-qt6/recipes-qt/qtbase_git.bb    ← not yours!
  
  If you modify it directly:
  ❌ git pull will overwrite your changes
  ❌ not professional
  ❌ might break things
  
  Solution: bbappend
  meta-test/recipes-qt/qtbase_git.bbappend  ← yours! ✅
  Adds or changes without touching the original
```

```
  devtool's bbappend:
  ═══════════════════
  
  Original recipe says:
  "Download code from GitHub"
  
  bbappend says:
  "NO! Don't download from GitHub
   Use local files from this path instead"
  
  ┌──────────────────────────────────┐
  │  Recipe (original):              │
  │  SRC_URI = "git://github.com/"  │
  │                                  │
  │  bbappend (override):            │
  │  inherit externalsrc             │
  │  EXTERNALSRC = "/local/path/"   │
  │                                  │
  │  Result: builds from local ✅    │
  └──────────────────────────────────┘
```

### ${S} vs ${WORKDIR}

```
  ${WORKDIR}/                    ← main working directory
  ├── git/                       ← ${S} (code from GitHub)
  │   └── WeatherApp/
  │       └── weatherapp/
  │           ├── main.cpp
  │           └── CMakeLists.txt
  ├── weatherapp.service         ← file:// lands here
  └── wayland-env.sh             ← file:// lands here
  
  ${S} = source code only
  ${WORKDIR} = everything (code + local files)
```
