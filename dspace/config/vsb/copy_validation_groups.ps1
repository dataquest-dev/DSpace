# Copy validation groups from FBI form to all other evyuka forms
# This preserves encoding by using the FBI form as a template

$sourceFile = "C:\dspace-be\dspace\config\vsb\evyuka_form_FBI.xml"
$targetFiles = @(
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

# Read the FBI form to get the validation group patterns
$fbiContent = [System.IO.File]::ReadAllText($sourceFile, [System.Text.Encoding]::UTF8)

foreach ($file in $targetFiles) {
    $filePath = "C:\dspace-be\dspace\config\vsb\$file"
    
    if (Test-Path $filePath) {
        Write-Host "Processing $file..." -ForegroundColor Green
        
        # Read target file with UTF-8 encoding
        $content = [System.IO.File]::ReadAllText($filePath, [System.Text.Encoding]::UTF8)
        
        # Add validation-group to subject.version fields (maintaining line breaks and spacing)
        $content = $content -replace '(<hint>Vyberte kódy všech verzí předmětu[^<]*</hint>\s*)</field>', '$1           <validation-group>evyuka-subject-codes</validation-group>$2         </field>'
        
        # Add validation-group to subject fields (maintaining line breaks and spacing) 
        $content = $content -replace '(<hint>Vyberte kódy všech předmětů[^<]*</hint>\s*)</field>', '$1           <validation-group>evyuka-subject-codes</validation-group>$2         </field>'
        
        # Add validation-group to discipline fields (maintaining line breaks and spacing)
        $content = $content -replace '(<hint>Vyberte kódy všech studijních oborů[^<]*</hint>\s*)</field>', '$1           <validation-group>evyuka-discipline-programme-codes</validation-group>$2         </field>'
        
        # Add validation-group to programme fields (maintaining line breaks and spacing)
        $content = $content -replace '(<hint>Vyberte kódy všech studijních programů[^<]*</hint>\s*)</field>', '$1           <validation-group>evyuka-discipline-programme-codes</validation-group>$2         </field>'
        
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
Write-Host "Czech encoding preserved from template." -ForegroundColor Green