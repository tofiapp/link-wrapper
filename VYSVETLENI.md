# Link Wrapper — co ten kód dělá (pro člověka bez Kotlinu)

Tenhle text je kompletní prohlídka projektu. Nepředpokládá, že umíte programovat.
Po dočtení by mělo být jasné: **co appka je, kudy teče uživatel, co který soubor
dělá, a proč je tohle řešení a ne Chrome / běžné přihlášení / „prostě to
ignoruj“ u certifikátů.**

Technický seznam oprav a **aktuální** tok (obě APK: Domů bez loginu,
PSST až po dlaždici; karty; VPN; HTTPS podle CA na tabletu)
je v [`AUDIT.md`](AUDIT.md). Některé starší odstavce níž (pinning CA v APK)
už neplatí — když se liší, platí AUDIT a README.
Bezpečnost uložených údajů: [`BEZPECNOST.md`](BEZPECNOST.md).
Návod na instalaci APK je v [`README.md`](README.md).

---

## 1. Co to vlastně je

Aplikace **PSST Data** (v kódu `Link Wrapper`) **není** samotný systém
`HSI.Psst.Data`. Je to **schválená obálka** kolem vestavěného prohlížeče
Androidu (WebView = Chromium, stejný motor jako Chrome, ale bez adresního
řádku Googlu).

Uvnitř obálky běží firemní web. Obálka řeší jen to, co běžný prohlížeč na
tabletu neumí nebo nesmí:

| problém tabletu | co obálka dodá |
| --- | --- |
| Chrome hlásí `NET::ERR_CERT_AUTHORITY_INVALID` | CA musí být na tabletu (Intune); appka spojení sama nepřekročí |
| Windows přihlášení (NTLM) v Chrome skoro nejde | vlastní formulář + HTTP auth |
| Interní síť je jen za VPN | bez Cisco AnyConnect se web vůbec nenačte |
| Odkaz z Outlooku má otevřít „ten správný“ prohlížeč | appka se nabídne v „Otevřít pomocí“ |
| Grafy na tabletu cukají | obálka kreslení zjemní (DPI, karty na pozadí) |

**Grafy, tabulky, menu webu kreslí stránka.** Kotlin kód je jen rám: VPN,
přihlášení, karty, certifikáty, tlačítka nahoře.

Domovská adresa:

```
https://test.psst.tudc.cz/HSI.Psst.Data
```

### Tablety bez prohlížeče (Chrome / Samsung Internet / Firefox)

**Ano — appka žádný nainstalovaný prohlížeč nepotřebuje a nikam ho
neotevírá.** Stránku kreslí sama, vestavěným motorem Androidu (WebView).
V kódu není jediný příkaz „otevři to v Chrome“. `startActivity` se používá
jen na restart po Odhlásit a na systémové Nastavení (otevírání odkazů).

To ale neznamená „tablet může být úplně holý“. Musí zůstat tři věci:

| musí být na tabletu | proč |
| --- | --- |
| **Android System WebView** (systémová komponenta, ne ikona prohlížeče) | to je ten motor. Bez něj appka při otevření webu spadne. |
| **síť + Cisco AnyConnect** | home je interní HTTPS; bez VPN se nenačte. „Bez prohlížeče“ ≠ „bez internetu“. |
| **tahle APK** | samotná obálka |

WebView je součást Androidu, ne „prohlížeč na ploše“. Na většině firemních
tabletů ho IT nechává, i když Chrome smaže.

**Jedna past, na kterou si IT musí dát pozor:** na některých tabletech
s Google Play *je* poskytovatelem WebView právě Chrome. Když se Chrome
odinstaluje a zároveň se nevypne / nenainstaluje balíček
**Android System WebView**, WebView přestane existovat a appka spadne
ve chvíli, kdy má otevřít stránku (včetně neviditelného ověření hesla).

