
# Session 3 - Patch Fix: Questions & Answers

## Q1: Why is the WORKDIR path `cortexa53-poky-linux` and not `raspberrypi3_64-poky-linux`?

The path `tmp/work/cortexa53-poky-linux/hello-ayman/1.0/` is made of 3 parts:

| Part | Value | Source |
|------|-------|--------|
| `cortexa53` | TUNE_PKGARCH (CPU type) | `MACHINE → DEFAULTTUNE → TUNE_PKGARCH` |
| `poky` | DISTRO | `local.conf` |
| `linux` | TARGET_OS | Default |

`hello-ayman` is a **normal C program** — it doesn't need anything
specific from the Raspberry Pi board. It only needs an ARM Cortex-A53 CPU.

So Yocto puts it at the **CPU level**, not the **machine level**.

---

## Q2: What goes in `raspberrypi3_64-poky-linux` then?

Yocto organizes `tmp/work/` into **4 levels**:

```
tmp/work/
├── raspberrypi3_64-poky-linux/   ← MACHINE-specific
│   e.g., linux-raspberrypi, u-boot, rpi-config
│
├── cortexa53-poky-linux/         ← CPU level (DEFAULT)
│   e.g., hello-ayman, busybox, bash, nano
│
├── all-poky-linux/               ← No binary (arch-independent)
│   e.g., base-files, config scripts
│
└── x86_64-linux/                 ← Native tools (run on your PC)
    e.g., cmake-native, python3-native
```

Only recipes that **need something from the board** go to
`raspberrypi3_64-poky-linux/` (like the kernel or bootloader).

---

## Q3: How does bitbake decide which level automatically?

bitbake sets a variable called `PACKAGE_ARCH` based on what the recipe uses:

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

You can verify:

```bash
bitbake -e hello-ayman | grep "^PACKAGE_ARCH="
# Output: PACKAGE_ARCH="cortexa53"

bitbake -e linux-raspberrypi | grep "^PACKAGE_ARCH="
# Output: PACKAGE_ARCH="raspberrypi3_64"
```

> You never set this manually. bitbake reads your recipe and decides.

---

## Q4: Why do we copy the file before fixing it? (`cp main.c main.c.orig`)

Because `diff` needs **two files** to compare:

```
Step 1: cp git/main.c git/main.c.orig   ← Save the broken version
Step 2: nano git/main.c                 ← Fix the file
Step 3: diff -u git/main.c.orig git/main.c > fix.patch

        main.c.orig (broken)  vs  main.c (fixed)
        ┌──────────────┐         ┌──────────────┐
        │ intmain() {  │  diff   │ int main() { │
        └──────────────┘  ──►    └──────────────┘
                          fix.patch
```

> **Important:** Always `diff -u old new` — old file first, new file second.
> If reversed, the patch will **undo** your fix instead of applying it!

---

## Q5: What does `.orig` mean?

`.orig` = **original**. It's just a naming convention, not a special extension.

| Name | Works? |
|------|--------|
| `main.c.orig` | ✅ Most common convention |
| `main.c.old` | ✅ |
| `main.c.backup` | ✅ |

---

## Q6: Can I name the patch file anything?

The **name** can be anything, but the **extension** must be `.patch` or `.diff`:

| Filename | Recognized by bitbake? |
|----------|----------------------|
| `fix.patch` | ✅ Applied in do_patch |
| `my-fix.diff` | ✅ Applied in do_patch |
| `fix.txt` | ❌ Treated as regular file |

> bitbake sees `.patch` or `.diff` in SRC_URI → applies it in `do_patch`.
> Any other extension → just copies to WORKDIR.

---

## Q7: Why move the patch from tmp/ to the layer (meta-main)?

### Reason 1: tmp/ is temporary

```
tmp/work/.../hello-ayman/1.0/fix.patch
             ↑
  bitbake -c cleanall deletes this entire folder!
  The patch would be lost.
```

### Reason 2: bitbake searches in the layer, not in tmp/

When the recipe says `SRC_URI = "file://fix.patch"`:

```
bitbake searches HERE:
  meta-main/recipes-test/hello-ayman/hello-ayman/fix.patch  ✅

bitbake does NOT search here:
  tmp/work/.../hello-ayman/1.0/fix.patch  ❌
```

---

## Q8: Which is better — `hello-ayman/` or `files/` folder for patches?

