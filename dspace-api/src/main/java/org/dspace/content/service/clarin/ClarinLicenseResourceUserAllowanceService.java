package org.dspace.content.service.clarin;

import java.util.UUID;

public interface ClarinLicenseResourceUserAllowanceService {
    boolean verifyToken(String resourceID, String token);
    boolean isUserAllowedToAccessTheResource(UUID userId, String resourceId);
}
