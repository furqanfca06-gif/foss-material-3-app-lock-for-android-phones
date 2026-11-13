Add-Type -AssemblyName System.Drawing
$root = Join-Path $PSScriptRoot '..\app\src\main\res'
$root = [IO.Path]::GetFullPath($root)
function New-LockBitmap([int]$size, [bool]$background) {
    $bmp = [Drawing.Bitmap]::new($size, $size, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.Clear([Drawing.Color]::Transparent)
    if ($background) {
        $bg = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(255, 23, 21, 34))
        $g.FillRectangle($bg, 0, 0, $size, $size); $bg.Dispose()
        $halo = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(255, 53, 45, 82))
        $g.FillEllipse($halo, [int]($size*.14), [int]($size*.14), [int]($size*.72), [int]($size*.72)); $halo.Dispose()
    }
    $scale = $size / 512.0
    $pen = [Drawing.Pen]::new([Drawing.Color]::FromArgb(255, 189, 160, 255), [single](42*$scale))
    $pen.StartCap = [Drawing.Drawing2D.LineCap]::Round; $pen.EndCap = [Drawing.Drawing2D.LineCap]::Round
    $g.DrawArc($pen, [single](179*$scale), [single](104*$scale), [single](154*$scale), [single](190*$scale), 180, 180)
    $pen.Dispose()
    $body = [Drawing.Drawing2D.GraphicsPath]::new()
    $r = [single](142*$scale); $x=[single](112*$scale); $y=[single](238*$scale); $w=[single](288*$scale); $h=[single](220*$scale)
    $body.AddArc($x,$y,$r,$r,180,90); $body.AddArc($x+$w-$r,$y,$r,$r,270,90); $body.AddArc($x+$w-$r,$y+$h-$r,$r,$r,0,90); $body.AddArc($x,$y+$h-$r,$r,$r,90,90); $body.CloseFigure()
    $brush = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(255, 151, 104, 255))
    $g.FillPath($brush,$body)
    $brush.Dispose(); $body.Dispose(); $g.Dispose()
    return $bmp
}
$fg = New-LockBitmap 512 $false
$fg.Save((Join-Path $root 'drawable-nodpi\ic_launcher_foreground.png'), [Drawing.Imaging.ImageFormat]::Png); $fg.Dispose()
foreach ($entry in @(@('mdpi',48),@('hdpi',72),@('xhdpi',96),@('xxhdpi',144),@('xxxhdpi',192))) {
    $dir = Join-Path $root ('mipmap-' + $entry[0]); New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $icon = New-LockBitmap ([int]$entry[1]) $true
    $icon.Save((Join-Path $dir 'ic_launcher.png'), [Drawing.Imaging.ImageFormat]::Png)
    $icon.Save((Join-Path $dir 'ic_launcher_round.png'), [Drawing.Imaging.ImageFormat]::Png)
    $icon.Dispose()
}
