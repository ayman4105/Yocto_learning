# Debugging Errors & Solutions - Qt Weather App Recipe

## Table of Contents

- [Error 1: CMakeLists.txt Not Found](#error-1-cmakeliststxt-not-found)
- [Error 2: Source Tree Not Clean](#error-2-source-tree-not-clean)
- [Error 3: cannot stat weatherapp.service](#error-3-cannot-stat-weatherappservice)
- [Error 4: Unable to get checksum for wayland-env.sh](#error-4-unable-to-get-checksum-for-wayland-envsh)
- [Error Summary Table](#error-summary-table)
- [Lesson Learned](#lesson-learned)

---

## Error 1: CMakeLists.txt Not Found

### The Error

```
CMake Error: The source directory "/path/to/workspace/sources/qt-weather-app"
does not appear to contain CMakeLists.txt.
```

### Why It Happened

```
  devtool created bbappend pointing to repo root:
  ═══════════════════════════════════════════════
  
  EXTERNALSRC = ".../workspace/sources/qt-weather-app"
  
  But the repo structure is:
  
  qt-weather-app/                    ← EXTERNALSRC points here
  ├── LED_RPI/                       ❌ no CMakeLists.txt!
  ├── Task1_gallery/
  ├── task2_calc/
  ├── task3_NetworkManager/
  └── WeatherApp/
      └── weatherapp/
          └── CMakeLists.txt         ← it's here!
  
  
  CMake looks for CMakeLists.txt in EXTERNALSRC root
  Can't find it → ERROR!
```

### The Fix

Edit the bbappend to point to the correct subfolder:

```bash
# Before (wrong ❌):
EXTERNALSRC = ".../sources/qt-weather-app"

# After (correct ✅):
EXTERNALSRC = ".../sources/qt-weather-app/WeatherApp/weatherapp"
EXTERNALSRC_BUILD = ".../sources/qt-weather-app/WeatherApp/weatherapp"
```

```
  Before:
  ═══════
  EXTERNALSRC ──► qt-weather-app/
                  ├── LED_RPI/
                  ├── WeatherApp/
                  └── ...
                  ❌ no CMakeLists.txt
  
  After:
  ══════
  EXTERNALSRC ──► qt-weather-app/WeatherApp/weatherapp/
                  ├── CMakeLists.txt  ✅
                  ├── main.cpp        ✅
                  ├── Main.qml        ✅
                  ├── WeatherAPI.cpp  ✅
                  └── WeatherAPI.h    ✅
```

---

## Error 2: Source Tree Not Clean

### The Error

```
ERROR: Source tree is not clean:

?? WeatherApp/weatherapp/.ninja_deps
?? WeatherApp/weatherapp/.ninja_log
?? WeatherApp/weatherapp/CMakeCache.txt
?? WeatherApp/weatherapp/CMakeFiles/
?? WeatherApp/weatherapp/appweatherapp
...
Ensure you have committed your changes or use -f/--force
```

### Why It Happened

```
  devtool finish checks if git is clean before moving the recipe.
  
  After devtool build, cmake created build artifacts:
  ═══════════════════════════════════════════════════
  
  .ninja_deps            ← build system file
  .ninja_log             ← build log
  CMakeCache.txt         ← cmake cache
  CMakeFiles/            ← cmake temp files
  appweatherapp          ← the compiled binary!
  
  These are NOT source code changes.
  They are build outputs.
  
  But devtool sees them as "uncommitted files"
  and refuses to finish.
```

### The Fix

Two options:

```
  Option 1: Force (quick ✅):
  ═══════════════════════════
  devtool finish -f qt-weather-app meta-test
  
  -f = "I know there are untracked files, ignore them"
  
  
  Option 2: Commit first (proper):
  ════════════════════════════════
  cd workspace/sources/qt-weather-app
  git add <files you want>
  git commit -m "message"
  devtool finish qt-weather-app meta-test
  
  But build artifacts should NOT be committed!
  So -f is actually the right choice here.
```

---

## Error 3: cannot stat weatherapp.service

### The Error

```
install: cannot stat '/path/to/tmp-rpi5/work/.../git/WeatherApp/
weatherapp/weatherapp.service': No such file or directory
```

### Why It Happened

```
  The recipe used ${S} to find weatherapp.service:
  ═════════════════════════════════════════════════
  
  do_install:append() {
      install -m 0644 ${S}/weatherapp.service ...
                      ────
                      ${S} = ${WORKDIR}/git/WeatherApp/weatherapp
  }
  
  
  In devtool (EXTERNALSRC):
  ═════════════════════════
  ${S} = workspace/sources/.../WeatherApp/weatherapp/
  Files were there because we created them! ✅
  
  
  After finish (normal build from GitHub):
  ════════════════════════════════════════
  ${S} = ${WORKDIR}/git/WeatherApp/weatherapp/
  
  Yocto downloads repo → applies patch
  But the patch might not have worked correctly
  OR the files ended up in ${WORKDIR} not ${S}
  
  
  The REAL problem:
  ═════════════════
  
  ${WORKDIR}/
  ├── git/                           ← ${S}
  │   └── WeatherApp/
  │       └── weatherapp/
  │           ├── main.cpp           ← from GitHub
  │           └── weatherapp.service ← patch SHOULD put it here
  │                                     but might fail!
  │
  ├── weatherapp.service             ← file:// puts it HERE
  └── wayland-env.sh                 ← file:// puts it HERE
  
  Using ${S} is fragile (depends on patch working)
  Using ${WORKDIR} is reliable (file:// always works)
```

### The Fix

1. Move files from source tree to `files/` directory
2. Use `file://` in SRC_URI
3. Use `${WORKDIR}` instead of `${S}`

```bash
# Create files directory
mkdir -p meta-test/recipes-qt-weather-app/qt-weather-app/files/

# Put files there
# files/weatherapp.service
# files/wayland-env.sh
```

```bash
# Recipe changes:

# SRC_URI - add file:// entries:
SRC_URI = " \
    git://github.com/ayman4105/Qt.git;protocol=https;branch=main \
    file://weatherapp.service \
    file://wayland-env.sh \
"

# do_install - use ${WORKDIR} not ${S}:
do_install:append() {
    install -m 0644 ${WORKDIR}/weatherapp.service ...
                    ──────────
                    ${WORKDIR} not ${S}!
}
```

```
  Before (fragile ❌):
  ════════════════════
  install -m 0644 ${S}/weatherapp.service
                  ─────
                  ${S} = source directory
                  depends on patch working correctly
  
  After (reliable ✅):
  ════════════════════
  install -m 0644 ${WORKDIR}/weatherapp.service
                  ──────────
                  ${WORKDIR} = working directory
                  file:// ALWAYS puts files here
```

```
  Where file:// looks for files:
  ══════════════════════════════
  
  meta-test/recipes-qt-weather-app/qt-weather-app/
  ├── qt-weather-app_git.bb          ← recipe
  └── files/                         ← file:// looks here!
      ├── weatherapp.service  ✅
      └── wayland-env.sh      ✅
```

---

## Error 4: Unable to get checksum for wayland-env.sh

### The Error

```
ERROR: Unable to get checksum for qt-weather-app SRC_URI entry
wayland-env.sh: file could not be found

The following paths were searched:
.../files/Hyper-NOVA-Cockpit/wayland-env.sh
.../files/raspberrypi5/wayland-env.sh
.../files/aarch64/wayland-env.sh
.../files/wayland-env.sh
```

### Why It Happened

```
  The recipe says: file://wayland-env.sh
  But the file didn't exist in files/ directory!
  
  files/
  └── weatherapp.service  ← exists ✅
                           ← wayland-env.sh MISSING! ❌
  
  We created weatherapp.service but FORGOT wayland-env.sh!
```

```
  Yocto searches for file:// in these paths (in order):
  ═════════════════════════════════════════════════════
  
  1. files/${DISTRO}/wayland-env.sh      (distro-specific)
  2. files/${MACHINE}/wayland-env.sh     (machine-specific)
  3. files/${ARCH}/wayland-env.sh        (arch-specific)
  4. files/wayland-env.sh               (generic) ← usually here
  
  None of them found it → ERROR!
```

### The Fix

Simply create the missing file:

```bash
cat > meta-test/recipes-qt-weather-app/qt-weather-app/files/wayland-env.sh << 'EOF'
export WAYLAND_DISPLAY=wayland-1
export XDG_RUNTIME_DIR=/run/user/0
EOF
```

```
  After fix:
  ══════════
  files/
  ├── weatherapp.service  ✅
  └── wayland-env.sh      ✅  ← now exists!
  
  Build succeeds! 🎉
```

---

## Error Summary Table

```
┌────┬──────────────────────────────┬────────────────────────────┬──────────────────────────────────┐
│ #  │ Error                        │ Root Cause                 │ Fix                              │
├────┼──────────────────────────────┼────────────────────────────┼──────────────────────────────────┤
│ 1  │ CMakeLists.txt not found     │ EXTERNALSRC points to      │ Change EXTERNALSRC to point      │
│    │                              │ repo root, but CMake is    │ to WeatherApp/weatherapp/        │
│    │                              │ in subfolder               │                                  │
├────┼──────────────────────────────┼────────────────────────────┼──────────────────────────────────┤
│ 2  │ Source tree not clean        │ cmake build artifacts      │ Use devtool finish -f            │
│    │                              │ seen as uncommitted files  │ (force, ignore build files)      │
├────┼──────────────────────────────┼────────────────────────────┼──────────────────────────────────┤
│ 3  │ cannot stat                  │ Recipe uses ${S} but file  │ Use file:// in SRC_URI           │
│    │ weatherapp.service           │ is not in source tree      │ Use ${WORKDIR} in do_install     │
│    │                              │ after normal build         │                                  │
├────┼──────────────────────────────┼────────────────────────────┼──────────────────────────────────┤
│ 4  │ Unable to get checksum       │ File declared in SRC_URI   │ Create the missing file          │
│    │ for wayland-env.sh           │ but doesn't exist in       │ in files/ directory              │
│    │                              │ files/ directory           │                                  │
└────┴──────────────────────────────┴────────────────────────────┴──────────────────────────────────┘
```

---

## Lesson Learned

```
  Key takeaways:
  ══════════════
  
  1️⃣  devtool is great for getting started quickly
      but the auto-generated recipe often needs manual fixes
  
  2️⃣  When repo has subfolders, you MUST fix the source path
      (EXTERNALSRC in bbappend, or S in recipe)
  
  3️⃣  Config files (service, env) are better as file:// in SRC_URI
      than inside the source tree
      ${WORKDIR} is more reliable than ${S}
  
  4️⃣  devtool finish -f is safe when untracked files are
      just build artifacts
  
  5️⃣  Always check that ALL files in SRC_URI actually exist
      in the files/ directory
  
  
  Final recipe structure:
  ═══════════════════════
  
  meta-test/recipes-qt-weather-app/qt-weather-app/
  ├── qt-weather-app_git.bb              ← recipe
  ├── files/                             ← config files
  │   ├── weatherapp.service             ← systemd service
  │   └── wayland-env.sh                 ← env variables
  └── qt-weather-app/
      └── 0001-Add-...patch              ← (from devtool, optional)
```
