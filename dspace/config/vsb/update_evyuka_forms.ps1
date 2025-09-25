# PowerShell script to add validation-group to all evyuka form files
# This script adds the validation-group element to all four evyuka code fields

$files = Get-ChildItem -Path "." -Name "evyuka_form_*.xml" | Where-Object { $_ -ne "evyuka_form_FAST.xml" -and $_ -ne "evyuka_form_template.xml" }

foreach ($file in $files) {
    Write-Host "Processing $file"
    $content = Get-Content $file -Raw
    
    # Replace evyuka.subject.version field
    $content = $content -replace `
        '(<field>\s*<dc-schema>evyuka</dc-schema>\s*<dc-element>subject</dc-element>\s*<dc-qualifier>version</dc-qualifier>\s*<repeatable>true</repeatable>\s*<label>Kód verze předmětu</label>\s*<input-type[^>]*>dropdown</input-type>\s*<hint>[^<]*</hint>)\s*</field>', `
        '$1           <validation-group>evyuka-codes</validation-group>         </field>'
    
    # Replace evyuka.subject field (no qualifier)
    $content = $content -replace `
        '(<field>\s*<dc-schema>evyuka</dc-schema>\s*<dc-element>subject</dc-element>\s*<dc-qualifier></dc-qualifier>\s*<repeatable>true</repeatable>\s*<label>Kód předmětu</label>\s*<input-type[^>]*>dropdown</input-type>\s*<hint>[^<]*</hint>)\s*</field>', `
        '$1           <validation-group>evyuka-codes</validation-group>         </field>'
    
    # Replace evyuka.discipline field
    $content = $content -replace `
        '(<field>\s*<dc-schema>evyuka</dc-schema>\s*<dc-element>discipline</dc-element>\s*<dc-qualifier></dc-qualifier>\s*<repeatable>true</repeatable>\s*<label>Kód studijního oboru</label>\s*<input-type[^>]*>dropdown</input-type>\s*<hint>[^<]*</hint>)\s*</field>', `
        '$1           <validation-group>evyuka-codes</validation-group>         </field>'
    
    # Replace evyuka.programme field
    $content = $content -replace `
        '(<field>\s*<dc-schema>evyuka</dc-schema>\s*<dc-element>programme</dc-element>\s*<dc-qualifier></dc-qualifier>\s*<repeatable>true</repeatable>\s*<label>Kód studijního programu</label>\s*<input-type[^>]*>dropdown</input-type>\s*<hint>[^<]*</hint>)\s*</field>', `
        '$1           <validation-group>evyuka-codes</validation-group>         </field>'
    
    $content | Set-Content $file -NoNewline
}

Write-Host "All evyuka form files processed."