Kontrola na tabletu: Nastavení → Aplikace → **Android System WebView**
má být nainstalovaný a zapnutý. Pokud tam je jen Chrome jako „WebView
implementation“, Chrome nesmí zmizet, nebo se musí přepnout poskytovatel
na System WebView.

Outlook / e-mail na tabletu taky není nutný ke spuštění. Bez něj jen
nebude cesta „klepni na odkaz v mailu → otevři v appce“. Ikona PSST Data
stačí.

---

## 2. Kotlin za minutu (jen to, co v projektu potkáte)

Kotlin je jazyk Androidu. Čtete ho skoro jako věty.

| zápis | význam |
| --- | --- |
| `class WebViewActivity` | jedna obrazovka (tady skoro celá appka) |
| `object Session` | jedna „krabice“ na relaci — existuje jen jednou |
| `fun refreshGate()` | funkce = pojmenovaný návod „udělej tohle“ |
| `private` | ostatní soubory to nesmějí sahat |
| `override fun onCreate` | Android volá „obrazovka se právě otevřela“ |
| `companion object { const val MAX_TABS = 8 }` | konstanty patřící k třídě |
| `if / else` | větev: když platí A, udělej X, jinak Y |

Android skládá appku ze dvou vrstev:

- **XML** (`res/layout/…`) — *jak to vypadá* (tlačítka, barvy, rozložení)
- **Kotlin** (`java/…/*.kt`) — *co se stane*, když někdo klepne

Když v XML je `android:id="@+id/loginButton"`, Kotlin si to tlačítko najde
přes `findViewById(R.id.loginButton)` a pověsí na něj „při klepnutí zavolej
`submitLogin()`“.

---

## 3. Mapa projektu (co je v které složce)

```
link-wrapper/
├── README.md, AUDIT.md, VYSVETLENI.md   dokumentace
├── build.gradle / settings.gradle       „jak se appka sestaví“
├── gradle.properties                    verze 1.0 + paměť pro Gradle
├── keystore.properties + app/keystore/  podpis APK (stejný klíč = aktualizace)
├── .github/workflows/                   robot na GitHubu, který z kódu udělá APK
└── app/src/main/
    ├── AndroidManifest.xml              občanka appky (oprávnění, obrazovky, odkazy)
    ├── java/com/example/linkwrapper/    VEŠKERÁ logika (Kotlin)
    └── res/
        ├── layout/                      vzhled obrazovek
        ├── menu/                        nabídka ⋮
        ├── values/                      barvy, texty, styly
        ├── drawable/                    ikony (+, domeček, …)
        └── xml/                         network security (CA na tabletu)
```

Balíček se jmenuje `com.example.linkwrapper` — to je historický název z šablony.
Na tabletu se obě APK jmenují **Obálka**.

**Hlavní Kotlin soubory:**

| soubor | role jednou větou |
| --- | --- |
| `WebViewActivity.kt` | celá appka: brána, karty, menu, VPN, dialogy |
| `Session.kt` | uložené jméno a heslo + mazání po Odhlásit |
| `AuthProbeActivity.kt` | „zkus heslo v jiném procesu, ať nezkazí prohlížeč“ |
| `AuthHosts.kt` | komu smí jít HTTP auth (PSST vs celé tudc.cz) |
| `SslPolicy.kt` | HTTPS jen podle CA na tabletu; žádný pinning v APK |
| `ChartPerf.kt` | JavaScript, který Highcharts na tabletu zklidní |
| `DesktopSite.kt` | Chrome na Windows, viewport na šířku WebView, jde scrollovat |

Žádný druhý jazyk v appce není. XML je vzhled, YAML v `.github` je sestavení.

---

## 4. Cesta uživatele (co kód opravdu hlídá)

Všechno se rozhoduje v jedné funkci: `refreshGate()` v `WebViewActivity.kt`.
„Gate“ = brána. Tři stavy:

