# 🟢 Yocto Team — Month 1 Task Tracker

## Overview

| Info | Details |
|------|---------|
| **Team Member** | Ayman (Solo) |
| **Duration** | 3.5 Weeks (18 Working Days) |
| **Daily Hours** | ~3 hours/day |
| **Days/Week** | 5 days/week |
| **Total Hours** | ~54 hours |
| **Target** | Custom Yocto Image (RPi5) + VSOMEIP Consumer Services |

---

## Week 1 — Build Environment + First Boot + Custom Layer

### Day 1 — Setup Build Environment
- [ ] Task 1: Initialize build environment
- [ ] Task 2: Configure `local.conf`
- [ ] Task 3: Configure `bblayers.conf`
- [ ] Task 4: Check system resources (disk, RAM, swap)
- [ ] Task 5: Start `bitbake core-image-minimal`

### Day 2 — Verify Build + Flash + Boot
- [ ] Task 6: Check build output
- [ ] Task 7: Flash image to SD card
- [ ] Task 8: Boot RPi5 and verify login

### Day 3 — Create Custom Layer
- [ ] Task 9: Create `meta-ivi` directory structure
- [ ] Task 10: Write `layer.conf`
- [ ] Task 11: Write `ivi-distro.conf`
- [ ] Task 12: Write basic `ivi-image.bb`
- [ ] Task 13: Add `meta-ivi` to `bblayers.conf` and set DISTRO

### Day 4 — Test Custom Layer
- [ ] Task 14: Run `bitbake-layers show-layers`
- [ ] Task 15: Fix any layer errors
- [ ] Task 16: Build `ivi-image`
- [ ] Task 17: Fix build errors if any
- [ ] Task 18: Flash, boot, and verify custom image

### Day 5 — Validation + Git Setup
- [ ] Task 19: Test SSH access
- [ ] Task 20: Test systemd is running
- [ ] Task 21: Test NetworkManager
- [ ] Task 22: Fix any remaining issues
- [ ] Task 23: Git init and first commit
- [ ] Task 24: Document Week 1 progress and issues

---

## Week 2 — External Layers + Qt6 + Audio + Python

### Day 6 — Clone and Integrate External Layers
- [ ] Task 25: Check Poky branch
- [ ] Task 26: Clone `meta-openembedded`
- [ ] Task 27: Clone `meta-qt6`
- [ ] Task 28: Clone any extra required layers
- [ ] Task 29: Update `bblayers.conf` with new layers
- [ ] Task 30: Verify all layers with `bitbake-layers show-layers`
- [ ] Task 31: Fix missing dependency errors

### Day 7 — Add Qt6 + Wayland to Image
- [ ] Task 32: Add RPi5 graphics config (vc4graphics)
- [ ] Task 33: Add Qt6 license acceptance
- [ ] Task 34: Add Weston and Wayland packages to image recipe
- [ ] Task 35: Add Qt6 packages to image recipe
- [ ] Task 36: Start build `bitbake ivi-image`
- [ ] Task 37: Fix any immediate build errors

### Day 8 — Test Qt6 on RPi5
- [ ] Task 38: Verify build success
- [ ] Task 39: Flash and boot RPi5
- [ ] Task 40: Verify Weston is running
- [ ] Task 41: Verify Qt6 libraries exist on target
- [ ] Task 42: Run simple QML test application
- [ ] Task 43: Debug Qt or Wayland issues if any

### Day 9 — Add Audio + Python + Boost
- [ ] Task 44: Add audio packages to image recipe
- [ ] Task 45: Add Python packages to image recipe
- [ ] Task 46: Add Boost library to image recipe
- [ ] Task 47: Rebuild image
- [ ] Task 48: Fix build errors if any

### Day 10 — Full Verification + Image v1.0
- [ ] Task 49: Flash and boot new image
- [ ] Task 50: Test audio output
- [ ] Task 51: Test Python3 and numpy
- [ ] Task 52: Test Boost libraries exist
- [ ] Task 53: Run full verification checklist
- [ ] Task 54: Fix any remaining issues
- [ ] Task 55: Git commit — Image v1.0

