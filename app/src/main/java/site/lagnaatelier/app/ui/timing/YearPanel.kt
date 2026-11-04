package site.lagnaatelier.app.ui.timing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.formatDegree
import site.lagnaatelier.app.data.monthYear
import site.lagnaatelier.app.data.timing.TimingFormat
import site.lagnaatelier.app.data.timing.YearChart
import site.lagnaatelier.app.data.timing.YearLordReason
import site.lagnaatelier.app.ui.components.Panel
import site.lagnaatelier.app.ui.reading.planetName
import site.lagnaatelier.app.ui.reading.signName

/* The varshaphal: the chart for the moment each year the Sun comes back to where it stood at birth. */
@Composable
internal fun YearPanel(holder: TimingHolder, onRetry: () -> Unit) {
    val birth = holder.details.date
    val start = birth.withYear(holder.targetYear)
    Panel(Modifier.fillMaxWidth()) {
        PanelHead(stringResource(R.string.timing_year_title)) {
            Meta(stringResource(R.string.timing_year_span, start.monthYear(), start.plusYears(1).monthYear()))
        }
        LoadState(holder.year, stringResource(R.string.timing_year_loading), onRetry) { year ->
            Year(year)
        }
    }
}

@Composable
private fun Year(year: YearChart) {
    val ordinals = stringArrayResource(R.array.timing_ordinals)
    fun ordinal(house: Int) = ordinals.getOrNull(house - 1) ?: house.toString()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FlatTile(stringResource(R.string.timing_year_rising), signName(year.rising), formatDegree(year.risingDegree))
                FlatTile(
                    stringResource(R.string.timing_year_lord),
                    planetName(year.yearLord),
                    stringResource(
                        when (year.yearLordReason) {
                            YearLordReason.RulesMuntha -> R.string.timing_lord_rules_muntha
                            YearLordReason.RulesYearRising -> R.string.timing_lord_rules_year_rising
                            YearLordReason.RulesBirthRising -> R.string.timing_lord_rules_birth_rising
                            YearLordReason.Other -> R.string.timing_lord_other
                        },
                    ),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FlatTile(
                    stringResource(R.string.timing_year_muntha),
                    signName(year.munthaSign),
                    stringResource(R.string.timing_house, ordinal(year.munthaHouse)),
                )
                FlatTile(
                    stringResource(R.string.timing_year_profection),
                    stringResource(R.string.timing_profection_year, year.profectionYear),
                    stringResource(R.string.timing_profection_meta, ordinal(year.profectionHouse), signName(year.profectionSign)),
                )
            }
        }
        if (year.summary.isNotBlank()) {
            val (title, body) = TimingFormat.splitSummary(year.summary)
            Lead(title, body)
        }
    }
}
