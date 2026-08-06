[CmdletBinding()]
param(
    [string]$SourceCommit,
    [string]$OutputRoot,
    [ValidateSet('smoke', 'standard', 'extended')]
    [string]$Preset = 'standard',
    [switch]$Overwrite,
    [switch]$SkipTests,
    [switch]$AllowDirtyWorkingTree,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ExpectedOutputFiles = @(
    'raw-results.csv',
    'summary-results.csv',
    'request-outcomes.csv',
    'scenario-snapshots.json',
    'metadata.json'
)
$ExpectedPresetIds = @{
    smoke = @('greedy-trap-validation')
    standard = @('core-profiles', 'scale-10', 'scale-20', 'scale-30', 'scale-40')
    extended = @(
        'core-profiles',
        'scale-10',
        'scale-20',
        'scale-30',
        'scale-40',
        'limits-100ms',
        'limits-500ms',
        'limits-1000ms',
        'limits-2000ms',
        'limits-5000ms'
    )
}

function Invoke-CapturedCommand {
    param(
        [Parameter(Mandatory = $true)][string]$Command,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )

    $output = & $Command @Arguments 2>&1
    $exitCode = $LASTEXITCODE

    if ($exitCode -ne 0) {
        throw "Command failed with exit code ${exitCode}: $Command $($Arguments -join ' ')`n$($output -join [Environment]::NewLine)"
    }

    return ($output -join [Environment]::NewLine).Trim()
}

function Invoke-RequiredCommand {
    param(
        [Parameter(Mandatory = $true)][string]$Command,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$Description
    )

    Write-Host "`n==> $Description"
    & $Command @Arguments
    $exitCode = $LASTEXITCODE

    if ($exitCode -ne 0) {
        throw "$Description failed with exit code $exitCode."
    }
}

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
    $values = @(([string]$Value -split ',', -1) | ForEach-Object { $_.Trim() })
    Assert-Condition ((@($values | Where-Object { [string]::IsNullOrWhiteSpace($_) })).Count -eq 0) "Experiment $ExperimentId has a blank $Label value."
    $duplicates = @($values | Group-Object | Where-Object Count -gt 1)
    if ($duplicates.Count -gt 0) {
        throw "Experiment $ExperimentId has duplicate $Label value: $($duplicates[0].Name)"
    }
    return $values
}

function Test-ExperimentDefinition {
    param([Parameter(Mandatory = $true)]$Experiment)

    $id = [string]$Experiment.experimentId
    Assert-Condition (-not [string]::IsNullOrWhiteSpace($id)) 'Campaign plan contains a blank experiment ID.'
    $profiles = @(Get-CommaValues $Experiment.profiles 'profile' $id)
    $seeds = @(Get-CommaValues $Experiment.seeds 'seed' $id)
    foreach ($seed in $seeds) {
        $parsedSeed = 0L
        Assert-Condition ([long]::TryParse($seed, [ref]$parsedSeed)) "Experiment $id has invalid seed value: $seed"
    }
    $warmups = 0
    $runs = 0
    $backtrackingLimit = 0L
    $cpLimit = 0.0
    Assert-Condition ([int]::TryParse([string]$Experiment.warmups, [ref]$warmups) -and $warmups -ge 0) "Experiment $id has invalid warmups: $($Experiment.warmups)"
    Assert-Condition ([int]::TryParse([string]$Experiment.measuredRuns, [ref]$runs) -and $runs -gt 0) "Experiment $id has invalid measuredRuns: $($Experiment.measuredRuns)"
    Assert-Condition (($runs % 3) -eq 0) "Experiment $id measuredRuns must be divisible by three: $runs"
    Assert-Condition ([long]::TryParse([string]$Experiment.backtrackingTimeLimitMs, [ref]$backtrackingLimit) -and $backtrackingLimit -gt 0) "Experiment $id has invalid Backtracking limit: $($Experiment.backtrackingTimeLimitMs)"
    Assert-Condition ([double]::TryParse(
        [string]$Experiment.cpSatTimeLimitSeconds,
        [Globalization.NumberStyles]::Float,
        [Globalization.CultureInfo]::InvariantCulture,
        [ref]$cpLimit
    )) "Experiment $id has invalid CP-SAT limit: $($Experiment.cpSatTimeLimitSeconds)"
    Assert-Condition (-not [double]::IsNaN($cpLimit) -and -not [double]::IsInfinity($cpLimit) -and $cpLimit -gt 0) "Experiment $id has invalid CP-SAT limit: $($Experiment.cpSatTimeLimitSeconds)"

    $scaleValues = @($Experiment.scaleResources, $Experiment.scaleRequests, $Experiment.scaleResourceTypes)
    $presentCount = @($scaleValues | Where-Object { $null -ne $_ }).Count
    $isScale = $profiles.Count -eq 1 -and $profiles[0] -eq 'SCALE'
    Assert-Condition ($presentCount -eq 0 -or $presentCount -eq 3) "Experiment $id has an incomplete SCALE parameter trio."
    Assert-Condition ($presentCount -eq 0 -or $isScale) "Experiment $id defines SCALE values for a non-SCALE profile."
    Assert-Condition (-not $isScale -or $presentCount -eq 3) "Experiment $id is SCALE but has no complete SCALE parameter trio."
    if ($presentCount -eq 3) {
        $scaleResources = 0; $scaleRequests = 0; $scaleTypes = 0
        $validScale = [int]::TryParse([string]$Experiment.scaleResources, [ref]$scaleResources) -and
            [int]::TryParse([string]$Experiment.scaleRequests, [ref]$scaleRequests) -and
            [int]::TryParse([string]$Experiment.scaleResourceTypes, [ref]$scaleTypes)
        Assert-Condition ($validScale -and $scaleResources -gt 0 -and $scaleRequests -gt 0 -and $scaleTypes -gt 0) "Experiment $id has non-positive or invalid SCALE values."
        Assert-Condition ($scaleTypes -le $scaleResources) "Experiment $id has more resource types than resources."
    }
}

