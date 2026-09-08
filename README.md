# Link Wrapper

Jednoduchá Android appka — "schválená obálka" pro otevírání odkazů, které
firemní síť/VPN vyžaduje otevřít mimo běžný prohlížeč.

## Co appka umí

- Na tabletu se appka jmenuje **Obálka** (běžná i zkušební APK).
- Po spuštění (a po VPN) nativní **Domů**.
  PSST má aplikační přihlášení (NTLM, jen `psst.tudc.cz` /
  `test.psst.tudc.cz`).
- Údaje se uloží v aplikaci, dokud je v ⋮ nesmažete.
  **Zkušební APK:** weby si cookies a NTLM mezi sebou nepředávají
  (každý host má vlastní zásobník). V ⋮ je **Neukládat přihlášení**
  (výchozí zapnuto) — jméno a heslo k PSST zůstanou jen do vypnutí
  aplikace.
- **Vymazat údaje** (⋮ dole, červeně) smaže cookies, přihlášení k PSST i
  relace na otevřených stránkách. Pak jste na webu odhlášení.
- **Karty** nahoře v liště. **Domeček** změní aktuální kartu (PSST,
  graf) na **Domů**. **+** otevře novou kartu **Domů**, aktuální web zůstane.
  Křížek je jen když je karet víc. Dlaždice na Domů má jen název
  (PSST Data), bez adresy.
  Zavření karty křížkem (max. 8). Neaktivní karta se pozastaví, ať grafy
  na pozadí nesežerou tablet. Dlouhé podržení karty: **připnout**, otevřít
  na druhé kartě, kopírovat adresu. Ikona složky v liště ukládá grafy
  na Domů. PSST Data na šířku: dvě karty vedle sebe, třetí pod nimi,
  vyplní zbytek displeje (i v běžné APK).
  Přiblížení webu: výchozí na výšku
  88 %, na šířku 80 %; v ⋮ → **Velikost stránek** pro PSST Data
  (drží se do Vymazat údaje). Grafy (`dmId`) mají vždy 84 %
  a karta ze sdílení se jmenuje **Graf …** podle `dmId`.
  Posun grafu: WebView bez hardware vrstvy,
  stropnuté DPI (tablet jinak kreslí 4× víc pixelů než PC) a vypnutý
  hover/tooltip při tažení.
- Nabídka **⋮**: zadat adresu, přenačíst, velikost stránek, nastavení odkazů.
- Objeví se jako volba v "Otevřít pomocí" (včetně `psst.tudc.cz` /
  `test.psst.tudc.cz`). Externí odkaz otevře **novou kartu**.
- **VPN brána**: bez Cisco AnyConnect hláška přes celou obrazovku — karty
  ani lišta nejdou použít. Po připojení se vrátí Domů nebo otevřené karty.
- **Poloha**: dialog a systémové oprávnění pro `navigator.geolocation`.
- **Bez prohlížeče na tabletu**: Chrome ani jiný prohlížeč appka
  nepotřebuje a neotevírá. Musí zůstat systémové **Android System WebView**
  (někde ho dodává Chrome — ten pak nesmí zmizet) a **Cisco AnyConnect**.
  Podrobněji v [`VYSVETLENI.md`](VYSVETLENI.md).

## Jak appku dostat jako .apk (bez Android Studia)

1. Založ si nový repozitář na GitHubu (klidně soukromý).
2. Nahraj do něj obsah tohoto projektu (celou tuhle složku).
3. Po pushnutí do větve `main` se automaticky spustí GitHub Actions workflow
   (`.github/workflows/build.yml`), který appku sestaví, očísluje a podepíše.
4. Stáhni APK jedním z těchto způsobů:
   - **Releases** (pohodlnější): v repozitáři záložka **Releases** → nejnovější
     verze → soubory `LinkWrapper-1.0.N.apk` (běžná) a
     `LinkWrapper-1.0.N-test.apk` (zkušební, stejné přihlášení).
   - **Actions**: záložka **Actions** → poslední běh → dole v sekci
     **Artifacts** najdeš `LinkWrapper-1.0.N` → stáhni zip, uvnitř je APK.
5. Ten `.apk` nahraj do tabletu (email sám sobě, Google Drive, USB…) a nainstaluj.
   Bude potřeba v nastavení tabletu povolit instalaci z "neznámých zdrojů"
   (protože appka nejde přes Google Play).

