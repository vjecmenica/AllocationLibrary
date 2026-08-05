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
    foreach ($experiment in $plan.experiments) {
        $experimentsById[$experiment.experimentId] = $experiment
    }

    $selected = @()
    foreach ($experimentId in $presetProperty.Value) {
        Assert-Condition ($experimentsById.ContainsKey($experimentId)) "Unknown experiment ID in campaign plan: $experimentId"
        $selected += $experimentsById[$experimentId]
    }

    return @($selected)
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

function Format-MavenBenchmarkCommand {
    param(
        [Parameter(Mandatory = $true)][string]$Commit,
        [Parameter(Mandatory = $true)][string[]]$BenchmarkArguments
    )

    $cliArguments = $BenchmarkArguments -join ' '
    return "mvn -pl allocation-core exec:java `"-Dbenchmark.sourceCommit=$Commit`" `"-Dexec.args=$cliArguments`""
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

    $outcomeGroups = @{}
    foreach ($group in ($outcomes | Group-Object { Get-ResultKey $_ })) {
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
    }

    foreach ($summaryRow in $summary) {
        $matchingRaw = @($raw | Where-Object {
            $_.profile -eq $summaryRow.profile -and
            $_.seed -eq $summaryRow.seed -and
            $_.algorithm -eq $summaryRow.algorithm
        })
        Assert-Condition ($matchingRaw.Count -eq [int]$Experiment.measuredRuns) "Summary does not match measured raw runs."
        Assert-Condition ((@($matchingRaw | Where-Object scenarioFingerprint -ne $summaryRow.scenarioFingerprint)).Count -eq 0) "Summary fingerprint does not match raw results."
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

    $combinedRows = @()
    foreach ($record in $ExperimentRecords) {
        $sourcePath = $record.files.$SourceProperty
        foreach ($sourceRow in @(Import-Csv -LiteralPath $sourcePath)) {
            $combined = [ordered]@{
                experimentId = $record.experimentId
                resultDirectory = $record.outputDirectory
            }
            foreach ($property in $sourceRow.PSObject.Properties) {
                $combined[$property.Name] = $property.Value
            }
            $combinedRows += [pscustomobject]$combined
        }
    }

    $combinedRows | Export-Csv -LiteralPath $OutputPath -NoTypeInformation -Encoding UTF8
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
        exitCode = $null
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

try {
    $repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
    $planPath = Join-Path $PSScriptRoot 'benchmark-campaign-plan.json'
    Assert-Condition (Test-Path -LiteralPath (Join-Path $repositoryRoot 'pom.xml') -PathType Leaf) 'Root pom.xml was not found.'
    Assert-Condition (Test-Path -LiteralPath (Join-Path $repositoryRoot 'allocation-core') -PathType Container) 'allocation-core module was not found.'
    Assert-Condition (Test-Path -LiteralPath $planPath -PathType Leaf) 'Campaign plan was not found.'

    $gitCommand = (Get-Command git -ErrorAction Stop).Source
    $mavenCommand = (Get-Command mvn -ErrorAction Stop).Source
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

    if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
        $OutputRoot = Join-Path 'benchmark-results/campaigns' $shortCommit
    }
    $resolvedOutputRoot = if ([IO.Path]::IsPathRooted($OutputRoot)) {
        [IO.Path]::GetFullPath($OutputRoot)
    } else {
        [IO.Path]::GetFullPath((Join-Path $repositoryRoot $OutputRoot))
    }

    if ((Test-Path -LiteralPath $resolvedOutputRoot) -and -not $Overwrite) {
        throw "Campaign output already exists: $resolvedOutputRoot. Choose another -OutputRoot or use -Overwrite."
    }

    $experiments = @(Get-CampaignExperiments $planPath $Preset)
    Test-CampaignPlan $experiments $resolvedOutputRoot $resolvedCommit $Overwrite.IsPresent

    $plannedExperiments = @()
    foreach ($experiment in $experiments) {
        $experimentOutput = [IO.Path]::GetFullPath((Join-Path $resolvedOutputRoot $experiment.experimentId))
        $benchmarkArguments = New-BenchmarkArguments $experiment $experimentOutput $Overwrite.IsPresent
        $plannedExperiments += [pscustomobject]@{
            experimentId = $experiment.experimentId
            outputDirectory = $experimentOutput
            measuredRuns = $experiment.measuredRuns
            expectedFiles = $ExpectedOutputFiles
            mavenCommand = Format-MavenBenchmarkCommand $resolvedCommit $benchmarkArguments
        }
    }

    if ($DryRun) {
        $dryRunPlan = [ordered]@{
            preset = $Preset
            sourceCommit = $resolvedCommit
            outputRoot = $resolvedOutputRoot
            workingTreeClean = $workingTreeClean
            testCommand = if ($SkipTests) { $null } else { 'mvn test' }
            packageCommand = 'mvn -pl allocation-core -am package'
            experiments = $plannedExperiments
        }
        Write-Host ($dryRunPlan | ConvertTo-Json -Depth 8)
        exit 0
    }

    Push-Location $repositoryRoot
    try {
        if (-not $SkipTests) {
            Invoke-RequiredCommand $mavenCommand @('test') 'Running Maven tests'
        }
        Invoke-RequiredCommand $mavenCommand @('-pl', 'allocation-core', '-am', 'package') 'Packaging allocation-core'

        $javaVersionOutput = & cmd.exe /d /c "java -version 2>&1"
        Assert-Condition ($LASTEXITCODE -eq 0) 'Java version could not be determined.'
        $mavenVersionOutput = & cmd.exe /d /c "`"$mavenCommand`" --version 2>&1"
        Assert-Condition ($LASTEXITCODE -eq 0) 'Maven version could not be determined.'

        New-Item -ItemType Directory -Path $resolvedOutputRoot -Force | Out-Null
        $manifestPath = Join-Path $resolvedOutputRoot 'campaign-manifest.json'
        $manifest = [ordered]@{
            schemaVersion = 1
            sourceCommit = $resolvedCommit
            sourceCommitShort = $shortCommit
            preset = $Preset
            generatedAt = [DateTime]::UtcNow.ToString('o')
            javaVersion = (($javaVersionOutput | Select-Object -First 1) -replace '"', '')
            mavenVersion = (($mavenVersionOutput | Select-Object -First 1) -replace ([char]27 + '\[[0-9;]*m'), '')
            operatingSystem = [Runtime.InteropServices.RuntimeInformation]::OSDescription
            workingTreeClean = $workingTreeClean
            repositoryRoot = $repositoryRoot
            campaignStatus = 'RUNNING'
            experiments = @()
            combinedFiles = $null
        }
        Write-CampaignManifest $manifest $manifestPath

        foreach ($experiment in $experiments) {
            $experimentOutput = [IO.Path]::GetFullPath((Join-Path $resolvedOutputRoot $experiment.experimentId))
            $benchmarkArguments = New-BenchmarkArguments $experiment $experimentOutput $Overwrite.IsPresent
            $commandText = Format-MavenBenchmarkCommand $resolvedCommit $benchmarkArguments
            $record = New-ExperimentRecord $experiment $experimentOutput $commandText
            $manifest.experiments += $record
            $record.status = 'RUNNING'
            Write-CampaignManifest $manifest $manifestPath

            try {
                $execArguments = @(
                    '-pl', 'allocation-core', 'exec:java',
                    "-Dbenchmark.sourceCommit=$resolvedCommit",
                    "-Dexec.args=$($benchmarkArguments -join ' ')"
                )
                Write-Host "`n==> Running experiment $($experiment.experimentId)"
                & $mavenCommand @execArguments
                $record.exitCode = $LASTEXITCODE
                if ($record.exitCode -ne 0) {
                    throw "Benchmark command failed with exit code $($record.exitCode)."
                }

                $validation = Test-ExperimentOutput $experiment $experimentOutput $resolvedCommit
                $record.status = 'COMPLETED'
                $record.benchmarkRunId = $validation.benchmarkRunId
                $record.rawRowCount = $validation.rawRowCount
                $record.summaryRowCount = $validation.summaryRowCount
                $record.requestOutcomeRowCount = $validation.requestOutcomeRowCount
                $record.scenarioSnapshotCount = $validation.scenarioSnapshotCount
                $record.fingerprints = $validation.fingerprints
                $record.files = $validation.paths
                Write-CampaignManifest $manifest $manifestPath
            } catch {
                if ($null -eq $record.exitCode) {
                    $record.exitCode = 1
                }
                $record.status = 'FAILED'
                $record.errorMessage = $_.Exception.Message
                $manifest.campaignStatus = 'FAILED'
                Write-CampaignManifest $manifest $manifestPath
                throw
            }
        }

        $rawCombinedPath = Join-Path $resolvedOutputRoot 'campaign-raw-results.csv'
        $summaryCombinedPath = Join-Path $resolvedOutputRoot 'campaign-summary.csv'
        $outcomeCombinedPath = Join-Path $resolvedOutputRoot 'campaign-request-outcomes.csv'
        Export-CombinedCampaignCsv $manifest.experiments 'rawResults' $rawCombinedPath
        Export-CombinedCampaignCsv $manifest.experiments 'summaryResults' $summaryCombinedPath
        Export-CombinedCampaignCsv $manifest.experiments 'requestOutcomes' $outcomeCombinedPath

        $manifest.combinedFiles = [pscustomobject]@{
            rawResults = [IO.Path]::GetFullPath($rawCombinedPath)
            summaryResults = [IO.Path]::GetFullPath($summaryCombinedPath)
            requestOutcomes = [IO.Path]::GetFullPath($outcomeCombinedPath)
        }
        $manifest.campaignStatus = 'COMPLETED'
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
    Write-Error $_.Exception.Message
    exit 1
}
