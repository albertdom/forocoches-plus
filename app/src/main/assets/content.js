(function () {
  if (typeof Android === 'undefined') { return; }

  const ignoredSet = new Set(JSON.parse(Android.getIgnoredUsersJson()).map(u => u.toLowerCase()));
  const keywordsEnabled = Android.getKeywordFilterEnabled();
  const keywords = JSON.parse(Android.getKeywordsJson()).map(k => k.toLowerCase());

  function isThreadPage() { return window.location.href.includes('showthread.php'); }


  // ── Thread list filtering ────────────────────────────────────────────────

  function getThreadRows() {
    const seen = new Set();
    const rows = [];
    for (const a of document.querySelectorAll('a[href*="showthread.php?t="]')) {
      let el = a;
      for (let i = 0; i < 4; i++) if (el.parentElement) el = el.parentElement;
      if (!seen.has(el)) { seen.add(el); rows.push(el); }
    }
    return rows;
  }

  function getAuthorFromRow(row) {
    const spans = Array.from(row.querySelectorAll('span'));
    for (let i = 0; i < spans.length - 1; i++) {
      if (spans[i].textContent.trim() === '@') return spans[i + 1].textContent.trim();
    }
    for (const el of row.querySelectorAll('span, a')) {
      const text = el.textContent.trim();
      if (!text.startsWith('@')) continue;
      const withoutAt = text.slice(1);
      const dashIdx = withoutAt.indexOf(' - ');
      return dashIdx !== -1 ? withoutAt.slice(0, dashIdx).trim() : withoutAt.trim();
    }
    return null;
  }

  function getTitleFromRow(row) {
    const a = row.querySelector('a[href*="showthread.php?t="]');
    return a ? a.textContent.trim().toLowerCase() : '';
  }

  function filterThreads(rows) {
    for (const row of rows) {
      const author = getAuthorFromRow(row);
      if (author && ignoredSet.has(author)) { row.style.display = 'none'; continue; }
      if (keywordsEnabled && keywords.length > 0) {
        const title = getTitleFromRow(row);
        if (keywords.find(k => title.includes(k))) row.style.display = 'none';
      }
    }
  }

  // ── Post filtering (dentro de hilos) ────────────────────────────────────

  function hidePost(post) {
    var el = post.parentElement || post;
    el.style.display = 'none';
  }

  function filterPosts(root) {
    const posts = (root || document).querySelectorAll('li[id^="td_post_"]');
    for (const post of posts) {
      if (post.textContent.includes('lista de ignorados')) {
        hidePost(post);
        continue;
      }
      const postId = post.id.replace('td_post_', '');
      const menu = document.getElementById('postmenu_' + postId);
      if (!menu) continue;
      const authorEl = menu.querySelector('a[href*="member.php"]');
      if (!authorEl) continue;
      const author = authorEl.textContent.trim().toLowerCase();
      if (ignoredSet.has(author)) hidePost(post);
    }

    const quotes = (root || document).querySelectorAll('div.quote');
    for (const quote of quotes) {
      const b = quote.querySelector('b');
      if (!b) continue;
      if (!ignoredSet.has(b.textContent.trim().toLowerCase())) continue;
      const msgDiv = quote.closest('[id^="post_message_"]');
      if (!msgDiv) continue;
      const wrapper = msgDiv.parentElement?.parentElement;
      if (wrapper) wrapper.style.display = 'none';
    }
  }

  // ── Inicialización ───────────────────────────────────────────────────────

  if (isThreadPage()) {
    filterPosts();
  } else {
    filterThreads(getThreadRows());
  }

  // Detectar navegación SPA (pushState) para re-filtrar al entrar en un hilo
  var lastUrl = location.href;
  var origPushState = history.pushState.bind(history);
  history.pushState = function() {
    origPushState.apply(history, arguments);
    var newUrl = location.href;
    if (newUrl !== lastUrl) {
      lastUrl = newUrl;
      if (isThreadPage()) setTimeout(filterPosts, 400);
    }
  };
  window.addEventListener('popstate', function() {
    if (isThreadPage()) setTimeout(filterPosts, 400);
  });

  const observer = new MutationObserver(function (mutations) {
    if (isThreadPage()) {
      for (const mutation of mutations) {
        for (const node of mutation.addedNodes) {
          if (node.nodeType !== 1) continue;
          if (node.querySelector?.('div[id^="postmenu_"]')) filterPosts(node);
        }
      }
    } else {
      const newRows = [];
      const seen = new Set();
      for (const mutation of mutations) {
        for (const node of mutation.addedNodes) {
          if (node.nodeType !== 1) continue;
          for (const a of node.querySelectorAll('a[href*="showthread.php?t="]')) {
            let el = a;
            for (let i = 0; i < 4; i++) if (el.parentElement) el = el.parentElement;
            if (!seen.has(el)) { seen.add(el); newRows.push(el); }
          }
        }
      }
      if (newRows.length > 0) filterThreads(newRows);
    }
  });

  observer.observe(document.body, { childList: true, subtree: true });
})();
