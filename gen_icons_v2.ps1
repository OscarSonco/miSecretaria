Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @"
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;

public static class BgCleaner {
    // Quita el "tablero" gris/blanco pegado en el JPG: relleno desde los bordes sobre
    // pixeles casi grises y claros. El contorno negro y el aro dorado frenan el relleno.
    public static Bitmap Clean(string path, int tol, int minLight) {
        Bitmap src = new Bitmap(path);
        int w = src.Width, h = src.Height;
        Bitmap bmp = new Bitmap(w, h, PixelFormat.Format32bppArgb);
        using (Graphics g = Graphics.FromImage(bmp)) g.DrawImage(src, 0, 0, w, h);
        src.Dispose();
        BitmapData d = bmp.LockBits(new Rectangle(0, 0, w, h), ImageLockMode.ReadWrite, PixelFormat.Format32bppArgb);
        int stride = d.Stride;
        byte[] px = new byte[stride * h];
        Marshal.Copy(d.Scan0, px, 0, px.Length);
        bool[] bg = new bool[w * h];
        Stack<int> st = new Stack<int>();
        for (int x = 0; x < w; x++) { Seed(px, stride, bg, st, w, x, 0, tol, minLight); Seed(px, stride, bg, st, w, x, h - 1, tol, minLight); }
        for (int y = 0; y < h; y++) { Seed(px, stride, bg, st, w, 0, y, tol, minLight); Seed(px, stride, bg, st, w, w - 1, y, tol, minLight); }
        int[] dx = { 1, -1, 0, 0 }; int[] dy = { 0, 0, 1, -1 };
        while (st.Count > 0) {
            int i = st.Pop(); int cx = i % w, cy = i / w;
            for (int k = 0; k < 4; k++) {
                int nx = cx + dx[k], ny = cy + dy[k];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                Seed(px, stride, bg, st, w, nx, ny, tol, minLight);
            }
        }
        // Dos pasadas para comer el halo claro que deja la compresion JPG junto al borde.
        for (int pass = 0; pass < 2; pass++) {
            List<int> add = new List<int>();
            for (int y = 1; y < h - 1; y++) for (int x = 1; x < w - 1; x++) {
                int i = y * w + x; if (bg[i]) continue;
                if (!(bg[i - 1] || bg[i + 1] || bg[i - w] || bg[i + w])) continue;
                int o = y * stride + x * 4; int b = px[o], gg = px[o + 1], r = px[o + 2];
                int mx = Math.Max(r, Math.Max(gg, b)), mn = Math.Min(r, Math.Min(gg, b));
                if (mx - mn <= tol + 6 && mn >= minLight - 40) add.Add(i);
            }
            foreach (int i in add) bg[i] = true;
        }
        int minX = w, minY = h, maxX = 0, maxY = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = y * w + x; int o = y * stride + x * 4;
            if (bg[i]) { px[o] = 0; px[o + 1] = 0; px[o + 2] = 0; px[o + 3] = 0; }
            else { px[o + 3] = 255; if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y; }
        }
        Marshal.Copy(px, 0, d.Scan0, px.Length);
        bmp.UnlockBits(d);
        Rectangle box = new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
        Bitmap cropped = bmp.Clone(box, PixelFormat.Format32bppArgb);
        bmp.Dispose();
        return cropped;
    }

    private static void Seed(byte[] px, int stride, bool[] bg, Stack<int> st, int w, int x, int y, int tol, int minLight) {
        int i = y * w + x; if (bg[i]) return;
        int o = y * stride + x * 4; int b = px[o], g = px[o + 1], r = px[o + 2];
        int mx = Math.Max(r, Math.Max(g, b)), mn = Math.Min(r, Math.Min(g, b));
        if (mx - mn <= tol && mn >= minLight) { bg[i] = true; st.Push(i); }
    }
}
"@

$root   = "Z:\Documents\miSecretaria\app\src\main\res"
$src    = "Z:\Documents\miSecretaria\miSecretaria.jpg"
$bgHex  = "#F6E7B4"   # color de fondo del icono (crema calido); cambiar aqui si no gusta
$bgCol  = [System.Drawing.ColorTranslator]::FromHtml($bgHex)

$subject = [BgCleaner]::Clean($src, 14, 165)
Write-Output "Personaje sin fondo: $($subject.Width)x$($subject.Height)"
$subject.Save("$env:TEMP\misecretaria_subject.png", [System.Drawing.Imaging.ImageFormat]::Png)

function Draw-Subject($canvas, $heightFraction) {
    $g = [System.Drawing.Graphics]::FromImage($canvas)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $h = [int]($canvas.Height * $heightFraction)
    $w = [int]($subject.Width * $h / $subject.Height)
    $x = [int](($canvas.Width - $w) / 2)
    $y = [int](($canvas.Height - $h) / 2)
    $g.DrawImage($subject, $x, $y, $w, $h)
    $g.Dispose()
}

function Save-Png($bmp, $path) { $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose() }

$densities = @{ "mdpi" = 48; "hdpi" = 72; "xhdpi" = 96; "xxhdpi" = 144; "xxxhdpi" = 192 }
foreach ($d in $densities.Keys) {
    $legacy = $densities[$d]
    $fg = [int]($legacy * 108 / 48)
    $dir = Join-Path $root "mipmap-$d"

    # Capa foreground (adaptativo): transparente, personaje dentro de la zona segura (~64% del alto)
    $fgBmp = New-Object System.Drawing.Bitmap($fg, $fg, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    Draw-Subject $fgBmp 0.64
    Save-Png $fgBmp (Join-Path $dir "ic_launcher_foreground.png")

    # Legacy cuadrado (Android < 8 / algunos launchers): fondo crema + personaje completo
    $sq = New-Object System.Drawing.Bitmap($legacy, $legacy, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($sq); $g.Clear($bgCol); $g.Dispose()
    Draw-Subject $sq 0.90

    # Legacy redondo
    $rd = New-Object System.Drawing.Bitmap($legacy, $legacy, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($rd)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $gp = New-Object System.Drawing.Drawing2D.GraphicsPath
    $gp.AddEllipse(0, 0, $legacy - 1, $legacy - 1)
    $g.SetClip($gp)
    $g.DrawImage($sq, 0, 0, $legacy, $legacy)
    $g.Dispose()
    Save-Png $rd (Join-Path $dir "ic_launcher_round.png")
    Save-Png $sq (Join-Path $dir "ic_launcher.png")
    Write-Output "OK $d legacy=$legacy fg=$fg"
}
$subject.Dispose()
Write-Output "DONE"
