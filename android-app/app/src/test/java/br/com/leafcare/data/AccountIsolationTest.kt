package br.com.leafcare.data

import org.junit.Assert.*
import org.junit.Test

/**
 * Account-switch isolation tests: Room rows carry no owner column, so rows of
 * a previous account must never be shown to (or synced as) another account.
 * Pure decision function, no Android framework.
 */
class AccountIsolationTest {

    @Test fun sameUserReLoginKeepsEverything() {
        assertFalse(shouldWipeForAccountSwitch("user-a", "user-a", hasLocalRows = true))
    }

    @Test fun differentUserWipesLocalData() {
        assertTrue(shouldWipeForAccountSwitch("user-a", "user-b", hasLocalRows = true))
    }

    @Test fun firstLoginWithoutRowsDoesNothing() {
        assertFalse(shouldWipeForAccountSwitch(null, "user-a", hasLocalRows = false))
    }

    @Test fun unattributableRowsAreWipedOnNextLogin() {
        // Rows from before isolation existed have no known owner.
        assertTrue(shouldWipeForAccountSwitch(null, "user-a", hasLocalRows = true))
    }

    @Test fun emptyStoreNeverWipes() {
        assertFalse(shouldWipeForAccountSwitch("user-a", "user-b", hasLocalRows = false))
    }
}
