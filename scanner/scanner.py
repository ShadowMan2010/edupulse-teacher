#!/usr/bin/env python3
"""
EduPulse QR Scanner — PyQt5 Desktop App
Full-screen kiosk mode with glassmorphism UI, camera QR scanning,
and real-time attendance marking via local API.
"""

import sys
import os
import json
import time
import threading
import requests
import numpy as np
from datetime import datetime

from PyQt5.QtCore import (
    Qt, QTimer, QThread, pyqtSignal, QRect, QPoint, QPointF, QPropertyAnimation,
    QEasingCurve, QRectF
)
from PyQt5.QtGui import (
    QPixmap, QImage, QPainter, QColor, QPen, QFont, QLinearGradient,
    QRadialGradient, QBrush, QPainterPath, QFontDatabase
)
from PyQt5.QtMultimedia import QMediaPlayer, QMediaContent
from PyQt5.QtCore import QUrl

from PyQt5.QtWidgets import (
    QApplication, QMainWindow, QWidget, QLabel, QVBoxLayout, QHBoxLayout,
    QPushButton, QFrame, QGraphicsDropShadowEffect, QStackedWidget,
    QSizePolicy, QSpacerItem
)
from PyQt5.QtTest import QTest

try:
    import cv2
except ImportError:
    cv2 = None

try:
    from pyzbar.pyzbar import decode as qr_decode
except ImportError:
    qr_decode = None

# ── Config ──
# Set CLOUD_API to True to use Firebase Cloud Functions, False for local server
CLOUD_API = False
CLOUD_URL = "https://us-central1-cyberpunk-attendance.cloudfunctions.net/api"
LOCAL_URL = "http://localhost:3000/api"
API_BASE = CLOUD_URL if CLOUD_API else LOCAL_URL
SOUNDS_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'sounds')
SCAN_INTERVAL_MS = 300  # Check for QR every 300ms
DUPLICATE_COOLDOWN = 2  # Seconds before allowing same student re-scan
# Camera source: 0 for built-in webcam, or IP camera URL:
# DroidCam: "http://<phone-ip>:4747/video"
# IP Webcam: "http://<phone-ip>:8080/video"
CAMERA_SOURCE = os.environ.get('EDUPULSE_CAMERA', "0")
# Display rotation: 0, 90, 180, 270 (set for portrait/landscape)
DISPLAY_ROTATION = int(os.environ.get('EDUPULSE_ROTATION', '0'))


class MjpegReader(threading.Thread):
    """Reads MJPEG stream and stores the latest frame."""
    def __init__(self, url):
        super().__init__(daemon=True)
        self.url = url
        self.running = True
        self.latest_frame = None
        self.buffer = b''

    def run(self):
        try:
            resp = requests.get(self.url, stream=True, timeout=10)
            resp.raise_for_status()
            print(f"[MJPEG] Connected")
            frame_count = 0
            for chunk in resp.iter_content(chunk_size=4096):
                if not self.running:
                    break
                self.buffer += chunk
                while True:
                    start = self.buffer.find(b'\xff\xd8')
                    end = self.buffer.find(b'\xff\xd9')
                    if start != -1 and end != -1 and end > start:
                        jpeg = self.buffer[start:end+2]
                        self.buffer = self.buffer[end+2:]
                        arr = np.frombuffer(jpeg, dtype=np.uint8)
                        frame = cv2.imdecode(arr, cv2.IMREAD_COLOR)
                        if frame is not None:
                            frame_count += 1
                            self.latest_frame = frame
                            if frame_count % 30 == 0:
                                print(f"[MJPEG] {frame_count} frames")
                    else:
                        break
        except Exception as e:
            print(f"[MJPEG] Error: {e}")

    def stop(self):
        self.running = False


class CameraThread(QThread):
    """Polls local camera or MJPEG reader for frames."""
    frame_ready = pyqtSignal(object)

    def __init__(self):
        super().__init__()
        self.running = True
        self.cap = None
        self.mjpeg = None
        self._is_mjpeg = False

    def run(self):
        if cv2 is None:
            return
        src = int(CAMERA_SOURCE) if CAMERA_SOURCE.isdigit() else CAMERA_SOURCE

        if isinstance(src, int):
            self._is_mjpeg = False
            print(f"[Camera] Opening local: {src}")
            self.cap = cv2.VideoCapture(src)
            if not self.cap.isOpened():
                print(f"[Camera] FAILED")
                return
            self.cap.set(cv2.CAP_PROP_FRAME_WIDTH, 1280)
            self.cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 720)
            count = 0
            while self.running and self.cap.isOpened():
                ret, frame = self.cap.read()
                if ret:
                    count += 1
                    self.frame_ready.emit(frame)
                else:
                    break
                self.msleep(30)
            self.cap.release()
            print(f"[Camera] Stopped ({count} frames)")
        else:
            self._is_mjpeg = True
            print(f"[Camera] Starting MJPEG reader")
            self.mjpeg = MjpegReader(src)
            self.mjpeg.start()
            count = 0
            while self.running:
                f = self.mjpeg.latest_frame
                if f is not None:
                    count += 1
                    self.frame_ready.emit(f)
                    if count == 1:
                        print(f"[Camera] First frame via MJPEG: {f.shape}")
                self.msleep(30)
            self.mjpeg.stop()

    def stop(self):
        self.running = False

    def stop(self):
        self.running = False
        if self.cap:
            self.cap.release()


