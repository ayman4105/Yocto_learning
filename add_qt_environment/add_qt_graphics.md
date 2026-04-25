# Qt6 & Graphics Setup on Raspberry Pi 5 with Yocto (Scarthgap)

## Table of Contents

- [Overview](#overview)
- [Graphics Stack Architecture](#graphics-stack-architecture)
- [Understanding Each Layer](#understanding-each-layer)
  - [Wayland vs X11](#wayland-vs-x11)
  - [Weston Compositor](#weston-compositor)
  - [Mesa and OpenGL](#mesa-and-opengl)
  - [DRM/KMS](#drmkms)
  - [Qt6 Packages](#qt6-packages)
- [Configuration Changes](#configuration-changes)
  - [Distro Config](#distro-config)
  - [local.conf](#localconf)
  - [Image Recipe](#image-recipe)
- [Package Breakdown](#package-breakdown)

---

## Overview

This guide explains what we added to our Yocto build to support Qt6 GUI
applications with graphics on Raspberry Pi 5.

```
  Goal:
  ═════
  
  ┌──────────────────────────────────┐
  │  HDMI Screen                     │
  │  ┌────────────────────────────┐  │
  │  │                            │  │
  │  │  🌤️ Weather App (Qt6)     │  │
  │  │  Cairo: 25°C               │  │
  │  │                            │  │
  │  └────────────────────────────┘  │
  │                                  │
  │  Running on RPi 5 with Yocto     │
  └──────────────────────────────────┘
```

---

## Graphics Stack Architecture

To display a Qt app on screen, we need 4 layers working together:

```
  ┌─────────────────────────────────────────┐
  │  Layer 4: Qt App (WeatherApp)           │
  │  Draws the UI (buttons, text, images)   │
  ├─────────────────────────────────────────┤
  │  Layer 3: Wayland + Weston              │
  │  Compositor: organizes windows and      │
  │  displays them on screen                │
  ├─────────────────────────────────────────┤
  │  Layer 2: Mesa (OpenGL ES)              │
  │  Translates drawing commands to GPU     │
  ├─────────────────────────────────────────┤
  │  Layer 1: Hardware (RPi 5 GPU)          │
  │  VideoCore VII - renders to screen      │
  └─────────────────────────────────────────┘
```

```
  Without any layer:
  ═══════════════════
  
  Without Layer 1 (GPU)     → no rendering at all
  Without Layer 2 (Mesa)    → kernel can't talk to GPU
  Without Layer 3 (Weston)  → app can't display on screen
  Without Layer 4 (Qt)      → no UI at all
```

---

## Understanding Each Layer

### Wayland vs X11

```
  Linux has 2 display systems:
  ════════════════════════════
  
  X11 (old - from 1984! 😱)
  ═════════════════════════
  App ──► X11 Server ──► GPU ──► Screen
  
  - Complex and large
  - Slow (everything goes through server)
  - Weak security (any app can see other windows)
  
  
  Wayland (modern - 2012 ✅)
  ══════════════════════════
  App ──► Compositor ──► GPU ──► Screen
  
  - Simple and fast
  - Each app is isolated
  - Perfect for Embedded ← what we use!
```

### Weston Compositor

A compositor collects windows from all apps and draws them on screen.

```
  Without Compositor:
  ═══════════════════
  
  App 1: "I'll draw here!"
  App 2: "No, I'll draw over you!"
  App 3: "Me too!"
  
  Result: chaos! 💥 all drawing over each other
  
  
  With Compositor:
  ════════════════
  
  App 1 ──► draws on buffer 1  ──┐
  App 2 ──► draws on buffer 2  ──┼──► Compositor ──► Screen
  App 3 ──► draws on buffer 3  ──┘    (collects & arranges)
```

```
  What the compositor does:
  ═════════════════════════
  
  1️⃣  Receives drawing from each app
  2️⃣  Decides which window is on top (z-order)
  3️⃣  Sets position of each window
  4️⃣  Applies effects (shadow, transparency)
  5️⃣  Routes input to correct app
      Mouse click ──► "that's on Weather App"
      ──► sends click to Weather App only
  6️⃣  Combines everything and renders to screen
```

```
  ┌──────────────────────────────────┐
  │  Screen                          │
  │                                  │
  │  ┌──────────────┐               │
  │  │  Weather App │  ← buffer 1   │
  │  │  🌤️ 25°C    │               │
  │  │              │  ┌──────────┐ │
  │  └──────────────┘  │ Terminal │ │
  │                    │ $ ls     │ │
  │                    │ ← buf 2  │ │
  │                    └──────────┘ │
  │                                  │
  │  Weston arranged all of this ▲   │
  └──────────────────────────────────┘
```

### Mesa and OpenGL

```
  OpenGL = language to talk to GPU
  ═════════════════════════════════
  
  glClear(...)          ← "clear the screen"
  glDrawTriangle(...)   ← "draw a triangle"
  glSetColor(RED)       ← "color is red"
  
  On Embedded we use OpenGL ES (lightweight version)
  
  
  Mesa = translator between OpenGL and GPU
  ═════════════════════════════════════════
  
  Without Mesa:
  Qt App: "glDrawTriangle!"
  GPU:    "I don't understand! 😐"
  
  With Mesa:
  Qt App: "glDrawTriangle!"
      │
      ▼
  Mesa: "I'll translate..."
      │
      ▼
  GPU Driver: "Here you go GPU!"
      │
      ▼
  GPU: "Done! 🔺"
```

### DRM/KMS

```
  DRM = Direct Rendering Manager
  KMS = Kernel Mode Setting
  ══════════════════════════
  
  Part of Linux kernel:
  
  DRM: gives apps direct access to GPU
  KMS: sets up display (resolution, refresh rate)
  
  Example:
  KMS: "Screen is 1920x1080 @ 60Hz"
  DRM: "App 1 wants to draw → go ahead GPU"
```

```
  Full stack for WeatherApp:
  ══════════════════════════
  
  Main.qml (UI)
       │
       ▼
  WeatherAPI.cpp (C++ backend)
       │
       ▼
  Qt Quick (qtdeclarative) → QML engine
       │
       ▼
  Qt Network (qtbase) → HTTP requests
       │
       ▼
  Qt GUI (qtbase) → rendering
       │
       ▼
  qtwayland → connects Qt to Wayland
       │
       ▼
  Weston → compositor
       │
       ▼
  Mesa → OpenGL → GPU → Screen 🖥️
```

---

## Configuration Changes

### Distro Config

File: `meta-test/conf/distro/Hyper-NOVA-Cockpit.conf`

```bash
require conf/distro/poky.conf

DISTRO = "Hyper-NOVA-Cockpit"
DISTRO_NAME = "Hyper-NOVA-Cockpit (RPi5 Custom Distro)"
MAINTAINER = "Ayman <abohamedayman22@gmail.com>"

DISTRO_FEATURES:append = " systemd usrmerge wifi pam wayland opengl"
VIRTUAL-RUNTIME_init_manager = "systemd"
VIRTUAL-RUNTIME_initscripts = ""
DISTRO_FEATURES_BACKFILL += " sysvinit"
LICENSE_FLAGS_ACCEPTED = "synaptics-killswitch"
```

```
  New DISTRO_FEATURES:
  ═════════════════════
  
  wayland  ← display protocol (tells recipes to build Wayland support)
  opengl   ← graphics support (tells recipes to build OpenGL support)
  
  
  How DISTRO_FEATURES work:
  ═════════════════════════
  
  Recipes check DISTRO_FEATURES:
  
  if "wayland" in DISTRO_FEATURES:
      → build Wayland support ✅
  else:
      → don't build Wayland ❌
  
  DISTRO_FEATURES does NOT install packages!
  It tells recipes WHAT to build support for.
```

### local.conf

```bash
MACHINE_FEATURES:append = " vc4graphics"
DISABLE_VC4GRAPHICS = "0"
```

```
  RPi has 2 graphics options:
  ═══════════════════════════
  
  ┌──────────────────────────────────────────┐
  │  Option 1: Broadcom Proprietary (old) ❌  │
  │  - Closed source                          │
  │  - Limited                                │
  │  - No Wayland support                     │
  └──────────────────────────────────────────┘
  
  ┌──────────────────────────────────────────┐
  │  Option 2: vc4graphics (Mesa) ✅          │
  │  - Open source                            │
  │  - Supports OpenGL ES + Wayland           │
  │  - Faster and better                      │
  └──────────────────────────────────────────┘
  
  
  What vc4graphics does:
  ══════════════════════
  1. Loads vc4 kernel driver
  2. Adds Mesa (OpenGL) to image
  3. Configures device tree for GPU
```

```
  local.conf vs distro.conf - who puts what?
  ═══════════════════════════════════════════
  
  distro.conf
  ───────────
  General policies for ANY machine
  "I want my system to support systemd, wifi, wayland"
  
  local.conf
  ──────────
  Settings specific to THIS machine
  "I'm building for RPi 5, use vc4graphics"
```

### Image Recipe

File: `meta-test/recipes-core/images/hyper-nova-cockpit-image.bb`

```bash
# Graphics + Wayland
IMAGE_INSTALL:append = " wayland weston weston-init"

# Qt6 packages
IMAGE_INSTALL:append = " qtbase"
IMAGE_INSTALL:append = " qtdeclarative"
IMAGE_INSTALL:append = " qtwayland"
IMAGE_INSTALL:append = " qtquick3d"
IMAGE_INSTALL:append = " qttools"
```

---

## Package Breakdown

```
  Graphics packages:
  ══════════════════
  
  wayland       ← core Wayland library (the protocol)
  weston        ← the compositor (draws windows)
  weston-init   ← systemd service (auto-starts weston on boot)
  
  
  Qt6 packages:
  ═════════════
  
  qtbase        ← Qt foundation (Core + GUI + Network + Widgets)
  qtdeclarative ← QML engine (runs .qml files)
  qtwayland     ← plugin to connect Qt to Wayland
                   without it Qt can't display on screen!
  qtquick3d     ← 3D support in QML (optional)
  qttools       ← development tools (optional)
  
  
  Relationships:
  ══════════════
  
  WeatherApp (your code)
       │ uses
       ▼
  qtdeclarative (QML engine runs Main.qml)
       │ needs
       ▼
  qtbase (Core + GUI + Network)
       │ connects via
       ▼
  qtwayland (Qt ──► Wayland protocol)
       │ talks to
       ▼
  weston (compositor draws on screen)
       │ uses
       ▼
  Mesa/OpenGL (talks to GPU)
       │
       ▼
  GPU → Screen 🖥️
```
