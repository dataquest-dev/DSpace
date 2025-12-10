#!/bin/bash
#
# The contents of this file are subject to the license and copyright
# detailed in the LICENSE and NOTICE files at the root of the source
# tree and available online at
#
# http://www.dspace.org/license/
#
set -e

CONF="$PGDATA/postgresql.conf"
LOG_CONF="/etc/postgresql/log.properties.conf"

echo "Setting up PostgreSQL logging configuration..."

# Wait for postgresql.conf to be created (up to 10 seconds)
TIMEOUT=10
ELAPSED=0
while [ ! -f "$CONF" ] && [ $ELAPSED -lt $TIMEOUT ]; do
  echo "Waiting for postgresql.conf to be created... (${ELAPSED}s)"
  sleep 1
  ELAPSED=$((ELAPSED + 1))
done

# Verify the file exists
if [ ! -f "$CONF" ]; then
  echo "ERROR: postgresql.conf not found at $CONF after ${TIMEOUT}s"
  echo "Contents of $PGDATA:"
  ls -la "$PGDATA" || true
  exit 1
fi

echo "Found postgresql.conf at $CONF"

# Add the include directive only if it's not already present
if ! grep -q "$LOG_CONF" "$CONF"; then
  echo "include '$LOG_CONF'" >> "$CONF"
  echo "Successfully added logging include to $CONF"
  echo "Logging configuration:"
  cat "$LOG_CONF"
else
  echo "Logging include already present in $CONF"
fi

echo "Logging setup completed successfully"
