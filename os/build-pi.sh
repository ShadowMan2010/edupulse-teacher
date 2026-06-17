#!/bin/bash
# =============================================================================
# EduPulse Kiosk OS — Raspberry Pi Image Builder
# =============================================================================
# Creates a Raspberry Pi OS Lite-based image with the EduPulse scanner as
# a dedicated kiosk app. Works on Raspberry Pi 3B+, 4B, Pi 400, and Pi 5.
#
# Usage:
#   sudo bash build-pi.sh
#
# Output: ./edupulse-pi-*.img.xz
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUTPUT_DIR="${SCRIPT_DIR}/output"
WORK_DIR="${SCRIPT_DIR}/_pi_build"
OS_NAME="edupulse-pi"
OS_VERSION="1.0.0"

PI_OS_URL="https://downloads.raspberrypi.com/raspios_lite_arm64/images/raspios_lite_arm64-2024-10-28/2024-10-22-raspios-bookworm-arm64-lite.img.xz"
PI_OS_IMAGE="${WORK_DIR}/raspios-lite.img"
PI_OS_XZ="${PI_OS_IMAGE}.xz"

CYAN='\033[0;36m'
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

info()  { echo -e "${CYAN}[Pi]${NC} $1"; }
ok()    { echo -e "${GREEN}[  OK  ]${NC} $1"; }
fail()  { echo -e "${RED}[FAIL]${NC} $1"; exit 1; }

[[ $EUID -eq 0 ]] || fail "Run with sudo"

# ── Check dependencies ──
for cmd in wget xz parted losetup mkfs.ext4 mkfs.vfat rsync; do
    command -v "$cmd" &>/dev/null || fail "Missing: $cmd"
done

# ── Prepare workspace ──
rm -rf "$WORK_DIR" "$OUTPUT_DIR"
mkdir -p "$WORK_DIR" "${OUTPUT_DIR}"
mkdir -p "${WORK_DIR}/mnt/boot" "${WORK_DIR}/mnt/root"

# ── Download Raspberry Pi OS Lite ──
if [[ ! -f "$PI_OS_XZ" ]]; then
    info "Downloading Raspberry Pi OS Lite (arm64)..."
    wget -q --show-progress "$PI_OS_URL" -O "$PI_OS_XZ"
fi

info "Extracting base image..."
xz -d -v "$PI_OS_XZ" -c > "$PI_OS_IMAGE" 2>/dev/null || \
    xz -d "$PI_OS_XZ" -c > "$PI_OS_IMAGE"

# ── Expand image by 2GB ──
info "Expanding image (+2GB for apps)..."
qemu-img resize -f raw "$PI_OS_IMAGE" +2G

# ── Mount and customize ──
info "Mounting image for customization..."
LOOP_DEV=$(losetup -f --show -P "$PI_OS_IMAGE")
BOOT_DEV="${LOOP_DEV}p1"
ROOT_DEV="${LOOP_DEV}p2"

# Resize root partition
e2fsck -fy "$ROOT_DEV" || true
parted -s "$LOOP_DEV" resizepart 2 100%
resize2fs "$ROOT_DEV"

mount "$ROOT_DEV" "${WORK_DIR}/mnt/root"
mount "$BOOT_DEV" "${WORK_DIR}/mnt/boot"

# ── Enable SSH (for headless setup) ──
touch "${WORK_DIR}/mnt/boot/ssh"

# ── Set up WiFi (optional) ──
if [[ -n "${WIFI_SSID:-}" && -n "${WIFI_PASS:-}" ]]; then
    cat << WPA > "${WORK_DIR}/mnt/root/etc/wpa_supplicant/wpa_supplicant.conf"
ctrl_interface=DIR=/var/run/wpa_supplicant GROUP=netdev
update_config=1
country=IN
network={
    ssid="${WIFI_SSID}"
    psk="${WIFI_PASS}"
}
WPA
fi

# ── Install Orbitron font (from host or download) ──
info "Installing Orbitron font..."
mkdir -p "${WORK_DIR}/mnt/root/usr/share/fonts/truetype/orbitron"
if [[ -f /usr/share/fonts/truetype/orbitron/Orbitron-Variable.ttf ]]; then
    cp /usr/share/fonts/truetype/orbitron/Orbitron-Variable.ttf \
       "${WORK_DIR}/mnt/root/usr/share/fonts/truetype/orbitron/"
else
    wget -q "https://github.com/google/fonts/raw/main/ofl/orbitron/Orbitron%5Bwght%5D.ttf" \
        -O "${WORK_DIR}/mnt/root/usr/share/fonts/truetype/orbitron/Orbitron-Variable.ttf"
fi

