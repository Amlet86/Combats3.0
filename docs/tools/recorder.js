(() => {
  if (window.__combatsRecorder) return 'recorder already running';
  window.__combatsRecorder = true;
  const KEY = '__combatsSnapshots';
  const load = () => { try { return JSON.parse(localStorage.getItem(KEY) || '{}'); } catch (e) { return {}; } };
  const store = (key, value) => {
    const all = load();
    const prev = all[key];
    value.seen = (prev ? prev.seen : 0) + 1;
    value.firstSeen = prev ? prev.firstSeen : value.time;
    all[key] = value;
    try { localStorage.setItem(KEY, JSON.stringify(all)); } catch (e) { console.warn('snapshot storage full', e); }
  };
  const shown = e => { try { const s = e.ownerDocument.defaultView.getComputedStyle(e); return s.visibility !== 'hidden' && s.display !== 'none' && !!(e.offsetWidth || e.offsetHeight); } catch (x) { return false; } };
  const clean = s => (s || '').replace(/\s+/g, ' ').trim();
  const describe = doc => {
    const sel = 'a,button,input,select,textarea,[onclick],img[title],img[alt],[id],form,iframe,frame';
    const out = [];
    doc.querySelectorAll(sel).forEach(e => {
      if (out.length >= 500 || e.tagName === 'SCRIPT') return;
      let d = e.tagName.toLowerCase();
      if (e.id) d += '#' + e.id;
      if (typeof e.className === 'string' && e.className.trim()) d += '.' + e.className.trim().split(/\s+/).join('.');
      ['name', 'type', 'value', 'action', 'title', 'alt', 'href', 'src', 'onclick', 'onload'].forEach(a => {
        let v = e.getAttribute(a);
        if (v == null || v === '') return;
        v = clean(v).replace(/\d\.\d{6,}/g, '<rnd>').slice(0, 120);
        d += ` [${a}=${v}]`;
      });
      if (['A', 'BUTTON', 'SPAN', 'TD', 'DIV', 'LABEL', 'OPTION', 'B', 'FONT', 'LEGEND'].includes(e.tagName)) {
        const t = clean(e.textContent).slice(0, 60);
        if (t && e.children.length < 4) d += ` "${t}"`;
      }
      if (!shown(e) && !['FORM', 'IFRAME', 'FRAME'].includes(e.tagName)) d += ' (hidden)';
      out.push(d);
    });
    return out;
  };
  const pageKey = url => {
    try {
      const u = new URL(url);
      const params = [...u.searchParams].filter(([k, v]) => !/^\d*\.\d+$/.test(k) && !/^\d*\.\d+$/.test(v) && !/^\d{9,}$/.test(v)).map(([k, v]) => k + (v ? '=' + v : ''));
      return u.pathname + (params.length ? '?' + params.join('&') : '');
    } catch (e) { return url; }
  };
  const snapDoc = (kind, doc) => {
    if (!doc || !doc.body) return;
    store(kind + ' ' + pageKey(doc.URL), {
      time: new Date().toISOString(), url: doc.URL.replace(/\d\.\d{6,}/g, '<rnd>'), title: doc.title,
      text: clean(doc.body.innerText).slice(0, 2000), elements: describe(doc)
    });
  };
  const mainFrame = () => [...document.querySelectorAll('iframe')].find(f => f.getAttribute('onload'));
  let lastMain = '', lastBattle = '';
  const tick = () => {
    try {
      const f = mainFrame();
      const d = f && f.contentDocument;
      if (d && d.readyState === 'complete' && d.URL !== lastMain) { lastMain = d.URL; setTimeout(() => snapDoc('frame', f.contentDocument), 700); }
      const btns = [...document.querySelectorAll('button[action]')].filter(shown).map(b => b.getAttribute('action')).sort().join(',');
      const err = clean([...document.querySelectorAll('td.UserBattleError')].map(e => e.innerText).join(' ')).slice(0, 40);
      const state = document.querySelector('[action=commit]') ? 'buttons=' + (btns || 'none') : '';
      if (state && state !== lastBattle) {
        lastBattle = state;
        const root = document.querySelector('table.UserBattle') || document.body;
        store('battle ' + state, {
          time: new Date().toISOString(), url: 'top document', title: document.title, message: err,
          text: clean(root.innerText).slice(0, 1500), elements: describe(document).filter(l => /UserBattle|action=|Radio|Method|#HP/.test(l))
        });
      }
    } catch (e) { console.warn('recorder', e); }
  };
  window.__combatsRecorderTimer = setInterval(tick, 1000);
  tick();
  return 'recorder started';
})()
