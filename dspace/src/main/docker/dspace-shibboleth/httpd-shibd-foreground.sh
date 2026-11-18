#!/bin/bash
#
# The contents of this file are subject to the license and copyright
# detailed in the LICENSE and NOTICE files at the root of the source
# tree and available online at
#
# http://www.dspace.org/license/
#

# This script simply starts up the Shibboleth Daemon & Apache in the foreground
set -e

# Replace the ${APACHE_SERVER_NAME} placeholder in our shibboleth2.xml with the current value of the
# $APACHE_SERVER_NAME environment variable.
sed -i "s/\${APACHE_SERVER_NAME}/$APACHE_SERVER_NAME/g" /etc/shibboleth/shibboleth2.xml

# Create the SP key pair on the first start, so it is never part of the image.
# -b skips it when the files already exist (e.g. after a restart or when mounted).
shib-keygen -b -o /etc/shibboleth
if [ ! -s /etc/shibboleth/sp-key.pem ] || [ ! -s /etc/shibboleth/sp-cert.pem ]; then
    echo "Both sp-key.pem and sp-cert.pem must exist in /etc/shibboleth." >&2
    exit 1
fi

# Start the Shibboleth daemon
/etc/init.d/shibd start

# Remove existing Apache PID files (if any)
rm -f /var/run/apache2/apache2.pid

# Start Apache (in foreground)
exec /usr/sbin/apache2ctl -DFOREGROUND
