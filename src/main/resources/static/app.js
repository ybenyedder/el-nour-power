// El Nour Power — Frontend Vanilla (JWT, Multi-utilisateurs, Catalogue & Marketplace)
const $ = (id) => document.getElementById(id);
const esc = (s) => String(s ?? '').replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
const fmt = (n) => Math.round(n).toLocaleString('fr-FR');
const fmt1 = (n) => (Math.round(n*10)/10).toLocaleString('fr-FR');
const fmt2 = (n) => (Math.round(n*100)/100).toLocaleString('fr-FR');



// Icônes & labels par catégorie
const CAT_ICONS = {
  COOLING: '#ic-snow', HEATING: '#ic-flame', LAUNDRY: '#ic-wash', COLD: '#ic-snow',
  COOKING: '#ic-flame', LIGHTING: '#ic-lightbulb', ELECTRONICS: '#ic-monitor',
  WATER: '#ic-drop', OTHER: '#ic-box'
};
const CAT_LABELS = {
  COOLING: 'Climatisation', HEATING: 'Chauffage', LAUNDRY: 'Lavage', COLD: 'Froid',
  COOKING: 'Cuisine', LIGHTING: 'Éclairage', ELECTRONICS: 'Électronique', WATER: 'Eau', OTHER: 'Autre'
};

function catIcon(cat) {
  const id = CAT_ICONS[cat] || '#ic-box';
  return `<svg class="ic"><use href="${id}"/></svg>`;
}

// Appareils courants pré-définis (Quick Add)
const QUICK_APPLIANCES = [
  { id: 'frigo_a', name: 'Réfrigérateur A+', category: 'COLD', power: 150, icon: '🧊' },
  { id: 'clim_12000', name: 'Climatiseur 12000 BTU', category: 'COOLING', power: 1200, icon: '❄️' },
  { id: 'clim_18000', name: 'Climatiseur 18000 BTU', category: 'COOLING', power: 1800, icon: '❄️' },
  { id: 'lave_linge', name: 'Machine à laver', category: 'LAUNDRY', power: 2000, icon: '🧺' },
  { id: 'four_elec', name: 'Four électrique', category: 'COOKING', power: 2200, icon: '🍲' },
  { id: 'chauffe_eau', name: 'Chauffe-eau 100L', category: 'WATER', power: 2000, icon: '⚡' },
  { id: 'tv_led', name: 'TV LED 55"', category: 'ELECTRONICS', power: 110, icon: '📺' },
  { id: 'eclairage_led', name: 'Éclairage LED', category: 'LIGHTING', power: 60, icon: '💡' },
  { id: 'ordinateur', name: 'PC de bureau', category: 'ELECTRONICS', power: 200, icon: '💻' },
  { id: 'pompe_eau', name: 'Pompe à eau', category: 'WATER', power: 750, icon: '🚰' }
];

const DEFAULT_POWER_BY_CAT = {
  COOLING: 1200, COLD: 200, LAUNDRY: 1800, COOKING: 2000,
  LIGHTING: 60, ELECTRONICS: 150, WATER: 1500, HEATING: 1800, OTHER: 800
};

// API Fetch
async function api(path, opts = {}) {
  const headers = { ...(opts.headers || {}) };
  if (opts.body && !headers['Content-Type']) headers['Content-Type'] = 'application/json';
  const res = await fetch(path, { ...opts, headers });
  if (!res.ok) {
    let msg = 'Erreur ' + res.status;
    try { const j = await res.json(); msg = j.error || j.message || msg; } catch {}
    throw new Error(msg);
  }
  return res.status === 204 ? null : res.json();
}

// ===================== APP INIT =====================
function showApp() {
  $('user-tag').textContent = 'Mode Local';
  initQuickCatalog();
  renderInventory();
  updateCart();
}

