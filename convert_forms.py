#!/usr/bin/env python3
"""
Script to convert v5 DSpace form definitions to v7 format
"""
import os
import re
import xml.etree.ElementTree as ET
from pathlib import Path

def is_v5_form_file(file_path):
    """Check if a file contains v5 form definitions (has <page number=> structure)"""
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
        return '<page number=' in content and '<form name=' in content
    except:
        return False

def is_v7_form_file(file_path):
    """Check if a file contains v7 form definitions (has <form-definitions> structure)"""
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
        return '<form-definitions>' in content or ('<form name=' in content and '<row>' in content)
    except:
        return False

def extract_form_code_from_filename(filename):
    """Extract form code from filename patterns like evyuka_form_HGF.xml"""
    # Match patterns like evyuka_form_XXX.xml, form_XXX.xml, etc.
    patterns = [
        r'evyuka_form_([^.]+)\.xml',
        r'form_([^.]+)\.xml',
        r'([^_]+)_form\.xml',
        r'([^.]+)\.xml'
    ]

    for pattern in patterns:
        match = re.search(pattern, filename)
        if match:
            return match.group(1)

    # Fallback: use filename without extension
    return Path(filename).stem

def extract_form_name_from_content(content):
    """Extract the original form name from v5 content"""
    form_match = re.search(r'<form name="([^"]+)">', content)
    if form_match:
        return form_match.group(1)
    return None

def convert_form_to_v7(input_file, output_file, form_code=None):
    """Convert a v5 form file to v7 format"""

    # Read the original v5 form
    with open(input_file, 'r', encoding='utf-8') as f:
        content = f.read()

    # Extract form name from content
    original_form_name = extract_form_name_from_content(content)
    if not original_form_name:
        print(f"Could not find form name in {input_file}")
        return False

    # If no form_code provided, try to extract from filename or use original form name
    if not form_code:
        form_code = extract_form_code_from_filename(input_file.name)

    # Extract all fields from all pages
    page_pattern = r'<page number="(\d+)">(.*?)</page>'
    pages = re.findall(page_pattern, content, re.DOTALL)

    if not pages:
        print(f"No pages found in {input_file}")
        return False

    # Start building the v7 form
    v7_content = '''<?xml version="1.0"?>
<!DOCTYPE form-definitions SYSTEM "submission-forms.dtd">

<form-definitions>
'''

    # Process each page and distribute fields across 3 forms
    all_fields = []
    for page_num, page_content in pages:
        field_pattern = r'<field>(.*?)</field>'
        fields = re.findall(field_pattern, page_content, re.DOTALL)
        all_fields.extend(fields)

    if not all_fields:
        print(f"No fields found in {input_file}")
        return False

    # Distribute fields across 3 pages (or the number of original pages if less than 3)
    num_target_pages = max(3, len(pages))
    fields_per_page = len(all_fields) // num_target_pages
    remainder = len(all_fields) % num_target_pages

    page_field_counts = [fields_per_page] * num_target_pages
    for i in range(remainder):
        page_field_counts[i] += 1

    # Generate form names based on the original form name
    page_names = []
    if 'e-vyuka' in original_form_name:
        # Handle evyuka forms specially
        base_name = original_form_name.replace('e-vyuka-', '').replace('e-vyuka', form_code)
        page_names = [f"e-vyuka-{form_code}page{word}" for word in ['one', 'two', 'three']]
    else:
        # Generic form naming
        page_names = [f"{original_form_name}page{word}" for word in ['one', 'two', 'three']]

    # Ensure we have enough page names
    while len(page_names) < num_target_pages:
        page_names.append(f"{original_form_name}page{len(page_names)+1}")

    field_index = 0
    for page_num in range(num_target_pages):
        if page_num < len(page_names):
            page_name = page_names[page_num]
        else:
            page_name = f"{original_form_name}page{page_num+1}"

        v7_content += f'    <form name="{page_name}">\n'

        # Add fields for this page
        fields_added = 0
        target_fields = page_field_counts[page_num] if page_num < len(page_field_counts) else 0

        while fields_added < target_fields and field_index < len(all_fields):
            field_content = all_fields[field_index].strip()
            # Convert field to row format
            v7_content += '       <row>\n'
            v7_content += '         <field>\n'

            # Clean up the field content and add proper indentation
            field_lines = field_content.split('\n')
            for line in field_lines:
                cleaned_line = line.strip()
                if cleaned_line:
                    v7_content += '           ' + cleaned_line + '\n'

            v7_content += '         </field>\n'
            v7_content += '       </row>\n\n'
            field_index += 1
            fields_added += 1

        v7_content += '    </form>\n\n'

    v7_content += '</form-definitions>'

    # Write the converted form
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write(v7_content)

    print(f"Converted {input_file} to v7 format -> {output_file}")
    return True

def find_all_form_files(directory):
    """Find all XML files that contain DSpace form definitions"""
    form_files = []
    directory = Path(directory)

    for xml_file in directory.glob('*.xml'):
        if is_v5_form_file(xml_file):
            form_files.append(xml_file)
        elif is_v7_form_file(xml_file):
            print(f"Skipping {xml_file.name} - already in v7 format")

    return form_files

def main():
    """Main function to convert all form files"""
    vsb_dir = Path('C:/dspace-be/dspace/config/vsb')

    if not vsb_dir.exists():
        print(f"Directory {vsb_dir} does not exist")
        return

    # Find all v5 form definition files
    v5_form_files = find_all_form_files(vsb_dir)

    if not v5_form_files:
        print("No v5 form definition files found for conversion")
        return

    print(f"Found {len(v5_form_files)} v5 form files to convert:")
    for file in v5_form_files:
        print(f"  - {file.name}")

    # Convert each file
    converted_count = 0
    for input_file in v5_form_files:
        form_code = extract_form_code_from_filename(input_file.name)
        if convert_form_to_v7(input_file, input_file, form_code):
            converted_count += 1

    print(f"\nConversion complete: {converted_count}/{len(v5_form_files)} files converted successfully")

if __name__ == "__main__":
    main()
