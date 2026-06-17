#!/bin/bash
# =============================================================================
# EduPulse Kiosk OS — Live ISO Builder
# =============================================================================
# Builds a bootable ISO that turns any x86_64 computer into an EduPulse
# attendance kiosk without installation (live session).
#
# Requirements: sudo, debootstrap, live-build, squashfs-tools, xorriso
#
# Usage:
#   sudo bash build-iso.sh
#
# Output: ./edupulse-kiosk-*.iso
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUTPUT_DIR="${SCRIPT_DIR}/output"
BUILD_DIR="${SCRIPT_DIR}/_build"
OS_NAME="edupulse-kiosk"
OS_VERSION="1.0.0"
ARCH="amd64"
SUITE="noble"  # Ubuntu 24.04 LTS

CYAN='\033[0;36m'
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

info()  { echo -e "${CYAN}[ISO]${NC} $1"; }
ok()    { echo -e "${GREEN}[  OK  ]${NC} $1"; }
fail()  { echo -e "${RED}[FAIL]${NC} $1"; exit 1; }

[[ $EUID -eq 0 ]] || fail "Run with sudo"

# ── Install build dependencies ──
if ! command -v lb &>/dev/null; then
    info "Installing live-build..."
    # Try to update, ignoring broken repos
    apt update -y --allow-releaseinfo-change -o APT::Update::Error-Mode=any 2>/dev/null || \
    apt update -y 2>/dev/null || true
    apt install -y live-build debootstrap squashfs-tools xorriso grub-pc-bin grub-efi-amd64-bin --ignore-missing 2>/dev/null || \
    apt install -y live-build debootstrap squashfs-tools xorriso grub-pc-bin grub-efi-amd64-bin --allow-unauthenticated --ignore-missing 2>/dev/null || \
    fail "Could not install live-build. Run this first:\n  sudo mv /etc/apt/sources.list.d/nodesource.list /etc/apt/sources.list.d/nodesource.list.disabled\n  sudo apt update"
fi

info "Starting ISO build for ${OS_NAME} v${OS_VERSION} (${SUITE})"

rm -rf "$BUILD_DIR" "$OUTPUT_DIR"
mkdir -p "$BUILD_DIR" "$OUTPUT_DIR"

# ── Initialize live-build config ──
cd "$BUILD_DIR"
# Ubuntu mirror (live-build defaults to Debian)
UBUNTU_MIRROR="http://archive.ubuntu.com/ubuntu"
SECURITY_MIRROR="http://security.ubuntu.com/ubuntu"

lb config \
    --mode ubuntu \
    --distribution "${SUITE}" \
    --parent-distribution "${SUITE}" \
    --architectures "${ARCH}" \
    --archive-areas "main universe" \
    --debian-installer false \
    --bootappend-live "boot=casper quiet splash console=tty0 vt.global_cursor_default=0 loglevel=3 rd.systemd.show_status=false" \
    --linux-flavours "generic" \
    --memtest none \
    --iso-application "EduPulse Kiosk ${OS_VERSION}" \
    --iso-preparer "EduPulse" \
    --iso-publisher "EduPulse" \
    --iso-volume "EDUPULSE_KIOSK" \
    --mirror-bootstrap "${UBUNTU_MIRROR}" \
    --mirror-chroot "${UBUNTU_MIRROR}" \
    --mirror-chroot-security "${SECURITY_MIRROR}" \
    --mirror-binary "${UBUNTU_MIRROR}" \
    --mirror-binary-security "${SECURITY_MIRROR}"

ok "live-build config done"

# ── Package list ──
info "Writing package list..."
cat << 'PKGS' > config/package-lists/edupulse.list.chroot
### EduPulse Kiosk packages
# Desktop / display
openbox
xorg
xserver-xorg-video-fbdev
xserver-xorg-video-intel
xserver-xorg-video-vesa
x11-xserver-utils
unclutter
lightdm
lightdm-gtk-greeter

# Plymouth & boot
plymouth
plymouth-label
plymouth-themes
grub-pc
grub-gfxpayload-lists

# Python & scanner
python3
python3-pip
python3-pyqt5
python3-opencv
python3-numpy
python3-requests
python3-pil

# Network
curl
wget
git
network-manager

# System
bash-completion
ca-certificates
dbus
sudo
PKGS

# Additional packages via pip
cat << 'PIP' > config/hooks/008-pip-packages.chroot
#!/bin/bash
pip3 install pyzbar Pillow --break-system-packages
PIP
chmod +x config/hooks/008-pip-packages.chroot

ok "Package list written"

# ── Custom files overlay ──
info "Setting up overlay..."
OVERLAY="${BUILD_DIR}/config/includes.chroot"
mkdir -p "${OVERLAY}/opt/edupulse/scanner/sounds"
mkdir -p "${OVERLAY}/usr/share/plymouth/themes/edupulse"
mkdir -p "${OVERLAY}/etc/lightdm"
mkdir -p "${OVERLAY}/home/edupulse/.config/openbox"
mkdir -p "${OVERLAY}/usr/share/fonts/truetype/orbitron"
mkdir -p "${OVERLAY}/etc/systemd/system"

