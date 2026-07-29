// El Nour Power — frontend vanilla (multi-utilisateurs, JWT)
const $ = (id) => document.getElementById(id);
const esc = (s) => String(s ?? '').replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
const fmt = (n) => Math.round(n).toLocaleString('fr-FR');
const fmt1 = (n) => (Math.round(n*10)/10).toLocaleString('fr-FR');
const fmt2 = (n) => (Math.round(n*100)/100).toLocaleString('fr-FR');

const TOKEN_KEY = 'elnp_token';
const USER_KEY = 'elnp_user';

// Icônes et labels par catégorie (partagés entre catalogue et marketplace)
// Icônes SVG par catégorie (vectorielles, sobres)
const CAT_ICONS_IMP = {
  COOLING: '#ic-snow', HEATING: '#ic-flame', LAUNDRY: '#ic-wash', COLD: '#ic-snow',
  COOKING: '#ic-flame', LIGHTING: '#ic-lightbulb', ELECTRONICS: '#ic-monitor',
  WATER: '#ic-drop', OTHER: '#ic-box'
};
const CAT_LABELS_IMP = { COOLING:'Climatisation', HEATING:'Chauffage', LAUNDRY:'Lavage', COLD:'Froid', COOKING:'Cuisine', LIGHTING:'Éclairage', ELECTRONICS:'Électronique', WATER:'Eau', OTHER:'Autre' };
function catIcon(cat) {
  const id = CAT_ICONS_IMP[cat] || '#ic-box';
  return `<svg class="ic"><use href="${id}"/></svg>`;
}

// fetch avec JWT + gestion expiration
async function api(path, opts = {}) {
  const token = localStorage.getItem(TOKEN_KEY);
  const headers = { ...(opts.headers || {}) };
  if (token) headers['Authorization'] = 'Bearer ' + token;
  if (opts.body && !headers['Content-Type']) headers['Content-Type'] = 'application/json';
  const res = await fetch(path, { ...opts, headers });
  if (res.status === 401 || res.status === 403) {
    // token invalide/expiré → retour à l'auth
    logout();
    throw new Error('Session expirée');
  }
  if (!res.ok) {
    let msg = 'Erreur ' + res.status;
    try { const j = await res.json(); msg = j.error || j.message || msg; } catch {}
    throw new Error(msg);
  }
  return res.status === 204 ? null : res.json();
}

// ===================== AUTH =====================
function isLoggedIn() { return !!localStorage.getItem(TOKEN_KEY); }

function showApp() {
  $('auth-screen').classList.add('hidden');
  $('app').classList.remove('hidden');
  $('footer').classList.remove('hidden');
  const user = JSON.parse(localStorage.getItem(USER_KEY) || '{}');
  $('user-tag').textContent = user.email || '—';
  updateCart();
}

function showAuth() {
  $('app').classList.add('hidden');
  $('footer').classList.add('hidden');
  $('auth-screen').classList.remove('hidden');
}

function logout() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
  showAuth();
}
$('logout-btn').onclick = logout;

// bascule login/register
document.querySelectorAll('.auth-tab').forEach(t => {
  t.onclick = () => {
    document.querySelectorAll('.auth-tab').forEach(x => x.classList.remove('active'));
    t.classList.add('active');
    const mode = t.dataset.mode;
    $('auth-form').elements.mode.value = mode;
    document.querySelectorAll('.reg-only').forEach(el => el.classList.toggle('hidden', mode !== 'register'));
    $('auth-submit').textContent = mode === 'register' ? 'Créer mon compte' : 'Se connecter';
    $('auth-error').classList.add('hidden');
  };
});

