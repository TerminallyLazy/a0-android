package com.terminallylazy.a0android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.terminallylazy.a0android.data.ProfileState
import com.terminallylazy.a0android.data.ServerProfile
import com.terminallylazy.a0android.data.ServerProfileStore
import com.terminallylazy.a0android.web.ServerOrigin
import com.terminallylazy.a0android.web.BrowserProfiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShellViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ServerProfileStore(application)
    val profiles: StateFlow<ProfileState?> = store.state.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()

    fun save(name: String, address: String, existing: ServerProfile?, onSaved: (ServerProfile) -> Unit) {
        val origin = ServerOrigin.parse(address)
        if (origin == null) {
            mutableError.value = "Enter an HTTPS server address with no credentials, query, or subpath."
            return
        }
        perform {
            if (existing != null && existing.origin != origin && !BrowserProfiles.forget(getApplication(), existing)) {
                error("Cannot clear previous profile")
            }
            onSaved(store.save(name, origin, existing))
        }
    }
    fun select(id: String) = perform { store.select(id) }
    fun forget(profile: ServerProfile) = perform {
        check(BrowserProfiles.forget(getApplication(), profile))
        store.forget(profile.id)
    }
    fun clearError() { mutableError.value = null }
    private fun perform(block: suspend () -> Unit) = viewModelScope.launch {
        if (mutableBusy.value) return@launch
        mutableBusy.value = true
        try { block(); mutableError.value = null }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { mutableError.value = "Could not update server settings. Try again, or update Android System WebView if session deletion is unavailable." }
        finally { mutableBusy.value = false }
    }
}