# ── Copy EduPulse scanner ──
info "Copying EduPulse scanner..."
mkdir -p "${WORK_DIR}/mnt/root/opt/edupulse/scanner/sounds"
cp -r "${SCRIPT_DIR}/../scanner"/*.py "${WORK_DIR}/mnt/root/opt/edupulse/scanner/" 2>/dev/null || true
cp -r "${SCRIPT_DIR}/../scanner/sounds"/* "${WORK_DIR}/mnt/root/opt/edupulse/scanner/sounds/" 2>/dev/null || true
cp -r "${SCRIPT_DIR}/../server.js" "${WORK_DIR}/mnt/root/opt/edupulse/" 2>/dev/null || true

# ── Copy Plymouth theme ──
info "Installing Plymouth theme..."
mkdir -p "${WORK_DIR}/mnt/root/usr/share/plymouth/themes/edupulse"
cp -r "${SCRIPT_DIR}/plymouth/edupulse"/*.png "${WORK_DIR}/mnt/root/usr/share/plymouth/themes/edupulse/"
cp -r "${SCRIPT_DIR}/plymouth/edupulse"/*.plymouth "${WORK_DIR}/mnt/root/usr/share/plymouth/themes/edupulse/"
cp -r "${SCRIPT_DIR}/plymouth/edupulse"/*.script "${WORK_DIR}/mnt/root/usr/share/plymouth/themes/edupulse/"

# ── System configuration via chroot ──
info "Applying system configuration..."

# We need to chroot with binfmt for ARM
if command -v systemd-nspawn &>/dev/null; then
    CHROOT_CMD="systemd-nspawn --resolv-conf=bind-host -D ${WORK_DIR}/mnt/root"
elif command -v qemu-aarch64-static &>/dev/null; then
    cp /usr/bin/qemu-aarch64-static "${WORK_DIR}/mnt/root/usr/bin/"
    CHROOT_CMD="chroot ${WORK_DIR}/mnt/root"
else
    info "qemu-aarch64-static not found — will skip chroot config"
    info "Install it: sudo apt install qemu-user-static binfmt-support"
    CHROOT_CMD=""
fi

if [[ -n "$CHROOT_CMD" ]]; then
    # Install packages
    $CHROOT_CMD /bin/bash -c '
        apt update -y
        apt install -y python3-pip python3-pyqt5 python3-opencv \
            python3-numpy python3-requests python3-pil \
            plymouth plymouth-label plymouth-themes \
            lightdm lightdm-gtk-greeter openbox xorg \
            xserver-xorg-video-fbdev x11-xserver-utils unclutter \
            network-manager
        pip3 install pyzbar --break-system-packages

        # Set Plymouth theme
        plymouth-set-default-theme edupulse
        update-initramfs -u

        # GRUB not used on Pi, but configure cmdline
        sed -i "s/console=serial0,115200//" /boot/firmware/cmdline.txt 2>/dev/null || true
        sed -i "s/$/ quiet splash logo.nologo loglevel=3 vt.global_cursor_default=0/" /boot/firmware/cmdline.txt 2>/dev/null || true

        # LightDM auto-login
        mkdir -p /etc/lightdm
        cat > /etc/lightdm/lightdm.conf << EOF
[Seat:*]
autologin-user=edupulse
autologin-user-timeout=0
user-session=openbox
greeter-session=lightdm-gtk-greeter
greeter-hide-users=true
EOF

        # Create edupulse user
        useradd -m -s /bin/bash edupulse || true
        echo "edupulse:edupulse" | chpasswd
        usermod -aG video,input,i2c,audio edupulse

        # Openbox autostart
        mkdir -p /home/edupulse/.config/openbox
        cat > /home/edupulse/.config/openbox/autostart << EOF
#!/bin/bash
xset s off
xset -dpms
xset s noblank
unclutter -idle 1 -root &
cd /opt/edupulse/scanner
python3 scanner.py
EOF
        chmod +x /home/edupulse/.config/openbox/autostart
        chown -R edupulse:edupulse /home/edupulse
        chown -R edupulse:edupulse /opt/edupulse

        # Disable sleep
        systemctl mask sleep.target suspend.target hibernate.target hybrid-sleep.target

        # Disable unnecessary services
        systemctl disable bluetooth.service cups.service avahi-daemon.service 2>/dev/null || true
    '
fi

# ── Cleanup ──
info "Cleaning up..."
sync
umount "${WORK_DIR}/mnt/boot"
umount "${WORK_DIR}/mnt/root"
losetup -d "$LOOP_DEV"

# ── Compress image ──
info "Compressing image..."
xz -T0 -v "$PI_OS_IMAGE" -c > "${OUTPUT_DIR}/${OS_NAME}-${OS_VERSION}-arm64.img.xz"

echo ""
info "============================================"
info "  Pi Image Build Complete!"
info "============================================"
echo ""
ls -lh "${OUTPUT_DIR}/${OS_NAME}-${OS_VERSION}-arm64.img.xz"
echo ""
info "Write to SD card:"
info "  xz -d -c ${OUTPUT_DIR}/${OS_NAME}-${OS_VERSION}-arm64.img.xz | sudo dd of=/dev/sdX bs=4M status=progress"
echo ""
