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
 * {@code dc.date.embargoend}) that the import cannot turn into a resource policy.
 *
 * <p>Checked on purpose. Every one of these conditions used to be a log line followed by a {@code return},
 * after which the item was archived anyway - and {@code installItem} then gave its bitstreams the collection's
 * undated default READ policy, so the files were public although their own metadata says they are closed, with
 * exit code 0. There is no correct way to swallow this exception: an embargo that cannot be written means the
 * package has to be refused.</p>
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
