package io.github.leerumax.huiltje

/**
 * De vijf Dunstan-geluiden (0 tot ~3 maanden), met wat je dan kunt doen. Volgorde = 'classes' in dunstan_head.json.
 * [kleur] = ARGB, voor de balkjes en (doorschijnend) de kaartjes.
 */
data class Sound(
    val key: String, val name: String, val meaning: String, val emoji: String, val kleur: Long,
    val hoorJe: String, val doen: List<String>,
)

val SOUNDS = listOf(
    Sound(
        "neh", "Neh", "Honger", "🍼", 0xFFFFB877,
        "Een 'n' aan het begin: de tong gaat tegen het gehemelte, zoals bij zuigen.",
        listOf(
            "Voeden (borst of fles).",
            "Kijk of je baby zoekt met het mondje, smakt of op de handjes zuigt.",
            "Huilt je baby door tijdens het drinken: even laten boeren en opnieuw proberen.",
        ),
    ),
    Sound(
        "owh", "Owh", "Moe", "😴", 0xFFB39DEB,
        "Klinkt als een geeuw: mond rond en open, een 'o'-klank.",
        listOf(
            "Prikkels omlaag: licht gedempt, geluid zacht, niet meer spelen.",
            "Inbakeren, wiegen of zacht 'sssh' bij het oortje.",
            "Op tijd naar bed: na een geeuw of wrijven in de oogjes is het moment er.",
        ),
    ),
    Sound(
        "heh", "Heh", "Ongemak", "😣", 0xFF7FCFB4,
        "Een korte 'h' met veel adem, wat hijgerig.",
        listOf(
            "Luier checken.",
            "Te warm of te koud? Voel in het nekje.",
            "Kriebelt er iets: label, naad, haartje om een teentje of vingertje?",
            "Andere houding of even oppakken.",
        ),
    ),
    Sound(
        "eairh", "Eairh", "Krampjes / windjes", "💨", 0xFFF7D26A,
        "Kreunend en geperst, vaak met opgetrokken beentjes.",
        listOf(
            "Buikje zacht masseren, met de klok mee.",
            "Fietsbeentjes: de knietjes om de beurt naar het buikje.",
            "Op de buik over je onderarm dragen ('tijgertje in de boom').",
            "Kan ook betekenen dat er een poepluier aankomt.",
        ),
    ),
    Sound(
        "eh", "Eh", "Boertje", "🎈", 0xFF8EC5EE,
        "Een kort 'eh' uit de borst, alsof er lucht omhoog wil.",
        listOf(
            "Rechtop tegen je schouder en zacht op het rugje kloppen of wrijven.",
            "Of rechtop op schoot, kinnetje in je hand, rugje masseren.",
            "Tijdens het voeden een pauze voor een boertje inbouwen.",
        ),
    ),
)

/** Onderaan Meer. Gebaseerd op het gewone huisartsadvies voor baby's jonger dan 3 maanden. */
val BEL_DE_HUISARTS = listOf(
    "Koorts: 38 °C of hoger (onder de 3 maanden altijd meteen bellen).",
    "Drinkt slecht of veel minder natte luiers.",
    "Huilt anders dan anders: schel, kreunend, of is juist slap en suf.",
    "Benauwd, blauwe lipjes, of spuugt alles uit.",
    "Bij direct gevaar: bel 112.",
)
