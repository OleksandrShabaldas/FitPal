package com.fitpal.app.ui.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.InterFamily
import com.fitpal.app.ui.theme.glassSoft
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The app's one number input: a big Inter number you can type, between round −/+ buttons that step
 * once on tap and keep stepping while held. Used by the weigh-in, and by every tracker's sheet (a water
 * amount, a goal…). [text] is what's typed; the caller keeps it, so a half-typed "7" isn't clobbered.
 *
 * [decimals] = 0 accepts digits only; 1+ also accepts "." / "," and formats with a dot. A step starts
 * from [fallback] when nothing valid is typed yet.
 */
@Composable
fun GlassNumberField(
    text: String,
    onText: (String) -> Unit,
    step: Float,
    min: Float,
    max: Float,
    modifier: Modifier = Modifier,
    decimals: Int = 0,
    fallback: Float = min,
    maxChars: Int = 6,
    fontSize: TextUnit = 52.sp,
    fieldWidth: Dp = 150.dp,
    onDone: (() -> Unit)? = null
) {
    fun parse(t: String): Float? = t.replace(',', '.').toFloatOrNull()
    fun format(v: Float): String = if (decimals == 0) v.roundToInt().toString()
        else String.format(Locale.US, "%.${decimals}f", v)
    fun stepBy(delta: Float) {
        // Reads the live text each step (not a value captured when the button was first drawn), so
        // holding the button keeps counting.
        val current = parse(text) ?: fallback
        onText(format((current + delta).coerceIn(min, max)))
    }
    val style = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = fontSize,
        fontFeatureSettings = "tnum",
        color = Cream,
        textAlign = TextAlign.Center
    )
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        StepButton(Icons.Default.Remove, "Less") { stepBy(-step) }
        Spacer(Modifier.width(14.dp))
        BasicTextField(
            value = text,
            onValueChange = { new ->
                onText(new.filter { it.isDigit() || (decimals > 0 && (it == '.' || it == ',')) }.take(maxChars))
            },
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(GoldLight),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (decimals > 0) KeyboardType.Decimal else KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.Center) {
                    if (text.isEmpty()) Text("—", style = style.copy(color = CreamFaint))
                    inner()
                }
            },
            modifier = Modifier.width(fieldWidth)
        )
        Spacer(Modifier.width(14.dp))
        StepButton(Icons.Default.Add, "More") { stepBy(step) }
    }
}

/** A round −/+ button that steps once on tap and keeps stepping while held. */
@Composable
private fun StepButton(icon: ImageVector, label: String, onStep: () -> Unit) {
    val scope = rememberCoroutineScope()
    val step by rememberUpdatedState(onStep)
    Box(
        modifier = Modifier
            .size(46.dp)
            .glassSoft(CircleShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    step()
                    val repeat: Job = scope.launch {
                        delay(420)
                        while (true) {
                            step()
                            delay(70)
                        }
                    }
                    waitForUpOrCancellation()
                    repeat.cancel()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = Cream, modifier = Modifier.size(22.dp))
    }
}
