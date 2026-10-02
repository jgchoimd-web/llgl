package com.llgl.app.ime

import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodSubtype
import com.llgl.app.dial.DialLayers
import com.llgl.app.dial.DialRecognizer
import com.llgl.app.dial.DialTables
import com.llgl.app.hangul.HangulComposer
import com.llgl.app.hangul.Jamo
import com.llgl.app.keyboard.Dir
import com.llgl.app.keyboard.Gesture
import com.llgl.app.keyboard.KeyAction
import com.llgl.app.keyboard.Layer
import com.llgl.app.predict.PredictionModel
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * The input method. Turns dial gestures from [KeyboardView] into text through the Hangul composer,
 * keeps the editor's composing region in sync, and feeds the prediction model.
 */
class ThumbInputMethodService : InputMethodService(), KeyboardView.Listener {

    private lateinit var settingsStore: KeyboardSettings
    private var settings = KeyboardSettings.Values()
    private var model = PredictionModel()
    private val composer = HangulComposer()
    private var view: KeyboardView? = null
    private var layer = Layer.HANGUL
    private var lettersLayer = Layer.HANGUL
    private var predictionEnabled = true
    private var capSentences = false
    private var enterSendsNewline = true
    private var enterAction = EditorInfo.IME_ACTION_NONE
    private var candidates: List<String> = emptyList()
    private var learnsSinceSave = 0
    private val executor = Executors.newSingleThreadExecutor()
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KeyboardSettings.KEY_MODEL_RESET) {
            resetModel()
        } else {
            settings = settingsStore.load()
            view?.settings = settings
        }
    }

    override fun onCreate() {
        super.onCreate()
        settingsStore = KeyboardSettings(this)
        settings = settingsStore.load()
        settingsStore.addListener(prefsListener)
        loadModel()
    }

    override fun onDestroy() {
        settingsStore.removeListener(prefsListener)
        saveModel()
        executor.shutdown()
        super.onDestroy()
    }

    override fun onCreateInputView(): View = KeyboardView(this).also {
        it.settings = settings
        it.listener = this
        it.layer = layer
        view = it
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        composer.flush()
        configure(attribute)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        view?.layer = layer
        refreshCandidates()
    }

    override fun onFinishInput() {
        currentInputConnection?.let { finishComposition(it) }
        composer.flush()
        super.onFinishInput()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        saveModel()
        super.onFinishInputView(finishingInput)
    }

    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        val english = newSubtype.languageTag.startsWith("en", ignoreCase = true)
        lettersLayer = if (english) Layer.ENGLISH else Layer.HANGUL
        if (layer != Layer.SYMBOLS && layer != lettersLayer) {
            currentInputConnection?.let { finishComposition(it) }
            layer = lettersLayer
            view?.layer = layer
        }
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // The user moved the cursor away from what we were composing: let the editor keep it as is.
        if (!composer.isEmpty && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            composer.flush()
            currentInputConnection?.finishComposingText()
        }
        refreshCandidates()
    }

    private fun configure(info: EditorInfo?) {
        val inputType = info?.inputType ?: 0
        val options = info?.imeOptions ?: 0
        val klass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val isPassword = (klass == InputType.TYPE_CLASS_TEXT && (
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            )) || (klass == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        val noLearning = (options and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0 ||
            (inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
        predictionEnabled = !isPassword && !noLearning
        capSentences = (inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0
        val multiline = (inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        enterAction = options and EditorInfo.IME_MASK_ACTION
        enterSendsNewline = multiline ||
            (options and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0 ||
            enterAction == EditorInfo.IME_ACTION_NONE || enterAction == EditorInfo.IME_ACTION_UNSPECIFIED
        val asciiField = klass == InputType.TYPE_CLASS_TEXT && (
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_URI
            )
        layer = when {
            klass == InputType.TYPE_CLASS_NUMBER || klass == InputType.TYPE_CLASS_PHONE || klass == InputType.TYPE_CLASS_DATETIME -> Layer.SYMBOLS
            isPassword || asciiField -> Layer.ENGLISH
            else -> lettersLayer
        }
    }

    // ---------------------------------------------------------------------------------------
    // KeyboardView.Listener
    // ---------------------------------------------------------------------------------------

    override fun onResult(result: DialRecognizer.Result) {
        val ic = currentInputConnection ?: return
        when (result) {
            DialRecognizer.Result.None -> Unit
            is DialRecognizer.Result.Hub -> perform(ic, hubAction(result.gesture))
            is DialRecognizer.Result.Item -> item(ic, result)
            is DialRecognizer.Result.Syllable -> syllable(ic, result)
        }
    }

    override fun onCandidate(index: Int) {
        val ic = currentInputConnection ?: return
        val word = candidates.getOrNull(index) ?: return
        val (current, previous) = wordsBeforeCursor(ic)
        val composingLength = composer.composing.length
        ic.beginBatchEdit()
        ic.setComposingText("", 1)
        composer.flush()
        val committedPrefix = (current.length - composingLength).coerceAtLeast(0)
        if (committedPrefix > 0) ic.deleteSurroundingText(committedPrefix, 0)
        ic.commitText(if (settings.autoSpace) "$word " else word, 1)
        ic.endBatchEdit()
        if (predictionEnabled) learn(word, previous)
        refreshCandidates()
    }

    override fun onBackspaceRepeat() {
        currentInputConnection?.let { backspace(it) }
    }

    override fun preview(result: DialRecognizer.Result): String = when (result) {
        DialRecognizer.Result.None -> ""
        is DialRecognizer.Result.Hub -> when (val action = hubAction(result.gesture)) {
            KeyAction.Space -> "␣"
            KeyAction.Backspace -> "⌫"
            KeyAction.DeleteWord -> "⌫ 단어"
            KeyAction.Enter -> "↵"
            KeyAction.Newline -> "↵ 줄바꿈"
            is KeyAction.MoveCursor -> if (action.delta < 0) "◀" else "▶"
            else -> ""
        }
        is DialRecognizer.Result.Item -> {
            val item = DialLayers.items(layer, result.ring)?.getOrNull(result.index)
            val action = item?.action
            when {
                item == null -> ""
                action is KeyAction.Consonant -> (if (result.pushed) DialTables.strengthen(action.c) else action.c).toString()
                result.pushed -> (item.pushed as? KeyAction.Text)?.text ?: item.label
                else -> item.label
            }
        }
        is DialRecognizer.Result.Syllable -> previewSyllable(result)
    }

    private fun previewSyllable(s: DialRecognizer.Result.Syllable): String {
        val index = s.initialIndex
        val cho: Char = if (index == null) {
            if (composer.isLoneConsonant) composer.composing[0] else 'ㅇ'
        } else {
            val item = DialLayers.outer(layer).getOrNull(index) ?: return ""
            val action = item.action
            if (action !is KeyAction.Consonant) return item.label
            if (s.initialHardened) DialTables.strengthen(action.c) else action.c
        }
        val v = DialTables.vowel(s.vowelSet, s.vowelTicks)
        return Jamo.compose(cho, v, finalOf(s)).toString()
    }

    // ---------------------------------------------------------------------------------------
    // Dial semantics
    // ---------------------------------------------------------------------------------------

    /** The hub: tap = space, flick away from the hand = backspace, flick up = enter. */
    private fun hubAction(gesture: Gesture): KeyAction {
        val (dir, long) = when (gesture) {
            Gesture.Tap -> return KeyAction.Space
            is Gesture.Flick -> gesture.dir to gesture.long
            // A bent flick carries no length, so read it as the short (non-destructive) form of its first leg.
            is Gesture.Turn -> gesture.first to false
        }
        return when (if (settings.leftHanded) mirror(dir) else dir) {
            Dir.W, Dir.SW, Dir.NW -> if (long) KeyAction.DeleteWord else KeyAction.Backspace
            Dir.N, Dir.NE -> if (long) KeyAction.Newline else KeyAction.Enter
            Dir.E, Dir.SE -> KeyAction.MoveCursor(1)
            Dir.S -> KeyAction.MoveCursor(-1)
        }
    }

    private fun mirror(dir: Dir): Dir = when (dir) {
        Dir.E -> Dir.W
        Dir.W -> Dir.E
        Dir.NE -> Dir.NW
        Dir.NW -> Dir.NE
        Dir.SE -> Dir.SW
        Dir.SW -> Dir.SE
        Dir.N, Dir.S -> dir
    }

    /** A fixed item on any ring: a bare consonant tap, a letter, a symbol or a layer key; pushed = its alternate. */
    private fun item(ic: InputConnection, r: DialRecognizer.Result.Item) {
        val item = DialLayers.items(layer, r.ring)?.getOrNull(r.index) ?: return
        val action = item.action
        if (action is KeyAction.Consonant) {
            val c = if (r.pushed) DialTables.strengthen(action.c) else action.c
            ic.beginBatchEdit()
            commitPrefix(ic, composer.consonant(c))
            ic.setComposingText(composer.composing, 1)
            ic.endBatchEdit()
            refreshCandidates()
            return
        }
        perform(ic, if (r.pushed) item.pushed ?: action else action)
    }

    /** A dialled syllable: initial from the outer ring (or a silent ㅇ), vowel by pulses, optional final by pulses. */
    private fun syllable(ic: InputConnection, s: DialRecognizer.Result.Syllable) {
        val index = s.initialIndex
        var cho: Char? = null
        if (index != null) {
            val item = DialLayers.outer(layer).getOrNull(index) ?: return
            val action = item.action
            if (action !is KeyAction.Consonant) {
                perform(ic, if (s.initialHardened) item.pushed ?: action else action)
                return
            }
            cho = if (s.initialHardened) DialTables.strengthen(action.c) else action.c
        }
        val v = DialTables.vowel(s.vowelSet, s.vowelTicks)
        ic.beginBatchEdit()
        if (cho == null && composer.isLoneConsonant) {
            // "ㄱ" tapped earlier, then a vowel dialled from the inner ring: finish that syllable.
            commitPrefix(ic, composer.vowel(v))
        } else {
            commitPrefix(ic, composer.syllable(cho ?: 'ㅇ', v))
        }
        finalOf(s)?.let { commitPrefix(ic, composer.consonant(it)) }
        ic.setComposingText(composer.composing, 1)
        ic.endBatchEdit()
        refreshCandidates()
    }

    private fun finalOf(s: DialRecognizer.Result.Syllable): Char? {
        val ticks = s.finalTicks ?: return null
        val f = DialTables.final(ticks)
        if (!s.finalHardened) return f
        val hard = DialTables.strengthen(f)
        return if (Jamo.canBeFinal(hard)) hard else f
    }

    // ---------------------------------------------------------------------------------------
    // Editing
    // ---------------------------------------------------------------------------------------

    private fun perform(ic: InputConnection, action: KeyAction) {
        when (action) {
            is KeyAction.Text -> {
                ic.beginBatchEdit()
                finishComposition(ic)
                var text = action.text
                if (isSeparator(text)) learnCurrentWord(ic)
                if (layer == Layer.ENGLISH && capSentences && text.length == 1 && text[0].isLowerCase() && atSentenceStart(ic)) {
                    text = text.uppercase()
                }
                ic.commitText(text, 1)
                ic.endBatchEdit()
            }
            KeyAction.Space -> {
                ic.beginBatchEdit()
                finishComposition(ic)
                learnCurrentWord(ic)
                ic.commitText(" ", 1)
                ic.endBatchEdit()
            }
            KeyAction.Backspace -> backspace(ic)
            KeyAction.DeleteWord -> deleteWord(ic)
            KeyAction.Enter -> {
                ic.beginBatchEdit()
                finishComposition(ic)
                learnCurrentWord(ic)
                ic.endBatchEdit()
                if (enterSendsNewline) ic.commitText("\n", 1) else ic.performEditorAction(enterAction)
            }
            KeyAction.Newline -> {
                finishComposition(ic)
                ic.commitText("\n", 1)
            }
            KeyAction.ToggleLang -> {
                finishComposition(ic)
                lettersLayer = if (lettersLayer == Layer.HANGUL) Layer.ENGLISH else Layer.HANGUL
                layer = lettersLayer
                view?.layer = layer
            }
            KeyAction.ToggleSymbols -> {
                finishComposition(ic)
                layer = if (layer == Layer.SYMBOLS) lettersLayer else Layer.SYMBOLS
                view?.layer = layer
            }
            is KeyAction.MoveCursor -> {
                finishComposition(ic)
                val code = if (action.delta < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
                repeat(abs(action.delta)) { sendDownUpKeyEvents(code) }
            }
            is KeyAction.Consonant, KeyAction.None -> Unit
        }
        refreshCandidates()
    }

    private fun backspace(ic: InputConnection) {
        if (composer.backspace()) {
            ic.setComposingText(composer.composing, 1)
            if (composer.isEmpty) ic.finishComposingText()
        } else {
            val before = ic.getTextBeforeCursor(2, 0)
            val count = if (before != null && before.length >= 2 && Character.isSurrogatePair(before[0], before[1])) 2 else 1
            ic.deleteSurroundingText(count, 0)
        }
        refreshCandidates()
    }

    private fun deleteWord(ic: InputConnection) {
        ic.beginBatchEdit()
        if (!composer.isEmpty) {
            composer.flush()
            ic.setComposingText("", 1)
            ic.finishComposingText()
        }
        val before = ic.getTextBeforeCursor(64, 0)?.toString() ?: ""
        var count = 0
        var i = before.length - 1
        while (i >= 0 && before[i].isWhitespace()) {
            count++
            i--
        }
        while (i >= 0 && !isSeparatorChar(before[i])) {
            count++
            i--
        }
        ic.deleteSurroundingText(if (count == 0) 1 else count, 0)
        ic.endBatchEdit()
        refreshCandidates()
    }

    /** Commits the composing text as final text (or just ends an empty composition). */
    private fun finishComposition(ic: InputConnection) {
        val text = composer.flush()
        if (text.isNotEmpty()) ic.commitText(text, 1) else ic.finishComposingText()
    }

    /** Commits a finished syllable in place of the composing region, before a new one is composed. */
    private fun commitPrefix(ic: InputConnection, text: String) {
        if (text.isNotEmpty()) ic.commitText(text, 1)
    }

    private fun atSentenceStart(ic: InputConnection): Boolean {
        val before = ic.getTextBeforeCursor(4, 0)?.toString() ?: return true
        if (before.isEmpty()) return true
        val last = before.last()
        if (last == '\n') return true
        if (!last.isWhitespace()) return false
        val trimmed = before.trimEnd()
        return trimmed.isEmpty() || trimmed.last() in ".?!"
    }

    // ---------------------------------------------------------------------------------------
    // Prediction
    // ---------------------------------------------------------------------------------------

    private fun refreshCandidates() {
        val view = view ?: return
        val ic = currentInputConnection
        if (!predictionEnabled || ic == null) {
            candidates = emptyList()
            view.candidates = candidates
            return
        }
        val (current, previous) = wordsBeforeCursor(ic)
        candidates = if (current.isNotEmpty()) model.suggest(current, previous) else model.next(previous)
        view.candidates = candidates
    }

    /** (the word being typed, the word before it) from the editor text, composing text included. */
    private fun wordsBeforeCursor(ic: InputConnection): Pair<String, String?> {
        val before = ic.getTextBeforeCursor(64, 0)?.toString() ?: return "" to null
        var end = before.length
        var i = end
        while (i > 0 && !isSeparatorChar(before[i - 1])) i--
        val current = before.substring(i, end)
        end = i
        while (end > 0 && isSeparatorChar(before[end - 1])) end--
        i = end
        while (i > 0 && !isSeparatorChar(before[i - 1])) i--
        val previous = if (i < end) before.substring(i, end) else null
        return current to previous
    }

    private fun learnCurrentWord(ic: InputConnection) {
        if (!predictionEnabled) return
        val (current, previous) = wordsBeforeCursor(ic)
        if (current.isNotEmpty()) learn(current, previous)
    }

    private fun learn(word: String, previous: String?) {
        model.learn(word, previous)
        if (++learnsSinceSave >= SAVE_EVERY) saveModel()
    }

    private fun modelFile(): File = File(filesDir, MODEL_FILE)

    private fun loadModel() {
        val file = modelFile()
        if (!file.exists()) return
        try {
            model.load(file.readText())
        } catch (_: Exception) {
            // A corrupt model file is not worth crashing the keyboard over.
        }
    }

    private fun saveModel() {
        if (learnsSinceSave == 0) return
        learnsSinceSave = 0
        val text = model.save()
        val file = modelFile()
        executor.execute {
            try {
                file.writeText(text)
            } catch (_: Exception) {
            }
        }
    }

    private fun resetModel() {
        model = PredictionModel()
        learnsSinceSave = 0
        val file = modelFile()
        executor.execute { file.delete() }
        refreshCandidates()
    }

    private companion object {
        const val MODEL_FILE = "prediction.txt"
        const val SAVE_EVERY = 20

        fun isSeparatorChar(c: Char): Boolean = c.isWhitespace() || c in ".,!?;:\"()[]{}<>/\\~…·"

        fun isSeparator(text: String): Boolean = text.isNotEmpty() && text.all(::isSeparatorChar)
    }
}
