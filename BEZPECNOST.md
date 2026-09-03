# Bezpečnostní audit — PSST Data (Link Wrapper)

Prohlídka kódu se zaměřením na **přihlašovací údaje**: zda jdou číst z
disku, z jiné appky, ze zálohy, z logu, nebo je odeslat na cizí server.

Verdikt nahoře, detaily pod ním. **Absolutní** nečitelnost hesla nejde
slíbit na žádném odemčeném tabletu (root, debug, fyzický přístup k RAM).
Jde slíbit, že *jiná appka, záloha, log a cizí web* se k heslu nedostanou
způsobem, který tenhle kód dřív dovoloval.

---

## Verdikt

| otázka | odpověď |
| --- | --- |
| Přečte heslo jiná aplikace na tabletu? | **Ne.** Úložiště je jen pro tuto appku. |
| Přečte ho záloha Google / USB? | **Ne.** Záloha je vypnutá, citlivé soubory jsou vyloučené. |
| Je na disku čitelným textem? | **Ne** (po této úpravě). Je zašifrované klíčem v Android Keystore. |
| Objeví se v logu při přihlášení? | **Ne.** Do Intentu se už nedává. |
| Odešle ho appka na cizí web (odkaz, phishing)? | **Ne.** Heslo jde jen na `psst.tudc.cz` / `test.psst.tudc.cz`. |
| Půjde screenshot přihlášení z recents? | **Ne.** Obrazovka s heslem má `FLAG_SECURE`. |
| Přečte ho root / firemní MDM s plným přístupem? | **Ano, to nejde zastavit v appce.** Tablet by neměl být rootnutý. |

---

## Co bylo špatně (před touto úpravou)

Tohle nebyly teoretické nápady. Šlo to z kódu ověřit.

### 1. Heslo v čitelném textu na disku (kritické)

`Session` ukládalo `username` a `password` do `SharedPreferences`
(`session_prefs`). Soubor leží v

`/data/data/com.example.linkwrapper/shared_prefs/session_prefs.xml`

Jiná appka ho bez rootu nepřečte. **S rootem, ADB jako root, nebo
zálohou** (dřív ještě `allowBackup` uměl být zapnutý) je to obyčejné XML.

EncryptedSharedPreferences jsme záměrně nepoužili — na některých tabletech
padá. Tady je šifrování přímo přes **Android Keystore** (AES-256-GCM), bez
té knihovny.

### 2. Heslo v Intentu do druhého procesu (vysoké)

Ověření hesla běží v procesu `:authprobe`. Údaje se předávaly jako
`putExtra("extra_user")` / `putExtra("extra_pass")`.

Intent extras Android často vypíše do **logcat** (`ActivityManager`) a
`dumpsys activity`. Kdo má USB ladění nebo čte logy, viděl heslo v
okamžiku přihlášení.

Teď jde předání souborem v `no_backup`, šifrovaným stejným klíčem. Po
přečtení se soubor přepíše nulami a smaže. Intent nese jen „spusť probe“.

### 3. Firemní heslo na cizí server (kritické)

Appka se nabízí u **každého** `https` odkazu. Po přihlášení WebView na
HTTP 401 automaticky posílalo uložené jméno a heslo **jakémukoli hostu**.

Cesta útoku: e-mail / „Otevřít pomocí“ / `⋮ → zadat URL` na
`https://attacker.example`. Server odpoví 401. Appka by odeslala
`SZDC\jnovak` a heslo.

Teď se údaje posílají jen na `psst.tudc.cz` a `test.psst.tudc.cz`
(a jejich subdomény). Cizí 401 heslo nedostane.

### 4. SSL „pokračuj“, i když jméno nesedí (vysoké)

`onReceivedSslError` pouštělo spojení, jakmile certifikát podepsala
firemní CA. **Nesedící jméno serveru** (`SSL_IDMISMATCH`) se nehlídalo.

Útočník s jakýmkoli certifikátem od SZT Sub CA (třeba pro jiný interní
web) mohl podvrhnout `psst.tudc.cz`.

Teď se pokračuje jen při `SSL_UNTRUSTED` (tablety nemají CA v systému),
řetězec musí být náš, a CN musí sedět na hostitele. Expirace / mismatch
spojení zruší.

### 5. Screenshot hesla (střední)

Přihlašovací obrazovka neměla `FLAG_SECURE`. Recent apps, screenshot,
některé MDM nahrávky mohly zachytit vyplněné pole.

