#!/usr/bin/env python3
"""
Microservice de scraping produits AliExpress / Alibaba.
Charge les pages comme un vrai navigateur (Playwright + stealth) pour contourner
l'anti-bot, et renvoie les produits en JSON : titre, photo, prix, lien.

Cache en mémoire de 24h pour éviter de re-scrapper la même recherche.
Expose : GET /search?q=climatiseur&source=aliexpress
         GET /health
"""
import json, sys, time, threading, queue
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse, parse_qs
from playwright.sync_api import sync_playwright

# Cache : { "source|query": (timestamp, [products]) }
CACHE = {}
CACHE_TTL = 24 * 3600  # 24h
CACHE_LOCK = threading.Lock()

# Worker dédié : un seul thread gère playwright (exigence de l'API sync).
# Les requêtes sont mises dans une queue, traitées en série par le worker.
_WORKER_Q = queue.Queue()
_WORKER_STARTED = False
_WORKER_READY = threading.Event()
_WORKER_LOCK = threading.Lock()


def _worker():
    """Boucle du worker : un seul navigateur, un seul thread, traite les jobs en série."""
    with sync_playwright() as pw:
        browser = pw.chromium.launch(
            headless=True,
            args=[
                "--disable-blink-features=AutomationControlled",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
            ]
        )
        print("[scraper] Worker prêt, navigateur lancé", file=sys.stderr)
        _WORKER_READY.set()
        while True:
            job = _WORKER_Q.get()
            if job is None:
                break
            query, source, limit, result_box = job
            try:
                result_box["products"] = _scrape_dispatch(browser, query, source, limit)
                result_box["error"] = None
            except Exception as e:
                result_box["error"] = str(e)
                print(f"[ERR] scrape {source} '{query}': {e}", file=sys.stderr)
            finally:
                result_box["done"] = True
                _WORKER_Q.task_done()


def warmup_cache():
    """Précharge les recherches communes en arrière-plan pour que l'utilisateur
    ait des résultats instantanés dès qu'il arrive."""
    common = [
        ("climatiseur", "all"),
        ("réfrigérateur", "all"),
        ("lave linge", "all"),
        ("four électrique", "all"),
        ("téléviseur LED", "all"),
        ("chauffe eau electrique", "all"),
    ]
    def run():
        time.sleep(5)  # laisser le serveur démarrer
        print("[scraper] Préchauffage du cache…", file=sys.stderr)
        for q, src in common:
            try:
                products, _ = search(q, src, limit=12)
                print(f"[scraper]   '{q}' → {len(products)} produits mis en cache", file=sys.stderr)
            except Exception as e:
                print(f"[scraper]   '{q}' échoué: {e}", file=sys.stderr)
        print("[scraper] Préchauffage terminé", file=sys.stderr)
    threading.Thread(target=run, daemon=True).start()


def start_worker():
    global _WORKER_STARTED
    with _WORKER_LOCK:
        if not _WORKER_STARTED:
            t = threading.Thread(target=_worker, daemon=True)
            t.start()
            _WORKER_STARTED = True


def new_context(browser):
    ctx = browser.new_context(
        user_agent="Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                   "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
        locale="fr-FR",
        viewport={"width": 1366, "height": 900},
        java_script_enabled=True,
    )
    ctx.add_init_script(
        "Object.defineProperty(navigator,'webdriver',{get:()=>undefined});"
        "Object.defineProperty(navigator,'plugins',{get:()=>[1,2,3,4,5]});"
        "Object.defineProperty(navigator,'languages',{get:()=>['fr-FR','fr','en']});"
        "window.chrome={runtime:{}};"
    )
    return ctx


def scrape_aliexpress_with(browser, source):
    """Retourne une fonction de scrape utilisant le browser partagé."""
    def _scrape(query, limit=20):
        ctx = new_context(browser)
        page = ctx.new_page()
        try:
            slug = query.strip().lower().replace(" ", "-")
            url = f"https://fr.aliexpress.com/w/wholesale-{slug}.html"
            page.goto(url, wait_until="domcontentloaded", timeout=45000)
            try:
                page.wait_for_selector('a[href*="/item/"]', timeout=15000)
            except Exception:
                pass
            page.wait_for_timeout(3500)
            return page.evaluate(EXTRACT_JS, limit)
        finally:
            ctx.close()
    return _scrape


