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

const app = express();
const PORT = process.env.PORT || 3000;
const DB_PATH = path.join(__dirname, 'edupulse.db');
const UPLOADS_DIR = path.join(__dirname, 'uploads');
const QR_DIR = path.join(__dirname, 'qrcodes');

// Ensure dirs exist
[UPLOADS_DIR, QR_DIR].forEach(d => fs.mkdirSync(d, { recursive: true }));

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
      FOREIGN KEY (student_id) REFERENCES students(id)
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
    const { name, roll_no, class: cls, section, email, phone, telegram_chat_id } = req.body;
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
      `INSERT INTO students (id, name, roll_no, class, section, email, phone, telegram_chat_id, photo, qr_code, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)`,
      [id, name, roll_no, cls, section || '', email || '', phone || '', telegram_chat_id || '', photo, qrCode]
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
    const { name, roll_no, class: cls, section, email, phone, telegram_chat_id, active } = req.body;
    const existing = rowsToObjects(query(`SELECT * FROM students WHERE id = ?`, [req.params.id]));
    if (!existing.length) return res.status(404).json({ success: false, error: 'Not found' });

    const photo = req.body.photo && req.body.photo.startsWith('data:image') ? req.body.photo : existing[0].photo;

    runSql(
      `UPDATE students SET name=?, roll_no=?, class=?, section=?, email=?, phone=?, telegram_chat_id=?, photo=?, active=? WHERE id=?`,
      [name || existing[0].name, roll_no || existing[0].roll_no, cls || existing[0].class,
       section ?? existing[0].section, email ?? existing[0].email, phone ?? existing[0].phone,
       telegram_chat_id ?? existing[0].telegram_chat_id,
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

// ─── ATTENDANCE ROUTES ────────────────────────────────────────────────────────

app.post('/api/attendance/scan', (req, res) => {
  try {
    const { student_id, qr_data } = req.body;
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
      `INSERT INTO attendance (id, student_id, date, time, status) VALUES (?, ?, ?, ?, ?)`,
      [id, sid, today, time, status]
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
    const { date, class: cls, student_id, from, to } = req.query;
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

// ─── HEALTH ───────────────────────────────────────────────────────────────────

app.get('/api/health', (req, res) => {
  res.json({ success: true, message: 'EduPulse API running', timestamp: new Date().toISOString() });
});

// Start server
initDB().then(() => {
  app.listen(PORT, '0.0.0.0', () => {
    console.log(`🚀 EduPulse API running on http://localhost:${PORT}`);
  });
}).catch(console.error);
