/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.itemimport;

/**
 * Thrown when a SAF package claims an embargo ({@code dc.rights.access=embargoedAccess} or
 * {@code dc.date.embargoend}) that the import cannot turn into a resource policy. It is checked because the
 * package has to be refused: an item archived without its embargo policy gets the collection default READ
 * policy from {@code installItem} and its files become public.
 */
public class EmbargoMetadataException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * @param message what the package asks for and why it cannot be done
     */
    public EmbargoMetadataException(String message) {
        super(message);
    }

    /**
     * @param message what the package asks for and why it cannot be done
     * @param cause   the underlying failure
     */
    public EmbargoMetadataException(String message, Throwable cause) {
        super(message, cause);
    }
}
