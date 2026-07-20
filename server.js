/**
 * EduPulse Local API Server
 * Express + sql.js backend for kiosk/scanner use
 */

const express = require('express');
const cors = require('cors');
const path = require('path');
const fs = require('fs');
const { v4: uuidv4 } = require('uuid');
const QRCode = require('qrcode');
const dayjs = require('dayjs');
const multer = require('multer');
const XLSX = require('xlsx');
const PDFDocument = require('pdfkit');
const initSqlJs = require('sql.js');

let admin, getMessaging;
const SA_PATH = path.join(__dirname, 'firebase-service-account.json');
if (fs.existsSync(SA_PATH)) {
  admin = require('firebase-admin');
  const sa = JSON.parse(fs.readFileSync(SA_PATH, 'utf-8'));
  admin.initializeApp({ credential: admin.cert(sa) });
  getMessaging = require('firebase-admin/messaging').getMessaging;
  console.log('✅ Firebase Admin initialized');
} else {
  console.warn('⚠️  firebase-service-account.json not found — FCM push disabled');
}

const app = express();
const PORT = process.env.PORT || 3000;
const DB_PATH = path.join(__dirname, 'edupulse.db');
const UPLOADS_DIR = path.join(__dirname, 'uploads');
const QR_DIR = path.join(__dirname, 'qrcodes');

// Ensure dirs exist
[UPLOADS_DIR, QR_DIR].forEach(d => fs.mkdirSync(d, { recursive: true }));

// ── School config (auto-generated on first start) ──
const SCHOOL_FILE = path.join(__dirname, 'school.json');
let schoolId, schoolName, setupComplete;
if (fs.existsSync(SCHOOL_FILE)) {
  const cfg = JSON.parse(fs.readFileSync(SCHOOL_FILE, 'utf-8'));
  schoolId = cfg.schoolId;
  schoolName = cfg.school_name || '';
  setupComplete = cfg.setup_complete === true;
  // Existing installs with schoolId but no setup_complete are treated as complete
  if (!setupComplete && cfg.schoolId && !cfg.hasOwnProperty('setup_complete')) {
    setupComplete = true;
  }
} else {
  schoolId = uuidv4();
  schoolName = '';
  setupComplete = false;
  fs.writeFileSync(SCHOOL_FILE, JSON.stringify({ schoolId, school_name: '', setup_complete: false }, null, 2));
  console.log('🏫 Generated school ID:', schoolId);
}

function saveSchoolConfig() {
  fs.writeFileSync(SCHOOL_FILE, JSON.stringify({ schoolId, school_name: schoolName, setup_complete: setupComplete }, null, 2));
}

app.get('/api/setup-status', (req, res) => {
  res.json({ success: true, setup_needed: !setupComplete, schoolId, school_name: schoolName });
});

app.post('/api/setup', (req, res) => {
  const { school_name } = req.body;
  if (!school_name || !school_name.trim()) {
    return res.json({ success: false, error: 'School name is required' });
  }
  schoolName = school_name.trim();
  setupComplete = true;
  saveSchoolConfig();
  console.log('🏫 School configured:', schoolName);
  // Sync school name to Firebase
  syncToFirebase();
  res.json({ success: true, schoolId, school_name: schoolName });
});

app.put('/api/school-info', (req, res) => {
  const id = (req.body.schoolId || '').trim();
  if (!id) return res.json({ success: false, error: 'School ID cannot be empty' });
  schoolId = id;
  schoolName = req.body.school_name || schoolName;
  saveSchoolConfig();
  res.json({ success: true, schoolId, school_name: schoolName });
});

// Middleware
app.use(cors());
app.use(express.json());
app.use('/uploads', express.static(UPLOADS_DIR));
app.use('/qrcodes', express.static(QR_DIR));
// Serve frontend static files
app.use(express.static(__dirname));

// File upload config
const storage = multer.diskStorage({
  destination: (req, file, cb) => cb(null, UPLOADS_DIR),
  filename: (req, file, cb) => cb(null, `${uuidv4()}${path.extname(file.originalname)}`)
});
const upload = multer({ storage, limits: { fileSize: 5 * 1024 * 1024 } });

// ── SQLite helper with prepared statements ──
let db;
let SQL;

function query(sql, params) {
  // For SELECT queries with parameters, use prepared statements
  if (params && params.length > 0 && sql.trim().toUpperCase().startsWith('SELECT')) {
    const stmt = db.prepare(sql);
    stmt.bind(params);
    const rows = [];
    while (stmt.step()) rows.push(stmt.getAsObject());
    stmt.free();
    return rows;
  }
  // For parameterized INSERT/UPDATE/DELETE
  if (params && params.length > 0) {
    runSql(sql, params);
    return [];
  }
  // Simple exec (no params) — use db.exec for raw results
  const raw = db.exec(sql);
  if (!raw || raw.length === 0) return [];
  const cols = raw[0].columns;
  return raw[0].values.map(row => {
    const obj = {};
    cols.forEach((col, i) => obj[col] = row[i]);
    return obj;
  });
}

function runSql(sql, params) {
  db.run(sql, params || []);
}