function Get-CampaignExperiments {
    param(
        [Parameter(Mandatory = $true)][string]$PlanPath,
        [Parameter(Mandatory = $true)][string]$SelectedPreset
    )

    $plan = Get-Content -LiteralPath $PlanPath -Raw | ConvertFrom-Json
    Assert-Condition ($plan.schemaVersion -eq 1) 'Unsupported campaign plan schema version.'

    $presetProperty = $plan.presets.PSObject.Properties[$SelectedPreset]
    Assert-Condition ($null -ne $presetProperty) "Unknown campaign preset: $SelectedPreset"
    Assert-Condition (
        (@($presetProperty.Value) -join ',') -eq (@($ExpectedPresetIds[$SelectedPreset]) -join ',')
    ) "Unexpected experiment IDs for preset: $SelectedPreset"

    $experimentsById = @{}
    $definitions = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
    foreach ($experiment in $plan.experiments) {
        Test-ExperimentDefinition $experiment
        $definition = $experiment | ConvertTo-Json -Compress -Depth 8
        Assert-Condition ($definitions.Add($definition)) "Duplicate experiment definition: $($experiment.experimentId)"
        Assert-Condition (-not $experimentsById.ContainsKey([string]$experiment.experimentId)) "Duplicate experiment ID: $($experiment.experimentId)"
        $experimentsById[[string]$experiment.experimentId] = $experiment
    }

    $selected = New-Object 'System.Collections.Generic.List[object]'
    foreach ($experimentId in $presetProperty.Value) {
        Assert-Condition ($experimentsById.ContainsKey($experimentId)) "Unknown experiment ID in campaign plan: $experimentId"
        $selected.Add($experimentsById[$experimentId])
    }

    return @($selected | ForEach-Object { $_ })
}

function New-BenchmarkArguments {
    param(
        [Parameter(Mandatory = $true)]$Experiment,
        [Parameter(Mandatory = $true)][string]$ExperimentOutput,
        [Parameter(Mandatory = $true)][bool]$ShouldOverwrite
    )

    $arguments = @(
        '--profile', [string]$Experiment.profiles,
        '--seed', [string]$Experiment.seeds,
        '--warmups', [string]$Experiment.warmups,
        '--runs', [string]$Experiment.measuredRuns,
        '--backtracking-limit-ms', [string]$Experiment.backtrackingTimeLimitMs,
        '--cp-sat-limit-seconds', ([Convert]::ToString(
            [double]$Experiment.cpSatTimeLimitSeconds,
            [Globalization.CultureInfo]::InvariantCulture
        )),
        '--output', $ExperimentOutput
    )

    if ($null -ne $Experiment.scaleResources) {
        $arguments += @('--resources', [string]$Experiment.scaleResources)
        $arguments += @('--requests', [string]$Experiment.scaleRequests)
        $arguments += @('--resource-types', [string]$Experiment.scaleResourceTypes)
    }

    if ($ShouldOverwrite) {
        $arguments += '--overwrite'
    }

    return $arguments
}

