package site.lagnaatelier.app.data

import java.time.LocalDate

/*
 * The web repo's sample chart, as its engine computed it on 2026-11-02
 * (lib/engines/chart-service.ts buildChart, Lahiri, whole signs):
 * Ananya Mehra, 15 June 1990, 14:30, New Delhi.
 *
 * A stand-in until the app calls the live API; the reading screen labels it
 * as a sample so nobody mistakes it for their own chart.
 */
object SampleChart {
    private val jupiter = Period("Jupiter", LocalDate.of(1990, 6, 29), LocalDate.of(2006, 6, 29))
    private val saturn = Period("Saturn", LocalDate.of(2006, 6, 29), LocalDate.of(2025, 6, 29))
    private val mercury = Period("Mercury", LocalDate.of(2025, 6, 29), LocalDate.of(2042, 6, 29))
    private val ketu = Period("Ketu", LocalDate.of(2042, 6, 29), LocalDate.of(2049, 6, 29))
    private val venus = Period("Venus", LocalDate.of(2049, 6, 29), LocalDate.of(2069, 6, 29))

    val chart = ChartSummary(
        name = "Ananya Mehra",
        place = "New Delhi",
        birthDate = LocalDate.of(1990, 6, 15),
        birthTime = "14:30",
        tradition = "Lahiri · whole sign",
        ascendant = Sign.Virgo,
        ascendantDegree = 28.7507,
        moonSign = Sign.Aquarius,
        moonDegree = 19.97,
        nakshatra = "Shatabhisha",
        pada = 4,
        nakshatraLord = "Rahu",
        mahadasha = mercury,
        antardasha = Period("Mercury", LocalDate.of(2025, 6, 29), LocalDate.of(2027, 11, 25)),
        mahadashas = listOf(jupiter, saturn, mercury, ketu, venus),
        placements = listOf(
            Placement("Su", Sign.Gemini),
            Placement("Mo", Sign.Aquarius),
            Placement("Me", Sign.Taurus),
            Placement("Ve", Sign.Aries),
            Placement("Ma", Sign.Pisces),
            Placement("Ju", Sign.Gemini),
            Placement("Sa", Sign.Capricorn),
            Placement("Ra", Sign.Capricorn),
            Placement("Ke", Sign.Cancer),
        ),
        findings = listOf(
            Finding(
                category = "Foundation",
                headline = "How you come across before you say anything",
                body = "Virgo rising. Mercury, which rules it, carries that approach into your 9th house.",
            ),
            Finding(category = "Core", headline = "What you are actually driving at"),
        ),
    )

    /* The sky over New Delhi at 17:30 on 2026-11-02, from the same engine. */
    val today = TodaySky(
        date = LocalDate.of(2026, 11, 2),
        moonSign = Sign.Cancer,
        moonNakshatra = "Ashlesha",
        sunSign = Sign.Libra,
    )
}
