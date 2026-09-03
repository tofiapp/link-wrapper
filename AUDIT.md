# Audit aplikace PSST Data (Link Wrapper)

Stav kódu po úklidu slepých větví (září 2026). Obálka nad WebView.
Přihlášení, karty, VPN a certifikáty řeší Android. Grafy kreslí stránka
uvnitř WebView.

Prohlídka souborů bez Kotlinu: [`VYSVETLENI.md`](VYSVETLENI.md)
(starší pasáže o „nejdřív login, pak home“ a názvech karet z `dmId`
už neplatí — platí tahle tabulka a [`README.md`](README.md)).
Bezpečnost hesla: [`BEZPECNOST.md`](BEZPECNOST.md).

---

## A. Co platí teď (tok)

| stav | co vidí uživatel |
| --- | --- |
| není VPN | hláška **přes celou obrazovku**; karty ani lišta nefungují |
| VPN, bez relace PSST | nativní **Domů** (dlaždice PSST Data a DSD) |
| klepnutí na PSST Data bez relace | formulář, ověření v procesu `:authprobe`, pak web v té kartě |
| klepnutí na DSD | web s vlastním přihlášením; údaje z PSST se **neposílají** |
| **+** nebo **domeček** | nová karta **Domů**; aktuální web zůstane |
| dočasný HTTP 401 u DSD | žádný dialog |
| Vymazat údaje | prefs, cookies, Chromium profil pryč, nový proces, znovu Domů |

Špatné heslo k PSST se **do hlavního WebView nedostane**.

---

## B. Úklid v tomto kole

Našli jsme zbytky staršího UX (login-před-home, kopírování karty plusem,
popisek hostitele na dlaždici, prázdné `applyChrome`). Pryč:

| co | proč |
| --- | --- |
| `Destinations.hostLabel`, `sameApp()` | dlaždice mají jen název; deduplikace karet se nepoužívá |
| `AuthHosts.hostnameMatches()` | žilo jen v testu, SSL ho nevolá |
| `currentHostForUi` / `applyChrome()` | zapisovalo se, nikdo to nečetl |
| `SslPolicy.loadError()` / `describeChain()` | obal nic nevolal; dialog bere `CertPinning` přímo |
| `showOpenUrlDialog(openAsNewTab)` | vždy `false` |
| `showSite()` | jeden řádek navíc |
| duplicitní `action_logout` ve `when` a mrtvá větev v `onBackPressed` | po VPN/HOME/LOGIN zbývá jen BROWSER |
| barvy `ok` / `warn` | nikde v UI |
| nepoužité id `browserChrome`, `loginFormPanel` | |
| veřejné `AuthProbe.deleteProfile`, `CertPinning.isIssuedByCorporateCa`, `loadError()` | stačí private |

**Ponecháno schválně:** `Session` pořád jednorázově smaže staré `session_gate`
prefs (upgrade ze staré APK). Pinning CA, `AuthHosts.allows`, Keystore,
VPN brána, dva flavor APK.

---

## C. Soubory (co je živé)

| soubor | role |
| --- | --- |
| `WebViewActivity.kt` | jediná obrazovka: VPN, Domů, karty, web, login PSST |
| `Destinations.kt` | PSST / DSD / Domů, popisek karty |
| `PageZoom.kt` | 88 % na výšku, 80 % na šířku |
| `Session.kt` + `SecretStore.kt` | šifrované údaje PSST, Keystore AES-256-GCM |
| `AuthProbeActivity.kt` + `AuthHandoff.kt` | ověření hesla v jiném procesu |
| `AuthHosts.kt` | heslo jen na `psst.tudc.cz` / `test.psst.tudc.cz` |
| `DeviceTrust.kt` | banner „tablet nemá CA“ před loginem |
| `ChartPerf.kt` | strop `devicePixelRatio` kvůli grafům |
| `SslPolicy` / `CertPinning` | pinned = firemní CA v APK; systemtrust = jen tablet |
| `layout_vpn_gate.xml` | celoobrazovková hláška bez VPN |
| `layout_home_screen.xml` | dlaždice jen s názvem |

Testy: `DestinationsTest`, `AuthHostsTest`, `DeviceTrustTest`, `PageZoomTest`.

---

## D. Přihlášení a údaje (stále platí)

Ověření mimo hlavní proces, Keystore, `allowBackup=false`, heslo jen na
PSST hosty, `FLAG_SECURE` na formuláři. Podrobnosti v `BEZPECNOST.md`.

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
| Catch-all `https` filtr | appka se nabídne i u cizího webu; **heslo tam nejde** |
| VPN detekce | `TRANSPORT_VPN`; split-tunnel umí lhát |
| `values-night` | appka je světlá; systémový tmavý režim může rozházet systémové dialogy |

---

## G. Co by stálo za další kolo

1. `mailto:` / `tel:` poslat do systému.
2. Ukládat seznam karet (URL) a po zabití procesu je obnovit.
3. Volitelně zúžit catch-all `https` filtr.
4. Pokud grafy cukají dál: měřit ve stránce `HSI.Psst.Data`, ne v obálce.
