// ── EduPulse Firebase Configuration (v8 compat SDK) ──

var firebaseConfig = {
  apiKey: "AIzaSyA7jzhxK7dDJ4Aa-TPNI891DBbng6eAg4o",
  authDomain: "cyberpunk-attendance.firebaseapp.com",
  databaseURL: "https://cyberpunk-attendance-default-rtdb.asia-southeast1.firebasedatabase.app",
  projectId: "cyberpunk-attendance",
  storageBucket: "cyberpunk-attendance.firebasestorage.app",
  messagingSenderId: "314544638337",
  appId: "1:314544638337:web:cf71511e55a998d3a04080"
};

firebase.initializeApp(firebaseConfig);

var auth = firebase.auth();
var db = firebase.database();
var storage = firebase.storage();

// ── Auth ──
var currentUser = null;
auth.onAuthStateChanged(function(user) {
  currentUser = user;
});

function loginWithEmail(email, password) {
  // Try Firebase first, fall back to demo mode
  return auth.signInWithEmailAndPassword(email, password).catch(function(err) {
    // Demo mode: if Firebase isn't configured, use local credentials
    var valid = {email: 'admin@edupulse.dev', password: 'admin123', role: 'admin'};
    if (email === valid.email && password === valid.password) {
      sessionStorage.setItem('edupulse_demo', JSON.stringify({role: valid.role, email: valid.email}));
      return Promise.resolve({user: {uid: 'demo-admin', email: valid.email}});
    }
    // Show the actual Firebase error for other cases
    throw new Error(err.message || 'Login failed');
  });
}

function logoutUser() {
  return auth.signOut();
}

// ── Low-level helpers ──
function dbGet(path) {
  return db.ref(path).once('value').then(function(s) { return s.val(); });
}
function dbSet(path, data) {
  return db.ref(path).set(data);
}
function dbPush(path, data) {
  return db.ref(path).push(data);
}
function dbUpdate(path, data) {
  return db.ref(path).update(data);
}
function dbRemove(path) {
  return db.ref(path).remove();
}
function dbList(path) {
  return dbGet(path).then(function(data) {
    var arr = [];
    for (var k in data) {
      if (data.hasOwnProperty(k)) { arr.push(Object.assign({id: k}, data[k])); }
    }
    return arr;
  });
}

// ── Storage ──
function uploadPhoto(file, path) {
  var ref = storage.ref(path);
  return ref.put(file).then(function(snapshot) {
    return snapshot.ref.getDownloadURL();
  });
}

// ── Role ──
function getCurrentUserRole() {
  if (!currentUser) return Promise.resolve(null);
  return dbGet('users/' + currentUser.uid + '/role');
}

// ── STUDENT CRUD ──
function getStudents() {
  // Try local API first, fall back to Firebase
  return fetch('http://localhost:3000/api/students', {signal: AbortSignal.timeout(3000)})
    .then(function(r) { return r.json(); })
    .then(function(d) {
      if (d.success && Array.isArray(d.data)) return d.data.filter(function(s) { return s.active !== false; });
      return fbGetStudents();
    })
    .catch(function() { return fbGetStudents(); });
}

function fbGetStudents() {
  return dbList('students').then(function(arr) {
    return arr.filter(function(s) { return s.active !== false; });
  });
}

function getStudent(id) {
  return fetch('http://localhost:3000/api/students/' + id, {signal: AbortSignal.timeout(3000)})
    .then(function(r) { return r.json(); })
    .then(function(d) { return d.success ? d.data : dbGet('students/' + id); })
    .catch(function() { return dbGet('students/' + id); });
}

function addStudent(data) {
  return fetch('http://localhost:3000/api/students', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(data),
    signal: AbortSignal.timeout(3000)
  }).then(function(r) { return r.json(); }).then(function(d) {
    if (d.success) return d.data;
    return fbAddStudent(data);
  }).catch(function() { return fbAddStudent(data); });
}

function fbAddStudent(data) {
  var ref = db.ref('students').push();
  data.active = true;
  data.created_at = firebase.database.ServerValue.TIMESTAMP;
  return ref.set(data).then(function() { return Object.assign({id: ref.key}, data); });
}

