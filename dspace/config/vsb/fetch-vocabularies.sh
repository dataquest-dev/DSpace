#!/bin/sh
#
# TODO:
# * [DONE] check validity of downloaded data (invalid or empty data will break loading input-forms.xml and prevent any submissions to DSpace)
#   xmllint -noout -format *.xml
# * back up all files before update
# * rotate backups    

cd /home/tomcat/dspace/config/vsb

# add root node (not needed anymore, was added to the web service)
# usage: curl "http://..." | awk_cmd > out.xml
awk_cmd () {
  awk '/<node/{gsub(/<node/, "    <node")};{print}; NR==1 {print "<node id=\"\" label=\"\">\n  <isComposedBy>"}; END{print "  </isComposedBy>\n</node>"}'
}

for dirtype in program branch subject subject-version; do
  for faculty in FAST FBI FS FEI HGF FMT EKF USP 9270; do
#    echo "fetching dir_${dirtype}_${faculty}.xml";
#    curl -4s "https://www-test.vsb.cz/edudocs/${dirtype}-directory?faculty=${faculty}" > "dir_${dirtype}_${faculty}.xml"
    curl -4s "https://www.vsb.cz/edudocs/${dirtype}-directory?faculty=${faculty}" > "dir_${dirtype}_${faculty}.xml"
    xmllint -noout dir_${dirtype}_${faculty}.xml 2> /dev/null
    if [ $? -eq 0 ]; then
      xsltproc --stringparam value_pairs_name vp_${dirtype}_${faculty} --stringparam dc_term programme controlled-vocabulary2value-pairs.xsl dir_${dirtype}_${faculty}.xml > vp_${dirtype}_${faculty}.xml
    fi
  done
done


#echo "";
#echo "input-forms.xml header:"
#echo "<!DOCTYPE input-forms [";
#for dirtype in program branch subject subject-version; do
#  for faculty in FAST FBI FS FEI HGF FMT EKF USP; do
#    echo "<!ENTITY vp_${dirtype}_${faculty} SYSTEM \"/home/tomcat/dspace/config/vsb/vp_${dirtype}_${faculty}.xml\">";
#  done
#done
#echo "]>";
#echo "";
#echo "input-forms.xml includes for <form-value-pairs>:"
#for dirtype in program branch subject subject-version; do
#  for faculty in FAST FBI FS FEI HGF FMT EKF USP; do
#    echo "&vp_${dirtype}_${faculty};";
#  done
#done

