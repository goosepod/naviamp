package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class NaviampOwnedResourceTest {
    @Test
    fun repeatedReplacementReleasesEverySupersededResourceExactlyOnce() {
        val released = mutableListOf<TestResource>()
        val owner = NaviampOwnedResource<TestResource>(released::add)
        val resources = List(1_000) { TestResource(it) }

        resources.forEach { resource -> assertSame(resource, owner.replace(resource)) }
        owner.close()
        owner.close()

        assertEquals(resources, released)
    }

    @Test
    fun retainingTheSameInstanceDoesNotReleaseItUntilShutdown() {
        val released = mutableListOf<TestResource>()
        val owner = NaviampOwnedResource<TestResource>(released::add)
        val resource = TestResource(1)

        owner.replace(resource)
        owner.replace(resource)
        assertEquals(emptyList(), released)

        owner.close()
        assertEquals(listOf(resource), released)
    }

    @Test
    fun resourcesArrivingAfterShutdownAreReleasedImmediately() {
        val released = mutableListOf<TestResource>()
        val owner = NaviampOwnedResource<TestResource>(released::add)
        val resource = TestResource(1)

        owner.close()

        assertNull(owner.replace(resource))
        assertEquals(listOf(resource), released)
    }

    @Test
    fun deferredReplacementKeepsSupersededResourceAliveUntilRenderHandoff() {
        val released = mutableListOf<TestResource>()
        val owner = NaviampOwnedResource<TestResource>(released::add)
        val first = TestResource(1)
        val second = TestResource(2)

        owner.replaceDeferred(first)
        val replacement = owner.replaceDeferred(second)

        assertSame(second, replacement.current)
        assertSame(first, replacement.retired)
        assertEquals(emptyList(), released)

        owner.releaseRetired(first)
        owner.releaseRetired(first)
        assertEquals(listOf(first), released)

        owner.close()
        assertEquals(listOf(first, second), released)
    }

    @Test
    fun shutdownReleasesCurrentAndEveryPendingRetirementExactlyOnce() {
        val released = mutableListOf<TestResource>()
        val owner = NaviampOwnedResource<TestResource>(released::add)
        val resources = List(1_000) { TestResource(it) }

        resources.forEach(owner::replaceDeferred)
        owner.close()
        resources.forEach(owner::releaseRetired)

        assertEquals(resources.size, released.size)
        assertEquals(resources.toSet(), released.toSet())
    }

    @Test
    fun revivingPendingResourceCancelsItsRetirement() {
        val released = mutableListOf<TestResource>()
        val owner = NaviampOwnedResource<TestResource>(released::add)
        val first = TestResource(1)
        val second = TestResource(2)

        owner.replaceDeferred(first)
        owner.replaceDeferred(second)
        owner.replaceDeferred(first)
        owner.releaseRetired(first)

        assertEquals(emptyList(), released)
        owner.close()
        assertEquals(listOf(first, second), released)
    }

    private data class TestResource(val id: Int)
}
