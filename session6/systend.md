# Setting Up systemd with meta-openembedded Layer

## Overview

This guide explains how to enable `systemd` as the init manager in your
Yocto build. Since `systemd` depends on packages from `meta-openembedded`,
we need to add the required sub-layers first.

---

## Prerequisites

- Yocto Project (poky) is already cloned and working
- You know which **branch** your poky is on

```bash
cd ~/ITI/fady/Yocto/poky
git branch
# example output: * kirkstone
```

---

## Step 1: Clone meta-openembedded

> ⚠️ **Important:** The branch must match your poky branch!

```bash
cd ~/ITI/fady/Yocto

# if your poky branch is kirkstone:
git clone -b kirkstone https://github.com/openembedded/meta-openembedded.git

# if your poky branch is scarthgap:
git clone -b scarthgap https://github.com/openembedded/meta-openembedded.git
```

### Directory structure after cloning:

```
~/ITI/fady/Yocto/
├── poky/
├── meta-openembedded/
│   ├── meta-oe/              ◄── required for systemd dependencies
│   ├── meta-python/          ◄── required (dependency of meta-oe)
│   ├── meta-networking/
│   ├── meta-filesystems/
│   └── ...
├── meta-test/
└── build/
```

---

## Step 2: Initialize Build Environment

```bash
cd ~/ITI/fady/Yocto
source poky/oe-init-build-env ../build
```

---

## Step 3: Add Required Layers

```bash
bitbake-layers add-layer ../meta-openembedded/meta-oe
bitbake-layers add-layer ../meta-openembedded/meta-python
bitbake-layers add-layer ../meta-openembedded/meta-networking
bitbake-layers add-layer ../meta-openembedded/meta-filesystems
```

---

## Step 4: Verify Layers Were Added

```bash
bitbake-layers show-layers
```

Expected output:

```
layer                 path                                                          priority
==============================================================================================
meta                  /home/ayman/ITI/fady/Yocto/poky/meta                             5
meta-poky             /home/ayman/ITI/fady/Yocto/poky/meta-poky                        5
meta-yocto-bsp        /home/ayman/ITI/fady/Yocto/poky/meta-yocto-bsp                   5
meta-test             /home/ayman/ITI/fady/Yocto/meta-test                              6
meta-oe               /home/ayman/ITI/fady/Yocto/meta-openembedded/meta-oe              6
meta-python           /home/ayman/ITI/fady/Yocto/meta-openembedded/meta-python          7
meta-networking       /home/ayman/ITI/fady/Yocto/meta-openembedded/meta-networking      5
meta-filesystems      /home/ayman/ITI/fady/Yocto/meta-openembedded/meta-filesystems     6
```

---

## Step 5: Enable systemd in local.conf

```bash
nano ~/ITI/fady/Yocto/build/conf/local.conf
```

Add the following lines at the end of `local.conf`:

```bash
# ── Enable systemd as init manager ──
DISTRO_FEATURES:append = " systemd usrmerge"
VIRTUAL-RUNTIME_init_manager = "systemd"

# ── Keep sysvinit compatibility for old packages ──
DISTRO_FEATURES_BACKFILL += " sysvinit"

# ── Accept required licenses ──
LICENSE_FLAGS_ACCEPTED = "synaptics-killswitch"
```

---

## Step 6: Build Your Image

```bash
bitbake core-image-minimal
```

---

## Step 7: Verify systemd After Boot

```bash
# check PID 1
ps aux | head -5

# check systemd status
systemctl status

# check systemd version
systemd --version
```

---

## Troubleshooting

### Error: "Nothing PROVIDES 'xxx' needed by systemd"

This means you are missing a layer. Make sure `meta-oe` is added:

```bash
bitbake-layers show-layers | grep meta-oe
```

### Error: "Layer meta-python is required by meta-oe but not enabled"

Some sub-layers depend on each other. Add them in this order:

```bash
bitbake-layers add-layer ../meta-openembedded/meta-oe
bitbake-layers add-layer ../meta-openembedded/meta-python
bitbake-layers add-layer ../meta-openembedded/meta-networking
bitbake-layers add-layer ../meta-openembedded/meta-filesystems
```

### Error: "Branch mismatch"

Make sure meta-openembedded branch matches poky branch:

```bash
cd ~/ITI/fady/Yocto/poky && git branch
cd ~/ITI/fady/Yocto/meta-openembedded && git branch
# both should show the same branch name
```

---

## Summary

| Step | Command / Action                              |
|------|-----------------------------------------------|
| 1    | `git clone -b <branch> meta-openembedded`     |
| 2    | `source poky/oe-init-build-env build`         |
| 3    | `bitbake-layers add-layer` for each sub-layer |
| 4    | `bitbake-layers show-layers` to verify        |
| 5    | Edit `local.conf` to enable systemd           |
| 6    | `bitbake core-image-minimal`                  |
| 7    | Boot and verify with `systemctl status`       |
