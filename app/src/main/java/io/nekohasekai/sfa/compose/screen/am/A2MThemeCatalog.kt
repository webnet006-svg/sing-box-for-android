package io.nekohasekai.sfa.compose.screen.am

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.nekohasekai.sfa.dams.theme.ThemePackage
import io.nekohasekai.sfa.database.Settings

/**
 * Catalogue de thèmes A2M TUN : palettes intégrées, thème personnalisé et
 * paquets `.a2mtheme` importables.
 *
 * 8 palettes × 2 modes (clair/sombre) = 16 combinaisons, appliquées à TOUTE
 * l'application via [A2MColors] (états Compose : recoloration instantanée).
 * Les clés persistées (Settings.themeKey / themeMode) ne doivent jamais être
 * renommées sans migration : elles constituent l'identité stable des thèmes.
 */
data class A2MPaletteTokens(
    val backgroundTop: Color,
    val backgroundMid: Color,
    val accent: Color,
    val accentUpload: Color,
    val text: Color,
    val textMuted: Color,
    val faint: Color,
    val border: Color,
    val cardTop: Color,
    val cardBottom: Color,
    val tile: Color,
    val danger: Color,
    val amber: Color,
)

data class A2MPalette(
    val id: String,
    val label: String,
    val tagline: String,
    val isDark: Boolean,
    val tokens: A2MPaletteTokens,
    /** Aperçu décoratif de la palette (artwork du sélecteur). */
    val preview: List<Color>,
)

enum class A2MThemeMode(val key: String, val label: String) {
    SYSTEM("system", "Système"),
    LIGHT("light", "Clair"),
    DARK("dark", "Sombre"),
    ;

    companion object {
        fun fromKey(key: String): A2MThemeMode = entries.firstOrNull { it.key == key } ?: DARK
    }
}

data class A2MThemeSettings(
    val paletteId: String = "a2m",
    val mode: A2MThemeMode = A2MThemeMode.DARK,
)

object A2MThemeCatalog {
    private fun parse(hex: String): Color = Color(android.graphics.Color.parseColor(hex.replaceFirst("0x", "#")))

    private fun t(
        bgTop: String,
        bgMid: String,
        accent: String,
        upload: String,
        text: String,
        muted: String,
        faint: String,
        border: String,
        cardTop: String,
        cardBottom: String,
        tile: String,
        danger: String,
        amber: String,
    ): A2MPaletteTokens =
        A2MPaletteTokens(
            backgroundTop = parse(bgTop),
            backgroundMid = parse(bgMid),
            accent = parse(accent),
            accentUpload = parse(upload),
            text = parse(text),
            textMuted = parse(muted),
            faint = parse(faint),
            border = parse(border),
            cardTop = parse(cardTop),
            cardBottom = parse(cardBottom),
            tile = parse(tile),
            danger = parse(danger),
            amber = parse(amber),
        )

