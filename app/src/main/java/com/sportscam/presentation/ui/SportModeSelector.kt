package com.sportscam.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sportscam.data.models.SportMode

@Composable
fun SportModeSelector(
    currentMode: SportMode,
    onModeSelected: (SportMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(8.dp)
            .clickable { expanded = true }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = currentMode.displayName,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            SportMode.values().forEach { mode ->
                DropdownMenuItem(
                    text = { Text(text = mode.displayName) },
                    onClick = {
                        onModeSelected(mode)
                        expanded = false
                    }
                )
            }
        }
    }
}