```
                    ┌─────────────────┐
                    │  Appka se otevře │
                    └────────┬────────┘
                             ▼
                    Je Cisco VPN zapnutá?
                     /                \
                   NE                  ANO
                   ▼                    ▼
            obrazovka              Běžná APK → Domů
         „VPN není připojená“      (PSST login až po dlaždici)

                                   Zkušební APK
                                   Máme uloženou relaci?
                                     /              \
                                   NE                ANO
                                   ▼                  ▼
                            přihlášení            karty + Domů
                            (údaje = tudc.cz)
```

**Obě APK:** Domů i bez přihlášení. Údaje jen pro PSST. DSD má vlastní formulář.
Dřív appka pinovala firemní CA v APK — to už není, viz `AUDIT.md`.

---

## 5. AndroidManifest.xml — občanka aplikace

Soubor říká systému: kdo jsme, co smíme, které obrazovky existují.

**Oprávnění**

- `INTERNET` — bez toho WebView nic nenačte
- `ACCESS_NETWORK_STATE` — abychom poznali VPN
- `ACCESS_FINE/COARSE_LOCATION` — jen když web zavolá `navigator.geolocation`
  (mapa). Nejdřív dialog v appce, pak systémové oprávnění.

**Vypnutá záloha** (`allowBackup="false"`) — ať se heslo z tabletu
nekopíruje do Google zálohy.

**Jen HTTPS** (`usesCleartextTraffic="false"`) — HTTP bez šifrování appka
odmítne.

**Dvě obrazovky (activity)**

1. `WebViewActivity` — to, co uživatel vidí. `LAUNCHER` = ikona na ploše.
   `singleTask` = odkaz z Outlooku nepřidá druhou kopii appky, ale přijde
   do té samé (`onNewIntent`).
2. `AuthProbeActivity` — neviditelná, **jiný proces** `:authprobe`.
   `exported="false"` = zvenku ji nikdo nespustí.

**Proč tři filtry na odkazy**

- Konkrétní hostitelé `psst.tudc.cz` / `test.psst.tudc.cz` — Android 12+
  obecné `https` filtry často přeskočí, proto jsou vypsaní zvlášť.
- Obecný `http`/`https` — appka se objeví v „Otevřít pomocí“ i u jiných
  adres (záměr: schválená obálka). Vedlejší efekt: nabídne se u *každého*
  webu, nejen PSST.
- `ACTION_SEND` text — když Outlook odkaz neotevře systémem, jde to přes
  Sdílet.

---

## 6. WebViewActivity.kt — hlavní (a skoro jediná) obrazovka

Soubor má okolo 1500 řádků. Je to celý životní cyklus tabletu. Níže po
blocích, v pořadí, jak se to děje.

### 6.1 Start (`onCreate`)

Android otevře obrazovku → kód:

1. natáhne XML vzhled (`activity_webview.xml`)
2. najde toolbar, karty, progress, přihlášení
3. zapne cookies
4. z intentu (klepnutí na ikonu / odkaz) vybere URL, jinak home
5. pokud uživatel právě dal Odhlásit, připraví banner „Byl jste odhlášen“
6. zavolá `refreshGate()`

### 6.2 VPN monitor

`isVpnActive()` se neptá „běží AnyConnect?“. Ptá se Androidu: má *právě
aktivní síť* příznak `TRANSPORT_VPN`? Pokud ano, bereme to jako „tunel je
nahoře“.

Callback sítě (`onAvailable` / `onLost`) nespouští bránu hned — počká
**350 ms**. VPN při přepínání chvilku „blike“. Bez prodlevy by se střídaly
obrazovky dokola.

Bez VPN: karty se pozastaví, URL se schovají do `savedTabUrls`. Až VPN
naskočí, karty se obnoví. Ve zkušební APK bez relace je zase formulář.

### 6.3 Přihlášení (`submitLogin` → `succeedLogin` / `failLogin`)

Formulář je overlay *přes* prohlížeč. Pod ním se web nenačítá.

