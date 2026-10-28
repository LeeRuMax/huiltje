# Huiltje

Android-app voor de eerste maanden met een baby. Hij luistert naar het geluidje vlak vóór het huilen en zegt welk
van de vijf "Dunstan"-geluiden het lijkt, met wat je dan kunt doen. Daarnaast is het een simpel babylogboek:
voeding, kolven, luiers, slaap, vitamine, groei. Alles blijft op je telefoon: geen account, geen server, geen
reclame, geen tracking.

> ⚠️ **Geen medisch hulpmiddel.** De herkenning is een hint, geen diagnose; de "Dunstan Baby Language" is
> wetenschappelijk niet bewezen. Twijfel je over je baby, bel de huisarts. Bij direct gevaar: 112.

*English: Huiltje ("little cry") is a Dutch-language Android app for newborns. It guesses which of the five
Dunstan pre-cry sounds your baby makes (on-device, YAMNet + a small linear model) and doubles as a baby log
(feeding with per-side timers, pumping, diapers, sleep, vitamins, growth chart). No accounts, no servers;
partners sync phone-to-phone over home Wi-Fi with an end-to-end encrypted pairing code.*

## Wat het kan

- **Luisteren**: tik op Luister zodra het geluidje begint. Je ziet welk geluid het lijkt (Neh = honger, Owh = moe,
  Heh = ongemak, Eairh = krampjes, Eh = boertje), hoe zeker, en wat je kunt doen. De klok telt mee: lang na de
  voeding maakt honger waarschijnlijker, lang wakker maakt moe waarschijnlijker. Wat je achteraf bevestigt, leert
  de app bij voor jullie eigen baby.
- **Borstvoeding** met een timer per kant (één kant tegelijk) en **kolven** met een timer per kant (beide mogen
  tegelijk, ook naast borstvoeding). Overal zie je waarmee begonnen en geëindigd is, en "volgende keer beginnen met".
