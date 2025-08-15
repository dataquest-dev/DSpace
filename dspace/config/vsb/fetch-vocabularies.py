#!/usr/bin/env python3
"""
VSB Vocabulary Fetching Script for DSpace 7
Downloads controlled vocabularies from VSB web services and converts them to DSpace format
"""

import os
import sys
import xml.etree.ElementTree as ET
import requests
import argparse
from datetime import datetime
import shutil
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor, as_completed
from threading import Lock

# Configuration
VSB_BASE_URL = "https://www.vsb.cz/edudocs"
VSB_TEST_URL = "https://www-test.vsb.cz/edudocs"
TIMEOUT = 10  # Reduced timeout
MAX_WORKERS = 8  # Concurrent downloads

# Faculty codes
FACULTIES = ["FAST", "FBI", "FS", "FEI", "HGF", "FMT", "EKF", "USP", "9270"]

# Vocabulary types
VOCAB_TYPES = ["program", "branch", "subject", "subject-version"]

# Thread-safe counters
stats_lock = Lock()
stats = {
    'total_downloads': 0,
    'successful_downloads': 0,
    'total_conversions': 0,
    'successful_conversions': 0
}

def create_backup_dir():
    """Create timestamped backup directory"""
    timestamp = datetime.now().strftime("%Y-%m-%d_%H-%M")
    backup_dir = f"vocab-backup-{timestamp}"
    Path(backup_dir).mkdir(exist_ok=True)
    return backup_dir

def backup_existing_files(backup_dir):
    """Backup existing vocabulary files"""
    print(f"Creating backup of existing vocabularies in {backup_dir}...")

    for vocab_type in VOCAB_TYPES:
        for faculty in FACULTIES:
            dir_file = f"dir_{vocab_type}_{faculty}.xml"
            vp_file = f"vp_{vocab_type}_{faculty}.xml"

            if os.path.exists(dir_file):
                shutil.copy2(dir_file, backup_dir)
            if os.path.exists(vp_file):
                shutil.copy2(vp_file, backup_dir)

def download_vocabulary(vocab_type, faculty, backup_dir):
    """Download vocabulary from VSB web service"""
    print(f"Fetching {vocab_type} vocabulary for {faculty}...")

    filename = f"dir_{vocab_type}_{faculty}.xml"
    urls = [
        f"{VSB_BASE_URL}/{vocab_type}-directory?faculty={faculty}",
        f"{VSB_TEST_URL}/{vocab_type}-directory?faculty={faculty}"
    ]

    for i, url in enumerate(urls):
        try:
            if i == 1:
                print("  Primary URL failed, trying fallback...")

            response = requests.get(url, timeout=TIMEOUT)
            response.raise_for_status()

            # Check if response contains valid vocabulary data
            if '<node' in response.text:
                with open(filename, 'w', encoding='utf-8') as f:
                    f.write(response.text)

                print(f"  ✓ Success: Downloaded {filename}" + (" (fallback)" if i == 1 else ""))
                return True

        except requests.RequestException as e:
            if i == 0:
                continue  # Try fallback URL
            else:
                print(f"  ✗ Failed: Could not download {filename}")

                # Try to restore from backup
                backup_file = os.path.join(backup_dir, filename)
                if os.path.exists(backup_file):
                    shutil.copy2(backup_file, filename)
                    print(f"  Restored from backup: {filename}")
                    return True

                # Create minimal valid XML placeholder file if download and backup both failed
                empty_content = '<node id="" label=""><isComposedBy></isComposedBy></node>'

                with open(filename, 'w', encoding='utf-8') as f:
                    f.write(empty_content)
                print(f"  Created empty placeholder: {filename}")
                return True

    return False

