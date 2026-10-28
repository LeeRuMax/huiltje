package io.github.leerumax.huiltje

/** Wat is normaal rond deze leeftijd. Algemene richtlijnen (JGZ, RIVM, Voedingscentrum); elk kind gaat in eigen tempo. */
class Week(val vanDag: Int, val titel: String, val tekst: String)

val WEKEN = listOf(
    Week(
        0, "Week 1: de eerste dagen",
        "Afvallen is normaal, tot ongeveer 7 tot 10 % van het geboortegewicht. Rond dag 3 tot 5 komt de melk op gang en " +
            "drinkt je baby vaak, ook 's nachts. In de eerste week komt de hielprik. Geboren tussen oktober en maart? Dan krijgt " +
            "je baby binnen 2 weken de RS-virusprik.",
    ),
    Week(
        7, "Week 2: weer op gewicht",
        "Rond 10 tot 14 dagen zijn de meeste baby's weer op hun geboortegewicht. Vanaf dag 8: elke dag vitamine D, en bij " +
            "borstvoeding ook vitamine K. Het navelstompje valt meestal in de eerste twee weken af. Rond deze tijd komt de " +
            "jeugdverpleegkundige op huisbezoek. Veel natte luiers (5 of meer zware per dag) = genoeg drinken.",
    ),
    Week(
        14, "Week 3: groeispurtje",
        "Rond 2 tot 3 weken willen veel baby's ineens veel vaker drinken, soms vlak achter elkaar (clusteren). Dat is " +
            "tijdelijk en helpt de melkproductie mee te groeien.",
    ),
    Week(
        21, "Week 4: eerste keer consultatiebureau",
        "Rond 4 weken het eerste bezoek aan het consultatiebureau: wegen, meten en al je vragen. Op de buik tilt je baby " +
            "het hoofdje al even op. Ogen volgen een gezicht van dichtbij (20 tot 30 cm).",
    ),
    Week(
        28, "Week 5: meer huilen is normaal",
        "Het huilen neemt bij veel baby's toe richting week 6, vaak 's avonds. Wakker blijven lukt nu zo'n 60 tot 90 " +
            "minuten. Prikkels laag houden en om de beurt troosten helpt.",
    ),
    Week(
        35, "Week 6: huilpiek en eerste lachje",
        "Rond 6 weken huilen veel baby's het meest; daarna wordt het meestal minder. Vaak komt nu ook het eerste echte " +
            "lachje. Tussen 6 en 9 weken de eerste vaccinatie (rotavirus, en DKTP-Hib-HepB als de moeder in de " +
            "zwangerschap geen kinkhoestprik had). Weer een groeispurtje is normaal.",
    ),
    Week(
        42, "Week 7: kirren en volgen",
        "Klinkers als 'ooh' en 'aah', en je baby volgt je met de ogen. Praat terug: dat vindt je baby prachtig.",
    ),
    Week(
        49, "Week 8: consultatiebureau",
        "Rond 8 weken weer naar het consultatiebureau. De handjes gaan open en worden ontdekt.",
    ),
    Week(
        56, "Week 9 en 10: rustiger",
        "Het huilen neemt bij de meeste baby's af. Sommige baby's slapen 's nachts een stuk langer achter elkaar, " +
            "andere nog niet; beide is normaal.",
    ),
    Week(
        70, "Week 11 en 12: bijna 3 maanden",
        "Rond 3 maanden: vaccinaties (rotavirus, DKTP-Hib-HepB en pneumokokken). Vitamine K stopt na week 12; vitamine D " +
            "gaat door tot 4 jaar. De vijf geluidjes verdwijnen langzaam, dus Huiltje wordt minder zeker.",
    ),
    Week(
        84, "Maand 3 en 4",
        "Lacht hardop, grijpt naar speelgoed en brengt alles naar de mond. Rollen van buik naar rug kan nu of later. " +
            "Rond 4 maanden weer consultatiebureau.",
    ),
    Week(
        140, "Maand 5",
        "Rond 5 maanden vaccinaties (DKTP-Hib-HepB en pneumokokken). Tussen 4 en 6 maanden kun je oefenen met kleine " +
            "hapjes; melk blijft de hoofdvoeding. Vraag het consultatiebureau wat bij jullie past.",
    ),
    Week(
        168, "Maand 6",
        "Zit met steun, brabbelt en 'praat' terug. Vast eten wordt langzaam meer. Rond 6 maanden consultatiebureau.",
    ),
)

