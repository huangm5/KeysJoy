package com.github.ifeel.keysjoy

import androidx.multidex.MultiDexApplication

@Suppress("unused")
class Kontroller: MultiDexApplication() {
    // MultiDexApplication handles class loading from secondary DEX files.
    // This prevents ClassNotFoundException on startup.
}
