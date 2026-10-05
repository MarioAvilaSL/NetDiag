package com.diagnostico.senales.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Registro de avisos y notas que el usuario ya ha cerrado.
 *
 * Vive en el contenedor de la aplicación, así que dura lo que el proceso: al
 * cerrar la app y volver a abrirla las notas vuelven a aparecer. No se escribe
 * nada en disco, que es justo lo que se quiere de un texto informativo.
 */
class DismissedNoteStore {

    private val keys = mutableStateOf(emptySet<String>())

    fun isDismissed(text: String): Boolean = keyOf(text) in keys.value

    fun dismiss(text: String) {
        keys.value = keys.value + keyOf(text)
    }

    fun restoreAll() {
        keys.value = emptySet()
    }

    /**
     * La propia nota es su clave: dos avisos con el mismo texto son el mismo
     * aviso, y así los 50 puntos de llamada no necesitan pasar un identificador.
     */
    private fun keyOf(text: String): String = text.hashCode().toString()
}

val LocalDismissedNotes = staticCompositionLocalOf { DismissedNoteStore() }

/** Envuelve el árbol de pantallas para que las notas compartan el mismo registro. */
@Composable
fun DismissedNotesProvider(store: DismissedNoteStore, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDismissedNotes provides store, content = content)
}