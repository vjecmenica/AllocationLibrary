Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'benchmark-campaign-functions.ps1')

function Assert-SequenceEqual {
    param(
        [Parameter(Mandatory = $true)][string[]]$Expected,
        [Parameter(Mandatory = $true)][string[]]$Actual,
        [Parameter(Mandatory = $true)][string]$CaseName
    )

    if (($Expected -join "`n") -ne ($Actual -join "`n")) {
        throw "$CaseName returned '$($Actual -join ',')' instead of '$($Expected -join ',')'."
    }
}

$validCases = @(
    @{
        Name = 'single seed'
        Value = '42'
        Label = 'seed'
        Expected = @('42')
    },
    @{
        Name = 'multiple seeds'
        Value = '42,43,44,45,46'
        Label = 'seed'
        Expected = @('42', '43', '44', '45', '46')
    },
    @{
        Name = 'multiple profiles'
        Value = 'BALANCED_SMALL,BALANCED_MEDIUM,CONFLICT_HEAVY,CAPACITY_HEAVY'
        Label = 'profile'
        Expected = @('BALANCED_SMALL', 'BALANCED_MEDIUM', 'CONFLICT_HEAVY', 'CAPACITY_HEAVY')
    }
)

foreach ($case in $validCases) {
    $actual = @(Get-CommaValues $case.Value $case.Label 'helper-test')
    Assert-SequenceEqual $case.Expected $actual $case.Name
}

$invalidCases = @(
    @{ Value = '42,,44'; Label = 'seed' },
    @{ Value = '42,43,'; Label = 'seed' },
    @{ Value = ',42,43'; Label = 'seed' },
    @{ Value = '42,42'; Label = 'seed' },
    @{ Value = 'BALANCED_SMALL,BALANCED_SMALL'; Label = 'profile' }
)

foreach ($case in $invalidCases) {
    $thrown = $false
    try {
        Get-CommaValues $case.Value $case.Label 'helper-test' | Out-Null
    }
    catch {
        $thrown = $true
    }

    if (-not $thrown) {
        throw "Invalid $($case.Label) value '$($case.Value)' was accepted."
    }
}

Write-Host "PowerShell campaign helper tests passed: $($validCases.Count + $invalidCases.Count) cases."
