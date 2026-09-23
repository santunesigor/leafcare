package br.com.leafcare.auth

import org.junit.Assert.*
import org.junit.Test

/**
 * Pure validation tests: no Android framework, no coroutines, no network.
 */
class AuthValidationTest {

    @Test fun signUp_blankDisplayNameFails() {
        assertEquals(
            "Nome não pode ser vazio",
            AuthRepository.validateSignUpInput("", "a@b.com", "123456")
        )
    }

    @Test fun signUp_whitespaceDisplayNameFails() {
        assertEquals(
            "Nome não pode ser vazio",
            AuthRepository.validateSignUpInput("   ", "a@b.com", "123456")
        )
    }

    @Test fun signUp_blankEmailFails() {
        assertEquals(
            "E-mail inválido",
            AuthRepository.validateSignUpInput("Nome", "  ", "123456")
        )
    }

    @Test fun signUp_shortPasswordFails() {
        assertEquals(
            "A senha deve ter pelo menos 6 caracteres",
            AuthRepository.validateSignUpInput("Nome", "a@b.com", "12345")
        )
    }

    @Test fun signUp_minimumPasswordPasses() {
        assertNull(AuthRepository.validateSignUpInput("Nome", "a@b.com", "123456"))
    }

    @Test fun signUp_validInputPasses() {
        assertNull(AuthRepository.validateSignUpInput("Nome Teste", "user@leafcare.test", "senha-segura"))
    }

    @Test fun signIn_blankEmailFails() {
        assertEquals(
            "E-mail inválido",
            AuthRepository.validateSignInInput("", "123456")
        )
    }

    @Test fun signIn_blankPasswordFails() {
        assertEquals(
            "Senha não pode ser vazia",
            AuthRepository.validateSignInInput("a@b.com", "")
        )
    }

    @Test fun signIn_validInputPasses() {
        assertNull(AuthRepository.validateSignInInput("a@b.com", "123456"))
    }
}
