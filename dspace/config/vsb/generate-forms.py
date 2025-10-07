#!/usr/bin/env python3
"""
VSB Form Template Generation Script for DSpace 7
Generates faculty-specific submission forms from a base template
"""

import os
import sys
import xml.etree.ElementTree as ET
import argparse
import logging
from datetime import datetime
import shutil
from pathlib import Path
import re

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'
)
logger = logging.getLogger(__name__)

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
    logger.info(f"Creating backup in {backup_dir}...")

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
        logger.error(f"XML validation error in {filename}: {e}")
        return False

def restore_from_backup(output_file, backup_dir):
    """Restores a file from the backup directory if it exists."""
    if not backup_dir:
        return  # Do nothing if no backup was made

    backup_file = backup_dir / output_file.name
    if backup_file.exists():
        shutil.copy2(backup_file, output_file)
        logger.info(f"  Restored previous version from backup: {output_file.name}")

def generate_form(faculty, backup_dir, work_dir="."):
    """Generate faculty-specific form from template"""
    work_path = Path(work_dir)
    template_file = work_path / "evyuka_form_template.xml"
    output_file = work_path / f"evyuka_form_{faculty}.xml"

    logger.info(f"Generating {output_file.name}...")

    try:
        # Read template file
        with open(template_file, 'r', encoding='utf-8') as f:
            content = f.read()

        # Replace PARAM placeholder with faculty code
        content = content.replace('PARAM', faculty)

        # Special handling for faculty 9270 - remove discipline and programme fields
        if faculty == "9270":
            logger.info("  Special handling: removing discipline and programme fields...")

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
            logger.info(f"  ✓ Successfully generated {output_file.name}")
            return True
        else:
            logger.error(f"  ✗ Error: Generated file {output_file.name} contains invalid XML!")
            restore_from_backup(output_file, backup_dir)  # Call helper function
            return False

    except Exception as e:
        logger.error(f"  ✗ Error generating {output_file.name}: {e}")
        restore_from_backup(output_file, backup_dir)  # Call helper function
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

    logger.info("VSB Template Generation Script for DSpace 7")
    logger.info("=" * 42)

    # Validate working directory
    work_path = Path(args.work_dir)
    if not work_path.exists():
        logger.error(f"Error: Working directory does not exist: {args.work_dir}")
        sys.exit(1)
    
    logger.info(f"Working directory: {work_path.absolute()}")

    # Validate template file
    template_file = work_path / "evyuka_form_template.xml"
    logger.info("Validating template file...")

    if not template_file.exists():
        logger.error(f"Error: Template file {template_file.name} not found in working directory!")
        sys.exit(1)

    if not validate_xml(template_file):
        logger.error(f"Error: Template file contains invalid XML!")
        sys.exit(1)

    # If validate-only mode, just check existing forms
    if args.validate_only:
        logger.info("Validating existing forms...")
        valid_count = 0
        for faculty in args.faculties:
            form_file = work_path / f"evyuka_form_{faculty}.xml"
            if form_file.exists():
                if validate_xml(form_file):
                    logger.info(f"  ✓ {form_file.name} is valid")
                    valid_count += 1
                else:
                    logger.error(f"  ✗ {form_file.name} is invalid")
            else:
                logger.warning(f"  - {form_file.name} does not exist")

        logger.info(f"Validation complete: {valid_count}/{len(args.faculties)} forms are valid")
        return

    # Create backup directory
    backup_dir = None
    if not args.no_backup:
        backup_dir = create_backup_dir(args.work_dir)
        backup_existing_files(backup_dir, args.work_dir)

    logger.info("Generating faculty-specific forms...")

    # Generate forms
    successful = 0
    failed = 0

    for faculty in args.faculties:
        if generate_form(faculty, backup_dir or work_path, args.work_dir):
            successful += 1
        else:
            failed += 1

    logger.info("Form generation completed!")
    if backup_dir:
        logger.info(f"Backup created in: {backup_dir}")

    logger.info("Summary:")
    logger.info(f"  Successfully generated: {successful}")
    logger.info(f"  Failed: {failed}")

    if failed == 0:
        logger.info("✓ All forms generated successfully!")
    else:
        logger.warning(f"⚠ {failed} forms failed to generate. Check error messages above.")

    logger.info("Next steps:")
    logger.info("1. Run fetch-vocabularies.py to update controlled vocabularies")
    logger.info("2. Restart DSpace to load the new forms")
    logger.info("3. Test form functionality in the submission interface")

if __name__ == "__main__":
    main()
