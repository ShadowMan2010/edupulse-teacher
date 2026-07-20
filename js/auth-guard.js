(function() {
  function showUnauthorized() {
    var div = document.createElement('div');
    div.id = 'authGuardOverlay';
    div.style.cssText = 'position:fixed;inset:0;z-index:99999;background:#0a0a0b;display:flex;flex-direction:column;align-items:center;justify-content:center;font-family:monospace;color:#f8fafc';
    div.innerHTML = '<img src="../../assets/icons8-error.gif" style="width:120px;height:120px;margin-bottom:24px;image-rendering:pixelated">' +
      '<div style="font-size:18px;letter-spacing:2px;text-transform:uppercase;margin-bottom:8px;color:#FF3B3B">Access Denied</div>' +
      '<div style="font-size:11px;color:rgba(255,255,255,0.4);margin-bottom:20px">You must be logged in to view this page</div>' +
      '<a href="/pages/login.html" style="padding:10px 24px;border:1px solid rgba(0,229,255,0.3);border-radius:8px;color:#00E5FF;text-decoration:none;font-size:11px;letter-spacing:1px;text-transform:uppercase">Go to Login</a>';
    document.body.appendChild(div);
    setTimeout(function() { window.location.href = '/pages/login.html'; }, 4000);
  }

  var _demoUser = sessionStorage.getItem('edupulse_demo');
  if (!_demoUser) {
    var _check = setInterval(function() {
      if (typeof auth !== 'undefined' && auth) {
        clearInterval(_check);
        var u = auth.currentUser;
        if (!u) {
          // Wait briefly for onAuthStateChanged to fire
          var timedOut = setTimeout(showUnauthorized, 2000);
          auth.onAuthStateChanged(function(user) {
            clearTimeout(timedOut);
            if (!user) showUnauthorized();
          });
        }
      }
    }, 100);
    setTimeout(function() {
      clearInterval(_check);
      if (typeof auth === 'undefined' || !auth.currentUser) showUnauthorized();
    }, 5000);
  }
})();