class Particle:
    """Floating particle for ambient effect."""
    def __init__(self, w, h):
        self.x = __import__('random').uniform(0, w)
        self.y = __import__('random').uniform(0, h)
        self.vx = __import__('random').uniform(-0.3, 0.3)
        self.vy = __import__('random').uniform(-0.3, 0.3)
        self.size = __import__('random').uniform(1.5, 4)
        self.alpha = __import__('random').uniform(0.1, 0.3)

    def update(self, w, h):
        self.x += self.vx
        self.y += self.vy
        if self.x < 0 or self.x > w: self.vx *= -1
        if self.y < 0 or self.y > h: self.vy *= -1


class ScannerOverlay(QWidget):
    """Transparent overlay with scan brackets, beam, and particles."""
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setAttribute(Qt.WA_TransparentForMouseEvents)
        self.scan_progress = 0
        self.beam_direction = 1
        self.particles = [Particle(800, 600) for _ in range(15)]
        self._timer = QTimer(self)
        self._timer.timeout.connect(self._animate)
        self._timer.start(30)

    def _animate(self):
        self.scan_progress += self.beam_direction * 1.5
        if self.scan_progress >= 100 or self.scan_progress <= 0:
            self.beam_direction *= -1
        for p in self.particles:
            p.update(self.width(), self.height())
        self.update()

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.Antialiasing)
        w, h = self.width(), self.height()
        scan_size = min(w, h) * 0.6
        cx, cy = w // 2, h // 2
        half = scan_size / 2

        # Darkened vignette
        vignette = QRadialGradient(cx, cy, half * 1.2)
        vignette.setColorAt(0, QColor(0, 0, 0, 0))
        vignette.setColorAt(0.7, QColor(0, 0, 0, 30))
        vignette.setColorAt(1, QColor(0, 0, 0, 120))
        painter.fillRect(0, 0, w, h, vignette)

        # Scan area highlight
        scan_rect = QRectF(cx - half, cy - half, scan_size, scan_size)
        path = QPainterPath()
        path.addRoundedRect(scan_rect, 16, 16)
        painter.setPen(QPen(QColor(0, 255, 255, 40), 1))
        painter.setBrush(QColor(0, 255, 255, 8))
        painter.drawPath(path)

        # Corner brackets
        bracket_len = 30
        bracket_color = QColor(0, 255, 255, 200)
        pen = QPen(bracket_color, 3)
        painter.setPen(pen)

        # Top-left
        painter.drawLine(int(cx - half), int(cy - half), int(cx - half + bracket_len), int(cy - half))
        painter.drawLine(int(cx - half), int(cy - half), int(cx - half), int(cy - half + bracket_len))
        # Top-right
        painter.drawLine(int(cx + half), int(cy - half), int(cx + half - bracket_len), int(cy - half))
        painter.drawLine(int(cx + half), int(cy - half), int(cx + half), int(cy - half + bracket_len))
        # Bottom-left
        painter.drawLine(int(cx - half), int(cy + half), int(cx - half + bracket_len), int(cy + half))
        painter.drawLine(int(cx - half), int(cy + half), int(cx - half), int(cy + half - bracket_len))
        # Bottom-right
        painter.drawLine(int(cx + half), int(cy + half), int(cx + half - bracket_len), int(cy + half))
        painter.drawLine(int(cx + half), int(cy + half), int(cx + half), int(cy + half - bracket_len))

        # Scanning beam
        beam_y = int(cy - half + (scan_size * self.scan_progress / 100))
        beam_grad = QLinearGradient(0, beam_y - 15, 0, beam_y + 15)
        beam_grad.setColorAt(0, QColor(0, 255, 255, 0))
        beam_grad.setColorAt(0.4, QColor(0, 255, 255, 120))
        beam_grad.setColorAt(0.5, QColor(0, 255, 255, 180))
        beam_grad.setColorAt(0.6, QColor(0, 255, 255, 120))
        beam_grad.setColorAt(1, QColor(0, 255, 255, 0))
        painter.fillRect(int(cx - half), beam_y - 15, int(scan_size), 30, beam_grad)

        # Particles
        for p in self.particles:
            painter.setPen(Qt.NoPen)
            painter.setBrush(QColor(0, 255, 255, int(p.alpha * 255)))
            painter.drawEllipse(QPointF(p.x, p.y), p.size, p.size)

        painter.end()


