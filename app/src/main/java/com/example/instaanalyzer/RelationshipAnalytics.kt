package com.example.instaanalyzer

data class RelationshipChange(
    val newFollowers: Set<String> = emptySet(),
    val lostFollowers: Set<String> = emptySet(),
    val newFollowing: Set<String> = emptySet(),
    val removedFollowing: Set<String> = emptySet()
)

object RelationshipAnalytics {
    fun compare(
        previousFollowers: Set<String>,
        currentFollowers: Set<String>,
        previousFollowing: Set<String>,
        currentFollowing: Set<String>
    ): RelationshipChange = RelationshipChange(
        newFollowers = currentFollowers - previousFollowers,
        lostFollowers = previousFollowers - currentFollowers,
        newFollowing = currentFollowing - previousFollowing,
        removedFollowing = previousFollowing - currentFollowing
    )
}