    /** Palettes intégrées : signature « a2m » puis 7 variantes claires/sombres. */
    val palettes: List<A2MPalette> = buildList {
        // ── a2m (signature) ─────────────────────────────────────────────
        add(
            A2MPalette(
                "a2m", "A2M", "Cyber sécurité, bleu nuit et cyan",
                false,
                t("0xFF14418A", "0xFF06152E", "0xFF20DFFF", "0xFF9A8CFF", "0xFFE8F2FF", "0xFF8CA6C8", "0xFF4A6A99", "0x3850A0FF", "0x7314325F", "0xB30A1E46", "0xFF102A50", "0xFFFF5C6C", "0xFFFFC93C"),
                emptyList(),
            ),
        )
        add(
            A2MPalette(
                "a2m", "A2M", "Cyber sécurité, bleu nuit et cyan",
                true,
                t("0xFF14418A", "0xFF06152E", "0xFF20DFFF", "0xFF9A8CFF", "0xFFE8F2FF", "0xFF8CA6C8", "0xFF4A6A99", "0x3850A0FF", "0x7314325F", "0xB30A1E46", "0xFF102A50", "0xFFFF5C6C", "0xFFFFC93C"),
                emptyList(),
            ),
        )

        // ── azure (bleu nuit + cyan) ────────────────────────────────────
        add(
            A2MPalette(
                "azure", "Azur", "Bleu nuit profond, accents cyan",
                true,
                t("0xFF04152B", "0xFF010812", "0xFF19D8F5", "0xFF2F80FF", "0xFFE8F4FF", "0xFF7FA0C4", "0xFF48688C", "0xFF16294A", "0xFF0D2949", "0xFF0A1E38", "0xFF122A4D", "0xFFFF5C6C", "0xFFF5A524"),
                emptyList(),
            ),
        )
        add(
            A2MPalette(
                "azure", "Azur", "Bleu clair aérien, accents cyan",
                false,
                t("0xFFE3F2FF", "0xFFF8FBFF", "0xFF0E9FD8", "0xFF2F80FF", "0xFF0A1E38", "0xFF4A6B8C", "0xFF8FABC8", "0xFFD2E4F7", "0xFFFFFFFF", "0xFFF2F8FE", "0xFFE2EEF9", "0xFFE5484D", "0xFFD97706"),
                emptyList(),
            ),
        )

        // ── aurora (cyan / violet froid) ────────────────────────────────
        add(
            A2MPalette(
                "aurora", "Aurora", "Aurores froides, cyan et violette",
                true,
                t("0xFF071A24", "0xFF030D14", "0xFF35E0D8", "0xFF8B5CF6", "0xFFE7F7F6", "0xFF7BA4A8", "0xFF476B70", "0xFF173039", "0xFF0E2A33", "0xFF091E26", "0xFF12333C", "0xFFFF5C6C", "0xFFF5A524"),
                emptyList(),
            ),
        )
        add(
            A2MPalette(
                "aurora", "Aurora", "Aurores froides, teintes claires",
                false,
                t("0xFFDFF7F5", "0xFFF7FCFB", "0xFF0C9E96", "0xFF7C5CFF", "0xFF10262A", "0xFF54787C", "0xFF93B8B8", "0xFFD4EAE8", "0xFFFFFFFF", "0xFFF3FAF9", "0xFFE1F1EF", "0xFFE5484D", "0xFFD97706"),
                emptyList(),
            ),
        )

        // ── ember (braises chaudes) ─────────────────────────────────────
        add(
            A2MPalette(
                "ember", "Ember", "Braises chaudes, accents orangés",
                true,
                t("0xFF1E120A", "0xFF100A06", "0xFFFF8A4C", "0xFFE0642A", "0xFFFDEFE6", "0xFFC09A82", "0xFF8A6F5C", "0xFF3A2A1C", "0xFF2A1C10", "0xFF1C130A", "0xFF33241A", "0xFFFF5C6C", "0xFFF5A524"),
                emptyList(),
            ),
        )
        add(
            A2MPalette(
                "ember", "Ember", "Braises chaudes, claires",
                false,
                t("0xFFFDEBE0", "0xFFFFF9F5", "0xFFE0642A", "0xFFB84A18", "0xFF2A1C10", "0xFF7A6A5C", "0xFFB0A090", "0xFFF3DDCC", "0xFFFFFFFF", "0xFFFCF4EE", "0xFFF7E6DA", "0xFFE5484D", "0xFFD97706"),
                emptyList(),
            ),
        )

        // ── graphite (neutres maîtrisés) ────────────────────────────────
        add(
            A2MPalette(
                "graphite", "Graphite", "Neutres sombres, contraste maîtrisé",
                true,
                t("0xFF141618", "0xFF0B0C0D", "0xFF4C9EE8", "0xFF6B7A88", "0xFFF0F2F4", "0xFF9AA4AC", "0xFF5C676E", "0xFF26292C", "0xFF1C1F21", "0xFF131517", "0xFF22262A", "0xFFFF5C6C", "0xFFF5A524"),
                emptyList(),
            ),
        )
        add(
            A2MPalette(
                "graphite", "Graphite", "Neutres clairs, lisibilité maximale",
                false,
                t("0xFFEEF0F2", "0xFFF9FAFB", "0xFF2E6FA8", "0xFF5C6B78", "0xFF1B2024", "0xFF5A6570", "0xFF9AA4AC", "0xFFDBDEE2", "0xFFFFFFFF", "0xFFF4F6F8", "0xFFE4E7EA", "0xFFE5484D", "0xFFD97706"),
                emptyList(),
            ),
        )

        // ── orchid (violet / magenta) ───────────────────────────────────
        add(
            A2MPalette(
                "orchid", "Orchid", "Violet profond, accents magenta",
                true,
                t("0xFF180F28", "0xFF0D0818", "0xFFB582FF", "0xFFE040FB", "0xFFF3EBFC", "0xFFA890C4", "0xFF6E5C8A", "0xFF2C1F42", "0xFF221735", "0xFF170F24", "0xFF2A1D42", "0xFFFF5C6C", "0xFFF5A524"),
                emptyList(),
            ),
        )
        add(
            A2MPalette(
                "orchid", "Orchid", "Violet profond, version claire",
                false,
                t("0xFFEFE6FA", "0xFFFBF8FE", "0xFF7A3ED6", "0xFFC43ED6", "0xFF241634", "0xFF6E5C8A", "0xFFA898C4", "0xFFE0D4F0", "0xFFFFFFFF", "0xFFF7F2FD", "0xFFEAE0F6", "0xFFE5484D", "0xFFD97706"),
                emptyList(),
            ),
        )

        // ── paper (clair et sobre) ──────────────────────────────────────
        add(
            A2MPalette(
                "paper", "Paper", "Clair et sobre, inspiration papier",
                false,
                t("0xFFF1EEE7", "0xFFFCFBF7", "0xFF3E6E5C", "0xFF5C6B5E", "0xFF232A24", "0xFF6E7A6E", "0xFFA8B0A6", "0xFFE0DDD2", "0xFFFFFFFF", "0xFFF8F7F2", "0xFFEBE9DF", "0xFFD9534F", "0xFFD97706"),
                emptyList(),
            ),
        )
        add(
            A2MPalette(
                "paper", "Paper", "Sobre, en sombre doux",
                true,
                t("0xFF22211D", "0xFF15140F", "0xFF7CC4A8", "0xFF94A894", "0xFFF1EEE7", "0xFFA8A69C", "0xFF6E6C62", "0xFF32302A", "0xFF26251F", "0xFF1B1A15", "0xFF2C2B24", "0xFFFF6B6B", "0xFFF5A524"),
                emptyList(),
            ),
        )

        // ── matcha (verts naturels) ─────────────────────────────────────
        add(
            A2MPalette(
                "matcha", "Matcha", "Verts naturels, ambiance apaisante",
                false,
                t("0xFFE2F0DF", "0xFFF8FBF7", "0xFF3E8E4E", "0xFF6B9E5C", "0xFF1B2A1C", "0xFF5C7A5E", "0xFF9DB8A0", "0xFFD5E4D4", "0xFFFFFFFF", "0xFFF5FAF4", "0xFFE4EFE2", "0xFFD9534F", "0xFFD97706"),
                emptyList(),
            ),
        )
        add(
            A2MPalette(
                "matcha", "Matcha", "Verts naturels, en sombre",
                true,
                t("0xFF101C12", "0xFF081108", "0xFF6ED47E", "0xFF94C484", "0xFFEBF5EA", "0xFF8AAA8C", "0xFF55705A", "0xFF1E2E1F", "0xFF16251A", "0xFF0F1A12", "0xFF1C2E1E", "0xFFFF5C6C", "0xFFF5A524"),
                emptyList(),
            ),
        )
    }.map { p -> p.copy(preview = previewFor(p)) }

