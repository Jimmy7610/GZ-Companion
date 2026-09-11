namespace GZCompanion.Installer.App;

/// <summary>Mirrors the in-game GZ Companion palette (se.jimmyeliasson.gzcompanion.ui.GZTheme) so the installer feels like part of the same product.</summary>
internal static class Theme
{
    public static readonly Color PanelBg = Color.FromArgb(0x0D, 0x14, 0x1C);
    public static readonly Color CardBg = Color.FromArgb(0x13, 0x1F, 0x2B);
    public static readonly Color CardInner = Color.FromArgb(0x0A, 0x10, 0x17);
    public static readonly Color BorderSubtle = Color.FromArgb(0x47, 0x55, 0x69);

    public static readonly Color Emerald = Color.FromArgb(0x10, 0xB9, 0x81);
    public static readonly Color EmeraldDark = Color.FromArgb(0x05, 0x96, 0x69);
    public static readonly Color Mint = Color.FromArgb(0x34, 0xD3, 0x99);

    public static readonly Color TextPrimary = Color.FromArgb(0xF8, 0xFA, 0xFC);
    public static readonly Color TextSecondary = Color.FromArgb(0x94, 0xA3, 0xB8);
    public static readonly Color TextMuted = Color.FromArgb(0x64, 0x74, 0x8B);
    public static readonly Color TextOnEmerald = Color.FromArgb(0x04, 0x17, 0x0E);

    public static readonly Color StatusGreen = Color.FromArgb(0x22, 0xC5, 0x5E);
    public static readonly Color StatusYellow = Color.FromArgb(0xF5, 0x9E, 0x0B);
    public static readonly Color StatusRed = Color.FromArgb(0xEF, 0x44, 0x44);

    public static readonly Font HeadingFont = new("Segoe UI", 16f, FontStyle.Bold);
    public static readonly Font BodyFont = new("Segoe UI", 9.5f);
    public static readonly Font SmallFont = new("Segoe UI", 8.5f);
    public static readonly Font MonoFont = new("Consolas", 9f);

    public static Button PrimaryButton(string text)
    {
        var btn = new Button
        {
            Text = text,
            Font = new Font("Segoe UI", 10f, FontStyle.Bold),
            BackColor = Emerald,
            ForeColor = TextOnEmerald,
            FlatStyle = FlatStyle.Flat,
            Height = 40,
            Cursor = Cursors.Hand,
        };
        btn.FlatAppearance.BorderSize = 0;
        btn.FlatAppearance.MouseOverBackColor = EmeraldDark;
        return btn;
    }

    public static Button SecondaryLinkButton(string text)
    {
        var btn = new Button
        {
            Text = text,
            Font = SmallFont,
            BackColor = PanelBg,
            ForeColor = TextSecondary,
            FlatStyle = FlatStyle.Flat,
            Height = 24,
            Cursor = Cursors.Hand,
            TextAlign = ContentAlignment.MiddleLeft,
        };
        btn.FlatAppearance.BorderSize = 0;
        return btn;
    }

    public static Panel Card()
    {
        var panel = new Panel
        {
            BackColor = CardBg,
            Padding = new Padding(12),
        };
        return panel;
    }
}
