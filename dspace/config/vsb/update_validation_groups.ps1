# Update validation groups in all evyuka forms
# This script updates validation-group tags to split them into two groups:
# - evyuka-subject-codes (for subject.version and subject fields)
# - evyuka-discipline-programme-codes (for discipline and programme fields)

$formFiles = @(
    "evyuka_form_FBI.xml",
    "evyuka_form_EKF.xml", 
    "evyuka_form_FEI.xml",
    "evyuka_form_FMT.xml",
    "evyuka_form_FS.xml",
    "evyuka_form_HGF.xml",
    "evyuka_form_USP.xml",
    "evyuka_form_9270.xml",
    "evyuka_form_AUD.xml"
)

foreach ($file in $formFiles) {
    $filePath = "C:\dspace-be\dspace\config\vsb\$file"
    
    if (Test-Path $filePath) {
        Write-Host "Updating $file..."
        
        # Read the file content
        $content = Get-Content $filePath -Raw
        
        # Update subject.version and subject fields to use evyuka-subject-codes
        $content = $content -replace '(<dc-element>subject</dc-element>[\s\S]*?<validation-group>)evyuka-codes(</validation-group>)', '${1}evyuka-subject-codes${2}'
        
        # Update discipline and programme fields to use evyuka-discipline-programme-codes  
        $content = $content -replace '(<dc-element>discipline</dc-element>[\s\S]*?<validation-group>)evyuka-codes(</validation-group>)', '${1}evyuka-discipline-programme-codes${2}'
        $content = $content -replace '(<dc-element>programme</dc-element>[\s\S]*?<validation-group>)evyuka-codes(</validation-group>)', '${1}evyuka-discipline-programme-codes${2}'
        
        # Write the updated content back to the file
        Set-Content $filePath $content -Encoding UTF8
        
        Write-Host "Updated $file successfully."
    } else {
        Write-Host "File not found: $filePath"
    }
}

Write-Host "All evyuka forms have been updated."