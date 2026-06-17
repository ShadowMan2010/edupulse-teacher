#!/bin/bash
# =============================================================================
# EduPulse Kiosk OS — Setup Script
# =============================================================================
# Configures a fresh Ubuntu/Debian/Raspberry Pi OS as a dedicated attendance
# kiosk that boots directly into the EduPulse QR Scanner.
#
# Usage:
#   sudo bash setup-kiosk.sh
#
# Or for dry-run:
#   sudo bash setup-kiosk.sh --dry-run
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DRY_RUN="${2:-}"
PLYMOUTH_DIR="${SCRIPT_DIR}/plymouth/edupulse"
OVERLAY_DIR="${SCRIPT_DIR}/overlay"
EDUPULSE_SRC="$(cd "${SCRIPT_DIR}/.." && pwd)"

# ── Colors ──
CYAN='\033[0;36m'
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

info()  { echo -e "${CYAN}[EduPulse]${NC} $1"; }
ok()    { echo -e "${GREEN}[  OK  ]${NC} $1"; }
fail()  { echo -e "${RED}[FAIL]${NC} $1"; exit 1; }

# ── Sanity checks ──
[[ $EUID -eq 0 ]] || fail "Run with sudo"
[[ -d "$PLYMOUTH_DIR" ]] || fail "Plymouth theme not found at ${PLYMOUTH_DIR}"

if [[ "${1:-}" == "--dry-run" ]]; then
    info "DRY RUN — commands will be printed, not executed"
    RUN="echo"
else
    RUN=""
fi

info "Starting EduPulse Kiosk Setup..."
echo ""

# ────────────────────────────────────────
# 1. System packages
# ────────────────────────────────────────
info "Installing system dependencies..."
$RUN apt update -y
$RUN apt install -y \
    python3 python3-pip python3-pyqt5 python3-opencv \
    python3-numpy python3-requests \
    plymouth plymouth-label plymouth-themes \
    lightdm lightdm-gtk-greeter \
    openbox xorg xserver-xorg-video-fbdev \
    x11-xserver-utils unclutter \
    curl wget git \
    || fail "apt install failed"
ok "System dependencies installed"

# ────────────────────────────────────────
# 2. Python packages
# ────────────────────────────────────────
info "Installing Python packages..."
$RUN pip3 install pyzbar Pillow --break-system-packages || true
ok "Python packages installed"

# ────────────────────────────────────────
# 3. Orbitron font
# ────────────────────────────────────────
info "Installing Orbitron font..."
FONT_DIR="/usr/share/fonts/truetype/orbitron"
$RUN mkdir -p "$FONT_DIR"
if [[ ! -f "${FONT_DIR}/Orbitron-Variable.ttf" ]]; then
    $RUN wget -q "https://github.com/google/fonts/raw/main/ofl/orbitron/Orbitron%5Bwght%5D.ttf" \
        -O "${FONT_DIR}/Orbitron-Variable.ttf"
fi
$RUN fc-cache -f
ok "Orbitron font installed"

# ────────────────────────────────────────
# 4. Copy EduPulse scanner to /opt
# ────────────────────────────────────────
info "Installing EduPulse scanner to /opt/edupulse..."
$RUN mkdir -p /opt/edupulse/scanner
$RUN cp -r "${EDUPULSE_SRC}/scanner"/*.py /opt/edupulse/scanner/
$RUN mkdir -p /opt/edupulse/scanner/sounds
$RUN cp -r "${EDUPULSE_SRC}/scanner/sounds"/* /opt/edupulse/scanner/sounds/ 2>/dev/null || true
$RUN cp -r "${EDUPULSE_SRC}/js" /opt/edupulse/js/ 2>/dev/null || true
$RUN cp -r "${EDUPULSE_SRC}/server.js" /opt/edupulse/ 2>/dev/null || true
ok "Scanner files copied to /opt/edupulse"

# ────────────────────────────────────────
# 5. Create edupulse user
# ────────────────────────────────────────
info "Creating edupulse user..."
if ! id -u edupulse &>/dev/null; then
    $RUN useradd -m -s /bin/bash edupulse
    $RUN echo "edupulse:edupulse" | chpasswd
    $RUN usermod -aG video,input,i2c,audio edupulse
fi
ok "edupulse user created"

# ────────────────────────────────────────
# 6. Plymouth theme
# ────────────────────────────────────────
info "Installing Plymouth theme..."
$RUN mkdir -p /usr/share/plymouth/themes/edupulse
$RUN cp "${PLYMOUTH_DIR}"/*.png /usr/share/plymouth/themes/edupulse/
$RUN cp "${PLYMOUTH_DIR}"/*.plymouth /usr/share/plymouth/themes/edupulse/
$RUN cp "${PLYMOUTH_DIR}"/*.script /usr/share/plymouth/themes/edupulse/
$RUN plymouth-set-default-theme -R edupulse 2>/dev/null || true
$RUN update-initramfs -u 2>/dev/null || true
ok "Plymouth theme installed"

# ────────────────────────────────────────
# 7. GRUB config
# ────────────────────────────────────────
info "Configuring GRUB..."
GRUB_CFG="/etc/default/grub"
$RUN sed -i 's/GRUB_CMDLINE_LINUX_DEFAULT=.*/GRUB_CMDLINE_LINUX_DEFAULT="quiet splash loglevel=3 rd.systemd.show_status=false"/' "$GRUB_CFG"
$RUN sed -i 's/GRUB_TERMINAL=console/GRUB_TERMINAL=gfxterm/' "$GRUB_CFG" 2>/dev/null || true
$RUN sed -i 's/#GRUB_GFXMODE=.*/GRUB_GFXMODE=1920x1080,1280x720,auto/' "$GRUB_CFG" 2>/dev/null
$RUN sed -i 's/GRUB_GFXMODE=.*/GRUB_GFXMODE=1920x1080,1280x720,auto/' "$GRUB_CFG" 2>/dev/null
$RUN update-grub 2>/dev/null || true
ok "GRUB configured"

