// ── EduPulse Theme Utilities ──

function initClock() {
  const el = document.getElementById('realtimeClock');
  if (!el) return;
  function tick() {
    const now = new Date();
    el.textContent = now.toLocaleTimeString('en-US', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });
  }
  tick();
  setInterval(tick, 1000);
}

function initScrollProgress() {
  const el = document.getElementById('scrollProgress');
  if (!el) return;
  window.addEventListener('scroll', () => {
    const h = document.documentElement.scrollHeight - window.innerHeight;
    el.style.width = `${(window.scrollY / h) * 100}%`;
  });
}

function initGlowBlob() {
  const blob = document.getElementById('glowBlob');
  if (!blob) return;
  document.addEventListener('mousemove', e => {
    blob.style.left = e.clientX + 'px';
    blob.style.top = e.clientY + 'px';
  });
}

function initFloatingShapes() {
  const container = document.getElementById('floatingShapes');
  if (!container) return;
  const types = ['shape-hex', 'shape-triangle', 'shape-ring'];
  for (let i = 0; i < 15; i++) {
    const s = document.createElement('div');
    s.className = `shape ${types[i % 3]}`;
    s.style.left = `${Math.random() * 100}%`;
    s.style.animationDuration = `${8 + Math.random() * 12}s`;
    s.style.animationDelay = `${Math.random() * 8}s`;
    container.appendChild(s);
  }
}

function init3DTilt() {
  document.querySelectorAll('.tilt-card').forEach(card => {
    card.addEventListener('mousemove', e => {
      const rect = card.getBoundingClientRect();
      const x = (e.clientX - rect.left) / rect.width - 0.5;
      const y = (e.clientY - rect.top) / rect.height - 0.5;
      card.style.transform = `rotateX(${y * -10}deg) rotateY(${x * 10}deg)`;
    });
    card.addEventListener('mouseleave', () => {
      card.style.transform = 'rotateX(0deg) rotateY(0deg)';
    });
  });
}

// ── TOAST SYSTEM ──
function showToast(message, type = 'info', duration = 4000) {
  const container = document.getElementById('toastContainer');
  if (!container) {
    const c = document.createElement('div');
    c.id = 'toastContainer';
    c.className = 'toast-container';
    document.body.appendChild(c);
  }
  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  const icons = { success: '✓', error: '✕', warning: '⚠', info: '◎' };
  toast.innerHTML = `<span>${icons[type] || '◎'}</span><span>${message}</span>`;
  document.getElementById('toastContainer').appendChild(toast);
  setTimeout(() => { toast.style.opacity = '0'; setTimeout(() => toast.remove(), 300); }, duration);
}

// ── MODAL SYSTEM ──
function openModal(id) { document.getElementById(id)?.classList.add('open'); }
function closeModal(id) { document.getElementById(id)?.classList.remove('open'); }

// ── API HELPERS ──
const API_BASE = 'http://localhost:3000/api';

async function apiGet(path) {
  const res = await fetch(`${API_BASE}${path}`);
  return res.json();
}

async function apiPost(path, body) {
  const res = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  return res.json();
}

async function apiPut(path, body) {
  const res = await fetch(`${API_BASE}${path}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  return res.json();
}

async function apiDelete(path) {
  const res = await fetch(`${API_BASE}${path}`, { method: 'DELETE' });
  return res.json();
}

// ── SIDEBAR ──
function toggleSidebar() {
  document.querySelector('.sidebar')?.classList.toggle('open');
}

function setActiveNav() {
  const path = window.location.pathname;
  document.querySelectorAll('.sidebar-link').forEach(link => {
    const href = link.getAttribute('href');
    if (href && path.endsWith(href)) link.classList.add('active');
  });
}

// ── FIREBASE AUTH REDIRECT GUARD ──
function requireAuth(redirectTo = '/pages/login.html') {
  // Demo mode bypass
  var demo = sessionStorage.getItem('edupulse_demo');
  if (demo) return;
  firebase.auth().onAuthStateChanged(user => {
    if (!user) window.location.href = redirectTo;
  });
}

function getUserRole(uid) {
  return firebase.database().ref(`users/${uid}/role`).once('value').then(s => s.val());
}

function requireRole(allowedRoles, redirectTo = '/pages/login.html') {
  firebase.auth().onAuthStateChanged(user => {
    if (!user) return window.location.href = redirectTo;
    getUserRole(user.uid).then(role => {
      if (!allowedRoles.includes(role)) window.location.href = redirectTo;
    });
  });
}

// ── Splash Screen ──
function showSplash() {
  // Only on dashboard pages (has sidebar)
  if (!document.querySelector('.sidebar')) return;
  var splash = document.createElement('div');
  splash.className = 'splash-overlay';
  splash.innerHTML =
    '<div class="splash-text">EduPulse</div>' +
    '<div class="splash-sub">ATTENDANCE SYSTEM</div>' +
    '<div class="splash-loader"></div>';
  document.body.appendChild(splash);
  // Fade out after content is ready
  setTimeout(function() {
    splash.classList.add('fade-out');
    setTimeout(function() { splash.remove(); }, 700);
  }, 1500);
}

// ── INIT ──
document.addEventListener('DOMContentLoaded', function() {
  showSplash();
  initClock();
  initScrollProgress();
  initGlowBlob();
  initFloatingShapes();
  setActiveNav();
});