// ===================== NAVIGATION =====================
function switchTab(tabName) {
  document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));
  document.querySelectorAll('.bnav-item').forEach(x => x.classList.remove('active'));
  document.querySelectorAll('.sidebar-item[data-tab]').forEach(x => x.classList.remove('active'));

  const panel = $('panel-' + tabName);
  if (panel) panel.classList.add('active');
  const bnavItem = document.querySelector(`.bnav-item[data-tab="${tabName}"]`);
  if (bnavItem) bnavItem.classList.add('active');
  const snavItem = document.querySelector(`.sidebar-item[data-tab="${tabName}"]`);
  if (snavItem) snavItem.classList.add('active');
  if (tabName === 'crm') loadCrm();
}

document.querySelectorAll('.bnav-item').forEach(b => { b.onclick = () => switchTab(b.dataset.tab); });
document.querySelectorAll('.sidebar-item[data-tab]').forEach(b => { b.onclick = () => switchTab(b.dataset.tab); });

function initSidebar() {
  const sidebar = $('desktop-sidebar');
  if (!sidebar) return;
  const mq = window.matchMedia('(min-width: 1100px)');
  const toggle = () => { sidebar.style.display = mq.matches ? 'flex' : 'none'; };
  toggle();
  mq.addEventListener('change', toggle);
}
initSidebar();

