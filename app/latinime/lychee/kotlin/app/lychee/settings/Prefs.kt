package app.lychee.settings

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * One keyboard setting as Compose state. It writes straight into LatinIME's default
 * SharedPreferences, so the running keyboard picks the change up through its own listener.
 */
class Pref<T>(initial: T, private val write: (T) -> Unit) {
    var value by mutableStateOf(initial)
        private set

    fun set(newValue: T) {
        value = newValue
        write(newValue)
    }
}

@Composable
fun SharedPreferences.boolPref(key: String, initial: () -> Boolean): Pref<Boolean> =
    remember(key) { Pref(initial()) { edit().putBoolean(key, it).apply() } }

@Composable
fun SharedPreferences.intPref(key: String, initial: () -> Int): Pref<Int> =
    remember(key) { Pref(initial()) { edit().putInt(key, it).apply() } }

@Composable
fun SharedPreferences.floatPref(key: String, initial: () -> Float): Pref<Float> =
    remember(key) { Pref(initial()) { edit().putFloat(key, it).apply() } }

@Composable
fun SharedPreferences.stringPref(key: String, initial: () -> String): Pref<String> =
    remember(key) { Pref(initial()) { edit().putString(key, it).apply() } }
