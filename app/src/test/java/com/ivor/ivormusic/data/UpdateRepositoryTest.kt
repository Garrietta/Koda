package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateRepositoryTest {

    @Test
    fun aTestBuildSaysItsNumberInItsNotesOrItsName() {
        val notes = "Build: 177\nBranch: claude/x\nCommit: abc1234\n\nThe newest signed build."
        assertEquals(177, UpdateRepository.testBuildNumberIn(notes, "Test build 176"))
        assertEquals(176, UpdateRepository.testBuildNumberIn("No number here", "Test build 176"))
        assertNull(UpdateRepository.testBuildNumberIn("Rebuilt the player", "5.3"))
    }

    @Test
    fun releaseVersionsCompareByTheirNumbers() {
        val repo = UpdateRepository()
        assertTrue(repo.isNewerVersion("5.3", "5.2"))
        assertTrue(repo.isNewerVersion("5.10", "5.9"))
        assertFalse(repo.isNewerVersion("5.2", "5.2"))
        assertFalse(repo.isNewerVersion("5.1.9", "5.2"))
    }

}