async function initDB() {
  SQL = await initSqlJs();

  if (fs.existsSync(DB_PATH)) {
    const data = fs.readFileSync(DB_PATH);
    db = new SQL.Database(data);
  } else {
    db = new SQL.Database();
  }

  runSql(`
    CREATE TABLE IF NOT EXISTS students (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      roll_no TEXT UNIQUE NOT NULL,
      class TEXT NOT NULL,
      section TEXT,
      email TEXT,
      phone TEXT,
      photo TEXT,
      qr_code TEXT,
      created_at TEXT,
      active INTEGER DEFAULT 1
    )
  `);

  runSql(`
    CREATE TABLE IF NOT EXISTS attendance (
      id TEXT PRIMARY KEY,
      student_id TEXT NOT NULL,
      date TEXT NOT NULL,
      time TEXT NOT NULL,
      status TEXT DEFAULT 'present',
      scanned_by TEXT DEFAULT 'kiosk',
      subject TEXT DEFAULT '',
      period TEXT DEFAULT '',
      FOREIGN KEY (student_id) REFERENCES students(id)
    )
  `);

  // Migration: add subject/period columns if missing
  try { runSql(`ALTER TABLE attendance ADD COLUMN subject TEXT DEFAULT ''`); } catch (e) {}
  try { runSql(`ALTER TABLE attendance ADD COLUMN period TEXT DEFAULT ''`); } catch (e) {}
  try { runSql(`ALTER TABLE students ADD COLUMN subjects TEXT DEFAULT ''`); } catch (e) {}

  runSql(`
    CREATE TABLE IF NOT EXISTS teachers (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      email TEXT,
      phone TEXT,
      classes TEXT,
      subjects TEXT,
      photo TEXT,
      role TEXT DEFAULT 'teacher',
      active INTEGER DEFAULT 1,
      created_at TEXT
    )
  `);

  runSql(`
    CREATE TABLE IF NOT EXISTS settings (
      key TEXT PRIMARY KEY,
      value TEXT
    )
  `);

  // Default settings
  const defaults = [
    ['duplicate_window_minutes', '60'],
    ['school_name', 'EduPulse Academy'],
    ['late_threshold_minutes', '30'],
    ['school_start_time', '08:30'],
    ['telegram_bot_token', '']
  ];
  defaults.forEach(([k, v]) => {
    runSql(`INSERT OR IGNORE INTO settings (key, value) VALUES (?, ?)`, [k, v]);
  });

  // Migration: add telegram_chat_id column if missing
  try { runSql(`ALTER TABLE students ADD COLUMN telegram_chat_id TEXT DEFAULT ''`); } catch (e) {}
  try { runSql(`ALTER TABLE students ADD COLUMN guardian TEXT DEFAULT ''`); } catch (e) {}
  try { runSql(`ALTER TABLE students ADD COLUMN guardian_phone TEXT DEFAULT ''`); } catch (e) {}
  try { runSql(`ALTER TABLE students ADD COLUMN dob TEXT DEFAULT ''`); } catch (e) {}

  saveDB();
  console.log('✅ Database initialized');
}

function saveDB() {
  const data = db.export();
  fs.writeFileSync(DB_PATH, Buffer.from(data));
}

function getSetting(key) {
  const rows = query(`SELECT value FROM settings WHERE key = ?`, [key]);
  return rows[0]?.value;
}

function rowsToObjects(results) {
  return results || [];
}

// ─── STUDENT ROUTES ───────────────────────────────────────────────────────────

