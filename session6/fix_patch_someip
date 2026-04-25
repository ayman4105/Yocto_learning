
# Fixing `std::string` Incomplete Type Error in vsomeip (GCC 13 / Scarthgap)

## Problem

When building `vsomeip` with Yocto Scarthgap (GCC 13), the build fails with:

```
include/CommonAPI/Types.hpp:113:40: error:
    return type 'std::string' is incomplete

include/CommonAPI/Runtime.hpp:172:17: error:
    field 'usedConfig_' has incomplete type 'std::string'
```

## Root Cause

```
GCC 13 removed many "transitive includes" that older versions had.

Old GCC (9, 10, 11):
═════════════════════
#include <memory>
    └── secretly includes <string>     ──►  std::string works by luck ✅

New GCC 13 (scarthgap):
════════════════════════
#include <memory>
    └── does NOT include <string>      ──►  std::string is incomplete ❌

The source code uses std::string but never explicitly does #include <string>.
This worked before by luck, but GCC 13 requires explicit includes.
```

## Fix

### Option A: Manual Edit

#### 1. Edit `Types.hpp`

```bash
nano include/CommonAPI/Types.hpp
```

Find the top of the file and add `#include <string>`:

```cpp
#ifndef COMMONAPI_TYPES_HPP_
#include <string>                // ◄── ADD THIS LINE
#define COMMONAPI_TYPES_HPP_
```

#### 2. Edit `Runtime.hpp`

```bash
nano include/CommonAPI/Runtime.hpp
```

Find `#include <memory>` and add `#include <string>` after it:

```cpp
#include <memory>
#include <string>                // ◄── ADD THIS LINE
#include <map>
```

---

### Option B: One-liner Fix Using `sed`

```bash
cd ~/ITI/fady/Yocto/build-rpi/workspace/sources/vsomeip

# fix Types.hpp
sed -i '/#ifndef COMMONAPI_TYPES_HPP_/a #include <string>' include/CommonAPI/Types.hpp

# fix Runtime.hpp
sed -i '/#include <memory>/a #include <string>' include/CommonAPI/Runtime.hpp
```

#### What these commands do:

```
sed -i '/#ifndef COMMONAPI_TYPES_HPP_/a #include <string>' include/CommonAPI/Types.hpp
 │   │   │                            │  │                   │
 │   │   │                            │  │                   └── file to edit
 │   │   │                            │  └── text to add
 │   │   │                            └── a = append (add AFTER found line)
 │   │   └── find this line
 │   └── -i = edit file in-place (save directly)
 └── sed = stream editor (text editing tool)
```

```
sed -i '/#include <memory>/a #include <string>' include/CommonAPI/Runtime.hpp
 │   │   │                 │  │                   │
 │   │   │                 │  │                   └── file to edit
 │   │   │                 │  └── text to add
 │   │   │                 └── a = append (add AFTER found line)
 │   │   └── find this line
 │   └── -i = edit file in-place
 └── sed = stream editor
```

---

### Option C: Proper Yocto Patch (Recommended)

#### 1. Create the patch from your changes

```bash
cd ~/ITI/fady/Yocto/build-rpi/workspace/sources/vsomeip

# apply the sed fixes first (Option B above), then:
git diff > fix-missing-string-include.patch
```

#### 2. Place the patch in the recipe

```bash
mkdir -p ~/ITI/fady/Yocto/build-rpi/workspace/recipes/vsomeip/vsomeip/

cp fix-missing-string-include.patch \
   ~/ITI/fady/Yocto/build-rpi/workspace/recipes/vsomeip/vsomeip/
```

#### 3. Directory structure after adding patch

```
workspace/recipes/vsomeip/
├── vsomeip_git.bb
└── vsomeip/
    └── fix-missing-string-include.patch    ◄── patch file
```

#### 4. Add the patch to the recipe

```bash
nano ~/ITI/fady/Yocto/build-rpi/workspace/recipes/vsomeip/vsomeip_git.bb
```

Add this line:

```bash
SRC_URI:append = " file://fix-missing-string-include.patch"
```

#### 5. Rebuild

```bash
cd ~/ITI/fady/Yocto
source poky/oe-init-build-env build-rpi

bitbake vsomeip -c cleansstate
bitbake vsomeip
```

---

## How Yocto Applies the Patch

```
bitbake vsomeip
     │
     ▼
do_fetch      ──►  download source from git
     │
     ▼
do_unpack     ──►  extract source code
     │
     ▼
do_patch      ──►  apply fix-missing-string-include.patch    ◄── PATCH APPLIED HERE
     │              - adds #include <string> to Types.hpp
     │              - adds #include <string> to Runtime.hpp
     ▼
do_configure  ──►  run cmake
     │
     ▼
do_compile    ──►  build with g++ (NOW it works!) ✅
     │
     ▼
do_install    ──►  install to rootfs
```

---

## Quick `sed` Reference

| Command          | What it does                        |
|------------------|-------------------------------------|
| `/FIND/a TEXT`   | add TEXT **after** the found line   |
| `/FIND/i TEXT`   | add TEXT **before** the found line  |
| `/FIND/d`        | **delete** the found line           |
| `s/OLD/NEW/`     | **replace** OLD with NEW            |
| `-i`             | edit file **in-place** (save it)    |
