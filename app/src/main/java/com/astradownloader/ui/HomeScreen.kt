package com.astradownloader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astradownloader.AstraApp

/** 首页：链接解析入口。 */
@Composable
fun HomeScreen(
    app: AstraApp,
    onParseLink: (String) -> Boolean,
    onReadClipboard: () -> String?,
    modifier: Modifier = Modifier
) {
    var linkText by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf("") }

    fun parseInput() {
        val text = linkText.trim()
        if (text.isBlank()) {
            inputError = "请输入网盘分享链接或直链"
            return
        }
        inputError = if (onParseLink(text)) "" else "未识别到有效链接"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Miuix.Background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("星流下载器", fontSize = 24.sp, color = Miuix.OnSurface)
        Text("解析链接", fontSize = 17.sp, color = Miuix.OnSurface)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Miuix.Surface, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = linkText,
                onValueChange = {
                    linkText = it
                    inputError = ""
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("粘贴网盘分享链接或直链", color = Miuix.TextSecondary) },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { parseInput() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Miuix.BrandBlue,
                    unfocusedBorderColor = Miuix.Divider,
                    focusedTextColor = Miuix.OnSurface,
                    unfocusedTextColor = Miuix.OnSurface,
                    cursorColor = Miuix.BrandBlue
                )
            )
            if (inputError.isNotBlank()) {
                Text(inputError, fontSize = 12.sp, color = Miuix.Warning)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val clipText = onReadClipboard()?.trim().orEmpty()
                        if (clipText.isBlank()) {
                            inputError = "剪贴板没有文本内容"
                        } else {
                            linkText = clipText
                            inputError = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Miuix.Background,
                        contentColor = Miuix.BrandBlue
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("粘贴剪贴板", fontSize = 14.sp)
                }
                Button(
                    onClick = { parseInput() },
                    enabled = linkText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Miuix.BrandBlue,
                        disabledContainerColor = Miuix.BrandBlue.copy(alpha = 0.35f)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("解析链接", fontSize = 14.sp, color = Color.White)
                }
            }
        }
    }
}