fun weekVan(dagen: Long?): Week? = dagen?.let { d -> WEKEN.lastOrNull { it.vanDag <= d } }

/** Voedingscentrum, "Zo bewaar en ontdooi je moedermelk" (2023). */
val BEWAREN = listOf(
    "Koelkast (4 °C, achterin): 3 dagen.",
    "Vriezer met 3 of 4 sterren: 6 maanden. Vries meteen na het kolven in, in kleine porties.",
    "Vriesvak van de koelkast (1 of 2 sterren): 2 weken.",
    "Ontdooid (het liefst in de koelkast): binnen 24 uur gebruiken. Restjes opgewarmde melk weggooien.",
    "Wateriger, vlokjes of wat zurig ruiken na ontdooien is normaal.",
)

// WHO Child Growth Standards (2006), gewicht en lengte naar leeftijd: L, M, S per week, week 0 t/m 52.
val WFA_JONGEN = floatArrayOf(0.3487f, 3.3464f, 0.14602f, 0.2776f, 3.4879f, 0.14483f, 0.2581f, 3.7529f, 0.14142f, 0.2442f, 4.0603f, 0.13807f, 0.2331f, 4.3671f, 0.13497f, 0.2237f, 4.659f, 0.13215f, 0.2155f, 4.9303f, 0.1296f, 0.2081f, 5.1817f, 0.12729f, 0.2014f, 5.4149f, 0.1252f, 0.1952f, 5.6319f, 0.1233f, 0.1894f, 5.8346f, 0.12157f, 0.184f, 6.0242f, 0.12001f, 0.1789f, 6.2019f, 0.1186f, 0.174f, 6.369f, 0.11732f, 0.1694f, 6.5265f, 0.11618f, 0.1651f, 6.6755f, 0.11516f, 0.1609f, 6.8168f, 0.11425f, 0.1568f, 6.9513f, 0.11345f, 0.1529f, 7.0794f, 0.11274f, 0.1492f, 7.2018f, 0.11212f, 0.1456f, 7.3187f, 0.11158f, 0.1421f, 7.4307f, 0.11111f, 0.1387f, 7.5379f, 0.1107f, 0.1354f, 7.6406f, 0.11035f, 0.1322f, 7.7394f, 0.11005f, 0.129f, 7.8344f, 0.1098f, 0.126f, 7.926f, 0.10959f, 0.123f, 8.0145f, 0.10942f, 0.1201f, 8.1f, 0.10927f, 0.1173f, 8.1826f, 0.10915f, 0.1145f, 8.2627f, 0.10906f, 0.1118f, 8.3404f, 0.10898f, 0.1092f, 8.4157f, 0.10892f, 0.1066f, 8.489f, 0.10887f, 0.104f, 8.5603f, 0.10884f, 0.1015f, 8.6299f, 0.10882f, 0.0991f, 8.6978f, 0.1088f, 0.0967f, 8.7642f, 0.1088f, 0.0943f, 8.8292f, 0.1088f, 0.092f, 8.8929f, 0.10881f, 0.0897f, 8.9555f, 0.10882f, 0.0875f, 9.0169f, 0.10884f, 0.0852f, 9.0772f, 0.10887f, 0.0831f, 9.1366f, 0.10889f, 0.0809f, 9.1951f, 0.10892f, 0.0788f, 9.2528f, 0.10895f, 0.0767f, 9.3097f, 0.10899f, 0.0746f, 9.3659f, 0.10903f, 0.0726f, 9.4215f, 0.10907f, 0.0706f, 9.4765f, 0.10911f, 0.0686f, 9.531f, 0.10915f, 0.0667f, 9.5849f, 0.1092f, 0.0648f, 9.6384f, 0.10925f)
val WFA_MEISJE = floatArrayOf(0.3809f, 3.2322f, 0.14171f, 0.2671f, 3.3388f, 0.146f, 0.2304f, 3.5693f, 0.14339f, 0.2024f, 3.8352f, 0.1406f, 0.1789f, 4.0987f, 0.13805f, 0.1582f, 4.3476f, 0.13583f, 0.1395f, 4.5793f, 0.13392f, 0.1224f, 4.795f, 0.13228f, 0.1065f, 4.9959f, 0.13087f, 0.0918f, 5.1842f, 0.12966f, 0.0779f, 5.3618f, 0.12861f, 0.0648f, 5.5295f, 0.1277f, 0.0525f, 5.6883f, 0.12691f, 0.0407f, 5.8393f, 0.12622f, 0.0295f, 5.9831f, 0.12561f, 0.0188f, 6.1202f, 0.12507f, 0.0086f, 6.2511f, 0.12459f, -0.0013f, 6.3761f, 0.12417f, -0.0107f, 6.4956f, 0.1238f, -0.0198f, 6.6099f, 0.12346f, -0.0285f, 6.7192f, 0.12317f, -0.0369f, 6.8238f, 0.12291f, -0.045f, 6.9241f, 0.12268f, -0.0529f, 7.0205f, 0.12248f, -0.0605f, 7.1133f, 0.12231f, -0.0678f, 7.2029f, 0.12217f, -0.0749f, 7.2894f, 0.12205f, -0.0818f, 7.3732f, 0.12196f, -0.0885f, 7.4543f, 0.12188f, -0.095f, 7.533f, 0.12183f, -0.1012f, 7.6094f, 0.12179f, -0.1073f, 7.6838f, 0.12177f, -0.1132f, 7.7562f, 0.12176f, -0.119f, 7.8268f, 0.12177f, -0.1245f, 7.8957f, 0.12179f, -0.1299f, 7.9629f, 0.12181f, -0.1352f, 8.0286f, 0.12185f, -0.1403f, 8.0928f, 0.12189f, -0.1452f, 8.1557f, 0.12193f, -0.1501f, 8.2172f, 0.12198f, -0.1547f, 8.2776f, 0.12203f, -0.1593f, 8.3369f, 0.12209f, -0.1637f, 8.3952f, 0.12214f, -0.168f, 8.4526f, 0.1222f, -0.1722f, 8.5092f, 0.12226f, -0.1763f, 8.565f, 0.12231f, -0.1802f, 8.6201f, 0.12237f, -0.1841f, 8.6745f, 0.12243f, -0.1878f, 8.7283f, 0.12248f, -0.1914f, 8.7816f, 0.12253f, -0.195f, 8.8344f, 0.12258f, -0.1984f, 8.8868f, 0.12262f, -0.2018f, 8.9388f, 0.12267f)
val LFA_JONGEN = floatArrayOf(1.0f, 49.8842f, 0.03795f, 1.0f, 51.1152f, 0.03723f, 1.0f, 52.3461f, 0.03652f, 1.0f, 53.3905f, 0.03609f, 1.0f, 54.3881f, 0.0357f, 1.0f, 55.3374f, 0.03534f, 1.0f, 56.2357f, 0.03501f, 1.0f, 57.0851f, 0.0347f, 1.0f, 57.8889f, 0.03442f, 1.0f, 58.6536f, 0.03416f, 1.0f, 59.3872f, 0.03392f, 1.0f, 60.0894f, 0.03369f, 1.0f, 60.7605f, 0.03348f, 1.0f, 61.4013f, 0.03329f, 1.0f, 62.013f, 0.03311f, 1.0f, 62.5966f, 0.03294f, 1.0f, 63.1533f, 0.03278f, 1.0f, 63.6842f, 0.03263f, 1.0f, 64.1906f, 0.03249f, 1.0f, 64.6744f, 0.03236f, 1.0f, 65.138f, 0.03223f, 1.0f, 65.5834f, 0.03212f, 1.0f, 66.0121f, 0.03201f, 1.0f, 66.4256f, 0.03191f, 1.0f, 66.8254f, 0.03182f, 1.0f, 67.2132f, 0.03174f, 1.0f, 67.5904f, 0.03166f, 1.0f, 67.9583f, 0.03159f, 1.0f, 68.3177f, 0.03152f, 1.0f, 68.6698f, 0.03147f, 1.0f, 69.0152f, 0.03141f, 1.0f, 69.3549f, 0.03137f, 1.0f, 69.6896f, 0.03133f, 1.0f, 70.0197f, 0.03129f, 1.0f, 70.3457f, 0.03126f, 1.0f, 70.6683f, 0.03123f, 1.0f, 70.9876f, 0.03121f, 1.0f, 71.3039f, 0.0312f, 1.0f, 71.6171f, 0.03118f, 1.0f, 71.9273f, 0.03117f, 1.0f, 72.2347f, 0.03117f, 1.0f, 72.539f, 0.03117f, 1.0f, 72.8402f, 0.03117f, 1.0f, 73.1384f, 0.03118f, 1.0f, 73.4337f, 0.03119f, 1.0f, 73.7261f, 0.0312f, 1.0f, 74.0157f, 0.03122f, 1.0f, 74.3026f, 0.03124f, 1.0f, 74.5868f, 0.03126f, 1.0f, 74.8686f, 0.03128f, 1.0f, 75.1479f, 0.03131f, 1.0f, 75.425f, 0.03134f, 1.0f, 75.6999f, 0.03137f)
val LFA_MEISJE = floatArrayOf(1.0f, 49.1477f, 0.0379f, 1.0f, 50.3298f, 0.03742f, 1.0f, 51.512f, 0.03694f, 1.0f, 52.4695f, 0.03669f, 1.0f, 53.3809f, 0.03647f, 1.0f, 54.2454f, 0.03627f, 1.0f, 55.0642f, 0.03609f, 1.0f, 55.8406f, 0.03593f, 1.0f, 56.5767f, 0.03578f, 1.0f, 57.2761f, 0.03564f, 1.0f, 57.9436f, 0.03552f, 1.0f, 58.5816f, 0.0354f, 1.0f, 59.1922f, 0.0353f, 1.0f, 59.7773f, 0.0352f, 1.0f, 60.3393f, 0.03511f, 1.0f, 60.8795f, 0.03503f, 1.0f, 61.3991f, 0.03495f, 1.0f, 61.8988f, 0.03488f, 1.0f, 62.3795f, 0.03482f, 1.0f, 62.8423f, 0.03476f, 1.0f, 63.2884f, 0.03471f, 1.0f, 63.7196f, 0.03466f, 1.0f, 64.137f, 0.03461f, 1.0f, 64.5422f, 0.03457f, 1.0f, 64.9366f, 0.03454f, 1.0f, 65.3214f, 0.03451f, 1.0f, 65.6978f, 0.03448f, 1.0f, 66.0668f, 0.03446f, 1.0f, 66.429f, 0.03444f, 1.0f, 66.785f, 0.03442f, 1.0f, 67.1355f, 0.03441f, 1.0f, 67.481f, 0.0344f, 1.0f, 67.8219f, 0.0344f, 1.0f, 68.1585f, 0.0344f, 1.0f, 68.4911f, 0.0344f, 1.0f, 68.82f, 0.0344f, 1.0f, 69.1452f, 0.03441f, 1.0f, 69.4672f, 0.03441f, 1.0f, 69.7858f, 0.03443f, 1.0f, 70.1015f, 0.03444f, 1.0f, 70.4142f, 0.03445f, 1.0f, 70.7241f, 0.03447f, 1.0f, 71.0314f, 0.03449f, 1.0f, 71.3359f, 0.03451f, 1.0f, 71.6378f, 0.03454f, 1.0f, 71.9372f, 0.03456f, 1.0f, 72.234f, 0.03459f, 1.0f, 72.5284f, 0.03462f, 1.0f, 72.8203f, 0.03465f, 1.0f, 73.1099f, 0.03468f, 1.0f, 73.3971f, 0.03471f, 1.0f, 73.682f, 0.03475f, 1.0f, 73.9647f, 0.03478f)