---

## Week 3 — VSOMEIP Recipe + Consumer Services

### Day 11 — VSOMEIP Research + Recipe
- [ ] Task 56: Study VSOMEIP basics (service, consumer, event, method)
- [ ] Task 57: Check VSOMEIP GitHub and pick stable release
- [ ] Task 58: Write Yocto recipe for VSOMEIP
- [ ] Task 59: Build standalone with `bitbake vsomeip`
- [ ] Task 60: Fix recipe build errors

### Day 12 — VSOMEIP Configuration
- [ ] Task 61: Add VSOMEIP to image, rebuild, and verify on RPi5
- [ ] Task 62: Define Service IDs (Vehicle, AC, Sensor)
- [ ] Task 63: Define Event IDs and Method IDs for each service
- [ ] Task 64: Write VSOMEIP JSON configuration file
- [ ] Task 65: Deploy config to target and test routing manager

### Day 13 — Vehicle Status Consumer
- [ ] Task 66: Study VSOMEIP consumer example code
- [ ] Task 67: Write Vehicle Status Consumer (speed, RPM, engine temp)
- [ ] Task 68: Write CMakeLists.txt for consumer project
- [ ] Task 69: Cross-compile or build on target
- [ ] Task 70: Run on RPi5 and verify no crash

### Day 14 — AC Control + Sensor Consumers
- [ ] Task 71: Write AC Control Consumer (receive status + send commands)
- [ ] Task 72: Write Sensor Data Consumer (ultrasonic + camera)
- [ ] Task 73: Cross-compile both consumers
- [ ] Task 74: Run all 3 consumers simultaneously on RPi5
- [ ] Task 75: Fix any runtime issues

### Day 15 — Mock Provider + Integration Test
- [ ] Task 76: Write Mock QNX Provider (fake data for all 3 services)
- [ ] Task 77: Cross-compile and deploy mock provider
- [ ] Task 78: Run mock provider and all 3 consumers together
- [ ] Task 79: Verify Vehicle Status Consumer receives data
- [ ] Task 80: Verify AC Control Consumer sends and receives
- [ ] Task 81: Verify Sensor Data Consumer receives data
- [ ] Task 82: Fix any communication issues

---

## Week 3.5 — Testing + Optimization + Documentation

### Day 16 — Stress Testing
- [ ] Task 83: Run long-duration test (1 hour continuous)
- [ ] Task 84: Monitor CPU and memory stability
- [ ] Task 85: Kill provider and test consumer error detection
- [ ] Task 86: Restart provider and test automatic reconnection
- [ ] Task 87: Fix any stability issues found

### Day 17 — Boot Time + Image Optimization
- [ ] Task 88: Analyze boot time with `systemd-analyze`
- [ ] Task 89: Identify slow services with `systemd-analyze blame`
- [ ] Task 90: Disable unnecessary systemd services
- [ ] Task 91: Review and reduce image size
- [ ] Task 92: Rebuild optimized image
- [ ] Task 93: Verify boot time under 15 seconds
- [ ] Task 94: Verify all features still working after optimization

### Day 18 — Documentation + Final Delivery
- [ ] Task 95: Write README with full build instructions
- [ ] Task 96: Document Service, Event, and Method IDs table
- [ ] Task 97: Document VSOMEIP configuration and run instructions
- [ ] Task 98: Write known issues and limitations
- [ ] Task 99: Final Git commit and tag as v1.0
- [ ] Task 100: Prepare handoff notes for Month 2

---

## Deliverables

| # | Deliverable | Target Date |
|---|-------------|-------------|
| 1 | Yocto Image v1.0 (Qt6 + Audio + Python + VSOMEIP) | End of Week 2 (Day 10) |
| 2 | VSOMEIP Consumer Services (Vehicle + AC + Sensor) | End of Week 3 (Day 15) |
| 3 | Mock QNX Provider for Testing | End of Week 3 (Day 15) |
| 4 | Full Documentation + Tagged Release | End of Week 3.5 (Day 18) |

