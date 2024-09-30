package org.futo.inputmethod.latin.uix

import android.app.Activity
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestionsResponse
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.futo.inputmethod.accessibility.AccessibilityUtils
import org.futo.inputmethod.latin.AudioAndHapticFeedbackManager
import org.futo.inputmethod.latin.BuildConfig
import org.futo.inputmethod.latin.FoldingOptions
import org.futo.inputmethod.latin.LanguageSwitcherDialog
import org.futo.inputmethod.latin.LatinIME
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.SuggestedWords
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import org.futo.inputmethod.latin.SuggestionBlacklist
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.inputlogic.InputLogic
import org.futo.inputmethod.latin.suggestions.SuggestionStripViewListener
import org.futo.inputmethod.latin.uix.actions.ActionEditor
import org.futo.inputmethod.latin.uix.actions.ActionRegistry
import org.futo.inputmethod.latin.uix.actions.AllActions
import org.futo.inputmethod.latin.uix.actions.EmojiAction
import org.futo.inputmethod.latin.uix.settings.DataStoreCacheProvider
import org.futo.inputmethod.latin.uix.settings.SettingsActivity
import org.futo.inputmethod.latin.uix.settings.pages.ActionBarDisplayedSetting
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.Typography
import org.futo.inputmethod.latin.uix.theme.UixThemeAuto
import org.futo.inputmethod.latin.uix.theme.UixThemeWrapper
import org.futo.inputmethod.updates.DISABLE_UPDATE_REMINDER
import org.futo.inputmethod.updates.autoDeferManualUpdateIfNeeded
import org.futo.inputmethod.updates.deferManualUpdate
import org.futo.inputmethod.updates.isManualUpdateTimeExpired
import org.futo.inputmethod.updates.openManualUpdateCheck
import org.futo.inputmethod.updates.retrieveSavedLastUpdateCheckResult
import org.futo.inputmethod.v2keyboard.ComputedKeyboardSize
import org.futo.inputmethod.v2keyboard.FloatingKeyboardSize
import org.futo.inputmethod.v2keyboard.KeyboardSizingCalculator
import org.futo.inputmethod.v2keyboard.OneHandedDirection
import org.futo.inputmethod.v2keyboard.OneHandedKeyboardSize
import org.futo.inputmethod.v2keyboard.RegularKeyboardSize
import org.futo.inputmethod.v2keyboard.SplitKeyboardSize
import org.futo.inputmethod.v2keyboard.getHeight
import org.futo.inputmethod.v2keyboard.getPadding
import org.futo.inputmethod.v2keyboard.getWidth
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

val LocalManager = staticCompositionLocalOf<KeyboardManagerForAction> {
    error("No LocalManager provided")
}

val LocalThemeProvider = compositionLocalOf<DynamicThemeProvider> {
    error("No LocalThemeProvider provided")
}

val LocalFoldingState = compositionLocalOf<FoldingOptions> {
    FoldingOptions(null)
}



private class LatinIMEActionInputTransaction(
    private val inputLogic: InputLogic,
    shouldApplySpace: Boolean,
    private val context: Context
): ActionInputTransaction {
    private val isSpaceNecessary: Boolean
    private var isFinished = false

    init {
        val priorText = inputLogic.mConnection.getTextBeforeCursor(1, 0)
        isSpaceNecessary = shouldApplySpace && !priorText.isNullOrEmpty() && !priorText.last().isWhitespace()

        inputLogic.startSuppressingLogic()
    }

    private fun transformText(text: String): String {
        return if(isSpaceNecessary) { " $text" } else { text }
    }

    private var previousText = ""
    override fun updatePartial(text: String) {
        if(isFinished) return
        previousText = text
        inputLogic.mConnection.setComposingText(
            transformText(text),
            1
        )
    }

    override fun commit(text: String) {
        if(isFinished) return
        isFinished = true
        inputLogic.mConnection.commitText(
            transformText(text),
            1
        )
        inputLogic.endSuppressingLogic()
    }

    override fun cancel() {
        commit(previousText)
    }
}

class UixActionKeyboardManager(val uixManager: UixManager, val latinIME: LatinIME) : KeyboardManagerForAction {
    override fun getContext(): Context {
        return latinIME
    }

    override fun getLifecycleScope(): LifecycleCoroutineScope {
        return latinIME.lifecycleScope
    }

    override fun triggerContentUpdate() {
        uixManager.setContent()
    }

    override fun createInputTransaction(applySpaceIfNeeded: Boolean): ActionInputTransaction {
        return LatinIMEActionInputTransaction(latinIME.inputLogic, applySpaceIfNeeded, latinIME)
    }

