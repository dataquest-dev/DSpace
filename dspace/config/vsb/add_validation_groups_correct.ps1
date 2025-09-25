# Add validation groups to evyuka fields with proper UTF-8 encoding
# This script correctly matches the XML structure and preserves Czech characters

$formFiles = @(
    "evyuka_form_FBI.xml",
    "evyuka_form_EKF.xml", 
    "evyuka_form_FEI.xml",
    "evyuka_form_HGF.xml",
    "evyuka_form_FMT.xml",
    "evyuka_form_FS.xml",
    "evyuka_form_USP.xml",
    "evyuka_form_AUD.xml",
    "evyuka_form_9270.xml",
    "evyuka_form_FAST.xml"
)

foreach ($file in $formFiles) {
    $filePath = "C:\dspace-be\dspace\config\vsb\$file"
    
    if (Test-Path $filePath) {
        Write-Host "Processing $file with UTF-8 encoding preservation..." -ForegroundColor Green
        
        # Read file with UTF-8 encoding
        $content = [System.IO.File]::ReadAllText($filePath, [System.Text.Encoding]::UTF8)
        
        # Pattern 1: subject.version field (Kód verze předmětu)
        $pattern1 = '(<field>\s*<dc-schema>evyuka</dc-schema>\s*<dc-element>subject</dc-element>\s*<dc-qualifier>version</dc-qualifier>[\s\S]*?)</field>'
        $content = $content -replace $pattern1, { 
            param($match) 
            $fieldContent = $match.Groups[1].Value
            if ($fieldContent -notmatch '<validation-group>') {
                $fieldContent + "           <validation-group>evyuka-subject-codes</validation-group>`r`n         </field>"
            } else {
                $match.Groups[0].Value
            }
        }
        
        # Pattern 2: subject field without qualifier (Kód předmětu)  
        $pattern2 = '(<field>\s*<dc-schema>evyuka</dc-schema>\s*<dc-element>subject</dc-element>\s*<dc-qualifier></dc-qualifier>[\s\S]*?)</field>'
        $content = $content -replace $pattern2, { 
            param($match) 
            $fieldContent = $match.Groups[1].Value
            if ($fieldContent -notmatch '<validation-group>') {
                $fieldContent + "           <validation-group>evyuka-subject-codes</validation-group>`r`n         </field>"
            } else {
                $match.Groups[0].Value
            }
        }
        
        # Pattern 3: discipline field (Kód studijního oboru)
        $pattern3 = '(<field>\s*<dc-schema>evyuka</dc-schema>\s*<dc-element>discipline</dc-element>\s*<dc-qualifier></dc-qualifier>[\s\S]*?)</field>'
        $content = $content -replace $pattern3, { 
            param($match) 
            $fieldContent = $match.Groups[1].Value
            if ($fieldContent -notmatch '<validation-group>') {
                $fieldContent + "           <validation-group>evyuka-discipline-programme-codes</validation-group>`r`n         </field>"
            } else {
                $match.Groups[0].Value
            }
        }
        
        # Pattern 4: programme field (Kód studijního programu)
        $pattern4 = '(<field>\s*<dc-schema>evyuka</dc-schema>\s*<dc-element>programme</dc-element>\s*<dc-qualifier></dc-qualifier>[\s\S]*?)</field>'
        $content = $content -replace $pattern4, { 
            param($match) 
            $fieldContent = $match.Groups[1].Value
            if ($fieldContent -notmatch '<validation-group>') {
                $fieldContent + "           <validation-group>evyuka-discipline-programme-codes</validation-group>`r`n         </field>"
            } else {
                $match.Groups[0].Value
            }
        }
        
        # Write back to file with UTF-8 encoding (without BOM)
        $utf8NoBomEncoding = New-Object System.Text.UTF8Encoding $false
        [System.IO.File]::WriteAllText($filePath, $content, $utf8NoBomEncoding)
        
        Write-Host "Updated $file successfully" -ForegroundColor Cyan
    } else {
        Write-Host "File not found: $file" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "All evyuka forms updated with validation groups!" -ForegroundColor Green
Write-Host "Czech characters preserved with UTF-8 encoding." -ForegroundColor Green