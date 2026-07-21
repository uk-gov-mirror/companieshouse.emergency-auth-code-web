package uk.gov.companieshouse.web.emergencyauthcodeweb.session;

import java.util.Map;

/**
 * The {@code SessionService} interface provides an abstraction that can be
 * used when testing {@code SessionHandler} static methods, without imposing
 * the use of a test framework that supports mocking of static methods.
 */
public interface SessionService {

    /**
     * Returns a map comprising data retrieved from the current session.
     *
     * @return a map of session data
     */
    Map<String, Object> getSessionDataFromContext();

    /**
     * Returns the user ID of the currently signed-in user from the session.
     *
     * @return the signed-in user's ID, or null if not available
     */
    String getSignedInUserId();
}