$('auth-form').onsubmit = async (e) => {
  e.preventDefault();
  const fd = new FormData(e.target);
  const mode = fd.get('mode');
  const errEl = $('auth-error');
  errEl.classList.add('hidden');
  $('auth-submit').innerHTML = '<span class="loading"></span>';
  $('auth-submit').disabled = true;
  try {
    const body = { email: fd.get('email'), password: fd.get('password') };
    if (mode === 'register') body.nom = fd.get('nom');
    const res = await fetch('/api/auth/' + mode, {
      method: 'POST', headers: {'Content-Type':'application/json'}, body: JSON.stringify(body)
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || data.message || 'Échec');
    localStorage.setItem(TOKEN_KEY, data.token);
    localStorage.setItem(USER_KEY, JSON.stringify({ userId: data.userId, email: data.email, nom: data.nom }));
    e.target.reset();
    showApp();
  } catch (err) {
    errEl.textContent = err.message;
    errEl.classList.remove('hidden');
  } finally {
    $('auth-submit').textContent = $('auth-form').elements.mode.value === 'register' ? 'Créer mon compte' : 'Se connecter';
    $('auth-submit').disabled = false;
  }
};

// ===================== NAV ONGLETS + BOTTOM NAV + SIDEBAR DESKTOP =====================
function switchTab(tabName) {
  document.querySelectorAll('.tab').forEach(x => x.classList.remove('active'));
  document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));
  document.querySelectorAll('.bnav-item').forEach(x => x.classList.remove('active'));
  document.querySelectorAll('.sidebar-item[data-tab]').forEach(x => x.classList.remove('active'));

  const topTab = document.querySelector(`.tab[data-tab="${tabName}"]`);
  if (topTab) topTab.classList.add('active');
  const panel = $('panel-' + tabName);
  if (panel) panel.classList.add('active');
  const bnavItem = document.querySelector(`.bnav-item[data-tab="${tabName}"]`);
  if (bnavItem) bnavItem.classList.add('active');
  const snavItem = document.querySelector(`.sidebar-item[data-tab="${tabName}"]`);
  if (snavItem) snavItem.classList.add('active');
  if (tabName === 'crm') loadCrm();
}

document.querySelectorAll('.tab').forEach(t => { t.onclick = () => switchTab(t.dataset.tab); });
document.querySelectorAll('.bnav-item').forEach(b => { b.onclick = () => switchTab(b.dataset.tab); });
document.querySelectorAll('.sidebar-item[data-tab]').forEach(b => { b.onclick = () => switchTab(b.dataset.tab); });

// Afficher la sidebar sur desktop (CSS display:none par défaut pour mobile)
function initSidebar() {
  const sidebar = $('desktop-sidebar');
  if (!sidebar) return;
  const mq = window.matchMedia('(min-width: 1100px)');
  const toggle = () => { sidebar.style.display = mq.matches ? 'flex' : 'none'; };
  toggle();
  mq.addEventListener('change', toggle);
}
initSidebar();

// ===================== PANIER (état partagé) =====================
// cart : { id: { qty, override, name, category, image? } }
const cart = {};

const CAT_ICONS = {
  COOLING: '#ic-snow', HEATING: '#ic-flame', LAUNDRY: '#ic-wash', COLD: '#ic-snow',
  COOKING: '#ic-flame', LIGHTING: '#ic-lightbulb', ELECTRONICS: '#ic-monitor',
  WATER: '#ic-drop', OTHER: '#ic-box'
};
const CAT_LABELS = {
  COOLING: 'Climatisation', HEATING: 'Chauffage', LAUNDRY: 'Lavage', COLD: 'Froid',
  COOKING: 'Cuisine', LIGHTING: 'Éclairage', ELECTRONICS: 'Électronique', WATER: 'Eau', OTHER: 'Autre'
};

function updateCart() {
  const ids = Object.keys(cart);
  const totalQty = ids.reduce((s, id) => s + (cart[id].qty || 1), 0);
  if (totalQty === 0) {
    $('cart-info').innerHTML = 'Aucun appareil sélectionné';
    return;
  }
  let totalW = 0;
  ids.forEach(id => {
    const entry = cart[id];
    const ov = parseFloat(entry.override);
    const w = (isNaN(ov) || ov <= 0) ? (DEFAULT_POWER_BY_CAT[entry.category] || 800) : ov;
    totalW += w * (entry.qty || 1);
  });
  $('cart-info').innerHTML = `<b>${totalQty} appareil${totalQty>1?'s':''}</b> sélectionné${totalQty>1?'s':''} · <b>${fmt(totalW)} W</b> de puissance cumulée`;
}

// Puissance par défaut (W) quand non détectée dans le titre du produit marketplace
const DEFAULT_POWER_BY_CAT = {
  COOLING: 1200, COLD: 200, LAUNDRY: 1800, COOKING: 2000,
  LIGHTING: 60, ELECTRONICS: 150, WATER: 1500, HEATING: 1800, OTHER: 800
};


