/**
 * Simple test to verify the maskEmail functionality in Utils class
 */
package org.dspace.core;

public class EmailMaskingTest {
    public static void main(String[] args) {
        System.out.println("Testing Utils.maskEmail() method:");
        
        String[] testEmails = {
            "john.doe@example.com",
            "j@example.com", 
            "admin@dspace.org",
            "very.long.email.address@university.edu",
            null,
            "",
            "invalid-email",
            "test@"
        };
        
        for (String email : testEmails) {
            String masked = Utils.maskEmail(email);
            System.out.println("Original: '" + email + "' -> Masked: '" + masked + "'");
        }
    }
}