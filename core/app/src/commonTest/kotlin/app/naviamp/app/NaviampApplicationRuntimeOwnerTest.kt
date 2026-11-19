package app.naviamp.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class NaviampApplicationRuntimeOwnerTest {
    @Test
    fun runtimeIsLazyAndSharedAcrossSceneConnectionOrdersAndReattachments() {
        for (carConnectsFirst in listOf(true, false)) {
            var creations = 0
            val owner = NaviampApplicationRuntimeOwner(create = { Any().also { creations++ } }, dispose = {})
            assertEquals(0, creations)
            val firstScene = owner.getOrCreate()
            val secondScene = owner.getOrCreate()
            val phone = if (carConnectsFirst) secondScene else firstScene
            val car = if (carConnectsFirst) firstScene else secondScene
            assertSame(phone, car)
            assertSame(car, owner.getOrCreate(), "Reattaching a scene must reuse the active player")
            assertEquals(1, creations)
        }
    }

    @Test
    fun shutdownDisposesTheRuntimeOnceAndPreventsRecreation() {
        val disposed = mutableListOf<Any>()
        val owner = NaviampApplicationRuntimeOwner(create = { Any() }, dispose = disposed::add)
        val runtime = owner.getOrCreate()
        owner.close()
        owner.close()
        assertEquals(listOf(runtime), disposed)
        assertFailsWith<IllegalStateException> { owner.getOrCreate() }
    }

    @Test
    fun shutdownBeforeFirstSceneDoesNotCreateARuntime() {
        var creations = 0
        val owner = NaviampApplicationRuntimeOwner(create = { Any().also { creations++ } }, dispose = {})
        owner.close()
        owner.close()
        assertEquals(0, creations)
        assertFailsWith<IllegalStateException> { owner.getOrCreate() }
    }

    @Test
    fun failedCreationCanRetryWithoutDisposingAnUncreatedRuntime() {
        var attempts = 0
        var disposals = 0
        val owner = NaviampApplicationRuntimeOwner(
            create = { if (++attempts == 1) error("Fixture failure") else Any() },
            dispose = { disposals++ },
        )
        assertFailsWith<IllegalStateException> { owner.getOrCreate() }
        assertSame(owner.getOrCreate(), owner.getOrCreate())
        assertEquals(2, attempts)
        assertEquals(0, disposals)
        owner.close()
        assertEquals(1, disposals)
    }

    @Test
    fun reentrantCreationCannotConstructASecondRuntimeOrCloseTheOwner() {
        lateinit var owner: NaviampApplicationRuntimeOwner<Any>
        var creations = 0
        owner = NaviampApplicationRuntimeOwner(create = {
            creations++
            assertFailsWith<IllegalStateException> { owner.getOrCreate() }
            assertFailsWith<IllegalStateException> { owner.close() }
            Any()
        }, dispose = {})
        assertSame(owner.getOrCreate(), owner.getOrCreate())
        assertEquals(1, creations)
    }

    @Test
    fun disposalFailureDoesNotReopenOrDisposeTheRuntimeTwice() {
        var disposals = 0
        val owner = NaviampApplicationRuntimeOwner(create = { Any() }, dispose = {
            disposals++
            error("Fixture disposal failure")
        })
        owner.getOrCreate()
        assertFailsWith<IllegalStateException> { owner.close() }
        owner.close()
        assertEquals(1, disposals)
        assertFailsWith<IllegalStateException> { owner.getOrCreate() }
    }
}
