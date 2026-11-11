package com.teo.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.teo.core.model.PatternCodec
import com.teo.core.model.SecretType
import kotlin.math.hypot

/**
 * Entry for a protection code of any [SecretType]. For PIN and PASSWORD this is a text field and
 * [onSubmit] fires from the keyboard's Done key (screens also have their own button). For
 * PATTERN, [onValueChange] and then [onSubmit] fire as soon as the finger lifts — there is no
 * separate confirm step for a drawn pattern, same as the system lock screen.
 */
@Composable
fun SecretInput(
    type: SecretType,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    onSubmit: () -> Unit = {}
) {
    when (type) {
        SecretType.PIN -> OutlinedTextField(
            value = value,
            onValueChange = { new ->
                if (new.length <= SecretType.MAX_PIN_LENGTH && new.all(Char::isDigit)) onValueChange(new)
            },
            label = { Text(label) },
            singleLine = true,
            isError = isError,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            modifier = modifier.fillMaxWidth()
        )
        SecretType.PASSWORD -> {
            var visible by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = value,
                onValueChange = { new -> if (new.length <= MAX_PASSWORD_LENGTH) onValueChange(new) },
                label = { Text(label) },
                singleLine = true,
                isError = isError,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                trailingIcon = {
                    TextButton(onClick = { visible = !visible }) { Text(if (visible) "Скрыть" else "Показать") }
                },
                modifier = modifier.fillMaxWidth()
            )
        }
        SecretType.PATTERN -> PatternLock(
            isError = isError,
            onPatternComplete = { dots ->
                onValueChange(PatternCodec.encode(dots))
                onSubmit()
            },
            modifier = modifier
        )
    }
}

/** Row of choices for which kind of code to set up. */
@Composable
fun SecretTypeSelector(selected: SecretType, onSelect: (SecretType) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecretType.entries.forEach { type ->
            FilterChip(
                selected = type == selected,
                onClick = { onSelect(type) },
                label = { Text(type.displayName()) }
            )
        }
    }
}

fun SecretType.displayName(): String = when (this) {
    SecretType.PIN -> "PIN-код"
    SecretType.PASSWORD -> "Пароль"
    SecretType.PATTERN -> "Ключ"
}

/**
 * 3×3 pattern grid like the system lock screen: drag across dots to join them; a dot skipped over
 * in a straight line (e.g. 0→2 passes 1) is joined automatically, as Android does. Reports the
 * joined dots on release; a single accidental dot is ignored.
 */
@Composable
fun PatternLock(
    onPatternComplete: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    val selected = remember { mutableStateListOf<Int>() }
    var finger by remember { mutableStateOf<Offset?>(null) }
    val dotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val activeColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Box(modifier = modifier.fillMaxWidth(0.8f).aspectRatio(1f)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .pointerInput(Unit) {
                    val cell = size.width / 3f
                    fun center(i: Int) = Offset(cell * (i % 3) + cell / 2, cell * (i / 3) + cell / 2)
                    fun hit(p: Offset): Int? = (0..8).firstOrNull { i ->
                        val c = center(i)
                        hypot(p.x - c.x, p.y - c.y) < cell * HIT_RADIUS_FRACTION
                    }
                    fun join(i: Int) {
                        if (i in selected) return
                        val last = selected.lastOrNull()
                        if (last != null) {
                            // Midpoint of two dots in a straight line (row, column or diagonal).
                            val (r1, c1) = last / 3 to last % 3
                            val (r2, c2) = i / 3 to i % 3
                            if ((r1 + r2) % 2 == 0 && (c1 + c2) % 2 == 0) {
                                val mid = ((r1 + r2) / 2) * 3 + (c1 + c2) / 2
                                if (mid !in selected) selected.add(mid)
                            }
                        }
                        selected.add(i)
                    }
                    detectDragGestures(
                        onDragStart = { p ->
                            selected.clear()
                            finger = p
                            hit(p)?.let(::join)
                        },
                        onDrag = { change, _ ->
                            finger = change.position
                            hit(change.position)?.let(::join)
                        },
                        onDragEnd = {
                            finger = null
                            val dots = selected.toList()
                            if (dots.size > 1) onPatternComplete(dots)
                            selected.clear()
                        },
                        onDragCancel = {
                            finger = null
                            selected.clear()
                        }
                    )
                }
        ) {
            val cell = size.width / 3f
            fun center(i: Int) = Offset(cell * (i % 3) + cell / 2, cell * (i / 3) + cell / 2)
            val stroke = 6.dp.toPx()
            selected.zipWithNext().forEach { (a, b) ->
                drawLine(activeColor, center(a), center(b), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            val lastDot = selected.lastOrNull()
            val f = finger
            if (lastDot != null && f != null) {
                drawLine(activeColor.copy(alpha = 0.5f), center(lastDot), f, strokeWidth = stroke, cap = StrokeCap.Round)
            }
            for (i in 0..8) {
                val active = i in selected
                drawCircle(
                    color = if (active) activeColor else dotColor,
                    radius = if (active) 12.dp.toPx() else 8.dp.toPx(),
                    center = center(i)
                )
            }
        }
    }
}

private const val MAX_PASSWORD_LENGTH = 64
private const val HIT_RADIUS_FRACTION = 0.3f
