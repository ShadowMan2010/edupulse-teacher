#!/usr/bin/env bash
# EduPulse OTA Update Script
# Checks GitHub for new version, pulls, and restarts services
# Usage: sudo bash os/ota-update.sh [--check] [--force]

set -e

REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
VERSION_FILE="$REPO_DIR/version.json"
SERVICE_NAME="edupulse-api"
SCANNER_NAME="edupulse-scanner"
BOT_NAME="edupulse-bot"
TARGET_NAME="edupulse.target"
UPDATE_URL="https://raw.githubusercontent.com/$(git -C "$REPO_DIR" config --get remote.origin.url 2>/dev/null | sed 's|https://github.com/||; s|\.git$||' 2>/dev/null || echo 'user/edupulse')/main/version.json"
BACKUP_DIR="/tmp/edupulse-backup"

log() { echo "[OTA] $(date '+%H:%M:%S') $*"; }

error() { log "ERROR: $*"; exit 1; }

get_local_version() {
    if [ -f "$VERSION_FILE" ]; then
        python3 -c "import json; print(json.load(open('$VERSION_FILE'))['version'])" 2>/dev/null || echo 0
    else
        echo 0
    fi
}

get_remote_version() {
    local json
    json=$(curl -s --connect-timeout 5 "$UPDATE_URL" 2>/dev/null)
    if [ -n "$json" ]; then
        echo "$json" | python3 -c "import json,sys; print(json.load(sys.stdin)['version'])" 2>/dev/null || echo 0
    else
        echo 0
    fi
}

check_update() {
    local local_ver remote_ver
    local_ver=$(get_local_version)
    remote_ver=$(get_remote_version)
    echo "$local_ver" "$remote_ver"
}

do_update() {
    log "Starting update..."

    # Backup critical data
    mkdir -p "$BACKUP_DIR"
    if [ -f "$REPO_DIR/edupulse.db" ]; then
        cp "$REPO_DIR/edupulse.db" "$BACKUP_DIR/edupulse.db"
        log "Backed up database"
    fi

    # Stop services via target
    log "Stopping services..."
    systemctl stop "$TARGET_NAME" 2>/dev/null || {
        systemctl stop "$SERVICE_NAME" 2>/dev/null || true
        systemctl stop "$SCANNER_NAME" 2>/dev/null || true
        systemctl stop "$BOT_NAME" 2>/dev/null || true
    }
    pkill -f "scanner.py" 2>/dev/null || true
    pkill -f "server.js" 2>/dev/null || true
    pkill -f "bot.py" 2>/dev/null || true
    sleep 2

    # Pull latest code
    log "Pulling latest code..."
    cd "$REPO_DIR"
    git fetch origin 2>/dev/null || log "Git fetch failed, trying direct download..."

    if git fetch origin 2>/dev/null; then
        git reset --hard origin/main 2>/dev/null || git reset --hard origin/master 2>/dev/null || {
            log "Git pull failed, downloading archive..."
            download_update
        }
    else
        download_update
    fi

    # Install npm dependencies
    if [ -f "$REPO_DIR/package.json" ]; then
        log "Installing npm dependencies..."
        cd "$REPO_DIR"
        npm install --production 2>/dev/null || log "npm install had issues"
    fi

    # Install Python dependencies
    if [ -f "$REPO_DIR/scanner/requirements.txt" ]; then
        log "Installing Python dependencies..."
        pip3 install -r "$REPO_DIR/scanner/requirements.txt" 2>/dev/null || log "pip install had issues"
    fi

    # Restore database backup if needed
    if [ -f "$BACKUP_DIR/edupulse.db" ] && [ ! -f "$REPO_DIR/edupulse.db" ]; then
        cp "$BACKUP_DIR/edupulse.db" "$REPO_DIR/edupulse.db"
        log "Restored database from backup"
    fi

    # Set permissions
    chmod 600 "$REPO_DIR"/scanner/*-key.json 2>/dev/null || true
    chmod +x "$REPO_DIR/os/"*.sh 2>/dev/null || true

    # Restart services via target
    log "Starting services..."
    systemctl daemon-reload 2>/dev/null || true
    systemctl enable "$TARGET_NAME" 2>/dev/null || true
    systemctl start "$TARGET_NAME" 2>/dev/null || {
        systemctl start "$SERVICE_NAME" 2>/dev/null || {
            log "Starting API server directly..."
            cd "$REPO_DIR"
            nohup node server.js > /tmp/edupulse-api.log 2>&1 &
        }
        systemctl start "$SCANNER_NAME" 2>/dev/null || {
            log "Scanner service not found, start manually: python3 scanner/scanner.py"
        }
        systemctl start "$BOT_NAME" 2>/dev/null || {
            log "Bot service not found, start manually: python3 bot/bot.py"
        }
    }

    log "Update complete!"
}

download_update() {
    local url
    url=$(git -C "$REPO_DIR" config --get remote.origin.url 2>/dev/null || echo "")
    if [ -z "$url" ]; then
        error "No git remote configured. Cannot download update."
    fi
    local repo_path
    repo_path=$(echo "$url" | sed 's|https://github.com/||; s|\.git$||')
    local archive_url="https://github.com/$repo_path/archive/refs/heads/main.zip"
    log "Downloading from $archive_url"
    curl -sL "$archive_url" -o /tmp/edupulse-update.zip
    unzip -o /tmp/edupulse-update.zip -d /tmp/edupulse-update/
    cp -r /tmp/edupulse-update/*/.[!.]* "$REPO_DIR/" 2>/dev/null || true
    cp -r /tmp/edupulse-update/*/* "$REPO_DIR/"
    rm -rf /tmp/edupulse-update /tmp/edupulse-update.zip
}

case "${1:-}" in
    --check)
        read -r local_ver remote_ver <<< "$(check_update)"
        echo "Local version: $local_ver"
        echo "Remote version: $remote_ver"
        if [ "$remote_ver" -gt "$local_ver" ]; then
            echo "Update available!"
            exit 2
        else
            echo "Up to date."
            exit 0
        fi
        ;;
    --force)
        do_update
        ;;
    *)
        read -r local_ver remote_ver <<< "$(check_update)"
        if [ "$remote_ver" -gt "$local_ver" ]; then
            log "Update available (v$local_ver -> v$remote_ver)"
            do_update
        else
            log "Up to date (v$local_ver)"
        fi
        ;;
esac
