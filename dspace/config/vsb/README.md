# VSB Template System for DSpace 7

## Overview

The VSB (Vysoká škola báňská - Technical University of Ostrava) template system provides maintenance scripts for automatically generating submission forms and controlled vocabularies for different faculties. This system is designed for DSpace 7 and provides tools to maintain faculty-specific submission workflows.

## Template Generation Process

### 1. Form Templates
The system uses a template-based approach to generate faculty-specific submission forms:

- **Base template**: `evyuka_form_template.xml` - Contains the base form structure
- **Generation scripts**: `evyuka_form_template.sh` / `evyuka_form_template.bat` - Generates faculty-specific forms
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

- **Fetch scripts**: `fetch-vocabularies.sh` / `fetch-vocabularies.bat` - Downloads vocabularies from VSB web services
- **XSLT transformer**: `controlled-vocabulary2value-pairs.xsl` - Converts XML to DSpace format
- **Vocabulary types**: program, branch, subject, subject-version

## Maintenance Scripts

### Key Scripts:

#### Template Generation
- **Windows**: `evyuka_form_template.bat`
- **Linux/Unix**: `evyuka_form_template.sh`

These scripts generate faculty-specific submission forms from the base template.

#### Vocabulary Updates
- **Windows**: `fetch-vocabularies.bat`
- **Linux/Unix**: `fetch-vocabularies.sh`

These scripts download and update controlled vocabularies from VSB web services.

#### XSLT Transformation
- `controlled-vocabulary2value-pairs.xsl` - Converts downloaded XML vocabularies to DSpace value-pairs format

### Script Features:

- **Automatic backups**: Creates timestamped backups before making changes
- **Error handling**: Validates XML and provides fallback options
- **Dependency checking**: Verifies required tools are available
- **Progress reporting**: Shows detailed status and completion summaries
- **Recovery mechanisms**: Restores from backups on failure

## Usage Instructions

### Regenerating Forms

1. **Edit the base template** (if needed):
   ```bash
   nano evyuka_form_template.xml
   ```

2. **Run the generation script**:
   
   **Windows:**
   ```cmd
   evyuka_form_template.bat
   ```
   
   **Linux/Unix:**
   ```bash
   ./evyuka_form_template.sh
   ```

3. **Update vocabularies**:
   
   **Windows:**
   ```cmd
   fetch-vocabularies.bat
   ```
   
   **Linux/Unix:**
   ```bash
   ./fetch-vocabularies.sh
   ```

### Special Form Handling

For faculty 9270 (special case without discipline/programme):
- The scripts automatically remove discipline and programme fields
- Uses xmlstarlet for precise XML editing while preserving encoding

### Adding New Faculties

1. Add the faculty code to the generation scripts
2. Ensure the VSB web service supports the new faculty
3. Test the generated forms before deployment

## File Structure

```
vsb/
├── README.md                           # This documentation
├── evyuka_form_template.xml           # Base template for all forms
├── evyuka_form_template.sh            # Form generation script (Linux/Unix)
├── evyuka_form_template.bat           # Form generation script (Windows)
├── fetch-vocabularies.sh              # Vocabulary fetching script (Linux/Unix)
├── fetch-vocabularies.bat             # Vocabulary fetching script (Windows)
├── controlled-vocabulary2value-pairs.xsl # XSLT transformer
├── evyuka_form_[FACULTY].xml          # Generated faculty forms
├── dir_[TYPE]_[FACULTY].xml           # Downloaded vocabulary data
├── vp_[TYPE]_[FACULTY].xml            # Processed value-pairs
└── bak-*/                             # Backup directories
```

## Regular Maintenance

### Updating Vocabularies
- Run vocabulary fetch scripts periodically to update vocabularies from VSB web services
- Vocabularies are cached locally to reduce web service calls
- Incremental updates are supported

### Form Updates
- Modify the base template (`evyuka_form_template.xml`) when changes are needed
- Regenerate all faculty forms using the generation scripts
- Test in development environment before production deployment

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
- xmlstarlet preserves encoding during transformations

### Web Service Dependencies
- VSB vocabulary services at `https://www.vsb.cz/edudocs/`
- Fallback to test services if primary services are unavailable
- XML validation for all downloaded data
- Timeout handling for network requests

### Performance Considerations
- Vocabularies cached locally to reduce web service calls
- Incremental updates supported to minimize processing time
- Minimal impact on submission performance
- Backup and validation processes are optimized for speed

## Troubleshooting

### Common Issues
- **XML validation errors**: Check template syntax and encoding
- **Network failures**: Scripts will attempt fallback URLs automatically
- **Missing dependencies**: Install required tools (curl, xsltproc, xmlstarlet)
- **Permission errors**: Ensure scripts have proper file permissions

### Validation Commands
```bash
# Validate XML files
xmllint --noout --format *.xml

# Check script dependencies
which curl xsltproc xmlstarlet

# Test vocabulary connectivity
curl -s "https://www.vsb.cz/edudocs/program-directory?faculty=FAST"
```

### Recovery Procedures
- Scripts automatically attempt to restore from backups on failure
- Manual restoration can be done from timestamped backup directories
- Always test generated forms in development before production deployment
