package com.datadragon.app.data

/** Home order is independent of the sorting of records inside a grouping. */
object HomeGroupingOrder {
    val modes = HomeView.entries.filter { it != HomeView.DAILY_LIST }

    fun <T> apply(items: List<T>, order: List<String>, uuid: (T) -> String): List<T> {
        val positions = order.withIndex().associate { it.value to it.index }
        // Stable sort retains the existing order for newly created/unlisted groupings.
        return items.sortedBy { positions[uuid(it)] ?: Int.MAX_VALUE }
    }

    fun validate(orders: Map<String, List<String>>) {
        require(orders.keys.all { key -> modes.any { it.key == key } }) { "Home grouping order contains an unsupported mode." }
        orders.values.forEach { ids ->
            require(ids.all { it.isNotBlank() } && ids.distinct().size == ids.size) { "Home grouping order identities must be nonblank and unique." }
        }
    }
}
