# ⚡ El Nour Power

Application web de **dimensionnement énergétique domestique** pour la Tunisie.
À partir des appareils de la maison et de la météo locale sur 3 jours, elle
estime la consommation jour/nuit, dimensionne le stockage batterie pour
tenir une coupure de courant (48 h par défaut), et propose une source
d'énergie (solaire + secours gaz) avec budget en dinars tunisiens.

> Backend **Kotlin / Spring Boot**, embarqué dans **Docker**, accessible via
> navigateur sur `http://localhost:8080`. Design web clean et minimaliste.

---

## 🚀 Lancer l'application

```bash
# Build + démarrage (un seul conteneur)
docker compose up -d --build

# Vérifier que ça tourne
curl http://localhost:8080/api/health
# → {"status":"UP","app":"el-nour-power", ...}
```

Puis ouvrir **http://localhost:8080** dans le navigateur.

**Au premier accès : écran de connexion.** Crée un compte (email + mot de passe ≥ 6 caractères) puis connecte-toi. Chaque utilisateur a son **espace privé** (appareils, recommandations, clients, RDV) — les données sont isolées par compte.

> 🔐 Variable d'environnement `JWT_SECRET` (base64) pour changer la clé de signature des tokens (72h de validité par défaut). Mots de passe hashés en BCrypt.

Arrêt : `docker compose down`

---

## 🧠 Ce que fait l'application

