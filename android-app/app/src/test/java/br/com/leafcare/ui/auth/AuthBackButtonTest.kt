package br.com.leafcare.ui.auth

import android.app.Application
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import br.com.leafcare.auth.AuthRepository
import br.com.leafcare.auth.AuthScreen
import br.com.leafcare.auth.AuthViewModel
import br.com.leafcare.auth.FakeBackend
import br.com.leafcare.auth.FakeRecoveryPendingStore
import br.com.leafcare.ui.LeafCareTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Back-button placement tests: every Auth screen with a back control renders
 * it above the screen content (shared top-start area, never inside the
 * centered column). Robolectric + Compose, no network.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w420dp-h865dp-mdpi", application = Application::class)
class AuthBackButtonTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun viewModel(): AuthViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        return AuthViewModel(
            app,
            AuthRepository(FakeBackend()),
            FakeRecoveryPendingStore()
        )
    }

    private fun assertBackAboveContent(title: String) {
        val back = compose.onNodeWithContentDescription("Voltar")
        back.assertExists()
        back.assertIsDisplayed()
        val backTop = back.fetchSemanticsNode().positionInRoot.y
        val titleTop = compose.onNodeWithText(title).fetchSemanticsNode().positionInRoot.y
        assert(backTop < titleTop) {
            "Back button (y=$backTop) must sit above content (y=$titleTop)"
        }
    }

    @Test fun signUp_backIsTopStart() {
        compose.setContent { LeafCareTheme { SignUpScreen(viewModel()) } }
        assertBackAboveContent("Crie sua conta")
    }

    @Test fun verifyEmail_backIsTopStart() {
        compose.setContent { LeafCareTheme { VerifyEmailScreen(viewModel()) } }
        assertBackAboveContent("Verifique seu e-mail")
    }

    @Test fun forgotPassword_backIsTopStart() {
        compose.setContent { LeafCareTheme { ForgotPasswordScreen(viewModel()) } }
        assertBackAboveContent("Recuperar senha")
    }

    @Test fun newPassword_backIsTopStart() {
        compose.setContent { LeafCareTheme { NewPasswordScreen(viewModel(), onBack = {}) } }
        assertBackAboveContent("Nova senha")
    }

    @Test fun profile_backIsTopStart() {
        compose.setContent { LeafCareTheme { ProfileScreen(viewModel(), {}, {}) } }
        assertBackAboveContent("CONTA LEAFCARE")
    }

    @Test fun recoveryNewPassword_backAsksBeforeLeaving() {
        compose.setContent {
            LeafCareTheme { NewPasswordScreen(viewModel(), {}, confirmCancelRecovery = true) }
        }
        compose.onNodeWithContentDescription("Voltar").performClick()
        compose.onNodeWithText("Cancelar recuperação?").assertExists().assertIsDisplayed()
        compose.onNodeWithText("Continuar recuperação").assertExists()
        compose.onNodeWithText("Sair").assertExists()
    }

    @Test fun recoveryNewPassword_continueStays() {
        val vm = viewModel()
        compose.setContent {
            LeafCareTheme { NewPasswordScreen(vm, {}, confirmCancelRecovery = true) }
        }
        compose.onNodeWithContentDescription("Voltar").performClick()
        compose.onNodeWithText("Continuar recuperação").performClick()
        compose.onNodeWithText("Cancelar recuperação?").assertDoesNotExist()
        compose.onNodeWithText("Nova senha").assertExists()
    }

    @Test fun recoveryNewPassword_quitSignsOutAndLeaves() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val backend = FakeBackend()
        val vm = AuthViewModel(app, AuthRepository(backend), FakeRecoveryPendingStore())
        compose.setContent {
            LeafCareTheme { NewPasswordScreen(vm, {}, confirmCancelRecovery = true) }
        }
        compose.onNodeWithContentDescription("Voltar").performClick()
        compose.onNodeWithText("Sair").performClick()
        compose.waitForIdle()

        assertEquals(1, backend.signOutCalls)
        assertEquals(AuthScreen.Login, vm.uiState.value.currentScreen)
        compose.onNodeWithText("Cancelar recuperação?").assertDoesNotExist()
    }

    @Test fun profileChangePassword_backHasNoDialog() {
        compose.setContent {
            LeafCareTheme { NewPasswordScreen(viewModel(), {}, confirmCancelRecovery = false) }
        }
        compose.onNodeWithContentDescription("Voltar").performClick()
        compose.onNodeWithText("Cancelar recuperação?").assertDoesNotExist()
    }
}
