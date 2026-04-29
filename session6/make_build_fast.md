# Yocto Build Performance & Troubleshooting Guide

## Table of Contents

- [System Resources Optimization](#system-resources-optimization)
- [Adding Swap Space](#adding-swap-space)
- [Build Performance Tuning](#build-performance-tuning)
- [Fixing vsomeip Compilation Error (GCC 13)](#fixing-vsomeip-compilation-error-gcc-13)
- [Fixing installed-vs-shipped Error](#fixing-installed-vs-shipped-error)
- [devtool Quick Reference](#devtool-quick-reference)

---

## System Resources Optimization

### Check Your System Resources

```bash
# check number of CPU cores
nproc

# check RAM and swap
free -h
```

Example output:

```
               total        used        free      shared  buff/cache   available
Mem:            15Gi       9.8Gi       488Mi       2.3Gi       7.4Gi       5.6Gi
Swap:             0B          0B          0B
```

### Problem: No Swap + High Thread Count = System Freeze

```
BB_NUMBER_THREADS = "8"    ──►  8 recipes building AT THE SAME TIME
PARALLEL_MAKE = "-j 8"    ──►  each recipe uses 8 CPU cores to compile

Worst case = 8 x 8 = 64 parallel compile processes!

With only 5.6GB available RAM and NO swap:
──► System FREEZES completely
──► Cannot use laptop during build
```

---

## Adding Swap Space

### Create 8GB Swap File

```bash
# create 8GB swap file
sudo fallocate -l 8G /swapfile

# set correct permissions
sudo chmod 600 /swapfile

# format as swap
sudo mkswap /swapfile

# enable swap NOW
sudo swapon /swapfile

# verify swap is active
free -h
```

Expected output after enabling swap:

```
               total        used        free      shared  buff/cache   available
Mem:            15Gi       9.8Gi       488Mi       2.3Gi       7.4Gi       5.6Gi
Swap:          8.0Gi          0B       8.0Gi       ◄── SWAP IS NOW ACTIVE!
```

### Make Swap Permanent (survive reboot)

```bash
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

---

## Build Performance Tuning

### Recommended Settings in local.conf

```bash
nano ~/ITI/fady/Yocto/build-rpi/conf/local.conf
```

### Thread Count Reference Table

```
┌────────────────┬────────────────────┬──────────────────┬──────────────────────┐
│ System Specs   │ BB_NUMBER_THREADS  │ PARALLEL_MAKE    │ Result               │
├────────────────┼────────────────────┼──────────────────┼──────────────────────┤
│ 4 cores, 8GB   │ "2"                │ "-j 2"           │ safe, slow           │
│ 8 cores, 8GB   │ "4"                │ "-j 4"           │ safe, medium         │
│ 8 cores, 16GB  │ "6"                │ "-j 6"           │ balanced ✅          │
│ 12 cores, 32GB │ "8"                │ "-j 8"           │ fast                 │
│ 16 cores, 32GB │ "12"               │ "-j 12"          │ very fast            │
└────────────────┴────────────────────┴──────────────────┴──────────────────────┘
```

### For 8 cores + 16GB RAM + 8GB Swap (Recommended)

```bash
BB_NUMBER_THREADS = "6"
PARALLEL_MAKE = "-j 6"
```

### Build Priority Options

```bash
# Option 1: Full speed (laptop might lag)
bitbake ayman-image

# Option 2: Medium priority (balanced - RECOMMENDED)
nice -n 10 bitbake ayman-image

# Option 3: Lowest priority (laptop smooth but build VERY slow)
ionice -c 3 nice -n 19 bitbake ayman-image
```

```
nice values:
════════════
nice -n 0    ──►  normal priority (default)
nice -n 10   ──►  medium-low priority (balanced) ✅
nice -n 19   ──►  lowest priority (very slow build) 🐢
```

---
