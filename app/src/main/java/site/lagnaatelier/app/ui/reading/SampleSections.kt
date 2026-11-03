package site.lagnaatelier.app.ui.reading

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.reading.ReadingSections
import site.lagnaatelier.app.data.reading.ReadingSectionsParser

/*
 * The sample chart's longer sections. SampleChart is a plain object with no
 * way to reach resources, so the parts of the same live response these
 * sections read (res/raw/sample_reading_sections.json, cut down from the
 * parser's test fixture chart-ananya-mehra.json) are read here, once per
 * composition of the reading. A resource that cannot be read is a sample
 * without those sections, never a crash.
 */
@Composable
internal fun rememberSampleSections(): ReadingSections {
    val resources = LocalContext.current.resources
    return remember(resources) {
        runCatching {
            resources.openRawResource(R.raw.sample_reading_sections).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.map(ReadingSectionsParser::parse).getOrDefault(ReadingSections())
    }
}