    /** Aperçu : [fond, accent, upload, carte] pour l'artwork du sélecteur. */
    private fun previewFor(p: A2MPalette): List<Color> =
        listOf(p.tokens.backgroundTop, p.tokens.accent, p.tokens.accentUpload, p.tokens.cardTop)

    // ── Thème personnalisé (.a2mtheme) ──────────────────────────────────

    /** Identifiant stable du thème personnalisé (persisté dans Settings.themeKey). */
    const val CUSTOM_ID = "custom"

    /** Couleurs proposées dans l'éditeur (accent et accent secondaire). */
    val ACCENT_SWATCHES: List<String> =
        listOf(
            "#FF16E0C4",
            "#FF0FA98F",
            "#FF19D8F5",
            "#FF2F80FF",
            "#FF7C5CFF",
            "#FFB582FF",
            "#FFE040FB",
            "#FFFF8A4C",
            "#FFE0642A",
            "#FF6ED47E",
            "#FF4C9EE8",
            "#FFFF5C6C",
        )

    private fun tokensFromHex(colors: Map<String, String>): A2MPaletteTokens =
        t(
            colors.getValue("backgroundTop"),
            colors.getValue("backgroundMid"),
            colors.getValue("accent"),
            colors.getValue("accentUpload"),
            colors.getValue("text"),
            colors.getValue("textMuted"),
            colors.getValue("faint"),
            colors.getValue("border"),
            colors.getValue("cardTop"),
            colors.getValue("cardBottom"),
            colors.getValue("tile"),
            colors.getValue("danger"),
            colors.getValue("amber"),
        )

