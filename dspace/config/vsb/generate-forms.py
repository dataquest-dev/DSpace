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
from .vsb_config import VSB_BASE_URL, VSB_TEST_URL

# Faculty codes
FACULTIES = ["FAST", "FBI", "FS", "FEI", "HGF", "FMT", "EKF", "USP", "9270", "AUD"]

def create_backup_dir(base_dir="."):
    """Create timestamped backup directory"""
    timestamp = datetime.now().strftime("%Y-%m-%d_%H-%M")
    backup_dir = Path(base_dir) / f"bak-{timestamp}"
    backup_dir.mkdir(exist_ok=True)
    return backup_dir

def backup_existing_files(backup_dir, work_dir="."):
    """Backup existing form files"""
    print(f"Creating backup in {backup_dir}...")

    work_path = Path(work_dir)
    for faculty in FACULTIES:
        form_file = work_path / f"evyuka_form_{faculty}.xml"
        if form_file.exists():
            shutil.copy2(form_file, backup_dir)

def validate_xml(filename):
    """Validate XML file"""
    try:
        ET.parse(filename)
        return True
    except ET.ParseError as e:
        print(f"XML validation error in {filename}: {e}")
        return False

def generate_form(faculty, backup_dir, work_dir="."):
    """Generate faculty-specific form from template"""
    work_path = Path(work_dir)
    template_file = work_path / "evyuka_form_template.xml"
    output_file = work_path / f"evyuka_form_{faculty}.xml"

    print(f"Generating {output_file.name}...")

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
            print(f"  ✓ Successfully generated {output_file.name}")
            return True
        else:
            print(f"  ✗ Error: Generated file {output_file.name} contains invalid XML!")
            # Restore from backup if available
            backup_file = backup_dir / output_file.name
            if backup_file.exists():
                shutil.copy2(backup_file, output_file)
                print(f"  Restored previous version from backup")
            return False

    except Exception as e:
        print(f"  ✗ Error generating {output_file.name}: {e}")
        # Restore from backup if available
        backup_file = backup_dir / output_file.name
        if backup_file.exists():
            shutil.copy2(backup_file, output_file)
            print(f"  Restored previous version from backup")
        return False

def main():
    parser = argparse.ArgumentParser(description='VSB Form Template Generation Script for DSpace 7',
                                   epilog="""
Examples:
  %(prog)s                                 # Use current directory
  %(prog)s /path/to/vsb                    # Use specific directory
  %(prog)s /path/to/vsb --faculties FBI FS  # Only specific faculties
  %(prog)s --validate-only                 # Only validate, don't generate
""")
    parser.add_argument('work_dir', nargs='?', default='.',
                       help='Directory containing VSB form template (default: current directory)')
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

    # Validate working directory
    work_path = Path(args.work_dir)
    if not work_path.exists():
        print(f"Error: Working directory does not exist: {args.work_dir}")
        sys.exit(1)
    
    print(f"Working directory: {work_path.absolute()}")
    print()

    # Validate template file
    template_file = work_path / "evyuka_form_template.xml"
    print("Validating template file...")

    if not template_file.exists():
        print(f"Error: Template file {template_file.name} not found in working directory!")
        sys.exit(1)

    if not validate_xml(template_file):
        print(f"Error: Template file contains invalid XML!")
        sys.exit(1)

    # If validate-only mode, just check existing forms
    if args.validate_only:
        print("Validating existing forms...")
        valid_count = 0
        for faculty in args.faculties:
            form_file = work_path / f"evyuka_form_{faculty}.xml"
            if form_file.exists():
                if validate_xml(form_file):
                    print(f"  ✓ {form_file.name} is valid")
                    valid_count += 1
                else:
                    print(f"  ✗ {form_file.name} is invalid")
            else:
                print(f"  - {form_file.name} does not exist")

        print(f"\nValidation complete: {valid_count}/{len(args.faculties)} forms are valid")
        return

    # Create backup directory
    backup_dir = None
    if not args.no_backup:
        backup_dir = create_backup_dir(args.work_dir)
        backup_existing_files(backup_dir, args.work_dir)

    print("Generating faculty-specific forms...")
    print()

    # Generate forms
    successful = 0
    failed = 0

    for faculty in args.faculties:
        if generate_form(faculty, backup_dir or work_path, args.work_dir):
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
