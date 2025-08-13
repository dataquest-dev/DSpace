#!/bin/bash
#
# VSB Vocabulary Fetching Script for DSpace 7
# Downloads controlled vocabularies from VSB web services and converts them to DSpace format
#
# Usage: ./fetch-vocabularies.sh
#

# Set script directory and configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Configuration
VSB_BASE_URL="https://www.vsb.cz/edudocs"
VSB_TEST_URL="https://www-test.vsb.cz/edudocs"
BACKUP_DIR="vocab-backup-$(date +%Y-%m-%d_%H-%M)"
TIMEOUT=30

# Create backup directory
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

# Function to check if file is empty or invalid
is_valid_vocabulary() {
  local file="$1"
  if [ ! -f "$file" ] || [ ! -s "$file" ]; then
    return 1
  fi

  # Check if file contains actual vocabulary data
  if grep -q "<node" "$file" 2>/dev/null; then
    return 0
  else
    return 1
  fi
}

# Function to backup existing vocabulary files
backup_existing() {
  echo "Creating backup of existing vocabularies in $BACKUP_DIR..."
  for dirtype in program branch subject subject-version; do
    for faculty in FAST FBI FS FEI HGF FMT EKF USP 9270; do
      if [ -f "dir_${dirtype}_${faculty}.xml" ]; then
        cp "dir_${dirtype}_${faculty}.xml" "$BACKUP_DIR/"
      fi
      if [ -f "vp_${dirtype}_${faculty}.xml" ]; then
        cp "vp_${dirtype}_${faculty}.xml" "$BACKUP_DIR/"
      fi
    done
  done
}

# Function to download vocabulary with fallback
download_vocabulary() {
  local dirtype="$1"
  local faculty="$2"
  local filename="dir_${dirtype}_${faculty}.xml"
  local temp_file="${filename}.tmp"

  echo -n "Fetching ${dirtype} vocabulary for ${faculty}... "

  # Try primary URL first
  if curl -4s --connect-timeout $TIMEOUT --max-time $((TIMEOUT*2)) \
     "${VSB_BASE_URL}/${dirtype}-directory?faculty=${faculty}" > "$temp_file" 2>/dev/null; then

    if is_valid_vocabulary "$temp_file" && validate_xml "$temp_file"; then
      mv "$temp_file" "$filename"
      echo "✓ Success"
      return 0
    fi
  fi

  # Try test URL as fallback
  echo -n "trying fallback... "
  if curl -4s --connect-timeout $TIMEOUT --max-time $((TIMEOUT*2)) \
     "${VSB_TEST_URL}/${dirtype}-directory?faculty=${faculty}" > "$temp_file" 2>/dev/null; then

    if is_valid_vocabulary "$temp_file" && validate_xml "$temp_file"; then
      mv "$temp_file" "$filename"
      echo "✓ Success (fallback)"
      return 0
    fi
  fi

  # Clean up temp file and report failure
  rm -f "$temp_file"
  echo "✗ Failed"
  return 1
}

# Function to convert vocabulary to value-pairs format
convert_vocabulary() {
  local dirtype="$1"
  local faculty="$2"
  local input_file="dir_${dirtype}_${faculty}.xml"
  local output_file="vp_${dirtype}_${faculty}.xml"

  if [ ! -f "$input_file" ]; then
    echo "Warning: Input file $input_file not found, skipping conversion"
    return 1
  fi

  echo -n "Converting ${dirtype} vocabulary for ${faculty}... "

  # Use XSLT transformation
  if command -v xsltproc >/dev/null 2>&1; then
    if xsltproc --stringparam value_pairs_name "vp_${dirtype}_${faculty}" \
                --stringparam dc_term "programme" \
                controlled-vocabulary2value-pairs.xsl \
                "$input_file" > "$output_file" 2>/dev/null; then

      if validate_xml "$output_file"; then
        echo "✓ Success"
        return 0
      fi
    fi
  fi

  echo "✗ Failed"
  return 1
}

# Main execution
echo "VSB Vocabulary Fetching Script for DSpace 7"
echo "==========================================="
echo

# Check dependencies
missing_deps=()
for cmd in curl xsltproc; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    missing_deps+=("$cmd")
  fi
done

if [ ${#missing_deps[@]} -ne 0 ]; then
  echo "Error: Missing required dependencies: ${missing_deps[*]}"
  echo "Please install the missing tools and try again."
  exit 1
fi

# Check if XSLT file exists
if [ ! -f "controlled-vocabulary2value-pairs.xsl" ]; then
  echo "Error: XSLT transformation file not found!"
  echo "Please ensure controlled-vocabulary2value-pairs.xsl exists in the current directory."
  exit 1
fi

# Create backup
backup_existing

echo "Downloading vocabularies from VSB web services..."
echo

# Download and process vocabularies
failed_downloads=0
failed_conversions=0
total_downloads=0
total_conversions=0

for dirtype in program branch subject subject-version; do
  for faculty in FAST FBI FS FEI HGF FMT EKF USP 9270; do
    total_downloads=$((total_downloads + 1))

    if download_vocabulary "$dirtype" "$faculty"; then
      total_conversions=$((total_conversions + 1))
      if ! convert_vocabulary "$dirtype" "$faculty"; then
        failed_conversions=$((failed_conversions + 1))
      fi
    else
      failed_downloads=$((failed_downloads + 1))

      # Try to restore from backup if download failed
      backup_file="$BACKUP_DIR/dir_${dirtype}_${faculty}.xml"
      if [ -f "$backup_file" ]; then
        cp "$backup_file" "dir_${dirtype}_${faculty}.xml"
        echo "  Restored from backup"
        if convert_vocabulary "$dirtype" "$faculty"; then
          total_conversions=$((total_conversions + 1))
        else
          failed_conversions=$((failed_conversions + 1))
        fi
      fi
    fi
  done
done

echo
echo "Summary:"
echo "========"
echo "Total vocabulary downloads attempted: $total_downloads"
echo "Successful downloads: $((total_downloads - failed_downloads))"
echo "Failed downloads: $failed_downloads"
echo "Successful conversions: $((total_conversions - failed_conversions))"
echo "Failed conversions: $failed_conversions"
echo
echo "Backup created in: $BACKUP_DIR"

if [ $failed_downloads -gt 0 ] || [ $failed_conversions -gt 0 ]; then
  echo
  echo "Warning: Some vocabularies could not be updated."
  echo "Check the backup directory and restore manually if needed."
  exit 1
else
  echo
  echo "All vocabularies updated successfully!"
  echo
  echo "Next steps:"
  echo "1. Regenerate forms with ./evyuka_form_template.sh if needed"
  echo "2. Restart DSpace to load the new vocabularies"
  echo "3. Test vocabulary functionality in the submission interface"
fi