    /** Convertit un paquet `.a2mtheme` en palette applicable par l'interface. */
    fun paletteFromPackage(data: ThemePackage.Data): A2MPalette {
        val palette =
            A2MPalette(
                CUSTOM_ID,
                data.name.ifBlank { "Personnalisé" },
                "Thème personnalisé importé ou créé dans l'application",
                data.dark,
                tokensFromHex(data.colors),
                emptyList(),
            )
        return palette.copy(preview = previewFor(palette))
    }

    /** Thème personnalisé enregistré, ou `null` s'il n'y en a pas. */
    fun storedCustom(): ThemePackage.Data? = ThemePackage.decode(Settings.customTheme)

    /** Thème personnalisé actif (créé par défaut si aucun n'a été enregistré). */
    fun customPalette(): A2MPalette =
        paletteFromPackage(
            storedCustom()?.let(::forceDark) ?: ThemePackage.derive(dark = true, accent = "#FF16E0C4", accentUpload = "#FF7C5CFF"),
        )

    private fun forceDark(data: ThemePackage.Data): ThemePackage.Data {
        val base = palettes.first { it.id == "a2m" && it.isDark }.tokens
        val requiredDarkColors = mapOf(
            "backgroundTop" to hexOf(base.backgroundTop), "backgroundMid" to hexOf(base.backgroundMid),
            "text" to hexOf(base.text), "textMuted" to hexOf(base.textMuted), "faint" to hexOf(base.faint),
            "border" to hexOf(base.border), "cardTop" to hexOf(base.cardTop), "cardBottom" to hexOf(base.cardBottom),
            "tile" to hexOf(base.tile), "danger" to hexOf(base.danger), "amber" to hexOf(base.amber),
        )
        return data.copy(dark = true, colors = data.colors + requiredDarkColors)
    }

    fun enforceDark(data: ThemePackage.Data): ThemePackage.Data = forceDark(data)

