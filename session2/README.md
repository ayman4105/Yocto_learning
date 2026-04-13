# Yocto Session 2 - Variables & Recipes
==========================================

## 1. Variables Assignment Types
---------------------------------

### 1.1 Normal Assignment (=)
```bash
# Lazy - evaluated at the end
A = "hello"
B = "${A} world"
A = "hi"
# Result: B = "hi world" (takes last value of A)
```

### 1.2 Immediate Assignment (:=)
```bash
# Evaluated immediately
A = "hello"
B := "${A} world"
A = "hi"
# Result: B = "hello world" (takes value at assignment time)
```

### 1.3 Default Assignment (?=)
```bash
# Only sets if no previous value exists
MACHINE ?= "qemux86-64"

# If already set - won't override
MACHINE = "raspberrypi4-64"
MACHINE ?= "qemux86-64"
# Result: MACHINE = "raspberrypi4-64"
```

### 1.4 Weak Default Assignment (??=)
```bash
# Weakest assignment - evaluated last
MACHINE ??= "qemux86-64"
MACHINE ?= "raspberrypi4"
# Result: MACHINE = "raspberrypi4"
```

### Strength Order:
```
=    💪💪💪💪  Strongest
:=   💪💪💪💪  Strongest
?=   💪💪      If no value
??=  💪        Weakest
```

---

## 2. Append / Prepend / Remove
---------------------------------

### 2.1 Append (:append)
```bash
IMAGE_INSTALL = "bash busybox"
IMAGE_INSTALL:append = " openssh"
# Result: "bash busybox openssh"
# ⚠️ Note the space before openssh!
```

### 2.2 Prepend (:prepend)
```bash
IMAGE_INSTALL = "bash busybox"
IMAGE_INSTALL:prepend = "nano "
# Result: "nano bash busybox"
# ⚠️ Note the space after nano!
```

### 2.3 Remove (:remove)
```bash
IMAGE_INSTALL = "bash busybox openssh nano"
IMAGE_INSTALL:remove = "nano"
# Result: "bash busybox openssh"
```

### 2.4 Difference between += and :append
```bash
# += executes during parsing (can be overwritten)
IMAGE_INSTALL = "bash"
IMAGE_INSTALL += "nano"
IMAGE_INSTALL = "busybox"
# Result: "busybox" (nano is lost!)

# :append executes after all parsing (always applied)
IMAGE_INSTALL = "bash"
IMAGE_INSTALL:append = " nano"
IMAGE_INSTALL = "busybox"
# Result: "busybox nano" (nano survives!)
```

---

## 3. Key Variables
--------------------

### 3.1 IMAGE_INSTALL
```bash
# What packages to install on the final image
IMAGE_INSTALL:append = " openssh"
IMAGE_INSTALL:append = " python3"
IMAGE_INSTALL:append = " nano"
```

### 3.2 DISTRO_FEATURES
```bash
# Enable/disable system-wide features
DISTRO_FEATURES:append = " wifi"
DISTRO_FEATURES:append = " bluetooth"
DISTRO_FEATURES:remove = "x11"
```

### 3.3 MACHINE_FEATURES
```bash
# Hardware capabilities (usually set in machine conf)
MACHINE_FEATURES = "wifi bluetooth screen gpio usbhost"
```

### 3.4 Relationship between them
```
DISTRO_FEATURES = "what features to enable in the OS"
IMAGE_INSTALL   = "what programs to put on the image"
MACHINE_FEATURES = "what hardware capabilities exist"

Example - WiFi:
  DISTRO_FEATURES:append = " wifi"        --> enable WiFi support
  IMAGE_INSTALL:append = " wpa-supplicant" --> install WiFi tool
  Both needed together!
```

### 3.5 PREFERRED_PROVIDER
```bash
# Which recipe provides the kernel
PREFERRED_PROVIDER_virtual/kernel = "linux-raspberrypi"
```

