# Fix validation groups in evyuka forms with proper UTF-8 encoding
# This script adds validation-group tags while preserving Czech character encoding

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
        
        # Add validation-group for subject version field
        $content = $content -replace '(<dc-element>evyuka\.subject\.version</dc-element>\s*<dc-qualifier></dc-qualifier>(?:[^<]*<[^>]+>[^<]*)*?</field>)', { 
            param($match) 
            $fieldContent = $match.Groups[1].Value
            if ($fieldContent -notmatch '<validation-group>') {
                $fieldContent -replace '</field>', "           <validation-group>evyuka-subject-codes</validation-group>`r`n         </field>"
            } else {
                $fieldContent
            }
        }
        
        # Add validation-group for subject field
        $content = $content -replace '(<dc-element>evyuka\.subject</dc-element>\s*<dc-qualifier></dc-qualifier>(?:[^<]*<[^>]+>[^<]*)*?</field>)', { 
            param($match) 
            $fieldContent = $match.Groups[1].Value
            if ($fieldContent -notmatch '<validation-group>') {
                $fieldContent -replace '</field>', "           <validation-group>evyuka-subject-codes</validation-group>`r`n         </field>"
            } else {
                $fieldContent
            }
        }
        
        # Add validation-group for discipline field
        $content = $content -replace '(<dc-element>evyuka\.discipline</dc-element>\s*<dc-qualifier></dc-qualifier>(?:[^<]*<[^>]+>[^<]*)*?</field>)', { 
            param($match) 
            $fieldContent = $match.Groups[1].Value
            if ($fieldContent -notmatch '<validation-group>') {
                $fieldContent -replace '</field>', "           <validation-group>evyuka-discipline-programme-codes</validation-group>`r`n         </field>"
            } else {
                $fieldContent
            }
        }
        
        # Add validation-group for programme field  
        $content = $content -replace '(<dc-element>evyuka\.programme</dc-element>\s*<dc-qualifier></dc-qualifier>(?:[^<]*<[^>]+>[^<]*)*?</field>)', { 
            param($match) 
            $fieldContent = $match.Groups[1].Value
            if ($fieldContent -notmatch '<validation-group>') {
                $fieldContent -replace '</field>', "           <validation-group>evyuka-discipline-programme-codes</validation-group>`r`n         </field>"
            } else {
                $fieldContent
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
Write-Host "All evyuka forms updated with proper UTF-8 encoding!" -ForegroundColor Green
Write-Host "Czech characters should now be preserved correctly." -ForegroundColor Green