# ────────────────────────────────────────
# 8. Auto-login via LightDM
# ────────────────────────────────────────
info "Configuring auto-login..."
LIGHTDM_CONF="/etc/lightdm/lightdm.conf"
$RUN mkdir -p /etc/lightdm
cat << 'LIGHTDM' | $RUN tee "$LIGHTDM_CONF" > /dev/null
[Seat:*]
autologin-user=edupulse
autologin-user-timeout=0
user-session=openbox
greeter-session=lightdm-gtk-greeter
greeter-hide-users=true
greeter-setup-script=/usr/bin/xset s off && /usr/bin/xset -dpms
LIGHTDM
ok "Auto-login configured"

# ────────────────────────────────────────
# 9. Openbox autostart → EduPulse scanner
# ────────────────────────────────────────
info "Configuring Openbox to launch EduPulse..."
$RUN mkdir -p /home/edupulse/.config/openbox
cat << 'OPENBOX' | $RUN tee /home/edupulse/.config/openbox/autostart > /dev/null
#!/bin/bash
# EduPulse Kiosk — auto-start scanner on login

# Disable screen blanking and power management
xset s off
xset -dpms
xset s noblank

# Hide mouse cursor after 1s
unclutter -idle 1 -root &

# Launch EduPulse scanner
cd /opt/edupulse/scanner
python3 scanner.py
OPENBOX
$RUN chmod +x /home/edupulse/.config/openbox/autostart
$RUN chown -R edupulse:edupulse /home/edupulse/.config
ok "Openbox autostart configured"

# ────────────────────────────────────────
# 10. Disable sleep / power management
# ────────────────────────────────────────
info "Disabling system sleep..."
$RUN systemctl mask sleep.target suspend.target hibernate.target hybrid-sleep.target 2>/dev/null || true
ok "Sleep disabled"

# ────────────────────────────────────────
# 11. Create systemd service for server.js
# ────────────────────────────────────────
info "Creating EduPulse API server service..."
cat << 'SERVICE' | $RUN tee /etc/systemd/system/edupulse-api.service > /dev/null
[Unit]
Description=EduPulse Attendance API Server
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

if command -v node &>/dev/null; then
    $RUN systemctl daemon-reload
    $RUN systemctl enable edupulse-api.service 2>/dev/null || true
    ok "API service created"
else
    info "Node.js not found — install it to enable the API server:"
    info "  curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && apt install -y nodejs"
fi

# ────────────────────────────────────────
# 12. Boot optimization
# ────────────────────────────────────────
info "Optimizing boot time..."
$RUN systemctl disable bluetooth.service 2>/dev/null || true
$RUN systemctl disable cups.service 2>/dev/null || true
$RUN systemctl disable avahi-daemon.service 2>/dev/null || true
$RUN systemctl disable whoopsie.service 2>/dev/null || true
ok "Non-essential services disabled"

# ────────────────────────────────────────
echo ""
info "============================================"
info "  EduPulse Kiosk Setup Complete!"
info "============================================"
echo ""
info "Reboot to start the EduPulse kiosk:"
info "  sudo reboot"
echo ""
info "To return to a normal desktop, run:"
info "  sudo systemctl set-default multi-user.target"
echo ""
