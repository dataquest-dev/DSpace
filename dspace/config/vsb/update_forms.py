#!/usr/bin/env python3
"""
Python script to update E-výuka forms with correct validation groups.

This script ensures that all evyuka forms in the /vsb folder have the proper
validation groups configured for the E-výuka validation system.

Validation Groups:
- evyuka-subject-codes: Applied to evyuka.subject.version and evyuka.subject fields
- evyuka-discipline-programme-codes: Applied to evyuka.discipline and evyuka.programme fields

The script supports both the master EvyukaCodesValidation (field name-based detection)
and legacy validators (validation group-based detection).

Author: Milan Majchrak (dspace at dataquest.sk)
"""

import os
import re
import xml.etree.ElementTree as ET
from pathlib import Path
import argparse
import logging
from datetime import datetime

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(levelname)s: %(message)s')
logger = logging.getLogger(__name__)

class EvyukaFormUpdater:
    """Updates E-výuka forms with correct validation groups."""
    
    # Validation group mappings based on current validation system
    VALIDATION_GROUPS = {
        'evyuka.subject.version': 'evyuka-subject-codes',
        'evyuka.subject': 'evyuka-subject-codes', 
        'evyuka.discipline': 'evyuka-discipline-programme-codes',
        'evyuka.programme': 'evyuka-discipline-programme-codes'
    }
    
    def __init__(self, forms_dir: str):
        """Initialize with forms directory path."""
        self.forms_dir = Path(forms_dir)
        if not self.forms_dir.exists():
            raise FileNotFoundError(f"Forms directory not found: {forms_dir}")
        
        # Create backup directory with timestamp (consistent with existing naming)
        timestamp = datetime.now().strftime("%Y-%m-%d_%H-%M")
        self.backup_dir = self.forms_dir / f"update-forms-backup-{timestamp}"
        self.backup_dir.mkdir(parents=True, exist_ok=True)
        logger.info(f"Backup directory created: {self.backup_dir}")
    
    def get_evyuka_forms(self):
        """Get list of all evyuka form files."""
        pattern = "evyuka_form_*.xml"
        forms = list(self.forms_dir.glob(pattern))
        
        # Exclude template file from processing
        forms = [f for f in forms if 'template' not in f.name]
        
        # Extra safety check - ensure we only process evyuka_form_*.xml files
        valid_forms = []
        for f in forms:
            if f.name.startswith('evyuka_form_') and f.name.endswith('.xml'):
                valid_forms.append(f)
            else:
                logger.warning(f"Skipping unexpected file: {f.name}")
        
        logger.info(f"Found {len(valid_forms)} evyuka forms to process")
        if logger.isEnabledFor(logging.DEBUG):
            for form in sorted(valid_forms):
                logger.debug(f"Will process: {form.name}")
        
        return sorted(valid_forms)
    
    def backup_file(self, file_path: Path):
        """Create backup of original file in dedicated backup directory."""
        backup_path = self.backup_dir / file_path.name
        backup_path.write_text(file_path.read_text(encoding='utf-8'), encoding='utf-8')
        logger.debug(f"Created backup: {backup_path}")
    
    def is_evyuka_field(self, field_element):
        """Check if field element is an evyuka code field."""
        schema_elem = field_element.find('dc-schema')
        element_elem = field_element.find('dc-element')
        qualifier_elem = field_element.find('dc-qualifier')
        
        if schema_elem is None or element_elem is None or qualifier_elem is None:
            return False, None
            
        schema = schema_elem.text
        element = element_elem.text
        qualifier = qualifier_elem.text or ""
        
        if schema == 'evyuka':
            field_name = f"{schema}.{element}"
            if qualifier:
                field_name += f".{qualifier}"
            
            if field_name in self.VALIDATION_GROUPS:
                return True, field_name
                
        return False, None
    

    
    def process_form(self, form_path: Path, create_backup=True):
        """Process a single evyuka form file using text-based processing to preserve formatting."""
        # Safety check - ensure we only process evyuka_form_*.xml files
        if not (form_path.name.startswith('evyuka_form_') and form_path.name.endswith('.xml')):
            logger.error(f"SECURITY: Refusing to process non-evyuka form file: {form_path.name}")
            return False
            
        logger.info(f"Processing {form_path.name}...")
        
        if create_backup:
            self.backup_file(form_path)
        
        # Read the original file content
        try:
            original_content = form_path.read_text(encoding='utf-8')
        except Exception as e:
            logger.error(f"Failed to read {form_path}: {e}")
            return False
        
        # Parse XML only for field detection
        try:
            tree = ET.parse(form_path)
            root = tree.getroot()
        except ET.ParseError as e:
            logger.error(f"Failed to parse {form_path}: {e}")
            return False
        
        validation_updates = []
        evyuka_fields_found = 0
        
        # Find all field elements for analysis
        for field in root.findall('.//field'):
            is_evyuka, field_name = self.is_evyuka_field(field)
            
            if is_evyuka:
                evyuka_fields_found += 1
                logger.debug(f"Found evyuka field: {field_name}")
                
                expected_group = self.VALIDATION_GROUPS[field_name]
                hint_elem = field.find('hint')
                hint_text = hint_elem.text if hint_elem is not None else ""
                
                validation_elem = field.find('validation-group')
                
                if validation_elem is not None:
                    # Check if update is needed
                    current_group = validation_elem.text
                    if current_group != expected_group:
                        validation_updates.append({
                            'field_name': field_name,
                            'validation_group': expected_group,
                            'old_validation_group': current_group,
                            'hint_text': hint_text,
                            'action': 'update'
                        })
                else:
                    # Add new validation group
                    if hint_text:  # Only if hint exists
                        validation_updates.append({
                            'field_name': field_name,
                            'validation_group': expected_group,
                            'hint_text': hint_text,
                            'action': 'add'
                        })
                    else:
                        logger.warning(f"No hint element found for {field_name}, cannot add validation-group")
        
        if validation_updates:
            # Apply only validation group changes
            self.save_file_with_validation_groups_only(form_path, original_content, validation_updates)
            logger.info(f"Updated {len(validation_updates)} validation groups in {form_path.name}")
        else:
            logger.info(f"No changes needed for {form_path.name}")
        
        logger.info(f"Found {evyuka_fields_found} evyuka fields in {form_path.name}")
        return True
    
    def save_file_with_validation_groups_only(self, file_path, original_content, validation_updates):
        """Save file with only validation group changes, preserving all other formatting."""
        updated_content = original_content
        
        for update in validation_updates:
            field_name = update['field_name']
            validation_group = update['validation_group']
            hint_text = update['hint_text']
            action = update['action']  # 'add' or 'update'
            
            if action == 'add':
                # Add validation-group after the hint element - escape special regex characters in hint text
                escaped_hint = re.escape(hint_text)
                pattern = f'(<hint[^>]*>{escaped_hint}</hint>)'
                replacement = f'\\1\n                    <validation-group>{validation_group}</validation-group>'
                updated_content = re.sub(pattern, replacement, updated_content, count=1)
                logger.debug(f"Added validation-group '{validation_group}' to {field_name}")
            
            elif action == 'update':
                # Update existing validation-group
                old_group = update['old_validation_group']
                pattern = f'<validation-group>{re.escape(old_group)}</validation-group>'
                replacement = f'<validation-group>{validation_group}</validation-group>'
                updated_content = re.sub(pattern, replacement, updated_content, count=1)
                logger.debug(f"Updated validation-group for {field_name}: '{old_group}' -> '{validation_group}'")
        
        # Write the updated content
        file_path.write_text(updated_content, encoding='utf-8')
    
    def process_all_forms(self, create_backup=True):
        """Process all evyuka forms in the directory."""
        forms = self.get_evyuka_forms()
        
        if not forms:
            logger.warning("No evyuka forms found to process")
            return
        
        success_count = 0
        total_forms = len(forms)
        
        for form_path in forms:
            try:
                if self.process_form(form_path, create_backup):
                    success_count += 1
            except Exception as e:
                logger.error(f"Error processing {form_path}: {e}")
        
        logger.info(f"Successfully processed {success_count}/{total_forms} forms")
    
    def validate_forms(self):
        """Validate that all forms have correct validation groups."""
        logger.info("Validating evyuka forms...")
        
        forms = self.get_evyuka_forms()
        validation_errors = []
        
        for form_path in forms:
            try:
                tree = ET.parse(form_path)
                root = tree.getroot()
                
                form_errors = []
                
                # Check each field
                for field in root.findall('.//field'):
                    is_evyuka, field_name = self.is_evyuka_field(field)
                    
                    if is_evyuka:
                        validation_elem = field.find('validation-group')
                        expected_group = self.VALIDATION_GROUPS[field_name]
                        
                        if validation_elem is None:
                            form_errors.append(f"Missing validation-group for {field_name}")
                        elif validation_elem.text != expected_group:
                            form_errors.append(f"Incorrect validation-group for {field_name}: got '{validation_elem.text}', expected '{expected_group}'")
                
                if form_errors:
                    validation_errors.append(f"{form_path.name}: {', '.join(form_errors)}")
                else:
                    logger.info(f"✓ {form_path.name} - validation groups correct")
                    
            except Exception as e:
                validation_errors.append(f"{form_path.name}: Error parsing file - {e}")
        
        if validation_errors:
            logger.error("Validation errors found:")
            for error in validation_errors:
                logger.error(f"  {error}")
            return False
        else:
            logger.info("All forms have correct validation groups!")
            return True