```
Option 1 (Preferred — Yocto convention):
  hello-ayman/
  ├── hello-ayman.bb
  └── hello-ayman/         ← Named after the recipe
      └── fix.patch

Option 2 (Works but not standard):
  hello-ayman/
  ├── hello-ayman.bb
  └── files/               ← Generic name
      └── fix.patch
```

`hello-ayman/` is better because it supports **version-specific** files:

```
hello-ayman/
├── hello-ayman_1.0.bb
├── hello-ayman_2.0.bb
├── hello-ayman/           ← Shared patches (any version)
│   └── common.patch
├── hello-ayman-1.0/       ← Only for version 1.0
│   └── old-fix.patch
└── hello-ayman-2.0/       ← Only for version 2.0
    └── new-fix.patch
```

---

## Q9: What is `${CFLAGS}` and `${LDFLAGS}` in do_compile?

```
${CC} ${CFLAGS} ${LDFLAGS} ${S}/main.c -o ayman
──┬── ───┬────  ───┬─────
  │      │         │
  │      │         └── LDFLAGS = Linker Flags
  │      │             (how to link libraries)
  │      │
  │      └── CFLAGS = Compiler Flags
  │          (optimization, debug, target arch)
  │
  └── CC = Cross Compiler
      (e.g., aarch64-poky-linux-gcc)
```

| Variable | Contains | Example Values |
|----------|----------|---------------|
| `${CC}` | Cross compiler | `aarch64-poky-linux-gcc` |
| `${CFLAGS}` | Compile flags | `-O2 -pipe -g -march=armv8-a+crc --sysroot=...` |
| `${LDFLAGS}` | Linker flags | `-Wl,-O1 -Wl,--hash-style=gnu --sysroot=...` |

> Without `${CFLAGS}` and `${LDFLAGS}` the build works but
> may produce QA warnings and miss optimizations.

---

## Q10: How does bitbake search for the patch file? (18 paths!)

From the `log.do_patch`, bitbake searches from **most specific to most generic**:

```
Search order:
─────────────
hello-ayman-1.0/poky/              ← version + distro
hello-ayman/poky/                  ← recipe + distro
files/poky/                        ← files + distro

hello-ayman-1.0/raspberrypi3-64/   ← version + machine
hello-ayman/raspberrypi3-64/       ← recipe + machine
files/raspberrypi3-64/             ← files + machine

hello-ayman-1.0/aarch64/           ← version + arch
hello-ayman/aarch64/               ← recipe + arch
files/aarch64/                     ← files + arch

hello-ayman-1.0/                   ← version (generic)
hello-ayman/                       ← recipe (generic) ✅ FOUND
files/                             ← files (generic)
```

> This means you can have **different patches per machine**:
> `hello-ayman/raspberrypi3-64/fix.patch` → only for RPI3
> `hello-ayman/fix.patch` → for all machines

---

## Q11: What is the QA warning about `Upstream-Status`?

```
QA Issue: Missing Upstream-Status in patch [patch-status]
```

This is a **warning, not an error**. Yocto recommends adding a header
to your patch file to indicate if the fix was sent to the original project:

| Status | Meaning |
|--------|---------|
| `Upstream-Status: Pending` | Not yet sent to upstream |
| `Upstream-Status: Submitted` | Pull request sent |
| `Upstream-Status: Accepted` | Merged by upstream |
| `Upstream-Status: Backport` | Taken from newer version |
| `Upstream-Status: Inappropriate` | Not applicable to upstream |

---

## Summary: Complete Patch Flow

```
1. Build fails (intmain error)
        │
        ▼
2. Go to WORKDIR, find the broken code
        │
        ▼
3. cp main.c main.c.orig (save original)
        │
        ▼
4. nano main.c (fix the code)
        │
        ▼
5. diff -u main.c.orig main.c > fix.patch
        │
        ▼
6. Copy fix.patch to meta-main/recipes-test/hello-ayman/hello-ayman/
        │
        ▼
7. Add "file://fix.patch" to SRC_URI in the recipe
        │
        ▼
8. bitbake hello-ayman -c cleanall
        │
        ▼
9. bitbake hello-ayman → SUCCESS 🎉

Task order:
  do_fetch → do_unpack → do_patch (fix applied) → do_compile ✅ → do_install
```
