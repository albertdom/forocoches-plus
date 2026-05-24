(function () {
  if (typeof Android === 'undefined') return;
  if (document.getElementById('fc-settings-overlay')) return;

  var hideMode = Android.getHideMode();
  var ignoredUsers = JSON.parse(Android.getIgnoredUsersJson());
  var lastUpdated = Android.getLastUpdatedMs();

  function timeAgo(ms) {
    if (!ms) return 'nunca';
    var diff = Date.now() - ms;
    var minutes = Math.floor(diff / 60000);
    var hours = Math.floor(diff / 3600000);
    if (minutes < 1) return 'hace menos de 1 minuto';
    if (minutes === 1) return 'hace 1 minuto';
    if (hours < 1) return 'hace ' + minutes + ' minutos';
    if (hours === 1) return 'hace 1 hora';
    return 'hace ' + hours + ' horas';
  }

  function escAttr(s) {
    return s.replace(/&/g,'&amp;').replace(/"/g,'&quot;').replace(/</g,'&lt;');
  }

  function escJs(s) {
    return s.replace(/\\/g,'\\\\').replace(/'/g,"\\'");
  }

  function buildUserRows(users) {
    if (users.length === 0)
      return '<div style="color:#666;font-size:12px;padding:4px 0;">No hay usuarios ignorados</div>';
    return users.map(function(u) {
      return '<div style="display:flex;justify-content:space-between;align-items:center;' +
             'padding:6px 0;border-bottom:1px solid #2a2a2a;" data-user="' + escAttr(u) + '">' +
             '<span style="color:#ccc;">@' + escAttr(u) + '</span>' +
             '<button onclick="fcRemoveUser(\'' + escJs(u) + '\')" style="' +
             'background:none;border:none;color:#ff5555;cursor:pointer;font-size:13px;padding:2px 8px;">✕</button>' +
             '</div>';
    }).join('');
  }

  function buildFavoriteRows(favs) {
    if (favs.length === 0)
      return '<div style="color:#666;font-size:12px;padding:4px 0;">No hay usuarios favoritos</div>';
    return favs.map(function(f) {
      return '<div style="display:flex;justify-content:space-between;align-items:center;' +
             'padding:6px 0;border-bottom:1px solid #2a2a2a;">' +
             '<span style="color:#ccc;">@' + escAttr(f.username) + '</span>' +
             '<button onclick="fcRemoveFavorite(\'' + escJs(f.username) + '\')" style="' +
             'background:none;border:none;color:#ff5555;cursor:pointer;font-size:13px;padding:2px 8px;">✕</button>' +
             '</div>';
    }).join('');
  }

  function getCurrentProfileInfo() {
    var params = new URLSearchParams(window.location.search);
    var userId = params.get('u');
    if (!userId || !window.location.href.includes('member.php')) return null;
    var nameEl = document.querySelector('.member_username, .profileusername, h1, h2');
    var username = nameEl ? nameEl.textContent.trim() : null;
    if (!username || username.length > 40) return null;
    return { userId: userId, username: username };
  }

  var isChecked = hideMode === 'complete';
  var sliderBg = isChecked ? '#00e5cc' : '#555';
  var thumbLeft = isChecked ? '21px' : '3px';

  // Overlay backdrop
  var overlay = document.createElement('div');
  overlay.id = 'fc-settings-overlay';
  overlay.style.cssText = 'display:none;position:fixed;inset:0;z-index:9998;background:rgba(0,0,0,0.5);';
  overlay.addEventListener('click', function(e) {
    if (e.target === overlay) closePanel();
  });

  // Settings panel (drops from header)
  var panel = document.createElement('div');
  panel.id = 'fc-settings-panel';
  panel.style.cssText = 'display:none;position:fixed;top:var(--header-height,56px);left:0;right:0;' +
    'z-index:9999;background:#1a1a1a;border-bottom:2px solid #333;padding:16px;' +
    'font-family:sans-serif;font-size:14px;color:#e0e0e0;max-height:80vh;overflow-y:auto;';

  panel.innerHTML =
    '<div style="font-weight:bold;font-size:15px;margin-bottom:14px;color:#00e5cc;">⚙ FC+ Configuración</div>' +

    '<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:14px;">' +
    '<span>Ocultar hilos completamente</span>' +
    '<label style="position:relative;width:40px;height:22px;flex-shrink:0;cursor:pointer;">' +
    '<input type="checkbox" id="fc-hide-toggle" ' + (isChecked ? 'checked' : '') +
    ' style="opacity:0;width:0;height:0;position:absolute;">' +
    '<span id="fc-toggle-track" style="position:absolute;inset:0;background:' + sliderBg +
    ';border-radius:22px;transition:background 0.2s;">' +
    '<span id="fc-toggle-thumb" style="position:absolute;width:16px;height:16px;left:' + thumbLeft +
    ';bottom:3px;background:#fff;border-radius:50%;transition:left 0.2s;"></span>' +
    '</span></label>' +
    '</div>' +

    '<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">' +
    '<span style="color:#888;font-size:12px;" id="fc-meta">Actualizado: ' + timeAgo(lastUpdated) + ' · ' + ignoredUsers.length + ' ignorados</span>' +
    '<button id="fc-refresh-btn" onclick="fcRefresh()" style="background:none;border:1px solid #00e5cc;color:#00e5cc;border-radius:4px;padding:2px 8px;font-size:11px;cursor:pointer;">↻ Actualizar</button>' +
    '</div>' +

    '<div id="fc-ignored-list" style="max-height:220px;overflow-y:auto;">' +
    buildUserRows(ignoredUsers) +
    '</div>';

  var favUsers = JSON.parse(Android.getFavoriteUsersJson());
  var profileInfo = getCurrentProfileInfo();
  var addFavBtn = profileInfo
    ? '<button id="fc-add-fav-btn" onclick="fcAddCurrentFavorite()" style="' +
      'width:100%;margin-top:8px;background:none;border:1px solid #00e5cc;color:#00e5cc;' +
      'border-radius:4px;padding:4px;font-size:12px;cursor:pointer;">' +
      '★ Añadir @' + escAttr(profileInfo.username) + ' a favoritos</button>'
    : '';

  panel.innerHTML +=
    '<div style="border-top:1px solid #333;margin-top:12px;padding-top:12px;">' +
    '<div style="font-size:12px;color:#888;margin-bottom:8px;">Usuarios favoritos <span style="font-size:10px;">(avisa de nuevos hilos)</span></div>' +
    '<div id="fc-fav-list">' + buildFavoriteRows(favUsers) + '</div>' +
    addFavBtn +
    '</div>';

  document.body.appendChild(overlay);
  document.body.appendChild(panel);

  function openPanel() {
    overlay.style.display = 'block';
    panel.style.display = 'block';
  }

  function closePanel() {
    overlay.style.display = 'none';
    panel.style.display = 'none';
  }

  window.fcToggleSettings = function() {
    if (panel.style.display === 'none') openPanel(); else closePanel();
  };

  window.fcRemoveUser = function(username) {
    Android.removeIgnoredUser(username);
    var el = panel.querySelector('[data-user="' + escAttr(username) + '"]');
    if (el) el.remove();
    var remaining = panel.querySelectorAll('[data-user]').length;
    if (remaining === 0) {
      document.getElementById('fc-ignored-list').innerHTML =
        '<div style="color:#666;font-size:12px;padding:4px 0;">No hay usuarios ignorados</div>';
    }
    var meta = document.getElementById('fc-meta');
    if (meta) meta.textContent = 'Actualizado: ' + timeAgo(Android.getLastUpdatedMs()) + ' · ' + remaining + ' ignorados';
  };

  window.fcRefresh = function() {
    var btn = document.getElementById('fc-refresh-btn');
    if (btn) { btn.textContent = '↻ ...'; btn.disabled = true; }
    window.fcOnRefreshDone = function() {
      window.fcOnRefreshDone = null;
      var users = JSON.parse(Android.getIgnoredUsersJson());
      var list = document.getElementById('fc-ignored-list');
      if (list) list.innerHTML = buildUserRows(users);
      var meta = document.getElementById('fc-meta');
      if (meta) meta.textContent = 'Actualizado: ' + timeAgo(Android.getLastUpdatedMs()) + ' · ' + users.length + ' ignorados';
      if (btn) { btn.textContent = '↻ Actualizar'; btn.disabled = false; }
    };
    Android.triggerRefresh();
  };

  panel.querySelector('#fc-hide-toggle').addEventListener('change', function() {
    var mode = this.checked ? 'complete' : 'message';
    Android.setHideMode(mode);
    panel.querySelector('#fc-toggle-track').style.background = this.checked ? '#00e5cc' : '#555';
    panel.querySelector('#fc-toggle-thumb').style.left = this.checked ? '21px' : '3px';
  });

  window.fcRemoveFavorite = function(username) {
    Android.removeFavoriteUser(username);
    var favs = JSON.parse(Android.getFavoriteUsersJson());
    var list = document.getElementById('fc-fav-list');
    if (list) list.innerHTML = buildFavoriteRows(favs);
  };

  window.fcAddCurrentFavorite = function() {
    var info = getCurrentProfileInfo();
    if (!info) return;
    Android.addFavoriteUser(info.username, info.userId);
    var favs = JSON.parse(Android.getFavoriteUsersJson());
    var list = document.getElementById('fc-fav-list');
    if (list) list.innerHTML = buildFavoriteRows(favs);
    var btn = document.getElementById('fc-add-fav-btn');
    if (btn) { btn.textContent = '✓ Añadido'; btn.disabled = true; }
  };

  // Fixed floating button (FAB) — always bottom-right, independent of page structure
  var fab = document.createElement('button');
  fab.id = 'fc-gear-btn';
  fab.onclick = window.fcToggleSettings;
  fab.style.cssText = 'position:fixed;bottom:24px;right:16px;z-index:9997;' +
    'width:48px;height:48px;border-radius:50%;background:#00e5cc;border:none;' +
    'color:#111;font-size:22px;cursor:pointer;box-shadow:0 2px 8px rgba(0,0,0,0.5);' +
    'display:flex;align-items:center;justify-content:center;';
  fab.textContent = '⚙';
  document.body.appendChild(fab);
})();
