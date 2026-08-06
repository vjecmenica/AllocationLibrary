function Assert-Condition {
    param(
        [Parameter(Mandatory = $true)][bool]$Condition,
        [Parameter(Mandatory = $true)][string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

function Get-CommaValues {
    param(
        [AllowNull()][object]$Value,
        [Parameter(Mandatory = $true)][string]$Label,
        [Parameter(Mandatory = $true)][string]$ExperimentId
    )

    Assert-Condition ($Value -is [string] -and -not [string]::IsNullOrWhiteSpace([string]$Value)) "Experiment $ExperimentId has a blank $Label argument."
    $values = @(
        ([string]$Value).Split(
            [char[]]@(','),
            [System.StringSplitOptions]::None
        ) | ForEach-Object { $_.Trim() }
    )
    Assert-Condition ((@($values | Where-Object { [string]::IsNullOrWhiteSpace($_) })).Count -eq 0) "Experiment $ExperimentId has a blank $Label value."
    $duplicates = @($values | Group-Object | Where-Object Count -gt 1)
    if ($duplicates.Count -gt 0) {
        throw "Experiment $ExperimentId has duplicate $Label value: $($duplicates[0].Name)"
    }
    return $values
}
