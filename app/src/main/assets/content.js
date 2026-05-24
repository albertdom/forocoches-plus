(function () {
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
      if (spans[i].textContent.trim() === '@') {
        return spans[i + 1].textContent.trim();
      }
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

  function filterThreads(rows, ignoredSet, keywordsEnabled, keywords) {
    for (const row of rows) {
      const author = getAuthorFromRow(row);
      if (author && ignoredSet.has(author)) { row.style.display = 'none'; continue; }
      if (keywordsEnabled && keywords.length > 0) {
        const title = getTitleFromRow(row);
        if (keywords.find(k => title.includes(k))) row.style.display = 'none';
      }
    }
  }

  const ignoredSet = new Set(window._fcIgnoredUsers || []);
  const keywordsEnabled = window._fcKeywordsEnabled !== false;
  const keywords = (window._fcKeywords || []).map(k => k.toLowerCase());

  filterThreads(getThreadRows(), ignoredSet, keywordsEnabled, keywords);

  const observer = new MutationObserver(function (mutations) {
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
    if (newRows.length > 0) filterThreads(newRows, ignoredSet, keywordsEnabled, keywords);
  });

  observer.observe(document.body, { childList: true, subtree: true });
})();
