"""
EduPulse Telegram Notification Module
Sends attendance updates to parent Telegram chat IDs.
Looks up chat_id from bot/pairings.json using student's phone number.
"""

import json
import os
import threading
import requests

TELEGRAM_API = "https://api.telegram.org/bot{token}/sendMessage"
PAIRINGS_FILE = os.path.join(os.path.dirname(__file__), '..', 'bot', 'pairings.json')


def _load_pairings():
    try:
        with open(PAIRINGS_FILE) as f:
            return json.load(f)
    except (FileNotFoundError, json.JSONDecodeError):
        return {}


def _normalise_phone(phone):
    import re
    return re.sub(r'[\s\-\(\)\+]', '', phone or '')


def get_chat_id_by_phone(phone):
    pairings = _load_pairings()
    normalised = _normalise_phone(phone)
    return pairings.get(normalised)


def send_telegram_notification(bot_token, chat_id, student_name, status, date, time_str, class_name=None):
    if not bot_token or not chat_id:
        return False

    status_emoji = {
        'present': '✅',
        'late': '⏰',
        'half-day': '⚠️',
        'absent': '❌',
        'duplicate': '🔄',
        'early': '🌟'
    }
    emoji = status_emoji.get(status, '📋')

    text = (
        f"{emoji} *Attendance Update*\n"
        f"━━━━━━━━━━━━━━━\n"
        f"👤 Student: {student_name}\n"
        f"📅 Date: {date}\n"
        f"⏰ Time: {time_str}\n"
        f"📌 Status: *{status.upper()}*"
    )
    if class_name:
        text += f"\n🏫 Class: {class_name}"

    try:
        url = TELEGRAM_API.format(token=bot_token)
        r = requests.post(url, json={
            'chat_id': str(chat_id).strip(),
            'text': text,
            'parse_mode': 'Markdown'
        }, timeout=10)
        return r.ok
    except Exception:
        return False


def notify_parent(bot_token, student, status, date, time_str):
    phone = student.get('phone', '')
    if not phone:
        return False

    chat_id = get_chat_id_by_phone(phone)
    if not chat_id:
        return False

    class_name = f"{student.get('class', '')} {student.get('section', '')}".strip()
    return send_telegram_notification(
        bot_token, chat_id,
        student.get('name', 'Unknown'),
        status, date, time_str,
        class_name or None
    )


def notify_parent_async(bot_token, student, status, date, time_str):
    threading.Thread(
        target=notify_parent,
        args=(bot_token, student, status, date, time_str),
        daemon=True
    ).start()
