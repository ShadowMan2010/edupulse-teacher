const functions = require('firebase-functions');
const admin = require('firebase-admin');
const express = require('express');
const cors = require('cors');
const { v4: uuidv4 } = require('uuid');
const dayjs = require('dayjs');
const XLSX = require('xlsx');
const PDFDocument = require('pdfkit');

admin.initializeApp();
const db = admin.database();
const bucket = admin.storage().bucket();

const app = express();
app.use(cors({ origin: true }));
app.use(express.json());

// ── Helpers ──

function getVal(arr, key) {
  if (!arr || !arr.length) return 0;
  return arr[0][key] !== undefined ? arr[0][key] : 0;
}

function toList(snapshot) {
  const data = snapshot.val();
  const arr = [];
  for (const k in data) {
    if (data.hasOwnProperty(k)) arr.push({ id: k, ...data[k] });
  }
  return arr;
}

async function getSetting(key) {
  const snap = await db.ref('settings/' + key).once('value');
  return snap.val();
}

// ── Health ──

app.get('/api/health', (req, res) => {
  res.json({ success: true, message: 'EduPulse API running', timestamp: new Date().toISOString() });
});

// ── Students ──

app.get('/api/students', async (req, res) => {
  try {
    const snap = await db.ref('students').once('value');
    let list = toList(snap).filter(s => s.active !== false);
    const { search, class: cls } = req.query;
    if (search) {
      const q = search.toLowerCase();
      list = list.filter(s => s.name?.toLowerCase().includes(q) || s.roll_no?.toLowerCase().includes(q));
    }
    if (cls) list = list.filter(s => s.class === cls);
    list.sort((a, b) => (a.name || '').localeCompare(b.name || ''));
    res.json({ success: true, data: list });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

app.get('/api/students/:id', async (req, res) => {
  try {
    const snap = await db.ref('students/' + req.params.id).once('value');
    const s = snap.val();
    if (!s || s.active === false) return res.status(404).json({ success: false, error: 'Not found' });
    res.json({ success: true, data: { id: req.params.id, ...s } });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

// ── Attendance ──

app.post('/api/attendance/scan', async (req, res) => {
  try {
    const { student_id, qr_data } = req.body;
    let sid = student_id;

    if (!sid && qr_data) {
      try {
        const parsed = JSON.parse(qr_data);
        sid = parsed.student_id;
      } catch {
        return res.status(400).json({ success: false, error: 'Invalid QR data' });
      }
    }

    if (!sid) return res.status(400).json({ success: false, error: 'student_id required' });

    const studentSnap = await db.ref('students/' + sid).once('value');
    const student = studentSnap.val();
    if (!student || student.active === false) return res.status(404).json({ success: false, error: 'Student not found or inactive' });

    // Settings
    const settingsSnap = await db.ref('settings').once('value');
    const settings = settingsSnap.val() || {};
    const windowMins = parseInt(settings.duplicate_window_minutes) || 60;
    const startTime = settings.school_start_time || '08:30';
    const lateThreshold = parseInt(settings.late_threshold_minutes) || 30;
    const today = dayjs().format('YYYY-MM-DD');
    const now = dayjs();

    // Duplicate check
    const attSnap = await db.ref('attendance').once('value');
    const allAtt = toList(attSnap);
    const dupWindow = windowMins * 60 * 1000;
    const dups = allAtt.filter(a =>
      a.student_id === sid && a.date === today &&
      (now.valueOf() - dayjs(today + 'T' + a.time).valueOf()) < dupWindow
    );

    if (dups.length) {
      return res.status(409).json({
        success: false, error: 'Duplicate scan',
        student: { id: sid, ...student },
        last_scan: dups[0]
      });
    }

    // Status
    const [sh, sm] = startTime.split(':').map(Number);
    const schoolStart = now.hour(sh).minute(sm).second(0);
    const status = now.diff(schoolStart, 'minute') > lateThreshold ? 'late' : 'present';

    const id = uuidv4();
    const time = now.format('HH:mm:ss');
    await db.ref('attendance/' + id).set({
      student_id: sid, date: today, time, status, scanned_by: 'kiosk'
    });

    res.json({
      success: true, message: 'Attendance marked: ' + status,
      student: { id: sid, ...student },
      attendance: { id, student_id: sid, date: today, time, status }
    });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

app.get('/api/attendance', async (req, res) => {
  try {
    const snap = await db.ref('attendance').once('value');
    let list = toList(snap);
    const { date, student_id, from, to } = req.query;

    if (date) list = list.filter(a => a.date === date);
    if (student_id) list = list.filter(a => a.student_id === student_id);
    if (from) list = list.filter(a => a.date >= from);
    if (to) list = list.filter(a => a.date <= to);

    list.sort((a, b) => b.date?.localeCompare(a.date) || b.time?.localeCompare(a.time));

    // Join student names
    const studentsSnap = await db.ref('students').once('value');
    const students = toList(studentsSnap);
    const studentMap = {};
    students.forEach(s => studentMap[s.id] = s);

    const data = list.map(a => ({
      ...a,
      name: studentMap[a.student_id]?.name || 'Unknown',
      roll_no: studentMap[a.student_id]?.roll_no || '',
      class: studentMap[a.student_id]?.class || '',
      photo: studentMap[a.student_id]?.photo || null
    }));

    res.json({ success: true, data });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

app.get('/api/attendance/summary', async (req, res) => {
  try {
    const date = req.query.date || dayjs().format('YYYY-MM-DD');

    const studentsSnap = await db.ref('students').once('value');
    const students = toList(studentsSnap).filter(s => s.active !== false);
    const total = students.length;

    const attSnap = await db.ref('attendance').once('value');
    const records = toList(attSnap).filter(a => a.date === date);

    const present = records.filter(r => r.status === 'present').length;
    const late = records.filter(r => r.status === 'late').length;
    const scanned = present + late;
    const absent = total - scanned;

    const classMap = {};
    students.forEach(s => {
      if (!classMap[s.class]) classMap[s.class] = { total: 0, present: 0 };
      classMap[s.class].total++;
    });
    records.forEach(r => {
      const s = students.find(st => st.id === r.student_id);
      if (s && classMap[s.class]) classMap[s.class].present++;
    });

    const byClass = Object.entries(classMap)
      .map(([cls, v]) => ({ class: cls, total: v.total, present: v.present }))
      .sort((a, b) => a.class.localeCompare(b.class));

    res.json({
      success: true,
      data: {
        date, total, present: scanned, absent, late,
        percentage: total > 0 ? Math.round((scanned / total) * 100) : 0,
        by_class: byClass
      }
    });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

app.get('/api/attendance/recent', async (req, res) => {
  try {
    const limit = parseInt(req.query.limit) || 20;
    const snap = await db.ref('attendance').once('value');
    let list = toList(snap);

    list.sort((a, b) => b.date?.localeCompare(a.date) || b.time?.localeCompare(a.time));
    list = list.slice(0, limit);

    const studentsSnap = await db.ref('students').once('value');
    const students = toList(studentsSnap);
    const studentMap = {};
    students.forEach(s => studentMap[s.id] = s);

    const data = list.map(a => ({
      ...a,
      name: studentMap[a.student_id]?.name || 'Unknown',
      roll_no: studentMap[a.student_id]?.roll_no || '',
      class: studentMap[a.student_id]?.class || '',
      photo: studentMap[a.student_id]?.photo || null
    }));

    res.json({ success: true, data });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

// ── Reports ──

app.get('/api/reports/excel', async (req, res) => {
  try {
    const { from, to, class: cls } = req.query;
    const date = from || dayjs().format('YYYY-MM-DD');
    const endDate = to || date;

    const studentsSnap = await db.ref('students').once('value');
    let students = toList(studentsSnap).filter(s => s.active !== false);
    if (cls) students = students.filter(s => s.class === cls);

    const attSnap = await db.ref('attendance').once('value');
    const allAtt = toList(attSnap).filter(a => a.date >= date && a.date <= endDate);
    const attMap = {};
    allAtt.forEach(a => {
      if (!attMap[a.student_id]) attMap[a.student_id] = [];
      attMap[a.student_id].push(a);
    });

    const wsData = [['Name', 'Roll No', 'Class', 'Section', 'Date', 'Time', 'Status']];
    students.forEach(s => {
      const atts = attMap[s.id] || [];
      if (atts.length) {
        atts.forEach(a => {
          wsData.push([s.name, s.roll_no, s.class, s.section || '', a.date, a.time, a.status]);
        });
      } else {
        wsData.push([s.name, s.roll_no, s.class, s.section || '', '-', '-', 'absent']);
      }
    });

    const wb = XLSX.utils.book_new();
    const ws = XLSX.utils.aoa_to_sheet(wsData);
    ws['!cols'] = [{ wch: 20 }, { wch: 12 }, { wch: 10 }, { wch: 10 }, { wch: 12 }, { wch: 10 }, { wch: 10 }];
    XLSX.utils.book_append_sheet(wb, ws, 'Attendance');

    const buf = XLSX.write(wb, { type: 'buffer', bookType: 'xlsx' });
    res.setHeader('Content-Type', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
    res.setHeader('Content-Disposition', 'attachment; filename="attendance_' + date + '.xlsx"');
    res.send(buf);
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

app.get('/api/reports/pdf', async (req, res) => {
  try {
    const { from, to, class: cls } = req.query;
    const date = from || dayjs().format('YYYY-MM-DD');
    const endDate = to || date;

    const studentsSnap = await db.ref('students').once('value');
    let students = toList(studentsSnap).filter(s => s.active !== false);
    if (cls) students = students.filter(s => s.class === cls);

    const attSnap = await db.ref('attendance').once('value');
    const allAtt = toList(attSnap).filter(a => a.date >= date && a.date <= endDate);

    const classMap = {};
    students.forEach(s => {
      if (!classMap[s.class]) classMap[s.class] = { total: 0, present: 0 };
      classMap[s.class].total++;
    });
    allAtt.forEach(a => {
      const s = students.find(st => st.id === a.student_id);
      if (s && classMap[s.class]) classMap[s.class].present++;
    });

    const doc = new PDFDocument({ margin: 50, size: 'A4' });
    res.setHeader('Content-Type', 'application/pdf');
    res.setHeader('Content-Disposition', 'attachment; filename="report_' + date + '.pdf"');
    doc.pipe(res);

    doc.fontSize(22).fillColor('#00ffff').text('EduPulse Attendance Report', { align: 'center' });
    doc.fontSize(12).fillColor('#888').text('Period: ' + date + ' to ' + endDate, { align: 'center' });
    doc.moveDown(2);
    doc.fontSize(14).fillColor('#00ffff').text('Class-wise Summary');
    doc.moveDown(0.5);

    Object.entries(classMap).sort().forEach(([cls, v]) => {
      const pct = v.total > 0 ? Math.round((v.present / v.total) * 100) : 0;
      doc.fontSize(11).fillColor('#ccc')
        .text('Class ' + cls + ': ' + v.present + '/' + v.total + ' present (' + pct + '%)');
    });

    doc.end();
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

// ── Settings ──

app.get('/api/settings', async (req, res) => {
  try {
    const snap = await db.ref('settings').once('value');
    res.json({ success: true, data: snap.val() || {} });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

app.put('/api/settings', async (req, res) => {
  try {
    await db.ref('settings').update(req.body);
    res.json({ success: true, message: 'Settings updated' });
  } catch (e) {
    res.status(500).json({ success: false, error: e.message });
  }
});

// ── Export Cloud Function ──

exports.api = functions.https.onRequest(app);
