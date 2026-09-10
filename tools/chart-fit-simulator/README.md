# Chart Fit Simulator

Interaktivní nástroj pro ladění problému „graf je malý a nevyplní ohraničení“.

## Použití

1. V Obálce otevřete graf (`dmId=…`).
2. Po načtení zvolte **⋮ → Stav grafu (simulátor)** — JSON se zkopíruje do schránky.
3. Otevřete `index.html` v prohlížeči (dvojklik nebo `python3 -m http.server` v této složce).
4. Vložte JSON a klikněte **Načíst JSON**.

## Co simulátor ukazuje

- **Šířka je vždy stejná** — mění se jen výška směrem dolů.
- **Teď** — `pxPerMeter` z aplikace; prodloužení boxu jen přidá prázdný pás.
- **Cíl** — stejná šířka, větší výška, přepočtený `pxPerMeter` aby signál dosáhl ke spodní hranici.

## Formát JSON

Viz `ChartFit.SNAPSHOT_JS` v `app/src/main/java/com/example/linkwrapper/ChartFit.kt`.
