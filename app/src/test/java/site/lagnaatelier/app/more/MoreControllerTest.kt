package site.lagnaatelier.app.more

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import site.lagnaatelier.app.ui.more.MoreController
import site.lagnaatelier.app.ui.more.MoreScreen
import site.lagnaatelier.app.ui.more.TourMemory
import site.lagnaatelier.app.ui.more.TourStep
import site.lagnaatelier.app.ui.reading.AppTab

class MoreControllerTest {
    private class Memory(var seen: Boolean = false) : TourMemory {
        override fun seen() = seen
        override fun markSeen() {
            seen = true
        }
    }

    private fun controller(memory: TourMemory) = MoreController.restored(memory, emptyList())

    @Test
    fun offersTheTourOnceOnAFirstVisit() {
        val memory = Memory()
        val more = controller(memory)
        more.offerTour(seen = memory.seen())
        assertEquals(0, more.tourStep)
        repeat(TourStep.entries.size - 1) { more.nextTourStep() }
        assertEquals(TourStep.entries.lastIndex, more.tourStep)
        more.nextTourStep()
        assertNull(more.tourStep)
        assertTrue(memory.seen)

        val again = controller(memory)
        again.offerTour(seen = memory.seen())
        assertNull(again.tourStep)
    }

    @Test
    fun skippingCountsAsSeen() {
        val memory = Memory()
        val more = controller(memory)
        more.offerTour(seen = false)
        more.nextTourStep()
        more.finishTour()
        assertNull(more.tourStep)
        assertTrue(memory.seen)
    }

    @Test
    fun helpTakesTheTourAgainFromTheStart() {
        val more = controller(Memory(seen = true))
        more.open(MoreScreen.Help)
        more.startTour()
        assertNull(more.screen)
        assertEquals(0, more.tourStep)
    }

    @Test
    fun theTourVisitsEveryTabStartingWithTheReading() {
        assertEquals(AppTab.Reading, TourStep.entries.first().tab)
        assertEquals(AppTab.entries.toSet(), TourStep.entries.map { it.tab }.toSet())
    }

    @Test
    fun backLeavesAVargaForTheAtlasThenTheAtlasForTheReading() {
        val more = controller(Memory(seen = true))
        more.open(MoreScreen.Vargas)
        more.division = 9
        more.back()
        assertEquals(MoreScreen.Vargas, more.screen)
        assertNull(more.division)
        more.back()
        assertNull(more.screen)
    }

    @Test
    fun survivesARotation() {
        val memory = Memory()
        val more = controller(memory)
        more.open(MoreScreen.Vargas)
        more.division = 24
        val restored = MoreController.restored(memory, listOf("Vargas", 24, 2))
        assertEquals(MoreScreen.Vargas, restored.screen)
        assertEquals(24, restored.division)
        assertEquals(2, restored.tourStep)
        assertFalse(MoreController.restored(memory, listOf("Nowhere", -1, 99)).let { it.screen != null || it.tourStep != null })
    }
}
