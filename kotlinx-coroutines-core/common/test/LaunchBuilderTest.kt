/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines

import kotlin.test.*

class LaunchBuilderTest : TestBase() {
    @Test
    fun testDefaultNoHandlers() = runTest {
        expect(1)
        val b0 = launchBuilder {
            expect(4)
        }
        expect(2)
        val job = b0.build()
        expect(3)
        assertRunning(job)
        yield()
        assertCompleted(job)
        finish(5)
    }

    @Test
    fun testUndispatchedNoHandlers() = runTest {
        expect(1)
        val b0 = launchBuilder(start = CoroutineStart.UNDISPATCHED) {
            expect(3)
        }
        expect(2)
        val job = b0.build()
        assertCompleted(job)
        finish(4)
    }

    @Test
    public fun testCatchNoException() = runTest {
        expect(1)
        val b0 = launchBuilder(start = CoroutineStart.UNDISPATCHED) {
            expect(4)
        }
        expect(2)
        val b1 = b0.catch<TestException> { e ->
            expectUnreached()
        }
        expect(3)
        val job = b1.build()
        assertCompleted(job)
        finish(5)
    }


    private fun assertRunning(job: Job) {
        assertTrue(job.isActive)
        assertFalse(job.isCompleted)
        assertFalse(job.isCancelled)
    }

    private fun assertCompleted(job: Job) {
        assertFalse(job.isActive)
        assertTrue(job.isCompleted)
        assertFalse(job.isCancelled)
    }
}