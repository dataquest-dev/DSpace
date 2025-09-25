#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Add validation groups to evyuka forms while preserving UTF-8 encoding
This script correctly handles Czech characters and XML structure
"""

import os
import re
from pathlib import Path

# Forms to process
FORM_FILES = [
    "evyuka_form_EKF.xml",
    "evyuka_form_FEI.xml", 
    "evyuka_form_HGF.xml",
    "evyuka_form_FMT.xml",
    "evyuka_form_FS.xml",
    "evyuka_form_USP.xml",
    "evyuka_form_AUD.xml",
    "evyuka_form_9270.xml",
    "evyuka_form_FAST.xml"
]

# Base directory
BASE_DIR = Path("C:/dspace-be/dspace/config/vsb")

def add_validation_groups():
    """Add validation groups to all evyuka forms"""
    
    for form_file in FORM_FILES:
        file_path = BASE_DIR / form_file
        
        if not file_path.exists():
            print(f"⚠ File not found: {form_file}")
            continue
            
        print(f"📄 Processing {form_file}...")
        
        # Read file with UTF-8 encoding
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # Pattern 1: subject.version field
        pattern1 = r'(<hint>Vyberte kódy všech verzí předmětu[^<]*</hint>\s*)</field>'
        replacement1 = r'\1           <validation-group>evyuka-subject-codes</validation-group>\n         </field>'
        content = re.sub(pattern1, replacement1, content)
        
        # Pattern 2: subject field  
        pattern2 = r'(<hint>Vyberte kódy všech předmětů[^<]*</hint>\s*)</field>'
        replacement2 = r'\1           <validation-group>evyuka-subject-codes</validation-group>\n         </field>'
        content = re.sub(pattern2, replacement2, content)
        
        # Pattern 3: discipline field
        pattern3 = r'(<hint>Vyberte kódy všech studijních oborů[^<]*</hint>\s*)</field>'
        replacement3 = r'\1           <validation-group>evyuka-discipline-programme-codes</validation-group>\n         </field>'
        content = re.sub(pattern3, replacement3, content)
        
        # Pattern 4: programme field
        pattern4 = r'(<hint>Vyberte kódy všech studijních programů[^<]*</hint>\s*)</field>'
        replacement4 = r'\1           <validation-group>evyuka-discipline-programme-codes</validation-group>\n         </field>'
        content = re.sub(pattern4, replacement4, content)
        
        # Write back to file with UTF-8 encoding
        with open(file_path, 'w', encoding='utf-8', newline='\n') as f:
            f.write(content)
            
        print(f"✅ Updated {form_file} successfully")
    
    print(f"\n🎉 All evyuka forms updated with validation groups!")
    print(f"🔤 Czech characters preserved with UTF-8 encoding.")

if __name__ == "__main__":
    add_validation_groups()