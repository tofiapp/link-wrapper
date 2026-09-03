# Audit aplikace PSST Data (Link Wrapper)

Obálka nad WebView. Přihlášení, karty, VPN a certifikáty řeší Android.
Grafy, tabulky a zbytek UI kreslí stránka `HSI.Psst.Data` uvnitř WebView.

Prohlídka kódu bez znalosti Kotlinu (mapa souborů, proč WebView / NTLM
probe / pinning CA): [`VYSVETLENI.md`](VYSVETLENI.md).
Bezpečnost uložených údajů: [`BEZPECNOST.md`](BEZPECNOST.md).

---

## A. Přihlášení a odhlášení (opraveno)

Přihlášení **nebyla brána do home**. Byla to hromada oprav kolem HTTP 401.
Aplikace nejdřív otevřela stránku a teprve když server řekl 401, zeptala
se na heslo.

### Co bylo špatně

1. **Home bez relace.** Při VPN `onCreate` volalo `openInNewTab`. Stejně
   po návratu VPN. Formulář byl vedlejší efekt 401, ne podmínka vstupu.
2. **Údaje se neověřovaly.** Overlay se schoval před odpovědí serveru.
   Špatné heslo pustilo dovnitř. Pozdější pokus trestal každý 401 během
   NTLM handshake (NTLM má právě několik 401 kol).
3. **Relace neměla jedno místo pravdy.** Prefs, cookies, NTLM pool,
   `LoginGate`, `session_gate`, `EXTRA_LOGGED_OUT` a dalších ~8 příznaků.
   Checkbox „Zapamatovat“ relaci jen mátl.
4. **Odhlášení nesmazalo NTLM.** Chromium drží session v procesu.
   `clearHttpAuthUsernamePassword()` nestačí. Heslo se mazalo `apply()`
   (asynchronně). `allowBackup=true` mohlo heslo vrátit ze zálohy.
5. **Zpět obešel bránu.** Overlay z 401 šlo zrušit a zůstat na
   neautorizované stránce.
6. **Mrtvý kód.** `HomeActivity`, starý HTTP dialog, checkbox, drawable
   z domácí obrazovky. README popisovalo chování, které už neplatilo.

### Co platí teď

| stav | co vidí uživatel |
| --- | --- |
| není VPN | jen AnyConnect |
| VPN, není relace | formulář; home se nenačítá |
| VPN, ověřují se údaje | formulář zůstane; ověření běží v odděleném procesu |
| VPN, špatné heslo | hláška na formuláři, jméno i heslo zůstanou; lze hned opravit |
| VPN, relace platí | home; HTTP auth z `Session` |
| Odhlásit | prefs + cookies + Chromium profil pryč, nový proces |

Špatné heslo se **do hlavního WebView nedostane**. Ověření běží v procesu
`:authprobe` s vlastním datovým adresářem. Po výsledku se ten proces zabije.
Další pokus je znovu čistý Chromium — správné heslo po překlepu funguje.

Restart hlavního procesu po špatném hesle nestačil: `singleTask` + NTLM
v Chromiu přežily. Proto je ověření mimo hlavní proces.

---

## B. Sekání při grafech (opraveno v obálce)

Grafy kreslí stránka (typicky canvas / Highcharts) ve WebView. Obálka
jim v tom aktivně škodila. Na tabletu to vypadá jako „aplikace se seka“.

### 1. Progress bar měnil výšku WebView

Ukazatel postupu byl **řádek v LinearLayout** nad WebView. Při načtení
grafu naskočil (VISIBLE), WebView se zmenšilo o 2 dp, graf přepočítal
layout. Na 100 % zmizel (GONE), WebView se zvětšilo, graf znovu
přepočítal. U animovaného grafu to je trhavé škubání.

**Úprava:** progress je overlay přes WebView. Velikost plochy se nemění.

### 2. Layout listener běžel při každém snímku grafu

`OnGlobalLayoutListener` na kořeni měřil klávesnici **při každém layoutu**.
WebView s grafem layoutuje pořád. Callback dělal `getLocationOnScreen`,
`getWindowVisibleDisplayFrame` a občas `setPadding` → další layout.

**Úprava:** listener se spouští jen na přihlášení, ne na home.

### 3. Lišta karet se skládala znova a znova

`onPageFinished` a `onReceivedTitle` vždy zničily a nafoukly všechny
chipy karet. Stránka grafu často nastaví `document.title` na „graf“ a
při iframe/hashe to volá dokola. Inflate + `smoothScrollTo` na UI vlákně
během kreslení canvasu = sekání.

**Úprava:** název karty je jen z URL (`Home` / `dmId`). Lišta se překreslí,
jen když se ten popisek opravdu změní.

### 4. Skryté karty pořád kreslily

Osm karet = osm WebView. `View.GONE` je neschová před Chromiem — JS a
canvas na pozadí běží. Otevřete home, pak graf v další kartě, a tablet
kreslí obojí.

Chybělo i `WebView.onPause()` / `onResume()` a `pauseTimers()` když
uživatel přepne pryč z aplikace.

**Úprava:** neaktivní karta `onPause()`, aktivní `onResume()`. Při odchodu
z aplikace se zastaví timery. Po návratu VPN se aktivní karta zase spustí.
Skrytá karta má `RENDERER_PRIORITY_WAIVED`, aktivní `IMPORTANT`.

### 5. Hardware vrstva kolem WebView (vráceno)