### 3.6 PREFERRED_VERSION
```bash
# Which version to use
PREFERRED_VERSION_linux-raspberrypi = "6.6%"
# % = wildcard (6.6.1, 6.6.2, etc)
```

---

## 4. OVERRIDES
----------------

```bash
# Apply settings based on MACHINE or DISTRO

# Only for raspberrypi4-64
IMAGE_INSTALL:append:raspberrypi4-64 = " raspi-gpio"

# Only for raspberrypi3
IMAGE_INSTALL:append:raspberrypi3 = " pi-bluetooth"

# Only for poky distro
DISTRO_FEATURES:append:poky = " bluetooth"
```

```
OVERRIDES = "linux:arm:raspberrypi4-64:poky"

:append:arm     --> Applied ✅ (arm in OVERRIDES)
:append:x86     --> Ignored ❌ (x86 not in OVERRIDES)
:append:poky    --> Applied ✅ (poky in OVERRIDES)
```

---

## 5. DEPENDS vs RDEPENDS
---------------------------

```bash
# Compile time dependency
DEPENDS = "openssl zlib"
# Needed to BUILD the recipe

# Runtime dependency
RDEPENDS:${PN} = "bash openssl"
# Needed to RUN the program on the target
# Automatically installed on the image
```

---

## 6. Recipe Structure
-----------------------

### 6.1 Basic Recipe Template
```bash
# myapp_1.0.bb

# ===== Metadata =====
SUMMARY = "My Application"
DESCRIPTION = "Description of my app"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

# ===== Source =====
SRC_URI = "git://github.com/ayman4105/test_yocto.git;protocol=https;branch=main"
SRCREV = "29f6b91d439552af80c1f7c4cf852bf5a3fffc30"

S = "${WORKDIR}"

# ===== Build =====
do_compile() {
    ${CC} ${CFLAGS} ${LDFLAGS} myapp.c -o myapp
}

# ===== Install =====
do_install() {
    install -d ${D}${bindir}
    install -m 0755 myapp ${D}${bindir}
}
```

### 6.2 Recipe File Structure
```
recipes-apps/myapp/
|
+-- myapp_1.0.bb          # The recipe
+-- files/
      +-- myapp.c          # Source code
      +-- Makefile          # (optional)
```

---

## 7. SRC_URI Types
--------------------

### 7.1 Local File
```bash
SRC_URI = "file://helloworld.c"

# Multiple files
SRC_URI = "file://main.c \
           file://utils.c \
           file://Makefile \
          "

S = "${WORKDIR}"
```

### 7.2 Git Repository
```bash
SRC_URI = "git://github.com/user/myapp.git;protocol=https;branch=main"
SRCREV = "a1b2c3d4e5f6..."  # commit hash

# Or for development (not recommended for production)
SRCREV = "${AUTOREV}"

S = "${WORKDIR}/git"
```

### 7.3 HTTP/HTTPS Tarball
```bash
SRC_URI = "https://example.com/myapp-1.0.tar.gz"
SRC_URI[sha256sum] = "abc123..."

S = "${WORKDIR}/myapp-1.0"
```

### 7.4 Git + Patches
```bash
SRC_URI = "git://github.com/user/myapp.git;protocol=https;branch=main \
           file://fix-build.patch \
           file://add-feature.patch \
          "
SRCREV = "a1b2c3d4e5f6..."
S = "${WORKDIR}/git"
# Patches applied automatically in do_patch
```

### 7.5 S Variable for each type
```
file:// (single file)  --> S = "${WORKDIR}"
git://                 --> S = "${WORKDIR}/git"
https:// (tarball)     --> S = "${WORKDIR}/myapp-1.0"
```

---

## 8. LICENSE Checksum
-----------------------

### Method 1: Use common license
```bash
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"
```

### Method 2: Get checksum manually
```bash
md5sum ~/ITI/fady/Yocto/poky/meta/files/common-licenses/MIT
# Output: 0835ade698e0bcf8506ecda2f7b4f302  MIT
```

