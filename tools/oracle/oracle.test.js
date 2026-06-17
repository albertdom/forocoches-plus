// Oraculo / golden de regresion para el filtrado de content.js.
//
// Carga cada fixture congelado en jsdom, inyecta el mismo puente Android que usa la app
// (con inputs fijos de test), EJECUTA EL content.js REAL del repo, y comprueba que oculta
// exactamente lo esperado. Si alguien rompe el filtrado, este test falla en el mismo PR.
//
// No comprueba "drift" de FC (eso es el canario en el movil); aqui el HTML esta congelado.

const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const { JSDOM } = require('jsdom');

const ROOT = path.resolve(__dirname, '..', '..');
const CONTENT_JS = fs.readFileSync(path.join(ROOT, 'app', 'src', 'main', 'assets', 'content.js'), 'utf8');
const FX = path.join(__dirname, 'fixtures');
const GOLDEN = JSON.parse(fs.readFileSync(path.join(__dirname, 'golden.json'), 'utf8'));

const { ignored, keywords, keywordFilterEnabled } = GOLDEN.inputs;
const CREATORS = GOLDEN.creators;

// --- Ejecuta el content.js real sobre un fixture, devuelve el window resultante ---
function runContentJs(file, url) {
  const html = fs.readFileSync(path.join(FX, file), 'utf8');
  const dom = new JSDOM(html, { url, runScripts: 'outside-only' });
  const win = dom.window;
  win.Android = {
    getIgnoredUsersJson: () => JSON.stringify(ignored),
    getKeywordFilterEnabled: () => keywordFilterEnabled,
    getKeywordsJson: () => JSON.stringify(keywords),
    getCachedCreator: (tid) => CREATORS[tid] || '',
    requestThreadCreator: () => {},
    reportCanary: () => {},
  };
  win.eval(CONTENT_JS); // filterPosts/filterThreads corren sincronamente al cargar
  return win;
}

const isHidden = (el) => el && el.style && el.style.display === 'none';
const isOldSkin = (doc) => !doc.querySelector('.menu-item, .user-notifications-count-wrapper');

// --- Helpers de LISTADO (replican getThreadRows/rowForAnchor para LOCALIZAR filas) ---
const THREAD_LINK = 'a[href*="showthread.php?t="]';
function threadIdOf(a) { const m = (a.getAttribute('href') || '').match(/[?&]t=(\d+)/); return m ? m[1] : null; }
function threadIdsIn(el) {
  const ids = new Set();
  for (const a of el.querySelectorAll(THREAD_LINK)) { const id = threadIdOf(a); if (id) ids.add(id); }
  return ids;
}
function rowForAnchor(a, tid) {
  let el = a;
  while (el.parentElement && el.parentElement.tagName !== 'BODY') {
    const ids = threadIdsIn(el.parentElement);
    if (ids.size > 1 || (ids.size === 1 && !ids.has(tid))) break;
    el = el.parentElement;
  }
  return el;
}
function rowForThreadId(doc, tid) {
  for (const a of doc.querySelectorAll(THREAD_LINK)) {
    if (threadIdOf(a) === tid) return rowForAnchor(a, tid);
  }
  return null;
}

// --- Helpers de HILO: posts con su contenedor y autor ---
function postsOf(doc) {
  const old = isOldSkin(doc);
  const out = [];
  for (const bit of doc.querySelectorAll('li.postbit')) {
    const wrap = old ? (bit.closest('ul') || bit) : (bit.closest('div.postbit_wrapper') || bit);
    const a = old ? bit.querySelector('span.xsaid a[href*="member.php"]')
                  : bit.querySelector('[id^="postmenu_"] a[href*="member.php"]');
    out.push({ wrap, author: a ? a.textContent.trim() : null });
  }
  return out;
}

