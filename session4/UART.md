# UART Setup for Raspberry Pi in Yocto

---

## 1. How UART Works on Raspberry Pi

Raspberry Pi 3 has two UARTs:

| UART | Device | Default Use |
|------|--------|-------------|
| PL011 (full) | `ttyAMA0` | Bluetooth |
| Mini UART | `ttyS0` / `serial0` | GPIO 14,15 |

---

## 2. Minimal local.conf for UART

```bash
ENABLE_UART = "1"
```

That's it. The recipe `rpi-cmdline.bb` handles everything else automatically.

---

## 3. How It Works — rpi-cmdline.bb Explained

### Recipe Header

```bash
INHIBIT_DEFAULT_DEPS = "1"
```
This recipe only creates a text file (cmdline.txt).
It does not compile any C code.
So it tells BitBake: "don't add gcc and glibc as dependencies, I don't need them."

```bash
inherit deploy nopackages
```
- `deploy` → gives the recipe a `do_deploy()` task to copy files to the deploy directory
- `nopackages` → this recipe does not produce any packages (no .rpm, .deb, .ipk). It just creates a boot file that goes directly on the SD card.

---

### USB Power Management

```bash
CMDLINE_DWC_OTG ?= "dwc_otg.lpm_enable=0"
```
Disables USB power saving on RPi.
Some USB devices disconnect randomly when power management is enabled.
`?=` means this is a default value — you can override it in `local.conf` if needed.

---

### Root Filesystem Settings

```bash
CMDLINE_ROOT_FSTYPE ?= "rootfstype=ext4"
```
Tells the kernel: "the root filesystem format is ext4."

```bash
CMDLINE_ROOT_PARTITION ?= "/dev/mmcblk0p2"
```
Tells the kernel: "the root filesystem is on SD card partition 2."

```
SD Card Layout:
  mmcblk0p1 → Boot partition (kernel, cmdline.txt, config.txt)
  mmcblk0p2 → Root filesystem (/bin, /etc, /usr, /home)
```

```bash
CMDLINE_ROOTFS ?= "root=${CMDLINE_ROOT_PARTITION} ${CMDLINE_ROOT_FSTYPE} rootwait"
```
Combines the two above into one string:
`root=/dev/mmcblk0p2 rootfstype=ext4 rootwait`

`rootwait` tells the kernel to wait until the SD card is ready before trying to mount it.
Without it, the kernel may fail to boot because the SD card isn't ready yet.

---

### Serial Console

```bash
CMDLINE_SERIAL ?= "${@oe.utils.conditional("ENABLE_UART", "1", "console=serial0,115200", "", d)}"
```
This is a Python conditional inside the recipe:
- If `ENABLE_UART == "1"` → returns `"console=serial0,115200"`
- If `ENABLE_UART` is not set → returns `""` (empty, no serial console)

When you set `ENABLE_UART = "1"` in `local.conf`, this line automatically
adds serial console support to `cmdline.txt`.

---

### Comparison with U-Boot

Both do the same thing — pass boot parameters to the Linux kernel:

| Method | Command |
|--------|---------|
| U-Boot | `setenv bootargs "root=/dev/mmcblk0p2 rootfstype=ext4 rootwait console=ttyAMA0,115200"` |
| RPi cmdline.txt | `root=/dev/mmcblk0p2 rootfstype=ext4 rootwait console=serial0,115200` |

After boot, both produce the same result:
```bash
cat /proc/cmdline
# root=/dev/mmcblk0p2 rootfstype=ext4 rootwait console=serial0,115200
```

---

## 4. Build and Flash Commands

```bash
# Setup environment
cd ~/ITI/fady/Yocto
source oe-init-build-env build-rpi

# Build
bitbake core-image-minimal

# Find image
ls tmp/deploy/images/raspberrypi3-64/*.wic*

# Find SD card
lsblk

# Unmount SD card
sudo umount /dev/mmcblk0p1
sudo umount /dev/mmcblk0p2

# Flash
cd tmp/deploy/images/raspberrypi3-64/
sudo bmaptool copy core-image-minimal-raspberrypi3-64.rootfs.wic.bz2 /dev/mmcblk0

# Safely remove
sudo sync
sudo eject /dev/mmcblk0
```

---

## 5. Connect and Login

```bash
# UART serial connection
picocom -b 115200 /dev/ttyUSB0

# Login
# user: root
# password: (none)

# Verify kernel command line
cat /proc/cmdline
