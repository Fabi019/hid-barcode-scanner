package dev.fabik.bluetoothhid.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.rememberJsEngineService
import dev.fabik.bluetoothhid.utils.rememberPreference
import kotlinx.coroutines.launch

@Composable
fun JavaScriptEditorDialog(jsDialog: DialogState) {
    val context = LocalContext.current
    val jsEngine = rememberJsEngineService(context)
    val scope = rememberCoroutineScope()

    var codePreference by rememberPreference(PreferenceStore.JS_CODE)

    val outString = stringResource(R.string.press_run_to_evaluate)
    var outputText by remember { mutableStateOf(outString) }

    var codeText by remember { mutableStateOf(codePreference) }

    ConfirmDialog(
        dialogState = jsDialog,
        title = stringResource(R.string.custom_javascript),
        onDismiss = {
            close()
            outputText = outString
        }, onConfirm = {
            close()
            codePreference = codeText
            outputText = outString
        }
    ) {
        JavaScriptEditor(
            initialCode = codePreference,
            onRunClicked = { code, value, type ->
                scope.launch {
                    outputText = ""
                    jsEngine?.evaluateTemplate(code, value, type) { message ->
                        outputText += message + "\n"
                    } ?: run {
                        outputText += "JSEngine not initialized or unsupported! Make sure that you have enabled the feature."
                    }
                }
            },
            onEdit = { codeText = it },
            outputText = outputText,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JavaScriptEditor(
    initialCode: String,
    onRunClicked: (String, String, String) -> Unit,
    onEdit: (String) -> Unit,
    outputText: String
) {
    var codeText by remember { mutableStateOf(TextFieldValue(initialCode)) }
    var valueText by remember { mutableStateOf(TextFieldValue("")) }
    var typeText by remember { mutableStateOf("") }

    val output by rememberUpdatedState(newValue = outputText)

    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.editor_desc))

        Text(
            stringResource(R.string.editor),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp)
        )

        // JavaScript code editor
        TextField(
            value = codeText,
            onValueChange = { codeText = it; onEdit(it.text) },
            textStyle = TextStyle(),
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(bottom = 16.dp)
        )

        Text(stringResource(R.string.debug), style = MaterialTheme.typography.titleMedium)

        // Input fields
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextField(
                value = valueText,
                onValueChange = { valueText = it },
                modifier = Modifier
                    .weight(0.5f)
                    .padding(end = 8.dp),
                placeholder = { Text(stringResource(R.string.code)) }
            )

            val options = stringArrayResource(R.array.code_types_values).toList()
            var exp by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = exp,
                onExpandedChange = { exp = !exp },
                modifier = Modifier.weight(0.5f)
            ) {
                TextField(
                    value = typeText,
                    onValueChange = { typeText = it },
                    label = { Text(stringResource(R.string.format)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = exp)
                    },
                    colors = ExposedDropdownMenuDefaults.textFieldColors(),
                    modifier = Modifier.menuAnchor()
                )
                // filter options based on text field value (i.e. crude autocomplete)
                val filterOpts = options.filter { it.contains(typeText, ignoreCase = true) }
                if (filterOpts.isNotEmpty()) {
                    ExposedDropdownMenu(expanded = exp, onDismissRequest = { exp = false }) {
                        filterOpts.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(text = option) },
                                onClick = {
                                    typeText = option
                                    exp = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Run button
        Button(
            onClick = { onRunClicked(codeText.text, valueText.text, typeText) },
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 8.dp)
        ) {
            Text(stringResource(R.string.run))
        }

        Text(stringResource(R.string.output), style = MaterialTheme.typography.titleMedium)

        // Output text
        Card(Modifier.fillMaxWidth()) {
            Text(output)
        }
    }
}

@Preview
@Composable
fun PreviewJavaScriptEditor() {
    Surface {
        JavaScriptEditor(
            initialCode = "<code>",
            onRunClicked = { _, _, _ -> },
            onEdit = {},
            outputText = "Output will appear here"
        )
    }
}