    /** Sérialise une palette intégrée pour l'export `.a2mtheme`. */
    fun packageOf(palette: A2MPalette): ThemePackage.Data =
        ThemePackage.Data(
            name = palette.label,
            dark = palette.isDark,
            colors =
                linkedMapOf(
                    "backgroundTop" to hexOf(palette.tokens.backgroundTop),
                    "backgroundMid" to hexOf(palette.tokens.backgroundMid),
                    "accent" to hexOf(palette.tokens.accent),
                    "accentUpload" to hexOf(palette.tokens.accentUpload),
                    "text" to hexOf(palette.tokens.text),
                    "textMuted" to hexOf(palette.tokens.textMuted),
                    "faint" to hexOf(palette.tokens.faint),
                    "border" to hexOf(palette.tokens.border),
                    "cardTop" to hexOf(palette.tokens.cardTop),
                    "cardBottom" to hexOf(palette.tokens.cardBottom),
                    "tile" to hexOf(palette.tokens.tile),
                    "danger" to hexOf(palette.tokens.danger),
                    "amber" to hexOf(palette.tokens.amber),
                ),
        )

    private fun hexOf(color: Color): String = String.format("#%08X", color.toArgb())

    fun forId(id: String, dark: Boolean): A2MPalette {
        // Migration douce : l'ancien identifiant « onfaa » désigne désormais « azure ».
        val key = if (id == "onfaa") "azure" else id
        if (key == CUSTOM_ID) return customPalette()
        return palettes.firstOrNull { it.id == key && it.isDark == dark }
            ?: palettes.first { it.id == "a2m" && it.isDark }
    }

    fun paletteIds(): List<Pair<String, String>> =
        palettes
            .filter { it.isDark }
            .map { it.id to it.label } + (CUSTOM_ID to "Personnalisé")
}

/** État de thème global : charge, persiste et applique la combinaison choisie. */
object A2MThemeState {
    var settings by mutableStateOf(A2MThemeSettings())
        private set

    /** Incrémenté à chaque application d'un thème personnalisé (recomposition). */
    var customRevision by mutableStateOf(0)
        private set

    /** Config système claire/sombre, mise à jour depuis l'interface (mode SYSTEM). */
    private var systemDark by mutableStateOf(false)

    var fontId by mutableStateOf("system")
        private set
    var orbAnimationId by mutableStateOf("pulse")
        private set

    /** Résout palette×mode selon le réglage (SYSTEM suit la configuration Android). */
    fun currentPalette(): A2MPalette = A2MThemeCatalog.forId(settings.paletteId, true)

    /** Signale la config système ; réapplique si le mode est SYSTEM. */
    fun applySystemDark(isDark: Boolean) {
        val changed = systemDark != isDark
        systemDark = isDark
        if (changed && settings.mode == A2MThemeMode.SYSTEM) applySettings()
    }

    /** Charge les préférences persistées au démarrage de l'application. */
    fun load() {
        fontId = A2MFontCatalog.normalizedId(Settings.a2mFontId)
        orbAnimationId = A2MOrbAnimations.normalizedId(Settings.a2mOrbAnimationId)
        val savedKey = Settings.themeKey
        // Migration douce de l'ancien identifiant de palette « onfaa ».
        val key =
            when (savedKey) {
                "onfaa" -> "azure"
                "" -> "a2m"
                else -> savedKey
            }
        if (key != savedKey) Settings.themeKey = key
        settings = A2MThemeSettings(paletteId = key, mode = A2MThemeMode.DARK)
        Settings.themeMode = A2MThemeMode.DARK.key
        applySettings()
    }

    fun setFont(id: String) {
        fontId = A2MFontCatalog.normalizedId(id)
        Settings.a2mFontId = fontId
    }

    fun setOrbAnimation(id: String) {
        orbAnimationId = A2MOrbAnimations.normalizedId(id)
        Settings.a2mOrbAnimationId = orbAnimationId
    }

    fun setPalette(id: String) {
        settings = settings.copy(paletteId = id)
        Settings.themeKey = id
        applySettings()
    }

    fun setMode(mode: A2MThemeMode) {
        settings = settings.copy(mode = A2MThemeMode.DARK)
        Settings.themeMode = A2MThemeMode.DARK.key
        applySettings()
    }

