package app.notch.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import app.notch.R

/**
 * A hallway at home: a cream wall, a painted door frame of honey wood with pencil marks on it, and
 * the crayons each child has their own colour of.
 */
@Immutable
data class Palette(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val line: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val pencil: Color,
    val accent: Color,
    val accentSoft: Color,
    val wood: Color,
    val woodLight: Color,
    val woodDark: Color,
    val woodGrain: Color,
    val paper: Color,
    val night: Color,
    val danger: Color,
)

val HallPalette = Palette(
    bg = Color(0xFFF5EFE4),
    surface = Color(0xFFFCF8F1),
    surface2 = Color(0xFFEDE4D4),
    line = Color(0xFFE0D4BF),
    ink = Color(0xFF332A24),
    muted = Color(0xFF75685C),
    faint = Color(0xFFA99B8D),
    pencil = Color(0xFF45444C),
    accent = Color(0xFFD2553A),
    accentSoft = Color(0xFFF6DCD2),
    wood = Color(0xFFD6B284),
    woodLight = Color(0xFFE8CFA9),
    woodDark = Color(0xFF9C7347),
    woodGrain = Color(0xFFB98F5E),
    paper = Color(0xFFFFFDF8),
    night = Color(0xFF1E1A17),
    danger = Color(0xFFB8432E),
)

/** One crayon per child. */
val Crayons = listOf(
    Color(0xFFD9573B), Color(0xFF3E7FC1), Color(0xFF5B9548), Color(0xFFDB9B22), Color(0xFF8A5CA8), Color(0xFF2E958D),
)

fun crayon(i: Int): Color = Crayons[((i % Crayons.size) + Crayons.size) % Crayons.size]

val LocalPalette = staticCompositionLocalOf { HallPalette }

val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
)

/** A bookish serif for names and titles. */
val Lora = FontFamily(
    Font(R.font.lora_regular, FontWeight.Normal),
    Font(R.font.lora_italic, FontWeight.Normal, FontStyle.Italic),
)

/** Pencil handwriting: dates on the door frame, words the child said. */
val Hand = FontFamily(Font(R.font.caveat, FontWeight.Normal))

object U {
    val c: Palette @Composable get() = LocalPalette.current

    val RXl = RoundedCornerShape(26.dp)
    val RLg = RoundedCornerShape(20.dp)
    val RMd = RoundedCornerShape(14.dp)
    val RSm = RoundedCornerShape(10.dp)
}

object UType {
    val Display: TextStyle @Composable get() = TextStyle(fontFamily = Lora, fontSize = 30.sp, lineHeight = 36.sp, color = U.c.ink)
    val Title: TextStyle @Composable get() = TextStyle(fontFamily = Lora, fontSize = 23.sp, lineHeight = 29.sp, color = U.c.ink)
    val Name: TextStyle @Composable get() = TextStyle(fontFamily = Lora, fontSize = 18.sp, lineHeight = 23.sp, color = U.c.ink)
    val Sub: TextStyle @Composable get() = TextStyle(fontFamily = Nunito, fontSize = 14.5.sp, lineHeight = 21.sp, color = U.c.muted)
    val Eyebrow: TextStyle @Composable get() = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, letterSpacing = 0.12.em, color = U.c.muted)
    val Strong: TextStyle @Composable get() = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = U.c.ink)
    val Body: TextStyle @Composable get() = TextStyle(fontFamily = Nunito, fontSize = 15.sp, lineHeight = 22.sp, color = U.c.ink)
    val Muted: TextStyle @Composable get() = TextStyle(fontFamily = Nunito, fontSize = 13.sp, lineHeight = 18.sp, color = U.c.muted)
    val Small: TextStyle @Composable get() = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = U.c.faint)
    val Hand: TextStyle @Composable get() = TextStyle(fontFamily = app.notch.ui.theme.Hand, fontSize = 22.sp, lineHeight = 25.sp, color = U.c.pencil)
    val Number: TextStyle @Composable get() = TextStyle(fontFamily = Lora, fontSize = 24.sp, color = U.c.ink)
    val Button: TextStyle @Composable get() = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 15.sp)
}

@Composable
fun NotchTheme(content: @Composable () -> Unit) {
    val p = HallPalette
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = p.accent,
                onPrimary = p.paper,
                primaryContainer = p.accentSoft,
                onPrimaryContainer = p.ink,
                secondary = p.woodDark,
                secondaryContainer = p.woodLight,
                onSecondaryContainer = p.ink,
                background = p.bg,
                surface = p.surface,
                surfaceContainer = p.surface,
                surfaceContainerHigh = p.surface,
                surfaceContainerHighest = p.surface2,
                surfaceContainerLow = p.surface2,
                onBackground = p.ink,
                onSurface = p.ink,
                onSurfaceVariant = p.muted,
                outline = p.line,
                outlineVariant = p.line,
                error = p.danger,
            ),
            typography = MaterialTheme.typography.copy(
                bodyLarge = UType.Body,
                bodyMedium = UType.Body.copy(fontSize = 14.sp),
                labelLarge = UType.Button,
            ),
            content = content,
        )
    }
}