// ====================== TESTS DE HILO ======================
for (const [file, kind] of Object.entries(GOLDEN.fixtures)) {
  if (kind !== 'thread') continue;
  test(`HILO ${file}: filtra posts de ignorados y respeta los demas`, () => {
    const win = runContentJs(file, GOLDEN.thread.url);
    const doc = win.document;
    const posts = postsOf(doc);
    assert.ok(posts.length > 0, 'no se detecto ningun post (¿selector roto?)');

    const ign = new Set(ignored.map(u => u.toLowerCase()));

    // 1) Ningun post VISIBLE puede ser de un usuario ignorado.
    for (const p of posts) {
      if (!isHidden(p.wrap) && p.author && ign.has(p.author.toLowerCase())) {
        assert.fail(`post de ignorado "${p.author}" sigue visible`);
      }
    }
    // 2) El autor ignorado de prueba tiene posts y TODOS estan ocultos.
    const target = GOLDEN.thread.ignoredAuthorWithPosts.toLowerCase();
    const targetPosts = posts.filter(p => p.author && p.author.toLowerCase() === target);
    assert.ok(targetPosts.length > 0, `no hay posts de ${GOLDEN.thread.ignoredAuthorWithPosts} en el fixture`);
    for (const p of targetPosts) assert.ok(isHidden(p.wrap), `un post de ${GOLDEN.thread.ignoredAuthorWithPosts} sigue visible`);

    // 3) FAIL-SAFE: no se ha blanqueado el hilo (queda algun post visible).
    assert.ok(posts.some(p => !isHidden(p.wrap)), 'se ocultaron TODOS los posts (blanqueo)');

    // 4) Negativo: un autor normal sigue visible.
    const keep = GOLDEN.thread.mustStayVisibleAuthor.toLowerCase();
    const keepPosts = posts.filter(p => p.author && p.author.toLowerCase() === keep);
    assert.ok(keepPosts.length > 0 && keepPosts.some(p => !isHidden(p.wrap)),
      `el autor normal ${GOLDEN.thread.mustStayVisibleAuthor} deberia seguir visible`);

    // 5) Solo skin nuevo: el post que CITA a un ignorado se oculta.
    if (!isOldSkin(doc)) {
      const cited = GOLDEN.thread.quotedIgnoredUserNewSkinOnly.toLowerCase();
      let found = false;
      for (const q of doc.querySelectorAll('div.quote')) {
        const b = q.querySelector('b');
        if (b && b.textContent.trim().toLowerCase() === cited) {
          found = true;
          const wrap = q.closest('div.postbit_wrapper') || q.closest('li.postbit');
          assert.ok(isHidden(wrap), `el post que cita a ${GOLDEN.thread.quotedIgnoredUserNewSkinOnly} sigue visible`);
        }
      }
      assert.ok(found, `no se encontro cita a ${GOLDEN.thread.quotedIgnoredUserNewSkinOnly} (¿cambio el fixture?)`);
    }
  });
}

// ====================== TESTS DE LISTADO ======================
for (const [file, kind] of Object.entries(GOLDEN.fixtures)) {
  if (kind !== 'listing') continue;
  test(`LISTADO ${file}: oculta hilos de ignorados + keyword, respeta el resto`, () => {
    const win = runContentJs(file, GOLDEN.listing.url);
    const doc = win.document;

    // 1) FAIL-SAFE: el listado no se ha blanqueado (hay filas visibles).
    let anyVisible = false;
    for (const tid of GOLDEN.listing.mustStayVisibleThreadIds) {
      const row = rowForThreadId(doc, tid);
      assert.ok(row, `no se encontro la fila del hilo ${tid} (¿selector roto?)`);
      if (!isHidden(row)) anyVisible = true;
    }
    assert.ok(anyVisible, 'se ocultaron filas que deberian seguir visibles (blanqueo)');

    // 2) Los hilos esperados (creador ignorado + keyword) estan ocultos.
    for (const tid of GOLDEN.listing.expectHiddenThreadIds) {
      const row = rowForThreadId(doc, tid);
      assert.ok(row, `no se encontro la fila del hilo ${tid}`);
      assert.ok(isHidden(row), `el hilo ${tid} deberia estar oculto y sigue visible`);
    }

    // 3) Negativos: los hilos normales siguen visibles.
    for (const tid of GOLDEN.listing.mustStayVisibleThreadIds) {
      const row = rowForThreadId(doc, tid);
      assert.ok(!isHidden(row), `el hilo normal ${tid} no deberia estar oculto`);
    }
  });
}