function updateStudent(id, data) {
  return fetch('http://localhost:3000/api/students/' + id, {
    method: 'PUT',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(data),
    signal: AbortSignal.timeout(3000)
  }).then(function(r) { return r.json(); }).then(function(d) {
    if (d.success) return d.data;
    return db.ref('students/' + id).update(data);
  }).catch(function() { return db.ref('students/' + id).update(data); });
}

function deactivateStudent(id) {
  return fetch('http://localhost:3000/api/students/' + id, {
    method: 'DELETE',
    signal: AbortSignal.timeout(3000)
  }).then(function(r) { return r.json(); }).then(function(d) {
    if (d.success) return;
    return db.ref('students/' + id).update({active: false});
  }).catch(function() { return db.ref('students/' + id).update({active: false}); });
}

// ── ATTENDANCE ──
function getTodaysDate() {
  var d = new Date();
  return d.getFullYear() + '-' + String(d.getMonth()+1).padStart(2,'0') + '-' + String(d.getDate()).padStart(2,'0');
}

function getTimeStr() {
  var d = new Date();
  return String(d.getHours()).padStart(2,'0') + ':' + String(d.getMinutes()).padStart(2,'0') + ':' + String(d.getSeconds()).padStart(2,'0');
}

function getSettings() {
  return fetch('http://localhost:3000/api/settings', {signal: AbortSignal.timeout(3000)})
    .then(function(r) { return r.json(); })
    .then(function(d) {
      if (d.success && d.data) return d.data;
      return fbGetSettings();
    })
    .catch(function() { return fbGetSettings(); });
}

function fbGetSettings() {
  return dbGet('settings').then(function(s) {
    return s || { duplicate_window_minutes: 60, school_name: 'EduPulse Academy', late_threshold_minutes: 30, school_start_time: '08:30' };
  });
}

function recordAttendance(studentId, status, scannedBy) {
  scannedBy = scannedBy || 'kiosk';
  var date = getTodaysDate();
  var time = getTimeStr();
  var data = { student_id: studentId, date: date, time: time, status: status || 'present', scanned_by: scannedBy };
  return fetch('http://localhost:3000/api/attendance/scan', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(data),
    signal: AbortSignal.timeout(3000)
  }).then(function(r) { return r.json(); }).then(function(d) {
    if (d.success) return d.data || d.attendance;
    return fbRecordAttendance(studentId, status, scannedBy);
  }).catch(function() { return fbRecordAttendance(studentId, status, scannedBy); });
}

function fbRecordAttendance(studentId, status, scannedBy) {
  var ref = db.ref('attendance').push();
  var data = { student_id: studentId, date: getTodaysDate(), time: getTimeStr(), status: status || 'present', scanned_by: scannedBy || 'kiosk' };
  return ref.set(data).then(function() { return Object.assign({id: ref.key}, data); });
}

function scanAttendance(studentId) {
  var date = getTodaysDate();
  return getSettings().then(function(settings) {
    var windowMins = parseInt(settings.duplicate_window_minutes) || 60;
    var startTime = settings.school_start_time || '08:30';
    var lateThreshold = parseInt(settings.late_threshold_minutes) || 30;
    var now = new Date();

    return getStudent(studentId).then(function(student) {
      if (!student || student.active === false) throw new Error('Student not found or inactive');

      // Duplicate check: look for scans by this student today within the window
      return dbGet('attendance').then(function(all) {
        var dupWindow = windowMins * 60 * 1000;
        var dups = [];
        for (var k in all) {
          if (all.hasOwnProperty(k)) {
            var a = all[k];
            if (a.student_id === studentId && a.date === date) {
              var scanTime = new Date(date + 'T' + a.time).getTime();
              if (now.getTime() - scanTime < dupWindow) {
                dups.push(Object.assign({id: k}, a));
              }
            }
          }
        }
        if (dups.length > 0) {
          throw { duplicate: true, message: 'Duplicate scan', student: student, last_scan: dups[0] };
        }

        // Determine status: present vs late
        var parts = startTime.split(':');
        var schoolStart = new Date();
        schoolStart.setHours(parseInt(parts[0]), parseInt(parts[1]), 0);
        var diffMin = (now.getTime() - schoolStart.getTime()) / 60000;
        var status = diffMin > lateThreshold ? 'late' : 'present';

        return recordAttendance(studentId, status).then(function(att) {
          return { success: true, message: 'Attendance marked: ' + status, student: student, attendance: att };
        });
      });
    });
  });
}

