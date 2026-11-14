@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.ramez.shasha.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage

object C {
    val bg = Color(0xFF0B0B0D)
    val surface = Color(0xFF151518)
    val surface2 = Color(0xFF232327)
    val text = Color(0xFFF5F5F7)
    val text2 = Color(0xFFC4C4CC)
    val muted = Color(0xFFA1A1AA)
    val accent = Color(0xFF7DD3FC)
    val placeholder = Color(0xFF1B2430)
    val onLight = Color(0xFF0B0B0D)
}

@Composable
fun ShashaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.text, onPrimary = C.onLight, background = C.bg, surface = C.surface,
            onSurface = C.text, onBackground = C.text,
        ),
    ) {
        CompositionLocalProvider(LocalContentColor provides C.text) { content() }
    }
}

@Composable
fun T(
    text: String,
    size: TextUnit = 20.sp,
    weight: FontWeight = FontWeight.Normal,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    Text(
        text = text, fontSize = size, fontWeight = weight, color = color, modifier = modifier,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis, lineHeight = lineHeight,
    )
}

/** Safe focus request: ignores the crash when the target isn't laid out yet. */
fun FocusRequester.tryFocus() { try { requestFocus() } catch (_: Exception) { } }

private val focusBorder = Border(BorderStroke(3.dp, C.text))

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) C.surface2 else Color.Transparent,
            focusedContainerColor = C.text,
            contentColor = if (selected) C.text else C.muted,
            focusedContentColor = C.onLight,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Row(Modifier.padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = text, modifier = Modifier.size(22.dp))
            } else {
                Text(text, fontSize = 18.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
            }
        }
    }
}

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) C.text else C.surface2,
            contentColor = if (primary) C.onLight else C.text,
            focusedContainerColor = C.text,
            focusedContentColor = C.onLight,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, C.text), inset = (-5).dp)),
    ) {
        Row(
            Modifier.padding(horizontal = 30.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Poster or thumbnail tile with the white focus ring. */
@Composable
fun MediaCard(
    image: String,
    title: String,
    onClick: () -> Unit,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    showTitle: Boolean = true,
    subtitle: String = "",
    onFocused: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
) {
    Column(modifier.width(width), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(width, height)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .then(if (onFocused != null) Modifier.onFocusGained(onFocused) else Modifier),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = C.placeholder, focusedContainerColor = C.placeholder),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.07f),
            border = ClickableSurfaceDefaults.border(focusedBorder = focusBorder),
        ) {
            Box(Modifier.fillMaxSize()) {
                if (image.isNotBlank()) {
                    AsyncImage(model = image, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    T(title, size = 16.sp, weight = FontWeight.SemiBold, color = C.text2, maxLines = 4,
                        modifier = Modifier.align(Alignment.Center).padding(14.dp))
                }
                if (progress != null) {
                    Box(
                        Modifier.align(Alignment.BottomCenter).padding(14.dp).fillMaxWidth().height(5.dp)
                            .background(Color(0x99000000), RoundedCornerShape(3.dp))
                    ) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(C.accent, RoundedCornerShape(3.dp)))
                    }
                }
            }
        }
        if (showTitle) {
            T(title, size = 16.sp, weight = FontWeight.Medium, maxLines = 1)
            if (subtitle.isNotEmpty()) T(subtitle, size = 14.sp, color = C.muted, maxLines = 1)
        }
    }
}

fun Modifier.onFocusGained(block: () -> Unit): Modifier =
    this.onFocusChanged { if (it.hasFocus) block() }

/** A remote-friendly text box: press OK to type. */
@Composable
fun InputField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    onDone: () -> Unit = {},
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    // The text box only takes focus while typing, so the remote moves between
    // fields (and on to buttons) in one press instead of stopping twice.
    var editing by remember { mutableStateOf(false) }
    val inner = remember { FocusRequester() }
    LaunchedEffect(editing) {
        if (editing) { inner.tryFocus(); keyboardController?.show() }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        T(label, size = 16.sp, weight = FontWeight.Medium, color = C.muted)
        Surface(
            onClick = { editing = true },
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = C.surface2, focusedContainerColor = C.surface2),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(2.dp, if (focused) C.text else Color.Transparent)),
                focusedBorder = focusBorder,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = C.text, fontSize = 20.sp),
                cursorBrush = SolidColor(C.text),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { keyboardController?.hide(); editing = false; focusRequester.tryFocus(); onDone() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
                    .focusRequester(inner)
                    .focusProperties { canFocus = editing }
                    .onFocusChanged { focused = it.isFocused; if (!it.isFocused && editing) editing = false },
            )
        }
    }
}