function ConvertTo-ExecArgument {
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$Value)

    if ($Value -match '^[A-Za-z0-9_./,:=+@\\-]+$') {
        return $Value
    }
    if (-not $Value.Contains("'")) {
        return "'$Value'"
    }

    $builder = [Text.StringBuilder]::new()
    [void]$builder.Append('"')
    $backslashes = 0
    foreach ($character in $Value.ToCharArray()) {
        if ($character -eq '\') {
            $backslashes++
            continue
        }
        if ($character -eq '"') {
            [void]$builder.Append(('\' * (($backslashes * 2) + 1)))
            [void]$builder.Append('"')
        } else {
            if ($backslashes -gt 0) { [void]$builder.Append(('\' * $backslashes)) }
            [void]$builder.Append($character)
        }
        $backslashes = 0
    }
    if ($backslashes -gt 0) { [void]$builder.Append(('\' * ($backslashes * 2))) }
    [void]$builder.Append('"')
    return $builder.ToString()
}

function Join-ExecArguments {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)
    return (($Arguments | ForEach-Object { ConvertTo-ExecArgument $_ }) -join ' ')
}

function ConvertTo-PowerShellLiteral {
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$Value)
    return "'" + $Value.Replace("'", "''") + "'"
}

function Format-MavenBenchmarkCommand {
    param(
        [Parameter(Mandatory = $true)][string]$Commit,
        [Parameter(Mandatory = $true)][string[]]$BenchmarkArguments
    )

    $cliArguments = Join-ExecArguments $BenchmarkArguments
    $sourceProperty = ConvertTo-PowerShellLiteral "-Dbenchmark.sourceCommit=$Commit"
    $execProperty = ConvertTo-PowerShellLiteral "-Dexec.args=$cliArguments"
    return "mvn -pl allocation-core exec:java $sourceProperty $execProperty"
}

function Test-CampaignPlan {
    param(
        [Parameter(Mandatory = $true)][object[]]$Experiments,
        [Parameter(Mandatory = $true)][string]$ResolvedOutputRoot,
        [Parameter(Mandatory = $true)][string]$Commit,
        [Parameter(Mandatory = $true)][bool]$ShouldOverwrite
    )

    $ids = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::OrdinalIgnoreCase)
    $directories = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::OrdinalIgnoreCase)

    foreach ($experiment in $Experiments) {
        Assert-Condition ($ids.Add([string]$experiment.experimentId)) "Duplicate experiment ID: $($experiment.experimentId)"
        Assert-Condition (($experiment.measuredRuns % 3) -eq 0) "Measured runs must be divisible by three: $($experiment.experimentId)"

        $directory = [IO.Path]::GetFullPath((Join-Path $ResolvedOutputRoot $experiment.experimentId))
        Assert-Condition ($directories.Add($directory)) "Duplicate experiment output directory: $directory"

        $benchmarkArguments = New-BenchmarkArguments $experiment $directory $ShouldOverwrite
        $command = Format-MavenBenchmarkCommand $Commit $benchmarkArguments
        Assert-Condition ($command.Contains("-Dbenchmark.sourceCommit=$Commit")) "Source commit is missing from command: $($experiment.experimentId)"
        Assert-Condition (($command.Contains('--overwrite')) -eq $ShouldOverwrite) "Unexpected overwrite flag: $($experiment.experimentId)"
    }
}

function Write-CampaignManifest {
    param(
        [Parameter(Mandatory = $true)]$Manifest,
        [Parameter(Mandatory = $true)][string]$ManifestPath
    )

    $Manifest | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $ManifestPath -Encoding UTF8
}

function Assert-SchemaValues {
    param(
        [Parameter(Mandatory = $true)][object[]]$Rows,
        [Parameter(Mandatory = $true)][string]$Expected,
        [Parameter(Mandatory = $true)][string]$Label
    )

    $versions = @($Rows | ForEach-Object { [string]$_.schemaVersion } | Select-Object -Unique)
    Assert-Condition ($versions.Count -eq 1 -and $versions[0] -eq $Expected) "$Label has an unexpected schemaVersion."
}

function Get-ResultKey {
    param([Parameter(Mandatory = $true)]$Row)
    return "$($Row.profile)|$($Row.seed)|$($Row.repetition)|$($Row.algorithm)"
}

function Get-SummaryKey {
    param([Parameter(Mandatory = $true)]$Row)
    return "$($Row.profile)|$($Row.seed)|$($Row.algorithm)"
}

function Assert-Close {
    param(
        [Parameter(Mandatory = $true)][double]$Actual,
        [Parameter(Mandatory = $true)][double]$Expected,
        [Parameter(Mandatory = $true)][string]$Label
    )
    Assert-Condition ([Math]::Abs($Actual - $Expected) -le 0.000002) "Summary aggregate mismatch for ${Label}: expected $Expected, found $Actual"
}

function Assert-SmokeResult {
    param(
        [Parameter(Mandatory = $true)][object[]]$Raw,
        [Parameter(Mandatory = $true)][object[]]$Outcomes
    )

    $expectedScores = @{ GREEDY = 10; BACKTRACKING = 19; CP_SAT = 19 }
    foreach ($algorithm in $expectedScores.Keys) {
        $rows = @($Raw | Where-Object algorithm -eq $algorithm)
        Assert-Condition ($rows.Count -eq 6) "Smoke result is missing runs for $algorithm."
        Assert-Condition ((@($rows | Where-Object { [int]$_.totalPriorityScore -ne $expectedScores[$algorithm] })).Count -eq 0) "Unexpected smoke score for $algorithm."
    }

    $expectedOutcomes = @(
        @('GREEDY', 'REQ_SMALL', 'ACCEPTED'),
        @('GREEDY', 'REQ_BIG', 'REJECTED'),
        @('BACKTRACKING', 'REQ_SMALL', 'ACCEPTED'),
        @('BACKTRACKING', 'REQ_BIG', 'ACCEPTED'),
        @('CP_SAT', 'REQ_SMALL', 'ACCEPTED'),
        @('CP_SAT', 'REQ_BIG', 'ACCEPTED')
    )

    foreach ($expected in $expectedOutcomes) {
        $matches = @($Outcomes | Where-Object {
            $_.algorithm -eq $expected[0] -and
            $_.requestId -eq $expected[1] -and
            $_.outcome -eq $expected[2]
        })
        Assert-Condition ($matches.Count -eq 6) "Unexpected smoke outcome for $($expected -join '/')."
    }
}

function Test-ExperimentOutput {
    param(
        [Parameter(Mandatory = $true)]$Experiment,
        [Parameter(Mandatory = $true)][string]$ExperimentOutput,
        [Parameter(Mandatory = $true)][string]$Commit
    )

    $paths = [ordered]@{}
    foreach ($fileName in $ExpectedOutputFiles) {
        $path = Join-Path $ExperimentOutput $fileName
        Assert-Condition (Test-Path -LiteralPath $path -PathType Leaf) "Missing benchmark output file: $path"
        $paths[$fileName] = [IO.Path]::GetFullPath($path)
    }

    $raw = @(Import-Csv -LiteralPath $paths['raw-results.csv'])
    $summary = @(Import-Csv -LiteralPath $paths['summary-results.csv'])
    $outcomes = @(Import-Csv -LiteralPath $paths['request-outcomes.csv'])
    $snapshots = Get-Content -LiteralPath $paths['scenario-snapshots.json'] -Raw | ConvertFrom-Json
    $metadata = Get-Content -LiteralPath $paths['metadata.json'] -Raw | ConvertFrom-Json

    Assert-SchemaValues $raw '2' 'raw-results.csv'
    Assert-SchemaValues $summary '2' 'summary-results.csv'
    Assert-SchemaValues $outcomes '1' 'request-outcomes.csv'
    Assert-Condition ($snapshots.schemaVersion -eq 1) 'scenario-snapshots.json has an unexpected schemaVersion.'
    Assert-Condition ($metadata.schemaVersion -eq 3) 'metadata.json has an unexpected schemaVersion.'

    $profileCount = @(([string]$Experiment.profiles) -split ',').Count
    $seedCount = @(([string]$Experiment.seeds) -split ',').Count
    $expectedRawCount = $profileCount * $seedCount * [int]$Experiment.measuredRuns * 3
    $expectedSummaryCount = $profileCount * $seedCount * 3
    $expectedOutcomeCount = [int](($raw | Measure-Object -Property requestCount -Sum).Sum)

    Assert-Condition ($raw.Count -eq $expectedRawCount) "Unexpected raw row count for $($Experiment.experimentId)."
    Assert-Condition ($summary.Count -eq $expectedSummaryCount) "Unexpected summary row count for $($Experiment.experimentId)."
    Assert-Condition ($outcomes.Count -eq $expectedOutcomeCount) "Unexpected request outcome row count for $($Experiment.experimentId)."
    Assert-Condition (@($outcomes | Where-Object outcome -eq 'UNKNOWN').Count -eq 0) "UNKNOWN request outcomes found for $($Experiment.experimentId)."

    $algorithms = @($raw.algorithm | Select-Object -Unique | Sort-Object)
    Assert-Condition (($algorithms -join ',') -eq 'BACKTRACKING,CP_SAT,GREEDY') "Not all algorithms are present for $($Experiment.experimentId)."

    $expectedProfiles = @(Get-CommaValues $Experiment.profiles 'profile' ([string]$Experiment.experimentId))
    $expectedSeeds = @(Get-CommaValues $Experiment.seeds 'seed' ([string]$Experiment.experimentId))
    Assert-Condition ((@($raw.profile | Select-Object -Unique) -join ',') -eq ($expectedProfiles -join ',')) "Raw profile set or order mismatch for $($Experiment.experimentId)."
    Assert-Condition ((@($raw.seed | Select-Object -Unique) -join ',') -eq ($expectedSeeds -join ',')) "Raw seed set or order mismatch for $($Experiment.experimentId)."

    $rawByKey = @{}
    foreach ($rawRow in $raw) {
        $key = Get-ResultKey $rawRow
        Assert-Condition (-not $rawByKey.ContainsKey($key)) "Duplicate raw result tuple: $key"
        $rawByKey[$key] = $rawRow
        Assert-Condition ([long]$rawRow.backtrackingTimeLimitMs -eq [long]$Experiment.backtrackingTimeLimitMs) "Raw Backtracking limit mismatch for $key."
        Assert-Close ([double]$rawRow.cpSatTimeLimitSeconds) ([double]$Experiment.cpSatTimeLimitSeconds) "$key/cpSatTimeLimitSeconds"
        if ([string]$Experiment.profiles -eq 'SCALE') {
            Assert-Condition ([int]$rawRow.resourceCount -eq [int]$Experiment.scaleResources) "SCALE resourceCount mismatch for $key."
            Assert-Condition ([int]$rawRow.requestCount -eq [int]$Experiment.scaleRequests) "SCALE requestCount mismatch for $key."
        }
    }
    foreach ($profile in $expectedProfiles) {
        foreach ($seed in $expectedSeeds) {
            $fingerprints = @($raw | Where-Object { $_.profile -eq $profile -and $_.seed -eq $seed } | ForEach-Object scenarioFingerprint | Select-Object -Unique)
            Assert-Condition ($fingerprints.Count -eq 1) "Expected one fingerprint for $profile/$seed."
            foreach ($repetition in 1..([int]$Experiment.measuredRuns)) {
                foreach ($algorithm in @('GREEDY', 'BACKTRACKING', 'CP_SAT')) {
                    $key = "$profile|$seed|$repetition|$algorithm"
                    Assert-Condition ($rawByKey.ContainsKey($key)) "Missing raw result tuple: $key"
                }
            }
            foreach ($algorithm in @('GREEDY', 'BACKTRACKING', 'CP_SAT')) {
                $positions = @($raw | Where-Object { $_.profile -eq $profile -and $_.seed -eq $seed -and $_.algorithm -eq $algorithm } | Group-Object executionOrderPosition)
                foreach ($position in 1..3) {
                    $match = @($positions | Where-Object Name -eq ([string]$position))
                    Assert-Condition ($match.Count -eq 1 -and $match[0].Count -eq ([int]$Experiment.measuredRuns / 3)) "Unbalanced executionOrderPosition for $profile/$seed/$algorithm at position $position."
                }
            }
        }
    }

    $runIds = @(
        @(
            @($raw.benchmarkRunId) +
            @($summary.benchmarkRunId) +
            @($outcomes.benchmarkRunId) +
            @([string]$metadata.benchmarkRunId)
        ) | Select-Object -Unique
    )
    Assert-Condition ($runIds.Count -eq 1) "Benchmark run IDs do not match for $($Experiment.experimentId)."
    Assert-Condition ([string]$metadata.sourceCommit -eq $Commit) "Metadata source commit does not match for $($Experiment.experimentId)."

    Assert-Condition ((@($metadata.profiles) -join ',') -eq ($expectedProfiles -join ',')) "Metadata profile order mismatch for $($Experiment.experimentId)."
    Assert-Condition ((@($metadata.seeds | ForEach-Object { [string]$_ }) -join ',') -eq ($expectedSeeds -join ',')) "Metadata seed order mismatch for $($Experiment.experimentId)."
    Assert-Condition ((@($metadata.algorithms) -join ',') -eq 'GREEDY,BACKTRACKING,CP_SAT') "Metadata algorithms mismatch for $($Experiment.experimentId)."
    Assert-Condition ([int]$metadata.configuration.warmupRuns -eq [int]$Experiment.warmups) "Metadata warmupRuns mismatch for $($Experiment.experimentId)."
    Assert-Condition ([int]$metadata.configuration.measuredRuns -eq [int]$Experiment.measuredRuns) "Metadata measuredRuns mismatch for $($Experiment.experimentId)."
    Assert-Condition ([long]$metadata.configuration.backtrackingTimeLimitMs -eq [long]$Experiment.backtrackingTimeLimitMs) "Metadata Backtracking limit mismatch for $($Experiment.experimentId)."
    Assert-Close ([double]$metadata.configuration.cpSatTimeLimitSeconds) ([double]$Experiment.cpSatTimeLimitSeconds) "$($Experiment.experimentId)/metadataCpSatTimeLimit"
    $expectedScaleResources = if ($null -eq $Experiment.scaleResources) { 20 } else { [int]$Experiment.scaleResources }
    $expectedScaleRequests = if ($null -eq $Experiment.scaleRequests) { 20 } else { [int]$Experiment.scaleRequests }
    $expectedScaleTypes = if ($null -eq $Experiment.scaleResourceTypes) { 3 } else { [int]$Experiment.scaleResourceTypes }
    Assert-Condition ([int]$metadata.configuration.scaleResourceCount -eq $expectedScaleResources) "Metadata scaleResourceCount mismatch for $($Experiment.experimentId)."
    Assert-Condition ([int]$metadata.configuration.scaleRequestCount -eq $expectedScaleRequests) "Metadata scaleRequestCount mismatch for $($Experiment.experimentId)."
    Assert-Condition ([int]$metadata.configuration.scaleResourceTypeCount -eq $expectedScaleTypes) "Metadata scaleResourceTypeCount mismatch for $($Experiment.experimentId)."
    Assert-Condition ([bool]$metadata.configuration.overwrite -eq $Overwrite.IsPresent) "Metadata overwrite mismatch for $($Experiment.experimentId)."
    Assert-Condition ([IO.Path]::GetFullPath([string]$metadata.configuration.outputDirectory) -eq [IO.Path]::GetFullPath($ExperimentOutput)) "Metadata output directory mismatch for $($Experiment.experimentId)."
    $metadataFileMap = [ordered]@{
        rawResults = 'raw-results.csv'; summaryResults = 'summary-results.csv'; requestOutcomes = 'request-outcomes.csv';
        scenarioSnapshots = 'scenario-snapshots.json'; metadata = 'metadata.json'
    }
    foreach ($entry in $metadataFileMap.GetEnumerator()) {
        $actualMetadataPath = [IO.Path]::GetFullPath([string]$metadata.files.($entry.Key))
        Assert-Condition ($actualMetadataPath -eq $paths[$entry.Value]) "Metadata path mismatch for $($entry.Key)."
    }

    $outcomeGroups = @{}
    foreach ($group in ($outcomes | Group-Object { Get-ResultKey $_ })) {
        Assert-Condition ($rawByKey.ContainsKey($group.Name)) "Request outcome has no matching raw result: $($group.Name)"
        $outcomeGroups[$group.Name] = @($group.Group)
    }

    foreach ($rawRow in $raw) {
        $key = Get-ResultKey $rawRow
        Assert-Condition ($outcomeGroups.ContainsKey($key)) "Missing request outcomes for raw row $key."
        $group = $outcomeGroups[$key]
        $accepted = @($group | Where-Object outcome -eq 'ACCEPTED').Count
        $rejected = @($group | Where-Object outcome -eq 'REJECTED').Count
        $unknown = @($group | Where-Object outcome -eq 'UNKNOWN').Count
        Assert-Condition ($accepted -eq [int]$rawRow.allocatedRequests) "Accepted count does not match raw result for $key."
        Assert-Condition ($rejected -eq [int]$rawRow.rejectedRequests) "Rejected count does not match raw result for $key."
        Assert-Condition (($accepted + $rejected + $unknown) -eq [int]$rawRow.requestCount) "Outcome count does not match request count for $key."
        Assert-Condition ((@($group | Where-Object scenarioFingerprint -ne $rawRow.scenarioFingerprint)).Count -eq 0) "Outcome fingerprint does not match raw result for $key."
        Assert-Condition ((@($group | Where-Object benchmarkRunId -ne $rawRow.benchmarkRunId)).Count -eq 0) "Outcome benchmarkRunId does not match raw result for $key."
        Assert-Condition ((@($group | Where-Object executionOrderPosition -ne $rawRow.executionOrderPosition)).Count -eq 0) "Outcome executionOrderPosition does not match raw result for $key."
    }

    $summaryKeys = @{}
    foreach ($summaryRow in $summary) {
        $summaryKey = Get-SummaryKey $summaryRow
        Assert-Condition (-not $summaryKeys.ContainsKey($summaryKey)) "Duplicate summary result tuple: $summaryKey"
        $summaryKeys[$summaryKey] = $true
        $matchingRaw = @($raw | Where-Object {
            $_.profile -eq $summaryRow.profile -and
            $_.seed -eq $summaryRow.seed -and
            $_.algorithm -eq $summaryRow.algorithm
        })
        Assert-Condition ($matchingRaw.Count -eq [int]$Experiment.measuredRuns) "Summary does not match measured raw runs."
        Assert-Condition ((@($matchingRaw | Where-Object scenarioFingerprint -ne $summaryRow.scenarioFingerprint)).Count -eq 0) "Summary fingerprint does not match raw results."
        Assert-Condition ([int]$summaryRow.resourceCount -eq [int]$matchingRaw[0].resourceCount) "Summary resourceCount mismatch for $(Get-SummaryKey $summaryRow)."
        Assert-Condition ([int]$summaryRow.requestCount -eq [int]$matchingRaw[0].requestCount) "Summary requestCount mismatch for $(Get-SummaryKey $summaryRow)."
        Assert-Condition ([int]$summaryRow.measuredRuns -eq $matchingRaw.Count) "Summary measuredRuns mismatch for $(Get-SummaryKey $summaryRow)."
        $times = @($matchingRaw | ForEach-Object { [double]$_.measuredExecutionTimeMs } | Sort-Object)
        $scores = @($matchingRaw | ForEach-Object { [int]$_.totalPriorityScore })
        $allocatedCounts = @($matchingRaw | ForEach-Object { [int]$_.allocatedRequests })
        $averageTime = [double](($times | Measure-Object -Average).Average)
        $medianTime = if (($times.Count % 2) -eq 1) { $times[[int][Math]::Floor($times.Count / 2)] } else { ($times[$times.Count / 2 - 1] + $times[$times.Count / 2]) / 2.0 }
        Assert-Close ([double]$summaryRow.averageMeasuredExecutionTimeMs) $averageTime "$(Get-SummaryKey $summaryRow)/averageMeasuredExecutionTimeMs"
        Assert-Close ([double]$summaryRow.medianMeasuredExecutionTimeMs) $medianTime "$(Get-SummaryKey $summaryRow)/medianMeasuredExecutionTimeMs"
        Assert-Close ([double]$summaryRow.minimumMeasuredExecutionTimeMs) ([double]($times | Measure-Object -Minimum).Minimum) "$(Get-SummaryKey $summaryRow)/minimumMeasuredExecutionTimeMs"
        Assert-Close ([double]$summaryRow.maximumMeasuredExecutionTimeMs) ([double]($times | Measure-Object -Maximum).Maximum) "$(Get-SummaryKey $summaryRow)/maximumMeasuredExecutionTimeMs"
        Assert-Close ([double]$summaryRow.averageTotalPriorityScore) ([double](($scores | Measure-Object -Average).Average)) "$(Get-SummaryKey $summaryRow)/averageTotalPriorityScore"
        Assert-Condition ([int]$summaryRow.bestTotalPriorityScore -eq [int](($scores | Measure-Object -Maximum).Maximum)) "Summary best score mismatch for $(Get-SummaryKey $summaryRow)."
        Assert-Condition ([int]$summaryRow.worstTotalPriorityScore -eq [int](($scores | Measure-Object -Minimum).Minimum)) "Summary worst score mismatch for $(Get-SummaryKey $summaryRow)."
        Assert-Close ([double]$summaryRow.averageAllocatedRequests) ([double](($allocatedCounts | Measure-Object -Average).Average)) "$(Get-SummaryKey $summaryRow)/averageAllocatedRequests"
        Assert-Condition ([int]$summaryRow.stoppedByLimitRuns -eq @($matchingRaw | Where-Object stoppedByLimit -eq 'true').Count) "Summary stoppedByLimitRuns mismatch for $(Get-SummaryKey $summaryRow)."
        Assert-Condition ([int]$summaryRow.optimalCpSatRuns -eq @($matchingRaw | Where-Object algorithmStatus -eq 'OPTIMAL').Count) "Summary optimalCpSatRuns mismatch for $(Get-SummaryKey $summaryRow)."
    }
    foreach ($profile in $expectedProfiles) {
        foreach ($seed in $expectedSeeds) {
            foreach ($algorithm in @('GREEDY', 'BACKTRACKING', 'CP_SAT')) {
                Assert-Condition ($summaryKeys.ContainsKey("$profile|$seed|$algorithm")) "Missing summary result tuple: $profile|$seed|$algorithm"
            }
        }
    }

    $snapshotRows = @($snapshots.scenarios)
    $snapshotKeys = @{}
    foreach ($snapshot in $snapshotRows) {
        $snapshotKeys["$($snapshot.profile)|$($snapshot.seed)|$($snapshot.scenarioFingerprint)"] = $true
    }

    $rawScenarioKeys = @($raw | ForEach-Object {
        "$($_.profile)|$($_.seed)|$($_.scenarioFingerprint)"
    } | Select-Object -Unique)
    Assert-Condition ($snapshotRows.Count -eq $rawScenarioKeys.Count) "Unexpected scenario snapshot count for $($Experiment.experimentId)."
    foreach ($key in $rawScenarioKeys) {
        Assert-Condition ($snapshotKeys.ContainsKey($key)) "Scenario snapshot is missing for $key."
    }

    if ($Experiment.experimentId -eq 'greedy-trap-validation') {
        Assert-Condition ($raw.Count -eq 18) 'Smoke raw row count must be 18.'
        Assert-Condition ($summary.Count -eq 3) 'Smoke summary row count must be 3.'
        Assert-Condition ($outcomes.Count -eq 36) 'Smoke request outcome row count must be 36.'
        Assert-Condition ($snapshotRows.Count -eq 1) 'Smoke scenario snapshot count must be 1.'
        Assert-Condition (@($raw.scenarioFingerprint | Select-Object -Unique).Count -eq 1) 'Smoke campaign must have one scenario fingerprint.'
        Assert-SmokeResult $raw $outcomes
    }

    return [pscustomobject]@{
        benchmarkRunId = [string]$metadata.benchmarkRunId
        rawRowCount = $raw.Count
        summaryRowCount = $summary.Count
        requestOutcomeRowCount = $outcomes.Count
        scenarioSnapshotCount = $snapshotRows.Count
        fingerprints = @($raw.scenarioFingerprint | Select-Object -Unique)
        paths = [pscustomobject]@{
            rawResults = $paths['raw-results.csv']
            summaryResults = $paths['summary-results.csv']
            requestOutcomes = $paths['request-outcomes.csv']
            scenarioSnapshots = $paths['scenario-snapshots.json']
            metadata = $paths['metadata.json']
        }
    }
}

function Export-CombinedCampaignCsv {
    param(
        [Parameter(Mandatory = $true)][object[]]$ExperimentRecords,
        [Parameter(Mandatory = $true)][string]$SourceProperty,
        [Parameter(Mandatory = $true)][string]$OutputPath
    )

    $completed = @($ExperimentRecords | Where-Object status -eq 'COMPLETED')
    Assert-Condition ($completed.Count -gt 0) 'No completed experiments are available for combined campaign CSV output.'
    $expectedHeader = $null
    $expectedRows = 0
    $rowCountProperty = @{
        rawResults = 'rawRowCount'; summaryResults = 'summaryRowCount'; requestOutcomes = 'requestOutcomeRowCount'
    }[$SourceProperty]

    $combinedObjects = New-Object 'System.Collections.Generic.List[object]'
    foreach ($record in $completed) {
        $sourcePath = $record.files.$SourceProperty
        $sourceRows = @(Import-Csv -LiteralPath $sourcePath)
        Assert-Condition ($sourceRows.Count -gt 0) "Combined CSV source is empty: $sourcePath"
        $header = @($sourceRows[0].PSObject.Properties.Name)
        if ($null -eq $expectedHeader) {
            $expectedHeader = $header
        } else {
            Assert-Condition (($header -join ',') -eq ($expectedHeader -join ',')) "Combined CSV header mismatch: $sourcePath"
        }
        $expectedRows += [int]$record.$rowCountProperty
        foreach ($sourceRow in $sourceRows) {
            $combined = [ordered]@{ experimentId = $record.experimentId; resultDirectory = $record.outputDirectory }
            foreach ($property in $sourceRow.PSObject.Properties) { $combined[$property.Name] = $property.Value }
            $combinedObjects.Add([pscustomobject]$combined)
        }
    }
    $combinedObjects | Export-Csv -LiteralPath $OutputPath -NoTypeInformation -Encoding UTF8

    $combinedRows = @(Import-Csv -LiteralPath $OutputPath)
    Assert-Condition ($combinedRows.Count -eq $expectedRows) "Combined CSV row count mismatch for $SourceProperty."
    $combinedHeader = @($combinedRows[0].PSObject.Properties.Name)
    Assert-Condition ($combinedHeader[0] -eq 'experimentId' -and $combinedHeader[1] -eq 'resultDirectory') "Combined CSV prefix columns are invalid: $OutputPath"
    Assert-Condition (($combinedHeader[2..($combinedHeader.Count - 1)] -join ',') -eq ($expectedHeader -join ',')) "Combined CSV did not preserve the original header: $OutputPath"
}

function New-ExperimentRecord {
    param(
        [Parameter(Mandatory = $true)]$Experiment,
        [Parameter(Mandatory = $true)][string]$ExperimentOutput,
        [Parameter(Mandatory = $true)][string]$Command
    )

    return [pscustomobject][ordered]@{
        experimentId = [string]$Experiment.experimentId
        profileArgument = [string]$Experiment.profiles
        seedArgument = [string]$Experiment.seeds
        warmups = [int]$Experiment.warmups
        measuredRuns = [int]$Experiment.measuredRuns
        backtrackingTimeLimitMs = [long]$Experiment.backtrackingTimeLimitMs
        cpSatTimeLimitSeconds = [double]$Experiment.cpSatTimeLimitSeconds
        scaleResources = $Experiment.scaleResources
        scaleRequests = $Experiment.scaleRequests
        scaleResourceTypes = $Experiment.scaleResourceTypes
        outputDirectory = $ExperimentOutput
        mavenCommand = $Command
        benchmarkExitCode = $null
        validationExitCode = $null
        status = 'PENDING'
        errorMessage = $null
        benchmarkRunId = $null
        rawRowCount = 0
        summaryRowCount = 0
        requestOutcomeRowCount = 0
        scenarioSnapshotCount = 0
        fingerprints = @()
        files = $null
    }
}

$manifest = $null
$manifestPath = $null
try {
    $repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
    $planPath = Join-Path $PSScriptRoot 'benchmark-campaign-plan.json'
    Assert-Condition (Test-Path -LiteralPath (Join-Path $repositoryRoot 'pom.xml') -PathType Leaf) 'Root pom.xml was not found.'
    Assert-Condition (Test-Path -LiteralPath (Join-Path $repositoryRoot 'allocation-core') -PathType Container) 'allocation-core module was not found.'
    Assert-Condition (Test-Path -LiteralPath $planPath -PathType Leaf) 'Campaign plan was not found.'

    $gitCommand = (Get-Command git -ErrorAction Stop).Source
    $mavenInfo = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($null -eq $mavenInfo) { $mavenInfo = Get-Command mvn -ErrorAction Stop }
    $mavenCommand = $mavenInfo.Source
    $javaCommand = (Get-Command java -ErrorAction Stop).Source
    $gitRoot = Invoke-CapturedCommand $gitCommand @('-C', $repositoryRoot, 'rev-parse', '--show-toplevel')
    Assert-Condition ([IO.Path]::GetFullPath($gitRoot) -eq $repositoryRoot) 'The script is not located inside the AllocationLibrary repository.'
    $headCommit = Invoke-CapturedCommand $gitCommand @('-C', $repositoryRoot, 'rev-parse', 'HEAD')
    $requestedCommit = if ([string]::IsNullOrWhiteSpace($SourceCommit)) { $headCommit } else { $SourceCommit.Trim() }
    Assert-Condition ($requestedCommit -match '^[0-9a-fA-F]{7,64}$') 'SourceCommit must be a valid Git commit hash.'
    $resolvedCommit = Invoke-CapturedCommand $gitCommand @('-C', $repositoryRoot, 'rev-parse', '--verify', "$requestedCommit^{commit}")
    Assert-Condition ($resolvedCommit -eq $headCommit) 'SourceCommit must match the current HEAD commit.'
    $shortCommit = $resolvedCommit.Substring(0, [Math]::Min(12, $resolvedCommit.Length))
    $workingTreeStatus = Invoke-CapturedCommand $gitCommand @('-C', $repositoryRoot, 'status', '--porcelain', '--untracked-files=normal')
    $workingTreeClean = [string]::IsNullOrWhiteSpace($workingTreeStatus)
    if (-not $AllowDirtyWorkingTree) {
        Assert-Condition $workingTreeClean 'Git working tree must be clean. Use -AllowDirtyWorkingTree only for intentional local verification.'
    }

    if ([string]::IsNullOrWhiteSpace($OutputRoot)) { $OutputRoot = Join-Path 'benchmark-results/campaigns' $shortCommit }
    $resolvedOutputRoot = if ([IO.Path]::IsPathRooted($OutputRoot)) { [IO.Path]::GetFullPath($OutputRoot) } else { [IO.Path]::GetFullPath((Join-Path $repositoryRoot $OutputRoot)) }
    if ((Test-Path -LiteralPath $resolvedOutputRoot) -and -not $Overwrite) {
        throw "Campaign output already exists: $resolvedOutputRoot. Choose another -OutputRoot or use -Overwrite."
    }

    $experiments = @(Get-CampaignExperiments $planPath $Preset)
    Test-CampaignPlan $experiments $resolvedOutputRoot $resolvedCommit $Overwrite.IsPresent
    $plannedExperiments = New-Object 'System.Collections.Generic.List[object]'
    foreach ($experiment in $experiments) {
        $experimentOutput = [IO.Path]::GetFullPath((Join-Path $resolvedOutputRoot $experiment.experimentId))
        $benchmarkArguments = New-BenchmarkArguments $experiment $experimentOutput $Overwrite.IsPresent
        $plannedExperiments.Add([pscustomobject]@{
            experimentId = $experiment.experimentId; outputDirectory = $experimentOutput
            measuredRuns = $experiment.measuredRuns; expectedFiles = $ExpectedOutputFiles
            mavenCommand = Format-MavenBenchmarkCommand $resolvedCommit $benchmarkArguments
        })
    }

    if ($DryRun) {
        $dryRunPlan = [ordered]@{
            preset = $Preset; sourceCommit = $resolvedCommit; outputRoot = $resolvedOutputRoot
            workingTreeClean = $workingTreeClean
            testCommand = if ($SkipTests) { $null } else { 'mvn -B -ntp test' }
            packageCommand = 'mvn -B -ntp -pl allocation-core -am package -DskipTests'
            experiments = $plannedExperiments
        }
        Write-Host ($dryRunPlan | ConvertTo-Json -Depth 8)
        exit 0
    }

    $javaVersionOutput = Invoke-CapturedCommand $javaCommand @('--version')
    $mavenVersionOutput = Invoke-CapturedCommand $mavenCommand @('--version')
    $ansiPattern = [char]27 + '\[[0-?]*[ -/]*[@-~]'
    $javaVersion = ((($javaVersionOutput -split "`r?`n")[0] -replace $ansiPattern, '') -replace '"', '')
    $mavenVersion = (($mavenVersionOutput -split "`r?`n")[0] -replace $ansiPattern, '')

    New-Item -ItemType Directory -Path $resolvedOutputRoot -Force | Out-Null
    $manifestPath = Join-Path $resolvedOutputRoot 'campaign-manifest.json'
    $records = New-Object 'System.Collections.Generic.List[object]'
    foreach ($experiment in $experiments) {
        $experimentOutput = [IO.Path]::GetFullPath((Join-Path $resolvedOutputRoot $experiment.experimentId))
        $arguments = New-BenchmarkArguments $experiment $experimentOutput $Overwrite.IsPresent
        $records.Add((New-ExperimentRecord $experiment $experimentOutput (Format-MavenBenchmarkCommand $resolvedCommit $arguments)))
    }
    $manifest = [ordered]@{
        schemaVersion = 2; sourceCommit = $resolvedCommit; sourceCommitShort = $shortCommit
        preset = $Preset; startedAt = [DateTime]::UtcNow.ToString('o'); completedAt = $null
        javaVersion = $javaVersion; mavenVersion = $mavenVersion
        operatingSystem = [Runtime.InteropServices.RuntimeInformation]::OSDescription
        workingTreeClean = $workingTreeClean; repositoryRoot = $repositoryRoot
        campaignStatus = 'RUNNING'; errorMessage = $null; experiments = $records; combinedFiles = $null
    }
    Write-CampaignManifest $manifest $manifestPath

    Push-Location $repositoryRoot
    try {
        if (-not $SkipTests) { Invoke-RequiredCommand $mavenCommand @('-B', '-ntp', 'test') 'Running Maven tests' }
        Invoke-RequiredCommand $mavenCommand @('-B', '-ntp', '-pl', 'allocation-core', '-am', 'package', '-DskipTests') 'Packaging allocation-core without rerunning tests'

        foreach ($experiment in $experiments) {
            $record = @($manifest.experiments | Where-Object experimentId -eq $experiment.experimentId)[0]
            $record.status = 'RUNNING'
            $record.errorMessage = $null
            Write-CampaignManifest $manifest $manifestPath
            $benchmarkArguments = New-BenchmarkArguments $experiment $record.outputDirectory $Overwrite.IsPresent
            $execArguments = @('-pl', 'allocation-core', 'exec:java', "-Dbenchmark.sourceCommit=$resolvedCommit", "-Dexec.args=$(Join-ExecArguments $benchmarkArguments)")
            Write-Host "`n==> Running experiment $($experiment.experimentId)"
            & $mavenCommand @execArguments
            $record.benchmarkExitCode = $LASTEXITCODE
            if ($record.benchmarkExitCode -ne 0) {
                $record.status = 'FAILED'; $record.errorMessage = "Benchmark command failed with exit code $($record.benchmarkExitCode)."
                throw $record.errorMessage
            }

            try {
                $validation = Test-ExperimentOutput $experiment $record.outputDirectory $resolvedCommit
                $record.validationExitCode = 0; $record.status = 'COMPLETED'
                $record.benchmarkRunId = $validation.benchmarkRunId; $record.rawRowCount = $validation.rawRowCount
                $record.summaryRowCount = $validation.summaryRowCount; $record.requestOutcomeRowCount = $validation.requestOutcomeRowCount
                $record.scenarioSnapshotCount = $validation.scenarioSnapshotCount; $record.fingerprints = $validation.fingerprints
                $record.files = $validation.paths
                Write-CampaignManifest $manifest $manifestPath
            } catch {
                $record.validationExitCode = 1; $record.status = 'FAILED'; $record.errorMessage = $_.Exception.Message
                throw
            }
        }

        $rawCombinedPath = [IO.Path]::GetFullPath((Join-Path $resolvedOutputRoot 'campaign-raw-results.csv'))
        $summaryCombinedPath = [IO.Path]::GetFullPath((Join-Path $resolvedOutputRoot 'campaign-summary.csv'))
        $outcomeCombinedPath = [IO.Path]::GetFullPath((Join-Path $resolvedOutputRoot 'campaign-request-outcomes.csv'))
        Export-CombinedCampaignCsv $manifest.experiments 'rawResults' $rawCombinedPath
        Export-CombinedCampaignCsv $manifest.experiments 'summaryResults' $summaryCombinedPath
        Export-CombinedCampaignCsv $manifest.experiments 'requestOutcomes' $outcomeCombinedPath
        $manifest.combinedFiles = [pscustomobject]@{ rawResults = $rawCombinedPath; summaryResults = $summaryCombinedPath; requestOutcomes = $outcomeCombinedPath }
        $manifest.campaignStatus = 'COMPLETED'; $manifest.errorMessage = $null; $manifest.completedAt = [DateTime]::UtcNow.ToString('o')
        Write-CampaignManifest $manifest $manifestPath
        Write-Host "`nCampaign completed successfully."
        Write-Host "Manifest: $manifestPath"
        Write-Host "Combined raw results: $rawCombinedPath"
        Write-Host "Combined summary: $summaryCombinedPath"
        Write-Host "Combined request outcomes: $outcomeCombinedPath"
    } finally {
        Pop-Location
    }
} catch {
    if ($null -ne $manifest -and $null -ne $manifestPath -and $manifest.campaignStatus -ne 'COMPLETED') {
        $manifest.campaignStatus = 'FAILED'
        $manifest.errorMessage = $_.Exception.Message
        $manifest.completedAt = [DateTime]::UtcNow.ToString('o')
        Write-CampaignManifest $manifest $manifestPath
    }
    Write-Error $_.Exception.Message
    exit 1
}
