# Yocto Build Performance & Troubleshooting Guide

## Table of Contents

- [System Resources Optimization](#system-resources-optimization)
- [Adding Swap Space](#adding-swap-space)
- [Build Performance Tuning](#build-performance-tuning)
- [Fixing vsomeip Compilation Error (GCC 13)](#fixing-vsomeip-compilation-error-gcc-13)
- [Fixing installed-vs-shipped Error](#fixing-installed-vs-shipped-error)
- [devtool Quick Reference](#devtool-quick-reference)

---

## System Resources Optimization

### Check Your System Resources

```bash
# check number of CPU cores
nproc

# check RAM and swap
free -h
```

Example output:

```
               total        used        free      shared  buff/cache   available
Mem:            15Gi       9.8Gi       488Mi       2.3Gi       7.4Gi       5.6Gi
Swap:             0B          0B          0B
```

### Problem: No Swap + High Thread Count = System Freeze

```
BB_NUMBER_THREADS = "8"    ──►  8 recipes building AT THE SAME TIME
PARALLEL_MAKE = "-j 8"    ──►  each recipe uses 8 CPU cores to compile

Worst case = 8 x 8 = 64 parallel compile processes!

With only 5.6GB available RAM and NO swap:
──► System FREEZES completely
──► Cannot use laptop during build
```

---

## Adding Swap Space

### Create 8GB Swap File

```bash
# create 8GB swap file
sudo fallocate -l 8G /swapfile

# set correct permissions
sudo chmod 600 /swapfile

# format as swap
sudo mkswap /swapfile

# enable swap NOW
sudo swapon /swapfile

# verify swap is active
free -h
```

Expected output after enabling swap:

```
               total        used        free      shared  buff/cache   available
Mem:            15Gi       9.8Gi       488Mi       2.3Gi       7.4Gi       5.6Gi
Swap:          8.0Gi          0B       8.0Gi       ◄── SWAP IS NOW ACTIVE!
```

### Make Swap Permanent (survive reboot)

```bash
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

---

## Build Performance Tuning

### Recommended Settings in local.conf

```bash
nano ~/ITI/fady/Yocto/build-rpi/conf/local.conf
```

### Thread Count Reference Table

```
┌────────────────┬────────────────────┬──────────────────┬──────────────────────┐
│ System Specs   │ BB_NUMBER_THREADS  │ PARALLEL_MAKE    │ Result               │
├────────────────┼────────────────────┼──────────────────┼──────────────────────┤
│ 4 cores, 8GB   │ "2"                │ "-j 2"           │ safe, slow           │
│ 8 cores, 8GB   │ "4"                │ "-j 4"           │ safe, medium         │
│ 8 cores, 16GB  │ "6"                │ "-j 6"           │ balanced ✅          │
│ 12 cores, 32GB │ "8"                │ "-j 8"           │ fast                 │
│ 16 cores, 32GB │ "12"               │ "-j 12"          │ very fast            │
└────────────────┴────────────────────┴──────────────────┴──────────────────────┘
```

### For 8 cores + 16GB RAM + 8GB Swap (Recommended)

```bash
BB_NUMBER_THREADS = "6"
PARALLEL_MAKE = "-j 6"
```

### Build Priority Options

```bash
# Option 1: Full speed (laptop might lag)
bitbake ayman-image

# Option 2: Medium priority (balanced - RECOMMENDED)
nice -n 10 bitbake ayman-image

# Option 3: Lowest priority (laptop smooth but build VERY slow)
ionice -c 3 nice -n 19 bitbake ayman-image
```

```
nice values:
════════════
nice -n 0    ──►  normal priority (default)
nice -n 10   ──►  medium-low priority (balanced) ✅
nice -n 19   ──►  lowest priority (very slow build) 🐢
```

---

## Fixing vsomeip Compilation Error (GCC 13)

### The Error

```
include/CommonAPI/Types.hpp:113:40: error:
    return type 'std::string' is incomplete

include/CommonAPI/Runtime.hpp:172:17: error:
    field 'usedConfig_' has incomplete type 'std::string'
```

### Root Cause

```
GCC 13 (used in Yocto scarthgap) removed many "transitive includes".

Old GCC (9, 10, 11):
  #include <memory>
      └── secretly includes <string>     ──►  std::string works by luck ✅

New GCC 13:
  #include <memory>
      └── does NOT include <string>      ──►  std::string is incomplete ❌

The source code uses std::string but never explicitly does #include <string>.
```

### How to View the Full Error Log

```bash
# show last 50 lines of compile log
cat /home/ayman/ITI/fady/Yocto/share/tmp/work/cortexa53-poky-linux/vsomeip/1.0+git/temp/log.do_compile | tail -50

# show only error lines
cat /home/ayman/ITI/fady/Yocto/share/tmp/work/cortexa53-poky-linux/vsomeip/1.0+git/temp/log.do_compile | grep -i "error:" | head -20
```

### Fix Option A: Manual Edit

#### Edit Types.hpp

```bash
nano include/CommonAPI/Types.hpp
```

Add `#include <string>` at the top:

```cpp
#ifndef COMMONAPI_TYPES_HPP_
#include <string>                // ◄── ADD THIS LINE
#define COMMONAPI_TYPES_HPP_
```

#### Edit Runtime.hpp

```bash
nano include/CommonAPI/Runtime.hpp
```

Add `#include <string>` after `#include <memory>`:

```cpp
#include <memory>
#include <string>                // ◄── ADD THIS LINE
```

### Fix Option B: One-liner Using sed

```bash
cd ~/ITI/fady/Yocto/build-rpi/workspace/sources/vsomeip

# fix Types.hpp
sed -i '/#ifndef COMMONAPI_TYPES_HPP_/a #include <string>' include/CommonAPI/Types.hpp

# fix Runtime.hpp
sed -i '/#include <memory>/a #include <string>' include/CommonAPI/Runtime.hpp
```

#### What sed commands do:

```
sed -i '/PATTERN/a NEW_TEXT' filename
 │   │   │        │  │        │
 │   │   │        │  │        └── file to edit
 │   │   │        │  └── text to add after found line
 │   │   │        └── a = append (add AFTER found line)
 │   │   └── search for this pattern
 │   └── -i = edit file in-place (save directly)
 └── sed = stream editor
```

### Fix Option C: Proper Yocto Patch (Recommended for permanent fix)

```bash
# 1. apply fixes first (Option A or B above)
cd ~/ITI/fady/Yocto/build-rpi/workspace/sources/vsomeip

# 2. create patch from changes
git diff > fix-missing-string-include.patch

# 3. place patch in recipe
mkdir -p ~/ITI/fady/Yocto/build-rpi/workspace/recipes/vsomeip/vsomeip/
cp fix-missing-string-include.patch \
   ~/ITI/fady/Yocto/build-rpi/workspace/recipes/vsomeip/vsomeip/

# 4. add patch to recipe
nano ~/ITI/fady/Yocto/build-rpi/workspace/recipes/vsomeip/vsomeip_git.bb
# add this line:
# SRC_URI:append = " file://fix-missing-string-include.patch"

# 5. rebuild
bitbake vsomeip -c cleansstate
bitbake vsomeip
```

### Rebuild After Fix

```bash
# force recompile
bitbake vsomeip -c compile -f

# or full clean rebuild
bitbake vsomeip -c cleansstate
bitbake vsomeip
```

---

## Fixing installed-vs-shipped Error

### The Error

```
ERROR: vsomeip0: Files/directories were installed but not shipped in any package:
  /usr/bin
  /usr/etc
  /usr/etc/vsomeip
  /usr/etc/vsomeip/vsomeip-tcp-client.json
  /usr/etc/vsomeip/vsomeip-udp-client.json
  /usr/etc/vsomeip/vsomeip-tcp-service.json
  /usr/etc/vsomeip/vsomeip-local.json
  /usr/etc/vsomeip/vsomeip.json
  /usr/etc/vsomeip/vsomeip-udp-service.json
```

### Root Cause

```
do_install puts files in:           FILES variable says to package:
═════════════════════════           ════════════════════════════════
/usr/bin/myapp                      (nothing about /usr/bin)     ❌
/usr/etc/vsomeip/*.json             (nothing about /usr/etc)     ❌

Yocto: "Files were installed but I don't know which package they belong to!"
```

### The Fix

Edit the recipe:

```bash
nano ~/ITI/fady/Yocto/build-rpi/workspace/recipes/vsomeip0/vsomeip0_git.bb
```

Add FILES variable:

```bash
FILES:${PN} += " \
    /usr/bin \
    /usr/etc \
"
```

> ⚠️ **Important:** Use `/usr/bin` NOT `/usr/bin/*`

```
/usr/bin/*    ──►  matches files INSIDE the folder only
                   Yocto: "who owns the /usr/bin DIRECTORY itself?" ❌

/usr/bin      ──►  matches the folder ITSELF + everything inside
                   Yocto: "I know about /usr/bin and its contents" ✅
```

### Rebuild

```bash
# rerun only the package task
bitbake vsomeip0 -c package -f

# then full build
bitbake vsomeip0
```

---

## devtool Quick Reference

### What is devtool?

```
devtool is a command-line tool that helps you:
- Add new recipes automatically
- Modify existing recipes easily
- Test changes quickly
- Create patches automatically
- All without manually editing .bb files!
```

### Common Commands

```bash
# add a NEW recipe from source URL
devtool add <recipe-name> <source-url>

# modify an EXISTING recipe (downloads source to workspace)
devtool modify <recipe-name>

# build the recipe using workspace source
devtool build <recipe-name>

# save changes as patches to a target layer (PERMANENT)
devtool finish <recipe-name> <target-layer>

# cancel modifications, go back to original
devtool reset <recipe-name>

# show all recipes currently in workspace
devtool status
```

### devtool Workflow

```
devtool modify vsomeip
     │
     ▼
workspace/sources/vsomeip/     ◄── source code appears here
     │
     ▼
edit code, fix bugs
     │
     ▼
git add -A && git commit -m "fix: description"
     │
     ▼
devtool build vsomeip          ◄── test the build
     │
     ▼
devtool finish vsomeip meta-test   ◄── auto-creates patches!
```

### Workspace Structure

```
build-rpi/workspace/
├── conf/
│   └── layer.conf            ◄── workspace is a layer with HIGHEST priority
├── recipes/
│   └── vsomeip/
│       └── vsomeip_git.bb    ◄── modified recipe
├── appends/
│   └── vsomeip_git.bbappend  ◄── redirects source to workspace
├── sources/
│   └── vsomeip/              ◄── actual source code you edit
└── README
```

### Command Summary Table

| Command              | What it does                                    |
|----------------------|-------------------------------------------------|
| `devtool add`        | create new recipe from source URL               |
| `devtool modify`     | download existing recipe source to workspace    |
| `devtool build`      | build recipe using workspace source             |
| `devtool finish`     | save changes as patches to target layer         |
| `devtool reset`      | cancel modifications, go back to original       |
| `devtool status`     | show all recipes currently in workspace         |
| `devtool upgrade`    | upgrade recipe to newer version of source       |

---

## Quick Troubleshooting Checklist

| Problem                    | Solution                                              |
|----------------------------|-------------------------------------------------------|
| System freezes during build | Reduce `BB_NUMBER_THREADS` and `PARALLEL_MAKE`       |
| No swap space              | Create swap file: `sudo fallocate -l 8G /swapfile`   |
| Build too slow             | Use `BB_NUMBER_THREADS = "6"` and `PARALLEL_MAKE = "-j 6"` |
| `std::string` incomplete   | Add `#include <string>` to header files              |
| installed-vs-shipped error | Add `FILES:${PN} += "/usr/bin /usr/etc"` to recipe   |
| Build slow with nice       | Use `nice -n 10` instead of `nice -n 19`             |
