package com.example.instaanalyzer

import org.junit.Assert.assertEquals
import org.junit.Test

class RelationshipAnalyticsTest {
    @Test fun comparesFollowerAndFollowingSnapshots() {
        val result = RelationshipAnalytics.compare(
            previousFollowers = setOf("a", "b", "c"),
            currentFollowers = setOf("b", "c", "d"),
            previousFollowing = setOf("x", "y"),
            currentFollowing = setOf("y", "z")
        )
        assertEquals(setOf("d"), result.newFollowers)
        assertEquals(setOf("a"), result.lostFollowers)
        assertEquals(setOf("z"), result.newFollowing)
        assertEquals(setOf("x"), result.removedFollowing)
    }
}
