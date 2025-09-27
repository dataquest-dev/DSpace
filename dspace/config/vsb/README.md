# VSB Template System for DSpace 7

## Overview

The VSB (Vysoká škola báňská - Technical University of Ostrava) template system provides maintenance scripts for automatically generating submission forms and controlled vocabularies for different faculties. This system is designed for DSpace 7 and provides Python-based tools to maintain faculty-specific submission workflows.

## Template Generation Process

### 1. Form Templates
The system uses a template-based approach to generate faculty-specific submission forms:

- **Base template**: `evyuka_form_template.xml` - Contains the base form structure
- **Generation script**: `generate-forms.py` - Generates faculty-specific forms from template
- **Generated forms**: `evyuka_form_[FACULTY].xml` files for each faculty

#### Faculties supported:
- 9270 (special handling - no discipline/programme selection)
- FAST (Faculty of Civil Engineering)
- FBI (Faculty of Safety Engineering)
- FS (Faculty of Mechanical Engineering)
- FEI (Faculty of Electrical Engineering and Computer Science)
- HGF (Faculty of Mining and Geology)
- FMT (Faculty of Materials Science and Technology)
- EKF (Faculty of Economics)
- USP (Institute of Clean Technologies)
- AUD (Audio/Special collections)

### 2. Controlled Vocabularies
The system fetches controlled vocabularies from VSB web services and converts them to DSpace format:

- **Fetch script**: `fetch-vocabularies.py` - Downloads vocabularies from VSB web services
- **XSLT transformer**: `controlled-vocabulary2value-pairs.xsl` - Reference for conversion format
- **Vocabulary types**: program, branch, subject, subject-version

## Maintenance Scripts

### Key Scripts:

#### Vocabulary Updates
- **`fetch-vocabularies.py`** - Downloads and updates controlled vocabularies from VSB web services

**Features:**
- Concurrent downloads for faster processing
- Automatic fallback to test URLs
- Built-in XML validation and conversion
- Automatic backup creation
- UTF-8 encoding support for Czech characters

#### Template Generation
- **`generate-forms.py`** - Generates faculty-specific submission forms from the base template

**Features:**
- Automatic form generation from template
- Special handling for faculty 9270 (removes discipline/programme fields)
- XML validation and error checking
- Automatic backup creation
- Cross-platform compatibility

#### Form Validation Updates
- **`update_forms.py`** - Updates E-výuka forms with correct validation groups

**Features:**
- Adds missing validation-group elements to evyuka forms
- Conservative text-based processing (preserves original XML formatting)
- Intelligent field detection and validation group assignment
- Timestamped backup creation with `update-forms-backup-` prefix
- Support for both master and legacy validation systems
- CLI interface with validation-only mode

### Script Features:

- **Automatic backups**: Creates timestamped backups before making changes
- **Error handling**: Validates XML and provides fallback options
- **Python-based**: No external dependencies, works on Windows, Linux, and macOS
- **Progress reporting**: Shows detailed status and completion summaries
- **Recovery mechanisms**: Restores from backups on failure
- **Concurrent processing**: Fast parallel downloads and conversions

## Usage Instructions

### Updating Vocabularies

**Basic usage (update all vocabularies in current directory):**
```bash
python fetch-vocabularies.py
```

**Specify working directory:**
```bash
# Update vocabularies in specific directory
python fetch-vocabularies.py /path/to/vsb

# Update vocabularies using current directory (equivalent to basic usage)
python fetch-vocabularies.py .
```

**Advanced options:**
```bash
# Update only specific vocabulary types
python fetch-vocabularies.py /path/to/vsb --vocab-types program branch

# Update only specific faculties
python fetch-vocabularies.py /path/to/vsb --faculties FAST FBI FS

# Skip backup creation
python fetch-vocabularies.py /path/to/vsb --no-backup

# Download only (skip conversion)
python fetch-vocabularies.py /path/to/vsb --download-only
```

### Regenerating Forms

**Generate all forms (in current directory):**
```bash
python generate-forms.py
```

**Specify working directory:**
```bash
# Generate forms in specific directory
python generate-forms.py /path/to/vsb

# Generate forms using current directory (equivalent to basic usage)
python generate-forms.py .
```

**Advanced options:**
```bash
# Generate forms for specific faculties
python generate-forms.py /path/to/vsb --faculties FAST FBI FS

# Skip backup creation
python generate-forms.py /path/to/vsb --no-backup

# Validate existing forms only
python generate-forms.py /path/to/vsb --validate-only
```

### Updating Form Validation Groups

**Update all evyuka forms with validation groups:**
```bash
python update_forms.py .
```

**Advanced options:**
```bash
# Validate forms without making changes
python update_forms.py . --validate

# Update forms without creating backups
python update_forms.py . --no-backup

# Verbose output with detailed logging
python update_forms.py . --verbose
```

