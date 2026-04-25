
# Creating a Custom Yocto Image: "ayman-image"

## Overview

This guide explains how to create a custom image recipe called `ayman-image`
inside the `meta-test` layer, what each line in the recipe does, and how
it relates to the layer hierarchy.

---

## Layer Structure

```
meta-test/
├── conf/
│   ├── distro/
│   │   └── ayman.conf              # custom distro config
│   └── layer.conf                   # layer definition
│
├── recipes-core/
│   └── images/
│       └── ayman-image.bb           # ◄── OUR CUSTOM IMAGE (this guide)
│
├── recipes-calc/
│   └── calc/
│       ├── calc_1.0.bb              # custom C application
│       └── files/                   # source code for calc
│
├── recipes-dash/
│   └── dash/
│       ├── dash_1.0.bb              # custom application
│       └── files/                   # source code for dash
│
├── recipes-math/
│   └── math/
│       ├── math_1.0.bb              # custom math library
│       └── files/                   # source code for math
│
├── recipes-example/
│   └── example/
│       └── example_0.1.bb           # example recipe
│
├── COPYING.MIT
└── README
```

---

## The Image Recipe: `ayman-image.bb`

### Full File

```bash
SUMMARY = "Ayman Image"
DESCRIPTION = "This is a simple image processing library that provides basic image manipulation operations."

LICENSE = "CLOSED"

# include core boot packages
IMAGE_INSTALL = "packagegroup-core-boot ${CORE_IMAGE_EXTRA_INSTALL}"

# inherit core-image class for image building functionality
inherit core-image

# add image features
IMAGE_FEATURES += "debug-tweaks ssh-server-dropbear"

# add extra packages
IMAGE_INSTALL:append = " packagegroup-core-boot vim python3 calc tcpdump"
IMAGE_INSTALL:append = " hello-ayman"
# IMAGE_INSTALL:append = " dash"
IMAGE_INSTALL:append = " math-dev"
```

---

### Line-by-Line Explanation

#### Section 1: Metadata

```bash
SUMMARY = "Ayman Image"
DESCRIPTION = "This is a simple image processing library that provides basic image manipulation operations."
LICENSE = "CLOSED"
```

| Variable      | Purpose                                          |
|---------------|--------------------------------------------------|
| `SUMMARY`     | short one-line description of the image          |
| `DESCRIPTION` | longer description of what this image is         |
| `LICENSE`     | `CLOSED` means proprietary / no open license     |

---

#### Section 2: Base Packages

```bash
IMAGE_INSTALL = "packagegroup-core-boot ${CORE_IMAGE_EXTRA_INSTALL}"
```

```
IMAGE_INSTALL = "packagegroup-core-boot ${CORE_IMAGE_EXTRA_INSTALL}"
                 │                       │
                 │                       └── any extra packages user adds
                 │                           from local.conf via this variable
                 │
                 └── minimal packages needed to BOOT Linux:
                     - busybox (basic commands)
                     - init manager (systemd in our case)
                     - base-files
                     - base-passwd
```

> ⚠️ **Note:** Using `=` (not `:append`) here means this REPLACES
> the default IMAGE_INSTALL. This is intentional because we want
> full control over what goes into the image.

---

#### Section 3: Inherit core-image

```bash
inherit core-image
```

```
inherit core-image
        │
        └── this gives your recipe all the "magic" to:
            - build a rootfs
            - create image files (.ext4, .wic, .tar.gz)
            - process IMAGE_FEATURES
            - process IMAGE_INSTALL
            - handle users, permissions, etc.

Without this line, Yocto doesn't know HOW to build an image!
```

---

#### Section 4: Image Features

```bash
IMAGE_FEATURES += "debug-tweaks ssh-server-dropbear"
```

```
IMAGE_FEATURES += "debug-tweaks ssh-server-dropbear"
                    │                │
                    │                └── install dropbear SSH server
                    │                    (lightweight SSH for embedded)
                    │                    allows you to SSH into the device
                    │
                    └── development/debug helpers:
                        - allows root login without password
                        - allows empty password
                        - sets debug flags
```

| Feature                | What it installs / does                        |
|------------------------|------------------------------------------------|
| `debug-tweaks`         | root login with no password (dev only!)        |
| `ssh-server-dropbear`  | lightweight SSH server (alternative: openssh)  |

> ⚠️ **Warning:** Never use `debug-tweaks` in production images!
> It allows anyone to login as root without a password.

---

#### Section 5: Extra Packages

```bash
IMAGE_INSTALL:append = " packagegroup-core-boot vim python3 calc tcpdump"
IMAGE_INSTALL:append = " hello-ayman"
# IMAGE_INSTALL:append = " dash"
IMAGE_INSTALL:append = " math-dev"
```