### Method 3: From project LICENSE file
```bash
md5sum files/LICENSE
# Use the output in LIC_FILES_CHKSUM
LIC_FILES_CHKSUM = "file://LICENSE;md5=<output>"
```

### Method 4: Let bitbake tell you
```bash
# Put wrong checksum
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=wrong"
# bitbake will error and show the correct checksum
```


## 9. WORKDIR & S
-------------------

### 9.1 What is WORKDIR?
The directory where bitbake does all work for each recipe.

Path format:
```
tmp/work/<ARCHITECTURE>/<RECIPE_NAME>/<VERSION>/
```

Real examples:
```bash
# Kernel
tmp/work/raspberrypi3_64-poky-linux/linux-raspberrypi/6.6.63+git/

# RPI Config
tmp/work/raspberrypi3_64-poky-linux/rpi-config/git/

# Base Files
tmp/work/raspberrypi3_64-poky-linux/base-files/3.0.14-r0/

# Custom Recipe
tmp/work/cortexa53-poky-linux/helloworld/1.0-r0/
```

### 9.2 Architecture Directories
```
tmp/work/
|
+-- all-poky-linux/                  # Architecture-independent packages
+-- cortexa53-poky-linux/            # ARM packages (run on RPI)
+-- raspberrypi3_64-poky-linux/      # RPI-specific packages
+-- x86_64-linux/                    # Host PC tools (gcc-cross, cmake-native)
```

### 9.3 What is S?
S = where the source code is located inside WORKDIR.

Rule:
```
file://    --> S = "${WORKDIR}"           (code directly in WORKDIR)
git://     --> S = "${WORKDIR}/git"       (code inside git/ folder)
https://   --> S = "${WORKDIR}/name-ver"  (code inside extracted folder)
No SRC_URI --> S = "${WORKDIR}"           (no source code)
```

### 9.4 Why S differs per SRC_URI type?

#### file:// - copied directly
```
WORKDIR/
+-- myapp.c         <-- code here (S = WORKDIR)
+-- temp/
```

#### git:// - git clone creates "git" folder
```
WORKDIR/
+-- git/            <-- code here (S = WORKDIR/git)
|     +-- src/
|     +-- CMakeLists.txt
+-- temp/
```

#### https:// tarball - tar extracts to folder
```
WORKDIR/
+-- myapp-1.0/      <-- code here (S = WORKDIR/myapp-1.0)
|     +-- src/
|     +-- Makefile
+-- temp/
```

### 9.5 Inside WORKDIR (after full build)
```
WORKDIR/
|
+-- <source files>       # Source code
+-- image/               # Files after do_install
+-- package/             # Files ready for packaging
+-- packages-split/      # Packages split (main, -dbg, -dev)
+-- deploy-rpms/         # Final RPM packages
+-- temp/                # Logs and scripts
|     +-- log.do_fetch
|     +-- log.do_compile
|     +-- log.do_install
|     +-- run.do_compile
```

### 9.6 sstate-cache Effect
When built from cache:
- No source code (no git/, no .c files)
- Only sstate-install-* files present
- Logs show "setscene" tasks only

To force full build:
```bash
bitbake -c cleansstate <recipe-name>
bitbake <recipe-name>
```

### 9.7 Quick Reference

| SRC_URI Type | S Value | Example |
|-------------|---------|---------|
| `file://` | `${WORKDIR}` | rpi-config, base-files |
| `git://` | `${WORKDIR}/git` | linux-raspberrypi |
| `https://.tar.gz` | `${WORKDIR}/name-ver` | bash, busybox |
| No SRC_URI | `${WORKDIR}` | core-image-minimal |


## 10. do_compile & Cross Compilation
---------------------------------------

### 10.1 Basic do_compile
```bash
do_compile() {
    ${CC} ${CFLAGS} ${LDFLAGS} helloworld.c -o helloworld
}
```

