# Night Client - Windows "now playing" bridge.
# Reads the system media controls (the same info as the Windows volume flyout).
#   no arguments  : prints one line per second:  status<TAB>artist<TAB>title<TAB>positionUs<TAB>lengthUs<TAB>artUrl
#   -Action <x>   : play-pause | next | previous, then exits
param([string]$Action = "")

$ErrorActionPreference = "SilentlyContinue"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Add-Type -AssemblyName System.Runtime.WindowsRuntime

$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
})[0]

function Await($op, [Type]$type) {
    $task = $asTaskGeneric.MakeGenericMethod($type).Invoke($null, @($op))
    $task.Wait(-1) | Out-Null
    $task.Result
}

[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
[void][Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime]
$managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]
$manager = Await ($managerType::RequestAsync()) $managerType

if ($Action -ne "") {
    $session = $manager.GetCurrentSession()
    if ($session) {
        switch ($Action) {
            "play-pause" { Await ($session.TryTogglePlayPauseAsync()) ([bool]) | Out-Null }
            "next"       { Await ($session.TrySkipNextAsync()) ([bool]) | Out-Null }
            "previous"   { Await ($session.TrySkipPreviousAsync()) ([bool]) | Out-Null }
        }
    }
    exit 0
}

$artFile = Join-Path $env:TEMP "night_album_art.jpg"
$lastKey = ""
$artUrl = ""

while ($true) {
    $line = "none"
    $session = $manager.GetCurrentSession()
    if ($session) {
        $props = Await ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
        if ($props -and $props.Title) {
            $playing = $session.GetPlaybackInfo().PlaybackStatus -eq "Playing"
            $timeline = $session.GetTimelineProperties()
            $pos = $timeline.Position.TotalMilliseconds
            if ($playing) {
                # the reported position is only updated now and then; move it forward to now
                $pos += ([DateTimeOffset]::Now - $timeline.LastUpdatedTime).TotalMilliseconds
            }
            $len = $timeline.EndTime.TotalMilliseconds

            # save the cover once per track
            $key = "$($props.Artist)|$($props.Title)"
            if ($key -ne $lastKey) {
                $lastKey = $key
                $artUrl = ""
                if ($props.Thumbnail) {
                    try {
                        $stream = Await ($props.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
                        $netStream = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($stream)
                        $file = [System.IO.File]::Create($artFile)
                        $netStream.CopyTo($file)
                        $file.Close()
                        $netStream.Close()
                        # change the url each track so the client reloads it
                        $artUrl = ([Uri]$artFile).AbsoluteUri + "?" + [Math]::Abs($key.GetHashCode())
                    } catch { }
                }
            }

            $status = if ($playing) { "Playing" } else { "Paused" }
            $clean = { param($s) ("$s" -replace "[`t`r`n]", " ") }
            $line = "$status`t$(& $clean $props.Artist)`t$(& $clean $props.Title)`t$([long]($pos * 1000))`t$([long]($len * 1000))`t$artUrl"
        }
    }
    [Console]::Out.WriteLine($line)
    [Console]::Out.Flush()
    Start-Sleep -Milliseconds 1000
}