- **Fles, luiers, slaap/wakker, vitamine D/K**, alles met één tik. Achteraf invullen of aanpassen kan per onderdeel.
- **Voorraad moedermelk** met houdbaarheid (Voedingscentrum) en een seintje als iets op moet.
- **Herinneringen**: vitamine (alleen als die dag nog niet gegeven), wakkertijd, afspraken.
- **Inzicht**: wat opvalt (bijv. krampjes vooral 's avonds), de afgelopen 7 dagen, huiltjes per uur, groeicurve met
  WHO-lijnen.
- **Week voor week**, **nachtstand** (zwart/amber, gedimd, grote knoppen) en een **widget** voor het beginscherm.
- **Delen met je partner** via een partnercode, rechtstreeks tussen de telefoons op de wifi thuis.
- **Backup** als zip (logboek + opnames); terugzetten voegt samen.

Werkt het best voor 0 tot ongeveer 3 maanden; daarna verdwijnen de geluidjes langzaam. De app is Nederlandstalig.

## Installeren

Download de APK bij [Releases](../../releases) en open hem op je telefoon (sta "installeren uit onbekende bron" toe).
Android 8 of nieuwer, arm64 (vrijwel alle telefoons van de laatste jaren).

## Privacy

- De app heeft internettoegang alleen voor het delen met je partner: `Sync.kt` praat uitsluitend met adressen in
  het eigen thuisnetwerk (privé-IP's) en weigert al het andere. Er is geen server en er gaat niets naar het internet.
- Opnames, logboek en bijgeleerde voorbeelden staan alleen op de telefoon (`Android/data/<app>/files/opnames/` en
  de app-database). Android-backup naar de cloud staat uit.
- De backup-zip is niet versleuteld: bewaar hem op een plek die van jou is.

## Zelf bouwen

Nodig: JDK 17 en de Android SDK (platform 35), of gewoon Android Studio.

```sh
git clone https://github.com/LeeRuMax/huiltje.git && cd huiltje
./gradlew assembleDebug            # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest        # alle tests, zie hieronder
```

`./gradlew assembleRelease` zonder sleutel geeft een release met de debug-sleutel: installeerbaar, maar het is
jouw eigen build en hij kan niet over de officiële app heen updaten (en andersom). Met een eigen sleutel:
`-PhuiltjeKeystore=/pad/naar.keystore -PhuiltjeWachtwoordBestand=/pad/naar/wachtwoord` (alias `huiltje`).

De tests draaien op de JVM met Robolectric: de logica (timers, kanten, voorkennis), echte SQLite, twee "telefoons"
die over een socket bijwerken, backup heen en terug, en `SchermTest` rendert de schermen naar
`app/build/schermen/*.png` zodat je ze zonder telefoon kunt bekijken.

## Hoe de herkenning werkt

1. Opname 16 kHz mono, tot 8 s.
2. **YAMNet** (Google, AudioSet) als TFLite per venster van 0,975 s: herkent of er een baby klinkt
   (*Crying*, *Baby cry*, *Whimper*) en geeft een embedding van 1024 getallen.
3. Gemiddelde embedding van de babyvensters → **lineair model** (`dunstan_head.json`) → kansen voor de vijf geluiden.
4. De klok als voorkennis (tijd sinds de voeding, wakkertijd), en bevestigde eigen voorbeelden via de
   dichtstbijzijnde buren: bij 15 voorbeelden weegt dat even zwaar als het basismodel.

### Model en meting (`training/results.json`)

Het meegeleverde model is getraind op de **1214 verzamelde clips** van InfantCry-DBL v2 (CC BY 4.0). De 337
knipsels uit Dunstans eigen video's zitten er bewust niet in (auteursrecht onzeker); die dienen alleen als test.
Gebalanceerde nauwkeurigheid, kans = 20 %:

| test | score |
|---|---|
| getraind op de verzamelde clips → getest op de Dunstan-knipsels (andere bron, andere baby's) | **75 %** |
| 5-voudig binnen de verzamelde clips | 62 % |
| andersom: getraind op de Dunstan-knipsels → getest op de verzamelde clips | 58 % |

Er zit geen baby-ID in de data, dus een eerlijke "andere baby"-test kan alleen tussen de twee bronnen. Reken op
een hint die er regelmatig naast zit; de eigen bevestigingen maken hem beter voor jouw baby.

### Opnieuw trainen

Linux met Docker. Download [InfantCry-DBL v2](https://data.mendeley.com/datasets/x493z8nmwc/2) en de
YAMNet als SavedModel (`https://tfhub.dev/google/yamnet/1?tf-hub-format=compressed`, uitpakken in `<models>/yamnet`).

```sh
cd training
export DATA=/pad/naar/InfantCry-DBL   # map met metadata.csv, dunstan337/, collected1214/
export MODELS=/pad/naar/models
./run.sh convert && ./run.sh features && ./run.sh train
# daarna out/yamnet_emb.tflite en out/dunstan_head.json naar app/src/main/assets,
# out/golden.json naar app/src/test/resources (de unittest controleert dat Kotlin hetzelfde rekent als Python)
```

## Delen met je partner (`Sync.kt`)

De partnercode (12 tekens, 60 bits) wordt via PBKDF2 (150.000 rondes) een AES-256-GCM-sleutel. Elke telefoon
luistert op TCP 47320 (alleen privé-adressen) en meldt zich op UDP 47321 met een daarvan afgeleide paar-id. Alle
logregels staan in één tabel; elke wijziging krijgt een lokaal volgnummer en de partner vraagt "alles na N". Bij
een conflict wint de laatste wijziging; verwijderen is markeren. Beide telefoons moeten op hetzelfde netwerk zitten
(een gastnetwerk dat apparaten van elkaar scheidt werkt niet); buiten de deur wordt bijgewerkt zodra je weer thuis
bent. Android eist een stille melding ("Huiltje deelt") zolang de app op de achtergrond luistert.

## Bugs en ideeën

Via [Issues](../../issues): kies "Bug melden" of "Idee". **Zet er geen opnames, foto's of gegevens van je kind in.**
Een beveiligingsprobleem meld je niet openbaar maar via [SECURITY.md](SECURITY.md). Zelf iets bouwen: zie
[CONTRIBUTING.md](CONTRIBUTING.md).

## Licentie en bronnen

Code: [MIT](LICENSE). Meegeleverde modellen en data hebben hun eigen licentie, zie [NOTICE.md](NOTICE.md):
YAMNet (Google, Apache 2.0), InfantCry-DBL v2 (M. Tawfik, Mendeley Data, doi:10.17632/x493z8nmwc.2, CC BY 4.0),
WHO Child Growth Standards. "Dunstan Baby Language" is van Priscilla Dunstan / Dunstan Baby Pty Ltd; deze app staat
daar los van. Adviezen in de app volgen de Nederlandse richtlijnen (Voedingscentrum, RIVM, JGZ); vraag bij twijfel
het consultatiebureau.
