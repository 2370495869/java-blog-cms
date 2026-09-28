Set-Location $PSScriptRoot
& "$PSScriptRoot\mvnw.cmd" -B -ntp spring-boot:run @args
exit $LASTEXITCODE