    override fun typeText(v: String) {
        if(latinIME.isInputConnectionOverridden) {
            latinIME.getBaseInputConnection()?.commitText(v, 1)
        } else {
            latinIME.latinIMELegacy.onTextInput(v)
        }
    }

    override fun typeUri(uri: Uri, mimeTypes: List<String>): Boolean {
        if(mimeTypes.isEmpty()) {
            Log.w("UixManager", "mimeTypes is empty")
            return false
        }

        val description = ClipDescription("Pasted image", mimeTypes.toTypedArray())

        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            InputContentInfo(uri, description, null)
        } else {
            return false
        }

        return latinIME.currentInputConnection?.commitContent(info, InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null) ?: run {
            Log.w("UixManager", "Current input connection is null")
            return false
        }
    }

    override fun backspace(amount: Int) {
        latinIME.latinIMELegacy.onCodeInput(
            Constants.CODE_DELETE,
            Constants.NOT_A_COORDINATE,
            Constants.NOT_A_COORDINATE, false)
    }

    override fun closeActionWindow() {
        if(uixManager.currWindowActionWindow == null) return
        uixManager.returnBackToMainKeyboardViewFromAction()
    }

    override fun triggerSystemVoiceInput() {
        latinIME.latinIMELegacy.onCodeInput(
            Constants.CODE_SHORTCUT,
            Constants.SUGGESTION_STRIP_COORDINATE,
            Constants.SUGGESTION_STRIP_COORDINATE,
            false
        );
    }

    override fun updateTheme(newTheme: ThemeOption) {
        latinIME.updateTheme(newTheme)
    }

    override fun getThemeProvider(): DynamicThemeProvider {
        return latinIME.getDrawableProvider()
    }

    override fun sendCodePointEvent(codePoint: Int) {
        latinIME.latinIMELegacy.onCodeInput(codePoint,
            Constants.NOT_A_COORDINATE,
            Constants.NOT_A_COORDINATE, false)
    }

    override fun sendKeyEvent(keyCode: Int, metaState: Int) {
        latinIME.inputLogic.sendDownUpKeyEvent(keyCode, metaState)
    }

    override fun cursorLeft(steps: Int, stepOverWords: Boolean, select: Boolean) {
        latinIME.inputLogic.cursorLeft(steps, stepOverWords, select)
    }

    override fun cursorRight(steps: Int, stepOverWords: Boolean, select: Boolean) {
        latinIME.inputLogic.cursorRight(steps, stepOverWords, select)
    }

    override fun performHapticAndAudioFeedback(code: Int, view: View) {
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(code, view)
    }

    override fun getActiveLocale(): Locale {
        return latinIME.latinIMELegacy.locale
    }

    override fun overrideInputConnection(inputConnection: InputConnection, editorInfo: EditorInfo) {
        latinIME.overrideInputConnection(inputConnection, editorInfo)
        uixManager.toggleExpandAction(true)
        uixManager.isInputOverridden.value = true
    }

    override fun unsetInputConnection() {
        latinIME.overrideInputConnection(null, null)
        uixManager.isInputOverridden.value = false
    }

    override fun requestDialog(text: String, options: List<DialogRequestItem>, onCancel: () -> Unit) {
        uixManager.activeDialogRequest.value = ActiveDialogRequest(text, options, onCancel)
        uixManager.activeDialogRequestDismissed.value = false
    }

    override fun openInputMethodPicker() {
        uixManager.showLanguageSwitcher()
    }

    override fun announce(s: String) {
        AccessibilityUtils.init(getContext())
        if(AccessibilityUtils.getInstance().isAccessibilityEnabled) {
            AccessibilityUtils.getInstance().announceForAccessibility(uixManager.composeView, s)
        }
    }

    override fun activateAction(action: Action) {
        uixManager.onActionActivated(action)
    }

    override fun showActionEditor() {
        uixManager.showActionEditor()
    }

    override fun showResizer() {
        uixManager.resizing.value = true
        uixManager.closeActionWindow()
    }

    override fun isDeviceLocked(): Boolean {
        return getContext().isDeviceLocked
    }

    override fun getSizingCalculator(): KeyboardSizingCalculator =
        latinIME.sizingCalculator

    override fun getLatinIMEForDebug(): LatinIME = latinIME

    override fun getSuggestionBlacklist(): SuggestionBlacklist = latinIME.suggestionBlacklist
}

data class ActiveDialogRequest(
    val text: String,
    val options: List<DialogRequestItem>,
    val onCancel: () -> Unit
)

class UixManager(private val latinIME: LatinIME) {
    internal val composeView: ComposeView?
        get() = latinIME.composeView

    private var shouldShowSuggestionStrip: Boolean = true
    private var suggestedWords: SuggestedWords? = null

