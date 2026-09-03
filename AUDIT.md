# Audit aplikace PSST Data (Link Wrapper)

Stav kódu po sjednocení důvěry tabletu a zkušebním přihlášení (září 2026).
Obálka nad WebView. Přihlášení, karty, VPN a HTTPS řeší Android. Grafy
kreslí stránka uvnitř WebView.

Prohlídka souborů bez Kotlinu: [`VYSVETLENI.md`](VYSVETLENI.md).
Bezpečnost hesla: [`BEZPECNOST.md`](BEZPECNOST.md).
Instalace APK: [`README.md`](README.md).

---

## A. Co platí teď (tok)

HTTPS v **obou** APK ověřuje jen Android podle CA na tabletu (systém +
uživatel / MDM). Přibalené firemní CA a `CertPinning` jsou pryč.

| stav | běžná APK (`pinned`) | zkušební APK (`systemtrust`) |
| --- | --- | --- |
| není VPN | hláška **přes celou obrazovku** | stejně |
| VPN, bez relace | nativní **Domů** | **přihlášení** (ne Domů) |
| relace | Domů / otevřené karty | totéž |
| klepnutí na PSST Data bez relace | formulář, ověření v `:authprobe`, pak web | na Domů se bez relace nedostanete |
| klepnutí na DSD | web s vlastním formulářem; údaje z PSST se **neposílají** | stejné údaje jako na Domů (NTLM na `tudc.cz`) |
| komu jde HTTP auth | jen `psst.tudc.cz` / `test.psst.tudc.cz` | celé `tudc.cz` |
| **+** nebo **domeček** | nová karta **Domů**; aktuální web zůstane | bez relace znovu přihlášení |
| dočasný HTTP 401 u DSD | žádný dialog | stejně |
| Vymazat údaje | prefs, cookies, profil pryč, nový proces, znovu Domů | totéž, pak znovu přihlášení |

Špatné heslo se **do hlavního WebView nedostane**.

---

## B. Úklid v tomto kole

Cíl: žádná APK už nepoužívá starý pinning v APK. Zkušební verze má
přihlášení na Domů pro celé `tudc.cz`.

| co | proč |
| --- | --- |
| `CertPinning.kt`, `corporate_ca.pem`, `corporate_sub_ca.pem` | starý způsob — ověření teď jen tablet |
| flavor `SslPolicy` + flavor `network_security_config` | jedno společné pravidlo v `main` |
| menu „info o certifikátech“, `ic_shield`, `action_cert_info` | patřilo k pinningu v APK |
| `TRIAL_HOME_LOGIN` | běžná = false, zkušební = true |
| `AuthHosts.allows(host, allTudc)` | zkušební smí poslat údaje na `*.tudc.cz` |

**Ponecháno schválně:** názvy flavorů `pinned` / `systemtrust` (CI a
názvy APK). `Session` pořád maže stará `session_gate` prefs. Keystore,
VPN brána, dva APK vedle sebe.

---

## C. Soubory (co je živé)

| soubor | role |
| --- | --- |
| `WebViewActivity.kt` | jediná obrazovka: VPN, Domů / login, karty, web |
| `Destinations.kt` | PSST / DSD / Domů, popisek karty |
| `PageZoom.kt` | 88 % na výšku, 80 % na šířku |
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
na formuláři. Běžná APK posílá heslo jen na PSST hosty. Zkušební APK
na celé `tudc.cz` — proto je to jen zkušební build, ne výchozí.
Podrobnosti v `BEZPECNOST.md`.

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
| Catch-all `https` filtr | appka se nabídne i u cizího webu; **běžná APK heslo tam nepošle** |
| Zkušební APK + cizí `*.tudc.cz` | HTTP 401 na podvrženém `tudc.cz` hostu dostane stejné údaje |
| VPN detekce | `TRANSPORT_VPN`; split-tunnel umí lhát |
| Tablety bez firemní CA | weby `tudc.cz` se nenačtou (pinning v APK už není) |
| `values-night` | appka je světlá; systémový tmavý režim může rozházet systémové dialogy |

---

## G. Co by stálo za další kolo

1. `mailto:` / `tel:` poslat do systému.
2. Ukládat seznam karet (URL) a po zabití procesu je obnovit.
3. Volitelně zúžit catch-all `https` filtr.
4. Pokud grafy cukají dál: měřit ve stránce `HSI.Psst.Data`, ne v obálce.