| Étape | Détail |
|------|--------|
| **1. Inventaire (sélecteur visuel)** | L'utilisateur sélectionne ses appareils via un **sélecteur par catégories** (Climatisation, Cuisine, Froid, Lavage, Éclairage, Électronique, Eau…) avec **recherche**. Pour chaque appareil : quantité réglable, **3 liens directs vers Alibaba / AliExpress / STEG** pour identifier le modèle exact, et un champ **« Puissance réelle identifiée »** pour saisir la vraie valeur trouvée sur le site → le calcul électrique l'utilise (calcul précis plutôt qu'estimé). L'inventaire est **sauvegardé** par compte. |
| **2. Météo 3 jours** | Récupérée en live via l'API publique **Open-Meteo** (gratuite, sans clé). Géocodage de n'importe quelle ville. |
| **3. Estimation conso** | Watts plaqués × facteur de marche × heures/jour. **Boost thermique** sur les clim/frigo selon la température réelle. Séparation **jour / nuit**. |
| **4. Prises connectées** | Option: lectures simulées (Tapo/Tuya/Shelly-like) qui remplacent l'estimation par la mesure réelle. |
| **5. Batterie** | Choix automatique du modèle le plus économique (Tesla Powerwall 3, Pylontech, Huawei LUNA, Enphase, BYD, Zendure) pour couvrir la coupure. |
| **6. Source d'énergie** | Kit solaire dimensionné + groupe électrogène gaz en secours, en achat **ou** en location, avec maintenance annuelle. |
| **7. STEG & PROSOL** | Estimation de la **facture STEG** actuelle (paliers progressifs réels 2026) vs après solaire, et calcul de l'**aide PROSOL ELEC** (subvention ANME 30%, prime 1200 DT/kW plafonnée à 3000 DT, crédit STEG 7 ans) + temps de retour sur investissement. |
| **8. Gestion CRM** | Enregistrement de **clients**, **partenaires** (installateurs/fournisseurs/distributeurs) et **rendez-vous** (liés client + partenaire, type, statut). Données persistées en base. |

---

## 🔌 API REST

### Auth
| Méthode | URL | Description |
|--------|-----|-------------|
| `POST` | `/api/auth/register` | Créer un compte (email, nom, password) |
| `POST` | `/api/auth/login` | Connexion → renvoie un JWT |
| `GET`  | `/api/auth/me` | Profil utilisateur (token requis) |

### Énergie
| Méthode | URL | Description |
|--------|-----|-------------|
| `GET`  | `/api/health` | Santé du service |
| `GET`  | `/api/appliances` | Catalogue appareils |
| `GET`  | `/api/appliances/links` | Liens produits (Alibaba/AliExpress/STEG) par appareil |
| `GET`  | `/api/batteries` | Catalogue batteries |
| `GET`  | `/api/sources` | Catalogue sources d'énergie |
| `GET`  | `/api/plugs` | Lectures prises connectées (mock) |
| `GET`  | `/api/weather?city=Tunis` | Météo 3 jours |
| `GET`  | `/api/steg/bill?monthlyKwh=300` | Facture STEG estimée |
| `GET`  | `/api/steg/prosol?installedKw=5&installCostTnd=14000` | Aide PROSOL ELEC |
| `GET`  | `/api/steg/links` | Liens officiels STEG/ANME |
| `POST` | `/api/recommend` | **Recommandation complète** (accepte `selections` avec `overridePowerWatts`) |

### Inventaire utilisateur (token requis)
| Méthode | URL | Description |
|--------|-----|-------------|
| `GET/POST` | `/api/my-appliances` | Lister / ajouter un appareil à mon inventaire |
| `PUT/DELETE` | `/api/my-appliances/{id}` | Modifier / supprimer |
| `DELETE` | `/api/my-appliances` | Vider l'inventaire |

### CRM (clients / partenaires / RDV)
| Méthode | URL | Description |
|--------|-----|-------------|
| `GET/POST` | `/api/clients` | Lister / créer un client |
| `PUT/DELETE` | `/api/clients/{id}` | Modifier / supprimer |
| `GET/POST` | `/api/partners` | Lister / créer un partenaire |
| `PUT/DELETE` | `/api/partners/{id}` | Modifier / supprimer |
| `GET/POST` | `/api/appointments` | Lister / créer un RDV |
| `GET` | `/api/appointments/upcoming` | Prochains RDV |

### Exemple `/api/recommend`

```bash
curl -X POST http://localhost:8080/api/recommend \
  -H "Content-Type: application/json" \
  -d '{
    "city": "Tunis",
    "outageHours": 48,
    "applianceIds": ["clim_18000","frigo_a","lave_linge","four_elec","tv_led","box_internet","eclairage_led","chauffe_eau","ordinateur"],
    "useSmartPlugs": false,
    "preferRent": false
  }'
```

Réponse (extrait) :
```json
{
  "profile": { "city": "Tunis", "averageDailyKwh": 27.3, "dayKwhAvg": 24.5, "nightKwhAvg": 2.8, "peakPowerW": 11677 },
  "battery": { "battery": { "brand": "Huawei", "model": "LUNA2000-10" }, "count": 7, "totalUsableKwh": 70.0, "autonomyHours": 61.5, "coversOutage": true },
  "powerSources": [
    { "source": { "name": "Kit solaire 8 kWc + onduleur hybride" }, "units": 2 },
    { "source": { "name": "Groupe électrogène gaz 5 kW portatif" }, "units": 1 }
  ],
  "totalInvestmentTnd": 168200
}
```

---

## 🗂️ Structure du projet

```
el-nour-power/
├── build.gradle.kts              # Kotlin + Spring Boot 3.3 + JDK 21
├── Dockerfile                    # Build multi-stage (gradle → JRE légère)
├── docker-compose.yml            # Un seul service, port 8080
└── src/main/
    ├── kotlin/com/elnourpower/
    │   ├── ElNourPowerApplication.kt
    │   ├── config/               # AppProperties, RestConfig
    │   ├── model/                # Appliance, Weather, PowerNeed, Battery, PowerSource, Recommendation…
    │   ├── repository/           # Catalogues (appareils, batteries, sources — prix TND)
    │   ├── service/              # WeatherService, ConsumptionService, RecommendationService, SmartPlugService
    │   └── controller/           # EnergyController (REST), PageController (web)
    └── resources/
        ├── application.yml
        └── static/               # index.html, style.css, app.js (SPA vanilla)
```

---

## 📊 Sources des données (2026)

- **Météo** : [Open-Meteo](https://open-meteo.com) (gratuit, sans clé API).
- **Batteries** : Selectra, Hellowatt, Haisic Storage, EDF Solutions Solaires, MonKitSolaire.
- **Prix solaire Tunisie** : Protunisie, Gamco Energy, Gigavolt Energy, STES (~2 900 DT/kWc posé).
- **Groupes électrogènes** : EPST Tunisie.

---

## 🛠️ Stack technique

- **Kotlin 1.9** + **Spring Boot 3.3** + **JDK 21**
- **Gradle 8.8** (multi-stage Docker)
- **Frontend** : HTML/CSS/JS vanilla (zéro framework, zéro build)
- **Pas de base de données** : tout en mémoire, stateless
