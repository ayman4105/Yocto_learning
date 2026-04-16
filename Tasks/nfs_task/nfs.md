# NFS Root Boot for Raspberry Pi 3 with Yocto

A complete guide to boot Raspberry Pi 3 root filesystem over NFS network
instead of SD card.

---

## Table of Contents

- [1. What is NFS Root Boot](#1-what-is-nfs-root-boot)
- [2. Network Setup](#2-network-setup)
- [3. NFS Server Setup on PC](#3-nfs-server-setup-on-pc)
- [4. Yocto Configuration](#4-yocto-configuration)
- [5. How the Kernel Knows About NFS](#5-how-the-kernel-knows-about-nfs)
- [6. cmdline.txt Parameters Explained](#6-cmdlinetxt-parameters-explained)
- [7. Comparison with U-Boot](#7-comparison-with-u-boot)
- [8. Build and Flash Commands](#8-build-and-flash-commands)
- [9. Verify NFS Boot](#9-verify-nfs-boot)

---

## 1. What is NFS Root Boot

Instead of booting the root filesystem from SD card, the Raspberry Pi
mounts its root filesystem from a folder on your PC over the network.

```
WITHOUT NFS:
  Edit code → Build → Flash SD card → Boot RPi (slow, 5-10 min every time)

WITH NFS:
  Edit code → Build → Extract rootfs → Reboot RPi (fast, seconds)
```

The SD card only holds the kernel and boot files.
The actual root filesystem (`/bin`, `/etc`, `/usr`, etc.) lives on
your PC and is shared over Ethernet using NFS protocol.

```
+============+                  +==========+
|  PC        |  Ethernet cable  |  RPi     |
|  (NFS      |←────────────────→|          |
|   Server)  |                  |          |
|            |                  |          |
| /srv/nfs/  |  NFS mount       | / (root) |
| rootfs/    |=================>|          |
| ├── bin/   |                  | ├── bin/ |
| ├── etc/   |                  | ├── etc/ |
| └── usr/   |                  | └── usr/ |
+============+                  +==========+
```

---

## 2. Network Setup

Direct Ethernet connection between PC and RPi (no router needed):

```
PC (eno1)                    RPi (eth0)
192.168.2.1  ←── Ethernet ──→  192.168.2.50
                  cable
```

Static IPs are used because there is no DHCP server on this direct connection.

| Device | Interface | IP Address | Netmask |
|--------|-----------|------------|---------|
| PC | eno1 | 192.168.2.1 | 255.255.255.0 |
| RPi | eth0 | 192.168.2.50 | 255.255.255.0 |

---

## 3. NFS Server Setup on PC

### Install NFS Server

```bash
sudo apt install nfs-kernel-server
```

### Configure Exports

```bash
sudo vim /etc/exports
```

Add this line:

```
/srv/nfs/rootfs 192.168.2.0/24(rw,sync,no_subtree_check,no_root_squash)
```

| Option | Meaning |
|--------|---------|
| `/srv/nfs/rootfs` | Folder to share |
| `192.168.2.0/24` | Allow any device on 192.168.2.x network |
| `rw` | Read and write access |
| `sync` | Write data to disk before responding |
| `no_subtree_check` | Faster NFS performance |
| `no_root_squash` | RPi root user has real root permissions on PC |

### Apply and Start

```bash
sudo exportfs -a
sudo systemctl restart nfs-kernel-server
```

### Configure PC Ethernet Interface

```bash
sudo ip addr add 192.168.2.1/24 dev eno1
sudo ip link set eno1 up
```

---

## 4. Yocto Configuration

### local.conf

```bash
CONF_VERSION = "2"

# Custom packages
IMAGE_INSTALL:append = " hello-ayman"
IMAGE_INSTALL:append = " dash"

# UART
ENABLE_UART = "1"


# Kernel cmdline for NFS Root Boot
CMDLINE_ROOTFS = "root=/dev/nfs rootfstype=nfs nfsroot=192.168.2.1:/srv/nfs/rootfs,nfsvers=3,tcp rw ip=192.168.2.50::192.168.2.1:255.255.255.0::eth0:off"
```

### How local.conf Overrides the Recipe

The recipe `rpi-cmdline.bb` has this default:

```bash
CMDLINE_ROOT_PARTITION ?= "/dev/mmcblk0p2"
CMDLINE_ROOT_FSTYPE ?= "rootfstype=ext4"
CMDLINE_ROOTFS ?= "root=${CMDLINE_ROOT_PARTITION} ${CMDLINE_ROOT_FSTYPE} rootwait"
```

The `?=` means "default value — use only if not set elsewhere."

When you set `CMDLINE_ROOTFS = "..."` with `=` in local.conf,
your value wins and replaces the entire default.

```
Recipe (rpi-cmdline.bb):
  CMDLINE_ROOTFS ?= "root=/dev/mmcblk0p2 rootfstype=ext4 rootwait"
                 ^^
                 ?= (weak, default)

local.conf:
  CMDLINE_ROOTFS = "root=/dev/nfs rootfstype=nfs nfsroot=..."
                 ^
                 = (strong, wins!)

Result: root=/dev/nfs (NFS boot, not SD card)
```

## 5. How the Kernel Knows About NFS

Everything is passed through `cmdline.txt`. The kernel reads it at boot time.

```
Boot sequence:

Step 1: Power ON
Step 2: RPi firmware reads cmdline.txt from SD card
Step 3: RPi firmware loads kernel and passes cmdline.txt parameters
Step 4: Kernel reads "ip=192.168.2.50::..." → configures eth0
Step 5: Kernel reads "root=/dev/nfs" → knows root is on network
Step 6: Kernel reads "nfsroot=192.168.2.1:/srv/nfs/rootfs" → connects to PC
Step 7: Kernel mounts /srv/nfs/rootfs as /
Step 8: Kernel runs /sbin/init
Step 9: System boots, login prompt appears
```

---

## 6. cmdline.txt Parameters Explained

Final cmdline.txt content:

```
dwc_otg.lpm_enable=0 console=serial0,115200 root=/dev/nfs rootfstype=nfs nfsroot=192.168.2.1:/srv/nfs/rootfs,nfsvers=3,tcp rw ip=192.168.2.50::192.168.2.1:255.255.255.0::eth0:off net.ifnames=0
```

| Parameter | Meaning |
|-----------|---------|
| `root=/dev/nfs` | Keyword telling kernel: "root filesystem is on NFS network" (NOT a file path) |
| `rootfstype=nfs` | Filesystem type is NFS (not ext4) |
| `nfsroot=192.168.2.1:/srv/nfs/rootfs` | NFS server IP and path to rootfs folder on server |
| `nfsvers=3` | Use NFS version 3 |
| `tcp` | Use TCP protocol for NFS |
| `rw` | Mount root filesystem as read-write |
| `ip=192.168.2.50` | RPi IP address |
| `::192.168.2.1` | Gateway (PC IP) |
| `:255.255.255.0` | Netmask |
| `::eth0` | Network interface |
| `:off` | Do not use DHCP (static IP) |

### IP Parameter Format

```
ip=<rpi-ip>::<gateway>:<netmask>::<interface>:<dhcp>
ip=192.168.2.50::192.168.2.1:255.255.255.0::eth0:off
```

---

## 7. Comparison with U-Boot

Both U-Boot and RPi firmware do the same thing — pass boot parameters to the kernel.

| | U-Boot | RPi Firmware |
|--|--------|-------------|
| Stored in | `bootargs` env variable | `cmdline.txt` file |
| Set by | `setenv bootargs "..."` | Yocto build (local.conf) |
| Read by | U-Boot bootloader | RPi firmware (start.elf) |
| Passed to | Linux Kernel | Linux Kernel |

U-Boot NFS example:

```
U-Boot => setenv bootargs "root=/dev/nfs rootfstype=nfs nfsroot=192.168.2.1:/srv/nfs/rootfs,nfsvers=3,tcp rw ip=192.168.2.50::192.168.2.1:255.255.255.0::eth0:off console=ttyAMA0,115200"
U-Boot => saveenv
U-Boot => boot
```

RPi Yocto equivalent (local.conf):

```bash
CMDLINE_ROOTFS = "root=/dev/nfs rootfstype=nfs nfsroot=192.168.2.1:/srv/nfs/rootfs,nfsvers=3,tcp rw ip=192.168.2.50::192.168.2.1:255.255.255.0::eth0:off"
```

The kernel does not care who passes the parameters.
After boot, both produce the same result:

```bash
cat /proc/cmdline
# same output regardless of U-Boot or RPi firmware
```

---

## 8. Build and Flash Commands

### Setup Environment

```bash
cd ~/ITI/fady/Yocto
source oe-init-build-env build-rpi
```

### Build Image

```bash
bitbake core-image-minimal
```

### Extract rootfs to NFS Folder

```bash
sudo mkdir -p /srv/nfs/rootfs
sudo tar -xjf ~/ITI/fady/Yocto/share/tmp/deploy/images/raspberrypi3-64/core-image-minimal-raspberrypi3-64.rootfs.tar.bz2 -C /srv/nfs/rootfs
```

### Verify rootfs

```bash
ls /srv/nfs/rootfs/
# Expected: bin boot dev etc home lib media mnt proc run sbin sys tmp usr var
```

### Start NFS Server

```bash
sudo exportfs -a
sudo systemctl restart nfs-kernel-server
sudo exportfs -v
# Expected: /srv/nfs/rootfs 192.168.2.0/24(rw,sync,no_root_squash,no_subtree_check)
```

### Setup PC Ethernet

```bash
sudo ip addr add 192.168.2.1/24 dev eno1
sudo ip link set eno1 up
ip addr show eno1 | grep inet
# Expected: inet 192.168.2.1/24
```

### Flash SD Card

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
```

### Verify cmdline.txt Before Booting

```bash
sudo mount /dev/mmcblk0p1 /mnt
cat /mnt/cmdline.txt
# Should contain: root=/dev/nfs nfsroot=192.168.2.1:/srv/nfs/rootfs
sudo umount /mnt
```

### Boot RPi

```
1. Insert SD card into RPi
2. Connect Ethernet cable between PC (eno1) and RPi
3. Connect UART serial cable
4. Open serial terminal:
```

```bash
picocom -b 115200 /dev/ttyUSB0
```

```
5. Power ON RPi
6. Login: root (no password)
```

---

## 9. Verify NFS Boot

After login on RPi, run these commands:

```bash
# Check kernel command line
cat /proc/cmdline
# Should show: root=/dev/nfs nfsroot=192.168.2.1:/srv/nfs/rootfs

# Check mount
mount | grep root
# NFS boot: 192.168.2.1:/srv/nfs/rootfs on / type nfs
# SD boot:  /dev/mmcblk0p2 on / type ext4

# Check IP
ip addr show eth0
# Should show: inet 192.168.2.50/24

# Check your custom binary
ls /usr/ayman/bin/
# Should show: dash
```

### Updating rootfs After Code Changes

After rebuilding, just extract the new rootfs and reboot RPi:

```bash
# On PC
sudo rm -rf /srv/nfs/rootfs/*
sudo tar -xjf ~/ITI/fady/Yocto/share/tmp/deploy/images/raspberrypi3-64/core-image-minimal-raspberrypi3-64.rootfs.tar.bz2 -C /srv/nfs/rootfs

# Reboot RPi (no need to reflash SD card!)