1. Musí být VPN, jméno i heslo neprázdné.
2. Údaje jdou do `pendingCredentials` — **ještě ne do Session**.
3. Spustí se `AuthProbeActivity` (jiný proces). Hlavní WebView heslo
   v tuhle chvíli nevidí.
4. Probe do 14–15 s buď uspěje, nebo selže.
5. Úspěch → `Session.start` (uloží jméno+heslo) → `refreshGate()` → home.
6. Špatné heslo → hláška na formuláři, jméno i heslo zůstanou, lze hned
   opravit. Probe proces se zabije.

Tlačítko mezitím ukáže „Přihlašuji…“ a nejde zmáčknout znovu.

### 6.4 Karty prohlížeče

Každá karta je `BrowserTab`: vlastní WebView + název + URL. Maximum **8**.
Devátá se neotevře (toast). Externí odkaz při plném limitu přepíše aktivní
kartu.

Název karty **není** `document.title` z webu. Graf by pořád přepisoval
titulek na „graf“ a lišta by se skládala dokola (sekání). Místo toho
`Destinations.tabTitle`: Domů / PSST Data / DSD / **Graf {dmId}** u grafu
ze sdílení, jinak název serveru.

Neaktivní karta se **vyjme z obrazovky** (`removeView`) a dostane
`onPause()` + `RENDERER_PRIORITY_WAIVED`. `View.GONE` nestačí — Chromium by
dál kreslilo canvas na pozadí a žralo tablet.

Křížek poslední karty neukončí appku — otevře znovu home.

### 6.5 WebView nastavení (proč tyhle přepínače)

| nastavení | proč |
| --- | --- |
| `javaScriptEnabled = true` | bez JS PSST vůbec neběží |
| `allowFileAccess = false` | ať stránka nesahá na soubory tabletu |
| `setSupportZoom(false)` | pinch-zoom by se pral s posunem grafu |
| desktopový UA + `width=device-width` | desktopové menu, stránka vyplní WebView a jde posouvat |
| `LAYER_TYPE_NONE` | hardware vrstva kolem WebView při posunu nahrává celou texturu na GPU → cukání |
| `safeBrowsingEnabled = false` | Google Safe Browsing u interního webu jen překáží |
| `forceDark` vypnutý | ať Android web nepřekresluje na tmu |
| dlouhý stisk zablokovaný | žádné „kopírovat odkaz“ náhodou prstem |
| cookies včetně third-party | firemní SSO/NTLM je na nich často závislé |

Když server pošle **HTTP 401** (chci jméno a heslo), `onReceivedHttpAuthRequest`
doplní údaje z `Session`. NTLM má *několik kol* 401 za sebou — to není
„špatné heslo“, to je handshake. Proto je strop `MAX_AUTH_ROUNDS = 16`.
Až po vyčerpání kol se to bere jako odmítnutí.

SSL chybu WebView **vždy zruší** (`SslPolicy`). Ověření nechává Android
podle CA na tabletu. Tlačítko „pokračovat i tak“ v dialogu **není**.

### 6.6 Klávesnice (IME)

Na přihlášení musí formulář vyjet nad klávesnici. Na home **nesmí** — měření
klávesnice při každém snímku grafu seká UI (graf pořád layoutuje). Proto
se `OnGlobalLayoutListener` na home vypíná.

Klepnutí mimo pole na přihlášení klávesnici schová. Tlačítko Zpět nejdřív
schová klávesnici, pak v prohlížeči jde zpět v historii, pak zavře kartu,
pak teprve opustí appku. Na přihlášení Zpět **neobejde bránu**.

### 6.7 Menu ⋮

XML: `menu_webview.xml`. Vpravo viditelně **+** (nová karta Domů, aktuální
web zůstane) a **domeček** (aktuální karta se změní na Domů). V ⋮:

- zadat URL
- přenačíst
- návod na „otevírání odkazů“ v nastavení Androidu
- **Vymazat údaje** (červeně, dole)