function getAttendance(filters) {
  filters = filters || {};
  return fetch('http://localhost:3000/api/attendance', {signal: AbortSignal.timeout(3000)})
    .then(function(r) { return r.json(); })
    .then(function(d) {
      if (d.success && Array.isArray(d.data)) {
        var arr = d.data;
        if (filters.date) arr = arr.filter(function(a) { return a.date === filters.date; });
        if (filters.student_id) arr = arr.filter(function(a) { return a.student_id === filters.student_id; });
        if (filters.status) arr = arr.filter(function(a) { return a.status === filters.status; });
        arr.sort(function(a, b) { return b.date.localeCompare(a.date) || b.time.localeCompare(a.time); });
        return arr;
      }
      return fbGetAttendance(filters);
    })
    .catch(function() { return fbGetAttendance(filters); });
}

function fbGetAttendance(filters) {
  return dbGet('attendance').then(function(all) {
    var arr = [];
    for (var k in all) {
      if (all.hasOwnProperty(k)) {
        var a = Object.assign({id: k}, all[k]);
        if (filters.date && a.date !== filters.date) continue;
        if (filters.student_id && a.student_id !== filters.student_id) continue;
        if (filters.status && a.status !== filters.status) continue;
        if (filters.from && a.date < filters.from) continue;
        if (filters.to && a.date > filters.to) continue;
        arr.push(a);
      }
    }
    arr.sort(function(a, b) { return b.date.localeCompare(a.date) || b.time.localeCompare(a.time); });
    return arr;
  });
}

function getAttendanceSummary(date) {
  date = date || getTodaysDate();
  return Promise.all([getStudents(), getAttendance({date: date}), getSettings()]).then(function(results) {
    var students = results[0];
    var records = results[1];
    var settings = results[2];

    var total = students.length;
    var present = records.filter(function(r) { return r.status === 'present'; }).length;
    var late = records.filter(function(r) { return r.status === 'late'; }).length;
    var scanned = present + late;
    var absent = total - scanned;

    // Class-wise
    var classMap = {};
    students.forEach(function(s) {
      if (!classMap[s.class]) classMap[s.class] = { total: 0, present: 0 };
      classMap[s.class].total++;
    });
    records.forEach(function(r) {
      var s = students.find(function(st) { return st.id === r.student_id; });
      if (s && classMap[s.class]) classMap[s.class].present++;
    });
    var byClass = [];
    for (var cls in classMap) {
      if (classMap.hasOwnProperty(cls)) {
        byClass.push({ class: cls, total: classMap[cls].total, present: classMap[cls].present });
      }
    }
    byClass.sort(function(a, b) { return a.class.localeCompare(b.class); });

    return {
      date: date, total: total, present: scanned, absent: absent, late: late,
      percentage: total > 0 ? Math.round((scanned / total) * 100) : 0,
      by_class: byClass
    };
  });
}

function getRecentAttendance(limit) {
  limit = limit || 20;
  return getAttendance().then(function(arr) {
    return arr.slice(0, limit);
  });
}

// ── QR CODE ──
function getQRDataURL(student) {
  var data = JSON.stringify({ student_id: student.id, roll_no: student.roll_no, name: student.name });
  // Use qrcode-generator library (loaded via CDN on pages that need QR)
  if (typeof qrcode !== 'undefined') {
    var qr = qrcode(0, 'M');
    qr.addData(data);
    qr.make();
    return qr.createDataURL(12, 16);
  }
  // Fallback to Google Charts
  return 'https://chart.googleapis.com/chart?cht=qr&chs=300x300&chl=' + encodeURIComponent(data) + '&choe=UTF-8';
}

function getQRImageUrl(student) {
  return getQRDataURL(student);
}

// ── REPORTS ──
function generateAttendanceData(filters) {
  return getAttendance(filters).then(function(records) {
    return Promise.all(records.map(function(r) {
      return getStudent(r.student_id).then(function(s) {
        return { name: s ? s.name : 'Unknown', roll_no: s ? s.roll_no : '', class: s ? s.class : '', section: s ? (s.section || '') : '', date: r.date, time: r.time, status: r.status };
      });
    }));
  });
}

// ── SETTINGS HELPERS ──
function saveSettings(data) {
  return dbSet('settings', data);
}
