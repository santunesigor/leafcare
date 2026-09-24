package br.com.leafcare.ui

import org.junit.Assert.*
import org.junit.Test

/**
 * Home greeting tests: real authenticated name, plain fallback otherwise.
 * Pure function, no Android framework.
 */
class HomeGreetingTest {

    @Test fun displayName_showsPersonalGreeting() {
        assertEquals("Olá, Maria Silva", homeGreeting("Maria Silva"))
    }

    @Test fun nullName_showsPlainGreeting() {
        assertEquals("Olá", homeGreeting(null))
    }

    @Test fun blankName_showsPlainGreeting() {
        assertEquals("Olá", homeGreeting("   "))
    }

    @Test fun greeting_neverShowsProducerFallback() {
        assertFalse(homeGreeting("Maria").contains("produtor", ignoreCase = true))
        assertFalse(homeGreeting(null).contains("produtor", ignoreCase = true))
    }
}
