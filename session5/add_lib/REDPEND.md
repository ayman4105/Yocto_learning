# Yocto: PROVIDES, PREFERRED_PROVIDER, DEPENDS, and RDEPENDS

## Table of Contents
1. [DEPENDS vs RDEPENDS](#depends-vs-rdepends)
2. [PROVIDES and PREFERRED_PROVIDER](#provides-and-preferred_provider)

---

## DEPENDS vs RDEPENDS

### Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│   DEPENDS   = Build-time dependency (Compilation time)         │
│                                                                 │
│   RDEPENDS  = Runtime dependency (Execution time)               │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Visual Comparison

```
         BUILD TIME                         RUNTIME
        (on your PC)                   (on Raspberry Pi)
              │                               │
              v                               v
┌─────────────────────────────┐  ┌─────────────────────────────┐
│                             │  │                             │
│  Compiling needs:           │  │  Running needs:             │
│                             │  │                             │
│  - Headers (.h)             │  │  - Shared libraries (.so)   │
│  - Libraries (.a, .so)      │  │  - Other programs           │
│  - Compilers (gcc)          │  │  - Config files             │
│                             │  │                             │
│  DEPENDS = "recipe"         │  │  RDEPENDS:${PN} = "recipe"  │
│                             │  │                             │
└─────────────────────────────┘  └─────────────────────────────┘
```

---

### Static vs Shared Library

**Case 1: Static Library (.a)**

```
┌─────────────────────────────────────────────────────────────────┐
│  libmath.a code is COPIED into calc executable                  │
│                                                                 │
│  calc = main.c code + libmath.a code (embedded!)                │
└─────────────────────────────────────────────────────────────────┘

    ┌──────────┐
    │   calc   │     Runs alone! No dependencies!
    │  ┌────┐  │
    │  │math│  │     libmath.a code is INSIDE
    │  │code│  │
    │  └────┘  │
    └──────────┘

Build time:     DEPENDS = "ayman"        ✅ Need library to compile
Runtime:        RDEPENDS = ???           ❌ NOT needed!
```

**Case 2: Shared Library (.so)**

```
┌─────────────────────────────────────────────────────────────────┐
│  libmath.so code is LINKED at runtime                           │
│                                                                 │
│  calc looks for libmath.so when running                         │
└─────────────────────────────────────────────────────────────────┘

    ┌──────────┐         ┌──────────────┐
    │   calc   │ ──────> │ libmath.so   │
    │          │  needs  │              │
    │ (small)  │   at    │ (must exist  │
    │          │ runtime │  on target!) │
    └──────────┘         └──────────────┘

Build time:     DEPENDS = "ayman"        ✅ Need library to compile
Runtime:        RDEPENDS:${PN} = "ayman" ✅ Need library to RUN!
```

---

### What Happens Without RDEPENDS?

```
Without RDEPENDS (shared library):
==================================

Image contents:
    /usr/bin/calc        ✅ exists
    /usr/lib/libmath.so  ❌ NOT included!

On target:
    $ calc
    ERROR: libmath.so: cannot open shared object file


With RDEPENDS:${PN} = "ayman":
==============================

Image contents:
    /usr/bin/calc        ✅ exists
    /usr/lib/libmath.so  ✅ included!

On target:
    $ calc
    The sum of 5 and 10 is 15    ✅ Works!
```

---

### Example Recipes

**Static Library (DEPENDS only)**

```bitbake
# calc_1.0.bb - using static library

DEPENDS = "ayman"

# No RDEPENDS needed - code is embedded in executable
```

**Shared Library (DEPENDS + RDEPENDS)**

```bitbake
# calc_1.0.bb - using shared library

DEPENDS = "ayman"                # Build-time
RDEPENDS:${PN} = "ayman"         # Runtime
```

**Python Script (RDEPENDS only)**

```bitbake
# myscript_1.0.bb

# No DEPENDS - nothing to compile
RDEPENDS:${PN} = "python3"       # Python needed to run
```

---

### Quick Decision Guide

```
Using static library (.a)?
└──> DEPENDS only

Using shared library (.so)?
└──> DEPENDS + RDEPENDS

Script that needs interpreter?
└──> RDEPENDS only

Program that calls other programs?
└──> RDEPENDS for those programs
```

---

### Syntax

```bitbake
# Build-time dependencies
DEPENDS = "recipe1 recipe2 recipe3"

# Runtime dependencies (note: needs package name)
RDEPENDS:${PN} = "recipe1 recipe2 recipe3"
```

---

## PROVIDES and PREFERRED_PROVIDER

### The Problem

Multiple recipes providing same functionality:

```
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│   mathlib   │  │   methics   │  │    real     │
│             │  │             │  │             │
│ PROVIDES=   │  │ PROVIDES=   │  │ PROVIDES=   │
│   "math"    │  │   "math"    │  │   "math"    │
└──────┬──────┘  └──────┬──────┘  └──────┬──────┘
       │                │                │
       └────────────────┼────────────────┘
                        │
                        v
              ┌─────────────────┐
              │      calc       │
              │ DEPENDS="math"  │
              │ Which one??? 🤔 │
              └─────────────────┘
```

Error:
```
ERROR: Multiple providers are available for "math"
```

---

### The Solution

In `conf/local.conf`:

```bitbake
PREFERRED_PROVIDER_math = "mathlib"
```

Result:

```
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│   mathlib   │  │   methics   │  │    real     │
│ ✅ SELECTED │  │ ❌ IGNORED  │  │ ❌ IGNORED  │
└─────────────┘  └─────────────┘  └─────────────┘
```

---

### Syntax

```bitbake
PREFERRED_PROVIDER_<provides-name> = "<recipe-name>"
```

Examples:

```bitbake
PREFERRED_PROVIDER_math = "mathlib"
PREFERRED_PROVIDER_virtual/kernel = "linux-raspberrypi"
PREFERRED_PROVIDER_jpeg = "libjpeg-turbo"
```

---

### Why Use PROVIDES?

```
┌─────────────────────────────────────────────────────────────────┐
│  Switch provider WITHOUT editing recipe files!                  │
│                                                                 │
│  # For embedded (small):                                        │
│  PREFERRED_PROVIDER_math = "mathlib"                            │
│                                                                 │
│  # For desktop (full features):                                 │
│  PREFERRED_PROVIDER_math = "real"                               │
│                                                                 │
│  calc_1.0.bb stays the same!                                    │
└─────────────────────────────────────────────────────────────────┘
```

---

## Complete Summary

```
┌──────────────────────┬──────────────────────────────────────────┐
│  Variable            │  Purpose                                 │
├──────────────────────┼──────────────────────────────────────────┤
│  DEPENDS             │  Build-time dependency                   │
│                      │  "I need this to COMPILE"                │
├──────────────────────┼──────────────────────────────────────────┤
│  RDEPENDS:${PN}      │  Runtime dependency                      │
│                      │  "I need this to RUN"                    │
├──────────────────────┼──────────────────────────────────────────┤
│  PROVIDES            │  "I can satisfy this dependency"         │
│                      │  Multiple recipes can provide same thing │
├──────────────────────┼──────────────────────────────────────────┤
│  PREFERRED_PROVIDER  │  "When multiple options exist,           │
│                      │   use THIS specific recipe"              │
└──────────────────────┴──────────────────────────────────────────┘
```

---

## Real-World Examples

```bitbake
# Kernel selection
PREFERRED_PROVIDER_virtual/kernel = "linux-raspberrypi"

# C Library selection
PREFERRED_PROVIDER_virtual/libc = "glibc"

# SSL library selection
PREFERRED_PROVIDER_ssl = "openssl"

# Application with shared library
DEPENDS = "openssl zlib"
RDEPENDS:${PN} = "openssl zlib"

# Shell script needing bash
RDEPENDS:${PN} = "bash"

# Python application
RDEPENDS:${PN} = "python3 python3-json python3-requests"
```
