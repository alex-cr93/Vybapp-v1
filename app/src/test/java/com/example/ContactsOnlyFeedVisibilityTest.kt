package com.example

import com.example.data.model.Post
import com.example.data.model.SavedContact
import com.example.data.model.Story
import com.example.ui.viewmodel.FeedViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactsOnlyFeedVisibilityTest {

    @Test
    fun buildAllowedAuthorIds_includesSelfAndAddedContactsOnly() {
        val contacts = listOf(
            SavedContact(contactUid = "bob_uid", displayName = "Bob", username = "bob_cr"),
            SavedContact(contactUid = "ana_uid", displayName = "Ana", username = "ana_cr"),
            SavedContact(contactUid = "", displayName = "Empty", username = "empty")
        )

        val allowed = FeedViewModel.buildAllowedAuthorIds("alice_uid", contacts)

        assertEquals(setOf("alice_uid", "bob_uid", "ana_uid"), allowed)
        assertFalse("stranger_uid" in allowed)
    }

    @Test
    fun filterVisiblePosts_blocksNonContactsAndShowsSelfAndContacts() {
        val allowed = FeedViewModel.buildAllowedAuthorIds(
            currentUserId = "alice_uid",
            contacts = listOf(SavedContact(contactUid = "bob_uid", displayName = "Bob", username = "bob_cr"))
        )

        val posts = listOf(
            Post(id = "p1", authorId = "alice_uid", text = "Mi publicación"),
            Post(id = "p2", authorId = "bob_uid", text = "Publicación de contacto Bob"),
            Post(id = "p3", authorId = "stranger_uid", text = "Publicación de desconocido"),
            Post(id = "p4", authorId = "", text = "Sin autor")
        )

        val visible = FeedViewModel.filterVisiblePosts(posts, allowed)

        assertEquals(listOf("p1", "p2"), visible.map { it.id })
        assertTrue(visible.none { it.authorId == "stranger_uid" })
    }

    @Test
    fun filterVisibleStories_blocksNonContactsAndRevokesWhenContactRemoved() {
        val contactsBefore = listOf(SavedContact(contactUid = "bob_uid", displayName = "Bob", username = "bob_cr"))
        val allowedBefore = FeedViewModel.buildAllowedAuthorIds("alice_uid", contactsBefore)

        val stories = listOf(
            Story(id = "s1", authorId = "alice_uid", caption = "Mi historia"),
            Story(id = "s2", authorId = "bob_uid", caption = "Historia de Bob"),
            Story(id = "s3", authorId = "stranger_uid", caption = "Historia de desconocido")
        )

        val visibleBefore = FeedViewModel.filterVisibleStories(stories, allowedBefore)
        assertEquals(listOf("s1", "s2"), visibleBefore.map { it.id })

        // Removing Bob immediately revokes Bob's stories from visible list
        val allowedAfterRemoval = FeedViewModel.buildAllowedAuthorIds("alice_uid", emptyList())
        val visibleAfter = FeedViewModel.filterVisibleStories(stories, allowedAfterRemoval)
        assertEquals(listOf("s1"), visibleAfter.map { it.id })
    }
}