    /** Enregistre et applique un thème personnalisé (créé ou importé). */
    fun applyCustom(data: ThemePackage.Data) {
        Settings.customTheme = ThemePackage.encodeCompact(A2MThemeCatalog.enforceDark(data))
        settings = settings.copy(paletteId = A2MThemeCatalog.CUSTOM_ID)
        Settings.themeKey = A2MThemeCatalog.CUSTOM_ID
        customRevision++
        applySettings()
    }

    /** Crée (et applique) un thème personnalisé à partir des choix de l'éditeur. */
    fun applyDerivedCustom(
        dark: Boolean,
        accent: String,
        accentUpload: String,
        name: String = "Personnalisé",
    ) {
        applyCustom(ThemePackage.derive(dark, accent, accentUpload, name))
    }

    /** Paquet `.a2mtheme` correspondant au thème actuellement appliqué. */
    fun currentPackage(): ThemePackage.Data {
        val palette = currentPalette()
        return if (palette.id == A2MThemeCatalog.CUSTOM_ID) {
            A2MThemeCatalog.storedCustom() ?: A2MThemeCatalog.packageOf(palette)
        } else {
            A2MThemeCatalog.packageOf(palette)
        }
    }

    /** Applique immédiatement la palette résolue. */
    private fun applySettings() {
        A2MColors.applyPalette(A2MThemeCatalog.forId(settings.paletteId, true).tokens)
    }
}

/** Built-in Android typefaces only: no runtime downloads or added font binaries. */
data class A2MFontChoice(val id: String, val label: String, val familyName: String)

object A2MFontCatalog {
    val choices = listOf(
        A2MFontChoice("system", "Système", "sans-serif"),
        A2MFontChoice("condensed", "Condensée", "sans-serif-condensed"),
        A2MFontChoice("light", "Fine", "sans-serif-light"),
        A2MFontChoice("medium", "Moyenne", "sans-serif-medium"),
        A2MFontChoice("black", "Noire", "sans-serif-black"),
        A2MFontChoice("thin", "Légère", "sans-serif-thin"),
        A2MFontChoice("condensed-light", "Condensée fine", "sans-serif-condensed-light"),
        A2MFontChoice("condensed-medium", "Condensée moyenne", "sans-serif-condensed-medium"),
        A2MFontChoice("condensed-black", "Condensée noire", "sans-serif-condensed-black"),
        A2MFontChoice("rounded", "Arrondie", "sans-serif-rounded"),
        A2MFontChoice("serif", "Sérif", "serif"),
        A2MFontChoice("monospace", "Monospace", "monospace"),
        A2MFontChoice("casual", "Décontractée", "casual"),
        A2MFontChoice("cursive", "Manuscrite", "cursive"),
        A2MFontChoice("small-caps", "Petites capitales", "sans-serif-smallcaps"),
    )

    fun normalizedId(id: String): String = choices.firstOrNull { it.id == id }?.id ?: "system"

    fun family(id: String): androidx.compose.ui.text.font.FontFamily {
        val choice = choices.firstOrNull { it.id == normalizedId(id) } ?: choices.first()
        val typeface = android.graphics.Typeface.create(choice.familyName, android.graphics.Typeface.NORMAL)
        return androidx.compose.ui.text.font.FontFamily(typeface)
    }
}

data class A2MOrbAnimation(val id: String, val label: String, val description: String)

object A2MOrbAnimations {
    val choices = listOf(
        A2MOrbAnimation("pulse", "Pulsation", "Halo qui respire"),
        A2MOrbAnimation("orbit", "Orbite", "Graduations en rotation lente"),
        A2MOrbAnimation("wave", "Onde", "Lueur qui varie doucement"),
        A2MOrbAnimation("spark", "Impulsion", "Accent lumineux rythmé"),
        A2MOrbAnimation("breathe", "Respiration", "Halo doux et lent"),
        A2MOrbAnimation("scan", "Balayage", "Anneau animé"),
        A2MOrbAnimation("steady", "Stable", "Aucune animation continue"),
    )

    fun normalizedId(id: String): String = choices.firstOrNull { it.id == id }?.id ?: "pulse"
}
