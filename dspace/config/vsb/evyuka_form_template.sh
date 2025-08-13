#!/bin/sh
#
# 1) edit evyuka_form_template.xml
# 2) run evyuka_form_template.sh to generate faculty-specific forms, e.g. evyuka_form_FAST.xml
#
for faculty in 9270 FAST FBI FS FEI HGF FMT EKF USP; do
  cp evyuka_form_template.xml evyuka_form_${faculty}.xml;
  sed -i "s/PARAM/${faculty}/" evyuka_form_${faculty}.xml;
done
## 9270 not included in list of faculties because we need to remove choice of discipline and programme from its form
## this could be done with:
## xmlstarlet ed --inplace -d "//field[dc-element='programme']" -d "//field[dc-element='discipline']" evyuka_form_9270.xml
## but that command also encodes non-ASCII characters.
# 
# This seems to restore encoding:
cat evyuka_form_9270.xml | xmlstarlet ed -P -d "//field[dc-element='programme']" -d "//field[dc-element='discipline']" | xmlstarlet fo -e utf-8 - | tee evyuka_form_9270.xml > /dev/null