app.get('/api/students', (req, res) => {
  try {
    const { search, class: cls, active } = req.query;
    let sql = `SELECT * FROM students WHERE 1=1`;
    const params = [];

    if (search) {
      sql += ` AND (name LIKE ? OR roll_no LIKE ? OR phone LIKE ?)`;
      params.push(`%${search}%`, `%${search}%`, `%${search}%`);
    }
    if (cls) { sql += ` AND class = ?`; params.push(cls); }
    if (active !== undefined) { sql += ` AND active = ?`; params.push(active === 'true' ? 1 : 0); }

    sql += ` ORDER BY name ASC`;
    const result = query(sql, params);
    res.json({ success: true, data: rowsToObjects(result) });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

app.get('/api/students/:id', (req, res) => {
  try {
    const result = query(`SELECT * FROM students WHERE id = ?`, [req.params.id]);
    const rows = rowsToObjects(result);
    if (!rows.length) return res.status(404).json({ success: false, error: 'Student not found' });
    res.json({ success: true, data: rows[0] });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

app.post('/api/students', async (req, res) => {
  try {
    const { name, roll_no, class: cls, section, email, phone, telegram_chat_id, dob, guardian, guardian_phone } = req.body;
    if (!name || !roll_no || !cls) {
      return res.status(400).json({ success: false, error: 'name, roll_no, class required' });
    }

    const id = uuidv4();
    const photo = req.body.photo && req.body.photo.startsWith('data:image') ? req.body.photo : null;

    // Generate QR
    const qrData = JSON.stringify({ student_id: id, roll_no, name });
    const qrPath = path.join(QR_DIR, `${id}.png`);
    await QRCode.toFile(qrPath, qrData, {
      width: 300, margin: 2,
      color: { dark: '#0a0a0b', light: '#00ffff' }
    });
    const qrCode = `/qrcodes/${id}.png`;

    runSql(
      `INSERT INTO students (id, name, roll_no, class, section, email, phone, telegram_chat_id, dob, guardian, guardian_phone, photo, qr_code, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)`,
      [id, name, roll_no, cls, section || '', email || '', phone || '', telegram_chat_id || '', dob || '', guardian || '', guardian_phone || '', photo, qrCode]
    );
    saveDB();

    const result = query(`SELECT * FROM students WHERE id = ?`, [id]);
    res.json({ success: true, data: rowsToObjects(result)[0] });
  } catch (e) {
    if (e.message?.includes('UNIQUE')) {
      return res.status(409).json({ success: false, error: 'Roll number already exists' });
    }
    res.status(500).json({ success: false, error: e.message });
  }
});

app.put('/api/students/:id', async (req, res) => {
  try {
    const { name, roll_no, class: cls, section, email, phone, telegram_chat_id, dob, guardian, guardian_phone, active } = req.body;
    const existing = rowsToObjects(query(`SELECT * FROM students WHERE id = ?`, [req.params.id]));
    if (!existing.length) return res.status(404).json({ success: false, error: 'Not found' });

    const photo = req.body.photo && req.body.photo.startsWith('data:image') ? req.body.photo : existing[0].photo;

    runSql(
      `UPDATE students SET name=?, roll_no=?, class=?, section=?, email=?, phone=?, telegram_chat_id=?, dob=?, guardian=?, guardian_phone=?, photo=?, active=? WHERE id=?`,
      [name || existing[0].name, roll_no || existing[0].roll_no, cls || existing[0].class,
       section ?? existing[0].section, email ?? existing[0].email, phone ?? existing[0].phone,
       telegram_chat_id ?? existing[0].telegram_chat_id,
       dob ?? existing[0].dob, guardian ?? existing[0].guardian, guardian_phone ?? existing[0].guardian_phone,
       photo, active !== undefined ? (active === 'true' ? 1 : 0) : existing[0].active, req.params.id]
    );
    saveDB();

    const result = query(`SELECT * FROM students WHERE id = ?`, [req.params.id]);
    res.json({ success: true, data: rowsToObjects(result)[0] });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

app.delete('/api/students/:id', (req, res) => {
  try {
    runSql(`UPDATE students SET active = 0 WHERE id = ?`, [req.params.id]);
    saveDB();
    res.json({ success: true, message: 'Student deactivated' });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

// Bulk import
app.post('/api/students/bulk', async (req, res) => {
  try {
    const { students } = req.body;
    if (!Array.isArray(students)) return res.status(400).json({ success: false, error: 'students array required' });

    const results = [];
    for (const s of students) {
      try {
        const id = uuidv4();
        const qrData = JSON.stringify({ student_id: id, roll_no: s.roll_no, name: s.name });
        const qrPath = path.join(QR_DIR, `${id}.png`);
        await QRCode.toFile(qrPath, qrData, { width: 300, margin: 2 });

        runSql(
          `INSERT OR IGNORE INTO students (id, name, roll_no, class, section, email, phone, qr_code) VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
          [id, s.name, s.roll_no, s.class, s.section || '', s.email || '', s.phone || '', `/qrcodes/${id}.png`]
        );
        results.push({ success: true, roll_no: s.roll_no });
      } catch (err) {
        results.push({ success: false, roll_no: s.roll_no, error: err.message });
      }
    }
    saveDB();
    res.json({ success: true, data: results });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

// ─── QR CODE ─────────────────────────────────────────────────────────────────

app.get('/api/students/:id/qr', async (req, res) => {
  try {
    const student = rowsToObjects(query(`SELECT * FROM students WHERE id = ?`, [req.params.id]));
    if (!student.length) return res.status(404).json({ success: false, error: 'Not found' });

    const s = student[0];
    const qrData = JSON.stringify({ student_id: s.id, roll_no: s.roll_no, name: s.name });
    const qrPath = path.join(QR_DIR, `${s.id}.png`);
    await QRCode.toFile(qrPath, qrData, {
      width: 400, margin: 3,
      color: { dark: '#00ffff', light: '#0a0a0b' }
    });

    runSql(`UPDATE students SET qr_code = ? WHERE id = ?`, [`/qrcodes/${s.id}.png`, s.id]);
    saveDB();
    res.json({ success: true, qr_url: `/qrcodes/${s.id}.png` });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

// ─── TELEGRAM NOTIFICATION ─────────────────────────────────────────────────────

const PAIRINGS_PATH = path.join(__dirname, 'bot', 'pairings.json');

function loadPairings() {
  try {
    return JSON.parse(fs.readFileSync(PAIRINGS_PATH, 'utf-8'));
  } catch {
    return {};
  }
}

function normPhone(phone) {
  return (phone || '').replace(/[\s\-\(\)\+]/g, '');
}

async function sendTelegramNotification(student, status, date, timeStr) {
  try {
    const botToken = getSetting('telegram_bot_token');
    if (!botToken) return;
    const phone = student.phone;
    if (!phone) return;
    const pairings = loadPairings();
    const chatId = pairings[normPhone(phone)];
    if (!chatId) return;

    const emojis = { present: '✅', late: '⏰', 'half-day': '⚠️', absent: '❌', duplicate: '🔄', early: '🌟' };
    const emoji = emojis[status] || '📋';
    const cls = [student.class, student.section].filter(Boolean).join(' ');

    const text = `${emoji} *Attendance Update*\n━━━━━━━━━━━━━━━\n👤 Student: ${student.name}\n📅 Date: ${date}\n⏰ Time: ${timeStr}\n📌 Status: *${status.toUpperCase()}*${cls ? '\n🏫 Class: ' + cls : ''}`;

    await fetch(`https://api.telegram.org/bot${botToken}/sendMessage`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ chat_id: String(chatId).trim(), text, parse_mode: 'Markdown' })
    });
  } catch {}
}

// ─── STUDENT LOGIN & PORTAL ────────────────────────────────────────────────────

app.post('/api/student/login', (req, res) => {
  try {
    const { roll_no, dob } = req.body;
    if (!roll_no || !dob) {
      return res.status(400).json({ success: false, error: 'roll_no and dob required' });
    }
    const result = query(`SELECT * FROM students WHERE roll_no = ? AND dob = ? AND active = 1`, [roll_no, dob]);
    const rows = rowsToObjects(result);
    if (!rows.length) {
      return res.status(401).json({ success: false, error: 'Invalid roll number or date of birth' });
    }
    const s = rows[0];
    res.json({
      success: true, data: {
        id: s.id, name: s.name, roll_no: s.roll_no, class: s.class,
        section: s.section, photo: s.photo, guardian: s.guardian,
        guardian_phone: s.guardian_phone, dob: s.dob
      }
    });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

app.get('/api/student/:id/attendance', (req, res) => {
  try {
    const { from, to, limit } = req.query;
    let sql = `SELECT a.* FROM attendance a WHERE a.student_id = ?`;
    const params = [req.params.id];
    if (from) { sql += ` AND a.date >= ?`; params.push(from); }
    if (to) { sql += ` AND a.date <= ?`; params.push(to); }
    sql += ` ORDER BY a.date DESC, a.time DESC`;
    if (limit) sql += ` LIMIT ?`;
    if (limit) params.push(parseInt(limit));
    const result = query(sql, params);
    res.json({ success: true, data: rowsToObjects(result) });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

app.get('/api/student/:id/stats', (req, res) => {
  try {
    const sid = req.params.id;
    const total = query(`SELECT COUNT(*) as cnt FROM attendance WHERE student_id = ?`, [sid]);
    const present = query(`SELECT COUNT(*) as cnt FROM attendance WHERE student_id = ? AND status = 'present'`, [sid]);
    const late = query(`SELECT COUNT(*) as cnt FROM attendance WHERE student_id = ? AND status = 'late'`, [sid]);
    const absent = query(`SELECT COUNT(*) as cnt FROM attendance WHERE student_id = ? AND status = 'absent'`, [sid]);
    const halfDay = query(`SELECT COUNT(*) as cnt FROM attendance WHERE student_id = ? AND status = 'half-day'`, [sid]);
    const recent = query(`SELECT a.date, a.time, a.status, a.subject FROM attendance a WHERE a.student_id = ? ORDER BY a.date DESC, a.time DESC LIMIT 5`, [sid]);

    const t = total[0]?.cnt || 0;
    res.json({
      success: true, data: {
        total: t,
        present: present[0]?.cnt || 0,
        late: late[0]?.cnt || 0,
        absent: absent[0]?.cnt || 0,
        half_day: halfDay[0]?.cnt || 0,
        percentage: t > 0 ? Math.round(((present[0]?.cnt || 0) / t) * 100) : 0,
        recent: rowsToObjects(recent)
      }
    });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

// ─── ATTENDANCE ROUTES ────────────────────────────────────────────────────────

app.post('/api/attendance/scan', (req, res) => {
  try {
    const { student_id, qr_data, subject, period } = req.body;
    let sid = student_id;

    // Parse QR if raw data given
    if (!sid && qr_data) {
      try {
        const parsed = JSON.parse(qr_data);
        sid = parsed.student_id;
      } catch {
        return res.status(400).json({ success: false, error: 'Invalid QR data' });
      }
    }

    if (!sid) return res.status(400).json({ success: false, error: 'student_id required' });

    const student = rowsToObjects(query(`SELECT * FROM students WHERE id = ? AND active = 1`, [sid]));
    if (!student.length) return res.status(404).json({ success: false, error: 'Student not found' });

    // Duplicate check
    const windowMins = parseInt(getSetting('duplicate_window_minutes') || '60');
    const windowStart = dayjs().subtract(windowMins, 'minute').format('YYYY-MM-DD HH:mm:ss');
    const today = dayjs().format('YYYY-MM-DD');

    const existing = rowsToObjects(query(
      `SELECT * FROM attendance WHERE student_id = ? AND date = ? AND time > ?`,
      [sid, today, windowStart.split(' ')[1]]
    ));

    if (existing.length) {
      return res.status(409).json({
        success: false,
        error: 'Duplicate scan',
        student: student[0],
        last_scan: existing[0]
      });
    }

    // Determine status
    const startTime = getSetting('school_start_time') || '08:30';
    const endTime = getSetting('entry_end_time') || '16:00';
    const halfDayCutoff = getSetting('half_day_cutoff') || '12:00';
    const lateThreshold = parseInt(getSetting('late_threshold_minutes') || '30');
    const now = dayjs();
    const [sh, sm] = startTime.split(':').map(Number);
    const [eh, em] = endTime.split(':').map(Number);
    const [hh, hm] = halfDayCutoff.split(':').map(Number);
    const schoolStart = dayjs().hour(sh).minute(sm).second(0);
    const schoolEnd = dayjs().hour(eh).minute(em).second(0);
    const halfDay = dayjs().hour(hh).minute(hm).second(0);

    let status;
    let timing_msg = '';

    if (now.isBefore(schoolStart)) {
      status = 'early';
      timing_msg = `Attendance opens at ${startTime}`;
    } else if (now.isAfter(schoolEnd)) {
      status = 'late';
      timing_msg = `Entry period ended at ${endTime}`;
    } else if (now.isAfter(halfDay)) {
      status = 'half-day';
      timing_msg = `Marked half-day (after ${halfDayCutoff})`;
    } else if (now.diff(schoolStart, 'minute') > lateThreshold) {
      status = 'late';
      timing_msg = `Late by ${now.diff(schoolStart, 'minute')} min`;
    } else {
      status = 'present';
      timing_msg = 'On time';
    }

    const id = uuidv4();
    const time = now.format('HH:mm:ss');
    runSql(
      `INSERT INTO attendance (id, student_id, date, time, status, subject, period) VALUES (?, ?, ?, ?, ?, ?, ?)`,
      [id, sid, today, time, status, subject || '', period || '']
    );
    saveDB();

    sendTelegramNotification(student[0], status, today, time);

    res.json({
      success: true,
      message: `Attendance marked: ${status}`,
      student: student[0],
      attendance: { id, student_id: sid, date: today, time, status },
      timing: {
        start_time: startTime,
        end_time: endTime,
        half_day_cutoff: halfDayCutoff,
        status,
        message: timing_msg
      }
    });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

app.get('/api/attendance', (req, res) => {
  try {
    const { date, class: cls, student_id, from, to, subject, period } = req.query;
    let sql = `
      SELECT a.*, s.name, s.roll_no, s.class, s.section, s.photo
      FROM attendance a JOIN students s ON a.student_id = s.id
      WHERE 1=1
    `;
    const params = [];

    if (date) { sql += ` AND a.date = ?`; params.push(date); }
    if (from) { sql += ` AND a.date >= ?`; params.push(from); }
    if (to) { sql += ` AND a.date <= ?`; params.push(to); }
    if (cls) { sql += ` AND s.class = ?`; params.push(cls); }
    if (student_id) { sql += ` AND a.student_id = ?`; params.push(student_id); }
    if (subject) { sql += ` AND a.subject = ?`; params.push(subject); }
    if (period) { sql += ` AND a.period = ?`; params.push(period); }

    sql += ` ORDER BY a.date DESC, a.time DESC`;

    const result = query(sql, params);
    res.json({ success: true, data: rowsToObjects(result) });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

app.get('/api/attendance/summary', (req, res) => {
  try {
    const date = req.query.date || dayjs().format('YYYY-MM-DD');

    function getVal(arr, key) {
      if (!arr || !arr.length) return 0;
      return arr[0][key] !== undefined ? arr[0][key] : 0;
    }

    const totalRes = query(`SELECT COUNT(*) as total FROM students WHERE active = 1`);
    const total = getVal(totalRes, 'total');

    const presentRes = query(
      `SELECT COUNT(DISTINCT student_id) as cnt FROM attendance WHERE date = ?`,
      [date]
    );
    const present = getVal(presentRes, 'cnt');
    const absent = total - present;

    const byClassRes = query(`
      SELECT s.class, COUNT(DISTINCT a.student_id) as present,
             (SELECT COUNT(*) FROM students s2 WHERE s2.class = s.class AND s2.active = 1) as total
      FROM students s
      LEFT JOIN attendance a ON s.id = a.student_id AND a.date = ?
      WHERE s.active = 1
      GROUP BY s.class
      ORDER BY s.class
    `, [date]);

    const lateRes = query(
      `SELECT COUNT(*) as cnt FROM attendance WHERE date = ? AND status = 'late'`, [date]
    );
    const late = getVal(lateRes, 'cnt');

    res.json({
      success: true,
      data: {
        date, total, present, absent, late,
        percentage: total > 0 ? Math.round((present / total) * 100) : 0,
        by_class: byClassRes
      }
    });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

app.get('/api/attendance/recent', (req, res) => {
  try {
    const limit = parseInt(req.query.limit || '20');
    const result = query(`
      SELECT a.*, s.name, s.roll_no, s.class, s.photo
      FROM attendance a JOIN students s ON a.student_id = s.id
      ORDER BY a.date DESC, a.time DESC LIMIT ?
    `, [limit]);
    res.json({ success: true, data: rowsToObjects(result) });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

// ─── REPORTS ─────────────────────────────────────────────────────────────────

app.get('/api/reports/excel', (req, res) => {
  try {
    const { from, to, class: cls } = req.query;
    const date = from || dayjs().format('YYYY-MM-DD');
    const endDate = to || date;

    let sql = `
      SELECT s.name, s.roll_no, s.class, s.section,
             a.date, a.time, a.status
      FROM students s
      LEFT JOIN attendance a ON s.id = a.student_id AND a.date BETWEEN ? AND ?
      WHERE s.active = 1
    `;
    const params = [date, endDate];
    if (cls) { sql += ` AND s.class = ?`; params.push(cls); }
    sql += ` ORDER BY s.class, s.name, a.date`;

    const rows = rowsToObjects(query(sql, params));

    const wsData = [['Name', 'Roll No', 'Class', 'Section', 'Date', 'Time', 'Status']];
    rows.forEach(r => wsData.push([r.name, r.roll_no, r.class, r.section, r.date || '-', r.time || '-', r.status || 'absent']));

    const wb = XLSX.utils.book_new();
    const ws = XLSX.utils.aoa_to_sheet(wsData);
    ws['!cols'] = [{ wch: 20 }, { wch: 12 }, { wch: 10 }, { wch: 10 }, { wch: 12 }, { wch: 10 }, { wch: 10 }];
    XLSX.utils.book_append_sheet(wb, ws, 'Attendance');

    const buf = XLSX.write(wb, { type: 'buffer', bookType: 'xlsx' });
    res.setHeader('Content-Type', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
    res.setHeader('Content-Disposition', `attachment; filename="attendance_${date}.xlsx"`);
    res.send(buf);
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

app.get('/api/reports/pdf', (req, res) => {
  try {
    const { from, to, class: cls } = req.query;
    const date = from || dayjs().format('YYYY-MM-DD');
    const endDate = to || date;

    const summaryRes = query(`
      SELECT s.class,
             COUNT(DISTINCT s.id) as total,
             COUNT(DISTINCT a.student_id) as present
      FROM students s
      LEFT JOIN attendance a ON s.id = a.student_id AND a.date BETWEEN ? AND ?
      WHERE s.active = 1
      GROUP BY s.class
    `, [date, endDate]);
    const summary = rowsToObjects(summaryRes);

    const doc = new PDFDocument({ margin: 50, size: 'A4' });
    res.setHeader('Content-Type', 'application/pdf');
    res.setHeader('Content-Disposition', `attachment; filename="report_${date}.pdf"`);
    doc.pipe(res);

    // Header
    doc.fontSize(22).fillColor('#00ffff').text('EduPulse Attendance Report', { align: 'center' });
    doc.fontSize(12).fillColor('#888').text(`Period: ${date} to ${endDate}`, { align: 'center' });
    doc.moveDown(2);

    // Summary table
    doc.fontSize(14).fillColor('#00ffff').text('Class-wise Summary');
    doc.moveDown(0.5);

    summary.forEach(row => {
      const pct = row.total > 0 ? Math.round((row.present / row.total) * 100) : 0;
      doc.fontSize(11).fillColor('#ccc')
        .text(`Class ${row.class}: ${row.present}/${row.total} present (${pct}%)`);
    });

    doc.end();
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

// ─── SETTINGS ─────────────────────────────────────────────────────────────────

app.get('/api/settings', (req, res) => {
  try {
    const result = query(`SELECT key, value FROM settings`);
    const settings = {};
    rowsToObjects(result).forEach(r => settings[r.key] = r.value);
    res.json({ success: true, data: settings });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

app.put('/api/settings', (req, res) => {
  try {
    Object.entries(req.body).forEach(([key, value]) => {
      runSql(`INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)`, [key, value]);
    });
    // Keep school.json in sync
    if (req.body.school_name) {
      schoolName = req.body.school_name;
      saveSchoolConfig();
    }
    saveDB();
    res.json({ success: true, message: 'Settings updated' });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

// ─── SUBJECTS ──────────────────────────────────────────────────────────────────

app.get('/api/subjects', (req, res) => {
  try {
    const result = query(`SELECT value FROM settings WHERE key = 'subjects'`);
    const rows = rowsToObjects(result);
    const val = rows.length ? rows[0].value : null;
    res.json({ success: true, data: val ? JSON.parse(val) : {} });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

app.put('/api/subjects', (req, res) => {
  try {
    runSql(`INSERT OR REPLACE INTO settings (key, value) VALUES ('subjects', ?)`, [JSON.stringify(req.body)]);
    saveDB();
    res.json({ success: true, message: 'Subjects saved' });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

// ─── FCM PUSH ────────────────────────────────────────────────────────────────

app.post('/api/fcm/token', (req, res) => {
  try {
    const { token, teacher_id, device } = req.body;
    if (!token) return res.status(400).json({ success: false, error: 'token required' });
    runSql(
      `INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)`,
      [`fcm_token_${teacher_id || 'default'}`, token]
    );
    if (device) {
      runSql(`INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)`,
        [`fcm_device_${teacher_id || 'default'}`, device]);
    }
    saveDB();
    res.json({ success: true, message: 'FCM token registered' });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

app.post('/api/fcm/send', async (req, res) => {
  try {
    if (!admin) return res.status(503).json({ success: false, error: 'FCM not configured — missing firebase-service-account.json' });

    const { token, teacher_id, title, body, data } = req.body;

    let targetToken = token;
    if (!targetToken && teacher_id) {
      const rows = query(`SELECT value FROM settings WHERE key = ?`, [`fcm_token_${teacher_id}`]);
      targetToken = rows[0]?.value;
    }
    if (!targetToken) return res.status(400).json({ success: false, error: 'No FCM token provided or registered' });

    const message = {
      token: targetToken,
      notification: { title: title || 'EduPulse', body: body || '' },
      data: data || {},
    };

    const response = await getMessaging().send(message);
    res.json({ success: true, message: 'Notification sent', fcm_response: response });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

app.get('/api/fcm/tokens', (req, res) => {
  try {
    const rows = query(`SELECT key, value FROM settings WHERE key LIKE 'fcm_token_%'`);
    const tokens = {};
    rows.forEach(r => tokens[r.key.replace('fcm_token_', '')] = r.value);
    res.json({ success: true, data: tokens });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

// ─── PENDING REGISTRATIONS (Firebase RTDB) ────────────────────────────────────

const FB_DB_URL = 'https://edupulse-attendance-qr-default-rtdb.asia-southeast1.firebasedatabase.app';
const FB_API_KEY = 'AIzaSyDPYjylSBhng6CRL77P0MXTUcuq7jBFnnA';

// ─── SYNC LOCAL SQLite → FIREBASE RTDB (school-partitioned) ─────────────────
const SYNC_INTERVAL = 30000; // 30 seconds

async function syncToFirebase() {
  if (!schoolId) return;
  try {
    const base = `schools/${schoolId}`;

    // Sync students
    const students = query(`SELECT id, name, roll_no, class, section, subjects, photo, active, created_at FROM students WHERE active = 1`);
    const studentMap = {};
    for (const s of students) {
      studentMap[s.id] = { name: s.name, roll_no: s.roll_no, class: s.class, section: s.section || '', subjects: s.subjects || '', photo: s.photo || null, active: true, created_at: s.created_at || new Date().toISOString() };
    }
    await fetch(`${FB_DB_URL}/${base}/students.json`, {
      method: 'PUT',
      body: JSON.stringify(studentMap),
      headers: { 'Content-Type': 'application/json' }
    });

    // Sync attendance
    const attRows = query(`SELECT id, student_id, date, time, status, subject, period, scanned_by FROM attendance ORDER BY date DESC, time DESC LIMIT 5000`);
    const attMap = {};
    for (const a of attRows) {
      attMap[a.id] = { student_id: a.student_id, date: a.date, time: a.time, status: a.status, subject: a.subject || '', period: a.period || '', scanned_by: a.scanned_by || 'kiosk' };
    }
    if (Object.keys(attMap).length) {
      await fetch(`${FB_DB_URL}/${base}/attendance.json`, {
        method: 'PUT',
        body: JSON.stringify(attMap),
        headers: { 'Content-Type': 'application/json' }
      });
    }

    // Sync settings
    const settings = query(`SELECT key, value FROM settings`);
    const settingsMap = {};
    for (const s of settings) settingsMap[s.key] = s.value;
    if (Object.keys(settingsMap).length) {
      await fetch(`${FB_DB_URL}/${base}/settings.json`, {
        method: 'PUT',
        body: JSON.stringify(settingsMap),
        headers: { 'Content-Type': 'application/json' }
      });
    }

    // Sync subjects
    const subjectsRow = query(`SELECT value FROM settings WHERE key = 'subjects'`);
    if (subjectsRow.length && subjectsRow[0].value) {
      try {
        const subData = JSON.parse(subjectsRow[0].value);
        await fetch(`${FB_DB_URL}/${base}/subjects.json`, {
          method: 'PUT',
          body: JSON.stringify(subData),
          headers: { 'Content-Type': 'application/json' }
        });
      } catch (e) {
        console.error('❌ Subjects sync parse error:', e.message);
      }
    }

    // Sync teachers
    const teachers = query(`SELECT id, name, email, phone, classes, subjects, photo, role, active, created_at FROM teachers WHERE active = 1`);
    const teacherMap = {};
    for (const t of teachers) {
      let cls = [];
      try { cls = t.classes ? JSON.parse(t.classes) : []; } catch(e) { cls = (t.classes || '').split(',').filter(Boolean); }
      let subs = [];
      try { subs = t.subjects ? JSON.parse(t.subjects) : []; } catch(e) { subs = (t.subjects || '').split(',').filter(Boolean); }
      teacherMap[t.id] = {
        name: t.name, email: t.email || '', phone: t.phone || '',
        classes: cls, subjects: subs,
        photo: t.photo || '', role: t.role || 'teacher',
        active: true, uid: t.id,
        created_at: t.created_at || new Date().toISOString()
      };
    }
    if (Object.keys(teacherMap).length) {
      await fetch(`${FB_DB_URL}/${base}/teachers.json`, {
        method: 'PUT',
        body: JSON.stringify(teacherMap),
        headers: { 'Content-Type': 'application/json' }
      });
    }

    // Sync school config
    await fetch(`${FB_DB_URL}/schoolId.json`, {
      method: 'PUT',
      body: JSON.stringify(schoolId),
      headers: { 'Content-Type': 'application/json' }
    });
    await fetch(`${FB_DB_URL}/${base}/settings/school_name.json`, {
      method: 'PUT',
      body: JSON.stringify(schoolName || ''),
      headers: { 'Content-Type': 'application/json' }
    });
  } catch (e) {
    console.error('❌ Sync error:', e.message);
  }
}

function scheduleSync() {
  setInterval(syncToFirebase, SYNC_INTERVAL);
}

// ─── PENDING REGISTRATIONS (school-partitioned Firebase RTDB) ───────────────

app.get('/api/pending', async (req, res) => {
  try {
    const base = `schools/${schoolId}`;
    const r = await fetch(`${FB_DB_URL}/${base}/pending_registrations.json`);
    if (!r.ok) return res.status(502).json({ success: false, error: 'Failed to fetch from Firebase' });
    const data = await r.json();
    const list = [];
    for (const id in data) {
      if (data[id] && data[id].status !== 'approved') {
        list.push({ id, ...data[id] });
      }
    }
    list.sort((a, b) => (a.created_at || '').localeCompare(b.created_at || ''));
    res.json({ success: true, data: list });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

app.post('/api/pending/:id/approve', async (req, res) => {
  try {
    const id = req.params.id;
    const base = `schools/${schoolId}`;
    const r = await fetch(`${FB_DB_URL}/${base}/pending_registrations/${id}.json`);
    if (!r.ok) return res.status(404).json({ success: false, error: 'Not found in Firebase' });
    const entry = await r.json();
    if (!entry) return res.status(404).json({ success: false, error: 'Registration not found' });

    const studentId = uuidv4();
    const subjects = (entry.subjects || []).join(',');
    runSql(
      `INSERT INTO students (id, name, roll_no, class, section, subjects, photo, email, phone, guardian, guardian_phone, dob, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)`,
      [studentId, entry.name, entry.roll_no, entry.class, entry.section || '', subjects, entry.photo || '', entry.email || '', entry.phone || '', entry.guardian || '', entry.guardian_phone || '', entry.dob || '']
    );
    saveDB();

    // Mark as approved on Firebase
    await fetch(`${FB_DB_URL}/${base}/pending_registrations/${id}.json`, {
      method: 'DELETE'
    });

    // Sync new student to Firebase
    syncToFirebase();

    res.json({ success: true, message: 'Student approved', student_id: studentId });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

app.post('/api/pending/:id/reject', async (req, res) => {
  try {
    const base = `schools/${schoolId}`;
    await fetch(`${FB_DB_URL}/${base}/pending_registrations/${req.params.id}.json`, {
      method: 'DELETE'
    });
    res.json({ success: true, message: 'Registration rejected' });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

// ─── PENDING TEACHERS ─────────────────────────────────────────────────────────

app.get('/api/pending-teachers', async (req, res) => {
  try {
    const base = `schools/${schoolId}`;
    const r = await fetch(`${FB_DB_URL}/${base}/pending_teachers.json`);
    if (!r.ok) return res.status(502).json({ success: false, error: 'Failed to fetch from Firebase' });
    const data = await r.json();
    const list = [];
    for (const id in data) {
      if (data[id] && data[id].status !== 'approved') {
        list.push({ id, ...data[id] });
      }
    }
    list.sort((a, b) => (a.created_at || '').localeCompare(b.created_at || ''));
    res.json({ success: true, data: list });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

app.post('/api/pending-teachers/:id/approve', async (req, res) => {
  try {
    const id = req.params.id;
    const base = `schools/${schoolId}`;
    const r = await fetch(`${FB_DB_URL}/${base}/pending_teachers/${id}.json`);
    if (!r.ok) return res.status(404).json({ success: false, error: 'Not found in Firebase' });
    const entry = await r.json();
    if (!entry) return res.status(404).json({ success: false, error: 'Teacher registration not found' });

    // Create Firebase Auth account via REST API
    const signUpRes = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${FB_API_KEY}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        email: entry.email,
        password: entry.password || 'edupulse123',
        returnSecureToken: true
      })
    });
    const signUpData = await signUpRes.json();
    if (!signUpRes.ok) {
      // If already exists, try to get the user
      if (signUpData.error && signUpData.error.message === 'EMAIL_EXISTS') {
        // Use signInWithPassword to get the uid
        const signInRes = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${FB_API_KEY}`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ email: entry.email, password: entry.password || 'edupulse123' })
        });
        const signInData = await signInRes.json();
        if (!signInRes.ok) throw new Error('Could not create or find auth user');
        entry.uid = signInData.localId;
      } else {
        throw new Error(signUpData.error ? signUpData.error.message : 'Failed to create auth user');
      }
    } else {
      entry.uid = signUpData.localId;
    }

    const teacherId = entry.uid || uuidv4();
    const classes = (entry.classes || []).join(',');
    const subjects = (entry.subjects || []).join(',');

    // Store teacher in local SQLite
    runSql(
      `CREATE TABLE IF NOT EXISTS teachers (
        id TEXT PRIMARY KEY, name TEXT NOT NULL, email TEXT,
        phone TEXT, classes TEXT, subjects TEXT, photo TEXT,
        role TEXT DEFAULT 'teacher', active INTEGER DEFAULT 1,
        created_at TEXT
      )`
    );
    runSql(
      `INSERT OR REPLACE INTO teachers (id, name, email, phone, classes, subjects, photo, role, active, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, 'teacher', 1, ?)`,
      [teacherId, entry.name, entry.email, entry.phone || '',
       classes, subjects, entry.photo || '', new Date().toISOString()]
    );
    saveDB();

    // Store under school path in Firebase
    const teacherData = {
      name: entry.name, email: entry.email, phone: entry.phone || '',
      classes: entry.classes || [], subjects: entry.subjects || [],
      photo: entry.photo || '', role: 'teacher', active: true,
      uid: teacherId, created_at: new Date().toISOString()
    };
    await fetch(`${FB_DB_URL}/${base}/teachers/${teacherId}.json`, {
      method: 'PUT',
      body: JSON.stringify(teacherData),
      headers: { 'Content-Type': 'application/json' }
    });

    // Remove pending
    await fetch(`${FB_DB_URL}/${base}/pending_teachers/${id}.json`, {
      method: 'DELETE'
    });

    res.json({ success: true, message: 'Teacher approved', teacher_id: teacherId });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

app.post('/api/pending-teachers/:id/reject', async (req, res) => {
  try {
    const base = `schools/${schoolId}`;
    await fetch(`${FB_DB_URL}/${base}/pending_teachers/${req.params.id}.json`, {
      method: 'DELETE'
    });
    res.json({ success: true, message: 'Teacher registration rejected' });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

// ─── HEALTH & SCHOOL INFO ─────────────────────────────────────────────────────

app.get('/api/health', (req, res) => {
  res.json({ success: true, message: 'EduPulse API running', schoolId, school_name: schoolName, timestamp: new Date().toISOString() });
});

app.get('/api/school-info', (req, res) => {
  res.json({ success: true, schoolId, school_name: schoolName, setup_needed: !setupComplete });
});



// Start server
initDB().then(() => {
  // Load school name from SQLite if not in school.json
  if (!schoolName) {
    const rows = query(`SELECT value FROM settings WHERE key = 'school_name'`);
    if (rows.length && rows[0].value) {
      schoolName = rows[0].value;
      saveSchoolConfig();
    }
  }
  app.listen(PORT, '0.0.0.0', () => {
    console.log(`🏫 School ID: ${schoolId}`);
    console.log(`🏫 School Name: ${schoolName || '(not set)'}`);
    console.log(`🚀 EduPulse API running on http://localhost:${PORT}`);
    if (!setupComplete) {
      const { networkInterfaces } = require('os');
      const nets = networkInterfaces();
      const ips = [];
      for (const name in nets) {
        for (const net of nets[name]) {
          if (net.family === 'IPv4' && !net.internal) ips.push(net.address);
        }
      }
      console.log(`⚙️  First run! Set up your school at:`);
      console.log(`   http://localhost:${PORT}/setup.html`);
      ips.forEach(ip => console.log(`   http://${ip}:${PORT}/setup.html`));
    }
    // Sync local data to Firebase RTDB
    syncToFirebase().then(() => {
      console.log('✅ Initial sync to Firebase complete');
      scheduleSync();
    });
  });
}).catch(console.error);