Na VPN/login obrazovce menu nic kromě Odhlásit neotevře.

### 6.8 Odhlásit (`performLogout`)

Nestačí smazat políčko v nastavení. Chromium drží NTLM relaci **v procesu**.
Proto:

1. `Session.end` — pryč jméno, heslo, staré prefs
2. `wipeBrowser` — cookies, HTTP auth cache, cache WebView
3. smazat složku `app_webview` (Chromium profil)
4. `killProcess` — celý proces umře a startuje znova čistý

Bez kroku 4 by další uživatel (nebo totéž špatné heslo) pořád „byl
přihlášený“.

---

## 7. Session.kt — kde leží jméno a heslo

`object Session` = jedna relace pro celou appku.

Na disku **není čitelné heslo**. Ukládá se jako AES-256-GCM (klíč v
Android Keystore, neopustí tablet). Zápis přes `.commit()` (hned).
Když Keystore selže, relace zůstane jen v RAM — po vypnutí appky je
potřeba přihlášení znovu.

`isActive` = údaje jdou dešifrovat. Relace **neexpirovává sama**. Platí,
dokud někdo nestiskne Odhlásit.

Při prvním spuštění nové verze se starý plaintext z prefs přežene do
šifry a smaže. Totéž pro ještě starší `http_auth_prefs`.

Podrobnosti a hrozby: [`BEZPECNOST.md`](BEZPECNOST.md).

---

## 8. AuthProbeActivity.kt — proč ověření v jiném procesu

Představte si Chromium jako číšníka, který si pamatuje „tohle je Novákovo
heslo“ u stolu. Když jednou dostane špatné heslo, u *toho samého stolu* ho
pořád posílá dál. Restart obrazovky nestačí — stůl (proces) žije.

Proto je v manifestu:

```
android:process=":authprobe"
```

To je **druhý proces**, vlastní WebView, vlastní datová složka
(`WebView.setDataDirectorySuffix("authprobe")`). Probe:

1. neviditelně načte cílovou URL
2. na 401 podstrčí zadané údaje
3. počká ~700 ms „usazení“ (NTLM má víc kol 401; první 401 ≠ konec)
4. vrátí OK nebo chybu
5. proces se zabije (`AuthProbe.kill`)

Hlavní prohlížeč špatné heslo **nikdy nedostane**. Další pokus je znovu
čistý Chromium. Údaje do probe nejdou Intentem (to by skončilo v logu),
ale šifrovaným souborem, který se po přečtení smaže.

Timeout 14 s (probe) / 15 s (hlavní obrazovka), max 16 auth kol — stejná
logika jako v hlavním WebView.

---

## 9. SslPolicy.kt — HTTPS podle tabletu

Firemní CA už **nejsou v APK**. Obě verze (běžná i zkušební) věří jen
tomu, čemu věří tablet: systémové CA a certifikáty, které nainstalovalo
IT / uživatel (Intune → trusted certificate profile).

`SslPolicy.handleSslError` spojení **vždy zruší**. Dialog „pokračovat i
tak“ není. Když tablet autoritě nedůvěřuje, stránka se nenačte a u
přihlášení svítí banner „Chybí certifikáty“.

Starý pinning (`CertPinning.kt`, `corporate_ca.pem`) je smazaný. Bez CA
na tabletu weby `tudc.cz` nepůjdou.

---

## 10. ChartPerf.kt — proč JavaScript uvnitř Kotlinu

Grafy kreslí **Highcharts na stránce**, ne Kotlin. Obálka stránce jen
před startem pošeptá jiná pravidla.

Na PC je `devicePixelRatio` ~1. Na tabletu 2–3. Highcharts podle toho
násobí canvas. Posun grafu pak překresluje **4–9× víc pixelů**. Proto
bootstrap **před** skripty stránky stropne DPR na **1.25** (kompromis
ostrost / plynulost).

Dál vypne:

- animace grafu
- tooltip, který jezdí s prstem
- hover stav řad (na dotyku stejně nedává smysl)