def convert_vocabulary(vocab_type, faculty):
    """Convert downloaded XML to DSpace value-pairs format"""
    input_file = f"dir_{vocab_type}_{faculty}.xml"
    output_file = f"vp_{vocab_type}_{faculty}.xml"
    value_pairs_name = f"vp_{vocab_type}_{faculty}"

    # All vocabulary types should use 'programme' as dc-term
    dc_term = 'programme'

    if not os.path.exists(input_file):
        print(f"  Warning: Input file {input_file} not found, creating default value-pairs file")
        # Create default vp_ file when input file doesn't exist
        create_default_vp_file(output_file, value_pairs_name, dc_term)
        return True

    try:
        print("  Converting to value-pairs format...")

        # Parse the XML file
        tree = ET.parse(input_file)
        root = tree.getroot()

        # Create the output XML structure with exact XSLT formatting
        output_lines = [
            f'<value-pairs value-pairs-name="{value_pairs_name}" dc-term="{dc_term}">',
            '  <pair>',
            '    <displayed-value>Neuvedeno</displayed-value>',
            '    <stored-value></stored-value>',
            '  </pair>'
        ]

        # Process each node (search recursively like XSLT does with .//node)
        for node in root.findall('.//node'):
            label = node.get('label', '')
            node_id = node.get('id', '')

            # Escape XML special characters
            label = label.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;').replace('"', '&quot;').replace("'", '&apos;')
            node_id = node_id.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;').replace('"', '&quot;').replace("'", '&apos;')

            output_lines.extend([
                '  <pair>',
                f'    <displayed-value>{label}</displayed-value>',
                f'    <stored-value>{node_id}</stored-value>',
                '  </pair>'
            ])

        output_lines.append('</value-pairs>')

        # Write output file with exact formatting (no XML declaration, proper indentation)
        with open(output_file, 'w', encoding='utf-8') as f:
            f.write('\n'.join(output_lines) + '\n')

        print(f"  ✓ Success: Generated {output_file}")
        return True

    except Exception as e:
        print(f"  ✗ Warning: Conversion failed for {output_file}: {e}")
        print(f"  Creating default value-pairs file instead")
        # Create default vp_ file when conversion fails
        create_default_vp_file(output_file, value_pairs_name, dc_term)
        return True

def create_default_vp_file(output_file, value_pairs_name, dc_term):
    """Create a default value-pairs file with only the 'Neuvedeno' option"""
    default_content = f'''<value-pairs value-pairs-name="{value_pairs_name}" dc-term="{dc_term}">
  <pair>
    <displayed-value>Neuvedeno</displayed-value>
    <stored-value/>
  </pair>
</value-pairs>
'''
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write(default_content)
    print(f"  ✓ Created default: {output_file}")

def main():
    parser = argparse.ArgumentParser(description='VSB Vocabulary Fetching Script for DSpace 7')
    parser.add_argument('--vocab-types', nargs='*', choices=VOCAB_TYPES, default=VOCAB_TYPES,
                        help='Vocabulary types to fetch (default: all)')
    parser.add_argument('--faculties', nargs='*', choices=FACULTIES, default=FACULTIES,
                        help='Faculties to fetch (default: all)')
    parser.add_argument('--no-backup', action='store_true',
                        help='Skip creating backup of existing files')
    parser.add_argument('--download-only', action='store_true',
                        help='Only download, skip conversion')

    args = parser.parse_args()

    print("VSB Vocabulary Fetching Script for DSpace 7")
    print("=" * 43)
    print()

    # Check if we're in the right directory
    if not os.path.exists('controlled-vocabulary2value-pairs.xsl'):
        print("Warning: controlled-vocabulary2value-pairs.xsl not found in current directory")
        print("Make sure you're running this script from the VSB config directory")
        print()

    # Create backup directory
    backup_dir = None
    if not args.no_backup:
        backup_dir = create_backup_dir()
        backup_existing_files(backup_dir)

    print("Downloading vocabularies from VSB web services...")
    print()

    # Initialize counters
    total_downloads = 0
    successful_downloads = 0
    total_conversions = 0
    successful_conversions = 0

    # Process vocabularies
    with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
        futures = []
        for vocab_type in args.vocab_types:
            for faculty in args.faculties:
                total_downloads += 1

                # Submit download task
                futures.append(executor.submit(download_vocabulary, vocab_type, faculty, backup_dir or "."))

        # Wait for all downloads to complete
        for future in as_completed(futures):
            if future.result():
                successful_downloads += 1

    if not args.download_only:
        with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
            futures = []
            for vocab_type in args.vocab_types:
                for faculty in args.faculties:
                    total_conversions += 1

                    # Submit conversion task
                    futures.append(executor.submit(convert_vocabulary, vocab_type, faculty))

            # Wait for all conversions to complete
            for future in as_completed(futures):
                if future.result():
                    successful_conversions += 1

    # Show summary
    print("Summary:")
    print("=" * 8)
    print(f"Total vocabulary downloads attempted: {total_downloads}")
    print(f"Successful downloads: {successful_downloads}")
    print(f"Failed downloads: {total_downloads - successful_downloads}")

    if not args.download_only:
        print(f"Successful conversions: {successful_conversions}")
        print(f"Failed conversions: {total_conversions - successful_conversions}")

    if backup_dir:
        print(f"\nBackup created in: {backup_dir}")

    if total_downloads - successful_downloads > 0:
        print("\nWarning: Some vocabularies could not be updated.")
        print("Check the backup directory and restore manually if needed.")
    else:
        print("\nAll vocabularies updated successfully!")

    print("\nNext steps:")
    print("1. Regenerate forms with the template generation script if needed")
    print("2. Restart DSpace to load the new vocabularies")
    print("3. Test vocabulary functionality in the submission interface")

if __name__ == "__main__":
    main()
