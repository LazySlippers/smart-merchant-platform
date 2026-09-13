function Get-ProjectBackendProcess {
    param([string]$BackendRoot)
    $names = @('tenant-service','iam-service','merchant-service','member-service','trade-service','analytics-service','saas-gateway')
    $javaProcesses = @(Get-CimInstance Win32_Process -Filter "name = 'java.exe'" -ErrorAction Stop)
    foreach ($name in $names) {
        $jar = [IO.Path]::GetFullPath((Join-Path $BackendRoot "$name\target\$name-0.1.0-SNAPSHOT.jar"))
        $escaped = [regex]::Escape($jar)
        $pattern = '(?i)(?:^|\s)-jar\s+(?:"' + $escaped + '"|' + $escaped + ')(?=\s|$)'
        foreach ($process in $javaProcesses) {
            if ($process.CommandLine -match $pattern) {
                [pscustomobject]@{ Name=$name; Pid=[int]$process.ProcessId }
            }
        }
    }
}
function Stop-ProjectBackendProcess {
    param([string]$BackendRoot)
    foreach ($item in @(Get-ProjectBackendProcess -BackendRoot $BackendRoot)) {
        $process = Get-Process -Id $item.Pid -ErrorAction SilentlyContinue
        if ($process) {
            [System.Diagnostics.Process]::GetProcessById($item.Pid).Kill()
            try { Wait-Process -Id $item.Pid -Timeout 15 -ErrorAction Stop } catch {
                if (Get-Process -Id $item.Pid -ErrorAction SilentlyContinue) { throw "Service $($item.Name) did not stop; cannot rebuild its JAR." }
            }
            Write-Host "  stopped $($item.Name) (PID $($item.Pid))"
        }
    }
}
