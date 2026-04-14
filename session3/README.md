
# Build Error Analysis, WORKDIR Path & Patch Fix

## 1. The Build Error

When building `hello-ayman` recipe:

```bash
bitbake hello-ayman
```

The build failed at `do_compile` with this error:

```
main.c:3:1: warning: return type defaults to 'int'
    3 | intmain() {
        ^~~~~~~
undefined reference to `main'
```

### Root Cause

| Wrong Code | Correct Code |
|-----------|-------------|
| `intmain() {` | `int main() {` |

A missing **space** between `int` and `main` caused the compiler to treat
`intmain` as a function name. The linker then could not find the required
`main` function entry point.

---

## 2. WORKDIR Path

The build output was located at:

```
tmp/work/cortexa53-poky-linux/hello-ayman/1.0/
```

### Path Breakdown

```
tmp/work/ cortexa53-poky-linux / hello-ayman / 1.0
          ──────────┬─────────   ─────┬─────   ─┬─
                    │                 │          │
                    │                 │          └── PV (version)
                    │                 └── PN (package name)
                    └── TUNE_PKGARCH - DISTRO - TARGET_OS
```

### Where does `cortexa53-poky-linux` come from?

| Part | Value | Meaning | Source |
|------|-------|---------|--------|
| `cortexa53` | TUNE_PKGARCH | CPU architecture | `MACHINE → DEFAULTTUNE → TUNE_PKGARCH` |
| `poky` | DISTRO | Distribution name | `local.conf` |
| `linux` | TARGET_OS | Target operating system | Default for Linux targets |

### The chain from MACHINE to TUNE_PKGARCH

```
local.conf
  MACHINE = "raspberrypi3-64"
      │
      ▼
meta-raspberrypi/conf/machine/raspberrypi3-64.conf
  DEFAULTTUNE = "cortexa53"
      │
      ▼
poky/meta/conf/machine/include/arm/armv8a/tune-cortexa53.conf
  TUNE_PKGARCH = "cortexa53"
```

---

## 3. WORKDIR Organization (4 Levels)

Yocto organizes `tmp/work/` into 4 directories based on recipe type:

```
tmp/work/
├── raspberrypi3_64-poky-linux/   (MACHINE-specific)
├── cortexa53-poky-linux/         (TUNE/CPU level)
├── all-poky-linux/               (Architecture-independent)
└── x86_64-linux/                 (Native PC tools)
```

### What goes where?

| Directory | PACKAGE_ARCH | What goes here | Examples |
|-----------|-------------|----------------|----------|
| `raspberrypi3_64-poky-linux/` | `raspberrypi3_64` | Recipes tied to the board | `linux-raspberrypi`, `u-boot`, `rpi-config` |
| `cortexa53-poky-linux/` | `cortexa53` | Normal recipes (default) | `hello-ayman`, `busybox`, `bash`, `nano` |
| `all-poky-linux/` | `all` | No compiled binary | `base-files`, config scripts |
| `x86_64-linux/` | `x86_64` | Tools that run on host PC | `cmake-native`, `python3-native` |

---

## 4. How bitbake Decides (PACKAGE_ARCH)

bitbake reads the recipe and sets `PACKAGE_ARCH` automatically:

```
Does recipe inherit native?
├── YES → x86_64-linux/
└── NO
    Does recipe inherit allarch?
    ├── YES → all-poky-linux/
    └── NO
        Does recipe use MACHINE variables?
        (COMPATIBLE_MACHINE, MACHINE_FEATURES, etc.)
        ├── YES → raspberrypi3_64-poky-linux/
        └── NO  → cortexa53-poky-linux/   ← DEFAULT
```

> **Key Point:** `hello-ayman` has no machine-specific variables,
> so it defaults to CPU level (`cortexa53`).

### Verify

```bash
# Check PACKAGE_ARCH for any recipe
bitbake -e hello-ayman | grep "^PACKAGE_ARCH="
# Output: PACKAGE_ARCH="cortexa53"

bitbake -e linux-raspberrypi | grep "^PACKAGE_ARCH="
# Output: PACKAGE_ARCH="raspberrypi3_64"
```

---

## 5. Fixing the Error with a Patch

### What is a Patch?

A patch file describes **changes** to source code without modifying the original.
It uses `-` for lines to remove and `+` for lines to add.

### How to Create a Patch

```bash
# 1. Go to WORKDIR
cd tmp/work/cortexa53-poky-linux/hello-ayman/1.0/

# 2. Save a copy of the original (broken) file
cp git/main.c git/main.c.orig

# 3. Fix the file
nano git/main.c
# Change: intmain() {
# To:     int main() {

# 4. Generate the patch (always: old first, new second)
diff -u git/main.c.orig git/main.c > fix.patch
```

### The Patch File Content

```diff
--- git/main.c.orig
+++ git/main.c
@@ -1,6 +1,6 @@
 #include<stdio.h>

-intmain() {
+int main() {
     printf("===========================================\n");
     printf("        Hello Ayman\n");
```

| Symbol | Meaning |
|--------|---------|
| `---` | Old file (before fix) |
| `+++` | New file (after fix) |
| `-` | Line removed |
| `+` | Line added |
| ` ` (space) | Context line (unchanged) |

### Patch File Naming

| Extension | Recognized by bitbake? |
|-----------|----------------------|
| `.patch` | ✅ Yes - applied in do_patch |
| `.diff` | ✅ Yes - applied in do_patch |
| `.txt` | ❌ No - treated as regular file |

---

## 6. Adding the Patch to the Recipe

### Step 1: Place the patch in the recipe directory

```
meta-main/
└── recipes-test/
    └── hello-ayman/
        ├── hello-ayman.bb         ← Recipe
        └── hello-ayman/           ← Folder named after recipe
            └── fix.patch          ← Patch file here
```

> **Why `hello-ayman/` folder?** This is the Yocto convention.
> bitbake searches for files in `<recipe-name>/` first.
> An alternative is `files/` but `<recipe-name>/` is preferred.

### Step 2: Add the patch to SRC_URI in the recipe

```python
SRC_URI = "git://github.com/ayman4105/test_yocto.git;protocol=https;branch=main \
           file://fix.patch \
          "
```

> bitbake sees `.patch` extension → automatically applies it in `do_patch`.

### Step 3: Clean and rebuild

```bash
bitbake hello-ayman -c cleanall    # Remove old build
bitbake hello-ayman                # Build fresh with patch
```

---

## 7. How do_patch Works (from the log)

When bitbake runs `do_patch`, it does 4 things:

### 7.1 Prepare Tools

```
Installed into sysroot: ['quilt-native', 'patch-native']
```

- `quilt-native` → patch management tool
- `patch-native` → the `patch` command itself
- `-native` means these run on your **PC**, not on the target

### 7.2 Search for the Patch File

bitbake searches **18 paths** from most specific to most generic:

```
Search order (specific → generic):
───────────────────────────────────
hello-ayman-1.0/poky/              ← version + distro
hello-ayman/poky/                  ← recipe + distro
files/poky/                        ← files + distro

hello-ayman-1.0/raspberrypi3-64/   ← version + machine
hello-ayman/raspberrypi3-64/        ← recipe + machine
files/raspberrypi3-64/              ← files + machine

hello-ayman-1.0/aarch64/           ← version + arch
hello-ayman/aarch64/                ← recipe + arch
files/aarch64/                      ← files + arch

hello-ayman-1.0/                   ← version (generic)
hello-ayman/                        ← recipe (generic) ✅ FOUND HERE
files/                              ← files (generic)
```

> This allows having **different patches per machine**:
> - `hello-ayman/raspberrypi3-64/fix.patch` → only for RPI3 64-bit
> - `hello-ayman/fix.patch` → for all machines

### 7.3 Apply the Patch

```
NOTE: Applying patch 'fix.patch'
```

The patch is applied to the source code in `S` (`${WORKDIR}/git`).

### 7.4 QA Check

```
NOTE: QA Issue: Missing Upstream-Status in patch [patch-status]
```

This is a **warning, not an error**. Yocto recommends adding an
`Upstream-Status` header to patches:

| Status | Meaning |
|--------|---------|
| `Upstream-Status: Pending` | Not yet sent upstream |
| `Upstream-Status: Submitted` | Pull request sent |
| `Upstream-Status: Accepted` | Merged upstream |
| `Upstream-Status: Backport` | Taken from newer version |
| `Upstream-Status: Inappropriate` | Not applicable upstream |

### Task Execution Order

```
do_fetch     → Downloaded code from github (with bug)
     │          + copied fix.patch
     ▼
do_unpack    → Extracted code into WORKDIR/git
     ▼
do_patch     → Applied fix.patch (intmain → int main) ✅
     ▼
do_compile   → Compiled successfully 🎉
     ▼
do_install   → Installed binary to ${D}/usr/bin/
```


## 8. Adding Recipe to an Existing Image

Instead of creating a custom image recipe, you can add packages to
`core-image-minimal` directly from `local.conf`:

### Edit local.conf

```bash
nano build-rpi/conf/local.conf
```

Add at the end:

```python
IMAGE_INSTALL:append = " hello-ayman"
#                      ↑ space is required!
```

> **Why the space?** `:append` concatenates directly without space.
> Without the leading space: `"packagegroup-core-boothello-ayman"` ❌
> With the leading space: `"packagegroup-core-boot hello-ayman"` ✅

### Build

```bash
bitbake core-image-minimal
```

### Build Result

```
Sstate summary: 99% match, 99% complete
Tasks: 3733 total, 3721 skipped, 12 ran

What happened:
- 3721 tasks already cached (sstate-cache) → SKIPPED
- Only ~12 new tasks ran:
  ├── hello-ayman (compile, install, package)
  ├── do_rootfs (add hello-ayman to filesystem)
  └── do_image (generate new image)
```

> The build was fast because sstate-cache reused everything
> that was already built. Only the new package and image
> generation tasks needed to run.

### Verify Package is in the Image

```bash
grep "hello-ayman" tmp/deploy/images/raspberrypi3-64/core-image-minimal-raspberrypi3-64.rootfs.manifest
```

Output:

```
hello-ayman cortexa53 1.0
```

---

## 9. Image Output Files

After building, the image files are located at:

```
tmp/deploy/images/raspberrypi3-64/
```

### Important Files

| File | What is it |
|------|-----------|
| `*.wic.bz2` | **SD Card image (compressed)** - this is what we flash 🎯 |
| `*.wic.bmap` | Block map file (speeds up flashing) |
| `*.rootfs.manifest` | List of all packages in the image |
| `*.tar.bz2` | Root filesystem (compressed tar) |
| `*.ext3` | Root filesystem (ext3 format) |
| `Image-raspberrypi3-64.bin` | Linux kernel |
| `bcm2710-rpi-3-b.dtb` | Device Tree for RPI3 Model B |
| `*.dtbo` | Device Tree Overlays |

### Symlinks vs Real Files

```bash
ls -la *.wic.bz2
```

```
core-image-minimal-raspberrypi3-64.rootfs.wic.bz2 → 
  core-image-minimal-raspberrypi3-64.rootfs-20260413184721.wic.bz2
  ↑ symlink (shortcut)                               ↑ real file (with timestamp)
```

> **Note:** Some tools (like `bzip2`) don't work with symlinks.
> Use the **real filename** (with timestamp) or use `bmaptool`
> which handles symlinks automatically.

---

## 10. Flashing to SD Card

### Method 1: bmaptool (Recommended ✅)

```bash
sudo apt install bmap-tools

cd tmp/deploy/images/raspberrypi3-64/

# Unmount SD card partitions
sudo umount /dev/mmcblk0p1 2>/dev/null
sudo umount /dev/mmcblk0p2 2>/dev/null

# Flash (decompresses automatically, verifies checksums, skips empty blocks)
sudo bmaptool copy core-image-minimal-raspberrypi3-64.rootfs-<timestamp>.wic.bz2 /dev/mmcblk0

sudo sync
```

> bmaptool automatically uses the `.bmap` file in the same directory.

### Method 2: dd

```bash
# Unmount
sudo umount /dev/mmcblk0p1 2>/dev/null
sudo umount /dev/mmcblk0p2 2>/dev/null

# Decompress and flash
bzcat core-image-minimal-raspberrypi3-64.rootfs-<timestamp>.wic.bz2 | \
  sudo dd of=/dev/mmcblk0 bs=4M status=progress conv=fsync

sudo sync
```

### bmaptool vs dd

| Feature | bmaptool | dd |
|---------|----------|-----|
| Decompress on the fly | ✅ | ❌ (need bzcat) |
| Skip empty blocks | ✅ (faster!) | ❌ (writes everything) |
| Checksum verification | ✅ | ❌ |
| Handles symlinks | ✅ | ❌ |

### ⚠️ Important

- **Check your device name!** `lsblk` to confirm
- SD Card = `/dev/mmcblk0`, USB Disk = `/dev/sdb`
- **Wrong device = data loss!**


### Boot Partition Files (config.txt & cmdline.txt)

After flashing, the SD card has two partitions:

```
/dev/mmcblk0p1 → boot   (FAT32 - kernel, dtb, config)
/dev/mmcblk0p2 → rootfs (EXT4 - Linux filesystem)
```

#### config.txt - needs `miniuart-bt` overlay

```
dtoverlay=vc4-fkms-v3d
disable_overscan=1
dtparam=audio=on
dtoverlay=miniuart-bt       ← Routes Bluetooth to mini UART
                               Frees PL011 UART for serial console
```

#### cmdline.txt - needs console entries

```
dwc_otg.lpm_enable=0 root=/dev/mmcblk0p2 rootfstype=ext4 rootwait console=tty1 console=ttyAMA0,115200 net.ifnames=0
```

| Parameter | Meaning |
|-----------|---------|
| `console=tty1` | Output to HDMI |
| `console=ttyAMA0,115200` | Output to Serial (PL011 UART) |

> **cmdline.txt must be ONE line! No line breaks!**

### inittab - needs getty for ttyAMA0

The kernel sends boot messages to serial, but **login prompt** needs
a `getty` process. Check `/etc/inittab` on the rootfs partition:

```bash
sudo mount /dev/mmcblk0p2 /mnt
cat /mnt/etc/inittab
```

If there is no `ttyAMA0` line, add:

```
AMA0:12345:respawn:/sbin/getty 115200 ttyAMA0 vt102
```

```bash
sudo sync
sudo umount /mnt
```

### Using minicom

```bash
# Find the device
ls /dev/ttyUSB*    # Usually /dev/ttyUSB0

# Connect
sudo minicom -D /dev/ttyUSB0 -b 115200

# Or configure with menu
sudo minicom -s
# Serial port setup:
#   A - Serial Device: /dev/ttyUSB0
#   E - Bps/Par/Bits: 115200 8N1
#   F - Hardware Flow Control: No
#   G - Software Flow Control: No
```

### Login

```
Poky (Yocto Project Reference Distro) 5.0.17 raspberrypi3-64 ttyAMA0

raspberrypi3-64 login: root
                       ↑ no password (debug-tweaks enabled)
```

---

## 12. Testing on RPI3

```bash
# Run our program
ayman
# Output:
# ===========================================
#         Hello Ayman
# ===========================================

# Check system info
uname -a
# Linux raspberrypi3-64 6.6.63-v8 ... aarch64 GNU/Linux

# Check where the binary is
which ayman
# /usr/bin/ayman

# Verify it's an ARM binary
file /usr/bin/ayman
# ayman: ELF 64-bit LSB executable, ARM aarch64 ...
```

---

## Summary: Complete Flow

```
1. Found error (intmain)
       │
       ▼
2. Understood WORKDIR path (cortexa53-poky-linux)
       │
       ▼
3. Created patch (diff -u old new > fix.patch)
       │
       ▼
4. Added patch to recipe layer (meta-main)
       │
       ▼
5. Added file://fix.patch to SRC_URI
       │
       ▼
6. Added hello-ayman to IMAGE_INSTALL in local.conf
       │
       ▼
7. Built: bitbake core-image-minimal
       │
       ▼
8. Flashed to SD Card (bmaptool or dd)
       │
       ▼
9. Configured serial (config.txt + cmdline.txt + inittab)
       │
       ▼
10. Booted RPI3 → login root → ran "ayman" → SUCCESS 🎉
```

## 13. Task Flags (varflags)

Tasks in bitbake can have **flags** that control their behavior.
The syntax is: `do_taskname[flag] = "value"`

### 13.1 `[dirs]` - Set working directory before running

```python
do_compile[dirs] = "${S}"
```

This tells bitbake to `cd` into `${S}` before executing `do_compile`.

```
Without [dirs]:

  do_compile() {
      ${CC} ${S}/src/main.c -o myapp
      ${CC} ${S}/src/utils.c -o utils.o
      ${CC} ${S}/src/helper.c -o helper.o
  }

With [dirs]:

  do_compile[dirs] = "${S}/src"

  do_compile() {
      ${CC} main.c -o myapp
      ${CC} utils.c -o utils.o
      ${CC} helper.c -o helper.o
  }
```

> Saves you from writing full paths repeatedly.

### 13.2 `[noexec]` - Skip a task entirely

```python
do_configure[noexec] = "1"
```

Tells bitbake to skip this task completely.

```
Without [noexec]:

  fetch → unpack → patch → configure → compile → install
                           ──────────
                           runs empty = wasted time

With [noexec] = "1":

  fetch → unpack → patch → SKIP → compile → install
                           ────
                           skipped = faster build
```

> Useful when recipe has no configure step (e.g., simple .c file with no
> CMake, Autotools, or Meson).

### 13.3 `[nostamp]` - Always run the task

```python
do_compile[nostamp] = "1"
```

Normally bitbake creates a **stamp file** after a task succeeds.
Next time it sees the stamp, it skips the task.
`[nostamp]` disables this — the task runs **every time**.

```
Normal (with stamp):

  $ bitbake hello-ayman
    do_compile → ran ✅ (stamp created)

  $ bitbake hello-ayman
    do_compile → SKIPPED (stamp exists)

With [nostamp] = "1":

  $ bitbake hello-ayman
    do_compile → ran ✅ (no stamp)

  $ bitbake hello-ayman
    do_compile → ran AGAIN ✅ (always runs)
```

> Useful for tasks that must run every time regardless of changes.

### 13.4 `[depends]` - Task-level dependency

```python
do_compile[depends] = "openssl:do_install"
```

This task will **not start** until the specified task in another recipe finishes.

```
hello-ayman: do_compile
    │
    │  WAITING...
    │
    └── openssl: do_install must finish first!
```

> Useful when your recipe needs a library from another recipe to compile.

### 13.5 `[cleandirs]` - Delete and recreate directory

```python
do_compile[cleandirs] = "${B}"
```

Before running the task, bitbake will `rm -rf ${B}` then `mkdir ${B}`.
Guarantees a clean build directory every time.

### Summary

| Flag | Meaning | Use case |
|------|---------|----------|
| `[dirs]` | `cd` to directory before running | Avoid writing full paths |
| `[noexec]` | Skip task entirely | No configure step needed |
| `[nostamp]` | Run task every time | Task must always execute |
| `[depends]` | Wait for another recipe's task | Cross-recipe dependency |
| `[cleandirs]` | Delete and recreate directory | Clean build each time |
