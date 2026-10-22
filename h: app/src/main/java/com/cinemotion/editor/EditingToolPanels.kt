
package com.cinemotion.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CineBlack = Color(0xFF101014)
private val CinePanel = Color(0xFF1B1B23)
private val CineCyan = Color(0xFF00F5E9)
private val CineWhite = Color(0xFFF4F4F7)
private val CineMuted = Color(0xFFAAAAB8)

enum class CineTool(val title: String, val symbol: String) {
    EDIT("Edit", "✂"),
    EFFECTS("Effects", "✦"),
    TRANSITIONS("Transitions", "⇄"),
    ANIMATION("Animation", "▶"),
    FILTERS("Filters", "◐"),
    TEXT("Text", "T"),
    AUDIO("Audio", "♫"),
    EXPORT("Export", "↑")
}

data class CineToolSelection(
    val tool: CineTool,
    val option: String,
    val amount: Float = 1f
)

@Composable
fun EditingToolPanels(
    modifier: Modifier = Modifier,
    onSelectionChanged: (CineToolSelection) -> Unit = {},
    onExport: () -> Unit = {}
) {
    var activeTool by remember { mutableStateOf(CineTool.EDIT) }
    var selectedOptions by remember {
        mutableStateOf(
            mapOf(
                CineTool.EFFECTS to "None",
                CineTool.TRANSITIONS to "None",
                CineTool.ANIMATION to "None",
                CineTool.FILTERS to "Original",
                CineTool.TEXT to "Add text",
                CineTool.AUDIO to "Add music",
                CineTool.EDIT to "Trim"
            )
        )
    }
    var intensity by remember { mutableFloatStateOf(1f) }

    val options = when (activeTool) {
        CineTool.EDIT -> listOf(
            "Trim", "Split", "Speed", "Volume",
            "Rotate", "Crop", "Duplicate"
        )

        CineTool.EFFECTS -> listOf(
            "None", "Glow", "Blur", "Vignette",
            "Glitch", "Chromatic", "Shake", "Grain"
        )

        CineTool.TRANSITIONS -> listOf(
            "None", "Fade", "Dissolve", "Zoom",
            "Slide", "Whip Pan", "Wipe"
        )

        CineTool.ANIMATION -> listOf(
            "None", "Zoom In", "Zoom Out",
            "Pan Left", "Pan Right", "Bounce", "Pulse"
        )

        CineTool.FILTERS -> listOf(
            "Original", "Warm", "Cool", "Vintage",
            "Cinematic", "B&W", "Vivid", "Fade"
        )

        CineTool.TEXT -> listOf(
            "Add text", "Title", "Subtitle",
            "Caption", "Sticker", "Emoji"
        )

        CineTool.AUDIO -> listOf(
            "Add music", "Voiceover", "Fade In",
            "Fade Out", "Volume", "Extract Audio"
        )

        CineTool.EXPORT -> listOf(
            "720p", "1080p", "1440p", "30 FPS", "60 FPS"
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CineBlack)
            .padding(bottom = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CineTool.entries.forEach { tool ->
                val selected = activeTool == tool

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            activeTool = tool
                            intensity = 1f
                        }
                        .background(
                            if (selected) CinePanel else Color.Transparent,
                            RoundedCornerShape(12.dp)
                        )
                        .then(
                            if (selected) {
                                Modifier.border(
                                    1.dp,
                                    CineCyan.copy(alpha = 0.75f),
                                    RoundedCornerShape(12.dp)
                                )
                            } else {
                                Modifier
                            }
                        )
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = tool.symbol,
                        color = if (selected) CineCyan else CineWhite,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(3.dp))

                    Text(
                        text = tool.title,
                        color = if (selected) CineCyan else CineMuted,
                        fontSize = 9.sp,
                        maxLines = 1
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = activeTool.title,
                color = CineWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = "Swipe for more",
                color = CineMuted,
                fontSize = 10.sp
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            options.forEach { option ->
                val selected = selectedOptions[activeTool] == option

                Column(
                    modifier = Modifier
                        .background(
                            if (selected) CineCyan.copy(alpha = 0.13f)
                            else CinePanel,
                            RoundedCornerShape(12.dp)
                        )
                        .then(
                            if (selected) {
                                Modifier.border(
                                    1.dp,
                                    CineCyan,
                                    RoundedCornerShape(12.dp)
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clickable {
                            selectedOptions =
                                selectedOptions + (activeTool to option)

                            val selection = CineToolSelection(
                                tool = activeTool,
                                option = option,
                                amount = intensity
                            )

                            onSelectionChanged(selection)

                            if (activeTool == CineTool.EXPORT) {
                                onExport()
                            }
                        }
                        .padding(
                            horizontal = 15.dp,
                            vertical = 12.dp
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = option,
                        color = if (selected) CineCyan else CineWhite,
                        fontSize = 12.sp,
                        fontWeight = if (selected) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        }
                    )
                }
            }
        }

        if (
            activeTool == CineTool.EFFECTS ||
            activeTool == CineTool.FILTERS ||
            activeTool == CineTool.ANIMATION
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Intensity",
                    color = CineMuted,
                    fontSize = 12.sp
                )

                Slider(
                    value = intensity,
                    onValueChange = {
                        intensity = it
                        onSelectionChanged(
                            CineToolSelection(
                                tool = activeTool,
                                option = selectedOptions[activeTool] ?: "None",
                                amount = it
                            )
                        )
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp)
                )

                Text(
                    text = "${(intensity * 100).toInt()}%",
                    color = CineCyan,
                    fontSize = 11.sp
                )
            }
        }

        Text(
            text = "Selected: ${
                selectedOptions[activeTool] ?: "Choose an option"
            }",
            color = CineMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = 6.dp
            )
        )
    }
}
