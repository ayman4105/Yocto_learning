# WiFi Setup on Raspberry Pi 3 B+ with Yocto (Scarthgap)

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Understanding the Components](#understanding-the-components)
  - [DISTRO_FEATURES and WiFi](#distro_features-and-wifi)
  - [Kernel Modules](#kernel-modules)
  - [Firmware Files](#firmware-files)
  - [wpa_supplicant](#wpa_supplicant)
  - [systemd Service File](#systemd-service-file)
  - [systemd Network File](#systemd-network-file)
- [Creating the wifi-config Recipe](#creating-the-wifi-config-recipe)
- [Configuring the Image](#configuring-the-image)
- [Building and Flashing](#building-and-flashing)
- [Debugging WiFi Issues](#debugging-wifi-issues)
- [devtool deploy-target](#devtool-deploy-target)
- [Troubleshooting Reference](#troubleshooting-reference)

---

## Overview

This guide explains how to set up WiFi on a Raspberry Pi 3 B+ running a custom
Yocto distribution (scarthgap branch) with systemd as the init manager.

The goal is to have the RPi connect to WiFi automatically on boot, get an IP
address via DHCP, and allow SSH access over WiFi — no Ethernet cable or
TTL/Serial adapter needed.

```
  Final Result:
  ═════════════
  
  ┌──────────┐      WiFi 📶      ┌──────────┐
  │  Laptop  │◄──────────────────►│  RPi 3   │
  │          │                    │  B+      │
  └──────────┘                    └──────────┘
  
  ✅ NO Ethernet cable
  ✅ NO TTL/Serial adapter
  ✅ NO HDMI monitor
  ✅ Just power cable + WiFi
```

---

## Architecture

```
  How WiFi works on RPi with systemd:
  ════════════════════════════════════

  ┌─────────────────────────────────────────────────────────────────┐
  │  Layer 1: HARDWARE                                              │
  │  BCM43455 WiFi chip (on RPi 3 B+)                              │
  │  Needs: firmware file to operate                                │
  └─────────────────────┬───────────────────────────────────────────┘
                        │
  ┌─────────────────────▼───────────────────────────────────────────┐
  │  Layer 2: KERNEL                                                │
  │  Modules: brcmfmac + brcmfmac-wcc + brcmutil + cfg80211       │
  │  Creates: wlan0 interface                                       │
  │  Loads: firmware from /lib/firmware/brcm/                       │
  └─────────────────────┬───────────────────────────────────────────┘
                        │
  ┌─────────────────────▼───────────────────────────────────────────┐
  │  Layer 3: USERSPACE                                             │
  │  wpa_supplicant: connects to WiFi (reads ssid + password)      │
  │  systemd: starts wpa_supplicant via .service file               │
  │  systemd-networkd: assigns IP via DHCP using .network file      │
  └─────────────────────┬───────────────────────────────────────────┘
                        │
  ┌─────────────────────▼───────────────────────────────────────────┐
  │  Layer 4: APPLICATION                                           │
  │  SSH server (dropbear): accepts connections over WiFi            │
  │  Your apps: can use network normally                             │
  └─────────────────────────────────────────────────────────────────┘
```

---

## Prerequisites

- Yocto Project (poky) scarthgap branch
- meta-raspberrypi layer (scarthgap branch)
- meta-openembedded layer (scarthgap branch)
- Raspberry Pi 3 Model B+ (BCM43455 WiFi chip)
- SD card + card reader
- bmaptool installed (`sudo apt install bmap-tools`)

---

## Understanding the Components

### DISTRO_FEATURES and WiFi

WiFi support in Yocto starts at the distro level. Recipes check
`DISTRO_FEATURES` to decide whether to build WiFi support or not.

```
  ayman.conf (distro config):
  
  DISTRO_FEATURES:append = " systemd usrmerge wifi"
                                              ▲
                                              │
                    recipes check this flag and build WiFi support
```

Without `wifi` in `DISTRO_FEATURES`, packages like `wpa-supplicant` might
build without full WiFi functionality.

### Kernel Modules

The RPi 3 B+ uses the Broadcom BCM43455 WiFi chip. The Linux kernel needs
specific modules to talk to this chip:

```
  Required kernel modules for RPi 3 B+:
  ══════════════════════════════════════
  
  brcmfmac          ──►  main Broadcom WiFi driver
  brcmfmac-wcc      ──►  WCC vendor extension (NEW in kernel 6.6+!)
  brcmfmac-bca      ──►  BCA vendor extension
  brcmutil          ──►  Broadcom utility functions (auto-loaded)
  cfg80211          ──►  Linux wireless configuration API (auto-loaded)
```

```
  ⚠️ IMPORTANT: In kernel 6.6+, brcmfmac-wcc is REQUIRED!
  Without it you get:
  "brcmf_fwvid_request_module: mod=wcc: failed 256"
  "brcmf_attach: brcmf_fwvid_attach failed"
```

In `local.conf`:

```bash
# auto-load WiFi module on boot
KERNEL_MODULE_AUTOLOAD += "brcmfmac"
```

In `ayman-image.bb`:

```bash
# install all required kernel modules
IMAGE_INSTALL:append = " kernel-module-brcmfmac kernel-module-brcmfmac-wcc kernel-module-brcmfmac-bca"
```

### Firmware Files

The kernel driver needs firmware files to communicate with the WiFi chip.
These are binary blobs provided by Broadcom.

```
  RPi 3 Model B   ──►  BCM43430  ──►  linux-firmware-rpidistro-bcm43430
  RPi 3 Model B+  ──►  BCM43455  ──►  linux-firmware-rpidistro-bcm43455  ◄── this one!
```

```
  How to find the right firmware package:
  ═══════════════════════════════════════
  
  # check what the kernel is looking for:
  dmesg | grep -i brcm
  # output: "using brcm/brcmfmac43455-sdio" ──► you need 43455!
  
  # find available packages:
  bitbake linux-firmware-rpidistro -e 2>/dev/null | grep "^PACKAGES="
```

The firmware files are installed to `/lib/firmware/brcm/` on the target.

### wpa_supplicant

wpa_supplicant is the userspace program that manages WiFi connections.
It handles authentication (WPA2-PSK) and maintains the connection.

```
  wpa_supplicant needs a config file:
  ════════════════════════════════════
  
  /etc/wpa_supplicant/wpa_supplicant-wlan0.conf
  
  This file contains:
  - WiFi network name (SSID)
  - WiFi password (PSK)
  - Control interface settings
```

Config file explained:

```bash
ctrl_interface=/var/run/wpa_supplicant
# creates a socket file for control tools (wpa_cli) to communicate
# with wpa_supplicant

ctrl_interface_group=0
# only root (group 0) can control WiFi settings

update_config=1
# allow wpa_supplicant to save new networks to this file
# 1 = can write, 0 = read-only

network={
    ssid="your_wifi_name"
    # SSID = Service Set Identifier = WiFi network name
    # case sensitive! "MyWiFi" ≠ "mywifi"
    
    psk="your_wifi_password"
    # PSK = Pre-Shared Key = WiFi password
}

# You can add multiple networks with priority:
# network={
#     ssid="HomeWiFi"
#     psk="home123"
#     priority=2       ◄── higher number = try first
# }
# network={
#     ssid="OfficeWiFi"
#     psk="office456"
#     priority=1       ◄── lower number = try second
# }
```

### systemd Service File

systemd needs a `.service` file to know WHEN and HOW to start wpa_supplicant.

```ini
# /usr/lib/systemd/system/wpa_supplicant@wlan0.service

[Unit]
Description=WPA supplicant daemon (interface-specific version)
Requires=sys-subsystem-net-devices-wlan0.device
After=sys-subsystem-net-devices-wlan0.device
Before=network.target
Wants=network.target

[Service]
Type=simple
ExecStart=/usr/sbin/wpa_supplicant -c /etc/wpa_supplicant/wpa_supplicant-wlan0.conf -i wlan0

[Install]
WantedBy=multi-user.target
```

Section breakdown:

```
  [Unit] - Dependencies and ordering:
  ════════════════════════════════════
  Requires=...wlan0.device    "I NEED wlan0 hardware to exist"
  After=...wlan0.device       "start me AFTER wlan0 appears"
  Before=network.target       "finish BEFORE network is considered ready"
  Wants=network.target        "I'd like network.target to start too"
  
  Why BOTH Requires and After?
  ════════════════════════════
  Requires alone: might start BEFORE wlan0 is ready (race condition!)
  After alone: starts even if wlan0 doesn't exist (waste!)
  Both: start AFTER wlan0 AND only IF wlan0 exists ✅
  
  
  [Service] - What to run:
  ════════════════════════
  Type=simple     "runs forever, doesn't fork to background"
  ExecStart=...   "the actual command to execute"
    /usr/sbin/wpa_supplicant
    -c /etc/wpa_supplicant/wpa_supplicant-wlan0.conf  (config file)
    -i wlan0                                           (interface)
  
  
  [Install] - When to enable:
  ═══════════════════════════
  WantedBy=multi-user.target  "enable on normal boot"
  
  Enabling creates a symlink:
  /etc/systemd/system/multi-user.target.wants/wpa_supplicant@wlan0.service
  ──► points to /usr/lib/systemd/system/wpa_supplicant@wlan0.service
```

### systemd Network File

systemd-networkd uses `.network` files to configure network interfaces.

```ini
# /etc/systemd/network/25-wlan0.network

[Match]
Name=wlan0

[Network]
DHCP=yes
```

```
  [Match]
  Name=wlan0     "apply this config to wlan0 ONLY"
  
  [Network]
  DHCP=yes       "get IP address automatically from router"
  
  
  The "25" in filename = priority (lower = applied first)
  Common convention:
  10-19  hardware specific
  20-29  interface specific  ◄── our file
  30-39  general
  50+    defaults
  
  
  DHCP process:
  RPi: "I need an IP!"  ──►  Router: "Take 192.168.1.6"
  RPi: "Thanks!"         ──►  Router: "It's yours for 24h"
```

---

## Creating the wifi-config Recipe

### Directory Structure

```
meta-test/
└── recipes-connectivity/
    └── wifi-config/
        ├── wifi-config_1.0.bb
        └── files/
            ├── wpa_supplicant-wlan0.conf
            ├── wpa_supplicant@wlan0.service
            └── 25-wlan0.network
```

### The Recipe: wifi-config_1.0.bb

```bash
SUMMARY = "WiFi Configuration for RPi3"
DESCRIPTION = "Installs wpa_supplicant config, systemd service, and network config for WiFi"
LICENSE = "CLOSED"

SRC_URI = " \
    file://wpa_supplicant-wlan0.conf \
    file://wpa_supplicant@wlan0.service \
    file://25-wlan0.network \
"

do_install() {
    # install wpa_supplicant config (WiFi credentials)
    # 0644 permissions so we can edit it on the device
    install -d ${D}/etc/wpa_supplicant/
    install -m 0644 ${WORKDIR}/wpa_supplicant-wlan0.conf ${D}/etc/wpa_supplicant/

    # install systemd service file
    # NOTE: must be /usr/lib NOT /lib (usrmerge!)
    install -d ${D}/usr/lib/systemd/system/
    install -m 0644 ${WORKDIR}/wpa_supplicant@wlan0.service ${D}/usr/lib/systemd/system/

    # enable service on boot (create symlink = systemctl enable)
    install -d ${D}/etc/systemd/system/multi-user.target.wants/
    ln -sf /usr/lib/systemd/system/wpa_supplicant@wlan0.service \
           ${D}/etc/systemd/system/multi-user.target.wants/wpa_supplicant@wlan0.service

    # install network config (DHCP)
    install -d ${D}/etc/systemd/network/
    install -m 0644 ${WORKDIR}/25-wlan0.network ${D}/etc/systemd/network/
}

# Tell Yocto which files belong to this package
# Use directory names (includes everything inside)
FILES:${PN} = " \
    /etc/wpa_supplicant \
    /etc/systemd \
    /usr/lib/systemd \
"
```

Key points:

```
  ⚠️ usrmerge: use /usr/lib NOT /lib for systemd files
  ⚠️ FILES: use directory names WITHOUT /* (includes dir + contents)
  ⚠️ SRC_URI file:// looks in files/ folder next to the recipe
  ⚠️ 0644 for .conf so you can edit WiFi credentials on device
  ⚠️ symlink in multi-user.target.wants = "systemctl enable"
```

---

## Configuring the Image

### distro config: ayman.conf

```bash
require conf/distro/poky.conf

DISTRO = "ayman"
DISTRO_NAME = "ayman (test Project Reference Distro)"
MAINTAINER = "Ayman <abohamedayman22@gmail.com>"

DISTRO_FEATURES:append = " systemd usrmerge wifi"
VIRTUAL-RUNTIME_init_manager = "systemd"
VIRTUAL-RUNTIME_initscripts = ""
DISTRO_FEATURES_BACKFILL += " sysvinit"
LICENSE_FLAGS_ACCEPTED = "synaptics-killswitch"
```

### local.conf additions

```bash
# WiFi kernel module
KERNEL_MODULE_AUTOLOAD += "brcmfmac"

# accept firmware license
LICENSE_FLAGS_ACCEPTED += "synaptics-killswitch"
```

### ayman-image.bb WiFi packages

```bash
IMAGE_INSTALL:append = " \
    wpa-supplicant \
    linux-firmware-rpidistro-bcm43455 \
    wifi-config \
    kernel-module-brcmfmac \
    kernel-module-brcmfmac-wcc \
    kernel-module-brcmfmac-bca \
"
```

```
  Package breakdown:
  ══════════════════
  wpa-supplicant                     ──►  WiFi connection manager binary
  linux-firmware-rpidistro-bcm43455  ──►  firmware for BCM43455 chip
  wifi-config                        ──►  OUR recipe (3 config files)
  kernel-module-brcmfmac             ──►  main WiFi driver
  kernel-module-brcmfmac-wcc         ──►  WCC vendor ext (needed in kernel 6.6+!)
  kernel-module-brcmfmac-bca         ──►  BCA vendor ext
```

---

## Building and Flashing

### Build

```bash
cd ~/ITI/fady/Yocto
source poky/oe-init-build-env build-rpi
bitbake ayman-image
```

### Flash to SD card using bmaptool

```bash
cd ~/ITI/fady/Yocto/share/tmp/deploy/images/raspberrypi3-64/

# find SD card device
lsblk

# unmount SD card partitions
sudo umount /media/ayman/boot 2>/dev/null
sudo umount /media/ayman/root 2>/dev/null

# flash (replace /dev/sdX with your SD card!)
sudo bmaptool copy ayman-image-raspberrypi3-64.rootfs.wic.bz2 /dev/sdX
```

### Boot and Connect

```bash
# put SD card in RPi, power on, wait 30 seconds

# find RPi IP
nmap -sn 192.168.1.0/24

# SSH over WiFi
ssh root@192.168.1.6
```

---

## Debugging WiFi Issues

### Debug Commands (run on RPi)

```bash
# check if wlan0 interface exists
ip link show

# check kernel loaded WiFi driver
dmesg | grep -i brcm

# check if kernel module is loaded
lsmod | grep brcm

# try loading module manually
modprobe brcmfmac

# check if firmware files exist
ls /lib/firmware/brcm/

# check if wpa_supplicant is installed
which wpa_supplicant

# check config files
ls /etc/wpa_supplicant/
ls /usr/lib/systemd/system/wpa_supplicant*
ls /etc/systemd/network/

# check service status
systemctl status wpa_supplicant@wlan0

# check WiFi connection status
wpa_cli -i wlan0 status

# check WiFi logs
journalctl -u wpa_supplicant@wlan0

# check IP address
ip addr show wlan0
```

### Common Issues and Solutions

```
┌─────────────────────────────────────┬────────────────────────────────────────────┐
│  Issue                              │  Solution                                  │
├─────────────────────────────────────┼────────────────────────────────────────────┤
│  wlan0 not found                    │  kernel module not loaded                  │
│                                     │  add kernel-module-brcmfmac to IMAGE_INSTALL│
├─────────────────────────────────────┼────────────────────────────────────────────┤
│  "mod=wcc: failed 256"              │  missing brcmfmac-wcc module               │
│                                     │  add kernel-module-brcmfmac-wcc            │
├─────────────────────────────────────┼────────────────────────────────────────────┤
│  "firmware load failed"             │  wrong firmware package                    │
│                                     │  check dmesg for which firmware is needed  │
│                                     │  RPi 3 B+ needs bcm43455 not bcm43430     │
├─────────────────────────────────────┼────────────────────────────────────────────┤
│  service "could not be found"       │  .service file not in /usr/lib/systemd/    │
│                                     │  check usrmerge: use /usr/lib not /lib     │
├─────────────────────────────────────┼────────────────────────────────────────────┤
│  "installed-vs-shipped" error       │  add FILES:${PN} with correct paths        │
│                                     │  use dir names without /* wildcard         │
├─────────────────────────────────────┼────────────────────────────────────────────┤
│  "not obeying usrmerge"             │  change /lib to /usr/lib in do_install     │
├─────────────────────────────────────┼────────────────────────────────────────────┤
│  WiFi connected but no IP           │  check 25-wlan0.network exists             │
│                                     │  check systemd-networkd is running         │
├─────────────────────────────────────┼────────────────────────────────────────────┤
│  Wrong WiFi network                 │  check ssid in wpa_supplicant-wlan0.conf   │
│                                     │  ssid is case sensitive!                   │
└─────────────────────────────────────┴────────────────────────────────────────────┘
```

---

## devtool deploy-target

Once WiFi + SSH is working, you can use devtool to deploy recipe changes
directly to the running RPi without reflashing:

```bash
# on laptop: build a recipe
devtool build vsomeip

# deploy to RPi over WiFi SSH
devtool deploy-target vsomeip root@192.168.1.6

# remove deployed files
devtool undeploy-target vsomeip root@192.168.1.6
```

```
  devtool deploy-target flow:
  ═══════════════════════════
  
  Laptop                              RPi
  ┌──────────────┐    SSH + SCP    ┌──────────────┐
  │  devtool     │ ──────────────► │  receives    │
  │  deploy-     │   WiFi 📶       │  new files   │
  │  target      │                 │              │
  │  copies:     │                 │  /usr/bin/*  │
  │  bins, libs  │                 │  /usr/lib/*  │
  │  configs     │                 │  /etc/*      │
  └──────────────┘                 └──────────────┘
  
  No reflashing needed! Test changes in seconds! 🚀
```

---

## Troubleshooting Reference

### How to find correct firmware package

```bash
# Step 1: check what kernel needs (on RPi)
dmesg | grep -i brcm
# look for: "using brcm/brcmfmacXXXXX-sdio"

# Step 2: find available packages (on laptop)
bitbake linux-firmware-rpidistro -e 2>/dev/null | grep "^PACKAGES=" | tr ' ' '\n'

# Step 3: find correct kernel modules
oe-pkgdata-util list-pkgs | grep -i kernel-module-brcm
```

### How to check image contents before flashing

```bash
# mount SD card rootfs partition
sudo mount /dev/sda2 /mnt

# check files
ls /mnt/etc/wpa_supplicant/
ls /mnt/usr/lib/systemd/system/wpa_supplicant*
ls /mnt/etc/systemd/network/
ls /mnt/lib/firmware/brcm/
ls /mnt/usr/sbin/wpa_supplicant

sudo umount /mnt

```