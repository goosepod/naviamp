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

    private data class TestResource(val id: Int)
}
