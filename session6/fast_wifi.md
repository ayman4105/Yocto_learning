# WiFi on RPi 3 B+ with Yocto - Quick Reference

## 1. Create Recipe Files

```bash
mkdir -p ~/ITI/fady/Yocto/meta-test/recipes-connectivity/wifi-config/files
```

### files/wpa_supplicant-wlan0.conf

```bash
cat > ~/ITI/fady/Yocto/meta-test/recipes-connectivity/wifi-config/files/wpa_supplicant-wlan0.conf << 'EOF'
ctrl_interface=/var/run/wpa_supplicant
ctrl_interface_group=0
update_config=1

network={
    ssid="YOUR_WIFI_NAME"
    psk="YOUR_WIFI_PASSWORD"
}
EOF
```

### files/wpa_supplicant@wlan0.service

```bash
cat > ~/ITI/fady/Yocto/meta-test/recipes-connectivity/wifi-config/files/wpa_supplicant@wlan0.service << 'EOF'
[Unit]
Description=WPA supplicant daemon (interface-specific version)
Requires=sys-subsystem-net-devices-wlan0.device
After=sys-subsystem-net-devices-wlan0.device
Before=network.target
Wants=network.target

[Service]
Type=simple
ExecStart=/usr/sbin/wpa_supplicant -c /etc/wpa_supplicant/wpa_supplicant-wlan0.conf -i wlan0

[Install]
WantedBy=multi-user.target
EOF
```

### files/25-wlan0.network

```bash
cat > ~/ITI/fady/Yocto/meta-test/recipes-connectivity/wifi-config/files/25-wlan0.network << 'EOF'
[Match]
Name=wlan0

[Network]
DHCP=yes
EOF
```

### wifi-config_1.0.bb

```bash
cat > ~/ITI/fady/Yocto/meta-test/recipes-connectivity/wifi-config/wifi-config_1.0.bb << 'EOF'
SUMMARY = "WiFi Configuration for RPi3"
DESCRIPTION = "Installs wpa_supplicant config, systemd service, and network config for WiFi"
LICENSE = "CLOSED"

SRC_URI = " \
    file://wpa_supplicant-wlan0.conf \
    file://wpa_supplicant@wlan0.service \
    file://25-wlan0.network \
"

do_install() {
    install -d ${D}/etc/wpa_supplicant/
    install -m 0644 ${WORKDIR}/wpa_supplicant-wlan0.conf ${D}/etc/wpa_supplicant/

    install -d ${D}/usr/lib/systemd/system/
    install -m 0644 ${WORKDIR}/wpa_supplicant@wlan0.service ${D}/usr/lib/systemd/system/

    install -d ${D}/etc/systemd/system/multi-user.target.wants/
    ln -sf /usr/lib/systemd/system/wpa_supplicant@wlan0.service \
           ${D}/etc/systemd/system/multi-user.target.wants/wpa_supplicant@wlan0.service

    install -d ${D}/etc/systemd/network/
    install -m 0644 ${WORKDIR}/25-wlan0.network ${D}/etc/systemd/network/
}

FILES:${PN} = " \
    /etc/wpa_supplicant \
    /etc/systemd \
    /usr/lib/systemd \
"
EOF
```

## 2. Update distro config

```bash
# add wifi to DISTRO_FEATURES in ayman.conf
# DISTRO_FEATURES:append = " systemd usrmerge wifi"
```

## 3. Update local.conf

```bash
# add to build-rpi/conf/local.conf:
# KERNEL_MODULE_AUTOLOAD += "brcmfmac"
# LICENSE_FLAGS_ACCEPTED += "synaptics-killswitch"
```

## 4. Update ayman-image.bb

```bash
# add to IMAGE_INSTALL:
# IMAGE_INSTALL:append = " wpa-supplicant linux-firmware-rpidistro-bcm43455 wifi-config kernel-module-brcmfmac kernel-module-brcmfmac-wcc kernel-module-brcmfmac-bca"
```

## 5. Build

```bash
cd ~/ITI/fady/Yocto
source poky/oe-init-build-env build-rpi
bitbake ayman-image
```

## 6. Flash

```bash
cd ~/ITI/fady/Yocto/share/tmp/deploy/images/raspberrypi3-64/
lsblk
sudo umount /media/ayman/boot 2>/dev/null
sudo umount /media/ayman/root 2>/dev/null
sudo bmaptool copy ayman-image-raspberrypi3-64.rootfs.wic.bz2 /dev/sdX
```

## 7. Find RPi and SSH

```bash
nmap -sn 192.168.1.0/24

# fix host key error after reflash
ssh-keygen -f ~/.ssh/known_hosts -R 192.168.1.6

ssh root@192.168.1.X
```

## 8. Debug on RPi

```bash
ip link show
dmesg | grep -i brcm
lsmod | grep brcm
systemctl status wpa_supplicant@wlan0
wpa_cli -i wlan0 status
ip addr show wlan0
```

## 9. Deploy with devtool

```bash
devtool build <recipe>
devtool deploy-target <recipe> root@192.168.1.X
devtool undeploy-target <recipe> root@192.168.1.X