// ===================== RECHERCHE MARKETPLACE (AliExpress / Alibaba) =====================
let mpCart = {}; // { produitId: { product, qty } }

async function mpSearch(query, source) {
  $('mp-results').innerHTML = '';
  const loading = $('mp-loading');
  loading.classList.remove('hidden');
  // Indicateur de progression : décompte du temps écoulé
  const start = Date.now();
  const timer = setInterval(() => {
    const secs = Math.floor((Date.now() - start) / 1000);
    const cached = secs < 3 ? '(cache…)' : `(chargement live ${secs}s)`;
    loading.innerHTML = `<span class="loading"></span> Recherche de « ${esc(query)} » sur ${source}… ${cached}`;
  }, 500);
  try {
    const data = await api(`/api/products/search?q=${encodeURIComponent(query)}&source=${source}&limit=24`);
    renderMpResults(data.products, query);
  } catch (e) {
    $('mp-results').innerHTML = `<div class="mp-empty">Erreur : ${esc(e.message)}. Réessayez dans un instant.</div>`;
  } finally {
    clearInterval(timer);
    loading.classList.add('hidden');
    loading.innerHTML = '<span class="loading"></span> Recherche sur la marketplace…';
  }
}

function renderMpResults(products, query) {
  if (!products || products.length === 0) {
    $('mp-results').innerHTML = `<div class="mp-empty">Aucun produit trouvé pour « ${esc(query)} ». Essayez une autre recherche (ex : "air conditioner", "refrigerator").</div>`;
    return;
  }
  $('mp-results').innerHTML = products.map((p, i) => {
    const id = `mp_${i}_${(p.url||'').slice(-12)}`;
    const added = mpCart[id] != null;
    const img = p.image
      ? `<img class="mp-img" src="${esc(p.image)}" alt="" loading="lazy" onerror="this.outerHTML='<div class=\\'mp-img-placeholder\\'><svg class=\\'ic\\'><use href=\\'#ic-box\\'/></svg></div>'">`
      : `<div class="mp-img-placeholder">${catIcon(p.category)}</div>`;
    return `
      <div class="mp-card ${added ? 'added' : ''}" data-mpid="${id}">
        ${img}
        <div class="mp-body">
          <div class="mp-title">${esc(p.title)}</div>
          <div class="mp-meta">
            ${p.price ? `<span class="mp-price">${esc(p.price)}</span>` : '<span></span>'}
            <span class="mp-cat">${CAT_LABELS_IMP[p.category] || p.category}</span>
          </div>
          <div class="mp-source-badge">${esc(p.source || 'marketplace')}</div>
          ${p.detectedPowerWatts ? `<div class="mp-power"><b>${Math.round(p.detectedPowerWatts)} W</b> détecté</div>` : ''}
        </div>
        <button class="mp-add" data-add="${id}">${added ? '<svg class="ic"><use href="#ic-check"/></svg> Ajouté' : '<svg class="ic"><use href="#ic-plus"/></svg> Ajouter à mon installation'}</button>
      </div>`;
  }).join('');

  document.querySelectorAll('[data-add]').forEach(btn => {
    btn.onclick = () => {
      const id = btn.dataset.add;
      const p = products[parseInt(id.split('_')[1])];
      if (mpCart[id]) {
        // Retirer : supprimer du mpCart ET du cart de calcul
        delete mpCart[id];
        delete cart[id];
        btn.innerHTML = '<svg class="ic"><use href="#ic-plus"/></svg> Ajouter à mon installation';
        btn.closest('.mp-card').classList.remove('added');
      } else {
        mpCart[id] = { product: p, qty: 1 };
        // Puissance à utiliser : détectée sinon valeur par défaut selon la catégorie
        let power = p.detectedPowerWatts;
        if (!power) {
          power = DEFAULT_POWER_BY_CAT[p.category] || 800;
        }
        // Toujours ajouter au cart sous un ID unique (produit marketplace)
        cart[id] = { qty: 1, override: String(Math.round(power)), name: p.title, category: p.category, image: p.image };
        btn.innerHTML = '<svg class="ic"><use href="#ic-check"/></svg> Ajouté';
        btn.closest('.mp-card').classList.add('added');
        flashAdd(`Ajouté : ${p.title.slice(0,40)}…`);
      }
      updateCart();
    };
  });
}