    private var currWindowAction: Action? = null
    private var persistentStates: HashMap<Action, PersistentActionState?> = hashMapOf()

    private var inlineSuggestions: List<MutableState<View?>> = listOf()
    private val keyboardManagerForAction = UixActionKeyboardManager(this, latinIME)

    private var mainKeyboardHidden = false

    private var numSuggestionsSinceNotice = 0
    private var currentNotice: MutableState<ImportantNotice?> = mutableStateOf(null)

    private var isActionsExpanded = mutableStateOf(false)
    private fun toggleActionsExpanded() {
        isActionsExpanded.value = !isActionsExpanded.value
        latinIME.deferSetSetting(ActionBarExpanded, isActionsExpanded.value)
    }

    val actionsExpanded: Boolean
        get() = isActionsExpanded.value

    internal val resizing = mutableStateOf(false)

    private var isShowingActionEditor = mutableStateOf(false)
    fun showActionEditor() {
        isShowingActionEditor.value = true
    }

    val foldingOptions = mutableStateOf(FoldingOptions(null))

    var isInputOverridden = mutableStateOf(false)

    var currWindowActionWindow: ActionWindow? = null
    val isActionWindowDocked: Boolean
        get() = currWindowActionWindow != null

    private var measuredTouchableHeight = 0
    val touchableHeight: Int
        get() = measuredTouchableHeight

    val isMainKeyboardHidden get() = mainKeyboardHidden

    fun onActionActivated(rawAction: Action) {
        resizing.value = false
        latinIME.inputLogic.finishInput()

        val action = runBlocking {
            ActionRegistry.getActionOverride(latinIME, rawAction)
        }

        if (action.windowImpl != null) {
            enterActionWindowView(action)
        } else if (action.simplePressImpl != null) {
            action.simplePressImpl.invoke(keyboardManagerForAction, persistentStates[action])
        } else {
            throw IllegalStateException("An action must have either a window implementation or a simple press implementation")
        }
    }

    fun onActionAltActivated(rawAction: Action) {
        latinIME.inputLogic.finishInput()

        val action = runBlocking {
            ActionRegistry.getActionOverride(latinIME, rawAction)
        }

        action.altPressImpl?.invoke(keyboardManagerForAction, persistentStates[action])
    }

    @Composable
    private fun MainKeyboardViewWithActionBar() {
        val view = LocalView.current

        val actionBarShown = useDataStore(ActionBarDisplayedSetting)

        Column {
            // Don't show suggested words when it's not meant to be shown
            val suggestedWordsOrNull = if(shouldShowSuggestionStrip) {
                suggestedWords
            } else {
                null
            }

            if(actionBarShown.value) {
                ActionBar(
                    suggestedWordsOrNull,
                    latinIME.latinIMELegacy as SuggestionStripViewListener,
                    inlineSuggestions = inlineSuggestions,
                    onActionActivated = {
                        keyboardManagerForAction.performHapticAndAudioFeedback(
                            Constants.CODE_TAB,
                            view
                        )
                        onActionActivated(it)
                    },
                    onActionAltActivated = {
                        if (it.altPressImpl != null) {
                            keyboardManagerForAction.performHapticAndAudioFeedback(
                                Constants.CODE_TAB,
                                view
                            )
                        }
                        onActionAltActivated(it)
                    },
                    importantNotice = currentNotice.value,
                    keyboardManagerForAction = keyboardManagerForAction,
                    isActionsExpanded = isActionsExpanded.value,
                    toggleActionsExpanded = { toggleActionsExpanded() },
                )
            }
        }
    }

