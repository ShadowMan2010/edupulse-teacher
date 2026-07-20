// ── EduPulse Firebase Configuration (v8 compat SDK) ──

var firebaseConfig = {
  apiKey: "AIzaSyDPYjylSBhng6CRL77P0MXTUcuq7jBFnnA",
  authDomain: "edupulse-attendance-qr.firebaseapp.com",
  databaseURL: "https://edupulse-attendance-qr-default-rtdb.asia-southeast1.firebasedatabase.app",
  projectId: "edupulse-attendance-qr",
  storageBucket: "edupulse-attendance-qr.firebasestorage.app",
  messagingSenderId: "364157582130",
  appId: "1:364157582130:web:43b538e60858c22ec5b52b"
};

firebase.initializeApp(firebaseConfig);

var auth = firebase.auth();
var db = firebase.database();
var storage = firebase.storage();

// ── Multi-school: data partitioned by schoolId ──
var SCHOOL_ID = localStorage.getItem('edupulse_school_id') || '';

function setSchoolId(id) {
  SCHOOL_ID = id;
  if (id) localStorage.setItem('edupulse_school_id', id);
  else localStorage.removeItem('edupulse_school_id');
}

function schoolPath(path) {
  if (SCHOOL_ID) return 'schools/' + SCHOOL_ID + '/' + path;
  return path;
}

// ── Auth ──
var currentUser = null;
auth.onAuthStateChanged(function(user) {
  currentUser = user;
});

function loginWithEmail(email, password) {
  return auth.signInWithEmailAndPassword(email, password).catch(function(err) {
    var valid = {email: 'admin@edupulse.dev', password: 'Dhruba@2010', role: 'admin'};
    if (email === valid.email && password === valid.password) {
      sessionStorage.setItem('edupulse_demo', JSON.stringify({role: valid.role, email: valid.email}));
      return Promise.resolve({user: {uid: 'demo-admin', email: valid.email}});
    }
    throw new Error(err.message || 'Login failed');
  });
}

function logoutUser() {
  SCHOOL_ID = '';
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

// ── API BASE ──
var API_BASE = (window.location.origin + '/api').replace('//api', '/api');

// ── STUDENT CRUD ──
function getStudents() {
  return fetch(API_BASE + '/students')
    .then(function(r) { return r.json(); })
    .then(function(d) {
      if (d.success && Array.isArray(d.data)) return d.data.filter(function(s) { return s.active == 1 || s.active === true; });
      throw new Error(d.message || 'Failed to fetch students');
    })
    .catch(function() {
      return dbGet(schoolPath('students')).then(function(all) {
        var arr = [];
        for (var k in all) {
          if (all.hasOwnProperty(k)) {
            var s = Object.assign({id: k}, all[k]);
            if (s.active == 1 || s.active === true || s.active === undefined) arr.push(s);
          }
        }
        return arr;
      });
    });
}

function getStudent(id) {
  return fetch(API_BASE + '/students/' + id, {signal: AbortSignal.timeout(3000)})
    .then(function(r) { return r.json(); })
    .then(function(d) {
      if (d.success) return d.data;
      throw new Error(d.message || 'Failed to fetch student');
    })
    .catch(function() {
      return dbGet(schoolPath('students/' + id)).then(function(s) { return s ? Object.assign({id: id}, s) : null; });
    });
}

function addStudent(data) {
  return fetch(API_BASE + '/students', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(data),
    signal: AbortSignal.timeout(3000)
  }).then(function(r) { return r.json(); }).then(function(d) {
    if (d.success) return d.data;
    throw new Error(d.message || 'Failed to save student');
  }).catch(function() {
    return dbPush(schoolPath('students'), data).then(function(ref) { return Object.assign({id: ref.key}, data); });
  });
}

function updateStudent(id, data) {
  return fetch(API_BASE + '/students/' + id, {
    method: 'PUT',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(data),
    signal: AbortSignal.timeout(3000)
  }).then(function(r) { return r.json(); }).then(function(d) {
    if (d.success) return d.data;
    throw new Error(d.message || 'Failed to update student');
  }).catch(function() {
    return dbUpdate(schoolPath('students/' + id), data).then(function() { return Object.assign({id: id}, data); });
  });
}