$('mp-search-btn').onclick = () => {
  const q = $('mp-query').value.trim();
  if (!q) return;
  mpSearch(q, $('mp-source').value);
};
$('mp-query').addEventListener('keydown', (e) => {
  if (e.key === 'Enter') { e.preventDefault(); $('mp-search-btn').click(); }
});
document.querySelectorAll('.suggest').forEach(b => {
  b.onclick = () => {
    $('mp-query').value = b.dataset.q;
    $('mp-search-btn').click();
  };
});

// ===== TOAST PREMIUM =====
let toastTimeout = null;
function showToast(msg, duration = 2500) {
  const t = $('toast');
  if (!t) return;
  t.textContent = msg;
  t.classList.add('show');
  if (toastTimeout) clearTimeout(toastTimeout);
  toastTimeout = setTimeout(() => t.classList.remove('show'), duration);
}

function flashAdd(msg) {
  showToast('✓ ' + msg.slice(0, 55) + (msg.length > 55 ? '…' : ''));
  updateCart();
}

// ===================== CALCUL =====================
$('compute').onclick = async () => {
  const ids = Object.keys(cart);
  if (ids.length === 0) { alert('Sélectionnez au moins un appareil.'); return; }
  const btn = $('compute');
  btn.disabled = true;
  btn.innerHTML = '<span class="loading"></span> Calcul…';
  try {
    // Séparer : appareils du catalogue (id connu) vs produits marketplace (custom)
    const selections = [];
    const customAppliances = [];
    ids.forEach(id => {
      const entry = cart[id];
      const power = parseFloat(entry.override) || DEFAULT_POWER_BY_CAT[entry.category] || 800;
      const cat = entry.category || 'OTHER';
      if (id.startsWith('mp_')) {
        // Produit marketplace : créer un appareil custom avec sa puissance
        customAppliances.push({
          id: id,
          name: entry.name || 'Appareil marketplace',
          category: cat,
          powerWatts: power,
          dutyCycle: cat === 'COOLING' || cat === 'COLD' ? 0.4 : 0.6,
          dailyHours: cat === 'COLD' ? 24 : (cat === 'COOLING' ? 8 : 3),
          isNightOnly: cat === 'COLD' || cat === 'LIGHTING',
          isCooling: cat === 'COOLING' || cat === 'COLD',
          surgeFactor: cat === 'COOLING' ? 3.0 : 1.0
        });
      } else {
        selections.push({
          applianceId: id,
          quantity: entry.qty,
          overridePowerWatts: power > 0 ? power : null
        });
      }
    });
    const body = {
      city: $('city').value.trim() || null,
      outageHours: parseInt($('outage').value, 10),
      selections,
      customAppliances,
      useSmartPlugs: $('usePlugs').checked,
      preferRent: $('preferRent').checked
    };
    const [reco, weather, plugs] = await Promise.all([
      api('/api/recommend', { method:'POST', body: JSON.stringify(body) }),
      api('/api/weather?city=' + encodeURIComponent(body.city || '')),
      body.useSmartPlugs ? api('/api/plugs') : null
    ]);

    renderWeather(weather);
    renderConsumption(reco.profile, selections);
    renderBattery(reco.battery, reco.outageHours);
    renderSources(reco.powerSources, reco.totalInvestmentTnd, reco.monthlyOptionTnd, body.preferRent, reco.battery.totalTnd);
    renderSteg(reco.steg);
    if (plugs) renderPlugs(plugs);

    ['step-weather','step-consumption','step-battery','step-source','step-steg'].forEach(id => $(id).classList.remove('hidden'));
    if (plugs) $('step-plugs').classList.remove('hidden');
    setTimeout(() => $('step-weather').scrollIntoView({ behavior:'smooth', block:'start' }), 100);

    // Sauvegarder l'inventaire en arrière-plan (best-effort)
    Promise.all(selections.map(s => api('/api/my-appliances', { method:'POST', body: JSON.stringify(s) }).catch(()=>null)));
  } catch (e) {
    alert('Erreur: ' + e.message);
  } finally {
    btn.disabled = false;
    btn.innerHTML = '<svg class="ic"><use href="#ic-bolt"/></svg> Calculer mes besoins';
  }
};