### 10.2 ${CC} - Cross Compiler
```bash
# ${CC} is NOT regular gcc
# It's a cross compiler that builds for target architecture

# Changes automatically based on MACHINE:
MACHINE = "raspberrypi3-64"  --> ${CC} = aarch64-poky-linux-gcc
MACHINE = "beaglebone"       --> ${CC} = arm-poky-linux-gnueabi-gcc
MACHINE = "qemux86-64"       --> ${CC} = x86_64-poky-linux-gcc

# Verify after build:
$ file helloworld
# Regular gcc:    ELF 64-bit x86-64        (PC only)
# Cross compiled: ELF 64-bit ARM aarch64   (runs on RPI)
```

### 10.3 ${CFLAGS} - Compiler Flags
```bash
# Expands to something like:
-O2 -pipe -g -feliminate-unused-debug-types

# -O2    : Optimization level 2
# -pipe  : Use pipes instead of temp files (faster)
# -g     : Add debug information
```

### 10.4 ${LDFLAGS} - Linker Flags
```bash
# Expands to something like:
-Wl,-O1 -Wl,--hash-style=gnu -Wl,--as-needed

# -Wl,-O1          : Linker optimization
# --hash-style=gnu : Hash table type
# --as-needed      : Link only what's needed
```

### 10.5 Why use variables not hard-coded?
```bash
# WRONG - only works for one machine
do_compile() {
    arm-poky-linux-gcc -O2 helloworld.c -o helloworld
}

# CORRECT - works for any machine
do_compile() {
    ${CC} ${CFLAGS} ${LDFLAGS} helloworld.c -o helloworld
}
```

### 10.6 All Available Variables
```
| Variable   | Purpose                                   |
|------------|-------------------------------------------|
| ${CC}      | C Compiler         (arm-...-gcc)          |
| ${CXX}     | C++ Compiler       (arm-...-g++)          |
| ${CPP}     | C Preprocessor     (arm-...-cpp)          |
| ${LD}      | Linker             (arm-...-ld)           |
| ${AR}      | Archiver           (arm-...-ar)           |
| ${AS}      | Assembler          (arm-...-as)           |
| ${STRIP}   | Strip debug info   (arm-...-strip)        |
| ${OBJCOPY} | Object Copy        (arm-...-objcopy)      |
| ${CFLAGS}  | C Compiler flags                          |
| ${CXXFLAGS}| C++ Compiler flags                        |
| ${LDFLAGS} | Linker flags                              |
```

### 10.7 C++ Example
```bash
do_compile() {
    ${CXX} ${CXXFLAGS} ${LDFLAGS} myapp.cpp -o myapp
}
```

### 10.8 Makefile Example
```bash
# If project has Makefile
do_compile() {
    oe_runmake
}
# oe_runmake automatically passes CC, CFLAGS, LDFLAGS to make
```

### 10.9 CMake Example
```bash
# If project uses CMake - just inherit
inherit cmake
# No need to write do_compile - cmake.bbclass handles it
```

## 11. do_install
-------------------

### 11.1 Basic do_install
```bash
do_install() {
    install -d ${D}${bindir}
    install -m 0755 helloworld ${D}${bindir}
}
```

### 11.2 ${D} - Destination Directory
```bash
# ${D} = fake root filesystem inside WORKDIR
# D = WORKDIR/image/

# Example:
# ${D} = ~/ITI/fady/Yocto/share/tmp/work/
#        cortexa53-poky-linux/helloworld/1.0-r0/image/

# Files go into image/ first, then bitbake moves them to final Image
```

### 11.3 Path Variables
```
| Variable                  | Path                    | Usage              |
|---------------------------|-------------------------|--------------------|
| ${bindir}                 | /usr/bin                | User programs      |
| ${sbindir}                | /usr/sbin               | Root programs      |
| ${sysconfdir}             | /etc                    | Config files       |
| ${libdir}                 | /usr/lib                | Libraries          |
| ${datadir}                | /usr/share              | Data files         |
| ${includedir}             | /usr/include            | Header files       |
| ${systemd_system_unitdir} | /lib/systemd/system     | Systemd services   |
```

