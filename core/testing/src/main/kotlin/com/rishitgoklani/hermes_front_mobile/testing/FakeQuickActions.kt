package com.rishitgoklani.hermes_front_mobile.testing

import com.rishitgoklani.hermes_front_mobile.domain.behavior.DefaultQuickActions
import com.rishitgoklani.hermes_front_mobile.domain.behavior.QuickAction
import com.rishitgoklani.hermes_front_mobile.domain.behavior.QuickActionsRepository

class FakeQuickActionsRepository(var items: List<QuickAction> = DefaultQuickActions.all) : QuickActionsRepository {
    override suspend fun all() = items
    override suspend fun set(actions: List<QuickAction>) { items = actions }
}
