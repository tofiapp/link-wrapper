# Link Wrapper

Jednoduchá Android appka — "schválená obálka" pro otevírání odkazů, které
firemní síť/VPN vyžaduje otevřít mimo běžný prohlížeč.

## Co appka umí

- Objeví se jako volba v systémovém dialogu "Otevřít pomocí" u http/https odkazů
  (např. při kliknutí na odkaz v Outlooku).
- Zobrazí stránku ve vestavěném WebView.
- Tlačítko **Přenačíst** v horní liště.
- **Historie** posledních 24 hodin otevřených odkazů (uložená jen lokálně
  v appce, nikam se neposílá).
- Na domovské obrazovce jde odkaz i ručně vložit a otevřít.

## Jak appku dostat jako .apk (bez Android Studia)

1. Založ si nový repozitář na GitHubu (klidně soukromý).
2. Nahraj do něj obsah tohoto projektu (celou tuhle složku).
3. Po pushnutí do větve `main` se automaticky spustí GitHub Actions workflow
   (`.github/workflows/build.yml`), který appku sestaví.
4. Jdi do repozitáře → záložka **Actions** → poslední běh → dole v sekci
   **Artifacts** najdeš `LinkWrapper-debug-apk` → stáhni si zip, uvnitř je
   `app-debug.apk`.
5. Ten `.apk` nahraj do tabletu (email sám sobě, Google Drive, USB…) a nainstaluj.
   Bude potřeba v nastavení tabletu povolit instalaci z "neznámých zdrojů"
   (protože appka nejde přes Google Play).

## Firemní certifikát (důležité)

Tablety nemají systémově nainstalovanou firemní kořenovou CA
(**SZT Root BAU ECC CA**, Správa železnic), proto jim prohlížeč hlásí
`NET::ERR_CERT_AUTHORITY_INVALID`. Aplikace to řeší tak, že ověřuje, jestli
certifikát stránky **vydala tato firemní CA** — její certifikát je vložený
v `app/src/main/res/raw/corporate_ca.pem`.

Aplikace **neignoruje certifikátové chyby plošně** a neváže se na jeden
konkrétní web. Cokoliv, co tato CA nevydala, je odmítnuto a uživateli se
zobrazí varování — ochrana proti podvrženému spojení zůstává funkční.

### Výhoda tohoto řešení

- Funguje pro **všechny** interní stránky za firemní SSL inspekcí.
- **Přežije pravidelnou výměnu** certifikátů jednotlivých stránek (ty se mění
  ~1× ročně) — v aplikaci není potřeba nic měnit.
- Zásah bude potřeba až **v dubnu 2039**, kdy vyprší kořenová CA.

### Až vyprší kořenová CA (2039)

1. Vyexportuj novou kořenovou CA z prohlížeče na PC (Base-64 X.509 `.CER`).
2. Nahraď jí soubor `app/src/main/res/raw/corporate_ca.pem`.
3. Uprav kontrolní otisk `EXPECTED_CA_SHA256` v `CertPinning.kt`
   (zjistíš: `openssl x509 -in ca.pem -noout -fingerprint -sha256`).
4. Pushni a stáhni nové APK z Actions.

**Nejlepší řešení dlouhodobě:** nechat IT nasadit tuto CA na tablety
systémově (Intune → trusted certificate profile). Pak ověřování v aplikaci
není potřeba vůbec a fungovat bude i běžný prohlížeč.

## Co ještě doladit

- **Omezení na konkrétní doménu**: aktuálně appka přijme jakoukoliv http/https
  adresu. Až budete znát přesnou firemní doménu, přidejte do
  `app/src/main/AndroidManifest.xml` do `intent-filter` u `WebViewActivity`
  atribut `android:host="vase-domena.cz"` k `<data>` tagu — appka se pak
  v "Otevřít pomocí" nabídne jen pro tuhle doménu.
- **Ikona appky**: teď je jen jednoduchý placeholder (modrý čtverec se
  symbolem odkazu). Dá se snadno vyměnit za firemní logo.
- **VPN kontrola**: appka nekontroluje, jestli je Cisco AnyConnect připojený —
  spoléhá na to, že systémová VPN běží. Pokud by se hodilo appce hlásit
  "nejsi připojený k VPN", dá se to doplnit.
