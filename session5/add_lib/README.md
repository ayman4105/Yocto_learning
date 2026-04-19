# Yocto Project: Creating a Library and Using it in Another Recipe

## 📋 Table of Contents
1. [Overview](#overview)
2. [Objective](#objective)
3. [Layer Structure](#layer-structure)
4. [The Math Library Recipe (ayman)](#the-math-library-recipe-ayman)
5. [The Calculator Recipe (calc)](#the-calculator-recipe-calc)
6. [Understanding DEPENDS and PROVIDES](#understanding-depends-and-provides)
7. [The Staging Area](#the-staging-area)
8. [Packaging and FILES Variables](#packaging-and-files-variables)
9. [Step-by-Step Commands](#step-by-step-commands)
10. [Common Errors and Solutions](#common-errors-and-solutions)

---

## Overview

This tutorial demonstrates how to:
- Create a static library in Yocto
- Create a program that depends on that library
- Understand recipe dependencies
- Add custom packages to your image

```
+------------------+         +------------------+
|   Math Library   |         |   Calculator     |
|   (libmath.a)    | <------ |   (calc)         |
|                  | depends |                  |
|  - add()         |   on    |  - main()        |
|  - sub()         |         |                  |
+------------------+         +------------------+
    Recipe: ayman               Recipe: calc
```

---

## Objective

Create two recipes:
1. **ayman** - A math library providing `add()` and `sub()` functions
2. **calc** - A calculator program that uses the library

---

## Layer Structure

```
meta-test/
├── conf/
│   └── layer.conf
├── COPYING.MIT
├── README
│
├── recipes-math/
│   └── ayman/
│       ├── ayman_1.0.bb          # Library recipe
│       └── files/
│           ├── mymath.c          # Library implementation
│           └── mymath.h          # Header file
│
└── recipes-calc/
    └── calc/
        ├── calc_1.0.bb           # Calculator recipe
        └── files/
            └── main.c            # Calculator source code
```

### Naming Conventions

| Component | Naming Rule | Example |
|-----------|-------------|---------|
| Layer | Must start with `meta-` | `meta-test` |
| Recipe folders | Should start with `recipes-` | `recipes-math` |
| Recipe file | `name_version.bb` | `ayman_1.0.bb` |
| Source folder | Usually named `files` | `files/` |

---

## The Math Library Recipe (ayman)

### Source Files

**mymath.h** (Header file - declarations)
```c
#ifndef MYMATH_H
#define MYMATH_H

int add(int a, int b);
int sub(int a, int b);

#endif
```

**mymath.c** (Implementation)
```c
#include "mymath.h"

int add(int a, int b) {
    return a + b;
}

int sub(int a, int b) {
    return a - b;
}
```

### Recipe File (ayman_1.0.bb)

```bitbake
SUMMARY = "A simple math library"
DESCRIPTION = "This is a simple math library that provides basic arithmetic operations."
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
FILES:${PN} = "${includedir}/* ${libdir}/*"
```

### Recipe Breakdown

```
+------------------------------------------------------------------+
|                    RECIPE EXPLANATION                            |
+------------------------------------------------------------------+

PROVIDES = "ayman"
    │
    └──> Tells Yocto: "I provide something called 'ayman'"
         Other recipes can depend on this name


do_compile():
=============

    ${CC} ${CFLAGS} -c ${S}/mymath.c -o ${S}/mymath.o
      │      │       │
      │      │       └──> -c = Compile only, NO linking
      │      │            (We don't have main(), so we can't link)
      │      │
      │      └──> Compiler flags (optimization, warnings, etc.)
      │
      └──> Cross Compiler (e.g., arm-poky-linux-gnueabi-gcc)


    ${AR} rcs ${S}/libmath.a ${S}/mymath.o
      │   │││
      │   ││└──> s = Create index (faster linking)
      │   │└───> c = Create archive
      │   └────> r = Replace/insert files
      │
      └──> Archive tool (creates .a static libraries)


do_install():
=============

    install -d ${D}${libdir}
              │  │    │
              │  │    └──> /usr/lib
              │  │
              │  └──> Destination root (fake root for packaging)
              │
              └──> -d = Create directory


    install -m 0644 ${S}/libmath.a ${D}${libdir}/libmath.a
               │
               └──> -m 0644 = Permissions (rw-r--r--)
```

### Compilation Process

```
+------------------------------------------------------------------+
|                    COMPILATION STAGES                            |
+------------------------------------------------------------------+

Step 1: Compile source to object file
======================================

    ┌──────────────┐         ┌──────────────┐
    │  mymath.c    │  -c     │  mymath.o    │
    │  (source)    │ ──────> │ (object file)│
    └──────────────┘         └──────────────┘


Step 2: Create static library archive
=====================================

    ┌──────────────┐         ┌──────────────┐
    │  mymath.o    │   ar    │  libmath.a   │
    │              │ ──────> │  (archive)   │
    └──────────────┘  rcs    └──────────────┘


Why -c flag is important:
=========================

    WITHOUT -c:
    gcc mymath.c -o mymath
    ❌ ERROR: "undefined reference to main"
       (Linker tries to create executable but there's no main())

    WITH -c:
    gcc -c mymath.c -o mymath.o
    ✅ SUCCESS: Creates object file, no linking attempted
```

### Static Library Naming Convention

```
lib  +  name  +  .a
 │       │       │
 │       │       └──> .a = static library (archive)
 │       │            .so = shared library
 │       │
 │       └──> Library name (your choice)
 │
 └──> MUST start with "lib" (Linux convention)

Examples:
    libmath.a      →  link with: -lmath
    libpthread.a   →  link with: -lpthread
    libssl.a       →  link with: -lssl
```

---

## The Calculator Recipe (calc)

### Source File

**main.c**
```c
#include "mymath.h"
#include <stdio.h>

int main() {
    int a = 5;
    int b = 10;
    
    int c = add(a, b);
    printf("The sum of %d and %d is %d\n", a, b, c);

    int d = sub(a, b);
    printf("The difference of %d and %d is %d\n", a, b, d);

    return 0;
}
```

### Recipe File (calc_1.0.bb)

```bitbake
SUMMARY = "A simple calculator"
DESCRIPTION = "This is a simple calculator that provides basic arithmetic operations."
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

### Recipe Breakdown

```
+------------------------------------------------------------------+
|                    RECIPE EXPLANATION                            |
+------------------------------------------------------------------+

DEPENDS = "ayman"
    │
    │   ⭐ CRITICAL! ⭐
    │
    └──> Tells Yocto:
         1. Build "ayman" recipe FIRST
         2. Make ayman's outputs available in STAGING
         3. THEN build me (calc)


do_compile():
=============

    ${CC} ${CFLAGS} ${LDFLAGS} ${S}/main.c \
        -L${STAGING_LIBDIR} \
        -I${STAGING_INCDIR} \
        -lmath \
        -o ${S}/calc


    -L${STAGING_LIBDIR}
        │
        └──> "Look for libraries in this directory"
             This is where libmath.a was staged by ayman recipe
             
             Without this: ❌ "cannot find -lmath"


    -I${STAGING_INCDIR}
        │
        └──> "Look for header files in this directory"
             This is where mymath.h was staged by ayman recipe
             
             Without this: ❌ "mymath.h: No such file or directory"


    -lmath
        │
        └──> "Link with libmath.a"
             Linker adds "lib" prefix and ".a" suffix automatically
             
             Without this: ❌ "undefined reference to 'add'"
                           ❌ "undefined reference to 'sub'"


    -o ${S}/calc
        │
        └──> Output executable name


do_install():
=============

    install -m 0755 ${S}/calc ${D}${bindir}/calc
               │                      │
               │                      └──> Install to /usr/bin/calc
               │
               └──> 0755 = rwxr-xr-x (executable)
```

---

## Understanding DEPENDS and PROVIDES

```
+------------------------------------------------------------------+
|              HOW DEPENDS AND PROVIDES WORK TOGETHER              |
+------------------------------------------------------------------+

Recipe: ayman_1.0.bb                Recipe: calc_1.0.bb
====================                ===================

PROVIDES = "ayman"                  DEPENDS = "ayman"
    │                                   │
    │                                   │
    └───────────────┬───────────────────┘
                    │
                    v
            ┌───────────────┐
            │    YOCTO      │
            │  Build System │
            └───────────────┘
                    │
                    │ Creates dependency graph
                    │
                    v
         ┌──────────────────────┐
         │   BUILD ORDER:       │
         │                      │
         │   1. Build ayman     │
         │   2. Stage outputs   │
         │   3. Build calc      │
         └──────────────────────┘


What if you forget DEPENDS = "ayman"?
=====================================

    Yocto might build calc BEFORE ayman!
    
    ❌ ERROR: mymath.h: No such file or directory
    
    Because: ayman wasn't built yet, nothing in STAGING!
```

---

## The Staging Area

```
+------------------------------------------------------------------+
|                    STAGING CONCEPT                               |
+------------------------------------------------------------------+

Problem: Each recipe builds in its OWN isolated directory
========================================================

    ┌──────────────┐         ┌──────────────┐
    │    ayman     │         │    calc      │
    │   WORKDIR    │    ❌    │   WORKDIR    │
    │              │ ──────> │              │
    │  libmath.a   │  Can't  │  needs it!   │
    │  mymath.h    │  see!   │              │
    └──────────────┘         └──────────────┘


Solution: STAGING - A shared directory
======================================

    ┌──────────────┐         ┌─────────────────────┐
    │    ayman     │  COPY   │   STAGING AREA      │
    │   (builds)   │ ──────> │   (shared)          │
    │              │         │                     │
    │  libmath.a   │         │  libmath.a ─────────────┐
    │  mymath.h    │         │  mymath.h  ─────────────┤
    └──────────────┘         └─────────────────────┘   │
                                                        │
                             ┌──────────────┐          │
                             │    calc      │    ✅    │
                             │   (builds)   │ <────────┘
                             │              │  Found it!
                             │  uses them!  │
                             └──────────────┘


Location of STAGING:
====================

    build-rpi/
    └── tmp/
        └── sysroots-components/
            └── <machine>/
                └── usr/
                    ├── lib/           ← ${STAGING_LIBDIR}
                    │   └── libmath.a
                    │
                    └── include/       ← ${STAGING_INCDIR}
                        └── mymath.h
```

---

## Packaging and FILES Variables

```
+------------------------------------------------------------------+
|                    YOCTO PACKAGES                                |
+------------------------------------------------------------------+

One recipe creates MULTIPLE packages:
=====================================

    Recipe: ayman
         │
         │ creates
         v
    ┌─────────────┬─────────────┬─────────────┬─────────────┐
    │   ayman     │  ayman-dev  │  ayman-dbg  │  ayman-doc  │
    │   (main)    │(development)│   (debug)   │    (docs)   │
    └─────────────┴─────────────┴─────────────┴─────────────┘
        ${PN}       ${PN}-dev     ${PN}-dbg     ${PN}-doc


Default contents:
=================

    ┌──────────────────┬──────────────────────────────────────┐
    │  Package         │  Default Contents                    │
    ├──────────────────┼──────────────────────────────────────┤
    │  ${PN}           │  /usr/bin/*, /usr/lib/*.so           │
    │  ${PN}-dev       │  /usr/include/*, /usr/lib/*.a        │
    │  ${PN}-dbg       │  Debug symbols                       │
    │  ${PN}-doc       │  /usr/share/doc/*                    │
    │  ${PN}-staticdev │  Static libraries (.a)               │
    └──────────────────┴──────────────────────────────────────┘


FILES variable:
===============

    FILES:${PN}-dev = "${includedir}/* ${libdir}/*"
           │
           └──> "ayman-dev package should contain:
                 - Everything in /usr/include/
                 - Everything in /usr/lib/"


Static vs Shared libraries:
===========================

    STATIC (.a):
    ┌────────────────────────────────────────────────────────┐
    │  Code is COPIED into final executable                  │
    │                                                        │
    │  calc executable = main.c code + libmath.a code        │
    │                    (all in one file!)                  │
    │                                                        │
    │  At runtime: calc works alone, doesn't need libmath.a  │
    └────────────────────────────────────────────────────────┘

    SHARED (.so):
    ┌────────────────────────────────────────────────────────┐
    │  Code is LINKED at runtime                             │
    │                                                        │
    │  calc executable = main.c code only                    │
    │                                                        │
    │  At runtime: calc needs libmath.so to exist on system  │
    └────────────────────────────────────────────────────────┘
```

---

## Step-by-Step Commands

### 1. Setup Environment

```bash
# Navigate to your Yocto directory
cd ~/ITI/fady/Yocto

# Source the build environment
source poky/oe-init-build-env ../build-rpi
```

### 2. Create Layer Structure

```bash
# Create layer directory
bitbake-layers create-layer ../meta-test
```

### This command automatically creates:
```

meta-test/
├── conf/
│   └── layer.conf           # Auto-generated
├── COPYING.MIT              # License file
├── README                   # Empty readme
└── recipes-example/
    └── example/
        └── example_0.1.bb   # Sample recipe
```

### 3. Create layer.conf

```bash
cat > meta-test/conf/layer.conf << 'EOF'
BBPATH .= ":${LAYERDIR}"

BBFILES += "${LAYERDIR}/recipes-*/*/*.bb \
            ${LAYERDIR}/recipes-*/*/*.bbappend"

BBFILE_COLLECTIONS += "meta-test"
BBFILE_PATTERN_meta-test = "^${LAYERDIR}/"
LAYERSERIES_COMPAT_meta-test = "kirkstone dunfell"
EOF
```

### 4. Create Math Library Files

```bash
# Create mymath.h
cat > meta-test/recipes-math/ayman/files/mymath.h << 'EOF'
#ifndef MYMATH_H
#define MYMATH_H

int add(int a, int b);
int sub(int a, int b);

#endif
EOF

# Create mymath.c
cat > meta-test/recipes-math/ayman/files/mymath.c << 'EOF'
#include "mymath.h"

int add(int a, int b) {
    return a + b;
}

int sub(int a, int b) {
    return a - b;
}
EOF

# Create ayman recipe
cat > meta-test/recipes-math/ayman/ayman_1.0.bb << 'EOF'
SUMMARY = "A simple math library"
DESCRIPTION = "This is a simple math library that provides basic arithmetic operations."
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
FILES:${PN} = "${includedir}/* ${libdir}/*"
EOF
```

### 5. Create Calculator Files

```bash
# Create main.c
cat > meta-test/recipes-calc/calc/files/main.c << 'EOF'
#include "mymath.h"
#include <stdio.h>

int main() {
    int a = 5;
    int b = 10;
    
    int c = add(a, b);
    printf("The sum of %d and %d is %d\n", a, b, c);

    int d = sub(a, b);
    printf("The difference of %d and %d is %d\n", a, b, d);

    return 0;
}
EOF

# Create calc recipe
cat > meta-test/recipes-calc/calc/calc_1.0.bb << 'EOF'
SUMMARY = "A simple calculator"
DESCRIPTION = "This is a simple calculator that provides basic arithmetic operations."
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
EOF
```

### 6. Add Layer to Build

```bash
# Navigate to build directory
cd ~/ITI/fady/Yocto/build-rpi

# Add the layer
bitbake-layers add-layer ../meta-test

# Verify layer was added
bitbake-layers show-layers
```

### 7. Add calc to Image

```bash
# Edit local.conf
echo 'IMAGE_INSTALL:append = " calc"' >> conf/local.conf
```

### 8. Build

```bash
# Build just the calc recipe (for testing)
bitbake calc

# Or build the entire image
bitbake core-image-minimal
```

### 9. Test on Target

```bash
# After flashing image to SD card and booting Raspberry Pi:
calc

# Expected output:
# The sum of 5 and 10 is 15
# The difference of 5 and 10 is -5
```

---

## Common Errors and Solutions

### Error 1: "mymath.h: No such file or directory"

```
Cause: Missing -I${STAGING_INCDIR} in do_compile()
       OR: Missing DEPENDS = "ayman"
       OR: ayman recipe didn't install header correctly

Solution: 
1. Verify DEPENDS = "ayman" exists in calc recipe
2. Verify -I${STAGING_INCDIR} is in compile command
3. Verify ayman's do_install() copies mymath.h to ${D}${includedir}
```

### Error 2: "cannot find -lmath"

```
Cause: Missing -L${STAGING_LIBDIR} in do_compile()
       OR: ayman recipe didn't install library correctly

Solution:
1. Verify -L${STAGING_LIBDIR} is in compile command
2. Verify ayman's do_install() copies libmath.a to ${D}${libdir}
```

### Error 3: "undefined reference to 'add'"

```
Cause: Missing -lmath in do_compile()
       OR: Library not linked properly

Solution:
1. Verify -lmath is in compile command
2. Make sure -lmath comes AFTER source files in command
```

### Error 4: Recipe not found

```
Cause: Layer not added to bblayers.conf

Solution:
$ bitbake-layers add-layer /path/to/meta-test
```

### Error 5: Package not in image

```
Cause: Missing IMAGE_INSTALL:append

Solution:
Add to conf/local.conf:
IMAGE_INSTALL:append = " calc"

Note: There MUST be a space before "calc"!
```

---

## Quick Reference

| Variable | Meaning | Example Value |
|----------|---------|---------------|
| `${PN}` | Package Name | `calc` |
| `${S}` | Source directory | `${WORKDIR}` |
| `${D}` | Destination (fake root) | `/path/to/image/` |
| `${bindir}` | Binary directory | `/usr/bin` |
| `${libdir}` | Library directory | `/usr/lib` |
| `${includedir}` | Include directory | `/usr/include` |
| `${STAGING_LIBDIR}` | Staged libraries | `/path/to/staging/usr/lib` |
| `${STAGING_INCDIR}` | Staged headers | `/path/to/staging/usr/include` |
| `${CC}` | C Compiler | `arm-poky-linux-gnueabi-gcc` |
| `${AR}` | Archive tool | `arm-poky-linux-gnueabi-ar` |
| `${CFLAGS}` | Compiler flags | `-O2 -pipe ...` |
| `${LDFLAGS}` | Linker flags | `-Wl,-O1 ...` |

---

## Summary

```
+------------------------------------------------------------------+
|                    COMPLETE WORKFLOW                             |
+------------------------------------------------------------------+

1. Create Layer (meta-test/)
   └── Contains recipes and layer.conf

2. Create Library Recipe (ayman_1.0.bb)
   └── PROVIDES = "ayman"
   └── Creates libmath.a and mymath.h
   └── Installs to ${libdir} and ${includedir}

3. Create Program Recipe (calc_1.0.bb)
   └── DEPENDS = "ayman"
   └── Uses -L${STAGING_LIBDIR} -I${STAGING_INCDIR} -lmath
   └── Creates calc executable

4. Add Layer
   └── bitbake-layers add-layer meta-test

5. Add to Image
   └── IMAGE_INSTALL:append = " calc"

6. Build
   └── bitbake core-image-minimal

7. Test
   └── Run "calc" on target device
```


