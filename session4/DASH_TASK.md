# Building a Custom Recipe (dash) for Raspberry Pi in Yocto

A complete guide documenting all problems encountered and how they were solved
when building the `dash` shell from a GitHub repository using Yocto Project.

---

## Table of Contents

- [1. Recipe Overview](#1-recipe-overview)
- [2. Layer Structure](#2-layer-structure)
- [3. The Recipe Explained](#3-the-recipe-explained)
- [4. Problem 1: Cross-Compilation Failure](#4-problem-1-cross-compilation-failure)
- [5. Problem 2: QA Error — File Not in Package](#5-problem-2-qa-error--file-not-in-package)
- [6. Creating the Patch](#6-creating-the-patch)
- [7. Build and Flash Commands](#7-build-and-flash-commands)

---

## 1. Recipe Overview

We built a custom recipe that:
1. Downloads `dash` source code from GitHub
2. Applies a patch to fix the Makefile for cross-compilation
3. Compiles it using Yocto's cross-compiler
4. Installs the binary to a custom path `/usr/ayman/bin/dash`
5. Packages it and includes it in the final image

---

## 2. Layer Structure

```
meta-test/
├── conf/
│   └── layer.conf
└── recipes-test/
    └── dash/
        ├── dash_1.0.bb
        └── dash/
            └── myfix.patch
```

---

## 3. The Recipe Explained

### dash_1.0.bb

```bash
SUMMARY="TEST: dash 1.0"
DESCRIPTION="This is a test recipe for dash 1.0"
```
Just a description. Does not affect the build.

```bash
LICENSE="CLOSED"
```
No open source license. Used for testing or proprietary code.
No license file check needed (no `LIC_FILES_CHKSUM`).

```bash
SRC_URI="git://github.com/danishprakash/dash.git;branch=master;protocol=https"
```
Download source code from GitHub using git.
- `branch=master` → use master branch
- `protocol=https` → clone over HTTPS

```bash
SRC_URI:append = " file://myfix.patch"
```
Also apply a patch file located at:
`meta-test/recipes-test/dash/dash/myfix.patch`

**Important:** There is a space before `file://`. Without the space:
- WRONG: `"git://...file://myfix.patch"` → broken URL
- RIGHT: `"git://... file://myfix.patch"` → two separate entries

BitBake looks for the patch file in a folder with the same name as the recipe:
```
recipes-test/dash/
├── dash_1.0.bb          ← recipe
└── dash/                ← same name as recipe
    └── myfix.patch      ← patch file goes here
```

```bash
SRCREV = "a9481f4a453f0ad25d9c9068c7b6e47253532deb"
```
Lock to this specific git commit. This ensures a reproducible build.
Every time you build, you get the exact same source code.

```bash
S = "${WORKDIR}/git"
```
After git clone, the source code is in `${WORKDIR}/git/`.
This tells BitBake where to find the source.

```bash
do_compile() {
    oe_runmake
}
```
Runs `make` with all Yocto environment variables set
(CC, CFLAGS, LDFLAGS, etc.).

```bash
do_install() {
    install -d ${D}/usr/ayman/bin
    install -m 0755 ${S}/dash ${D}/usr/ayman/bin/dash
}
```
- `install -d` → create directory `/usr/ayman/bin` inside the staging area
- `install -m 0755` → copy the `dash` binary with executable permissions (rwxr-xr-x)
- `${D}` → destination/staging directory
- `${S}` → source directory where the compiled binary lives

```bash
FILES:${PN} = "/usr/ayman/bin/*"
```
Tells BitBake to include everything under `/usr/ayman/bin/` in the package.
This is needed because `/usr/ayman/bin/` is a custom path.
See [Problem 2](#5-problem-2-qa-error--file-not-in-package) for details.

---

## 4. Problem 1: Cross-Compilation Failure

### What Happened

The original Makefile uses `cc` which is the host (PC) compiler:

```makefile
all: dash.c
	cc -Wall -o dash dash.c
```

When Yocto runs `make`, it needs to use the **cross-compiler** (e.g. `arm-poky-linux-gnueabi-gcc`)
to produce an ARM binary for the Raspberry Pi. But the Makefile hardcodes `cc` which
produces an x86 binary that cannot run on the RPi.

### The Error

```
ERROR: dash-1.0: do_compile failed
# The binary was compiled for x86, not ARM
```

### The Fix — Create a Patch

We created `myfix.patch` to replace hardcoded `cc` with Yocto variables:

```diff
diff --git a/makefile b/makefile
index 44ce757..84942c3 100644
--- a/makefile
+++ b/makefile
@@ -1,5 +1,5 @@
 all: dash.c
-	cc  -Wall -o dash  dash.c
+	${CC} ${CFLAGS} ${LDFLAGS}  -o dash  dash.c
 
 clean:
 	dash
```

What changed:

| Before | After | Why |
|--------|-------|-----|
| `cc` | `${CC}` | Use Yocto's cross-compiler instead of host compiler |
| `-Wall` | `${CFLAGS}` | Use Yocto's compiler flags (optimization, architecture, etc.) |
| (nothing) | `${LDFLAGS}` | Use Yocto's linker flags (sysroot path, etc.) |

When Yocto runs make, these variables expand to:
- `${CC}` → `arm-poky-linux-gnueabi-gcc`
- `${CFLAGS}` → `-O2 -pipe -march=armv7-a ...`
- `${LDFLAGS}` → `--sysroot=/path/to/sysroot ...`

---

## 5. Problem 2: QA Error — File Not in Package

### What Happened

After fixing the compilation, the build failed at the packaging step with a QA error:

```
ERROR: dash-1.0: do_package_qa: QA Issue:
dash: Files/directories were installed but not shipped in any package:
  /usr/ayman/bin/dash
```

### Why It Happened

BitBake has a list of **default paths** it knows about:

```
/usr/bin/*       ✅ BitBake knows
/usr/lib/*       ✅ BitBake knows
/etc/*           ✅ BitBake knows
/usr/ayman/bin/* ❌ BitBake does NOT know
```

We installed `dash` to `/usr/ayman/bin/` which is a custom path.
BitBake found the file during `do_install` but didn't know which
package should contain it.

### The Fix

We added `FILES:${PN}` to tell BitBake explicitly:

```bash
FILES:${PN} = "/usr/ayman/bin/*"
```

This means: "When packaging the `dash` recipe, include everything
under `/usr/ayman/bin/` in the main package."

`${PN}` = Package Name = `dash`

If we had installed to a standard path like `/usr/bin/`, this line
would not be needed because BitBake already knows about `/usr/bin/`.

---

## 6. Creating the Patch

### How the Patch Was Created

```bash
# Go to the source directory
cd ~/ITI/fady/Yocto/build-rpi/tmp/work/cortexa72-poky-linux/dash/1.0-r0/git

# Or use devshell
bitbake dash -c devshell

# Initialize git if needed
git init
git add -A
git commit -m "original"

# Edit the makefile
vim makefile
# Change: cc -Wall -o dash dash.c
# To:     ${CC} ${CFLAGS} ${LDFLAGS} -o dash dash.c

# Create the patch
git diff > myfix.patch

# Copy patch to recipe directory
cp myfix.patch ~/ITI/fady/Yocto/meta-test/recipes-test/dash/dash/myfix.patch
```

### Patch File Location

BitBake searches for patch files in this order:
1. `recipes-test/dash/dash-1.0/` (version specific)
2. `recipes-test/dash/dash/` (generic) ← we used this
3. `recipes-test/dash/files/` (common files)

---

## 7. Build and Flash Commands

### Setup

```bash
cd ~/ITI/fady/Yocto
source oe-init-build-env build-rpi
```

### Verify Layer

```bash
bitbake-layers show-layers
# Confirm meta-test is listed
```

### Verify local.conf

```bash
cat conf/local.conf
```

Should contain:
```bash
CONF_VERSION = "2"
IMAGE_INSTALL:append = " hello-ayman"
IMAGE_INSTALL:append = " dash"
ENABLE_UART = "1"
```

### Build

```bash
# Build full image
bitbake core-image-minimal

# Or build dash recipe only (for testing)
bitbake dash
```

### If Build Fails — Debug

```bash
# Check compile log
cat tmp/work/cortexa72-poky-linux/dash/1.0-r0/temp/log.do_compile

# Check package QA log
cat tmp/work/cortexa72-poky-linux/dash/1.0-r0/temp/log.do_package_qa

# Open devshell to debug manually
bitbake dash -c devshell

# Check environment variables
bitbake -e dash | grep "^S="
bitbake -e dash | grep "^SRC_URI="
bitbake -e dash | grep "^FILES"
```

### Clean and Rebuild

```bash
# Clean dash recipe
bitbake -c clean dash

# Full clean
bitbake -c cleanall dash

# Rebuild
bitbake dash
```

### Find Image

```bash
ls ~/ITI/fady/Yocto/share/tmp/deploy/images/raspberrypi3-64/*.wic*
```

### Flash to SD Card

```bash
# Find SD card
lsblk

# Unmount
sudo umount /dev/mmcblk0p1
sudo umount /dev/mmcblk0p2

# Flash
cd ~/ITI/fady/Yocto/share/tmp/deploy/images/raspberrypi3-64/
sudo bmaptool copy core-image-minimal-raspberrypi3-64.rootfs.wic.bz2 /dev/mmcblk0

# Safely remove
sudo sync
sudo eject /dev/mmcblk0
```

### Boot and Verify

```bash
# Connect via UART
picocom -b 115200 /dev/ttyUSB0

# Login
# user: root (no password)

# Verify dash is installed
ls /usr/ayman/bin/
# Output: dash

# Run dash
/usr/ayman/bin/dash
echo "Hello from dash!"

# Check kernel command line
cat /proc/cmdline