**Validation Groups Applied:**
- `evyuka-subject-codes` - Applied to evyuka.subject.version and evyuka.subject fields
- `evyuka-discipline-programme-codes` - Applied to evyuka.discipline and evyuka.programme fields

### Special Form Handling

For faculty 9270 (special case without discipline/programme):
- The script automatically removes discipline and programme fields using regex
- Uses Python's built-in XML processing for precise editing while preserving encoding

### Adding New Faculties

1. Add the faculty code to the Python scripts
2. Ensure the VSB web service supports the new faculty
3. Test the generated forms before deployment

## File Structure

```
vsb/
├── README.md                           # This documentation
├── evyuka_form_template.xml           # Base template for all forms
├── generate-forms.py                  # Form generation script (Python)
├── fetch-vocabularies.py              # Vocabulary fetching script (Python)
├── update_forms.py                    # Form validation group updater (Python)
├── controlled-vocabulary2value-pairs.xsl # XSLT transformer (reference)
├── evyuka_form_[FACULTY].xml          # Generated faculty forms
├── dir_[TYPE]_[FACULTY].xml           # Downloaded vocabulary data
├── vp_[TYPE]_[FACULTY].xml            # Processed value-pairs
├── vocab-backup-*/                    # Vocabulary backup directories
├── bak-*/                             # Form backup directories
└── update-forms-backup-*/             # Form validation update backups
```

## Regular Maintenance

### Updating Vocabularies
- Run `python fetch-vocabularies.py` periodically to update vocabularies from VSB web services
- Vocabularies are cached locally to reduce web service calls
- Concurrent processing makes updates fast and efficient
- Automatic fallback to test URLs if primary services are unavailable

### Form Updates
- Modify the base template (`evyuka_form_template.xml`) when changes are needed
- Run `python generate-forms.py` to regenerate all faculty forms
- Test in development environment before production deployment

### Form Validation Group Updates
- Run `python update_forms.py .` to ensure all evyuka forms have correct validation groups
- Use `python update_forms.py . --validate` to check validation groups without making changes
- Script preserves original XML formatting while only modifying validation-group elements
- Automatic backup creation with `update-forms-backup-` timestamped directories

### Backup Management
- Scripts automatically create timestamped backups before changes
- Manual backups are recommended before major updates
- Old backup directories can be cleaned up periodically

## Integration with DSpace 7

The forms are integrated into DSpace 7 through:
1. `submission-forms.xml` - Main form definitions file with XInclude directives
2. `item-submission.xml` - Maps collections to specific forms
3. Controlled vocabularies loaded from `vp_*.xml` files

## Technical Notes

### Character Encoding
- All files use UTF-8 encoding
- Special handling for Czech diacritics
- Python's built-in XML processing preserves encoding during transformations

### Web Service Dependencies
- VSB vocabulary services at `https://www.vsb.cz/edudocs/`
- Fallback to test services if primary services are unavailable
- XML validation for all downloaded data
- Configurable timeout handling for network requests

### Performance Considerations
- Concurrent downloads for faster vocabulary updates
- Vocabularies cached locally to reduce web service calls
- Incremental updates supported to minimize processing time
- Minimal impact on submission performance
- Optimized backup and validation processes

## Requirements

- **Python 3.6+** - Required for running the maintenance scripts
- **requests library** - For HTTP downloads (install with `pip install requests`)
- **Internet access** - For downloading vocabularies from VSB web services

## Troubleshooting

### Common Issues
- **XML validation errors**: Check template syntax and encoding
- **Network failures**: Scripts will attempt fallback URLs automatically
- **Missing Python dependencies**: Install required packages with `pip install requests`
- **Permission errors**: Ensure scripts have proper file permissions

### Validation Commands
```bash
# Run vocabulary updates with verbose output
python fetch-vocabularies.py --vocab-types program

# Validate forms after generation
python generate-forms.py --validate-only

# Check evyuka form validation groups
python update_forms.py . --validate --verbose

# Update validation groups with detailed logging
python update_forms.py . --verbose
```

## Manual File Maintenance

### AUD Faculty Vocabularies
The following vocabulary files for the AUD (Audio/Special collections) faculty were manually copied from the previous version due to errors during automatic fetching:

- `vp_branch_AUD.xml`
- `vp_program_AUD.xml` 
- `vp_subject-version_AUD.xml`
- `vp_subject_AUD.xml`

These files were not generated by the automatic vocabulary fetching process because of errors during the download from VSB web services. They should be manually updated when needed or when the web service issues are resolved.

### Recovery Steps
If automatic fetching for AUD faculty is restored:
1. Remove the manually copied files
2. Run `python fetch-vocabularies.py --faculties AUD` to test automatic generation
3. Verify the generated files contain correct data before deployment
