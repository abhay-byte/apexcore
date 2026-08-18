package com.ivarna.apexcore.tune

class FakeKeyValue : KeyValue {
    val map = mutableMapOf<String, Any>()

    override fun getBoolean(key: String, default: Boolean): Boolean {
        return map[key] as? Boolean ?: default
    }

    override fun putBoolean(key: String, value: Boolean) {
        map[key] = value
    }

    override fun getString(key: String, default: String?): String? {
        return map[key] as? String ?: default
    }

    override fun putString(key: String, value: String?) {
        if (value == null) {
            map.remove(key)
        } else {
            map[key] = value
        }
    }

    override fun remove(key: String) {
        map.remove(key)
    }
}
