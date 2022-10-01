package org.dspace.app.rest.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * This is the exception to capture details about a not existing linked resource
 *
 * @author Andrea Bollini (andrea.bollini at 4science.it)
 */
@ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "This Clarin License is not supported in the CLARIN/DSpace")
public class ClarinLicenseNotFoundException extends RuntimeException {
    String apiCategory;
    String model;
    String id;

    public ClarinLicenseNotFoundException(String apiCategory) {
        this.apiCategory = apiCategory;
        this.model = model;
        this.id = id;
    }

}
