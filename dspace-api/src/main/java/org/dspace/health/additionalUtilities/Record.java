/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.health.additionalUtilities;

public interface Record {
    public void setLineNumber(int lineNumber);

    public int getLineNumber();

    public void setValid(boolean valid);

    public boolean isValid();

}