Build jde spustit i ručně: **Actions** → **Build APK** → **Run workflow**.

## Automatické číslování verzí

Číslo verze se zvedne **jen při merge do `main`** (a ručním **Run workflow**).
CI na pull requestu appku sestaví kvůli kontrole, ale **není to verze** —
na tabletu se z něj neaktualizuje.

| Co | Příklad | Kdy |
| --- | --- | --- |
| `versionName` | `1.0.15` | merge do `main` → GitHub Release, název APK |
| `versionCode` | `15` | totéž; musí růst, aby šla appka na tabletu přepsat |
| PR artifact | `1.0.0-pr20` | jen Actions → Artifacts, versionCode `1` |

Lokální sestavení v Android Studiu použije `1.0.0-local` (versionCode `1`).

Major/minor (ta `1.0`) se mění ručně v `gradle.properties` (`versionMajor` /
`versionMinor`). Patch doplní CI samo z pořadí běhů workflow **Build APK**.

Na tabletu se nainstalovaná verze ukáže dole na úvodní obrazovce. Novější APK
ze stejného podpisu přepíše starší instalaci — není potřeba nejdřív odinstalovat.

Podpisový klíč je v `app/keystore/` (repo je soukromé). Díky tomu mají všechny
CI buildy stejný podpis a aktualizace na tabletu fungují.

## Firemní certifikáty

HTTPS ověřuje **Android podle CA na tabletu** (systémové i ty, které
nainstalovalo IT / uživatel). V aplikaci už **nejsou** přibalené firemní
CA ani obejití „pokračovat i tak“. Když tablet autoritě nedůvěřuje,
spojení se nenačte.

IT musí mít na tabletech nasazenou firemní CA (Intune → trusted
certificate profile). Bez toho weby `tudc.cz` nepůjdou.

Obě APK (běžná i zkušební) používají stejné ověření HTTPS i stejné
přihlášení. Na zařízení se obě jmenují **Obálka**.

## Přihlášení a odhlášení

Android neumí Windows Integrated Auth (Kerberos) jako firemní PC. Appka
proto má vlastní přihlašovací obrazovku. Zkoušejte tvar `DOMÉNA\uživatel`
(např. `SZDC\jnovak`) a firemní heslo.

Tok:

1. Bez VPN → hláška přes celou obrazovku (Cisco AnyConnect). Nic jiného nefunguje.
2. VPN → nativní **Domů** i bez přihlášení.
3. **PSST Data** bez uložených údajů → formulář, ověření, pak web.
   Údaje platí jen pro `psst.tudc.cz` / `test.psst.tudc.cz`.
4. **⋮ → Vymazat údaje** → prefs, cookies, HTTP auth cache, velikost stránek
   i Chromium profil se smažou a proces se restartuje (NTLM jinak v procesu
   přežije). Pak znovu Domů.
5. Špatné heslo se ověří v odděleném procesu a do prohlížeče se nedostane.

| Typ na serveru | Šance ve WebView |
| --- | --- |
| Basic / Digest | Dobrá |
| NTLM | Funguje (několik kol handshake) |
| Negotiate / Kerberos | Typicky **nefunguje** — 401 bez výzvy k heslu |

Pokud server vyžaduje Kerberos, je potřeba na IIS povolit NTLM (nebo Basic
přes HTTPS). To aplikace sama nevyřeší.

Údaje se nikam neodesílají mimo cílový server. Záloha aplikace je vypnutá
(`allowBackup=false`), aby se heslo nezkopírovalo z tabletu. Na disku je
heslo šifrované klíčem v Android Keystore — čitelný text v souboru
není. Podrobný bezpečnostní audit: [`BEZPECNOST.md`](BEZPECNOST.md).

## Co ještě doladit

- **Ikona appky**: teď je jen jednoduchý placeholder (modrý čtverec se
  symbolem odkazu). Dá se snadno vyměnit za firemní logo.

Celkový audit (přihlášení, sekání grafů): [`AUDIT.md`](AUDIT.md).
Bezpečnost údajů: [`BEZPECNOST.md`](BEZPECNOST.md).

Vysvětlení celého projektu pro člověka bez Kotlinu (co který soubor dělá a proč): [`VYSVETLENI.md`](VYSVETLENI.md).
