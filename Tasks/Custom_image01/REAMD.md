# Yocto Custom Image - Quick Guide

## Overview

This guide shows how to create a custom Yocto image with your own packages.

```
┌─────────────────────────────────────────────────────────────────┐
│                    CUSTOM IMAGE                                 │
│                                                                 │
│   ┌─────────────┐  ┌─────────────┐  ┌─────────────┐            │
│   │    calc     │  │   python3   │  │   tcpdump   │            │
│   │ (your app)  │  │             │  │             │            │
│   └─────────────┘  └─────────────┘  └─────────────┘            │
│                                                                 │
│   + vim + SSH access + UART enabled                             │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Layer Structure

```
meta-test/
├── conf/
│   └── layer.conf
│
├── recipes-core/
│   └── images/
│       └── ayman-image.bb          ← Your custom image
│
├── recipes-calc/
│   └── calc/
│       ├── calc_1.0.bb             ← Your app recipe
│       └── files/
│           └── main.c
│
└── recipes-math/
    └── math/
        ├── math_1.0.bb             ← Library recipe
        └── files/
            ├── mymath.c
            └── mymath.h
```

---

## Custom Image Recipe

**File:** `meta-test/recipes-core/images/ayman-image.bb`

```bitbake
SUMMARY = "Custom Image for Raspberry Pi"
LICENSE = "MIT"

inherit core-image

# Features (bundles)
IMAGE_FEATURES += "debug-tweaks ssh-server-dropbear"

# Base packages (REQUIRED!)
IMAGE_INSTALL = "packagegroup-core-boot"

# Your packages
IMAGE_INSTALL += "vim python3 tcpdump"
IMAGE_INSTALL += "calc"
```

---

## What Goes Where

```
┌──────────────────────┬─────────────────────────────────────────┐
│  Setting             │  Location                               │
├──────────────────────┼─────────────────────────────────────────┤
│  IMAGE_INSTALL       │  image.bb (your custom image)           │
│  IMAGE_FEATURES      │  image.bb (your custom image)           │
├──────────────────────┼─────────────────────────────────────────┤
│  ENABLE_UART         │  local.conf (or machine.conf)           │
│  CMDLINE_ROOTFS      │  local.conf (or machine.conf)           │
├──────────────────────┼─────────────────────────────────────────┤
│  MACHINE             │  local.conf                             │
│  DL_DIR, SSTATE_DIR  │  local.conf                             │
└──────────────────────┴─────────────────────────────────────────┘
```

---

## local.conf Settings

```bash
# Machine
MACHINE = "raspberrypi3-64"

# Paths
DL_DIR ?= "/path/to/downloads"
SSTATE_DIR ?= "/path/to/sstate-cache"

# Hardware
ENABLE_UART = "1"

# NFS Boot (optional)
CMDLINE_ROOTFS = "root=/dev/nfs rootfstype=nfs nfsroot=192.168.2.1:/srv/nfs/rootfs,nfsvers=3,tcp rw ip=192.168.2.50::192.168.2.1:255.255.255.0::eth0:off"
```

---

## Adding External Layers

For packages like `tcpdump`, you need `meta-openembedded`:

```bash
# Clone meta-openembedded
cd ~/Yocto
git clone -b kirkstone https://github.com/openembedded/meta-openembedded.git

# Add layers (ORDER MATTERS!)
cd build-rpi
bitbake-layers add-layer ../meta-openembedded/meta-oe
bitbake-layers add-layer ../meta-openembedded/meta-python
bitbake-layers add-layer ../meta-openembedded/meta-networking
```

---

## Build Commands

```bash
# Setup environment
cd ~/Yocto
source poky/oe-init-build-env build-rpi

# Add your layer
bitbake-layers add-layer ../meta-test

# Build image
bitbake ayman-image

# Output location
ls tmp/deploy/images/raspberrypi3-64/ayman-image*.wic.bz2
```

---

## Flash to SD Card

```bash
cd tmp/deploy/images/raspberrypi3-64

# Using bmaptool (faster)
sudo bmaptool copy ayman-image-raspberrypi3-64.rootfs.wic.bz2 /dev/mmcblk0

# Or using dd
bzip2 -dk ayman-image-raspberrypi3-64.rootfs.wic.bz2
sudo dd if=ayman-image-raspberrypi3-64.rootfs.wic of=/dev/mmcblk0 bs=4M status=progress
sync
```

---

## Verify Image Contents

```bash
# Check installed packages
cat ayman-image-raspberrypi3-64.rootfs.manifest | grep -E "calc|python|vim|tcpdump"
```

---

## Quick Reference

| Component | Description |
|-----------|-------------|
| `packagegroup-core-boot` | Required! Makes system bootable |
| `IMAGE_FEATURES` | Package bundles (ssh, debug-tweaks) |
| `IMAGE_INSTALL` | Individual packages |
| `inherit core-image` | Required! Image building class |

---

## Boot and Test

```bash
# Login
Username: root
Password: (empty)

# Test packages
calc
vim --version
python3 --version
tcpdump --version
