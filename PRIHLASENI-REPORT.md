# Report: co bylo špatně na přihlášení a odhlášení

Přihlášení v aplikaci **nebyla brána do home**. Byla to hromada oprav
kolem HTTP 401 ve WebView. Každý další PR přidal příznak, overlay nebo
restart procesu, ale základní model zůstal špatně: aplikace nejdřív
otevřela stránku a teprve když server řekl 401, zeptala se na heslo.

## 1. Home se otevíral bez přihlášení

Při zapnuté VPN `onCreate` rovnou volalo `openInNewTab(startUrl)`.
Žádná kontrola, jestli vůbec existuje relace.

Důsledek: uživatel viděl home (nebo 401 stránku, nebo starou NTLM
session v Chromiu) **aniž by zadal údaje**. Přihlašovací formulář byl
vedlejší efekt challenge od serveru, ne podmínka vstupu.

To samé po výpadku VPN: `exitVpnGate()` schovalo overlay a obnovilo
karty, i když relace neexistovala. Stačilo spustit appku bez VPN a
pak VPN zapnout — home naskočil bez formuláře.

## 2. Platnost údajů se neřešila (nebo se řešila pozdě a špatně)

Dlouho stačilo neprázdné jméno. Heslo se začalo vyžadovat až v PR #18.
Uložení hesla a schování overlay probíhalo **před** tím, než server
odpověděl. Špatné heslo tedy „pustilo dovnitř“.

Pozdější pokus (PR #19) nechal overlay, dokud se nenačte stránka, ale
zároveň trestal **každý HTTP 401** během NTLM handshake jako neplatné
údaje. NTLM má právě několik 401 kol, než uspěje. Výsledek: občas
odmítnuté správné heslo, občas puštěné špatné.

Počítadlo challenge navíc v dřívějších verzích bylo globální. Nová
karta (třeba z historie) sečetla kola všech WebView a po ~12
**sama smazala heslo**. Vypadalo to jako odhlášení, které nikdo
nestiskl (PR #16).

## 3. Relace neměla jedno místo pravdy

Současně žilo:

- `HttpCredentials` (prefs `http_auth_prefs`, klíče podle hostitele)
- `session_gate` / `KEY_LOGGED_OUT` (jestli zrovna běžel restart po Odhlásit)
- `EXTRA_LOGGED_OUT` na intentu
- `LoginGate { NONE, VPN, LOGOUT, AUTH }`
- `loginVisible`, `authDialogShowing`, `awaitingHttpAuth`
- `loginAwaitingPage`, `loginSubmitInFlight`, `submittedCredentials`
- cookies WebView, HTTP auth databáze, Chromium NTLM connection pool

Žádný z těchto příznaků neznamenal „uživatel je přihlášený“. VPN, 401
a Odhlásit si je přepisovaly. Checkbox **Zapamatovat na tomto zařízení**
navíc relaci podmiňoval, i když pro `*.psst.tudc.cz` se ukládalo vždycky.
Uživatel neměl šanci pochopit, kdy údaje zůstanou a kdy ne.

Požadované chování je přitom jednoduché:

- údaje se uloží po **úspěšném** přihlášení
- zůstanou, **dokud uživatel nestiskne Odhlásit**
- Odhlásit je smaže **úplně**

## 4. Odhlášení nesmazalo NTLM (a oprava to zamaskovala restartem)

Chromium drží NTLM v síťovém kontextu **procesu**. `clearHttpAuthUsernamePassword()`,
smazání cookies i `WebView.destroy()` na to nestačí. Po Odhlásit šlo
znovu otevřít home se starou relací — i s vymyšleným heslem.

To se „opravilo“ zabitím procesu (`killProcess` + `exitProcess`) a
příznakem „právě odhlášen“. Hammer byl nutný kvůli NTLM, ale visel na
souběhu extra intentu, druhých prefs a vracení se do stejné activity,
která pořád uměla otevřít home bez relace. Když se kterýkoli z těch
kroků neprovedl, relace žila dál.

Heslo v prefs se navíc dřív mazalo přes `apply()` (asynchronně). Čtení
hned po Odhlásit mohlo staré údaje ještě vidět.

`android:allowBackup="true"` mohlo heslo vytáhnout ze zálohy tabletu
zpátky do aplikace.

## 5. Zpět a 401 dialog obcházely bránu

Na přihlášení z HTTP 401 šlo tlačítkem Zpět overlay zrušit a zůstat na
neautorizované stránce. Dialog 401 nabízel smazání uloženého hesla jako
radu, i když uživatel Odhlásit nestiskl.

## 6. Mrtvý kód z předchozích vrstev

Přihlášení se přesouvalo z dialogu do celoobrazovky, z `HomeActivity`
do WebView, z historie pryč. V projektu ale zůstalo:

- `HomeActivity` + `activity_home.xml` (launcher už to nespouštěl)
- `dialog_http_auth.xml` (starý 401 dialog, nic ho neinflatovalo)
- checkbox „Zapamatovat“, který relaci jen mátl
- ikony a drawable jen pro zrušenou domácí obrazovku
- README pořád popisovalo „po spuštění rovnou otevře home“ a
  „Zapomenout přihlášení“, což v menu nebylo

`WebViewActivity` měla přes 1700 řádků, z toho stovky na stavy, které
se překrývaly. Další patch do toho nešel rozumně přidat.

## 7. Proč to pořád „nějak“ vypadalo, že přihlášení je“

Několik posledních PR (heslové pole povinné, overlay dokud se nenačte
stránka, wipe + restart po Odhlásit, historie pryč) hasilo konkrétní
bugy. Nezměnil se model. Appka pořád:

1. otevřela home
2. čekala, až WebView dostane 401
3. podle tuctu příznaků hádala, jestli ukázat formulář, VPN, nebo
   uživatele pustit dál

To nejde stabilně otestovat ani udržovat. Chyby se projevovaly jako
„občas mě to odhlásilo“, „občas mě to pustilo bez hesla“, „po Odhlásit
jsem pořád uvnitř“.

---

## Co je teď jinak

Jedna relace (`Session`): `start` po úspěchu na serveru, `end` jen z
Odhlásit. Jedna brána v activity:

| stav | co vidí uživatel |
| --- | --- |
| není VPN | jen varování AnyConnect |
| VPN, není relace | formulář; home se nenačítá |
| VPN, ověřují se údaje | formulář zůstane, dokud server neodsouhlasí |
| VPN, relace platí | home; HTTP auth jde z uložených údajů |
| Odhlásit | prefs + cookies + Chromium profil pryč, nový proces, znovu formulář |

Starý kód (`HomeActivity`, HTTP dialog, `HttpCredentials`, checkbox,
mrtvé drawable) je pryč. Záloha aplikace je vypnutá.
