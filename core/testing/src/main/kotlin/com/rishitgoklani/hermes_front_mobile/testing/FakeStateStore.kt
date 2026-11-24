package com.rishitgoklani.hermes_front_mobile.testing

import com.rishitgoklani.hermes_front_mobile.domain.state.StateStore

class FakeStateStore(val items: MutableMap<String, String> = mutableMapOf()) : StateStore {
    override fun load(key: String) = items[key]
    override fun save(key: String, value: String) { items[key] = value }
}
