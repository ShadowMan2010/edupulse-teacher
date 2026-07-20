// ── Google Fonts (loaded async so CSS is not blocked) ──
(function() {
  var link = document.createElement('link');
  link.rel = 'stylesheet';
  link.href = 'https://fonts.googleapis.com/css2?family=Orbitron:wght@400;500;600;700;800;900&family=Inter:wght@300;400;500;600;700;800&family=JetBrains+Mono:wght@400;500;700&display=swap';
  document.head.appendChild(link);
})();

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
// API_BASE is defined in firebase-config.js

async function apiGet(path) {
  const res = await fetch(`${window.API_BASE}${path}`);
  return res.json();
}

async function apiPost(path, body) {
  const res = await fetch(`${window.API_BASE}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  return res.json();
}

async function apiPut(path, body) {
  const res = await fetch(`${window.API_BASE}${path}`, {
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
// Content is hidden by an inline <script> in each page's <head>.
// This function reveals it after successful auth verification.

function showAuthOverlay() {
  var o = document.getElementById('authOverlay');
  if (o) return o;
  o = document.createElement('div');
  o.id = 'authOverlay';
  o.style.cssText = 'position:fixed;inset:0;display:flex;align-items:center;justify-content:center;flex-direction:column;background:#0a0a0b;z-index:99999;color:#f8fafc';
  o.innerHTML = '<div style="font-size:1.5rem;font-weight:700;font-family:Orbitron,monospace;letter-spacing:0.1em">EDUPULSE</div><div style="margin-top:1rem;width:32px;height:32px;border:2px solid rgba(255,255,255,0.1);border-top-color:#00E5FF;border-radius:50%;animation:spin 0.8s linear infinite"></div><style>@keyframes spin{to{transform:rotate(360deg)}}</style>';
  document.body.appendChild(o);
  return o;
}
function hideAuthOverlay() {
  var o = document.getElementById('authOverlay');
  if (o) o.remove();
}

function requireAuth(redirectTo) {
  redirectTo = redirectTo || '/pages/login.html';
  // Unhide page so overlay is visible (inline script set visibility=hidden as FOUC guard)
  document.documentElement.style.visibility = 'visible';
  showAuthOverlay();

  var demo = sessionStorage.getItem('edupulse_demo');
  if (demo) { hideAuthOverlay(); return; }

  // Fast path: check currentUser synchronously
  try {
    if (firebase.auth().currentUser) {
      hideAuthOverlay();
      return;
    }
  } catch(e) {
    window.location.href = redirectTo;
    return;
  }
  // Wait for onAuthStateChanged with timeout
  var timer = setTimeout(function() {
    window.location.href = redirectTo;
  }, 4000);
  try {
    firebase.auth().onAuthStateChanged(function(user) {
      clearTimeout(timer);
      if (user) {
        hideAuthOverlay();
      } else {
        window.location.href = redirectTo;
      }
    });
  } catch(e) {
    clearTimeout(timer);
    window.location.href = redirectTo;
  }
}

function getUserRole(uid) {
  return firebase.database().ref(`users/${uid}/role`).once('value').then(s => s.val());
}

function requireRole(allowedRoles, redirectTo) {
  redirectTo = redirectTo || '/pages/login.html';
  document.documentElement.style.visibility = 'visible';
  showAuthOverlay();

  function checkAndShow(user) {
    if (!user) { window.location.href = redirectTo; return; }
    getUserRole(user.uid).then(function(role) {
      if (!allowedRoles.includes(role)) window.location.href = redirectTo;
      else hideAuthOverlay();
    });
  }
  try {
    if (firebase.auth().currentUser) {
      checkAndShow(firebase.auth().currentUser);
      return;
    }
  } catch(e) {
    window.location.href = redirectTo;
    return;
  }
  var timer = setTimeout(function() { window.location.href = redirectTo; }, 4000);
  try {
    firebase.auth().onAuthStateChanged(function(user) {
      clearTimeout(timer);
      checkAndShow(user);
    });
  } catch(e) {
    clearTimeout(timer);
    window.location.href = redirectTo;
  }
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
