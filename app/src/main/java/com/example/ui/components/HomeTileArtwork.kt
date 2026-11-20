package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.navigation.NavRoutes
import com.example.ui.theme.LocalAqroColors

@Composable
fun AqroLauncherIconEmblem(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.26f))
            .testTag("header_app_icon"),
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = 1.28f, scaleY = 1.28f),
        )
    }
}

@Composable
fun AqroBrandLeafMark(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    tintOverride: Color? = null,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val primary = tintOverride ?: Color(0xFF156D34)
        val secondary = tintOverride?.copy(alpha = 0.85f) ?: Color(0xFF2FA852)
        val accent = tintOverride?.copy(alpha = 0.7f) ?: Color(0xFF56C568)

        // Left leaning leaf
        val leftLeaf = Path().apply {
            moveTo(w * 0.48f, h * 0.88f)
            cubicTo(w * 0.14f, h * 0.78f, w * 0.08f, h * 0.36f, w * 0.24f, h * 0.16f)
            cubicTo(w * 0.44f, h * 0.24f, w * 0.52f, h * 0.58f, w * 0.48f, h * 0.88f)
            close()
        }
        drawPath(leftLeaf, color = primary, style = Fill)

        // Tall center-right leaf
        val centerLeaf = Path().apply {
            moveTo(w * 0.46f, h * 0.90f)
            cubicTo(w * 0.38f, h * 0.52f, w * 0.46f, h * 0.16f, w * 0.68f, h * 0.08f)
            cubicTo(w * 0.82f, h * 0.32f, w * 0.74f, h * 0.68f, w * 0.46f, h * 0.90f)
            close()
        }
        drawPath(
            centerLeaf,
            brush = Brush.linearGradient(
                listOf(secondary, primary),
                start = Offset(w * 0.5f, h * 0.1f),
                end = Offset(w * 0.5f, h * 0.9f),
            ),
        )

        // Small bright right accent leaf
        val rightLeaf = Path().apply {
            moveTo(w * 0.50f, h * 0.88f)
            cubicTo(w * 0.68f, h * 0.82f, w * 0.92f, h * 0.62f, w * 0.90f, h * 0.38f)
            cubicTo(w * 0.70f, h * 0.44f, w * 0.56f, h * 0.64f, w * 0.50f, h * 0.88f)
            close()
        }
        drawPath(rightLeaf, color = accent, style = Fill)

        // Subtle midrib vein
        drawLine(
            color = Color.White.copy(alpha = 0.55f),
            start = Offset(w * 0.48f, h * 0.86f),
            end = Offset(w * 0.64f, h * 0.24f),
            strokeWidth = w * 0.035f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun BotanicalCornerBackdrop(modifier: Modifier = Modifier) {
    val isDark = LocalAqroColors.current.isDark
    val leafAlpha = if (isDark) 0.12f else 0.14f
    val primaryLeaf = Color(0xFF2D9650).copy(alpha = leafAlpha)
    val softLeaf = Color(0xFF66BB6A).copy(alpha = leafAlpha * 0.8f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Bottom-right subtle decorative leaves
        rotate(degrees = -22f, pivot = Offset(w * 0.94f, h * 0.92f)) {
            val brLeaf = Path().apply {
                moveTo(w * 0.96f, h * 0.98f)
                cubicTo(w * 0.78f, h * 0.90f, w * 0.78f, h * 0.74f, w * 0.94f, h * 0.68f)
                cubicTo(w * 1.04f, h * 0.78f, w * 1.02f, h * 0.92f, w * 0.96f, h * 0.98f)
                close()
            }
            drawPath(brLeaf, color = primaryLeaf)
        }

        // Bottom-left subtle decorative leaves
        rotate(degrees = 24f, pivot = Offset(w * 0.04f, h * 0.92f)) {
            val blLeaf = Path().apply {
                moveTo(w * 0.02f, h * 0.98f)
                cubicTo(w * 0.20f, h * 0.92f, w * 0.18f, h * 0.76f, w * 0.04f, h * 0.70f)
                cubicTo(-w * 0.06f, h * 0.78f, -w * 0.04f, h * 0.92f, w * 0.02f, h * 0.98f)
                close()
            }
            drawPath(blLeaf, color = softLeaf)
        }
    }
}

@Composable
fun ModuleIconMedallion(
    route: String,
    fallbackIcon: ImageVector,
    fallbackAccent: Color,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
) {
    val isDark = LocalAqroColors.current.isDark

    val bgTint: Color = when (route) {
        NavRoutes.CHAT -> if (isDark) Color(0xFF193729) else Color(0xFFE3F5EC)
        NavRoutes.PESTICIDE -> if (isDark) Color(0xFF1B3A24) else Color(0xFFE4F6E7)
        NavRoutes.NPK -> if (isDark) Color(0xFF173832) else Color(0xFFE0F4EE)
        NavRoutes.WEATHER -> if (isDark) Color(0xFF172D44) else Color(0xFFE3F2FD)
        NavRoutes.FIELDS -> if (isDark) Color(0xFF18392D) else Color(0xFFE1F5EA)
        NavRoutes.HARVEST -> if (isDark) Color(0xFF1B3B28) else Color(0xFFE6F6EA)
        NavRoutes.SUBSIDY -> if (isDark) Color(0xFF3A2E17) else Color(0xFFFEF3DC)
        NavRoutes.EQUIPMENT -> if (isDark) Color(0xFF203A1D) else Color(0xFFE9F7E2)
        NavRoutes.FINANCE -> if (isDark) Color(0xFF16353A) else Color(0xFFE0F2F4)
        NavRoutes.AGRO_CALENDAR -> if (isDark) Color(0xFF2E2142) else Color(0xFFF1E9FD)
        NavRoutes.INSURANCE -> if (isDark) Color(0xFF173145) else Color(0xFFE3F1FA)
        NavRoutes.STATIONS -> if (isDark) Color(0xFF173836) else Color(0xFFE2F5F2)
        NavRoutes.SEEDS -> if (isDark) Color(0xFF1B3B22) else Color(0xFFE5F6E4)
        NavRoutes.GALLERY -> if (isDark) Color(0xFF173242) else Color(0xFFE3F2FA)
        NavRoutes.LINKS -> if (isDark) Color(0xFF23284A) else Color(0xFFE9ECFC)
        NavRoutes.ADMIN -> if (isDark) Color(0xFF3C2B18) else Color(0xFFFEF0DD)
        else -> fallbackAccent.copy(alpha = if (isDark) 0.24f else 0.14f)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgTint),
    ) {
        when (route) {
            NavRoutes.CHAT -> {
                Icon(
                    imageVector = Icons.Filled.SmartToy,
                    contentDescription = null,
                    tint = Color(0xFF1B6E52),
                    modifier = Modifier.size(size * 0.52f).offset(x = (-2).dp, y = 1.dp),
                )
                Icon(
                    imageVector = Icons.Filled.Eco,
                    contentDescription = null,
                    tint = Color(0xFF2EA44F),
                    modifier = Modifier.size(size * 0.30f).offset(x = 9.dp, y = (-7).dp),
                )
            }

            NavRoutes.WEATHER -> {
                Icon(
                    imageVector = Icons.Filled.WbSunny,
                    contentDescription = null,
                    tint = Color(0xFFF5A623),
                    modifier = Modifier.size(size * 0.42f).offset(x = (-5).dp, y = (-5).dp),
                )
                Icon(
                    imageVector = Icons.Filled.Cloud,
                    contentDescription = null,
                    tint = Color(0xFF3FA2F6),
                    modifier = Modifier.size(size * 0.50f).offset(x = 4.dp, y = 4.dp),
                )
            }

            NavRoutes.FIELDS -> {
                Icon(
                    imageVector = Icons.Filled.Map,
                    contentDescription = null,
                    tint = Color(0xFF1B8A5A),
                    modifier = Modifier.size(size * 0.54f).offset(y = 3.dp),
                )
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = Color(0xFFE53935),
                    modifier = Modifier.size(size * 0.36f).offset(x = (-2).dp, y = (-7).dp),
                )
            }

            NavRoutes.PESTICIDE -> {
                Icon(
                    imageVector = Icons.Filled.Eco,
                    contentDescription = null,
                    tint = Color(0xFF208A42),
                    modifier = Modifier.size(size * 0.56f),
                )
            }

            NavRoutes.NPK -> {
                Icon(
                    imageVector = Icons.Filled.Calculate,
                    contentDescription = null,
                    tint = Color(0xFF136F48),
                    modifier = Modifier.size(size * 0.54f),
                )
            }

            NavRoutes.HARVEST -> {
                Icon(
                    imageVector = Icons.Filled.TrendingUp,
                    contentDescription = null,
                    tint = Color(0xFF198744),
                    modifier = Modifier.size(size * 0.56f),
                )
            }

            NavRoutes.SUBSIDY -> {
                Icon(
                    imageVector = Icons.Filled.MonetizationOn,
                    contentDescription = null,
                    tint = Color(0xFFD99B16),
                    modifier = Modifier.size(size * 0.56f),
                )
            }

            NavRoutes.EQUIPMENT -> {
                Icon(
                    imageVector = Icons.Filled.Agriculture,
                    contentDescription = null,
                    tint = Color(0xFF1E8E3E),
                    modifier = Modifier.size(size * 0.58f),
                )
            }

            NavRoutes.FINANCE -> {
                Icon(
                    imageVector = Icons.Filled.Description,
                    contentDescription = null,
                    tint = Color(0xFF0E7478),
                    modifier = Modifier.size(size * 0.54f),
                )
            }

            NavRoutes.AGRO_CALENDAR -> {
                Icon(
                    imageVector = Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = Color(0xFF7E22CE),
                    modifier = Modifier.size(size * 0.52f),
                )
            }

            NavRoutes.INSURANCE -> {
                Icon(
                    imageVector = Icons.Filled.VerifiedUser,
                    contentDescription = null,
                    tint = Color(0xFF0369A1),
                    modifier = Modifier.size(size * 0.52f),
                )
            }

            NavRoutes.STATIONS -> {
                Icon(
                    imageVector = Icons.Filled.Storefront,
                    contentDescription = null,
                    tint = Color(0xFF0D9488),
                    modifier = Modifier.size(size * 0.52f),
                )
            }

            NavRoutes.SEEDS -> {
                Icon(
                    imageVector = Icons.Filled.LocalFlorist,
                    contentDescription = null,
                    tint = Color(0xFF15803D),
                    modifier = Modifier.size(size * 0.54f),
                )
            }

            NavRoutes.GALLERY -> {
                Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = null,
                    tint = Color(0xFF0284C7),
                    modifier = Modifier.size(size * 0.52f),
                )
            }

            NavRoutes.LINKS -> {
                Icon(
                    imageVector = Icons.Filled.Link,
                    contentDescription = null,
                    tint = Color(0xFF4F46E5),
                    modifier = Modifier.size(size * 0.52f),
                )
            }

            NavRoutes.ADMIN -> {
                Icon(
                    imageVector = Icons.Filled.AdminPanelSettings,
                    contentDescription = null,
                    tint = Color(0xFFB45309),
                    modifier = Modifier.size(size * 0.52f),
                )
            }

            else -> {
                Icon(
                    imageVector = fallbackIcon,
                    contentDescription = null,
                    tint = fallbackAccent,
                    modifier = Modifier.size(size * 0.52f),
                )
            }
        }
    }
}
