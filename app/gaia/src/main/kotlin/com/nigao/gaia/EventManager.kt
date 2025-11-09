package com.nigao.gaia

import io.github.aakira.napier.Napier

class GaiaEvent(
    val key: String = "",
    val payload: Any? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun <T> getPayloadAs(clazz: Class<T>): T? {
        return payload as? T
    }
}

private class GaiaEventListener(val key:String, val callback: (GaiaEvent?) -> Unit) {
    fun onEvent(event: GaiaEvent?) {
        callback(event)
    }
}

object GaiaEventManager {
    private val listeners = mutableMapOf<String, MutableList<GaiaEventListener>>()

    fun registerEventObserver(key: String, callback:(data: GaiaEvent?) -> Unit) {
        if (!listeners.containsKey(key)) {
            listeners[key] = mutableListOf<GaiaEventListener>()
        }
        val keyListener = GaiaEventListener(key, callback)
        listeners[key]?.add(keyListener)

        Napier.i("Register event observer: $key")
    }

    fun start(event: GaiaEvent) {
        val eventListeners = listeners[event.key]
        if (eventListeners.isNullOrEmpty()) return
        eventListeners.forEach {
            it.onEvent(event)
        }
    }
}