# Copy scanner
cp -r "${SCRIPT_DIR}/../scanner"/*.py "${OVERLAY}/opt/edupulse/scanner/" 2>/dev/null || true
cp -r "${SCRIPT_DIR}/../scanner/sounds"/* "${OVERLAY}/opt/edupulse/scanner/sounds/" 2>/dev/null || true
cp -r "${SCRIPT_DIR}/../server.js" "${OVERLAY}/opt/edupulse/" 2>/dev/null || true

# Copy Plymouth theme
cp -r "${SCRIPT_DIR}/plymouth/edupulse"/*.png "${OVERLAY}/usr/share/plymouth/themes/edupulse/"
cp -r "${SCRIPT_DIR}/plymouth/edupulse"/*.plymouth "${OVERLAY}/usr/share/plymouth/themes/edupulse/"
cp -r "${SCRIPT_DIR}/plymouth/edupulse"/*.script "${OVERLAY}/usr/share/plymouth/themes/edupulse/"

# Orbitron font
cp -r /usr/share/fonts/truetype/orbitron/* "${OVERLAY}/usr/share/fonts/truetype/orbitron/" 2>/dev/null || \
    wget -q "https://github.com/google/fonts/raw/main/ofl/orbitron/Orbitron%5Bwght%5D.ttf" \
        -O "${OVERLAY}/usr/share/fonts/truetype/orbitron/Orbitron-Variable.ttf"

# LightDM auto-login
cat << 'LIGHTDM' > "${OVERLAY}/etc/lightdm/lightdm.conf"
[Seat:*]
autologin-user=edupulse
autologin-user-timeout=0
user-session=openbox
greeter-session=lightdm-gtk-greeter
greeter-hide-users=true
LIGHTDM

# Openbox autostart
cat << 'OPENBOX' > "${OVERLAY}/home/edupulse/.config/openbox/autostart"
#!/bin/bash
xset s off
xset -dpms
xset s noblank
unclutter -idle 1 -root &
cd /opt/edupulse/scanner
python3 scanner.py
OPENBOX
chmod +x "${OVERLAY}/home/edupulse/.config/openbox/autostart"

# Set ownership
chroot "${OVERLAY}" chown -R 1000:1000 /home/edupulse 2>/dev/null || true

# Systemd service for API
cat << 'SERVICE' > "${OVERLAY}/etc/systemd/system/edupulse-api.service"
[Unit]
Description=EduPulse API Server
After=network.target
[Service]
Type=simple
User=edupulse
WorkingDirectory=/opt/edupulse
ExecStart=/usr/bin/node server.js
Restart=always
RestartSec=3
[Install]
WantedBy=multi-user.target
SERVICE

ok "Overlay files set up"

# ── Preseed / configure ──
info "Adding configuration hooks..."

# Plymouth default (use full path, install themes first)
cat << 'PLYMOUTH' > config/hooks/010-plymouth.chroot
#!/bin/bash
apt install -y plymouth-themes 2>/dev/null || true
/usr/sbin/plymouth-set-default-theme edupulse 2>/dev/null || true
update-initramfs -u 2>/dev/null || true
PLYMOUTH
chmod +x config/hooks/010-plymouth.chroot

# GRUB config (live ISO uses syslinux, not GRUB — just set defaults)
cat << 'GRUB' > config/hooks/020-grub.chroot
#!/bin/bash
if [ -f /etc/default/grub ]; then
    sed -i 's/GRUB_CMDLINE_LINUX_DEFAULT=.*/GRUB_CMDLINE_LINUX_DEFAULT="quiet splash loglevel=3 rd.systemd.show_status=false"/' /etc/default/grub || true
    sed -i 's/GRUB_GFXMODE=.*/GRUB_GFXMODE=1920x1080,1280x720,auto/' /etc/default/grub || true
fi
GRUB
chmod +x config/hooks/020-grub.chroot

# Disable sleep
cat << 'SLEEP' > config/hooks/030-disable-sleep.chroot
#!/bin/bash
systemctl mask sleep.target suspend.target hibernate.target hybrid-sleep.target
SLEEP
chmod +x config/hooks/030-disable-sleep.chroot

# Enable API server
cat << 'API' > config/hooks/040-enable-api.chroot
#!/bin/bash
systemctl enable edupulse-api.service 2>/dev/null || true
API
chmod +x config/hooks/040-enable-api.chroot

# Disable non-essential services
cat << 'FAST' > config/hooks/050-boot-optimize.chroot
#!/bin/bash
systemctl disable bluetooth.service cups.service avahi-daemon.service whoopsie.service 2>/dev/null || true
FAST
chmod +x config/hooks/050-boot-optimize.chroot

ok "Configuration hooks added"

# ── Build! ──
info "Building ISO (this will take a long time)..."

if lb build 2>&1 | tee "${OUTPUT_DIR}/build.log"; then
    # Move ISO to output directory
    mv "${BUILD_DIR}/live-image-${ARCH}.hybrid.iso" \
       "${OUTPUT_DIR}/${OS_NAME}-${OS_VERSION}-${ARCH}.iso" 2>/dev/null || \
    mv "${BUILD_DIR}"/*.iso "${OUTPUT_DIR}/" 2>/dev/null || true

    echo ""
    info "============================================"
    info "  ISO Build Complete!"
    info "============================================"
    echo ""
    ls -lh "${OUTPUT_DIR}"/*.iso 2>/dev/null || info "ISO not found — check build.log"
    echo ""
    info "Write to USB:"
    info "  sudo dd if=${OUTPUT_DIR}/${OS_NAME}-${OS_VERSION}-${ARCH}.iso of=/dev/sdX bs=4M status=progress"
    echo ""
else
    fail "Build failed — check ${OUTPUT_DIR}/build.log"
fi
