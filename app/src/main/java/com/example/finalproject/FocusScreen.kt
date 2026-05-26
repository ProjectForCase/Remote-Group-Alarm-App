package com.example.finalproject

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FocusTabContent(viewModel: FocusViewModel) {
    val options = listOf(
        FocusCategory(R.string.cat_work, "工作", Icons.Default.Assignment),
        FocusCategory(R.string.cat_read, "閱讀", Icons.Default.MenuBook),
        FocusCategory(R.string.cat_meditation, "冥想", Icons.Default.SelfImprovement),
        FocusCategory(R.string.cat_study, "學習", Icons.Default.School),
        FocusCategory(R.string.cat_exercise, "運動", Icons.Default.FitnessCenter),
        FocusCategory(R.string.cat_rest, "休息", Icons.Default.Bedtime)
    )
    val mainColor = Color(0xFF673AB7) // 紫色基調

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            options.forEach { category ->
                val isSelected = viewModel.selectedType == category.internalName
                val isInteractionDisabled = viewModel.isRunning || viewModel.timerSeconds > 0
                
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(enabled = !isInteractionDisabled) {
                            viewModel.onTypeChange(category.internalName)
                        },
                    color = if (isSelected) mainColor else Color.Transparent,
                    border = if (isSelected) null else BorderStroke(1.dp, Color.LightGray),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = null,
                            tint = if (isSelected) Color.White else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(category.titleRes),
                            color = if (isSelected) Color.White else Color.Gray,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(300.dp)
        ) {
            Canvas(modifier = Modifier.size(280.dp)) {
                drawCircle(
                    color = mainColor.copy(alpha = 0.1f),
                    style = Stroke(width = 8.dp.toPx())
                )
            }
            
            Canvas(modifier = Modifier.size(280.dp)) {
                drawArc(
                    color = mainColor,
                    startAngle = -90f,
                    sweepAngle = 360f * (viewModel.timerSeconds % 60 / 60f), 
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            Text(
                text = viewModel.formatTime(viewModel.timerSeconds),
                fontSize = 80.sp,
                fontWeight = FontWeight.W200,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        if (viewModel.isRunning) {
            Button(
                onClick = { viewModel.toggleTimer() },
                modifier = Modifier
                    .width(120.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f))
            ) {
                Text("STOP", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        } else if (viewModel.timerSeconds > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = { viewModel.startTimer() },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.height(56.dp)
                ) {
                    Text("CONTINUE")
                }
                Button(
                    onClick = { viewModel.completeTimer() },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                ) {
                    Text("FINISH")
                }
            }
        } else {
            Button(
                onClick = { viewModel.toggleTimer() },
                modifier = Modifier
                    .width(150.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = mainColor.copy(alpha = 0.2f), contentColor = mainColor)
            ) {
                Text("START", fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
    }
}

data class FocusCategory(
    val titleRes: Int,
    val internalName: String,
    val icon: ImageVector
)