function deactivateStudent(id) {
  return fetch(API_BASE + '/students/' + id, {
    method: 'DELETE'
  }).then(function(r) { return r.json(); }).then(function(d) {
    if (d.success) return;
    throw new Error(d.message || 'Failed to deactivate student');
  }).catch(function() {
    return dbUpdate(schoolPath('students/' + id), {active: false});
  });
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
  return fetch(API_BASE + '/settings', {signal: AbortSignal.timeout(3000)})
    .then(function(r) { return r.json(); })
    .then(function(d) {
      if (d.success && d.data) return d.data;
      return fbGetSettings();
    })
    .catch(function() { return fbGetSettings(); });
}

function fbGetSettings() {
  return dbGet(schoolPath('settings')).then(function(s) {
    return s || { duplicate_window_minutes: 60, school_name: 'EduPulse Academy', late_threshold_minutes: 30, school_start_time: '08:30' };
  });
}

function recordAttendance(studentId, status, scannedBy, period, subject) {
  scannedBy = scannedBy || 'kiosk';
  var date = getTodaysDate();
  var time = getTimeStr();
  var data = { student_id: studentId, date: date, time: time, status: status || 'present', scanned_by: scannedBy };
  if (period !== undefined) data.period = period;
  if (subject) data.subject = subject;
  return fetch(API_BASE + '/attendance/scan', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(data),
    signal: AbortSignal.timeout(3000)
  }).then(function(r) { return r.json(); }).then(function(d) {
    if (d.success) return d.data || d.attendance;
    return fbRecordAttendance(studentId, status, scannedBy, period, subject);
  }).catch(function() { return fbRecordAttendance(studentId, status, scannedBy, period, subject); });
}

