package com.ddopmago

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/** 系统输入法「ꅇꂷꇬ」：在手机的其他软件里也能用本软件的凉山彝文键盘（含拼音/首字母/撇号隔字符输入法引擎） */
class YiImeService : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val reg = LifecycleRegistry(this)
    private val vms = ViewModelStore()
    private val ssc = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = reg
    override val viewModelStore: ViewModelStore get() = vms
    override val savedStateRegistry: SavedStateRegistry get() = ssc.savedStateRegistry
    private var before by mutableStateOf("")

    override fun onCreate() {
        super.onCreate()
        ssc.performRestore(null)
        reg.currentState = Lifecycle.State.CREATED
        val app = applicationContext
        Repo.load(app)
        Fonts.init(app)
        I18n.init(app)
        Thread { YiIme.ensure(app) }.start()
    }

    override fun onCreateInputView(): View {
        window.window?.decorView?.let {
            it.setViewTreeLifecycleOwner(this)
            it.setViewTreeViewModelStoreOwner(this)
            it.setViewTreeSavedStateRegistryOwner(this)
        }
        val v = ComposeView(this)
        reg.currentState = Lifecycle.State.RESUMED
        v.setContent {
            MaterialTheme {
                AppKeyboard(
                    K_YI, before, { commit(it) }, { del() }, { requestHideSelf(0) },
                    { commit(Clip.paste(this@YiImeService)) }, { enter() }
                )
            }
        }
        return v
    }

    private fun refresh() { before = currentInputConnection?.getTextBeforeCursor(60, 0)?.toString() ?: "" }
    private fun commit(s: String) { currentInputConnection?.commitText(s, 1); refresh() }
    private fun del() { currentInputConnection?.deleteSurroundingTextInCodePoints(1, 0); refresh() }
    private fun enter() {
        val ei = currentInputEditorInfo
        val act = (ei?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        if (act != EditorInfo.IME_ACTION_NONE && act != EditorInfo.IME_ACTION_UNSPECIFIED && (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0)
            currentInputConnection?.performEditorAction(act)
        else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        refresh()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) { super.onStartInputView(info, restarting); refresh() }
    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        refresh()
    }

    override fun onDestroy() { YiIme.save(); reg.currentState = Lifecycle.State.DESTROYED; vms.clear(); super.onDestroy() }
}