def main():
    """Main function with command line interface."""
    parser = argparse.ArgumentParser(
        description="Update E-výuka forms with correct validation groups",
        epilog="""
Examples:
  %(prog)s /path/to/vsb                    # Process all forms in directory
  %(prog)s /path/to/vsb --validate         # Only validate forms
  %(prog)s /path/to/vsb --no-backup        # Process without creating backups
  %(prog)s /path/to/vsb --verbose          # Enable debug logging
        """
    )
    
    parser.add_argument('forms_dir', 
                       help='Directory containing evyuka form XML files')
    parser.add_argument('--validate', action='store_true',
                       help='Only validate forms, do not modify')
    parser.add_argument('--no-backup', action='store_true',
                       help='Do not create backup files')
    parser.add_argument('--verbose', action='store_true',
                       help='Enable verbose logging')
    
    args = parser.parse_args()
    
    if args.verbose:
        logging.getLogger().setLevel(logging.DEBUG)
    
    try:
        updater = EvyukaFormUpdater(args.forms_dir)
        
        if args.validate:
            success = updater.validate_forms()
            exit(0 if success else 1)
        else:
            updater.process_all_forms(create_backup=not args.no_backup)
            
            # Validate after processing
            logger.info("\nValidating processed forms...")
            success = updater.validate_forms()
            if not success:
                logger.error("Some validation errors remain after processing")
                exit(1)
            
    except FileNotFoundError as e:
        logger.error(str(e))
        exit(1)
    except Exception as e:
        logger.error(f"Unexpected error: {e}")
        exit(1)


if __name__ == '__main__':
    main()