# Script d'extraction partagé (exécuté dans le navigateur)
EXTRACT_JS = """(limit) => {
    const items = [];
    const links = [...document.querySelectorAll('a[href*="/item/"]')];
    const seen = new Set();
    for (const a of links) {
        if (seen.size >= limit) break;
        const href = a.href.split('?')[0];
        if (seen.has(href)) continue;
        seen.add(href);
        const card = a.closest('div[class]') || a.parentElement?.parentElement || a;
        const img = card.querySelector('img') || a.querySelector('img');
        const allText = (card.textContent || '').replace(/\\s+/g, ' ').trim();
        const priceMatch = allText.match(/(?:US\\$|\\$|€|EUR)\\s?([0-9]+[.,]?[0-9]*)/);
        let title = (a.getAttribute('title') || allText.slice(0, 120) || '').trim();
        if (title.length < 15) continue;
        let imgSrc = img?.src || img?.getAttribute('data-src') || null;
        if (imgSrc && !imgSrc.startsWith('http')) continue;
        items.push({
            title: title.slice(0, 150),
            url: href,
            image: imgSrc,
            price: priceMatch ? priceMatch[0] : null,
            source: 'aliexpress'
        });
    }
    return items;
}"""


def scrape_banggood(browser, query, limit=20):
    """Scrape Banggood — marketplace chinoise qui ne bloque PAS le scraping."""
    ctx = new_context(browser)
    page = ctx.new_page()
    try:
        import urllib.parse as up
        url = f"https://www.banggood.com/search/{up.quote(query)}.html"
        page.goto(url, wait_until="domcontentloaded", timeout=30000)
        # attendre l'apparition des titres produit (plus rapide que networkidle)
        try:
            page.wait_for_selector('a.title[href*="banggood.com"]', timeout=8000)
        except Exception:
            # fallback : un court délai fixe si le sélecteur exact n'arrive pas
            page.wait_for_timeout(3000)
        # un seul scroll pour déclencher le lazy-load des images
        page.evaluate("window.scrollBy(0, 600)")
        page.wait_for_timeout(800)
        return page.evaluate(
            """(limit) => {
                const items = [];
                // Structure Banggood réelle : a.title (href complet + attr title),
                // span.price (oriprice + texte), conteneur li parent.
                const links = [...document.querySelectorAll('a.title[href*="banggood.com"]')];
                const seen = new Set();
                for (const a of links) {
                    if (seen.size >= limit) break;
                    const href = a.href.split('?')[0];
                    if (seen.has(href)) continue;
                    seen.add(href);
                    const card = a.closest('li') || a.parentElement?.parentElement || a;
                    const img = card.querySelector('img');
                    let title = (a.getAttribute('title') || a.textContent || '').trim();
                    title = title.replace(/\\s+/g, ' ').slice(0, 150);
                    if (title.length < 10) continue;
                    const priceEl = card.querySelector('.price, span.price');
                    let priceText = '';
                    if (priceEl) {
                        priceText = (priceEl.textContent || '').trim();
                        if (!priceText && priceEl.getAttribute('oriprice')) {
                            priceText = 'US$' + priceEl.getAttribute('oriprice');
                        }
                    }
                    let imgSrc = img?.src || img?.getAttribute('data-original') || img?.getAttribute('data-src') || img?.getAttribute('data-lazy-src') || null;
                    if (imgSrc && !imgSrc.startsWith('http')) imgSrc = null;
                    items.push({
                        title, url: href, image: imgSrc,
                        price: priceText || null,
                        source: 'banggood'
                    });
                }
                return items;
            }""",
            limit,
        )
    finally:
        try:
            html = page.content()
            with open("/tmp/bg_debug.html", "w") as f: f.write(html[:150000])
        except Exception: pass
        ctx.close()
    ctx = new_context(browser)
    page = ctx.new_page()
    try:
        import urllib.parse as up
        url = f"https://www.alibaba.com/trade/search?SearchText={up.quote(query)}"
        page.goto(url, wait_until="domcontentloaded", timeout=45000)
        try:
            page.wait_for_selector('a[href*="/product-detail/"], [class*="product-card"]', timeout=15000)
        except Exception:
            pass
        page.wait_for_timeout(3500)
        return page.evaluate(
            """(limit) => {
                const items = [];
                const cards = [...document.querySelectorAll('[class*="product-card"], div[class*="offercard"]')];
                const seen = new Set();
                for (const card of cards) {
                    if (seen.size >= limit) break;
                    const a = card.querySelector('a[href*="/product-detail/"], a[href*="product"]');
                    const img = card.querySelector('img');
                    const title = (card.querySelector('h2,h3,[class*="title"]')?.textContent || card.textContent || '').trim().slice(0, 150);
                    const priceMatch = (card.textContent || '').match(/(?:US\\$|\\$|\\¥)\\s?([0-9]+[.,]?[0-9]*)/);
                    if (!title || title.length < 10) continue;
                    const href = a?.href || '';
                    if (href && seen.has(href)) continue;
                    if (href) seen.add(href);
                    let imgSrc = img?.src || img?.getAttribute('data-src') || null;
                    if (imgSrc && !imgSrc.startsWith('http')) imgSrc = null;
                    items.push({ title, url: href, image: imgSrc, price: priceMatch ? priceMatch[0] : null, source: 'alibaba' });
                }
                return items;
            }""",
            limit,
        )
    finally:
        ctx.close()


