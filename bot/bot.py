"""
EduPulse Telegram Bot — Parent self-service pairing.
Parents send their phone number → bot looks up student → shows confirmation card → saves chat_id.
"""

import json
import logging
import os
import re
import sys
import time
import urllib.parse
from pathlib import Path

import requests

logging.basicConfig(level=logging.INFO, format='%(asctime)s [%(levelname)s] %(message)s')
log = logging.getLogger(__name__)

API_BASE = os.environ.get('API_BASE', 'http://localhost:3000')
POLL_INTERVAL = int(os.environ.get('POLL_INTERVAL', 2))
BOT_DIR = Path(__file__).parent
STATE_FILE = BOT_DIR / 'last_update.json'
PAIRINGS_FILE = BOT_DIR / 'pairings.json'

_sessions = {}  # chat_id -> {'step': 'phone'|'confirm', 'student': dict|None}


def _normalise_phone(phone):
    return re.sub(r'[\s\-\(\)\+]', '', phone or '')


def _save_pairing(phone, chat_id):
    pairings = {}
    if PAIRINGS_FILE.exists():
        try:
            pairings = json.loads(PAIRINGS_FILE.read_text())
        except Exception:
            pass
    normalised = _normalise_phone(phone)
    pairings[normalised] = chat_id
    PAIRINGS_FILE.write_text(json.dumps(pairings, indent=2))
    log.info('Paired phone %s with chat_id %s', normalised, chat_id)


def get_bot_token():
    r = requests.get(f'{API_BASE}/api/settings', timeout=5)
    if r.ok:
        data = r.json().get('data', {})
        token = data.get('telegram_bot_token', '')
        if token:
            return token.strip()
    return None


def find_student_by_phone(phone):
    cleaned = _normalise_phone(phone)
    r = requests.get(f'{API_BASE}/api/students', params={
        'search': cleaned, 'active': 'true'
    }, timeout=5)
    if not r.ok:
        return None
    data = r.json().get('data', [])
    # exact phone match
    for s in data:
        sp = re.sub(r'[\s\-\(\)\+]', '', s.get('phone', ''))
        if sp == cleaned:
            return s
    # if only one result, return it
    if len(data) == 1:
        return data[0]
    return None


def build_student_card(student):
    lines = [
        f"📋 *Student Profile*",
        f"━━━━━━━━━━━━━━━",
        f"👤 Name: {student.get('name', '?')}",
        f"🎓 Class: {student.get('class', '?')} {student.get('section', '')}".strip(),
        f"🔢 Roll No: {student.get('roll_no', '?')}",
        f"📱 Phone: {student.get('phone', '?')}",
    ]
    return '\n'.join(lines)


def send_photo_or_message(bot_token, chat_id, student):
    caption = build_student_card(student)
    caption += '\n\n❓ Is this your child? Reply *yes* to confirm or *no* to cancel.'

    photo_val = student.get('photo', '')
    sent_photo = False

    if photo_val and not photo_val.startswith('data:'):
        local_path = None
        if photo_val.startswith('/uploads/') and API_BASE.startswith('http://localhost'):
            local_path = Path(re.sub(r'^.*?/uploads/', str(Path(API_BASE.replace('http://localhost:3000', '.')) / 'uploads'), photo_val))
            if not local_path.exists():
                local_path = Path(__file__).parent.parent / 'uploads' / Path(photo_val).name
            if not local_path.exists():
                local_path = None
        elif photo_val.startswith('/'):
            local_path = Path(photo_val)
            if not local_path.exists():
                local_path = None

        if local_path and local_path.exists():
            try:
                url = f'https://api.telegram.org/bot{bot_token}/sendPhoto'
                with open(local_path, 'rb') as f:
                    r = requests.post(url, data={'chat_id': chat_id, 'caption': caption},
                                      files={'photo': f}, timeout=15)
                if r.ok:
                    sent_photo = True
            except Exception:
                pass

    if not sent_photo:
        url = f'https://api.telegram.org/bot{bot_token}/sendMessage'
        requests.post(url, json={
            'chat_id': chat_id, 'text': caption, 'parse_mode': 'Markdown'
        }, timeout=10)