Předchozí kolo zapnulo `LAYER_TYPE_HARDWARE` a `offscreenPreRaster`. To
pomáhá u statického obsahu. U grafu, který se při posunu pořád kreslí,
Android pokaždé nahraje celou texturu WebView na GPU. Chromium přitom má
vlastní compositor — obalová vrstva mu škodí.

**Úprava:** `LAYER_TYPE_NONE`, vypnutý `offscreenPreRaster`, vypnutý
WebView zoom (ať se nepere s posunem grafu), vypnuté force-dark.

### 6. Tablet kreslí 4–9× víc pixelů než PC

Na PC je `devicePixelRatio` ~1. Na tabletu 2–3. Highcharts podle toho
násobí canvas. Posun pak překresluje obří bitmapu — na PC plynulé,
ve WebView cukavé. Obálka před skripty stránky stropne DPR na 1.25
a vypne hover/tooltip při tažení. **Nepřekresluje** existující grafy
(`update`/`redraw` by posun rozbilo).

Skrytá karta se vyjme z view hierarchy. Layout listener klávesnice
na home vůbec neběží.

### 7. Další tření na UI vlákně

- `setProgressCompat(..., true)` animoval každý procento načtení.
- `dispatchTouchEvent` procházel celý strom (včetně WebView) při každém
  klepnutí, i na home.
- Padding kvůli klávesnici se v BROWSER režimu už neaplikuje.

**Úprava:** progress bez animace, overlay; procházení stromu jen na
přihlášení; IME padding jen na přihlášení.

### Co obálka neovlivní

Těžký graf (desítky tisíc bodů, live refresh) může na slabém tabletu
cukat i po těchto úpravách — backing store je menší, ale JS pořád běží
na jednom vlákně. Další zrychlení (méně bodů, Highcharts boost) je
změna `HSI.Psst.Data`.

---

## C. Další zjištění

### Bezpečnost

| věc | stav |
| --- | --- |
| Heslo na disku | **šifrované** AES-256-GCM, klíč v Android Keystore. Starý plaintext se při spuštění smaže. Viz [`BEZPECNOST.md`](BEZPECNOST.md). |
| Heslo v Intentu (probe) | pryč — šifrovaný soubor v `no_backup`, po čtení skartace |
| HTTP auth na cizí host | jen `psst.tudc.cz` / `test.psst.tudc.cz` |
| `allowFileAccess` / access z `file://` | vypnuto (dřív default zapnutý) |
| `usesCleartextTraffic` | false |
| Catch-all intent filtr `http/https` | záměr („Otevřít pomocí“); **heslo se na cizí host neposílá** |
| Exportovaná activity `singleTask` | nutná pro odkazy; zkontrolovat, že bez relace nejde na home (teď brána) |
| JS v WebView | nutný pro PSST; souborový přístup je vypnutý |

### Chování karet a odkazů

- Max. 8 WebView pořád žere paměť. Pozastavení skrytých karet pomůže CPU/GPU,
  ne RAM. Na 2 GB tabletu může Android karty zabít — po návratu se
  nenačtou z `onSaveInstanceState` (stav karet se neukládá).
- Není `shouldOverrideUrlLoading` pro `mailto:` / `tel:` — takové odkazy
  se nenačtou (`https` / `about` jen). `intent:` a `file:` jsou zablokované.
- `setOnLongClickListener { true }` blokuje dlouhé stisknutí (kopírování
  odkazu / textu). Pravděpodobně záměr proti náhodnému menu.

### VPN a síť

- Detekce je `TRANSPORT_VPN` na `activeNetwork`. Některé Always-On VPN
  nebo split-tunnel se tváří jinak — falešné „VPN není připojená“.
- Callback sítě volá bránu po 350 ms. To je v pořádku; nesmí znovu
  otevírat home bez relace (už neotevírá).

### Certifikáty

- Řetězec SZT Root → Sub → server je v pořádku. Dialog „pokračovat i tak“
  není a nemá být.
- Ověření běží na UI vlákně v `onReceivedSslError`. Je to krátké a
  řetězec je v cache. Není to příčina sekání grafů.

### Odhlášení

- Restart procesu (`killProcess`) je u NTLM nutný po **Odhlásit**. Špatné
  heslo se ověřuje v procesu `:authprobe` a ten se po pokusu zabije — hlavní
  Chromium špatné údaje nikdy nedostane.
- Stav karet se po Odhlásit záměrně zahazuje.

### Build / údržba

- Keystore je v repu (soukromý). Aktualizace na tabletu díky tomu fungují.
- CI staví APK. V tomto prostředí nejde spustit tablet s VPN a firemní CA.
- `values-night` existuje, ale appka je světlá. Na tabletu v tmavém režimu
  systému to může vypadat divně jen u systémových dialogů.

---

## D. Co by stálo za další kolo (není v tomto PR)

1. `mailto:` / `tel:` poslat do systému.
2. Ukládat seznam karet do prefs a po zabití procesu je obnovit (URL,
   ne DOM) — jen pokud relace platí.
3. Volitelně zúžit catch-all `https` filtr, pokud vadí, že se appka nabízí
   u veřejného webu (heslo se tam už neposílá).
4. Pokud grafy cukají i po těchto úpravách: měřit ve stránce (počet bodů,
   refresh, SVG vs canvas, Highcharts boost). To už je změna `HSI.Psst.Data`,
   ne obálky.