def search(query, source="aliexpress", limit=20):
    """Recherche avec cache. Délègue le scrape au worker dédié."""
    key = f"{source}|{query.lower().strip()}"
    now = time.time()
    with CACHE_LOCK:
        cached = CACHE.get(key)
        if cached and (now - cached[0]) < CACHE_TTL:
            return cached[1], True

    start_worker()
    # attendre que le worker (navigateur) soit prêt, max 40s
    if not _WORKER_READY.wait(timeout=40):
        return [], False
    result_box = {"products": [], "error": None, "done": False}
    _WORKER_Q.put((query, source, limit, result_box))
    # attendre la fin (max 90s)
    deadline = time.time() + 90
    while not result_box["done"] and time.time() < deadline:
        time.sleep(0.3)

    products = result_box["products"]
    # NE PAS mettre en cache les résultats vides (échecs temporaires : anti-bot,
    # réseau, etc.) pour qu'une nouvelle tentative puisse réussir.
    if products:
        with CACHE_LOCK:
            CACHE[key] = (now, products)
    return products, False


# Helper utilisé par le worker pour dispatcher selon la source.
def _scrape_dispatch(browser, query, source, limit):
    if source == "all":
        # Recherche sur toutes les sources, agrégation + déduplication.
        per_source = max(6, limit // 2)
        combined = []
        seen_titles = set()
        for src in ["banggood", "aliexpress"]:
            try:
                if src == "banggood":
                    items = scrape_banggood(browser, query, per_source)
                else:
                    items = scrape_aliexpress_with(browser, "aliexpress")(query, per_source)
                for it in items:
                    # déduplication par titre normalisé
                    key = it["title"].lower().strip()[:60]
                    if key in seen_titles:
                        continue
                    seen_titles.add(key)
                    it["source"] = src
                    combined.append(it)
                if len(combined) >= limit:
                    break
            except Exception as e:
                print(f"[ERR] multi-source {src} '{query}': {e}", file=sys.stderr)
        return combined[:limit]
    if source == "banggood":
        return scrape_banggood(browser, query, limit)
    if source == "alibaba":
        # Alibaba a aussi un anti-bot fort ; fallback sur banggood
        return scrape_banggood(browser, query, limit)
    # aliexpress (CAPTCHA fréquent) : on tente quand même, sinon l'utilisateur
    # verra 0 résultat et pourra basculer sur banggood.
    try:
        return scrape_aliexpress_with(browser, "aliexpress")(query, limit)
    except Exception:
        return scrape_banggood(browser, query, limit)


class Handler(BaseHTTPRequestHandler):
    def _json(self, code, data):
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path == "/health":
            self._json(200, {"status": "UP", "service": "scraper"})
            return
        if parsed.path == "/search":
            qs = parse_qs(parsed.query)
            q = (qs.get("q", [""])[0] or "").strip()
            source = qs.get("source", ["aliexpress"])[0]
            limit = min(int(qs.get("limit", ["20"])[0]), 40)
            if not q:
                self._json(400, {"error": "Paramètre 'q' requis"})
                return
            t0 = time.time()
            products, cached = search(q, source, limit)
            self._json(200, {
                "query": q,
                "source": source,
                "count": len(products),
                "took_ms": int((time.time() - t0) * 1000),
                "cached": cached,
                "products": products,
            })
            return
        self._json(404, {"error": "Not found"})

    def log_message(self, fmt, *args):
        sys.stderr.write(f"[scraper] {self.address_string()} - {fmt % args}\n")


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 5000
    print(f"[scraper] Démarrage sur le port {port}", file=sys.stderr)
    print("[scraper] Navigateur sera initialisé au premier appel (lazy)", file=sys.stderr)
    # lancer le worker + préchauffer le cache en arrière-plan
    start_worker()
    warmup_cache()
    HTTPServer(("0.0.0.0", port), Handler).serve_forever()