function renderWeather(w) {
  $('weather-loc').textContent = `${w.city} · ${w.latitude.toFixed(3)}, ${w.longitude.toFixed(3)} · ${w.timezone}`;
  $('weather-grid').innerHTML = w.days.map(d => `
    <div class="weather-day">
      <div class="d">${new Date(d.date).toLocaleDateString('fr-FR', {weekday:'long', day:'numeric', month:'short'})}</div>
      <div class="t">${Math.round(d.tempMaxC)}°<span style="color:var(--muted);font-size:16px">/${Math.round(d.tempMinC)}°</span></div>
      <div class="sub">moy ${Math.round(d.tempMeanC)}°</div>
        <div class="sub sun"><svg class="ic"><use href="#ic-sun"/></svg> ${d.sunshineHours.toFixed(1)} h soleil</div>
      <div class="sub">${d.precipitationMm.toFixed(1)} mm · ${Math.round(d.windKmh)} km/h</div>
    </div>`).join('');
}

// ===== COUNT-UP ANIMATION =====
function animateCountUp(el, target, suffix, duration = 900) {
  const start = performance.now();
  const isInt = Number.isInteger(target);
  function step(now) {
    const progress = Math.min((now - start) / duration, 1);
    const eased = 1 - Math.pow(1 - progress, 3);
    const current = target * eased;
    el.textContent = isInt ? fmt(current) : fmt1(current);
    if (progress < 1) requestAnimationFrame(step);
    else el.textContent = isInt ? fmt(target) : fmt1(target);
  }
  requestAnimationFrame(step);
}

function renderConsumption(p, selections) {
  const nb = selections.reduce((s,x) => s + x.quantity, 0);
  const ovCount = selections.filter(x => x.overridePowerWatts).length;
  $('basis-info').textContent = `Calcul basé sur ${nb} appareil${nb>1?'s':''} identifié${nb>1?'s':''}${ovCount>0 ? ` · ${ovCount} avec puissance réelle précise` : ''}.`;
  // Render KPIs with count-up
  $('kpis').innerHTML = `
    <div class="kpi"><div class="label">Conso / jour</div><div class="val"><span class="kpi-num" data-v="${p.averageDailyKwh}" data-int="0">0</span><span class="unit"> kWh</span></div></div>
    <div class="kpi"><div class="label">De jour</div><div class="val"><span class="kpi-num" data-v="${p.dayKwhAvg}" data-int="0">0</span><span class="unit"> kWh</span></div></div>
    <div class="kpi"><div class="label">De nuit</div><div class="val"><span class="kpi-num" data-v="${p.nightKwhAvg}" data-int="0">0</span><span class="unit"> kWh</span></div></div>
    <div class="kpi"><div class="label">Pic puissance</div><div class="val"><span class="kpi-num" data-v="${p.peakPowerW}" data-int="1">0</span><span class="unit"> W</span></div></div>`;
  // Trigger count-up on each KPI
  document.querySelectorAll('.kpi-num').forEach(el => {
    const target = parseFloat(el.dataset.v);
    const isInt  = el.dataset.int === '1';
    animateCountUp(el, target, '', 900);
  });
  const max = Math.max(...p.needs.map(n => n.dayWh), ...p.needs.map(n => n.nightWh));
  $('daynight-chart').innerHTML = p.needs.flatMap(n => [
    `<div class="bar-row"><div class="name">${new Date(n.date).toLocaleDateString('fr-FR', {weekday:'short'})} ${Math.round(n.tempMaxC)}°</div><div class="bar"><div class="fill day" style="width:0%" data-target="${(n.dayWh/max*100).toFixed(0)}"></div></div><div class="v">${fmt1(n.dayWh/1000)} kWh</div></div>`,
    `<div class="bar-row"><div class="name" style="visibility:hidden">.</div><div class="bar"><div class="fill night" style="width:0%" data-target="${(n.nightWh/max*100).toFixed(0)}"></div></div><div class="v">${fmt1(n.nightWh/1000)} kWh</div></div>`
  ]).join('');
  // Animate bars
  setTimeout(() => {
    document.querySelectorAll('.fill[data-target]').forEach(bar => {
      bar.style.transition = 'width 0.7s cubic-bezier(0.4,0,0.2,1)';
      bar.style.width = bar.dataset.target + '%';
    });
  }, 80);
  const catTotals = {};
  p.needs.forEach(n => Object.entries(n.byCategory).forEach(([k,v]) => { catTotals[k] = (catTotals[k]||0) + v; }));
  const entries = Object.entries(catTotals).sort((a,b)=>b[1]-a[1]).slice(0,5);
  $('cat-breakdown').innerHTML = `
    <span><i style="background:var(--amber)"></i>Jour</span><span><i style="background:var(--teal)"></i>Nuit</span>
    <span style="margin-left:18px">Top: ${entries.map(e => `${(CAT_LABELS[e[0]]||e[0]).toLowerCase()} ${fmt1(e[1]/3/1000)}kWh`).join(' · ')}</span>`;
}

