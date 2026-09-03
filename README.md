# Link Wrapper

Jednoduchá Android appka — "schválená obálka" pro otevírání odkazů, které
firemní síť/VPN vyžaduje otevřít mimo běžný prohlížeč.

## Co appka umí

- Po spuštění nejdřív **VPN**, pak **přihlášení**. Home
  (`https://test.psst.tudc.cz/HSI.Psst.Data`) se otevře až po platných údajích.
- Relace se uloží v aplikaci. Při dalším spuštění se uživatel nepřihlašuje znovu.
- **Odhlásit** (⋮, dole, červeně) smaže jméno, heslo, cookies i NTLM relaci
  úplně a vrátí na obrazovku „Byl jste odhlášen“.
- **Karty** nahoře v liště; vpravo **+** (modré), **domeček**, **⋮**.
  Zavření karty křížkem (max. 8). Neaktivní karta se pozastaví, ať grafy
  na pozadí nesežerou tablet. Posun grafu: WebView bez hardware vrstvy,
  stropnuté DPI (tablet jinak kreslí 4× víc pixelů než PC) a vypnutý
  hover/tooltip při tažení.
- Nabídka **⋮**: zadat adresu, přenačíst, nastavení odkazů
  (u běžné APK i přehled firemních CA).
- Objeví se jako volba v "Otevřít pomocí" (včetně `psst.tudc.cz` /
  `test.psst.tudc.cz`). Externí odkaz otevře **novou kartu**.
- **VPN brána**: bez Cisco AnyConnect jen varování. Po připojení se buď
  zobrazí přihlášení, nebo se vrátí otevřené karty (když relace ještě platí).
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
     verze → soubory `LinkWrapper-pinned-1.0.N.apk` (běžná) a
     `LinkWrapper-systemtrust-1.0.N-system.apk` (jen trust store tabletu).
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

## Firemní certifikáty (důležité)

Tablety nemají systémově nainstalovanou firemní CA (Správa železnic), proto
jim prohlížeč hlásí `NET::ERR_CERT_AUTHORITY_INVALID`. Aplikace si ověření
provede sama.

Řetězec certifikátů je dvouúrovňový:

```
SZT Root BAU ECC CA          platnost do 4. 4. 2039
  └─ SZT Sub BAU ECC CA1     platnost do 22. 5. 2028
       └─ psst.tudc.cz       obnovuje se ~1× ročně
```

Certifikát stránky podepisuje **mezilehlá** CA, ne kořenová. Oba certifikáty
autorit jsou vložené v projektu:

- `app/src/pinned/res/raw/corporate_ca.pem` — kořenová
- `app/src/pinned/res/raw/corporate_sub_ca.pem` — mezilehlá

### Jak ověření probíhá

1. Kořenová CA musí odpovídat otisku `EXPECTED_ROOT_SHA256` v `CertPinning.kt`
   a být self-signed.
2. Mezilehlá CA musí být podepsaná kořenovou. Vlastní otisk zapsaný nemá —
   ověřuje se podpisem, takže při její výměně stačí vyměnit soubor.
3. Certifikát serveru musí být podepsaný mezilehlou CA.
4. Všechny tři musí být časově platné, obě autority musí mít `CA:TRUE`.

Aplikace **neignoruje certifikátové chyby plošně** a neváže se na jeden
konkrétní web. Cokoliv mimo tento řetězec je odmítnuto a stránka se nenačte —
ochrana proti podvrženému spojení zůstává funkční. Dialog nemá možnost
„pokračovat i tak" a nemá ji dostat.

### Výhody

- Funguje pro **všechny** interní stránky za firemní SSL inspekcí.
- **Přežije každoroční obnovu** certifikátů jednotlivých stránek — v aplikaci
  není potřeba měnit nic.

### Termíny údržby

**Květen 2028** — vyprší mezilehlá CA. Vyexportuj novou z prohlížeče
(Base-64 X.509 `.CER`, prostřední položka v cestě k certifikátu) a nahraď jí
`corporate_sub_ca.pem`. Otisk nikam zapisovat nemusíš.

**Duben 2039** — vyprší kořenová CA. Stejný postup pro `corporate_ca.pem`,
navíc je potřeba přepsat `EXPECTED_ROOT_SHA256` v `CertPinning.kt`
(`openssl x509 -in ca.pem -noout -fingerprint -sha256`).

Certifikáty se často obnovují dřív, než skutečně vyprší — stojí za to mít
připomínku pár měsíců předem.

**Nejlepší řešení dlouhodobě:** nechat IT nasadit obě CA na tablety systémově
(Intune → trusted certificate profile). Pak ověřování v aplikaci není potřeba
vůbec a fungovat bude i běžný prohlížeč.

### APK jen s důvěrou tabletu (`systemtrust`)

Druhá APK **PSST Data (systém)** (`com.example.linkwrapper.systemtrust`)
nemá v sobě žádné firemní CA, žádné „pokračovat i tak“ a v menu žádnou
položku o certifikátech. HTTPS ověřuje jen Android podle toho, čemu
tablet důvěřuje (systémové CA **i** certifikáty nainstalované uživatelem
/ MDM).

Když se v ní home načte, nainstalované CA na tabletu stačí. Když ne,
Android spojení odmítne — appka ho nepřekročí.

Jde nainstalovat vedle běžné APK (jiný název i id). Na tabletu musí být
stejně VPN.

## Přihlášení a odhlášení

Android neumí Windows Integrated Auth (Kerberos) jako firemní PC. Appka
proto má vlastní přihlašovací obrazovku. Zkoušejte tvar `DOMÉNA\uživatel`
(např. `SZDC\jnovak`) a firemní heslo.

Tok:

1. Bez VPN → jen hláška Cisco AnyConnect. Žádný formulář, žádný home.
2. VPN běží a **není relace** → formulář. Home se nenačte, dokud server
   údaje nepřijme.
3. Údaje sedí → uloží se do `Session` a otevře se home. Další karty i
   další spuštění aplikace použijí stejné údaje (HTTP Basic / Digest / NTLM).
4. **⋮ → Odhlásit** → prefs, cookies, HTTP auth cache i Chromium profil
   se smažou a proces se restartuje (NTLM jinak v procesu přežije).
5. Špatné heslo se ověří v odděleném procesu a do prohlížeče se nedostane.
   Na formuláři zůstane hláška; správné heslo lze zadat hned znovu.

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