```
IMAGE_INSTALL:append = " packagegroup-core-boot vim python3 calc tcpdump"
                          │                      │    │       │     │
                          │                      │    │       │     └── network packet analyzer
                          │                      │    │       └── our custom calc app (recipes-calc/)
                          │                      │    └── python3 interpreter
                          │                      └── text editor
                          └── boot packages (already set above, duplicated here)

IMAGE_INSTALL:append = " hello-ayman"
                          │
                          └── custom hello app (from another layer or recipe)

# IMAGE_INSTALL:append = " dash"
# │
# └── COMMENTED OUT: dash app is NOT included in the image

IMAGE_INSTALL:append = " math-dev"
                          │
                          └── our custom math library DEV package
                              (from recipes-math/)
                              "-dev" means it includes headers + .so symlinks
                              (useful for on-device development)
```

> ⚠️ **Important:** Notice the SPACE before each package name in `:append`
> ```
> ✅  IMAGE_INSTALL:append = " vim"      (space before vim)
> ❌  IMAGE_INSTALL:append = "vim"       (no space = broken!)
> ```

---

## How Packages Map to Recipes in meta-test

```
IMAGE_INSTALL package    ──►    Recipe in meta-test
════════════════════════════════════════════════════

calc                     ──►    recipes-calc/calc/calc_1.0.bb
                                └── files/   (source code)

dash (commented out)     ──►    recipes-dash/dash/dash_1.0.bb
                                └── files/   (source code)

math-dev                 ──►    recipes-math/math/math_1.0.bb
                                └── files/   (source code)

hello-ayman              ──►    (from another layer or recipe)

vim, python3, tcpdump    ──►    (from poky/meta or meta-oe)
packagegroup-core-boot   ──►    (from poky/meta)
```

```
  When you run: bitbake ayman-image
  
  Yocto reads ayman-image.bb
       │
       ├── finds IMAGE_INSTALL packages
       │    │
       │    ├── calc ──────────► builds recipes-calc/calc/calc_1.0.bb
       │    ├── math-dev ──────► builds recipes-math/math/math_1.0.bb
       │    ├── vim ───────────► builds from poky/meta
       │    ├── python3 ───────► builds from poky/meta
       │    ├── tcpdump ───────► builds from poky/meta or meta-oe
       │    └── hello-ayman ───► builds from its recipe
       │
       ├── finds IMAGE_FEATURES
       │    │
       │    ├── debug-tweaks ──► configures root with no password
       │    └── ssh-server-dropbear ──► installs dropbear SSH
       │
       └── inherits core-image
            │
            └── builds rootfs and generates image files
                (.ext4, .wic, .tar.gz, etc.)
```

---

## Steps to Create This Image

### Step 1: Create the directory

```bash
mkdir -p ~/ITI/fady/Yocto/meta-test/recipes-core/images
```

### Step 2: Create the image recipe

```bash
nano ~/ITI/fady/Yocto/meta-test/recipes-core/images/ayman-image.bb
```

Paste the recipe content shown above.

### Step 3: Make sure your custom recipes exist

```bash
# verify your custom recipes are in place
ls ~/ITI/fady/Yocto/meta-test/recipes-calc/calc/calc_1.0.bb
ls ~/ITI/fady/Yocto/meta-test/recipes-math/math/math_1.0.bb
```

### Step 4: Build the image

```bash
cd ~/ITI/fady/Yocto
source poky/oe-init-build-env ../build
bitbake ayman-image
```

### Step 5: Find the output image

```bash
ls tmp/deploy/images/<MACHINE>/ayman-image-<MACHINE>.*
```

---

## Summary Table

| What                       | Where / Value                                      |
|----------------------------|----------------------------------------------------|
| Image recipe               | `recipes-core/images/ayman-image.bb`               |
| Base class                 | `inherit core-image`                               |
| Boot packages              | `packagegroup-core-boot`                           |
| SSH access                 | `ssh-server-dropbear` (lightweight)                |
| Dev/debug access           | `debug-tweaks` (root no password)                  |
| Text editor                | `vim`                                              |
| Scripting language         | `python3`                                          |
| Network debugging          | `tcpdump`                                          |
| Custom apps from meta-test | `calc`, `math-dev`                                 |
| Custom apps from elsewhere | `hello-ayman`                                      |
| Disabled packages          | `dash` (commented out)                             |
| Build command              | `bitbake ayman-image`                              |

---

## IMAGE_INSTALL vs IMAGE_FEATURES Quick Reference

```
┌──────────────────────┬───────────────────────────────────────────────────┐
│  IMAGE_INSTALL       │  individual packages to install                   │
│                      │  example: vim, python3, calc                      │
│                      │  (you name the exact package)                     │
├──────────────────────┼───────────────────────────────────────────────────┤
│  IMAGE_FEATURES      │  high-level features that install GROUPS          │
│                      │  of packages + do configuration                   │
│                      │  example: ssh-server-dropbear installs dropbear   │
│                      │  AND configures it to start on boot               │
└──────────────────────┴───────────────────────────────────────────────────┘
