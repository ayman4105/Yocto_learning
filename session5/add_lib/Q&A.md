# Yocto Project Complete Guide - Q&A Summary

## Table of Contents

1. [Creating a Library and Application in Yocto](#1-creating-a-library-and-application-in-yocto)
2. [Understanding DEPENDS and PROVIDES](#2-understanding-depends-and-provides)
3. [PROVIDES and PREFERRED_PROVIDER](#3-provides-and-preferred_provider)
4. [DEPENDS vs RDEPENDS](#4-depends-vs-rdepends)
5. [Understanding Yocto Image Structure](#5-understanding-yocto-image-structure)
6. [IMAGE_INSTALL vs IMAGE_FEATURES](#6-image_install-vs-image_features)
7. [Understanding packagegroup-core-boot](#7-understanding-packagegroup-core-boot)
8. [CORE_IMAGE_EXTRA_INSTALL vs IMAGE_INSTALL:append](#8-core_image_extra_install-vs-image_installappend)
9. [What Goes Where - Configuration Files](#9-what-goes-where---configuration-files)
10. [Adding External Layers](#10-adding-external-layers)
11. [Understanding Sstate Cache](#11-understanding-sstate-cache)
12. [Build Errors and Solutions](#12-build-errors-and-solutions)
13. [Verifying Image Contents](#13-verifying-image-contents)
14. [Complete Layer Structure](#14-complete-layer-structure)

---

## 1. Creating a Library and Application in Yocto

### Question
How to create a library recipe and an application that uses it?

### Answer

#### Layer Structure

```
meta-test/
├── conf/
│   └── layer.conf
├── recipes-math/
│   └── ayman/
│       ├── ayman_1.0.bb
│       └── files/
│           ├── mymath.c
│           └── mymath.h
└── recipes-calc/
    └── calc/
        ├── calc_1.0.bb
        └── files/
            └── main.c
```

#### Library Recipe (ayman_1.0.bb)

```bitbake
SUMMARY = "A simple math library"
LICENSE = "CLOSED"

SRC_URI = "file://mymath.c file://mymath.h"
S = "${WORKDIR}"

PROVIDES = "ayman"

do_compile() {
    ${CC} ${CFLAGS} -c ${S}/mymath.c -o ${S}/mymath.o
    ${AR} rcs ${S}/libmath.a ${S}/mymath.o
}

do_install() {
    install -d ${D}${libdir}
    install -d ${D}${includedir}
    install -m 0644 ${S}/libmath.a ${D}${libdir}/libmath.a
    install -m 0644 ${S}/mymath.h ${D}${includedir}/mymath.h
}

FILES:${PN}-dev = "${includedir}/* ${libdir}/*"
```

#### Application Recipe (calc_1.0.bb)

```bitbake
SUMMARY = "A simple calculator"
LICENSE = "CLOSED"

SRC_URI = "file://main.c"
S = "${WORKDIR}"

DEPENDS = "ayman"

do_compile() {
    ${CC} ${CFLAGS} ${LDFLAGS} ${S}/main.c \
        -L${STAGING_LIBDIR} \
        -I${STAGING_INCDIR} \
        -lmath \
        -o ${S}/calc
}

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${S}/calc ${D}${bindir}/calc
}
```

#### Key Concepts

```
Compilation Flags:
==================

-c                  = Compile only, don't link (no main() needed)
-L${STAGING_LIBDIR} = Where to find libraries
-I${STAGING_INCDIR} = Where to find headers
-lmath              = Link with libmath.a
${AR} rcs           = Create static archive (r=insert, c=create, s=index)
```

#### Staging Area

```
Problem: Each recipe builds in isolated directory

    ┌──────────────┐         ┌──────────────┐
    │    ayman     │    ❌    │    calc      │
    │   WORKDIR    │ ──────> │   WORKDIR    │
    │  libmath.a   │  Can't  │  needs it!   │
    └──────────────┘  see!   └──────────────┘

Solution: STAGING - shared area

    ┌──────────────┐         ┌─────────────────┐
    │    ayman     │  COPY   │  STAGING AREA   │
    │   (builds)   │ ──────> │  libmath.a ─────────┐
    │              │         │  mymath.h  ─────────┤
    └──────────────┘         └─────────────────┘   │
                                                    │
                             ┌──────────────┐      │
                             │    calc      │  ✅  │
                             │   (builds)   │ <────┘
                             └──────────────┘
```

---

## 2. Understanding DEPENDS and PROVIDES

### Question
What is PROVIDES and how does DEPENDS connect to it?

### Answer

```
PROVIDES = "ayman"      (in ayman_1.0.bb)
    │
    └── "I provide something called 'ayman'"

DEPENDS = "ayman"       (in calc_1.0.bb)
    │
    └── "I need something called 'ayman'"

Yocto connects them:
    1. calc needs "ayman"
    2. Yocto finds ayman recipe PROVIDES "ayman"
    3. Yocto builds ayman FIRST
    4. Stages outputs to STAGING_LIBDIR/STAGING_INCDIR
    5. Then builds calc
```

---

## 3. PROVIDES and PREFERRED_PROVIDER

### Question
What if multiple recipes PROVIDE the same thing?

### Answer

```
Problem:
========

    ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
    │   mathlib   │  │   methics   │  │    real     │
    │ PROVIDES=   │  │ PROVIDES=   │  │ PROVIDES=   │
    │   "math"    │  │   "math"    │  │   "math"    │
    └──────┬──────┘  └──────┬──────┘  └──────┬──────┘
           └────────────────┼────────────────┘
                            v
                  ┌─────────────────┐
                  │      calc       │
                  │ DEPENDS="math"  │
                  │ Which one??? 🤔 │
                  └─────────────────┘

Error:
    "Multiple providers are available for math"


Solution:
=========

    # In local.conf or distro.conf
    PREFERRED_PROVIDER_math = "mathlib"

Syntax:
    PREFERRED_PROVIDER_<provides-name> = "<recipe-name>"
```

---

## 4. DEPENDS vs RDEPENDS

### Question
What is the difference between DEPENDS and RDEPENDS?

### Answer

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│   DEPENDS   = Build-time dependency (Compilation)               │
│               "I need this to COMPILE"                          │
│                                                                 │
│   RDEPENDS  = Runtime dependency (Execution)                    │
│               "I need this to RUN"                              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

         BUILD TIME                         RUNTIME
        (on your PC)                   (on Raspberry Pi)
              │                               │
              v                               v
    ┌─────────────────────┐       ┌─────────────────────┐
    │  Compiling needs:   │       │  Running needs:     │
    │  - Headers (.h)     │       │  - Shared libs (.so)│
    │  - Libraries        │       │  - Other programs   │
    │                     │       │                     │
    │  DEPENDS = "lib"    │       │  RDEPENDS:${PN} =   │
    │                     │       │    "lib"            │
    └─────────────────────┘       └─────────────────────┘


When to use:
============

    Static library (.a)?
    └──> DEPENDS only        (code embedded in executable)

    Shared library (.so)?
    └──> DEPENDS + RDEPENDS  (code loaded at runtime)

    Python script?
    └──> RDEPENDS only       (python3 needed to run)
```

---

## 5. Understanding Yocto Image Structure

### Question
How does the Yocto image structure work?

### Answer

```
                            ┌─────────────┐
                            │    IMAGE    │
                            └──────┬──────┘
                                   │
            ┌──────────────────────┼──────────────────────┐
            │                      │                      │
            v                      v                      v
    ┌──────────────┐      ┌──────────────┐      ┌──────────────┐
    │ Image Recipe │      │  local.conf  │      │ distro.conf  │
    │ (.bb file)   │      │              │      │              │
    └──────────────┘      └──────────────┘      └──────────────┘
            │                      │                      │
            v                      v                      v
    ┌─────────────────────────────────────────────────────────────┐
    │                                                             │
    │   IMAGE_INSTALL    = What packages to install               │
    │   IMAGE_FEATURES   = What features to enable                │
    │   DISTRO_FEATURES  = What distro capabilities               │
    │                                                             │
    └─────────────────────────────────────────────────────────────┘


File Locations:
===============

    poky/
    └── meta/
        └── recipes-core/
            └── images/
                └── core-image-minimal.bb

    meta-iti/
    └── recipes-core/
        └── images/
            └── iti-image.bb           ← Your custom image

    build-rpi/
    └── conf/
        ├── local.conf                 ← Personal settings
        └── bblayers.conf              ← Layer list
```

---

## 6. IMAGE_INSTALL vs IMAGE_FEATURES

### Question
What is the difference between IMAGE_INSTALL and IMAGE_FEATURES?

### Answer

```
IMAGE_INSTALL = Individual packages (manual)
================================================

    IMAGE_INSTALL = "vim nano htop calc"

    You MANUALLY specify each package.


IMAGE_FEATURES = Package bundles (automatic)
============================================

    IMAGE_FEATURES = "ssh-server-openssh"

    Automatically installs:
    - openssh
    - openssh-sshd
    - openssh-sftp
    - openssh-keygen
    - ... and more!


Available IMAGE_FEATURES:
=========================

    ┌─────────────────────┬───────────────────────────────────────┐
    │  Feature            │  What it adds                         │
    ├─────────────────────┼───────────────────────────────────────┤
    │  debug-tweaks       │  Allow root login, empty password     │
    │  ssh-server-openssh │  OpenSSH server (full)                │
    │  ssh-server-dropbear│  Dropbear SSH (smaller)               │
    │  tools-sdk          │  gcc, make on target                  │
    │  tools-debug        │  gdb, strace                          │
    │  package-management │  opkg/rpm on target                   │
    └─────────────────────┴───────────────────────────────────────┘


When to use:
============

    "I want SSH access"
        └──> IMAGE_FEATURES = "ssh-server-openssh"

    "I want vim editor"
        └──> IMAGE_INSTALL += "vim"
```

---

## 7. Understanding packagegroup-core-boot

### Question
What is packagegroup-core-boot and why is it needed?

### Answer

```
IMAGE_INSTALL = "packagegroup-core-boot ${CORE_IMAGE_EXTRA_INSTALL}"
                       │                        │
                       v                        v
              ┌─────────────────┐      ┌─────────────────┐
              │  REQUIRED!      │      │  YOUR packages  │
              │  System boots   │      │  from config    │
              └─────────────────┘      └─────────────────┘


packagegroup-core-boot contains:
================================

    📦 busybox       → Basic Linux commands (ls, cp, cat)
    📦 base-files    → Filesystem structure (/etc, /home)
    📦 base-passwd   → Root user, /etc/passwd
    📦 sysvinit      → Init system (PID 1)
    📦 udev          → Device management


Without it:
===========

    ❌ System cannot boot
    ❌ No root user
    ❌ No basic commands
    ❌ No init system
    ❌ NOTHING WORKS!
```

---

## 8. CORE_IMAGE_EXTRA_INSTALL vs IMAGE_INSTALL:append

### Question
What is the difference and which should I use?

### Answer

```
┌──────────────────────────┬──────────────────────────────────────┐
│  IMAGE_INSTALL:append    │  CORE_IMAGE_EXTRA_INSTALL            │
├──────────────────────────┼──────────────────────────────────────┤
│                          │                                      │
│  ⚠️ Need space before    │  ✅ No space needed                  │
│     first package!       │                                      │
│                          │                                      │
│  " vim nano"             │  "vim nano"                          │
│   │                      │                                      │
│   └─ forget = ERROR!     │                                      │
│                          │                                      │
├──────────────────────────┼──────────────────────────────────────┤
│                          │                                      │
│  ✅ Works on ANY image   │  ⚠️ Only works if image uses it!    │
│                          │                                      │
└──────────────────────────┴──────────────────────────────────────┘


Recommendation:
===============

    For TESTING (in local.conf):
        IMAGE_INSTALL:append = " vim calc"    ← OK for testing

    For PRODUCTION:
        Put in your image recipe (.bb file)   ← RECOMMENDED!


Why image recipe is better:
===========================

    local.conf is NOT shared via git!
    Image recipe IS shared via git!

    Team member clones your layer → gets all packages automatically!
```

---

## 9. What Goes Where - Configuration Files

### Question
Where should I put different settings?

### Answer

```
┌──────────────────────────┬──────────────────────────────────────┐
│  Setting                 │  Where                               │
├──────────────────────────┼──────────────────────────────────────┤
│                          │                                      │
│  IMAGE_INSTALL           │  image.bb ✅                         │
│  IMAGE_FEATURES          │  image.bb ✅                         │
│  (What to install)       │                                      │
│                          │                                      │
├──────────────────────────┼──────────────────────────────────────┤
│                          │                                      │
│  ENABLE_UART             │  machine.conf ✅ (or local.conf)     │
│  CMDLINE_ROOTFS          │  machine.conf ✅ (or local.conf)     │
│  GPU_MEM                 │  machine.conf ✅                     │
│  (Hardware/Boot)         │                                      │
│                          │                                      │
├──────────────────────────┼──────────────────────────────────────┤
│                          │                                      │
│  DISTRO_FEATURES         │  distro.conf ✅                      │
│  INIT_MANAGER (systemd)  │  distro.conf ✅                      │
│  (System behavior)       │                                      │
│                          │                                      │
├──────────────────────────┼──────────────────────────────────────┤
│                          │                                      │
│  MACHINE = "..."         │  local.conf ✅                       │
│  DISTRO = "..."          │  local.conf ✅                       │
│  DL_DIR, SSTATE_DIR      │  local.conf ✅                       │
│  (Personal choices)      │                                      │
│                          │                                      │
└──────────────────────────┴──────────────────────────────────────┘


Golden Rule:
============

    "If someone else needs it, put it in your LAYER"
    "If it's only for your machine, put it in local.conf"


Example:
========

    MACHINE = "raspberrypi3-64"     → local.conf (your hardware choice)
    IMAGE_INSTALL = "vim calc"      → image.bb (project requirement)
    ENABLE_UART = "1"               → machine.conf or local.conf
```

---

## 10. Adding External Layers

### Question
How to add meta-openembedded for tcpdump?

### Answer

```
tcpdump is NOT in poky/meta!
It's in meta-networking (part of meta-openembedded)


Step 1: Clone
=============

    cd ~/ITI/fady/Yocto
    git clone -b kirkstone https://github.com/openembedded/meta-openembedded.git


Step 2: Add layers (ORDER MATTERS!)
===================================

    cd build-rpi

    # First: meta-oe (required by others!)
    bitbake-layers add-layer ../meta-openembedded/meta-oe

    # Second: meta-python
    bitbake-layers add-layer ../meta-openembedded/meta-python

    # Third: meta-networking (has tcpdump)
    bitbake-layers add-layer ../meta-openembedded/meta-networking


Dependency Chain:
=================

           ┌─────────────┐
           │   meta-oe   │  ← Add FIRST!
           └──────┬──────┘
                  │
           ┌──────┴──────┐
           │             │
           v             v
    ┌─────────────┐ ┌─────────────────┐
    │ meta-python │ │ meta-networking │
    └─────────────┘ └─────────────────┘


Important Note:
===============

    meta-openembedded/ is NOT a layer itself!
    It CONTAINS multiple layers!

    ❌ WRONG:  bitbake-layers add-layer meta-openembedded/
    ✅ CORRECT: bitbake-layers add-layer meta-openembedded/meta-oe


Layer Contents:
===============

    ┌───────────────────────┬─────────────────────────────────────┐
    │  Layer                │  Contains                           │
    ├───────────────────────┼─────────────────────────────────────┤
    │  poky/meta            │  vim, python3, openssh, busybox     │
    │  meta-oe              │  htop, screen, tmux                 │
    │  meta-networking      │  tcpdump, iperf, nmap               │
    │  meta-python          │  python3-pip, python3-requests      │
    │  meta-raspberrypi     │  RPi kernel, firmware, drivers      │
    └───────────────────────┴─────────────────────────────────────┘
```

---

## 11. Understanding Sstate Cache

### Question
Is Yocto building from scratch every time?

### Answer

```
NO! Yocto uses sstate (shared state) cache!

Sstate summary: Wanted 1264 Local 368 Mirrors 0 Missed 896 Current 1710
                       │        │                    │
                       │        │                    └── Need to BUILD
                       │        │
                       │        └── Found in cache! SKIP! ✅
                       │
                       └── Total needed


What this means:
================

    Local 368    = Found in sstate cache → SKIPPED!
    Missed 896   = Not in cache → Building now


Cache Locations:
================

    SSTATE_DIR = "/home/ayman/.../sstate-cache"   ← Cached build outputs
    DL_DIR = "/home/ayman/.../downloads"          ← Downloaded sources


Benefits:
=========

    ✅ Same package = skip rebuild (use cached)
    ✅ Same source = skip download (use cached)
    ✅ Multiple builds share cache
    ✅ Saves hours of build time!


Build Times:
============

    First build (no cache):     2-4 hours
    Subsequent builds:          10-30 minutes
```

---

## 12. Build Errors and Solutions

### Question
How to fix common build errors?

### Answer

```
Error: "Nothing RPROVIDES 'tcpdump'"
====================================

    Meaning: tcpdump recipe not found in any layer

    Solution:
        bitbake-layers add-layer ../meta-openembedded/meta-oe
        bitbake-layers add-layer ../meta-openembedded/meta-python
        bitbake-layers add-layer ../meta-openembedded/meta-networking


Error: "undefined reference to 'function'"
==========================================

    Meaning: Library mismatch or corrupted cache

    Solution:
        bitbake -c cleansstate <package-name>
        bitbake <image-name>


Error: openssh/icu/fontconfig failed
====================================

    Solution:
        # Clean corrupted packages
        bitbake -c cleansstate openssh
        bitbake -c cleansstate openssl
        bitbake -c cleansstate icu
        bitbake -c cleansstate fontconfig

        # Rebuild
        bitbake ayman-image


Nuclear Option (if all else fails):
===================================

    # Remove entire tmp directory
    rm -rf ~/ITI/fady/Yocto/share/tmp

    # Rebuild from scratch (takes 1-3 hours)
    bitbake ayman-image


Prevent Crashes - Add to local.conf:
====================================

    # Reduce parallel tasks (prevents memory crashes)
    BB_NUMBER_THREADS = "4"
    PARALLEL_MAKE = "-j 4"

    # Better sstate reuse
    BB_HASHSERVE = "auto"
    BB_SIGNATURE_HANDLER = "OEEquivHash"
```

---

## 13. Verifying Image Contents

### Question
How to check what packages are in my image?

### Answer

```bash
# Check manifest file
cd ~/ITI/fady/Yocto/share/tmp/deploy/images/raspberrypi3-64

# Show all packages
cat ayman-image-raspberrypi3-64.rootfs.manifest

# Search for specific packages
cat ayman-image-raspberrypi3-64.rootfs.manifest | grep -E "tcpdump|python|vim|calc"

# Expected output:
# calc cortexa53 1.0
# python3-core cortexa53 3.12.12
# tcpdump cortexa53 4.99.4
# vim cortexa53 9.1.1683
```

---

## 14. Complete Layer Structure

### Question
What is the final complete layer structure?

### Answer

```
meta-test/
├── conf/
│   └── layer.conf
│
├── recipes-core/
│   └── images/
│       └── ayman-image.bb              ← Custom image recipe
│
├── recipes-calc/
│   └── calc/
│       ├── calc_1.0.bb                 ← Application recipe
│       └── files/
│           └── main.c
│
├── recipes-math/
│   └── math/
│       ├── math_1.0.bb                 ← Library recipe
│       └── files/
│           ├── mymath.c
│           └── mymath.h
│
└── recipes-dash/
    └── dash/
        └── ...


build-rpi/
└── conf/
    ├── local.conf                      ← MACHINE, paths, UART
    └── bblayers.conf                   ← Layer list


~/ITI/fady/Yocto/
├── poky/                               ← Yocto core
├── meta-raspberrypi/                   ← RPi support
├── meta-openembedded/                  ← Extra packages
│   ├── meta-oe/
│   ├── meta-python/
│   └── meta-networking/
├── meta-test/                          ← YOUR layer
├── build-rpi/                          ← Build directory
└── share/
    ├── downloads/                      ← Downloaded sources
    ├── sstate-cache/                   ← Build cache
    └── tmp/
        └── deploy/images/              ← Output images
```

---

## Quick Reference Commands

```bash
# Setup environment
source poky/oe-init-build-env build-rpi

# Layer management
bitbake-layers add-layer ../meta-test
bitbake-layers show-layers
bitbake-layers show-recipes "*"

# Building
bitbake ayman-image
bitbake -c cleansstate <package>
bitbake -c clean <package>

# Flashing
sudo bmaptool copy ayman-image-raspberrypi3-64.rootfs.wic.bz2 /dev/mmcblk0

# Verify image contents
cat ayman-image-raspberrypi3-64.rootfs.manifest | grep <package>
```

---

## Final Image Contents

```
ayman-image includes:
=====================

✅ calc          → Your custom calculator app
✅ tcpdump       → Network packet analyzer
✅ python3       → Full Python 3.12 with all modules
✅ vim           → Text editor with syntax highlighting
✅ SSH           → Remote access via Dropbear
✅ UART          → Serial console enabled
✅ NFS boot      → Configured for network boot
```
