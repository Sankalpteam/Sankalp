package com.indoone.authentication

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indoone.R

private val IndoonePurple = Color(0xFF6330DB)
private val IndoonePurpleDark = Color(0xFF5E2DD2)
private val IndoonePurpleLight = Color(0xFF9147ED)
private val IndooneText = Color(0xFF17151D)
private val IndooneMuted = Color(0xFF77717F)
private val IndooneBorder = Color(0xFFE3DFE8)
private val IndooneField = Color(0xFFFBFAFC)
private val IndooneEyebrow = Color(0xFF7650D8)

@Composable
fun AuthPage(
    showCard: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFECE7DE),
                        Color(0xFFE2DCD2),
                        Color(0xFFD9D1C6),
                    ),
                )
            ),
    ) {
        val wideLayout = maxWidth >= 700.dp
        val horizontalPadding = if (wideLayout) 34.dp else 18.dp
        val verticalPadding = if (wideLayout) 28.dp else 24.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showCard) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 460.dp)
                        .shadow(
                            elevation = 14.dp,
                            shape = RoundedCornerShape(18.dp),
                            clip = false,
                        )
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFFFFDF7),
                                    Color(0xFFF7F0E3),
                                ),
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = Color(0xFFD8CBB8),
                            shape = RoundedCornerShape(18.dp),
                        )
                        .padding(horizontal = 22.dp, vertical = 24.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        content = content,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 460.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
fun AuthBrand() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.sankalp_logo),
            contentDescription = "Sankalp logo",
            modifier = Modifier
                .width(72.dp)
                .height(72.dp),
        )
        Column {
            Text(
                text = "Sankalp",
                color = IndoonePurpleDark,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 19.sp,
            )

        }
    }
}

@Composable
fun AuthHeading(eyebrow: String, title: String, description: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            eyebrow,
            color = IndooneEyebrow,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.3.sp,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            title,
            color = IndooneText,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp,
            lineHeight = 40.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            description,
            color = IndooneMuted,
            fontSize = 14.sp,
            lineHeight = 22.sp,
        )
    }
}

@Composable
fun AuthFieldLabel(text: String) {
    Text(
        text,
        color = Color(0xFF7650D8),
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.4.sp,
        modifier = Modifier.padding(bottom = 7.dp),
    )
}

@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingContent: (@Composable (() -> Unit))? = null,
    trailingContent: (@Composable (() -> Unit))? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, fontSize = 13.sp, color = Color(0xFF8A8490)) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        leadingIcon = leadingContent,
        trailingIcon = trailingContent,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = IndooneField,
            focusedContainerColor = Color.White,
            disabledContainerColor = IndooneField,
            unfocusedBorderColor = IndooneBorder,
            focusedBorderColor = Color(0xFF8652E7),
            disabledBorderColor = IndooneBorder,
        ),
    )
}

@Composable
fun AuthPrimaryButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    fillMaxWidth: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
            .height(48.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(
                Brush.horizontalGradient(listOf(IndoonePurple, IndoonePurpleLight))
            ),
        shape = RoundedCornerShape(13.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContentColor = Color.White,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
fun AuthSecondaryButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    textColor: Color = Color(0xFF5F5966),
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, IndooneBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = textColor,
            disabledContainerColor = Color.White,
            disabledContentColor = textColor,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun AuthStatus(text: String, error: Boolean) {
    if (text.isBlank()) return
    Text(
        text,
        color = if (error) MaterialTheme.colorScheme.error else IndooneMuted,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
}
