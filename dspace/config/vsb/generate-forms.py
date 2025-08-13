#!/usr/bin/env python3
"""
VSB Form Template Generation Script for DSpace 7
Generates faculty-specific submission forms from a base template
"""

import os
import sys
import xml.etree.ElementTree as ET
import argparse
from datetime import datetime
import shutil
from pathlib import Path
import re

# Faculty codes
FACULTIES = ["FAST", "FBI", "FS", "FEI", "HGF", "FMT", "EKF", "USP", "9270", "AUD"]

def create_backup_dir():
    """Create timestamped backup directory"""
    timestamp = datetime.now().strftime("%Y-%m-%d_%H-%M")
    backup_dir = f"bak-{timestamp}"
    Path(backup_dir).mkdir(exist_ok=True)
    return backup_dir

def backup_existing_files(backup_dir):
    """Backup existing form files"""
    print(f"Creating backup in {backup_dir}...")

    for faculty in FACULTIES:
        form_file = f"evyuka_form_{faculty}.xml"
        if os.path.exists(form_file):
            shutil.copy2(form_file, backup_dir)

def validate_xml(filename):
    """Validate XML file"""
    try:
        ET.parse(filename)
        return True
    except ET.ParseError as e:
        print(f"XML validation error in {filename}: {e}")
        return False

def generate_form(faculty, backup_dir):
    """Generate faculty-specific form from template"""
    template_file = "evyuka_form_template.xml"
    output_file = f"evyuka_form_{faculty}.xml"

    print(f"Generating {output_file}...")

    try:
        # Read template file
        with open(template_file, 'r', encoding='utf-8') as f:
            content = f.read()

        # Replace PARAM placeholder with faculty code
        content = content.replace('PARAM', faculty)

        # Special handling for faculty 9270 - remove discipline and programme fields
        if faculty == "9270":
            print("  Special handling: removing discipline and programme fields...")

            # Remove discipline field rows
            content = re.sub(
                r'<row>\s*<field>\s*<dc-schema>evyuka</dc-schema>\s*<dc-element>discipline</dc-element>.*?</field>\s*</row>',
                '',
                content,
                flags=re.DOTALL
            )

            # Remove programme field rows
            content = re.sub(
                r'<row>\s*<field>\s*<dc-schema>evyuka</dc-schema>\s*<dc-element>programme</dc-element>.*?</field>\s*</row>',
                '',
                content,
                flags=re.DOTALL
            )

        # Write output file
        with open(output_file, 'w', encoding='utf-8') as f:
            f.write(content)

        # Validate generated file
        if validate_xml(output_file):
            print(f"  ✓ Successfully generated {output_file}")
            return True
        else:
            print(f"  ✗ Error: Generated file {output_file} contains invalid XML!")
            # Restore from backup if available
            backup_file = os.path.join(backup_dir, output_file)
            if os.path.exists(backup_file):
                shutil.copy2(backup_file, output_file)
                print(f"  Restored previous version from backup")
            return False

    except Exception as e:
        print(f"  ✗ Error generating {output_file}: {e}")
        # Restore from backup if available
        backup_file = os.path.join(backup_dir, output_file)
        if os.path.exists(backup_file):
            shutil.copy2(backup_file, output_file)
            print(f"  Restored previous version from backup")
        return False

def main():
    parser = argparse.ArgumentParser(description='VSB Form Template Generation Script for DSpace 7')
    parser.add_argument('--faculties', nargs='*', choices=FACULTIES, default=FACULTIES,
                        help='Faculties to generate forms for (default: all)')
    parser.add_argument('--no-backup', action='store_true',
                        help='Skip creating backup of existing files')
    parser.add_argument('--validate-only', action='store_true',
                        help='Only validate existing forms, do not generate')

    args = parser.parse_args()

    print("VSB Template Generation Script for DSpace 7")
    print("=" * 42)
    print()

    # Validate template file
    template_file = "evyuka_form_template.xml"
    print("Validating template file...")

    if not os.path.exists(template_file):
        print(f"Error: Template file {template_file} not found!")
        sys.exit(1)

    if not validate_xml(template_file):
        print(f"Error: Template file contains invalid XML!")
        sys.exit(1)

    # If validate-only mode, just check existing forms
    if args.validate_only:
        print("Validating existing forms...")
        valid_count = 0
        for faculty in args.faculties:
            form_file = f"evyuka_form_{faculty}.xml"
            if os.path.exists(form_file):
                if validate_xml(form_file):
                    print(f"  ✓ {form_file} is valid")
                    valid_count += 1
                else:
                    print(f"  ✗ {form_file} is invalid")
            else:
                print(f"  - {form_file} does not exist")

        print(f"\nValidation complete: {valid_count}/{len(args.faculties)} forms are valid")
        return

    # Create backup directory
    backup_dir = None
    if not args.no_backup:
        backup_dir = create_backup_dir()
        backup_existing_files(backup_dir)

    print("Generating faculty-specific forms...")
    print()

    # Generate forms
    successful = 0
    failed = 0

    for faculty in args.faculties:
        if generate_form(faculty, backup_dir or "."):
            successful += 1
        else:
            failed += 1

    print()
    print("Form generation completed!")
    if backup_dir:
        print(f"Backup created in: {backup_dir}")

    print()
    print("Summary:")
    print(f"  Successfully generated: {successful}")
    print(f"  Failed: {failed}")

    if failed == 0:
        print("\n✓ All forms generated successfully!")
    else:
        print(f"\n⚠ {failed} forms failed to generate. Check error messages above.")

    print()
    print("Next steps:")
    print("1. Run fetch-vocabularies.py to update controlled vocabularies")
    print("2. Restart DSpace to load the new forms")
    print("3. Test form functionality in the submission interface")

if __name__ == "__main__":
    main()
