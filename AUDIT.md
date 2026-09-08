# Audit aplikace Obálka (Link Wrapper)

Stav kódu po zrušení zkušebního celoplošného přihlášení (září 2026).
Obálka nad WebView. Přihlášení, karty, VPN a HTTPS řeší Android. Grafy
kreslí stránka uvnitř WebView.

Prohlídka souborů bez Kotlinu: [`VYSVETLENI.md`](VYSVETLENI.md).
Bezpečnost hesla: [`BEZPECNOST.md`](BEZPECNOST.md).
Instalace APK: [`README.md`](README.md).

---

## A. Co platí teď (tok)

HTTPS v **obou** APK ověřuje jen Android podle CA na tabletu (systém +
uživatel / MDM). Přibalené firemní CA a `CertPinning` jsou pryč.

| stav | běžná i zkušební APK |
| --- | --- |
| není VPN | hláška **přes celou obrazovku** |
| VPN, bez relace | nativní **Domů** |
| relace | Domů / otevřené karty |
| klepnutí na PSST Data bez relace | formulář, ověření v `:authprobe`, pak web |
| odchod na pozadí | relace a cookies pryč; karty v liště zůstanou |
| komu jde HTTP auth | jen `psst.tudc.cz` / `test.psst.tudc.cz` |
| **domeček** | aktuální karta se změní na **Domů** |
| **+** | nová karta **Domů**; aktuální web zůstane |
| dlouhé podržení karty / odkazu | dialog **Otevřít na druhé kartě** |

Špatné heslo se **do hlavního WebView nedostane**.

---

## B. Úklid v tomto kole

Cíl: stejné přihlášení v obou APK (jen PSST). Žádné celoplošné údaje
pro `tudc.cz`. Na zařízení se obě jmenují **Obálka**.

| co | proč |
| --- | --- |
| `TRIAL_HOME_LOGIN` | celkové přihlášení ve zkušební nefungovalo |
| `AuthHosts.allows` jen PSST | heslo nesmí jít na cizí 401 |

**Ponecháno schválně:** názvy flavorů `pinned` / `systemtrust` (CI a
vnitřní Gradle). Soubory APK jsou `LinkWrapper-1.0.N.apk` a
`LinkWrapper-1.0.N-test.apk`. `Session` pořád maže stará `session_gate` prefs. Keystore,
VPN brána, dva APK vedle sebe.

---

## C. Soubory (co je živé)

| soubor | role |
| --- | --- |
| `WebViewActivity.kt` | jediná obrazovka: VPN, Domů / login, karty, web |
| `Destinations.kt` | PSST / Domů, popisek karty |
| `PageZoom.kt` | PSST Data; grafy vždy 84 % |
| `Session.kt` + `SecretStore.kt` | šifrované údaje, Keystore AES-256-GCM |
| `AuthProbeActivity.kt` + `AuthHandoff.kt` | ověření hesla v jiném procesu |
| `AuthHosts.kt` | komu smí jít HTTP auth (PSST vs celé tudc.cz) |
| `DeviceTrust.kt` | banner „tablet nemá CA“ u přihlášení |
| `SslPolicy.kt` + `network_security_config.xml` | HTTPS jen podle CA na tabletu |
| `ChartPerf.kt` | strop `devicePixelRatio` kvůli grafům |
| `layout_vpn_gate.xml` | celoobrazovková hláška bez VPN |
| `layout_home_screen.xml` | dlaždice jen s názvem |

Testy: `DestinationsTest`, `AuthHostsTest`, `DeviceTrustTest`, `PageZoomTest`.

---

## D. Přihlášení a údaje (stále platí)

Ověření mimo hlavní proces, Keystore, `allowBackup=false`, `FLAG_SECURE`
na formuláři. Obě APK posílají heslo jen na PSST hosty. Podrobnosti
v `BEZPECNOST.md`.

Reverse engineering APK **heslo nedá** — klíč je v čipu tabletu, ne v APK.

---

## E. Sekání grafů (stále platí)

Progress je overlay (nemění výšku WebView). Layout listener klávesnice
neběží na webu. Skryté karty se pozastaví. DPR strop 1.25. Hardware
vrstva kolem WebView je vypnutá.

---

## F. Rizika, která appka nezastaví

| téma | stav |
| --- | --- |
| Root / MDM / dump RAM u přihlášené appky | heslo jde získat; Keystore to na rootnutém tabletu neochrání |
| Max. 8 karet | žerou RAM; stav karet se po zabití procesu neukládá |
| `mailto:` / `tel:` | nenačtou se (jen `https` / `about`) |
| Catch-all `https` filtr | appka se nabídne i u cizího webu; heslo tam **nepošle** (jen PSST) |
| VPN detekce | `TRANSPORT_VPN`; split-tunnel umí lhát |
| Tablety bez firemní CA | weby `tudc.cz` se nenačtou (pinning v APK už není) |
| `values-night` | appka je světlá; systémový tmavý režim může rozházet systémové dialogy |

---

## G. Co by stálo za další kolo

1. `mailto:` / `tel:` poslat do systému.
2. Ukládat seznam karet (URL) a po zabití procesu je obnovit.
3. Volitelně zúžit catch-all `https` filtr.
4. Pokud grafy cukají dál: měřit ve stránce `HSI.Psst.Data`, ne v obálce.
