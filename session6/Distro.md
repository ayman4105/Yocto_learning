# Creating a Custom Yocto Distro: "ayman"

## Overview

This guide explains how to create a custom Yocto distribution called `ayman`
inside the `meta-test` layer, including the full directory structure and
configuration files needed.

---

## Layer Hierarchy

```
meta-test/
├── conf/
│   ├── layer.conf                    # layer definition
│   └── distro/
│       └── ayman.conf                # our custom distro config
│
├── recipes-core/
│   └── images/
│       └── ayman-image.bb            # our custom image recipe
│
├── recipes-calc/
│   └── calc/
│       ├── calc_1.0.bb               # custom recipe
│       └── files/                    # source files for calc
│
├── recipes-dash/
│   └── dash/
│       ├── dash_1.0.bb               # custom recipe
│       └── files/                    # source files for dash
│
├── recipes-math/
│   └── math/
│       ├── math_1.0.bb               # custom recipe
│       └── files/                    # source files for math
│
├── recipes-example/
│   └── example/
│       └── example_0.1.bb            # example recipe
│
├── COPYING.MIT
└── README
```

### How the hierarchy maps to Yocto concepts:

```
meta-test/
│
├── conf/                         ◄── CONFIGURATION
│   ├── layer.conf                    tells Yocto "I am a valid layer"
│   └── distro/
│       └── ayman.conf                tells Yocto "this is a distro called ayman"
│
├── recipes-core/                 ◄── CORE RECIPES
│   └── images/
│       └── ayman-image.bb            defines what goes into the final image
│
├── recipes-calc/                 ◄── CUSTOM APP RECIPES
├── recipes-dash/                     each folder holds one application
├── recipes-math/                     with its .bb recipe and source files
└── recipes-example/
```

---

## Step 1: Create the Layer (if not already created)

```bash
cd ~/ITI/fady/Yocto
source poky/oe-init-build-env ../build

bitbake-layers create-layer ../meta-test
```

This generates the basic structure:

```
meta-test/
├── conf/
│   └── layer.conf
├── recipes-example/
│   └── example/
│       └── example_0.1.bb
├── COPYING.MIT
└── README
```

---

## Step 2: Create the Distro Directory

```bash
mkdir -p ~/ITI/fady/Yocto/meta-test/conf/distro
```

```
meta-test/
├── conf/
│   ├── layer.conf
│   └── distro/               ◄── NEW directory
```

---

## Step 3: Create the Distro Config File

```bash
nano ~/ITI/fady/Yocto/meta-test/conf/distro/ayman.conf
```

Write the following:

```bash
# ── Base: inherit everything from poky ──
require conf/distro/poky.conf

# ── Distro Identity ──
DISTRO = "ayman"
DISTRO_NAME = "ayman (test Project Reference Distro)"
MAINTAINER = "Ayman <abohamedayman22@gmail.com>"

# ── Enable systemd + merged /usr ──
DISTRO_FEATURES:append = " systemd usrmerge"

# ── Set systemd as init manager ──
VIRTUAL-RUNTIME_init_manager = "systemd"

# ── Don't install sysvinit startup scripts ──
VIRTUAL-RUNTIME_initscripts = ""

# ── Keep sysvinit compatibility for old packages ──
DISTRO_FEATURES_BACKFILL += " sysvinit"

# ── Accept required licenses ──
LICENSE_FLAGS_ACCEPTED = "synaptics-killswitch"
```

### What each line does:

```
┌─────────────────────────────────────────┬──────────────────────────────────────────┐
│  Line                                   │  Purpose                                 │
├─────────────────────────────────────────┼──────────────────────────────────────────┤
│  require conf/distro/poky.conf          │  start from poky defaults, don't start   │
│                                         │  from scratch                            │
├─────────────────────────────────────────┼──────────────────────────────────────────┤
│  DISTRO = "ayman"                       │  technical name of the distro            │
│                                         │  Yocto searches for conf/distro/ayman.conf│
├─────────────────────────────────────────┼──────────────────────────────────────────┤
│  DISTRO_NAME = "..."                    │  human-readable name shown in logs       │
├─────────────────────────────────────────┼──────────────────────────────────────────┤
│  MAINTAINER = "..."                     │  who maintains this distro               │
├─────────────────────────────────────────┼──────────────────────────────────────────┤
│  DISTRO_FEATURES:append = " systemd     │  add systemd and usrmerge to the         │
│                             usrmerge"   │  features poky already has               │
├─────────────────────────────────────────┼──────────────────────────────────────────┤
│  VIRTUAL-RUNTIME_init_manager           │  PID 1 = systemd (not sysvinit)         │
│  = "systemd"                            │                                          │
├─────────────────────────────────────────┼──────────────────────────────────────────┤
│  VIRTUAL-RUNTIME_initscripts = ""       │  don't install sysvinit scripts          │
├─────────────────────────────────────────┼──────────────────────────────────────────┤
│  DISTRO_FEATURES_BACKFILL += "sysvinit" │  keep sysvinit compat for old packages  │
│                                         │  that only have init.d scripts           │
├─────────────────────────────────────────┼──────────────────────────────────────────┤
│  LICENSE_FLAGS_ACCEPTED                 │  accept proprietary license for          │
│  = "synaptics-killswitch"              │  synaptics-killswitch package            │
└─────────────────────────────────────────┴──────────────────────────────────────────┘
```

