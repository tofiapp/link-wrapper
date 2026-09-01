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
  Zavření karty křížkem (max. 8).
- Nabídka **⋮**: zadat adresu, přenačíst, certifikáty, nastavení odkazů.
- Objeví se jako volba v "Otevřít pomocí" (včetně `psst.tudc.cz` /
  `test.psst.tudc.cz`). Externí odkaz otevře **novou kartu**.
- **VPN brána**: bez Cisco AnyConnect jen varování. Po připojení se buď
  zobrazí přihlášení, nebo se vrátí otevřené karty (když relace ještě platí).
- **Poloha**: dialog a systémové oprávnění pro `navigator.geolocation`.

## Jak appku dostat jako .apk (bez Android Studia)

1. Založ si nový repozitář na GitHubu (klidně soukromý).
2. Nahraj do něj obsah tohoto projektu (celou tuhle složku).
3. Po pushnutí do větve `main` se automaticky spustí GitHub Actions workflow
   (`.github/workflows/build.yml`), který appku sestaví, očísluje a podepíše.
4. Stáhni APK jedním z těchto způsobů:
   - **Releases** (pohodlnější): v repozitáři záložka **Releases** → nejnovější
     verze → soubor `LinkWrapper-1.0.N.apk`.
   - **Actions**: záložka **Actions** → poslední běh → dole v sekci
     **Artifacts** najdeš `LinkWrapper-1.0.N` → stáhni zip, uvnitř je APK.
5. Ten `.apk` nahraj do tabletu (email sám sobě, Google Drive, USB…) a nainstaluj.
   Bude potřeba v nastavení tabletu povolit instalaci z "neznámých zdrojů"
   (protože appka nejde přes Google Play).

Build jde spustit i ručně: **Actions** → **Build APK** → **Run workflow**.

## Automatické číslování verzí

Každý GitHub Actions build dostane unikátní číslo z pořadí běhu workflow:

| Co | Příklad | K čemu je |
| --- | --- | --- |
| `versionName` | `1.0.15` | Viditelná verze (domovská obrazovka appky, název APK, GitHub Release) |
| `versionCode` | `15` | Interní číslo pro Android — musí růst, aby šla appka na tabletu aktualizovat |

Lokální sestavení v Android Studiu použije `1.0.0-local` (versionCode `1`).

Major/minor (ta `1.0`) se mění ručně v `gradle.properties` (`versionMajor` /
`versionMinor`). Patch doplní CI samo.

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

- `app/src/main/res/raw/corporate_ca.pem` — kořenová
- `app/src/main/res/raw/corporate_sub_ca.pem` — mezilehlá

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

| Typ na serveru | Šance ve WebView |
| --- | --- |
| Basic / Digest | Dobrá |
| NTLM | Funguje (několik kol handshake) |
| Negotiate / Kerberos | Typicky **nefunguje** — 401 bez výzvy k heslu |

Pokud server vyžaduje Kerberos, je potřeba na IIS povolit NTLM (nebo Basic
přes HTTPS). To aplikace sama nevyřeší.

Údaje se nikam neodesílají mimo cílový server. Záloha aplikace je vypnutá
(`allowBackup=false`), aby se heslo nezkopírovalo z tabletu.

## Co ještě doladit

- **Ikona appky**: teď je jen jednoduchý placeholder (modrý čtverec se
  symbolem odkazu). Dá se snadno vyměnit za firemní logo.
