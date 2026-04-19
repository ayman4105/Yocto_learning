# Yocto: PROVIDES and PREFERRED_PROVIDER

## Overview

When multiple recipes can provide the same functionality, Yocto needs to know which one to use.

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
              │                 │
              │ DEPENDS="math"  │
              │                 │
              │ Which one??? 🤔 │
              └─────────────────┘
```

---

## The Problem

```
$ bitbake calc

ERROR: Multiple providers are available for "math"
  - mathlib
  - methics  
  - real

Consider defining PREFERRED_PROVIDER_math
```

---

## The Solution

In `conf/local.conf`:

```bitbake
PREFERRED_PROVIDER_math = "mathlib"
```

Now Yocto knows:

```
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│   mathlib   │  │   methics   │  │    real     │
│             │  │             │  │             │
│ ✅ SELECTED │  │ ❌ IGNORED  │  │ ❌ IGNORED  │
└─────────────┘  └─────────────┘  └─────────────┘
```

---

## Syntax

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

## Example Recipes

**mathlib_1.0.bb**
```bitbake
SUMMARY = "Basic math library"
LICENSE = "CLOSED"
PROVIDES = "math"
# ... rest of recipe
```

**methics_2.0.bb**
```bitbake
SUMMARY = "Standard math library"
LICENSE = "CLOSED"
PROVIDES = "math"
# ... rest of recipe
```

**calc_1.0.bb**
```bitbake
SUMMARY = "Calculator"
LICENSE = "CLOSED"
DEPENDS = "math"    # Generic name, not specific recipe
# ... rest of recipe
```

**conf/local.conf**
```bitbake
PREFERRED_PROVIDER_math = "mathlib"
```

---

## Why Use This?

```
┌─────────────────────────────────────────────────────────────┐
│  Switch provider WITHOUT editing recipe files!              │
│                                                             │
│  # For embedded (small):                                    │
│  PREFERRED_PROVIDER_math = "mathlib"                        │
│                                                             │
│  # For desktop (full features):                             │
│  PREFERRED_PROVIDER_math = "real"                           │
│                                                             │
│  calc_1.0.bb stays the same!                                │
└─────────────────────────────────────────────────────────────┘
```

---

## Quick Reference

| Variable | Purpose |
|----------|---------|
| `PROVIDES` | "I can satisfy this dependency" |
| `DEPENDS` | "I need this to build" |
| `PREFERRED_PROVIDER` | "Use this recipe when multiple options exist" |

---

## Common Real-World Examples

```bitbake
# Kernel
PREFERRED_PROVIDER_virtual/kernel = "linux-raspberrypi"

# C Library
PREFERRED_PROVIDER_virtual/libc = "glibc"

# SSL
PREFERRED_PROVIDER_ssl = "openssl"

# JPEG
PREFERRED_PROVIDER_jpeg = "libjpeg-turbo"
```
