# Meebouwen

Leuk! Een paar afspraken:

- **Eerst een issue** voor alles wat groter is dan een kleine fix, zodat we niet langs elkaar heen werken.
- **Bouwen en testen**: `./gradlew assembleDebug testDebugUnitTest`. Alle tests moeten slagen. Nieuwe logica krijgt
  een test (zie `BabyTest.kt` en `DelenTest.kt`); voor schermen kun je `SchermTest.kt` uitbreiden, die schrijft
  afbeeldingen naar `app/build/schermen/`.
- **Stijl**: Kotlin + Jetpack Compose, Nederlandse namen en teksten, zo min mogelijk code en geen nieuwe
  afhankelijkheden als het met wat er al is kan.
- **Privacy is een harde eis**: geen analytics, geen server, geen nieuwe internettoegang. Alles blijft op de
  telefoon; delen alleen rechtstreeks tussen telefoons op het eigen netwerk.
- **Gezondheidsadvies**: verwijs naar een Nederlandse bron (Voedingscentrum, RIVM, JGZ, NHG) in de pull request.
- **Geen opnames of gegevens van kinderen** in issues, pull requests of testdata.
