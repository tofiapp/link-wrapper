# Audit aplikace Obálka (Link Wrapper)

Obálka nad WebView. Přihlášení, karty, síť a HTTPS řeší Android. Grafy
kreslí stránka uvnitř WebView.

Instalace APK: [`README.md`](README.md).

---

## A. Tok

HTTPS ověřuje Android podle CA na tabletu (systém +
uživatel / MDM).

| stav | appka |
| --- | --- |
| není síť / VPN | pruh dole **Offline režim — jen připnuté karty** |
| síť, bez relace | nativní **Domů** |
| relace | Domů / otevřené karty |
| klepnutí na PSST Data bez relace | formulář, ověření v `:authprobe`, pak web |
| odchod na pozadí | relace a cookies pryč; karty v liště zůstanou |
| komu jde HTTP auth | jen `psst.tudc.cz` / `test.psst.tudc.cz` |
| **domeček** | aktuální karta se změní na **Domů** |
| **+** | nová karta **Domů**; aktuální web zůstane |
| dlouhé podržení karty / odkazu | připnout, nová karta, uložit / přejmenovat / odebrat |

Špatné heslo se **do hlavního WebView nedostane**.

---

## B. Soubor APK

Na ploše se instalace jmenuje **Obálka** (`LinkWrapper-1.0.N.apk`).

Údaje v `Session` jsou šifrované klíčem v Android Keystore.

---

## C. Soubory (co je živé)

| soubor | role |
| --- | --- |
| `WebViewActivity.kt` | jediná obrazovka: Domů / login, karty, web, offline pruh |
| `Destinations.kt` | PSST / Domů, popisek karty |
| `PageZoom.kt` | PSST Data; grafy vždy 84 % |
| `Session.kt` + `SecretStore.kt` | šifrované údaje, Keystore AES-256-GCM |
| `AuthProbeActivity.kt` + `AuthHandoff.kt` | ověření hesla v jiném procesu |
| `AuthHosts.kt` | komu smí jít HTTP auth (jen PSST) |
| `DeviceTrust.kt` | banner „tablet nemá CA“ u přihlášení |
| `SslPolicy.kt` + `network_security_config.xml` | HTTPS jen podle CA na tabletu |
| `ChartPerf.kt` / `ChartFit.kt` / `PsstDataLayout.kt` | grafy a karty PSST Data |
| `TrialPins.kt` / `TrialBookmarks.kt` | karty v RAM, uložené stránky |
| `layout_connection_banner.xml` | pruh dole bez sítě / VPN |
| `layout_home_screen.xml` | dlaždice jen s názvem |

Testy: `DestinationsTest`, `AuthHostsTest`, `DeviceTrustTest`,
`PageZoomTest`, `ChartFitTest`, `PsstDataLayoutTest`, `TrialPinsTest`,
`TrialBookmarksTest`, `TrialIdleTest`, `TrialIsolationTest`,
`TrialSettingsTest`.

---

## D. Přihlášení a údaje

Ověření mimo hlavní proces, Keystore, `allowBackup=false`, `FLAG_SECURE`
na formuláři. Údaje jdou jen na PSST hosty.

---

## E. Sekání grafů

Progress je overlay (nemění výšku WebView). Layout listener klávesnice
neběží na webu. Skryté karty se pozastaví. DPR strop 1.25. Hardware
vrstva kolem WebView je vypnutá.

---

## F. Co by stálo za další kolo

1. `mailto:` / `tel:` poslat do systému.
2. Pokud grafy cukají dál: měřit ve stránce `HSI.Psst.Data`, ne v obálce.