**Záměrně nevolá** `chart.update()` / `redraw()` — to by při posunu graf
znovu složilo a cukalo ještě víc.

Vstříkne se přes `addDocumentStartJavaScript` (nejdřív, než stránka
běží). Starší WebView to umí až v `onPageStarted` — první canvas pak
může ještě chvilku kreslit naplno.

Těžký graf (desítky tisíc bodů, live refresh) může cukat i tak. To už
je změna `HSI.Psst.Data`, ne obálky.

---

## 11. Vzhled (XML) — co vidíte, aniž by to bylo „web“

| soubor | co to je |
| --- | --- |
| `activity_webview.xml` | nahoře lišta karet + +, domeček, ⋮; pod tím WebView; progress je *přes* web, ne nad ním (jinak se mění výška a graf seká) |
| `layout_login_screen.xml` | overlay: formulář, banner odhlášení, nebo jen VPN hláška |
| `item_browser_tab.xml` | jedna „pilulka“ karty + křížek |
| `dialog_open_url.xml` | pole pro ruční URL |
| `dialog_confirm_logout.xml` | „Odhlásit se?“ |
| `menu_webview.xml` | položky nabídky |
| `values/colors.xml` | paleta (modrá `#1F659F`, červená odhlášení) |
| `values-night/` | tmavé barvy, když má tablet noční režim — chrome appky ztmavne, *obsah webu* ne (force-dark je vypnutý) |

Ikona je zatím placeholder (modrý čtverec s odkazem).

---

## 12. Sestavení a verze — proč Gradle a GitHub Actions

**Gradle** (`build.gradle`) je kuchařka: „vezmi Kotlin, Android SDK 34,
podepiš klíčem, vyjeď APK“. Minimální Android je **8.0** (`minSdk 26`) —
starší tablety odpadají záměrně (VPN API, WebView).

**Proč podpisový klíč v soukromém repu:** Android aktualizaci nainstaluje,
jen když nová APK má **stejný podpis** jako stará. Kdyby CI pokaždé
podepsalo jiným klíčem, na tabletu by to nešlo přepsat.

Číslování:

- `1.0` ručně v `gradle.properties`
- třetí číslo = pořadí běhu workflow **Build APK** na `main`
- `versionCode` musí růst, jinak tablet novou APK odmítne
- PR dostane `1.0.0-pr20` a `versionCode 1` — to **není** ostrá verze

Dva workflow:

- `ci.yml` — „jde to vůbec sestavit?“ na každém PR
- `build.yml` — ostré APK + GitHub Release jen z `main`

Řetězec `Verze %1$s` v `strings.xml` se v layoutu **nikde nepoužívá**
(README říká, že verze je dole na úvodní obrazovce — v aktuálním XML
tam není). Drobná neshoda dokumentace a kódu.

---

## 13. Proč tohle a ne tamto (rozhodnutí narychlo)

**Proč WebView a ne Chrome?**
Chrome na tabletu nevěří firemní CA, neumí tenhle NTLM tok a nejde mu
přikázat „bez VPN ani náhodou“. Obálka je schválený kanál. Na tabletech
bez ikony prohlížeče to proto **funguje** — dokud zůstane systémové
WebView (viz § 1, tablety bez prohlížeče).

**Proč ne Windows přihlášení jako na PC?**
Android nemá Kerberos/Negotiate jako firemní notebook. IIS musí pustit
NTLM (nebo Basic přes HTTPS). Appka Kerberos sama nedodělá.

**Proč heslo šifrované Keystore a ne EncryptedSharedPreferences?**
Ta knihovna na některých tabletech padá. AES-GCM přímo v Android
Keystore je v systému od API 23 (appka chce 26). Klíč neopustí hardware.

**Proč zabíjet proces při Odhlásit?**
NTLM žije v Chromiu v RAM procesu. API `clearHttpAuthUsernamePassword()`
to nestačí. Zabití procesu je hrubé a spolehlivé.

