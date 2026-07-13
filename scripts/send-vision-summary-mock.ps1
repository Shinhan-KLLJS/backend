param(
    [string]$QueueUrl = $env:VISION_SQS_QUEUE_URL,
    [string]$Region = $(if ($env:VISION_SQS_REGION) { $env:VISION_SQS_REGION } else { "ap-northeast-2" }),
    [string]$BoardId = "board_gangnam_01",
    [string]$DeviceId = "adscope-cam-01",
    [int]$Count = 120,
    [decimal]$IntervalSec = 5.425,
    [int]$SendDelaySec = 5,
    [int]$SeqStart = 1,
    [string]$StartTimestamp = "",
    [switch]$UseSampleZeroCounts
)

if (-not $QueueUrl) {
    throw "QueueUrl is required. Set VISION_SQS_QUEUE_URL or pass -QueueUrl."
}

$baseTimestamp = $null
if ($StartTimestamp) {
    $baseTimestamp = [DateTimeOffset]::Parse($StartTimestamp).ToUniversalTime()
}

function New-AgeBuckets {
    param([int]$Total)

    $under10 = [math]::Floor($Total * 0.05)
    $age10s = [math]::Floor($Total * 0.08)
    $age20s = [math]::Floor($Total * 0.25)
    $age30s = [math]::Floor($Total * 0.25)
    $age40s = [math]::Floor($Total * 0.18)
    $age50s = [math]::Floor($Total * 0.12)
    $assigned = $under10 + $age10s + $age20s + $age30s + $age40s + $age50s
    $age60plus = [math]::Max(0, $Total - $assigned)

    [ordered]@{
        under10 = [int]$under10
        "10s" = [int]$age10s
        "20s" = [int]$age20s
        "30s" = [int]$age30s
        "40s" = [int]$age40s
        "50s" = [int]$age50s
        "60plus" = [int]$age60plus
    }
}

function New-Demographics {
    param([int]$Total)

    $male = [math]::Floor($Total * 0.52)
    $female = [math]::Max(0, $Total - $male)

    [ordered]@{
        male = [ordered]@{
            count = [int]$male
            age = New-AgeBuckets -Total ([int]$male)
        }
        female = [ordered]@{
            count = [int]$female
            age = New-AgeBuckets -Total ([int]$female)
        }
    }
}

function New-DwellDistribution {
    param([int]$Total)

    $oneToUnder2s = [math]::Floor($Total * 0.25)
    $twoToUnder3s = [math]::Floor($Total * 0.35)
    $threeToUnder4s = [math]::Floor($Total * 0.25)
    $assigned = $oneToUnder2s + $twoToUnder3s + $threeToUnder4s
    $fourSAndOver = [math]::Max(0, $Total - $assigned)

    [ordered]@{
        "1_to_under_2s" = [int]$oneToUnder2s
        "2_to_under_3s" = [int]$twoToUnder3s
        "3_to_under_4s" = [int]$threeToUnder4s
        "4s_and_over" = [int]$fourSAndOver
    }
}

for ($i = 0; $i -lt $Count; $i++) {
    $seq = $SeqStart + $i
    if ($UseSampleZeroCounts) {
        $otsCount = 0
        $ltsCount = 0
        $avgDwellSec = 0.0
    } else {
        $otsCount = 80 + (Get-Random -Minimum 0 -Maximum 80)
        $ltsCount = [math]::Min($otsCount, 20 + (Get-Random -Minimum 0 -Maximum 45))
        $avgDwellSec = [math]::Round((1.2 + (Get-Random -Minimum 0 -Maximum 28) / 10.0), 3)
    }
    $dwellSumSec = [math]::Round($avgDwellSec * $ltsCount, 3)
    $eventTimestamp = if ($baseTimestamp) {
        $baseTimestamp.AddSeconds($i * [double]$IntervalSec)
    } else {
        [DateTimeOffset]::UtcNow
    }

    $message = [ordered]@{
        device_id = $DeviceId
        board_id = $BoardId
        seq = $seq
        timestamp = $eventTimestamp.ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
        interval_sec = $IntervalSec
        ots_count = [int]$otsCount
        lts_count = [int]$ltsCount
        ots_demographics = New-Demographics -Total ([int]$otsCount)
        lts_demographics = New-Demographics -Total ([int]$ltsCount)
        attention = [ordered]@{
            avg_dwell_sec = $avgDwellSec
            dwell_sum_sec = $dwellSumSec
            dwell_distribution = New-DwellDistribution -Total ([int]$ltsCount)
        }
    }

    $bodyFile = New-TemporaryFile
    try {
        $message | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $bodyFile.FullName -Encoding utf8NoBOM
        aws sqs send-message `
            --queue-url $QueueUrl `
            --message-body "file://$($bodyFile.FullName)" `
            --region $Region | Out-Null
        Write-Host "Sent seq=$seq ots=$otsCount lts=$ltsCount timestamp=$($message.timestamp)"
    } finally {
        Remove-Item -LiteralPath $bodyFile.FullName -Force -ErrorAction SilentlyContinue
    }

    if ($i -lt ($Count - 1)) {
        Start-Sleep -Seconds $SendDelaySec
    }
}