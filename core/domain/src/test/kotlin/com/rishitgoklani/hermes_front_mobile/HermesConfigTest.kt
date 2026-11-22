package com.rishitgoklani.hermes_front_mobile

import com.rishitgoklani.hermes_front_mobile.domain.HermesConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class HermesConfigTest {
    @Test fun defaultsPointAtLoopbackApiServer() = assertEquals("http://127.0.0.1:8642", HermesConfig().baseUrl)
}