### 11.4 install Command
```bash
# Create directory (-d flag)
install -d ${D}${bindir}
# Same as: mkdir -p WORKDIR/image/usr/bin

# Copy file with permissions (-m flag)
install -m 0755 helloworld ${D}${bindir}
# Same as: cp helloworld WORKDIR/image/usr/bin/ && chmod 0755
```

### 11.5 File Permissions
```
0755 = rwxr-xr-x  (executable program)
0644 = rw-r--r--  (config file, data file)
0600 = rw-------  (private key, secret file)

Breakdown of 0755:
  0    7    5    5
  |    |    |    |
  |    |    |    +-- Others: read + execute
  |    |    +-- Group: read + execute
  |    +-- Owner: read + write + execute
  +-- Special bits (none)
```

### 11.6 Examples

#### Program only
```bash
do_install() {
    install -d ${D}${bindir}
    install -m 0755 myapp ${D}${bindir}
}
# Result on RPI: /usr/bin/myapp
```

#### Program + Config file
```bash
do_install() {
    install -d ${D}${bindir}
    install -d ${D}${sysconfdir}
    install -m 0755 myapp ${D}${bindir}
    install -m 0644 myapp.conf ${D}${sysconfdir}
}
# Result on RPI:
#   /usr/bin/myapp
#   /etc/myapp.conf
```

#### Program + Library + Header
```bash
do_install() {
    install -d ${D}${bindir}
    install -d ${D}${libdir}
    install -d ${D}${includedir}
    install -m 0755 myapp ${D}${bindir}
    install -m 0755 libmylib.so ${D}${libdir}
    install -m 0644 mylib.h ${D}${includedir}
}
# Result on RPI:
#   /usr/bin/myapp
#   /usr/lib/libmylib.so
#   /usr/include/mylib.h
```

#### Program + Systemd Service
```bash
do_install() {
    install -d ${D}${bindir}
    install -d ${D}${systemd_system_unitdir}
    install -m 0755 myapp ${D}${bindir}
    install -m 0644 myapp.service ${D}${systemd_system_unitdir}
}
# Result on RPI:
#   /usr/bin/myapp
#   /lib/systemd/system/myapp.service
```

#### Script + Data Files
```bash
do_install() {
    install -d ${D}${bindir}
    install -d ${D}${datadir}/myapp
    install -m 0755 myscript.sh ${D}${bindir}
    install -m 0644 data.json ${D}${datadir}/myapp
    install -m 0644 logo.png ${D}${datadir}/myapp
}
# Result on RPI:
#   /usr/bin/myscript.sh
#   /usr/share/myapp/data.json
#   /usr/share/myapp/logo.png
```

### 11.7 File Journey
```
do_install
  WORKDIR/image/usr/bin/helloworld
      |
      v
do_package
  WORKDIR/packages-split/helloworld/usr/bin/helloworld
      |
      v
do_package_write_rpm
  WORKDIR/deploy-rpms/helloworld-1.0-r0.cortexa53.rpm
      |
      v
do_image
  tmp/deploy/images/raspberrypi3-64/core-image-minimal.wic
      |
      v
Flash to SD Card
  /usr/bin/helloworld  (on RPI)
```

##  Useful Bash Commands
---------------------------

```bash
# Setup build environment
source poky/oe-init-build-env ../build-rpi

# Build an image
bitbake core-image-minimal

# Build a single recipe
bitbake helloworld

# Clean a recipe
bitbake -c clean helloworld
bitbake -c cleansstate helloworld

# Show layers
bitbake-layers show-layers

# Add a layer
bitbake-layers add-layer ../meta-mylayer

# Create a new layer
bitbake-layers create-layer ../meta-mylayer

# Show recipes
bitbake-layers show-recipes

# Check available licenses
ls poky/meta/files/common-licenses/

# Get license checksum
md5sum poky/meta/files/common-licenses/MIT

# Get git commit hash
git ls-remote https://github.com/user/repo.git
```