class Card3DWidget(QWidget):
    """3D flipping ID card with particle burst after scan."""
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setAttribute(Qt.WA_TransparentForMouseEvents, False)
        self.setFixedSize(420, 520)
        self.angle = 0.0
        self.target_angle = 0.0
        self.student = None
        self.status = ''
        self.particles = []
        self.glow_intensity = 0.0
        self._timer = QTimer(self)
        self._timer.timeout.connect(self._tick)
        self._timer.start(16)
        self.hide()

    def flip_in(self, student, status, timing=None):
        self.student = student
        self.status = status
        self.timing = timing or {}
        self.scan_time = datetime.now().strftime("%H:%M:%S")
        self.angle = 0.0
        self.target_angle = 180.0
        self.glow_intensity = 1.0
        self._photo_pix = None
        photo = student.get('photo', '')
        if photo:
            try:
                u = photo if not photo.startswith('/') else f"{API_BASE.replace('/api', '')}{photo}"
                self._photo_pix = QPixmap()
                self._photo_pix.loadFromData(requests.get(u, timeout=3).content)
            except:
                pass
        r = __import__('random')
        self.particles = []
        for _ in range(40):
            a = r.uniform(0, 6.283)
            sp = r.uniform(2, 8)
            self.particles.append({
                'x': 210, 'y': 260, 'vx': np.cos(a) * sp, 'vy': np.sin(a) * sp,
                'size': r.uniform(2, 6), 'alpha': 1.0, 'decay': r.uniform(0.01, 0.03)
            })
        self.show()
        self.raise_()
        if not self._timer.isActive():
            self._timer.start()
        # Auto-dismiss after 4s
        QTimer.singleShot(4000, self._auto_dismiss)

    def _auto_dismiss(self):
        if self.isVisible():
            self.hide()

    def _tick(self):
        if self.angle < self.target_angle:
            self.angle = min(self.angle + 12, self.target_angle)
            self.glow_intensity = max(0, self.glow_intensity - 0.03)
        for p in list(self.particles):
            p['x'] += p['vx']
            p['y'] += p['vy']
            p['vy'] += 0.05
            p['alpha'] -= p['decay']
            if p['alpha'] <= 0:
                self.particles.remove(p)
        if self.particles or self.angle < self.target_angle:
            self.update()
        elif self.angle >= self.target_angle and not self.particles:
            self._timer.stop()

    def paintEvent(self, event):
        if not self.student:
            return
        painter = QPainter(self)
        painter.setRenderHint(QPainter.Antialiasing)
        w, h = 340, 440
        cx, cy = (self.width() - w) // 2, (self.height() - h) // 2 + 20

        # Glow burst
        if self.glow_intensity > 0:
            g = QRadialGradient(cx + w//2, cy + h//2, w * 0.7)
            g.setColorAt(0, QColor(0, 255, 255, int(self.glow_intensity * 60)))
            g.setColorAt(1, Qt.transparent)
            painter.fillRect(self.rect(), g)

        # Particles
        for p in self.particles:
            painter.setPen(Qt.NoPen)
            painter.setBrush(QColor(0, 255, 255, int(p['alpha'] * 220)))
            painter.drawEllipse(QPointF(p['x'], p['y']), p['size'], p['size'])

        # 3D flip
        theta = min(self.angle, 180)
        sx = np.cos(np.radians(theta))
        if sx < 0.01:
            sx = 0.01

        # Render card face to pixmap
        face = QPixmap(w, h)
        face.fill(Qt.transparent)
        fp = QPainter(face)
        fp.setRenderHint(QPainter.Antialiasing)

        # Background
        bg = QRadialGradient(w//2, h//2, w*0.6)
        bg.setColorAt(0, QColor(20, 22, 26))
        bg.setColorAt(0.8, QColor(10, 10, 12))
        bg.setColorAt(1, QColor(5, 5, 8))
        fp.setBrush(bg)
        fp.setPen(QPen(QColor(0, 255, 255, 180), 2))
        fp.drawRoundedRect(1, 1, w-2, h-2, 18, 18)

        # Neon top accent
        ac = QLinearGradient(0, 0, w, 0)
        ac.setColorAt(0, QColor(0, 255, 255, 0))
        ac.setColorAt(0.5, QColor(0, 255, 255, 120))
        ac.setColorAt(1, QColor(0, 255, 255, 0))
        fp.setBrush(ac)
        fp.setPen(Qt.NoPen)
        fp.drawRoundedRect(10, 1, w-20, 3, 2, 2)

        # Title
        fp.setPen(QColor(0, 255, 255, 160))
        f = QFont("Orbitron", 10, QFont.Bold)
        fp.setFont(f)
        fp.drawText(QRect(0, 18, w, 24), Qt.AlignCenter, "⚡ EDUPULSE")

        # Photo
        pix = self._photo_pix if hasattr(self, '_photo_pix') else None
        if pix and not pix.isNull():
            pp = pix.scaled(130, 130, Qt.KeepAspectRatioByExpanding, Qt.SmoothTransformation)
            m = QPixmap(130, 130)
            m.fill(Qt.transparent)
            mp = QPainter(m)
            mp.setBrush(Qt.white)
            mp.setPen(Qt.NoPen)
            mp.drawEllipse(0, 0, 130, 130)
            mp.end()
            pp.setMask(m.mask())
            fp.drawPixmap((w-130)//2, 50, 130, 130, pp)
            fp.setPen(QPen(QColor(0, 255, 255, 80), 2))
            fp.setBrush(Qt.NoBrush)
            fp.drawEllipse((w-130)//2, 50, 130, 130)
        else:
            fp.setBrush(QColor(0, 255, 255, 20))
            fp.setPen(QPen(QColor(0, 255, 255, 60), 2))
            fp.drawEllipse((w-130)//2, 50, 130, 130)
            nm = self.student.get('name', '?')
            fp.setPen(QColor(0, 255, 255, 120))
            f2 = QFont("Orbitron", 40, QFont.Bold)
            fp.setFont(f2)
            fp.drawText(QRect((w-130)//2, 50, 130, 130), Qt.AlignCenter, nm[0].upper() if nm else '?')

        # Name
        fp.setPen(QColor(248, 250, 252))
        f3 = QFont("Segoe UI", 20, QFont.Bold)
        fp.setFont(f3)
        fp.drawText(QRect(20, 200, w-40, 36), Qt.AlignCenter, self.student.get('name', 'Unknown'))

        # Info
        info = f"{self.student.get('roll_no', '')}  ·  Class {self.student.get('class', '')}"
        if self.student.get('section'):
            info += f"  ·  Section {self.student['section']}"
        fp.setPen(QColor(148, 163, 184))
        f4 = QFont("Segoe UI", 12)
        fp.setFont(f4)
        fp.drawText(QRect(20, 238, w-40, 24), Qt.AlignCenter, info)

        # Status badge
        if self.status:
            status_colors = {
                'present':  (QColor(34, 197, 94, 40),  QColor(34, 197, 94),  "✓ ON TIME"),
                'late':     (QColor(245, 158, 11, 40), QColor(245, 158, 11), "⚠ MARKED LATE"),
                'half-day': (QColor(255, 193, 7, 40),  QColor(255, 193, 7),  "◐ HALF DAY"),
                'early':    (QColor(96, 165, 250, 40), QColor(96, 165, 250), "◈ EARLY"),
                'absent':   (QColor(239, 68, 68, 40),  QColor(239, 68, 68),  "✕ ABSENT"),
                'duplicate':(QColor(148, 163, 184, 40),QColor(148, 163, 184),"◈ ALREADY SCANNED"),
            }
            bc, fc, txt = status_colors.get(self.status, (QColor(239, 68, 68, 40), QColor(239, 68, 68), f"✕ {self.status.upper()}"))
            fp.setBrush(bc)
            fp.setPen(QPen(fc, 1))
            fp.drawRoundedRect(60, 288, w-120, 36, 18, 18)
            fp.setPen(fc)
            f5 = QFont("Segoe UI", 12, QFont.Bold)
            fp.setFont(f5)
            fp.drawText(QRect(60, 288, w-120, 36), Qt.AlignCenter, txt)

        # Scan time
        fp.setPen(QColor(148, 163, 184, 160))
        f6 = QFont("Segoe UI", 10)
        fp.setFont(f6)
        fp.drawText(QRect(60, 330, w-120, 20), Qt.AlignCenter, f"Scanned at {self.scan_time}")

        # Timing message
        timing_msg = self.timing.get('message', '')
        if timing_msg:
            fp.setPen(QColor(148, 163, 184, 100))
            f7 = QFont("Segoe UI", 9)
            fp.setFont(f7)
            fp.drawText(QRect(60, 350, w-120, 18), Qt.AlignCenter, timing_msg)

        # Footer
        fp.setPen(QColor(0, 255, 255, 40))
        fp.drawLine(60, h-50, w-60, h-50)
        f8 = QFont("Orbitron", 7)
        fp.setFont(f8)
        fp.setPen(QColor(148, 163, 184, 80))
        fp.drawText(QRect(0, h-44, w, 20), Qt.AlignCenter, "EDUPULSE ATTENDANCE SYSTEM v1.0")
        fp.end()

        # Perspective draw
        off = (1 - sx) * (w // 2)
        dst = QRectF(cx + off, cy, w * sx, h)
        src = QRectF(0, 0, w, h)
        painter.drawPixmap(dst, face, src)

        # Scan line during flip
        if self.angle < self.target_angle:
            progress = self.angle / self.target_angle
            sy = int(cy + h * progress)
            grad = QLinearGradient(0, sy-10, 0, sy+10)
            grad.setColorAt(0, Qt.transparent)
            grad.setColorAt(0.4, QColor(0, 255, 255, 60))
            grad.setColorAt(0.5, QColor(0, 255, 255, 100))
            grad.setColorAt(0.6, QColor(0, 255, 255, 60))
            grad.setColorAt(1, Qt.transparent)
            painter.fillRect(int(cx + off), sy-10, int(w * sx), 20, grad)

        painter.end()


class BootScreen(QWidget):
    """Full-screen boot animation with EduPulse branding."""
    def __init__(self):
        super().__init__()
        self.setWindowTitle("EduPulse")
        self.setCursor(Qt.BlankCursor)
        self.showFullScreen()
        self.setStyleSheet("background-color: #0a0a0b;")
        self.progress = 0
        self.beam_y = -20
        self.dots = 0
        self._timer = QTimer(self)
        self._timer.timeout.connect(self._tick)
        self._timer.start(30)

    def _tick(self):
        self.progress = min(self.progress + 0.8, 100)
        self.beam_y += 3
        if self.beam_y > self.height() + 20:
            self.beam_y = -20
        if self.progress < 100:
            self.dots = (self.dots + 1) % 4
        self.update()
        if self.progress >= 100:
            self._timer.stop()
            QTimer.singleShot(800, self._finish)

    def _finish(self):
        self.close()

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.Antialiasing)
        w, h = self.width(), self.height()

        # Grid dots
        painter.setPen(Qt.NoPen)
        for x in range(0, w, 40):
            for y in range(0, h, 40):
                painter.setBrush(QColor(0, 255, 255, 15))
                painter.drawEllipse(x, y, 2, 2)

        # Scan beam
        grad = QLinearGradient(0, self.beam_y - 30, 0, self.beam_y + 30)
        grad.setColorAt(0, Qt.transparent)
        grad.setColorAt(0.4, QColor(0, 255, 255, 40))
        grad.setColorAt(0.5, QColor(0, 255, 255, 80))
        grad.setColorAt(0.6, QColor(0, 255, 255, 40))
        grad.setColorAt(1, Qt.transparent)
        painter.fillRect(0, self.beam_y - 30, w, 60, grad)

        # Glow blob behind logo
        glow = QRadialGradient(w//2, h//2 - 60, 300)
        glow.setColorAt(0, QColor(0, 255, 255, 30))
        glow.setColorAt(0.5, QColor(0, 255, 255, 8))
        glow.setColorAt(1, Qt.transparent)
        painter.fillRect(0, 0, w, h, glow)

        # Logo / brand
        painter.setPen(QColor(0, 255, 255, 180))
        font_logo = QFont("Orbitron", 56, QFont.Bold)
        painter.setFont(font_logo)
        painter.drawText(QRect(0, h//2 - 100, w, 80), Qt.AlignCenter, "EduPulse")

        # Tagline
        painter.setPen(QColor(148, 163, 184, 120))
        font_tag = QFont("Segoe UI", 14)
        painter.setFont(font_tag)
        painter.drawText(QRect(0, h//2 - 20, w, 30), Qt.AlignCenter, "ATTENDANCE SYSTEM")

        # Loading bar
        bar_w, bar_h = 300, 3
        bx, by = (w - bar_w) // 2, h//2 + 40
        painter.setBrush(QColor(0, 255, 255, 30))
        painter.setPen(Qt.NoPen)
        painter.drawRoundedRect(bx, by, bar_w, bar_h, 2, 2)
        fill_w = int(bar_w * self.progress / 100)
        if fill_w > 0:
            grad2 = QLinearGradient(bx, 0, bx + bar_w, 0)
            grad2.setColorAt(0, QColor(0, 255, 255, 60))
            grad2.setColorAt(0.5, QColor(0, 255, 255, 200))
            grad2.setColorAt(1, QColor(0, 255, 255, 60))
            painter.setBrush(grad2)
            painter.drawRoundedRect(bx, by, fill_w, bar_h, 2, 2)

        # Loading text
        dots_str = '.' * self.dots
        painter.setPen(QColor(148, 163, 184, 100))
        font_load = QFont("Orbitron", 9)
        painter.setFont(font_load)
        painter.drawText(QRect(0, by + 12, w, 20), Qt.AlignCenter, f"INITIALIZING{dots_str}")

        # Version
        painter.setPen(QColor(148, 163, 184, 60))
        font_ver = QFont("Orbitron", 8)
        painter.setFont(font_ver)
        painter.drawText(QRect(0, h - 40, w, 20), Qt.AlignCenter, "v1.0.0  ·  CYBERPUNK EDITION")

        # Corner brackets
        pen = QPen(QColor(0, 255, 255, 60), 2)
        painter.setPen(pen)
        bracket = 30
        m = 20
        painter.drawLine(m, m, m + bracket, m)
        painter.drawLine(m, m, m, m + bracket)
        painter.drawLine(w - m - bracket, m, w - m, m)
        painter.drawLine(w - m, m, w - m, m + bracket)
        painter.drawLine(m, h - m, m + bracket, h - m)
        painter.drawLine(m, h - m, m, h - m - bracket)
        painter.drawLine(w - m - bracket, h - m, w - m, h - m)
        painter.drawLine(w - m, h - m, w - m, h - m - bracket)

        painter.end()


# ── Sound ──
_sound_player = None
def init_sound():
    global _sound_player
    _sound_player = QMediaPlayer()
def play_sound(name):
    path = os.path.join(SOUNDS_DIR, name)
    if os.path.exists(path) and _sound_player:
        _sound_player.setMedia(QMediaContent(QUrl.fromLocalFile(path)))
        _sound_player.play()

class Desktop(QWidget):
    """Full-screen OS-like desktop shell — taskbar, dock, app launcher."""
    def __init__(self):
        super().__init__()
        self.setWindowTitle("EduPulse OS")
        self.setCursor(Qt.BlankCursor)
        self.showFullScreen()

        # Kiosk env overrides
        self._rotation = DISPLAY_ROTATION
        if self._rotation:
            self._apply_rotation()

        self.scanned_students = {}
        self.current_frame = None
        self.qr_found = False

        self._init_ui()
        self._start_clock()
        self._check_api()
        self._fetch_settings()
        self._start_camera()
        init_sound()

    def _apply_rotation(self):
        """Apply display rotation via xrandr (only on X11)."""
        try:
            import subprocess
            displays = subprocess.check_output(
                ["xrandr", "--query"], text=True
            ).splitlines()
            for line in displays:
                if " connected " in line:
                    disp = line.split()[0]
                    rot_map = {90: "left", 180: "inverted", 270: "right"}
                    rot = rot_map.get(self._rotation, "normal")
                    subprocess.run(
                        ["xrandr", "--output", disp, "--rotate", rot],
                        capture_output=True
                    )
                    break
        except Exception:
            pass

    def _init_ui(self):
        self.setStyleSheet("background-color: #0a0a0b;")

        main = QVBoxLayout(self)
        main.setContentsMargins(0, 0, 0, 0)
        main.setSpacing(0)

        # ── Taskbar ──
        taskbar = QWidget()
        taskbar.setFixedHeight(44)
        taskbar.setStyleSheet("background: rgba(8, 8, 10, 0.92); border-bottom: 1px solid rgba(0, 255, 255, 0.12);")
        tb = QHBoxLayout(taskbar)
        tb.setContentsMargins(16, 0, 16, 0)

        # Left: brand
        brand = QLabel("EduPulse")
        brand.setStyleSheet("font-size: 15px; font-weight: 700; color: #00ffff; font-family: Orbitron; letter-spacing: 3px;")
        tb.addWidget(brand)

        # Center: clock
        self.clock_label = QLabel()
        self.clock_label.setStyleSheet("font-size: 14px; color: #94a3b8; font-family: monospace; padding: 0 20px;")
        tb.addWidget(self.clock_label, alignment=Qt.AlignCenter)

        # Right: statuses
        self.timing_label = QLabel()
        self.timing_label.setStyleSheet("font-size: 10px; font-family: monospace; color: #94a3b8; padding: 0 12px;")
        tb.addWidget(self.timing_label, alignment=Qt.AlignRight)

        self.api_status = QLabel()
        self.api_status.setStyleSheet("font-size: 10px; font-family: monospace; padding: 3px 10px; border-radius: 8px; background: rgba(239,68,68,0.12); color: #ef4444;")
        tb.addWidget(self.api_status, alignment=Qt.AlignRight)

        self.scan_count_label = QLabel("SCANS: 0")
        self.scan_count_label.setStyleSheet("font-size: 10px; font-family: monospace; color: #00ffff; padding: 0 12px;")
        tb.addWidget(self.scan_count_label, alignment=Qt.AlignRight)

        main.addWidget(taskbar)

        # ── Desktop Area (camera as centerpiece app) ──
        desktop_area = QWidget()
        desktop_area.setStyleSheet("background: #0a0a0b;")
        da_layout = QVBoxLayout(desktop_area)
        da_layout.setContentsMargins(0, 0, 0, 0)

        self.camera_container = QWidget()
        self.camera_container.setStyleSheet("background: #000;")
        cam_layout = QVBoxLayout(self.camera_container)
        cam_layout.setContentsMargins(0, 0, 0, 0)

        self.camera_label = QLabel()
        self.camera_label.setAlignment(Qt.AlignCenter)
        self.camera_label.setStyleSheet("background: transparent;")
        cam_layout.addWidget(self.camera_label)

        da_layout.addWidget(self.camera_container, 1)
        main.addWidget(desktop_area, 1)

        # Overlays
        self.overlay = ScannerOverlay(self.camera_container)
        self.overlay.setGeometry(self.camera_container.rect())
        self.card_3d = Card3DWidget(self.camera_container)

        # ── Desktop icons (bottom-left floating) ──
        self.desktop_icons = QWidget(self)
        self.desktop_icons.setAttribute(Qt.WA_TransparentForMouseEvents, False)
        di_layout = QVBoxLayout(self.desktop_icons)
        di_layout.setContentsMargins(20, 0, 0, 80)
        di_layout.setSpacing(16)
        di_layout.addStretch()

        def make_desk_icon(emoji, text, shortcut):
            btn = QPushButton(f"{emoji}\n{text}")
            btn.setFixedSize(80, 80)
            btn.setStyleSheet("""
                QPushButton {
                    background: rgba(0, 255, 255, 0.06); color: #94a3b8;
                    font-size: 10px; font-family: Orbitron; border: 1px solid rgba(0,255,255,0.12);
                    border-radius: 12px; padding: 4px;
                }
                QPushButton:hover {
                    background: rgba(0, 255, 255, 0.15); color: #00ffff;
                    border-color: rgba(0, 255, 255, 0.4);
                }
            """)
            btn.clicked.connect(lambda: self._launch(shortcut))
            return btn

        self.scan_icon = make_desk_icon("📷", "Scanner", "scanner")
        di_layout.addWidget(self.scan_icon, alignment=Qt.AlignLeft)

        self.reports_icon = make_desk_icon("📊", "Reports", "reports")
        di_layout.addWidget(self.reports_icon, alignment=Qt.AlignLeft)

        self.settings_icon = make_desk_icon("⚙", "Settings", "settings")
        di_layout.addWidget(self.settings_icon, alignment=Qt.AlignLeft)

        self.power_icon = make_desk_icon("⏻", "Shutdown", "shutdown")
        di_layout.addWidget(self.power_icon, alignment=Qt.AlignLeft)

        self.desktop_icons.setLayout(di_layout)

        # ── Dock (bottom) ──
        dock = QWidget()
        dock.setFixedHeight(56)
        dock.setStyleSheet("background: rgba(8, 8, 10, 0.92); border-top: 1px solid rgba(0, 255, 255, 0.12);")
        dk = QHBoxLayout(dock)
        dk.setContentsMargins(20, 0, 20, 0)

        def make_dock_btn(emoji, text, shortcut):
            btn = QPushButton(f"  {emoji}  {text}  ")
            btn.setFixedHeight(38)
            btn.setStyleSheet("""
                QPushButton {
                    background: rgba(0, 255, 255, 0.06); color: #94a3b8;
                    font-size: 11px; font-family: Orbitron; border: 1px solid rgba(0,255,255,0.08);
                    border-radius: 8px; padding: 0 12px;
                }
                QPushButton:hover {
                    background: rgba(0, 255, 255, 0.15); color: #00ffff;
                }
            """)
            btn.clicked.connect(lambda: self._launch(shortcut))
            return btn

        dk.addWidget(make_dock_btn("📷", "Scanner", "scanner"))
        dk.addWidget(make_dock_btn("📊", "Reports", "reports"))
        dk.addWidget(make_dock_btn("⚙", "Settings", "settings"))
        dk.addStretch()
        self.status_msg = QLabel("● Ready — waiting for QR code...")
        self.status_msg.setStyleSheet("font-size: 11px; color: #475569; font-family: monospace;")
        dk.addWidget(self.status_msg, alignment=Qt.AlignCenter)
        dk.addStretch()
        dk.addWidget(make_dock_btn("⏻", "Exit", "shutdown"))

        main.addWidget(dock)

        # QR scan timer
        self.scan_timer = QTimer(self)
        self.scan_timer.timeout.connect(self._process_frame)
        self.scan_timer.start(SCAN_INTERVAL_MS)

        # Desktop particles
        self.desk_particles = [Particle(self.width(), self.height()) for _ in range(25)]
        self.desk_part_timer = QTimer(self)
        self.desk_part_timer.timeout.connect(self._animate_desk)
        self.desk_part_timer.start(50)

    def _animate_desk(self):
        w, h = self.width(), self.height()
        for p in self.desk_particles:
            p.update(w, h)
        self.update()

    def _start_clock(self):
        def tick():
            now = datetime.now()
            self.clock_label.setText(now.strftime("%H:%M:%S  •  %d %b %Y"))
        tick()
        self.clock_timer = QTimer(self)
        self.clock_timer.timeout.connect(tick)
        self.clock_timer.start(1000)

    def _launch(self, app):
        if app == "shutdown":
            self.close()

    def _check_api(self):
        def check():
            try:
                r = requests.get(f"{API_BASE}/health", timeout=3)
                if r.status_code == 200:
                    self.api_status.setText("● API Online")
                    self.api_status.setStyleSheet(
                        "font-size: 10px; font-family: monospace; padding: 3px 10px; border-radius: 8px; "
                        "background: rgba(34,197,94,0.12); color: #22c55e;"
                    )
                else:
                    raise Exception("Bad status")
            except Exception:
                self.api_status.setText("✕ API Offline")
                self.api_status.setStyleSheet(
                    "font-size: 10px; font-family: monospace; padding: 3px 10px; border-radius: 8px; "
                    "background: rgba(239,68,68,0.12); color: #ef4444;"
                )
            QTimer.singleShot(10000, self._check_api)

        thread = threading.Thread(target=check, daemon=True)
        thread.start()

    def _fetch_settings(self):
        def do_fetch():
            try:
                r = requests.get(f"{API_BASE}/settings", timeout=3)
                if r.status_code == 200:
                    data = r.json().get('data', {})
                    start = data.get('school_start_time', '08:30')
                    end = data.get('entry_end_time', '16:00')
                    late = data.get('late_threshold_minutes', '30')
                    half = data.get('half_day_cutoff', '12:00')
                    msg = f"⏰ {start}–{end}  |  Late >{late}min  |  Half >{half}"
                    self.timing_label.setText(msg)
            except:
                pass

        threading.Thread(target=do_fetch, daemon=True).start()
        QTimer.singleShot(30000, self._fetch_settings)  # Refresh every 30s

    def _start_camera(self):
        if cv2 is None:
            self.status_msg.setText("✕ OpenCV not installed — no camera available")
            return
        self.cam_thread = CameraThread()
        self.cam_thread.frame_ready.connect(self._on_frame)
        self.cam_thread.start()

    def _on_frame(self, frame):
        self.current_frame = frame
        h, w, ch = frame.shape
        qimg = QImage(frame.data, w, h, ch * w, QImage.Format_RGB888).rgbSwapped()
        pix = QPixmap.fromImage(qimg)
        scaled = pix.scaled(
            self.camera_label.width(), self.camera_label.height(),
            Qt.KeepAspectRatio, Qt.SmoothTransformation
        )
        self.camera_label.setPixmap(scaled)

    def _process_frame(self):
        if self.current_frame is None or qr_decode is None:
            return
        frame = self.current_frame
        try:
            decoded = qr_decode(frame)
        except Exception:
            return
        if not decoded:
            return
        for obj in decoded:
            try:
                data = obj.data.decode('utf-8')
                payload = json.loads(data)
                student_id = payload.get('student_id')
            except (json.JSONDecodeError, UnicodeDecodeError):
                student_id = None
            if not student_id:
                continue
            now = time.time()
            if student_id in self.scanned_students:
                if now - self.scanned_students[student_id] < DUPLICATE_COOLDOWN:
                    continue
            self.scanned_students[student_id] = now
            self._mark_attendance(student_id)

    def _mark_attendance(self, student_id):
        def do_mark():
            try:
                resp = requests.post(
                    f"{API_BASE}/attendance/scan",
                    json={"student_id": student_id},
                    timeout=5
                )
                data = resp.json()
                if data.get('success'):
                    student = data.get('student', {})
                    status = data.get('attendance', {}).get('status', 'present')
                    timing = data.get('timing', {})
                    play_sound('success.wav')
                    self.status_msg.setText(f"✓ {student.get('name', 'Unknown')}")
                    count = int(self.scan_count_label.text().split(':')[1]) + 1
                    self.scan_count_label.setText(f"SCANS: {count}")
                    self.card_3d.flip_in(student, status, timing)
                elif resp.status_code == 409:
                    student = data.get('student', {})
                    play_sound('error.wav')
                    self.status_msg.setText(f"◈ Already: {student.get('name', 'Unknown')}")
                    self.card_3d.flip_in(student, "duplicate")
                else:
                    self.status_msg.setText(f"✕ {data.get('error', 'Unknown')}")
            except requests.exceptions.ConnectionError:
                self.status_msg.setText("✕ API unreachable")
            except Exception as e:
                self.status_msg.setText(f"✕ {str(e)[:40]}")

        threading.Thread(target=do_mark, daemon=True).start()

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setPen(Qt.NoPen)
        for p in self.desk_particles:
            painter.setBrush(QColor(0, 255, 255, int(p.alpha * 255)))
            painter.drawEllipse(QPointF(p.x, p.y), p.size, p.size)
        painter.end()

    def resizeEvent(self, event):
        super().resizeEvent(event)
        if hasattr(self, 'overlay'):
            self.overlay.setGeometry(self.camera_container.rect())
            pw, ph = self.camera_container.width(), self.camera_container.height()
            self.card_3d.move(
                (pw - self.card_3d.width()) // 2,
                (ph - self.card_3d.height()) // 2 - 30
            )
        self.desktop_icons.setGeometry(0, 0, self.width(), self.height())

    def keyPressEvent(self, event):
        if event.key() == Qt.Key_Escape:
            self.close()
        elif event.key() == Qt.Key_Space:
            if self.card_3d.isVisible():
                self.card_3d.hide()

    def mousePressEvent(self, event):
        """Tap anywhere to dismiss card (touchscreen-friendly)."""
        if self.card_3d.isVisible():
            self.card_3d.hide()
        super().mousePressEvent(event)

    def closeEvent(self, event):
        if hasattr(self, 'cam_thread'):
            self.cam_thread.stop()
        event.accept()


def main():
    QApplication.setAttribute(Qt.AA_EnableHighDpiScaling, True)
    QApplication.setAttribute(Qt.AA_UseHighDpiPixmaps, True)

    app = QApplication(sys.argv)

    # Load Orbitron font
    font_db = QFontDatabase()
    font_path = os.path.expanduser("~/.fonts/Orbitron-Variable.ttf")
    if os.path.exists(font_path):
        font_db.addApplicationFont(font_path)
    default_font = QFont("Segoe UI", 10)
    app.setFont(default_font)

    # Boot screen → Desktop
    boot = BootScreen()
    boot.show()

    def start_desktop():
        desktop = Desktop()
        desktop.show()
        boot.close()

    QTimer.singleShot(4500, start_desktop)
    sys.exit(app.exec_())


if __name__ == "__main__":
    main()