function renderBattery(b, outage) {
  const cov = b.coversOutage;
  $('battery-pick').innerHTML = `
    <div class="battery-box">
        <div class="icon"><svg class="ic"><use href="#ic-battery"/></svg></div>
      <div class="info">
        <div class="title">${b.count} × ${esc(b.battery.brand)} ${esc(b.battery.model)}</div>
        <div class="meta">${esc(b.battery.chemistry)} · ${b.totalUsableKwh} kWh utiles · ${b.battery.cycles.toLocaleString('fr-FR')} cycles · garantie ${b.battery.warrantyYears} ans</div>
          <div class="${cov ? 'tag-ok' : 'tag-warn'}">${cov ? 'OK' : 'Attention'} · Autonomie ${b.autonomyHours}h ${cov ? '≥' : '<'} ${outage}h requise</div>
      </div>
      <div class="price"><div class="big">${fmt(b.totalTnd)} DT</div><div class="sm">≈ ${fmt(b.totalTnd/b.count)} DT/unité</div></div>
    </div>
    <p class="muted">Source: <a href="${b.battery.sourceUrl}" target="_blank">${new URL(b.battery.sourceUrl).hostname}</a></p>`;
}

function renderSources(sources, totalInv, monthly, preferRent, batteryTotal) {
  $('source-picks').innerHTML = sources.map(s => {
    const v = preferRent ? `<b>${fmt(s.monthlyTnd)} DT/mois</b> (location)` : `<b>${fmt(s.source.purchaseTnd * s.units)} DT</b> achat`;
    return `<div class="source-card">
      <div class="ico"><svg class="ic"><use href="${s.source.kind === 'GAS_GENERATOR' ? '#ic-cog' : s.source.kind === 'SOLAR_KIT' ? '#ic-sun' : '#ic-plug'}"/></svg></div>
      <div><div class="nm">${esc(s.source.name)} × ${s.units}</div><div class="ds">${esc(s.rationale)}</div>
      <div class="ds" style="margin-top:6px">${s.source.powerKw} kW · ${esc(s.source.fuelType)} · maintenance ${fmt(s.source.maintenancePerYearTnd)} DT/an</div></div>
      <div class="rt">${v}</div></div>`;
  }).join('');
  if (preferRent) {
    $('budget').innerHTML = `
      <div class="line"><span>Sources louées + maintenance</span><span class="v">${fmt(monthly)} DT/mois</span></div>
      <div class="line total"><span>Budget mensuel</span><span class="v">${fmt(monthly)} DT/mois</span></div>
      <p class="muted" style="margin:10px 0 0">Batterie généralement achetée même en location.</p>`;
  } else {
    const sourcesBuy = sources.reduce((sum, s) => sum + s.source.purchaseTnd * s.units, 0);
    $('budget').innerHTML = `
      <div class="line"><span>Batterie (achat)</span><span class="v">${fmt(batteryTotal)} DT</span></div>
      <div class="line"><span>Source d'énergie (achat)</span><span class="v">${fmt(sourcesBuy)} DT</span></div>
      <div class="line total"><span>Investissement total</span><span class="v">${fmt(totalInv)} DT</span></div>
      <p class="muted" style="margin:10px 0 0">Prix indicatifs marché Tunisie 2026.</p>`;
  }
}

