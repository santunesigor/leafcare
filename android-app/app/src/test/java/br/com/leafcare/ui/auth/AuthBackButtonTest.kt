package br.com.leafcare.ui.auth

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
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
import org.junit.Assert.assertFalse
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

    @Test fun signUp_androidBackReturnsToLoginWithoutFinishingActivity() {
        val vm = viewModel()
        vm.setScreen(AuthScreen.SignUp)
        compose.setContent { LeafCareTheme { AuthNavHost(vm) } }
        compose.onNodeWithText("Crie sua conta").assertExists()

        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }

        compose.onNodeWithText("Bem-vindo ao LeafCare").assertExists()
        assertEquals(AuthScreen.Login, vm.uiState.value.currentScreen)
        assertFalse(compose.activity.isFinishing)
    }

    @Test fun signUp_arrowReturnsToLogin() {
        val vm = viewModel()
        vm.setScreen(AuthScreen.SignUp)
        compose.setContent { LeafCareTheme { AuthNavHost(vm) } }

        compose.onNodeWithContentDescription("Voltar").performClick()

        compose.onNodeWithText("Bem-vindo ao LeafCare").assertExists()
    }

    @Test fun forgotPassword_androidBackReturnsToLogin() {
        val vm = viewModel()
        vm.setScreen(AuthScreen.ForgotPassword)
        compose.setContent { LeafCareTheme { AuthNavHost(vm) } }
        compose.onNodeWithText("Recuperar senha").assertExists()

        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }

        compose.onNodeWithText("Bem-vindo ao LeafCare").assertExists()
        assertFalse(compose.activity.isFinishing)
    }

    @Test fun verifyEmail_androidBackReturnsToSignUpThenLogin() {
        val vm = viewModel()
        vm.setScreen(AuthScreen.VerifyEmail)
        compose.setContent { LeafCareTheme { AuthNavHost(vm) } }
        compose.onNodeWithText("Verifique seu e-mail").assertExists()

        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Crie sua conta").assertExists()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }

        compose.onNodeWithText("Bem-vindo ao LeafCare").assertExists()
    }

    @Test fun recoveryNewPassword_androidBackAsksBeforeLeaving() {
        compose.setContent {
            LeafCareTheme { NewPasswordScreen(viewModel(), {}, confirmCancelRecovery = true) }
        }

        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }

        compose.onNodeWithText("Cancelar recuperação?").assertExists()
        assertFalse(compose.activity.isFinishing)
    }

    @Test fun profileChangePassword_androidBackCallsPreviousScreen() {
        var returned = false
        compose.setContent {
            LeafCareTheme { NewPasswordScreen(viewModel(), { returned = true }) }
        }

        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }

        assertEquals(true, returned)
        compose.onNodeWithText("Cancelar recuperação?").assertDoesNotExist()
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
        assertEquals(AuthScreen.ForgotPassword, vm.uiState.value.currentScreen)
        compose.onNodeWithText("Cancelar recuperação?").assertDoesNotExist()
        compose.onNodeWithText("Recuperação cancelada. Solicite um novo link.").assertExists()
    }

    @Test fun profileChangePassword_backHasNoDialog() {
        compose.setContent {
            LeafCareTheme { NewPasswordScreen(viewModel(), {}, confirmCancelRecovery = false) }
        }
        compose.onNodeWithContentDescription("Voltar").performClick()
        compose.onNodeWithText("Cancelar recuperação?").assertDoesNotExist()
    }

    @Test fun forgotPassword_cooldownDisablesButtonWithCountdown() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val store = FakeRecoveryPendingStore()
        store.lastRecoveryRequestAt = System.currentTimeMillis() - 10_000L
        val vm = AuthViewModel(app, AuthRepository(FakeBackend()), store)
        compose.setContent { LeafCareTheme { ForgotPasswordScreen(vm) } }

        val button = compose.onNodeWithText("Enviar novamente em", substring = true)
        button.assertExists().assertIsDisplayed()
        assert(button.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled)) {
            "Cooldown button must be disabled"
        }
    }

    @Test fun forgotPassword_afterCooldown_showsResendEnabled() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val store = FakeRecoveryPendingStore()
        store.lastRecoveryRequestAt = System.currentTimeMillis() - 61_000L
        val vm = AuthViewModel(app, AuthRepository(FakeBackend()), store)
        compose.setContent { LeafCareTheme { ForgotPasswordScreen(vm) } }

        compose.onNodeWithText("Enviar novamente").assertExists().assertIsDisplayed()
        assert(
            !compose.onNodeWithText("Enviar novamente").fetchSemanticsNode().config
                .contains(SemanticsProperties.Disabled)
        ) {
            "Button must be enabled after cooldown"
        }
    }

    @Test fun forgotPassword_whileSending_showsSendingDisabled() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val backend = FakeBackend()
        // Hold the request open to observe the in-flight UI state.
        backend.resetGate = { kotlinx.coroutines.awaitCancellation() }
        val vm = AuthViewModel(app, AuthRepository(backend), FakeRecoveryPendingStore())
        vm.setEmail("a@b.com")
        compose.setContent { LeafCareTheme { ForgotPasswordScreen(vm) } }

        compose.onNodeWithText("Enviar e-mail").performClick()
        compose.waitForIdle()

        assert(vm.resetSending.value)
        compose.onNodeWithText("Enviando...").assertExists().assertIsDisplayed()
    }

    @Test fun forgotPassword_composeAlone_sendsNoRequest() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val backend = FakeBackend()
        val vm = AuthViewModel(app, AuthRepository(backend), FakeRecoveryPendingStore())
        vm.setEmail("a@b.com")
        compose.setContent { LeafCareTheme { ForgotPasswordScreen(vm) } }

        // Composition, recomposition and the cooldown-ticker LaunchedEffect
        // must never send: only an explicit user action may POST.
        compose.waitForIdle()
        compose.onNodeWithText("Enviar e-mail").assertExists().assertIsDisplayed()

        assertEquals(0, backend.resetRequests.size)
    }
}