def send_message(bot_token, chat_id, text, parse_mode='Markdown'):
    url = f'https://api.telegram.org/bot{bot_token}/sendMessage'
    try:
        requests.post(url, json={
            'chat_id': chat_id, 'text': text, 'parse_mode': parse_mode
        }, timeout=10)
    except Exception as e:
        log.error('send_message failed: %s', e)



def handle_message(bot_token, chat_id, text):
    text = text.strip()
    session = _sessions.get(chat_id, {'step': 'phone', 'student': None})

    if text.lower() == '/start':
        session['step'] = 'phone'
        session['student'] = None
        _sessions[chat_id] = session
        send_message(bot_token, chat_id,
                     "👋 Welcome to *EduPulse Attendance*\n\n"
                     "Send your *phone number* to link with your child's profile "
                     "and receive attendance updates.\n\n"
                     "Example: `9876543210` or `+91 98765 43210`")
        return

    if session['step'] == 'phone':
        student = find_student_by_phone(text)
        if not student:
            send_message(bot_token, chat_id,
                         "❌ No student found with that phone number.\n"
                         "Please check and try again, or contact the school.")
            return

        session['step'] = 'confirm'
        session['student'] = student
        _sessions[chat_id] = session
        send_photo_or_message(bot_token, chat_id, student)
        return

    if session['step'] == 'confirm':
        student = session.get('student')
        if not student:
            session['step'] = 'phone'
            _sessions[chat_id] = session
            send_message(bot_token, chat_id, "Session expired. Send your phone number again.")
            return

        if text.lower() in ('yes', 'y', 'confirm', '✅'):
            phone = student.get('phone', '')
            if phone:
                _save_pairing(phone, chat_id)
            name = student.get('name', 'your child')
            send_message(bot_token, chat_id,
                         f"✅ *Success!*\n\nYou're now connected for {name}'s "
                         f"attendance updates.\n\n"
                         f"You'll receive a message each time {name} scans in at school. 📩")
            session['step'] = 'phone'
            session['student'] = None
            _sessions[chat_id] = session
        elif text.lower() in ('no', 'n', 'cancel', '❌'):
            send_message(bot_token, chat_id,
                         "Cancelled. Send your phone number to try again.")
            session['step'] = 'phone'
            session['student'] = None
            _sessions[chat_id] = session
        else:
            send_message(bot_token, chat_id,
                         "Please reply *yes* to confirm or *no* to cancel.")
        return


def get_updates(bot_token, offset):
    url = f'https://api.telegram.org/bot{bot_token}/getUpdates'
    try:
        r = requests.get(url, params={
            'offset': offset, 'timeout': 30, 'allowed_updates': ['message']
        }, timeout=35)
        if r.ok:
            return r.json().get('result', [])
    except requests.exceptions.Timeout:
        pass
    except Exception as e:
        log.error('getUpdates error: %s', e)
    return []


def main_loop():
    log.info('EduPulse Telegram Bot starting...')
    offset = 0
    state_file = Path(STATE_FILE)
    if state_file.exists():
        try:
            offset = json.loads(state_file.read_text()).get('offset', 0)
        except Exception:
            pass

    while True:
        try:
            token = get_bot_token()
            if not token:
                log.warning('No bot token configured in settings. Retrying in 30s...')
                time.sleep(30)
                continue

            updates = get_updates(token, offset)
            for u in updates:
                if 'message' in u and 'text' in u['message']:
                    chat_id = u['message']['chat']['id']
                    text = u['message']['text']
                    log.info('Message from %s: %s', chat_id, text[:50])
                    handle_message(token, chat_id, text)
                offset = u['update_id'] + 1

            Path(STATE_FILE).write_text(json.dumps({'offset': offset}))
        except KeyboardInterrupt:
            log.info('Shutting down.')
            break
        except Exception as e:
            log.error('Loop error: %s', e)

        time.sleep(POLL_INTERVAL)


if __name__ == '__main__':
    main_loop()