// Navigation vers les étapes du calculateur
function scrollToStep(stepId) {
  const target = $(stepId);
  if (!target) return;
  if (target.classList.contains('hidden')) {
    // Si les étapes de résultat sont cachées mais que l'inventaire n'est pas vide, déclencher le calcul
    if (Object.keys(cart).length > 0) {
      $('compute').click();
      return;
    }
  }
  target.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

// ===================== PANIER & INVENTAIRE =====================
// cart : { id: { qty, override, name, category, image? } }
const cart = {};
let mpCart = {};

function initQuickCatalog() {
  const chipsContainer = $('quick-catalog-chips');
  if (!chipsContainer) return;
  chipsContainer.innerHTML = QUICK_APPLIANCES.map(item => {
    const isAdded = cart[item.id] != null;
    return `
      <button class="qc-chip ${isAdded ? 'active' : ''}" data-qid="${item.id}">
        <span>${item.icon}</span>
        <span>${esc(item.name)}</span>
        <span class="w">${item.power}W</span>
      </button>`;
  }).join('');

  chipsContainer.querySelectorAll('.qc-chip').forEach(btn => {
    btn.onclick = () => {
      const qid = btn.dataset.qid;
      const item = QUICK_APPLIANCES.find(x => x.id === qid);
      if (!item) return;

      if (cart[qid]) {
        cart[qid].qty += 1;
        showToast(`Quantité augmentée : ${item.name} (${cart[qid].qty})`);
      } else {
        cart[qid] = { qty: 1, override: String(item.power), name: item.name, category: item.category };
        btn.classList.add('active');
        showToast(`Ajouté : ${item.name}`);
      }
      renderInventory();
      updateCart();
    };
  });
}

function renderInventory() {
  const listEl = $('inventory-list');
  const countBadge = $('inv-count-badge');
  const ids = Object.keys(cart);

  const totalQty = ids.reduce((s, id) => s + (cart[id].qty || 1), 0);
  if (countBadge) countBadge.textContent = `${totalQty} appareil${totalQty > 1 ? 's' : ''}`;

  if (!listEl) return;
  if (ids.length === 0) {
    listEl.innerHTML = '<div class="inv-empty">Aucun appareil sélectionné. Cliquez sur les boutons d\'ajout rapide ci-dessus ou cherchez sur la marketplace.</div>';
    // Réinitialiser l'état des chips
    document.querySelectorAll('.qc-chip').forEach(c => c.classList.remove('active'));
    return;
  }

  listEl.innerHTML = ids.map(id => {
    const item = cart[id];
    const ov = parseFloat(item.override);
    const power = (isNaN(ov) || ov <= 0) ? (DEFAULT_POWER_BY_CAT[item.category] || 800) : ov;
    const catLabel = CAT_LABELS[item.category] || item.category || 'Appareil';

    return `
      <div class="inv-row" data-invid="${id}">
        <div class="inv-icon">${catIcon(item.category)}</div>
        <div class="inv-info">
          <div class="inv-title">${esc(item.name)}</div>
          <div class="inv-sub">${catLabel}</div>
        </div>
        <div class="inv-watt-wrap">
          <input type="number" class="inv-watt-input" value="${Math.round(power)}" min="1" max="25000" data-w-id="${id}" aria-label="Puissance en watts">
          <span class="inv-unit">W</span>
        </div>
        <div class="qty-ctrl">
          <button class="qty-btn" data-qty-dec="${id}" aria-label="Diminuer">-</button>
          <span class="qty-val">${item.qty || 1}</span>
          <button class="qty-btn" data-qty-inc="${id}" aria-label="Augmenter">+</button>
        </div>
        <button class="inv-del" data-del-inv="${id}" title="Supprimer de l'inventaire" aria-label="Supprimer">
          <svg class="ic"><use href="#ic-trash"/></svg>
        </button>
      </div>`;
  }).join('');

  // Events sur la liste d'inventaire
  listEl.querySelectorAll('[data-w-id]').forEach(input => {
    input.onchange = () => {
      const id = input.dataset.wId;
      const val = parseFloat(input.value);
      if (!isNaN(val) && val > 0 && cart[id]) {
        cart[id].override = String(val);
        updateCart();
      }
    };
  });

  listEl.querySelectorAll('[data-qty-inc]').forEach(btn => {
    btn.onclick = () => {
      const id = btn.dataset.qtyInc;
      if (cart[id]) { cart[id].qty += 1; renderInventory(); updateCart(); }
    };
  });

  listEl.querySelectorAll('[data-qty-dec]').forEach(btn => {
    btn.onclick = () => {
      const id = btn.dataset.qtyDec;
      if (cart[id]) {
        if (cart[id].qty > 1) { cart[id].qty -= 1; }
        else { delete cart[id]; delete mpCart[id]; }
        initQuickCatalog();
        renderInventory();
        updateCart();
      }
    };
  });

  listEl.querySelectorAll('[data-del-inv]').forEach(btn => {
    btn.onclick = () => {
      const id = btn.dataset.delInv;
      delete cart[id];
      delete mpCart[id];
      initQuickCatalog();
      renderInventory();
      updateCart();
    };
  });
}

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

// ===================== RECHERCHE MARKETPLACE =====================
async function mpSearch(query, source) {
  $('mp-results').innerHTML = '';
  const loading = $('mp-loading');
  loading.classList.remove('hidden');
  try {
    const data = await api(`/api/products/search?q=${encodeURIComponent(query)}&source=${source}&limit=24`);
    renderMpResults(data.products, query);
  } catch (e) {
    $('mp-results').innerHTML = `<div class="mp-empty">Erreur : ${esc(e.message)}. Réessayez dans un instant.</div>`;
  } finally {
    loading.classList.add('hidden');
  }
}

function renderMpResults(products, query) {
  if (!products || products.length === 0) {
    $('mp-results').innerHTML = `<div class="mp-empty">Aucun produit trouvé pour « ${esc(query)} ».</div>`;
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
            <span class="mp-cat">${CAT_LABELS[p.category] || p.category}</span>
          </div>
          <div class="mp-source-badge">${esc(p.source || 'marketplace')}</div>
          ${p.detectedPowerWatts ? `<div class="mp-power"><b>${Math.round(p.detectedPowerWatts)} W</b> détecté</div>` : ''}
        </div>
        <button class="mp-add" data-add="${id}">${added ? '<svg class="ic"><use href="#ic-check"/></svg> Ajouté' : '<svg class="ic"><use href="#ic-plus"/></svg> Ajouter à l\'inventaire'}</button>
      </div>`;
  }).join('');

  document.querySelectorAll('[data-add]').forEach(btn => {
    btn.onclick = () => {
      const id = btn.dataset.add;
      const p = products[parseInt(id.split('_')[1])];
      if (mpCart[id]) {
        delete mpCart[id];
        delete cart[id];
        btn.innerHTML = '<svg class="ic"><use href="#ic-plus"/></svg> Ajouter à l\'inventaire';
        btn.closest('.mp-card').classList.remove('added');
      } else {
        mpCart[id] = { product: p, qty: 1 };
        let power = p.detectedPowerWatts || DEFAULT_POWER_BY_CAT[p.category] || 800;
        cart[id] = { qty: 1, override: String(Math.round(power)), name: p.title, category: p.category, image: p.image };
        btn.innerHTML = '<svg class="ic"><use href="#ic-check"/></svg> Ajouté';
        btn.closest('.mp-card').classList.add('added');
        showToast(`Ajouté : ${p.title.slice(0,40)}…`);
      }
      renderInventory();
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

// ===== TOAST =====
let toastTimeout = null;
function showToast(msg, duration = 2500) {
  const t = $('toast');
  if (!t) return;
  t.textContent = msg;
  t.classList.add('show');
  if (toastTimeout) clearTimeout(toastTimeout);
  toastTimeout = setTimeout(() => t.classList.remove('show'), duration);
}

// ===================== CALCUL & RENDER RESULTATS =====================
$('compute').onclick = async () => {
  const ids = Object.keys(cart);
  if (ids.length === 0) { alert('Veuillez sélectionner au moins un appareil dans votre inventaire.'); return; }
  const btn = $('compute');
  btn.disabled = true;
  btn.innerHTML = '<span class="loading"></span> Calcul en cours…';
  try {
    const selections = [];
    const customAppliances = [];
    ids.forEach(id => {
      const entry = cart[id];
      const power = parseFloat(entry.override) || DEFAULT_POWER_BY_CAT[entry.category] || 800;
      const cat = entry.category || 'OTHER';
      if (id.startsWith('mp_')) {
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
      city: $('city').value.trim() || 'Tunis',
      outageHours: parseInt($('outage').value, 10),
      selections,
      customAppliances,
      useSmartPlugs: $('usePlugs').checked,
      preferRent: $('preferRent').checked
    };

    const [reco, weather, plugs] = await Promise.all([
      api('/api/recommend', { method:'POST', body: JSON.stringify(body) }),
      api('/api/weather?city=' + encodeURIComponent(body.city || 'Tunis')),
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
    showToast('✓ Calcul du besoin énergétique réalisé avec succès !');

    // Sauvegarde d'inventaire
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
      <div class="t">${Math.round(d.tempMaxC)}°<span style="color:var(--muted);font-size:15px"> / ${Math.round(d.tempMinC)}°</span></div>
      <div class="sub">Moy. ${Math.round(d.tempMeanC)}°C</div>
      <div class="sub sun"><svg class="ic"><use href="#ic-sun"/></svg> ${d.sunshineHours.toFixed(1)} h d'ensoleillement</div>
      <div class="sub">${d.precipitationMm.toFixed(1)} mm préc. · ${Math.round(d.windKmh)} km/h vent</div>
    </div>`).join('');
}

function renderConsumption(p, selections) {
  const nb = selections.reduce((s,x) => s + x.quantity, 0);
  $('basis-info').textContent = `Profil de consommation estimé pour ${nb} appareil${nb>1?'s':''}.`;
  $('kpis').innerHTML = `
    <div class="kpi"><div class="label">Conso / jour</div><div class="val">${fmt1(p.averageDailyKwh)} <span class="unit">kWh</span></div></div>
    <div class="kpi"><div class="label">De jour</div><div class="val">${fmt1(p.dayKwhAvg)} <span class="unit">kWh</span></div></div>
    <div class="kpi"><div class="label">De nuit</div><div class="val">${fmt1(p.nightKwhAvg)} <span class="unit">kWh</span></div></div>
    <div class="kpi"><div class="label">Puissance crête</div><div class="val">${fmt(p.peakPowerW)} <span class="unit">W</span></div></div>`;

  const max = Math.max(...p.needs.map(n => n.dayWh), ...p.needs.map(n => n.nightWh));
  $('daynight-chart').innerHTML = p.needs.flatMap(n => [
    `<div class="bar-row"><div class="name">${new Date(n.date).toLocaleDateString('fr-FR', {weekday:'short'})} ${Math.round(n.tempMaxC)}°</div><div class="bar"><div class="fill day" style="width:${(n.dayWh/max*100).toFixed(0)}%"></div></div><div class="v">${fmt1(n.dayWh/1000)} kWh</div></div>`,
    `<div class="bar-row"><div class="name" style="visibility:hidden">.</div><div class="bar"><div class="fill night" style="width:${(n.nightWh/max*100).toFixed(0)}%"></div></div><div class="v">${fmt1(n.nightWh/1000)} kWh</div></div>`
  ]).join('');

  const catTotals = {};
  p.needs.forEach(n => Object.entries(n.byCategory).forEach(([k,v]) => { catTotals[k] = (catTotals[k]||0) + v; }));
  const entries = Object.entries(catTotals).sort((a,b)=>b[1]-a[1]).slice(0,5);
  $('cat-breakdown').innerHTML = `
    <span><i style="background:var(--amber)"></i>Jour</span><span><i style="background:var(--emerald)"></i>Nuit</span>
    <span style="margin-left:14px">Top consommations : ${entries.map(e => `${(CAT_LABELS[e[0]]||e[0]).toLowerCase()} ${fmt1(e[1]/3/1000)}kWh`).join(' · ')}</span>`;
}

function renderBattery(b, outage) {
  const cov = b.coversOutage;
  $('battery-pick').innerHTML = `
    <div class="battery-box">
      <div class="icon"><svg class="ic"><use href="#ic-battery"/></svg></div>
      <div class="info">
        <div class="title">${b.count} × ${esc(b.battery.brand)} ${esc(b.battery.model)}</div>
        <div class="meta">${esc(b.battery.chemistry)} · ${b.totalUsableKwh} kWh utiles · ${b.battery.cycles.toLocaleString('fr-FR')} cycles · Garantie ${b.battery.warrantyYears} ans</div>
        <div class="${cov ? 'tag-ok' : 'tag-warn'}">${cov ? '✓ Autonomie couverte' : '⚠️ Autonomie partielle'} (${b.autonomyHours}h / ${outage}h requises)</div>
      </div>
      <div class="price"><div class="big">${fmt(b.totalTnd)} DT</div><div class="sm">≈ ${fmt(b.totalTnd/b.count)} DT/unité</div></div>
    </div>`;
}

function renderSources(sources, totalInv, monthly, preferRent, batteryTotal) {
  $('source-picks').innerHTML = sources.map(s => {
    const v = preferRent ? `<b>${fmt(s.monthlyTnd)} DT/mois</b> (location)` : `<b>${fmt(s.source.purchaseTnd * s.units)} DT</b> achat`;
    return `<div class="source-card">
      <div class="ico"><svg class="ic"><use href="${s.source.kind === 'GAS_GENERATOR' ? '#ic-cog' : s.source.kind === 'SOLAR_KIT' ? '#ic-sun' : '#ic-plug'}"/></svg></div>
      <div>
        <div class="nm">${esc(s.source.name)} × ${s.units}</div>
        <div class="ds">${esc(s.rationale)}</div>
        <div class="ds" style="margin-top:4px">${s.source.powerKw} kW · ${esc(s.source.fuelType)} · Maintenance : ${fmt(s.source.maintenancePerYearTnd)} DT/an</div>
      </div>
      <div class="rt">${v}</div>
    </div>`;
  }).join('');

  if (preferRent) {
    $('budget').innerHTML = `
      <div class="line"><span>Sources en location + maintenance</span><span class="v">${fmt(monthly)} DT/mois</span></div>
      <div class="line total"><span>Budget mensuel estimé</span><span class="v">${fmt(monthly)} DT/mois</span></div>`;
  } else {
    const sourcesBuy = sources.reduce((sum, s) => sum + s.source.purchaseTnd * s.units, 0);
    $('budget').innerHTML = `
      <div class="line"><span>Batterie (achat)</span><span class="v">${fmt(batteryTotal)} DT</span></div>
      <div class="line"><span>Source d'énergie (achat)</span><span class="v">${fmt(sourcesBuy)} DT</span></div>
      <div class="line total"><span>Investissement Total</span><span class="v">${fmt(totalInv)} DT</span></div>`;
  }
}

function renderSteg(s) {
  const p = s.prosol;
  $('steg-block').innerHTML = `
    <div class="steg-grid">
      <div class="steg-box"><div class="label">Facture STEG actuelle</div><div class="big">${fmt2(s.monthlyBillNowTnd)} DT</div><div class="sm">≈ ${fmt2(s.yearlyBillNowTnd)} DT / an</div></div>
      <div class="steg-box highlight"><div class="label">Facture après installation solaire</div><div class="big">${fmt2(s.monthlyBillWithSolarTnd)} DT</div><div class="sm">≈ ${fmt2(s.monthlyBillWithSolarTnd*12)} DT / an</div></div>
    </div>
    ${p.totalImmediateSavings > 0 ? `
    <div class="steg-box" style="margin-bottom:14px">
      <div class="label" style="margin-bottom:8px">Programme d'aide PROSOL ELEC (ANME / STEG)</div>
      <div class="prosol-row"><span>Subvention ANME (30%)</span><span class="v">${fmt(p.anmeSubsidy)} DT</span></div>
      <div class="prosol-row"><span>Prime d'installation kW</span><span class="v">${fmt(p.prime)} DT</span></div>
      <div class="prosol-row"><span>Aide financière totale</span><span class="v">${fmt(p.totalImmediateSavings)} DT</span></div>
      <div class="prosol-row"><span>Crédit STEG bonifié</span><span class="v">${fmt2(p.creditMonthly)} DT/mois / ${p.creditYears} ans</span></div>
      <div class="prosol-row"><span>Économie annuelle</span><span class="v">${fmt2(s.yearlySavingsTnd)} DT/an</span></div>
      <div class="prosol-row"><span>Retour sur investissement</span><span class="v">${fmt1(s.paybackYears)} ans</span></div>
    </div>` : `<p class="muted">Sélectionnez une source solaire pour calculer l'aide PROSOL ELEC.</p>`}`;
}

function renderPlugs(plugs) {
  $('plug-list').innerHTML = plugs.map(p => `
    <div class="plug-row">
      <div class="nm"><span class="plug-dot"></span>${esc(p.name)}</div>
      <div class="w">${fmt(p.currentWatts)} W actuels</div>
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
    <div class="list-row">
      <div class="main">
        <div class="nm">${esc(a.titre)}</div>
        <div class="sub">${new Date(a.dateTime).toLocaleDateString('fr-FR', {weekday:'long', day:'numeric', month:'long', hour:'2-digit', minute:'2-digit'})}${a.client ? ' · ' + esc(a.client.nom) : ''}${a.partner ? ' · ' + esc(a.partner.nom) : ''}</div>
      </div>
      <span class="badge ${a.type}">${a.type}</span><span class="badge ${a.statut}">${a.statut}</span>
    </div>`).join('');
}

function renderClients() {
  $('client-list').innerHTML = clients.length === 0 ? '<div class="empty">Aucun client enregistré.</div>' : clients.map(c => `
    <div class="list-row">
      <div class="main">
        <div class="nm">${esc(c.nom)} ${esc(c.prenom || '')}</div>
        <div class="sub">${[c.telephone, c.ville, c.email].filter(Boolean).map(esc).join(' · ')}</div>
      </div>
      <div class="actions"><button class="del" data-del-client="${c.id}">Supprimer</button></div>
    </div>`).join('');
  document.querySelectorAll('[data-del-client]').forEach(b => b.onclick = async () => { if(confirm('Supprimer ce client ?')){ await api('/api/clients/'+b.dataset.delClient,{method:'DELETE'}); loadCrm(); } });
}

function renderPartners() {
  $('partner-list').innerHTML = partners.length === 0 ? '<div class="empty">Aucun partenaire enregistré.</div>' : partners.map(p => `
    <div class="list-row">
      <div class="main">
        <div class="nm">${esc(p.nom)}</div>
        <div class="sub">${[p.telephone, p.zone, p.email].filter(Boolean).map(esc).join(' · ')}</div>
      </div>
      <span class="badge ${p.type}">${p.type}</span>
      <div class="actions"><button class="del" data-del-partner="${p.id}">Supprimer</button></div>
    </div>`).join('');
  document.querySelectorAll('[data-del-partner]').forEach(b => b.onclick = async () => { if(confirm('Supprimer ce partenaire ?')){ await api('/api/partners/'+b.dataset.delPartner,{method:'DELETE'}); loadCrm(); } });
}

function renderAppointments() {
  $('appt-list').innerHTML = appts.length === 0 ? '<div class="empty">Aucun rendez-vous planifié.</div>' : appts.map(a => `
    <div class="list-row">
      <div class="main">
        <div class="nm">${esc(a.titre)}</div>
        <div class="sub">${new Date(a.dateTime).toLocaleDateString('fr-FR', {weekday:'short', day:'numeric', month:'short', hour:'2-digit', minute:'2-digit'})}${a.client ? ' · ' + esc(a.client.nom) : ''}${a.partner ? ' · ' + esc(a.partner.nom) : ''}</div>
      </div>
      <span class="badge ${a.type}">${a.type}</span>
      <div class="actions"><button class="del" data-del-appt="${a.id}">Supprimer</button></div>
    </div>`).join('');
  document.querySelectorAll('[data-del-appt]').forEach(b => b.onclick = async () => { if(confirm('Supprimer ce rendez-vous ?')){ await api('/api/appointments/'+b.dataset.delAppt,{method:'DELETE'}); loadCrm(); } });
}

function bindForm(btnId, formId) {
  $(btnId).onclick = () => $(formId).classList.toggle('hidden');
  $(formId).querySelectorAll('.cancel-btn').forEach(b => b.onclick = () => $(formId).classList.add('hidden'));
}
bindForm('add-client-btn','client-form'); bindForm('add-partner-btn','partner-form'); bindForm('add-appt-btn','appt-form');
$('client-form').onsubmit = async (e) => { e.preventDefault(); const data = Object.fromEntries(new FormData(e.target)); await api('/api/clients',{method:'POST',body:JSON.stringify(data)}); e.target.reset(); $('client-form').classList.add('hidden'); loadCrm(); };
$('partner-form').onsubmit = async (e) => { e.preventDefault(); const data = Object.fromEntries(new FormData(e.target)); await api('/api/partners',{method:'POST',body:JSON.stringify(data)}); e.target.reset(); $('partner-form').classList.add('hidden'); loadCrm(); };
$('appt-form').onsubmit = async (e) => { e.preventDefault(); const fd = new FormData(e.target); const data = { titre: fd.get('titre'), clientId: fd.get('clientId')||null, partnerId: fd.get('partnerId')||null, dateTime: fd.get('dateTime'), type: fd.get('type'), statut: fd.get('statut'), notes: fd.get('notes') }; await api('/api/appointments',{method:'POST',body:JSON.stringify(data)}); e.target.reset(); $('appt-form').classList.add('hidden'); loadCrm(); };

// INITIALISATION
showApp();