**Proč ověření hesla mimo hlavní WebView?**
Špatné NTLM údaje v hlavním procesu zablokují i další správný pokus.
Oddělený proces = čistý stůl na každý pokus.

**Proč catch-all `https` filtr?**
Ať jde otevřít i jiný interní web ve stejné obálce. Cena: appka se nabízí
u veřejných odkazů. Jde zúžit, pokud to vadí.

**Proč max 8 karet?**
Každá karta = celé Chromium v paměti. Pozastavení šetří CPU/GPU, ne RAM.
Na 2 GB tabletu Android karty klidně zabije; seznam karet se do prefs
neukládá (po zabití procesu jsou pryč).

**Proč `singleTask`?**
Jeden „úkol“ v recents. Odkaz z mailu přijde do běžící appky, ne otevře
druhou.

**Proč JavaScript v ChartPerf a ne úprava webu?**
Obálka nesmí sahat do `HSI.Psst.Data`. Umí jen změnit prostředí *před*
tím, než Highcharts nastartuje.

---

## 14. Co appka záměrně nedělá

- Není to prohlížeč s historií, záložkami v cloudu, rozšířeními.
- `mailto:` / `tel:` / `intent:` nenechává systému — odkaz skončí ve
  WebView nebo selže.
- Neukládá seznam karet na disk. Po zabití procesu (málo RAM, Odhlásit)
  jste znovu na home.
- Neřeší obsah webu (oprávnění v PSST, data grafů). To je server.
- Kerberos/Negotiate typicky nefunguje.
- VPN detekce může selhat u některých Always-On / split-tunnel režimů
  (Android nehlásí `TRANSPORT_VPN`).

---

## 15. Bezpečnost — stručně na jednu stránku

| téma | stav |
| --- | --- |
| Heslo v plaintext prefs | ne — AES-GCM, klíč v Keystore |
| Heslo na cizí server | jen PSST (`psst.tudc.cz` / `test.psst.tudc.cz`) |
| Pokračovat přes špatný certifikát | ne |
| HTTP bez TLS | ne |
| Přístup na `file://` | vypnutý |
| JS ve WebView | nutný; souborový přístup vypnutý |
| Probe activity zvenku | ne (`exported=false`) |
| Hlavní activity zvenku | ano (odkazy); bez relace na PSST se ukáže formulář |
| Keystore v soukromém gitu | ano, kvůli aktualizacím; heslo k úložišti je v `keystore.properties` |

---

## 16. Jak číst kód, když budete chtít později

Začněte v tomto pořadí:

1. `AndroidManifest.xml` — co systém o appce ví
2. `refreshGate()` / `presentLogin()` / `presentBrowser()` / `enterVpnGate()`
3. `submitLogin()` → `AuthProbeActivity` → `succeedLogin()`
4. `Session.kt` celé (je krátké)
5. `createWebView()` — nastavení + SSL + HTTP auth
6. `SslPolicy.handleSslError` / `AuthHosts.allows`
7. `ChartPerf.BOOTSTRAP_JS` jen komentář nahoře; JS je „šeptání Highcharts“
8. `performLogout()` nakonec — ukáže, co všechno relace znamená

Když v kódu uvidíte `@SuppressLint("SetJavaScriptEnabled")`, není to
schovaná chyba. Android Studio křičí „JS ve WebView je nebezpečné“ —
tady je nutný, proto je varování potlačené a zároveň je vypnutý přístup
k souborům.

---

## 17. Shrnutí jednou větou

**Kotlin tady není „program PSST“.** Je to vrátný: pustí vás jen s VPN
(u zkušební i s přihlášením), otevře web ve WebView, které věří CA na
tabletu, drží až osm karet aniž by pozadí sežralo tablet, a při Vymazat
údaje smaže relaci tak důkladně, že umře i proces — protože jinak NTLM
v Chromiu přežije.
