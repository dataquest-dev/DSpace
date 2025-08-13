#!/bin/bash
#
# VSB Template Generation Script for DSpace 7
# Generates faculty-specific submission forms from a base template
#
# Usage:
# 1) Edit evyuka_form_template.xml to modify the base template
# 2) Run this script to generate faculty-specific forms
#

# Set script directory and create backup
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "VSB Template Generation Script for DSpace 7"
echo "=========================================="

# Create backup directory with timestamp
BACKUP_DIR="bak-$(date +%Y-%m-%d_%H-%M)"
mkdir -p "$BACKUP_DIR"

# Function to validate XML files
validate_xml() {
  local file="$1"
  if command -v xmllint >/dev/null 2>&1; then
    xmllint --noout "$file" 2>/dev/null
    return $?
  else
    echo "Warning: xmllint not available, skipping XML validation"
    return 0
  fi
}

# Function to backup existing files
backup_existing() {
  echo "Creating backup in $BACKUP_DIR..."
  for faculty in 9270 FAST FBI FS FEI HGF FMT EKF USP AUD; do
    if [ -f "evyuka_form_${faculty}.xml" ]; then
      cp "evyuka_form_${faculty}.xml" "$BACKUP_DIR/"
    fi
  done
}

# Validate template file
echo "Validating template file..."
if [ ! -f "evyuka_form_template.xml" ]; then
  echo "Error: Template file evyuka_form_template.xml not found!"
  exit 1
fi

if ! validate_xml "evyuka_form_template.xml"; then
  echo "Error: Template file contains invalid XML!"
  exit 1
fi

# Create backup
backup_existing

echo "Generating faculty-specific forms..."

# Generate forms for all faculties except 9270 (special handling needed)
for faculty in FAST FBI FS FEI HGF FMT EKF USP AUD; do
  echo "Generating evyuka_form_${faculty}.xml..."
  cp evyuka_form_template.xml evyuka_form_${faculty}.xml

  # Replace PARAM placeholder with faculty code
  if command -v sed >/dev/null 2>&1; then
    sed -i "s/PARAM/${faculty}/g" evyuka_form_${faculty}.xml
  else
    # Fallback for systems without sed
    perl -pe "s/PARAM/${faculty}/g" evyuka_form_${faculty}.xml > temp_${faculty}.xml && mv temp_${faculty}.xml evyuka_form_${faculty}.xml
  fi

  # Validate generated file
  if validate_xml "evyuka_form_${faculty}.xml"; then
    echo "  Successfully generated evyuka_form_${faculty}.xml"
  else
    echo "  Error: Generated file evyuka_form_${faculty}.xml contains invalid XML!"
    # Restore from backup if available
    if [ -f "$BACKUP_DIR/evyuka_form_${faculty}.xml" ]; then
      cp "$BACKUP_DIR/evyuka_form_${faculty}.xml" .
      echo "  Restored previous version from backup"
    fi
  fi
done

# Special handling for faculty 9270 - remove discipline and programme fields
echo "Generating evyuka_form_9270.xml (special handling)..."
cp evyuka_form_template.xml evyuka_form_9270.xml
sed -i "s/PARAM/9270/g" evyuka_form_9270.xml

# Remove discipline and programme fields for 9270
if command -v xmlstarlet >/dev/null 2>&1; then
  # Use xmlstarlet for precise XML editing while preserving encoding
  xmlstarlet ed --inplace \
    -d "//field[dc-schema='evyuka' and dc-element='discipline']" \
    -d "//field[dc-schema='evyuka' and dc-element='programme']" \
    evyuka_form_9270.xml

  # Fix encoding if needed
  if ! validate_xml "evyuka_form_9270.xml"; then
    echo "Fixing encoding for evyuka_form_9270.xml..."
    xmlstarlet ed -P \
      -d "//field[dc-schema='evyuka' and dc-element='discipline']" \
      -d "//field[dc-schema='evyuka' and dc-element='programme']" \
      evyuka_form_template.xml | \
    sed "s/PARAM/9270/g" | \
    xmlstarlet fo -e utf-8 - > evyuka_form_9270_temp.xml && \
    mv evyuka_form_9270_temp.xml evyuka_form_9270.xml
  fi
else
  echo "Warning: xmlstarlet not available, using sed fallback"
  # Fallback method using sed (less precise but functional)
  # Remove entire row blocks containing discipline or programme fields
  sed -i '/dc-schema>evyuka<\/dc-schema>/{
    N
    /<dc-element>discipline<\/dc-element>/{
      :a
      N
      /<\/row>/!ba
      d
    }
  }' evyuka_form_9270.xml

  sed -i '/dc-schema>evyuka<\/dc-schema>/{
    N
    /<dc-element>programme<\/dc-element>/{
      :a
      N
      /<\/row>/!ba
      d
    }
  }' evyuka_form_9270.xml
fi

# Validate final 9270 file
if validate_xml "evyuka_form_9270.xml"; then
  echo "  Successfully generated evyuka_form_9270.xml"
else
  echo "  Error: Generated file evyuka_form_9270.xml contains invalid XML!"
  if [ -f "$BACKUP_DIR/evyuka_form_9270.xml" ]; then
    cp "$BACKUP_DIR/evyuka_form_9270.xml" .
    echo "  Restored previous version from backup"
  fi
fi

echo ""
echo "Form generation completed!"
echo "Backup created in: $BACKUP_DIR"
echo ""
echo "Next steps:"
echo "1. Run ./fetch-vocabularies.sh to update controlled vocabularies"
echo "2. Restart DSpace to load the new forms"
echo "3. Test form functionality in the submission interface"
