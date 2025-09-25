#!/bin/bash

# Script to add validation-group to evyuka forms

FILES="evyuka_form_EKF.xml evyuka_form_FEI.xml evyuka_form_FMT.xml evyuka_form_FS.xml evyuka_form_HGF.xml evyuka_form_USP.xml evyuka_form_9270.xml evyuka_form_AUD.xml"

for file in $FILES; do
  if [ -f "$file" ]; then
    echo "Processing $file..."
    
    # Add validation-group to evyuka.subject.version
    sed -i '/<dc-element>subject<\/dc-element>/{
      N
      /<dc-qualifier>version<\/dc-qualifier>/{
        :loop
        N
        /<\/hint>/!b loop
        s/<\/hint>/<\/hint>\n           <validation-group>evyuka-codes<\/validation-group>/
      }
    }' "$file"
    
    # Add validation-group to evyuka.subject (no qualifier)  
    sed -i '/<dc-element>subject<\/dc-element>/{
      N
      /<dc-qualifier><\/dc-qualifier>/{
        :loop
        N
        /<\/hint>/!b loop
        s/<\/hint>/<\/hint>\n           <validation-group>evyuka-codes<\/validation-group>/
      }
    }' "$file"
    
    # Add validation-group to evyuka.discipline
    sed -i '/<dc-element>discipline<\/dc-element>/{
      :loop
      N
      /<\/hint>/!b loop
      s/<\/hint>/<\/hint>\n           <validation-group>evyuka-codes<\/validation-group>/
    }' "$file"
    
    # Add validation-group to evyuka.programme
    sed -i '/<dc-element>programme<\/dc-element>/{
      :loop
      N
      /<\/hint>/!b loop
      s/<\/hint>/<\/hint>\n           <validation-group>evyuka-codes<\/validation-group>/
    }' "$file"
    
    echo "Finished processing $file"
  else
    echo "File $file not found"
  fi
done

echo "All files processed"