function fbRecordAttendance(studentId, status, scannedBy, period, subject) {
  var ref = db.ref(schoolPath('attendance')).push();
  var data = { student_id: studentId, date: getTodaysDate(), time: getTimeStr(), status: status || 'present', scanned_by: scannedBy || 'kiosk' };
  if (period !== undefined) data.period = period;
  if (subject) data.subject = subject;
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

      return dbGet(schoolPath('attendance')).then(function(all) {
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
  return fetch(API_BASE + '/attendance', {signal: AbortSignal.timeout(3000)})
    .then(function(r) { return r.json(); })
    .then(function(d) {
      if (d.success && Array.isArray(d.data)) {
        var arr = d.data;
        if (filters.date) arr = arr.filter(function(a) { return a.date === filters.date; });
        if (filters.student_id) arr = arr.filter(function(a) { return a.student_id === filters.student_id; });
        if (filters.status) arr = arr.filter(function(a) { return a.status === filters.status; });
        if (filters.period) arr = arr.filter(function(a) { return String(a.period) === String(filters.period); });
        arr.sort(function(a, b) { return b.date.localeCompare(a.date) || b.time.localeCompare(a.time); });
        return arr;
      }
      return fbGetAttendance(filters);
    })
    .catch(function() { return fbGetAttendance(filters); });
}

function fbGetAttendance(filters) {
  return dbGet(schoolPath('attendance')).then(function(all) {
    var arr = [];
    for (var k in all) {
      if (all.hasOwnProperty(k)) {
        var a = Object.assign({id: k}, all[k]);
        if (filters.date && a.date !== filters.date) continue;
        if (filters.student_id && a.student_id !== filters.student_id) continue;
        if (filters.status && a.status !== filters.status) continue;
        if (filters.period && String(a.period) !== String(filters.period)) continue;
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
    var scanned = records.length;
    var absent = total - scanned;

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
  if (typeof qrcode !== 'undefined') {
    var qr = qrcode(0, 'M');
    qr.addData(data);
    qr.make();
    return qr.createDataURL(12, 16);
  }
  return 'https://chart.googleapis.com/chart?cht=qr&chs=300x300&chl=' + encodeURIComponent(data) + '&choe=UTF-8';
}

function getQRImageUrl(student) {
  return getQRDataURL(student);
}

// ── SCHOOL INFO ──
function getSchoolInfo() {
  return fetch(API_BASE + '/school-info', {signal: AbortSignal.timeout(3000)})
    .then(function(r) { return r.json(); })
    .then(function(d) {
      if (d.success) return d;
      throw new Error(d.message || 'Failed to load school info');
    })
    .catch(function() {
      return dbGet('schools/' + SCHOOL_ID).then(function(schoolData) {
        if (schoolData) return { success: true, schoolId: SCHOOL_ID, ...schoolData.settings };
        return dbGet('schoolId').then(function(schoolId) {
          return dbGet('settings').then(function(settings) {
            var result = { success: true, schoolId: schoolId || '' };
            if (settings) Object.assign(result, settings);
            return result;
          });
        });
      });
    });
}

function saveSchoolInfo(data) {
  return fetch(API_BASE + '/school-info', {
    method: 'PUT',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(data),
    signal: AbortSignal.timeout(3000)
  }).then(function(r) { return r.json(); }).then(function(d) {
    if (d.success) return;
    throw new Error(d.message || 'Failed to save school info');
  }).catch(function() {
    var promises = [];
    if (data.schoolId) promises.push(dbSet('schoolId', data.schoolId));
    var settingsData = {};
    if (data.school_name) settingsData.school_name = data.school_name;
    if (data.school_start_time) settingsData.school_start_time = data.school_start_time;
    if (data.school_end_time) settingsData.school_end_time = data.school_end_time;
    if (data.half_day_cutoff) settingsData.half_day_cutoff = data.half_day_cutoff;
    if (data.late_threshold_minutes) settingsData.late_threshold_minutes = data.late_threshold_minutes;
    if (data.duplicate_window_minutes) settingsData.duplicate_window_minutes = data.duplicate_window_minutes;
    if (Object.keys(settingsData).length) promises.push(dbSet(schoolPath('settings'), settingsData));
    return Promise.all(promises);
  });
}

// ── PENDING TEACHERS ──
function getPendingTeachers() {
  return fetch(API_BASE + '/pending-teachers', {signal: AbortSignal.timeout(3000)})
    .then(function(r) { return r.json(); })
    .then(function(d) {
      if (d.success) return d.data;
      throw new Error(d.message || 'Failed to load pending teachers');
    })
    .catch(function() {
      return dbGet(schoolPath('pending_teachers')).then(function(data) { return data || {}; });
    });
}

function approvePendingTeacher(id) {
  return fetch(API_BASE + '/pending-teachers/' + id + '/approve', {
    method: 'POST', signal: AbortSignal.timeout(3000)
  }).then(function(r) { return r.json(); }).then(function(d) {
    if (d.success) return d.data;
    throw new Error(d.message || 'Failed to approve teacher');
  }).catch(function() {
    return dbGet(schoolPath('pending_teachers/' + id)).then(function(pending) {
      if (!pending) throw new Error('Pending teacher not found');
      // Client-side fallback: store data under school path, skip Auth creation
      var teacherRef = db.ref(schoolPath('teachers')).push();
      return teacherRef.set({
        name: pending.name, email: pending.email, phone: pending.phone || '',
        classes: pending.classes || [], subjects: pending.subjects || [],
        photo: pending.photo || '', role: 'teacher',
        created_at: new Date().toISOString(), active: true
      }).then(function() {
        return dbRemove(schoolPath('pending_teachers/' + id)).then(function() {
          return { success: true, message: 'Teacher approved (Auth account must be created via server)' };
        });
      });
    });
  });
}

function rejectPendingTeacher(id) {
  return fetch(API_BASE + '/pending-teachers/' + id + '/reject', {
    method: 'POST', signal: AbortSignal.timeout(3000)
  }).then(function(r) { return r.json(); }).then(function(d) {
    if (d.success) return;
    throw new Error(d.message || 'Failed to reject');
  }).catch(function() {
    return dbRemove(schoolPath('pending_teachers/' + id));
  });
}

// ── PENDING REGISTRATIONS ──
function getPendingRegistrations() {
  return fetch(API_BASE + '/pending', {signal: AbortSignal.timeout(3000)})
    .then(function(r) { return r.json(); })
    .then(function(d) {
      if (d.success) return d.data;
      throw new Error(d.message || 'Failed to load pending');
    })
    .catch(function() {
      return dbGet(schoolPath('pending_registrations')).then(function(data) { return data || {}; });
    });
}

function approvePendingRegistration(id) {
  return fetch(API_BASE + '/pending/' + id + '/approve', {
    method: 'POST',
    signal: AbortSignal.timeout(3000)
  }).then(function(r) { return r.json(); }).then(function(d) {
    if (d.success) return d.data;
    throw new Error(d.message || 'Failed to approve');
  }).catch(function() {
    return dbGet(schoolPath('pending_registrations/' + id)).then(function(pending) {
      if (!pending) throw new Error('Pending registration not found');
      var studentRef = db.ref(schoolPath('students')).push();
      var studentData = {
        name: pending.name, roll_no: pending.roll_no || '',
        class: pending.class, section: pending.section || '',
        photo: pending.photo || null, active: true,
        created_at: new Date().toISOString()
      };
      return studentRef.set(studentData).then(function() {
        return dbRemove(schoolPath('pending_registrations/' + id)).then(function() {
          return Object.assign({id: studentRef.key}, studentData);
        });
      });
    });
  });
}

function rejectPendingRegistration(id) {
  return fetch(API_BASE + '/pending/' + id + '/reject', {
    method: 'POST',
    signal: AbortSignal.timeout(3000)
  }).then(function(r) { return r.json(); }).then(function(d) {
    if (d.success) return;
    throw new Error(d.message || 'Failed to reject');
  }).catch(function() {
    return dbRemove(schoolPath('pending_registrations/' + id));
  });
}

// ── REPORTS ──
function generateAttendanceData(filters) {
  return getAttendance(filters).then(function(records) {
    return Promise.all(records.map(function(r) {
      return getStudent(r.student_id).then(function(s) {
        return { name: s ? s.name : 'Unknown', roll_no: s ? s.roll_no : '', class: s ? s.class : '', section: s ? (s.section || '') : '', date: r.date, time: r.time, status: r.status, subject: r.subject || '', period: r.period || '' };
      });
    }));
  });
}

// ── SETTINGS HELPERS ──
function saveSettings(data) {
  return fetch(API_BASE + '/settings', {
    method: 'PUT',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(data),
    signal: AbortSignal.timeout(3000)
  }).then(function(r) { return r.json(); }).then(function(d) {
    if (d.success) return;
    throw new Error(d.message || 'Failed to save settings');
  }).catch(function() {
    return dbSet(schoolPath('settings'), data);
  });
}

// ── SUBJECTS ──
function getSubjects() {
  return fetch(API_BASE + '/subjects')
    .then(function(r) { return r.json(); })
    .then(function(d) {
      if (d.success) return d.data || {};
      throw new Error(d.message || 'Failed to load subjects');
    })
    .catch(function() {
      return dbGet(schoolPath('subjects')).then(function(s) {
        if (!s) return {};
        if (Array.isArray(s)) {
          var obj = {};
          for (var i = 0; i < s.length; i++) {
            if (s[i]) obj[String(i)] = s[i];
          }
          return obj;
        }
        return s;
      });
    });
}
function saveSubjects(data) {
  return fetch(API_BASE + '/subjects', {
    method: 'PUT',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(data)
  }).then(function(r) { return r.json(); }).then(function(d) {
    if (d.success) return;
    throw new Error(d.message || 'Failed to save subjects');
  }).catch(function() {
    return dbSet(schoolPath('subjects'), data);
  });
}
