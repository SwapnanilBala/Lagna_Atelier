package site.lagnaatelier.app.data

import java.time.LocalDate

/*
 * The web repo's sample chart, as the live site computed it on 2026-11-02
 * (GET https://lagnaatelier.site/api/chart, Lahiri, whole signs):
 * Ananya Mehra, 15 June 1990, 14:30, New Delhi. The same response is the
 * parser's test fixture, app/src/test/resources/chart-ananya-mehra.json.
 *
 * Shown, and labelled as a sample, when the app has no chart of the reader's.
 */
object SampleChart {
    private fun period(lord: String, start: String, end: String) = Period(lord, LocalDate.parse(start), LocalDate.parse(end))

    private val mercury = period("Mercury", "2025-06-29", "2042-06-29")

    val chart = ChartSummary(
        name = "Ananya Mehra",
        place = "New Delhi",
        birthDate = LocalDate.of(1990, 6, 15),
        birthTime = "14:30",
        tradition = "Lahiri Classic",
        ascendant = Sign.Virgo,
        ascendantDegree = 28.7507,
        moonSign = Sign.Aquarius,
        moonDegree = 19.97,
        nakshatra = "Shatabhisha",
        pada = 4,
        nakshatraLord = "Rahu",
        mahadasha = mercury,
        antardasha = period("Mercury", "2025-06-29", "2027-11-25"),
        mahadashas = listOf(
            period("Rahu", "1990-06-15", "1990-06-29"),
            period("Jupiter", "1990-06-29", "2006-06-29"),
            period("Saturn", "2006-06-29", "2025-06-29"),
            mercury,
            period("Ketu", "2042-06-29", "2049-06-29"),
            period("Venus", "2049-06-29", "2069-06-29"),
            period("Sun", "2069-06-29", "2075-06-29"),
        ),
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
        navamsa = listOf(
            Placement("Su", Sign.Libra),
            Placement("Mo", Sign.Pisces),
            Placement("Me", Sign.Aries),
            Placement("Ve", Sign.Scorpio),
            Placement("Ma", Sign.Sagittarius),
            Placement("Ju", Sign.Aries),
            Placement("Sa", Sign.Capricorn),
            Placement("Ra", Sign.Taurus),
            Placement("Ke", Sign.Scorpio),
        ),
        navamsaAscendant = Sign.Virgo,
        findings = listOf(
            Finding(
                "Foundation",
                "How your working life is built",
                "Communication-centered career path. Journalism, writing, teaching, marketing, or trading leverage your intellectual agility.",
            ),
            Finding(
                "Foundation",
                "What you are actually looking for in a partner",
                "Romantic, empathic and spiritually connected in love. You seek a soulmate bond, creative expression, and emotional transcendence.",
            ),
            Finding(
                "Foundation",
                "The engine your life actually runs on",
                "The planet in charge of your whole chart is Mercury, and where it sits decides where your life keeps steering: beliefs, wisdom and long-distance journeys.",
            ),
            Finding(
                "Foundation",
                "How you come across before you say anything",
                "Analytical, service-oriented and precision-focused. Systems, routines and quality standards matter deeply.",
            ),
        ),
    )

    /* For previews only. The landing screen shows the live sky, or says it has none. */
    val today = TodaySky(
        date = LocalDate.of(2026, 11, 2),
        moonSign = Sign.Cancer,
        moonNakshatra = "Ashlesha",
        sunSign = Sign.Libra,
    )
}