---

## Step 4: Create the Image Recipe Directory

```bash
mkdir -p ~/ITI/fady/Yocto/meta-test/recipes-core/images
```

```bash
nano ~/ITI/fady/Yocto/meta-test/recipes-core/images/ayman-image.bb
```

---

## Step 5: Add meta-openembedded (systemd dependency)

```bash
cd ~/ITI/fady/Yocto

# check your poky branch
cd poky && git branch && cd ..

# clone with matching branch
git clone -b <your-branch> https://github.com/openembedded/meta-openembedded.git
```

---

## Step 6: Add All Required Layers

```bash
cd ~/ITI/fady/Yocto
source poky/oe-init-build-env build

# add your layer
bitbake-layers add-layer ../meta-test

# add meta-openembedded sub-layers (needed for systemd)
bitbake-layers add-layer ../meta-openembedded/meta-oe
bitbake-layers add-layer ../meta-openembedded/meta-python
bitbake-layers add-layer ../meta-openembedded/meta-networking
bitbake-layers add-layer ../meta-openembedded/meta-filesystems
```

Verify:

```bash
bitbake-layers show-layers
```

Expected:

```
layer                 path                                                           priority
===============================================================================================
meta                  /home/ayman/ITI/fady/Yocto/poky/meta                              5
meta-poky             /home/ayman/ITI/fady/Yocto/poky/meta-poky                         5
meta-yocto-bsp        /home/ayman/ITI/fady/Yocto/poky/meta-yocto-bsp                    5
meta-test             /home/ayman/ITI/fady/Yocto/meta-test                               6
meta-oe               /home/ayman/ITI/fady/Yocto/meta-openembedded/meta-oe               6
meta-python           /home/ayman/ITI/fady/Yocto/meta-openembedded/meta-python           7
meta-networking       /home/ayman/ITI/fady/Yocto/meta-openembedded/meta-networking       5
meta-filesystems      /home/ayman/ITI/fady/Yocto/meta-openembedded/meta-filesystems      6
```

---

## Step 7: Point local.conf to Your Distro

```bash
nano ~/ITI/fady/Yocto/build/conf/local.conf
```

Set the distro:

```bash
DISTRO = "ayman"
```

### How Yocto resolves this:

```
local.conf
══════════
DISTRO = "ayman"
     │
     │  Yocto searches all layers for:
     │  conf/distro/ayman.conf
     │
     ▼
meta-test/conf/distro/ayman.conf    ◄── FOUND!
     │
     │  ayman.conf says:
     │  require conf/distro/poky.conf
     │
     ▼
poky/meta-poky/conf/distro/poky.conf  ◄── loaded first as base
     │
     │  then ayman.conf overrides/appends:
     │  DISTRO_FEATURES:append = " systemd usrmerge"
     │  VIRTUAL-RUNTIME_init_manager = "systemd"
     │  ...
     ▼
Final distro config = poky defaults + ayman customizations
```

---

## Step 8: Build

```bash
cd ~/ITI/fady/Yocto
source poky/oe-init-build-env ../build
bitbake ayman-image
```

---

## Full Picture

```
~/ITI/fady/Yocto/
│
├── poky/                              (base Yocto)
│   ├── meta/                          core recipes + systemd recipe
│   ├── meta-poky/                     poky.conf (base distro we inherit from)
│   └── meta-yocto-bsp/               BSP configs
│
├── meta-openembedded/                 (extra packages)
│   ├── meta-oe/                       systemd dependencies
│   ├── meta-python/                   python dependencies
│   ├── meta-networking/               networking tools
│   └── meta-filesystems/              filesystem tools
│
├── meta-test/                         (OUR custom layer)
│   ├── conf/
│   │   ├── layer.conf                 layer definition
│   │   └── distro/
│   │       └── ayman.conf             OUR distro (systemd + usrmerge)
│   ├── recipes-core/
│   │   └── images/
│   │       └── ayman-image.bb         OUR image recipe
│   ├── recipes-calc/                  custom app
│   ├── recipes-dash/                  custom app
│   ├── recipes-math/                  custom app
│   └── recipes-example/               example app
│
└── build/
    └── conf/
        ├── local.conf                 DISTRO = "ayman"
        └── bblayers.conf              lists all layers above
```

---

## Quick Reference

| What                    | Where                                         |
|-------------------------|-----------------------------------------------|
| Distro config           | `meta-test/conf/distro/ayman.conf`            |
| Image recipe            | `meta-test/recipes-core/images/ayman-image.bb`|
| Layer config            | `meta-test/conf/layer.conf`                   |
| Build config            | `build/conf/local.conf`                       |
| Layers list             | `build/conf/bblayers.conf`                    |
| systemd recipe          | `poky/meta/recipes-core/systemd/`             |
| systemd dependencies    | `meta-openembedded/meta-oe/`                  |