function renderSteg(s) {
  const p = s.prosol;
  $('steg-block').innerHTML = `
    <div class="steg-grid">
      <div class="steg-box"><div class="label">Facture STEG actuelle</div><div class="big">${fmt2(s.monthlyBillNowTnd)} DT</div><div class="sm">≈ ${fmt2(s.yearlyBillNowTnd)} DT / an</div></div>
      <div class="steg-box highlight"><div class="label">Après solaire</div><div class="big">${fmt2(s.monthlyBillWithSolarTnd)} DT</div><div class="sm">≈ ${fmt2(s.monthlyBillWithSolarTnd*12)} DT / an</div></div>
    </div>
    ${p.totalImmediateSavings > 0 ? `
    <div class="steg-box" style="margin-bottom:14px">
      <div class="label" style="margin-bottom:8px">Programme PROSOL ELEC (ANME / STEG)</div>
      <div class="prosol-row"><span>Subvention ANME (30%)</span><span class="v">${fmt(p.anmeSubsidy)} DT</span></div>
      <div class="prosol-row"><span>Prime par kW installé</span><span class="v">${fmt(p.prime)} DT</span></div>
      <div class="prosol-row"><span>Aide financière immédiate</span><span class="v">${fmt(p.totalImmediateSavings)} DT</span></div>
      <div class="prosol-row"><span>Crédit STEG</span><span class="v">${fmt2(p.creditMonthly)} DT/mois / ${p.creditYears} ans</span></div>
      <div class="prosol-row"><span>Économie annuelle</span><span class="v">${fmt2(s.yearlySavingsTnd)} DT/an</span></div>
      <div class="prosol-row"><span>Retour sur investissement</span><span class="v">${fmt1(s.paybackYears)} ans</span></div>
    </div>` : `<p class="muted">Activez une source solaire pour voir l'aide PROSOL ELEC.</p>`}
    <p class="muted">Sources: <a href="https://www.steg.com.tn/fr/page/les-tarifs-d'électricité" target="_blank">tarifs STEG</a> · <a href="https://www.anme.tn/fr/project/prosol-elec-economique" target="_blank">PROSOL ELEC (ANME)</a></p>`;
}

function renderPlugs(plugs) {
  $('plug-list').innerHTML = plugs.map(p => `
    <div class="plug-row">
      <div class="nm"><span class="plug-dot"></span>${esc(p.name)}</div>
      <div class="w">${fmt(p.currentWatts)} W maintenant</div>
      <div class="w">${p.todayKwh} kWh aujourd'hui</div>
    </div>`).join('');
}

// ===================== CRM =====================
let clients = [], partners = [], appts = [];
async function loadCrm() {
  try {
    [clients, partners, appts] = await Promise.all([api('/api/clients'), api('/api/partners'), api('/api/appointments')]);
    renderClients(); renderPartners(); renderAppointments(); renderUpcoming(); populateSelects();
  } catch (e) { console.error(e); }
}
function populateSelects() {
  $('appt-form').clientId.innerHTML = '<option value="">— Client —</option>' + clients.map(c => `<option value="${c.id}">${esc(c.nom)} ${esc(c.prenom)}</option>`).join('');
  $('appt-form').partnerId.innerHTML = '<option value="">— Partenaire —</option>' + partners.map(p => `<option value="${p.id}">${esc(p.nom)}</option>`).join('');
}
function renderUpcoming() {
  const now = new Date();
  const up = appts.filter(a => a.statut !== 'ANNULE' && new Date(a.dateTime) >= now).sort((a,b)=>new Date(a.dateTime)-new Date(b.dateTime)).slice(0,5);
  $('upcoming-appts').innerHTML = up.length === 0 ? '<div class="empty">Aucun rendez-vous à venir.</div>' : up.map(a => `
    <div class="list-row"><div class="main"><div class="nm">${esc(a.titre)}</div>
    <div class="sub">${new Date(a.dateTime).toLocaleDateString('fr-FR', {weekday:'long', day:'numeric', month:'long', hour:'2-digit', minute:'2-digit'})}${a.client ? ' · ' + esc(a.client.nom) : ''}${a.partner ? ' · ' + esc(a.partner.nom) : ''}</div></div>
    <span class="badge ${a.type}">${a.type}</span><span class="badge ${a.statut}">${a.statut}</span></div>`).join('');
}
function renderClients() {
  $('client-list').innerHTML = clients.length === 0 ? '<div class="empty">Aucun client.</div>' : clients.map(c => `
    <div class="list-row"><div class="main"><div class="nm">${esc(c.nom)} ${esc(c.prenom)}</div>
    <div class="sub">${[c.telephone,c.ville,c.email].filter(Boolean).map(esc).join(' · ')}</div></div>
    <div class="actions"><button class="del" data-del-client="${c.id}">Supprimer</button></div></div>`).join('');
  document.querySelectorAll('[data-del-client]').forEach(b => b.onclick = async () => { if(confirm('Supprimer ?')){ await api('/api/clients/'+b.dataset.delClient,{method:'DELETE'}); loadCrm(); } });
}
function renderPartners() {
  $('partner-list').innerHTML = partners.length === 0 ? '<div class="empty">Aucun partenaire.</div>' : partners.map(p => `
    <div class="list-row"><div class="main"><div class="nm">${esc(p.nom)}</div>
    <div class="sub">${[p.telephone,p.zone,p.email].filter(Boolean).map(esc).join(' · ')}</div></div>
    <span class="badge ${p.type}">${p.type}</span>
    <div class="actions"><button class="del" data-del-partner="${p.id}">Supprimer</button></div></div>`).join('');
  document.querySelectorAll('[data-del-partner]').forEach(b => b.onclick = async () => { if(confirm('Supprimer ?')){ await api('/api/partners/'+b.dataset.delPartner,{method:'DELETE'}); loadCrm(); } });
}
function renderAppointments() {
  $('appt-list').innerHTML = appts.length === 0 ? '<div class="empty">Aucun rendez-vous.</div>' : appts.map(a => `
    <div class="list-row"><div class="main"><div class="nm">${esc(a.titre)}</div>
    <div class="sub">${new Date(a.dateTime).toLocaleDateString('fr-FR', {weekday:'short', day:'numeric', month:'short', hour:'2-digit', minute:'2-digit'})}${a.client ? ' · ' + esc(a.client.nom) : ''}${a.partner ? ' · ' + esc(a.partner.nom) : ''}</div></div>
    <span class="badge ${a.type}">${a.type}</span>
    <div class="actions"><button class="del" data-del-appt="${a.id}">Supprimer</button></div></div>`).join('');
  document.querySelectorAll('[data-del-appt]').forEach(b => b.onclick = async () => { if(confirm('Supprimer ?')){ await api('/api/appointments/'+b.dataset.delAppt,{method:'DELETE'}); loadCrm(); } });
}
function bindForm(btnId, formId) {
  $(btnId).onclick = () => $(formId).classList.toggle('hidden');
  $(formId).querySelectorAll('.cancel-btn').forEach(b => b.onclick = () => $(formId).classList.add('hidden'));
}
bindForm('add-client-btn','client-form'); bindForm('add-partner-btn','partner-form'); bindForm('add-appt-btn','appt-form');
$('client-form').onsubmit = async (e) => { e.preventDefault(); const data = Object.fromEntries(new FormData(e.target)); await api('/api/clients',{method:'POST',body:JSON.stringify(data)}); e.target.reset(); $('client-form').classList.add('hidden'); loadCrm(); };
$('partner-form').onsubmit = async (e) => { e.preventDefault(); const data = Object.fromEntries(new FormData(e.target)); await api('/api/partners',{method:'POST',body:JSON.stringify(data)}); e.target.reset(); $('partner-form').classList.add('hidden'); loadCrm(); };
$('appt-form').onsubmit = async (e) => { e.preventDefault(); const fd = new FormData(e.target); const data = { titre: fd.get('titre'), clientId: fd.get('clientId')||null, partnerId: fd.get('partnerId')||null, dateTime: fd.get('dateTime'), type: fd.get('type'), statut: fd.get('statut'), notes: fd.get('notes') }; await api('/api/appointments',{method:'POST',body:JSON.stringify(data)}); e.target.reset(); $('appt-form').classList.add('hidden'); loadCrm(); };

// ===================== INIT =====================
if (isLoggedIn()) showApp(); else showAuth();