    private fun enterActionWindowView(action: Action) {
        assert(action.windowImpl != null)

        mainKeyboardHidden = true

        currWindowAction = action

        if (persistentStates[action] == null) {
            persistentStates[action] = action.persistentState?.let { it(keyboardManagerForAction) }
        }

        currWindowActionWindow = (action.windowImpl!!)(keyboardManagerForAction, persistentStates[action])

        if(action.keepScreenAwake) {
            latinIME.window.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        setContent()

        keyboardManagerForAction.announce("${latinIME.resources.getString(action.name)} mode")
    }

    fun returnBackToMainKeyboardViewFromAction() {
        if(currWindowActionWindow == null) return

        val name = latinIME.resources.getString(currWindowAction!!.name)

        currWindowActionWindow!!.close()

        currWindowAction = null
        currWindowActionWindow = null

        mainKeyboardHidden = false

        latinIME.onKeyboardShown()

        latinIME.window.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent()

        keyboardManagerForAction.announce("$name closed")
    }

    fun toggleExpandAction(to: Boolean? = null) {
        mainKeyboardHidden = !(to ?: mainKeyboardHidden)
        if(!mainKeyboardHidden) {
            latinIME.onKeyboardShown()
        }

        setContent()
    }

    @Composable
    private fun ActionViewWithHeader(windowImpl: ActionWindow) {
        val heightDiv = if(mainKeyboardHidden) {
            1
        } else {
            1.5
        }
        Column {
            if(mainKeyboardHidden || latinIME.isInputConnectionOverridden) {
                ActionWindowBar(
                    onBack = { returnBackToMainKeyboardViewFromAction() },
                    canExpand = currWindowAction!!.canShowKeyboard,
                    onExpand = { toggleExpandAction() },
                    windowTitleBar = { windowImpl.WindowTitleBar(this) }
                )
            }

            Box(modifier = Modifier
                .fillMaxWidth()
                .height(with(LocalDensity.current) {
                    (latinIME
                        .getInputViewHeight()
                        .toFloat() / heightDiv.toFloat()).toDp()
                })
            ) {
                windowImpl.WindowContents(keyboardShown = !isMainKeyboardHidden)
            }

            if(!mainKeyboardHidden && !latinIME.isInputConnectionOverridden) {
                val suggestedWordsOrNull = if (shouldShowSuggestionStrip) {
                    suggestedWords
                } else {
                    null
                }

                CollapsibleSuggestionsBar(
                    onCollapse = { toggleExpandAction() },
                    onClose = { returnBackToMainKeyboardViewFromAction() },
                    words = suggestedWordsOrNull,
                    suggestionStripListener = latinIME.latinIMELegacy as SuggestionStripViewListener
                )
            }
        }
    }

    val activeDialogRequest: MutableState<ActiveDialogRequest?> = mutableStateOf(null)
    val activeDialogRequestDismissed: MutableState<Boolean> = mutableStateOf(true)

    @Composable
    fun BoxScope.ForgetWordDialog() {
        AnimatedVisibility(
            visible = !activeDialogRequestDismissed.value,
            modifier = Modifier.matchParentSize(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (activeDialogRequest.value != null) {
                Box(modifier = Modifier.matchParentSize()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.66f),
                        modifier = Modifier
                            .matchParentSize()
                            .pointerInput(Unit) {
                                this.detectTapGestures(onPress = {
                                    activeDialogRequestDismissed.value = true
                                    activeDialogRequest.value?.onCancel?.invoke()
                                })
                            }
                    ) { }

                    Box(modifier = Modifier
                        .matchParentSize()
                        .padding(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    activeDialogRequest.value?.text ?: "",
                                    style = Typography.bodyMedium
                                )

                                Row {
                                    activeDialogRequest.value?.options?.forEach {
                                        TextButton(
                                            onClick = {
                                                it.onClick()
                                                activeDialogRequestDismissed.value = true
                                            }
                                        ) {
                                            Text(it.option)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private var languageSwitcherDialog: DialogComposeView? = null
    fun showLanguageSwitcher() {
        // Dismiss old dialog
        languageSwitcherDialog?.dismiss()

        // Create new dialog
        languageSwitcherDialog = createDialogComposeView(latinIME) {
            DataStoreCacheProvider {
                UixThemeAuto {
                    LanguageSwitcherDialog(
                        onDismiss = { it.dismiss() }
                    )
                }
            }
        }

        languageSwitcherDialog?.show()
    }

    @Composable
    fun ActionEditorHost() {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            AnimatedVisibility(
                visible = isShowingActionEditor.value,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                content = {
                    ActionEditor()
                },
            )
        }
    }

    @Composable
    fun InputDarkener(darken: Boolean, onClose: () -> Unit) {
        val color by animateColorAsState(
            if (darken) Color.Black.copy(alpha = 0.25f) else Color.Transparent
        )

        LaunchedEffect(darken) {
            latinIME.setInputModal(darken)
        }

        Box(Modifier
            .background(color)
            .fillMaxWidth()
            .fillMaxHeight()
            .then(
                if (darken) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures {
                            onClose()
                        }
                    }
                } else {
                    Modifier
                })
        )
    }

    @Composable
    private fun OffsetPositioner(offset: Offset, content: @Composable () -> Unit) {
        Column(modifier = Modifier.fillMaxHeight().absoluteOffset { IntOffset(offset.x.toInt(), 0) }) {
            Spacer(Modifier.weight(1.0f))
            content()
            Spacer(Modifier.height(with(LocalDensity.current) { offset.y.toDp() }))
        }
    }

    @Composable
    private fun KeyboardSurface(
        requiredWidthPx: Int,
        backgroundColor: Color,
        modifier: Modifier = Modifier,
        shape: Shape = RectangleShape,
        padding: Rect = Rect(),
        content: @Composable BoxScope.() -> Unit
    ) = with(LocalDensity.current) {
        Box(modifier
            .onSizeChanged { measuredTouchableHeight = it.height}
            .background(backgroundColor, shape)
            .requiredWidth(requiredWidthPx.toDp())
            .absolutePadding(
                left = padding.left.toDp().coerceAtLeast(0.dp),
                top = padding.top.toDp().coerceAtLeast(0.dp),
                right = padding.right.toDp().coerceAtLeast(0.dp),
                bottom = padding.bottom.toDp().coerceAtLeast(0.dp),
            )
            .clipToBounds()
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColorFor(backgroundColor)) {
                content()
            }
        }
    }

    @Composable
    private fun FloatingKeyboardContents(
        pointerInputKey: Any?,
        onDragged: (Offset) -> Unit,
        onDragEnd: () -> Unit,
        onResizerOpen: () -> Unit,
        content: @Composable BoxScope.(actionBarGap: Dp) -> Unit
    ) {
        // Content
        Box(Modifier.fillMaxWidth()) {
            content(4.dp)
        }

        // Bottom drag bar
        Spacer(modifier = Modifier.height(24.dp))
        Box(modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .pointerInput(pointerInputKey) {
                detectDragGestures(
                    onDrag = { _, dragAmount -> onDragged(dragAmount)},
                    onDragEnd = { onDragEnd() })
            }) {

            IconButton(onClick = {
                onResizerOpen()
            }, Modifier.align(Alignment.CenterEnd)) {
                Icon(Icons.Default.Menu, contentDescription = "resize")
            }

            Box(
                modifier = Modifier.fillMaxWidth(0.6f).height(4.dp)
                    .align(Alignment.TopCenter).background(
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        RoundedCornerShape(100)
                    )
            )
        }
    }

    @Composable
    private fun FloatingKeyboardWindow(
        size: FloatingKeyboardSize,
        content: @Composable BoxScope.(actionBarGap: Dp) -> Unit
    ) = with(LocalDensity.current) {
        val offset = remember(size) { mutableStateOf(Offset(size.bottomOrigin.first.toFloat(), size.bottomOrigin.second.toFloat())) }

        OffsetPositioner(offset.value) {
            KeyboardSurface(
                requiredWidthPx = size.width,
                backgroundColor = latinIME.keyboardColor,
                shape = RoundedCornerShape(16.dp),
                padding = size.decorationPadding
            ) {
                Column {
                    FloatingKeyboardContents(
                        pointerInputKey = size,
                        onDragged = { dragAmount ->
                            var newOffset = offset.value.copy(
                                x = offset.value.x + dragAmount.x,
                                y = offset.value.y - dragAmount.y
                            )

                            // Ensure we are not out of bounds
                            newOffset = newOffset.copy(
                                newOffset.x.coerceAtLeast(0.0f),
                                newOffset.y.coerceAtLeast(0.0f)
                            )
                            newOffset = newOffset.copy(
                                newOffset.x.coerceAtMost(
                                    latinIME.getViewWidth().toFloat() - size.width
                                ),
                                newOffset.y.coerceAtMost(
                                    latinIME.getViewHeight().toFloat() - measuredTouchableHeight
                                )
                            )

                            offset.value = newOffset
                        },
                        onDragEnd = {
                            latinIME.sizingCalculator.editSavedSettings { settings ->
                                settings.copy(
                                    floatingBottomOriginDp = Pair(
                                        offset.value.x.toDp().value,
                                        offset.value.y.toDp().value
                                    )
                                )
                            }
                        },
                        onResizerOpen = {
                            resizing.value = true
                        },
                        content = content
                    )
                }

                ResizerSwitch(size)
            }
        }
    }

    @Composable
    private fun BoxScope.FloatingKeyboardResizer(size: FloatingKeyboardSize) = with(LocalDensity.current) {
        ResizerRect({ delta ->
            // Matching the necessary coordinate space
            var deltaX = delta.left
            var deltaY = -delta.bottom
            var deltaWidth = delta.right - delta.left
            var deltaHeight = delta.bottom - delta.top

            var result = true

            // TODO: Limit the values so that we do not go off-screen
            // If we have reached a minimum limit, return false

            // Basic limiting for minimum size
            val currSettings = latinIME.sizingCalculator.getSavedSettings()
            val currSize = Size(
                currSettings.floatingWidthDp.dp.toPx(),
                currSettings.floatingHeightDp.dp.toPx()
            )

            if(currSize.width + deltaWidth < 200.dp.toPx()) {
                deltaWidth = deltaWidth.coerceAtLeast(200.dp.toPx() - currSize.width)
                deltaX = 0.0f
                result = false
            }

            if(currSize.height + deltaHeight < 160.dp.toPx()) {
                deltaHeight = deltaHeight.coerceAtLeast(160.dp.toPx() - currSize.height)
                deltaY = 0.0f
                result = false
            }

            latinIME.sizingCalculator.editSavedSettings { settings ->
                settings.copy(
                    floatingBottomOriginDp = Pair(
                        settings.floatingBottomOriginDp.first + deltaX.toDp().value,
                        settings.floatingBottomOriginDp.second + deltaY.toDp().value
                    ),
                    floatingWidthDp = settings.floatingWidthDp + deltaWidth.toDp().value,
                    floatingHeightDp = settings.floatingHeightDp + deltaHeight.toDp().value
                )
            }

            result
        }, true, {
            resizing.value = false
        }, {
            // Reset
        })
    }

    @Composable
    private fun BoxScope.RegularKeyboardResizer(size: RegularKeyboardSize) = with(LocalDensity.current) {
        ResizerRect({ delta ->
            var result = true

            val sideDelta = delta.left - delta.right
            latinIME.sizingCalculator.editSavedSettings { settings ->
                val existingHeight = latinIME.size.value!!.getHeight()
                var targetHeight = existingHeight + delta.top

                var newSidePadding =
                    (settings.paddingDp.left + sideDelta.toDp().value)
                if (newSidePadding !in 0.0f..64.0f) {
                    newSidePadding = newSidePadding.coerceIn(0.0f..64.0f)
                    result = false
                }

                var newBottomPadding =
                    (settings.paddingDp.bottom - delta.bottom.toDp().value)
                if (newBottomPadding !in 0.0f..64.0f) {
                    // Correct for height difference if it's being dragged up/down
                    val correction = if (newBottomPadding < 0.0f) {
                        newBottomPadding.dp.toPx().coerceAtLeast(-delta.top)
                    } else {
                        (newBottomPadding - 64.0f).dp.toPx()
                            .coerceAtMost(-delta.top)
                    }
                    targetHeight += correction

                    newBottomPadding = newBottomPadding.coerceIn(0.0f..64.0f)
                    result = false
                }

                var newHeightMultiplier =
                    (settings.heightMultiplier * (existingHeight / targetHeight))
                if (newHeightMultiplier !in 0.3f..2.0f) {
                    newHeightMultiplier =
                        newHeightMultiplier.coerceIn(0.3f..2.0f)
                    result = false
                }

                settings.copy(
                    paddingDp = Rect(
                        newSidePadding.roundToInt(),
                        settings.paddingDp.top,
                        newSidePadding.roundToInt(),
                        newBottomPadding.roundToInt(),
                    ),
                    heightMultiplier = newHeightMultiplier
                )
            }
            result
        }, true, {
            resizing.value = false
        }, {
            // TODO: Reset
        })
    }

    @Composable
    private fun BoxScope.OneHandedResizer(size: OneHandedKeyboardSize) = with(LocalDensity.current) {
        Box(
            modifier = Modifier.matchParentSize().let {
                val pad = (size.width - size.layoutWidth - size.padding.left - size.padding.right).toDp().coerceAtLeast(0.dp)
                when(size.direction) {
                    OneHandedDirection.Left -> it.absolutePadding(right = pad)
                    OneHandedDirection.Right -> it.absolutePadding(left = pad)
                }
            }
        ) {
            ResizerRect({ delta ->
                var result = true

                latinIME.sizingCalculator.editSavedSettings { settings ->
                    val existingHeight = latinIME.size.value!!.getHeight()
                    var targetHeight = existingHeight + delta.top

                    var newHeightMultiplier =
                        ((settings.oneHandedHeightMultiplier ?: settings.heightMultiplier) * (existingHeight / targetHeight))
                    if (newHeightMultiplier !in 0.3f..2.0f) {
                        newHeightMultiplier =
                            newHeightMultiplier.coerceIn(0.3f..2.0f)
                        result = false
                    }

                    // These have to be flipped in right handed mode for the setting
                    val deltaLeft = if(size.direction == OneHandedDirection.Left) {
                        delta.left
                    } else {
                        -delta.right
                    }

                    val deltaRight = if(size.direction == OneHandedDirection.Left) {
                        delta.right
                    } else {
                        -delta.left
                    }

                    settings.copy(
                        oneHandedRectDp = Rect(
                            settings.oneHandedRectDp.left + deltaLeft.toDp().value.toInt(),
                            settings.oneHandedRectDp.top,
                            settings.oneHandedRectDp.right + deltaRight.toDp().value.toInt(),
                            settings.oneHandedRectDp.bottom - delta.bottom.toDp().value.toInt(),
                        ),
                        oneHandedHeightMultiplier = newHeightMultiplier
                    )
                }
                result
            }, true, {
                resizing.value = false
            }, {
                // TODO: Reset
            })
        }
    }

    @Composable
    private fun BoxScope.SplitKeyboardResizer(size: SplitKeyboardSize) = with(LocalDensity.current) {
        Box(
            modifier = Modifier.matchParentSize()
                .width(size.splitLayoutWidth.toDp()).align(
                    Alignment.CenterStart
                )
        ) {
            ResizerRect({ delta ->
                true
            }, true, {
                resizing.value = false
            }, {

            })
        }
    }

    @Composable
    private fun BoxScope.ResizerSwitch(size: ComputedKeyboardSize) {
        if(!resizing.value) return
        when (size) {
            is OneHandedKeyboardSize -> OneHandedResizer(size)
            is RegularKeyboardSize -> RegularKeyboardResizer(size)
            is SplitKeyboardSize -> SplitKeyboardResizer(size)
            is FloatingKeyboardSize -> FloatingKeyboardResizer(size)
        }
    }

    @Composable
    private fun NonFloatingKeyboardWindow(
        size: ComputedKeyboardSize,
        content: @Composable BoxScope.(actionBarGap: Dp) -> Unit
    ) = with(LocalDensity.current) {
        OffsetPositioner(Offset(0.0f, 0.0f)) {
            KeyboardSurface(
                requiredWidthPx = size.getWidth(),
                backgroundColor = latinIME.keyboardColor,
                padding = size.getPadding()
            ) {
                val actionBarGap = 4.dp

                when(size) {
                    is OneHandedKeyboardSize -> {
                        Box(modifier = Modifier.width(size.layoutWidth.toDp()).align(
                            when(size.direction) {
                                OneHandedDirection.Left -> Alignment.CenterStart
                                OneHandedDirection.Right -> Alignment.CenterEnd
                            }
                        )) {
                            content(actionBarGap)
                        }
                    }
                    else -> {
                        content(actionBarGap)
                    }
                }

                ResizerSwitch(size)
            }
        }
    }

    @Composable
    fun KeyboardWindowSelector(content: @Composable BoxScope.(actionBarGap: Dp) -> Unit) {
        val size = latinIME.size.value
        when(size) {
            is FloatingKeyboardSize -> FloatingKeyboardWindow(size, content)

            is OneHandedKeyboardSize,
            is RegularKeyboardSize,
            is SplitKeyboardSize -> NonFloatingKeyboardWindow(size, content)

            null -> return
        }
    }

    @Composable
    private fun ProvidersAndWrapper(content: @Composable () -> Unit) {
        UixThemeWrapper(latinIME.colorScheme) {
            DataStoreCacheProvider {
                CompositionLocalProvider(LocalManager provides keyboardManagerForAction) {
                    CompositionLocalProvider(LocalThemeProvider provides latinIME.getDrawableProvider()) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            CompositionLocalProvider(LocalFoldingState provides foldingOptions.value) {
                                content()
                            }
                        }
                    }
                }
            }
        }
    }

    fun setContent() {
        composeView?.setContent {
            ProvidersAndWrapper {
                InputDarkener(isInputOverridden.value || isShowingActionEditor.value) {
                    closeActionWindow()
                    isShowingActionEditor.value = false
                }

                KeyboardWindowSelector { gap ->
                    Column {
                        when {
                            currWindowActionWindow != null -> ActionViewWithHeader(
                                currWindowActionWindow!!
                            )

                            else -> MainKeyboardViewWithActionBar()
                        }

                        Spacer(modifier = Modifier.height(gap))

                        latinIME.LegacyKeyboardView(hidden = isMainKeyboardHidden)
                    }

                    ForgetWordDialog()
                }

                ActionEditorHost()
            }
        }
    }

    suspend fun showUpdateNoticeIfNeeded() {
        if(!BuildConfig.UPDATE_CHECKING) return

        autoDeferManualUpdateIfNeeded(latinIME)

        val updateInfo = retrieveSavedLastUpdateCheckResult(latinIME)
        if(updateInfo != null && updateInfo.isNewer()) {
            if(!latinIME.getSetting(DISABLE_UPDATE_REMINDER)) {
                numSuggestionsSinceNotice = 0
                currentNotice.value = object : ImportantNotice {
                    @Composable
                    override fun getText(): String {
                        return "Update available: ${updateInfo.nextVersionString}"
                    }

                    override fun onDismiss(context: Context) {
                        currentNotice.value = null
                    }

                    override fun onOpen(context: Context) {
                        currentNotice.value = null

                        val intent = Intent(context, SettingsActivity::class.java)
                        intent.putExtra("navDest", "update")

                        if (context !is Activity) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }

                        context.startActivity(intent)
                    }
                }
            }
        } else {
            if(isManualUpdateTimeExpired(latinIME)) {
                numSuggestionsSinceNotice = 0
                currentNotice.value = object : ImportantNotice {
                    @Composable
                    override fun getText(): String {
                        return "Please tap to check for updates"
                    }

                    override fun onDismiss(context: Context) {
                        currentNotice.value = null
                    }

                    override fun onOpen(context: Context) {
                        currentNotice.value = null
                        context.openManualUpdateCheck()

                        runBlocking {
                            deferManualUpdate(latinIME)
                        }
                    }
                }

            }
        }
    }

    fun onColorSchemeChanged() {
        setContent()
    }

    fun onInputFinishing() {
        closeActionWindow()
        languageSwitcherDialog?.dismiss()
        isShowingActionEditor.value = false
        resizing.value = false
    }

    fun cleanUpPersistentStates() {
        println("Cleaning up persistent states")
        for((key, value) in persistentStates.entries) {
            if(currWindowAction != key) {
                latinIME.lifecycleScope.launch { value?.cleanUp() }
            }
        }
    }

    fun closeActionWindow() {
        if(currWindowActionWindow == null) return
        returnBackToMainKeyboardViewFromAction()
    }


    fun updateVisibility(shouldShowSuggestionsStrip: Boolean, fullscreenMode: Boolean) {
        this.shouldShowSuggestionStrip = shouldShowSuggestionsStrip
        setContent()
    }

    fun setSuggestions(suggestedWords: SuggestedWords?, rtlSubtype: Boolean) {
        this.suggestedWords = suggestedWords
        setContent()

        if(currentNotice.value != null) {
            numSuggestionsSinceNotice += 1
            if(numSuggestionsSinceNotice > 4) {
                currentNotice.value?.onDismiss(latinIME)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        currentNotice.value?.onDismiss(latinIME)

        inlineSuggestions = response.inlineSuggestions.map {
            latinIME.inflateInlineSuggestion(it)
        }
        setContent()

        return true
    }

    fun openEmojiKeyboard() {
        if(currWindowAction == null) {
            onActionActivated(EmojiAction)
        }
    }

    fun triggerAction(id: Int, alt: Boolean) {
        val action = AllActions.getOrNull(id) ?: throw IllegalArgumentException("No such action with ID $id")

        if(alt) {
            onActionAltActivated(action)
        } else {
            if (currWindowAction != null && action.windowImpl != null) {
                closeActionWindow()
            }

            onActionActivated(action)
        }
    }

    fun requestForgetWord(suggestedWordInfo: SuggestedWords.SuggestedWordInfo) {
        keyboardManagerForAction.requestDialog(
            latinIME.getString(R.string.blacklist_from_suggestions, suggestedWordInfo.mWord),
            listOf(
                DialogRequestItem(latinIME.getString(R.string.cancel)) { },
                DialogRequestItem(latinIME.getString(R.string.blacklist)) {
                    latinIME.forceForgetWord(suggestedWordInfo)
                },
            ) + if(suggestedWordInfo.mKindAndFlags == SuggestedWordInfo.KIND_EMOJI_SUGGESTION) {
                listOf(
                    DialogRequestItem(latinIME.getString(R.string.disable_emoji)) {
                        runBlocking { latinIME.setSetting(SHOW_EMOJI_SUGGESTIONS, false) }
                        latinIME.refreshSuggestions()
                    }
                )
            } else {
                listOf()
            },
            { }
        )

        val v = latinIME.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v!!.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            v!!.vibrate(50)
        }
    }

    fun onCreate() {
        AllActions.forEach { action ->
            if(action.persistentStateInitialization == PersistentStateInitialization.OnKeyboardLoad) {
                persistentStates[action] = action.persistentState?.let { it(keyboardManagerForAction) }
            }
        }

        isActionsExpanded.value = latinIME.getSettingBlocking(ActionBarExpanded)

        latinIME.lifecycleScope.launch(Dispatchers.Main) {
            WindowInfoTracker.getOrCreate(latinIME).windowLayoutInfo(latinIME).collect {
                foldingOptions.value = FoldingOptions(it.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull())
                latinIME.invalidateKeyboard(true)
            }
        }
    }

    fun onPersistentStatesUnlocked() {
        persistentStates.forEach {
            latinIME.lifecycleScope.launch {
                it.value?.onDeviceUnlocked()
            }
        }
    }
}