Teď je příznak zapnutý na overlay přihlášení / VPN a na neviditelném
probe okně. Na home (grafy) je vypnutý — ať jde pořídit snímek obrazovky
pro práci.

### 6. `intent:` / `file:` v WebView (střední)

Chyběl `shouldOverrideUrlLoading`. Stránka mohla otevřít `intent://`
nebo `file://`. Teď se načítá jen `https` a `about`.

---

## Co platí teď (úložiště hesla)

```
Uživatel zadá heslo
        │
        ▼
AuthHandoff (šifrovaný soubor na pár sekund) → proces :authprobe
        │
        │  server přijal
        ▼
Session.start
  • v RAM do zavření procesu
  • na disk: AES-256-GCM, klíč v Android Keystore (neopustí tablet)
  • starý plaintext se při prvním spuštění přepíše a smaže
        │
        │  Odhlásit
        ▼
prefs.clear, Keystore klíč pryč, cookies pryč, Chromium profil pryč,
proces zabitý
```

Když Keystore na tabletu selže, heslo **na disk nejde vůbec** — zůstane
jen v paměti do vypnutí appky. Další spuštění chce znovu přihlášení.
Čitelný text na disk se už nezapíše.

---

## Co appka pořád nemůže (a nemá slibovat)

1. **Root / odemčený bootloader** — útočník čte RAM i Keystore extrakcí.
   Řešení je správa tabletu (Intune: ne root, lockscreen, šifrování disku).
2. **Heslo v RAM, dokud jste přihlášení** — NTLM ho musí umět poslat
   serveru. Odhlásit + zabití procesu to z RAM smaže.
3. **Stejně podepsaná falešná aktualizace** — podpisový klíč je v soukromém
   gitu (`app/keystore/`). Kdo má repo, umí nahrát APK, která heslo pošle
   pryč. Klíč v gitu je kvůli aktualizacím na tabletu; přístup k repu
   držet úzký.
4. **Fyzicky odemčený tablet** — kdo sedí u odemčené relace, vidí web.
   To není díra v obálce.
5. **Kerberos ticket na PC** — appka má heslo, ne lístek. Po Odhlásit
   heslo v appce není; účet na serveru žije dál, dokud ho IT nezmění.

---

## Další kontroly (prošly)

| kontrola | výsledek |
| --- | --- |
| `allowBackup=false` + exclusion XML | záloha a device-to-device transfer bez dat appky |
| `usesCleartextTraffic=false` + network security config | HTTP bez TLS se nenačte |
| Probe `exported=false` | jiná appka probe nespustí |
| Žádný `addJavascriptInterface` | web nevolá Kotlin s heslem |
| `allowFileAccess` / `file://` z webu | vypnuto |
| Autofill na přihlášení | `importantForAutofill=no` |
| `Log` / `println` hesla | v kódu není |
| WebView debugging | `setWebContentsDebuggingEnabled(false)` |
| Mixed content | `MIXED_CONTENT_NEVER_ALLOW` |
| Dialog „pokračovat i tak“ u certifikátu | není |
| Poloha z cizího webu | jen hostitelé z `AuthHosts` |

---

## Doporučení mimo kód (IT / provoz)

1. Tablety bez rootu, se zámkem obrazovky a šifrováním úložiště.
2. USB ladění (ADB) na ostrých tabletech vypnout.
3. Přístup k GitHub repozitáři (a k podpisovému klíči) jen kdo musí.
4. Až to půjde: CA nainstalovat na tablety přes Intune — pak vlastní
   pinning v appce není potřeba.
5. Hesla účtů otáčet, když tablet zmizí.

---

## Soubory, které tohle mění

| soubor | změna |
| --- | --- |
| `SecretStore.kt` | AES-GCM + Android Keystore |
| `Session.kt` | šifrované uložení, migrace plaintextu pryč |
| `AuthHandoff.kt` | předání do probe bez Intent extras |
| `AuthHosts.kt` | komu smí jít HTTP auth |
| `CertPinning.kt` | `shouldProceed` — ne mismatch / expirace |
| `WebViewActivity.kt` | auth allowlist, FLAG_SECURE, blok `intent:` |
| `AuthProbeActivity.kt` | čte handoff, stejná SSL pravidla |
| `AndroidManifest.xml` | backup / network security XML |
