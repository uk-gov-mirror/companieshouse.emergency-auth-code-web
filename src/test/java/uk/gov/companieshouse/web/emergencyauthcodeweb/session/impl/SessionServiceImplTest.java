package uk.gov.companieshouse.web.emergencyauthcodeweb.session.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.companieshouse.session.Session;
import uk.gov.companieshouse.session.model.SignInInfo;
import uk.gov.companieshouse.session.model.UserProfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class SessionServiceImplTest {

    private final SessionServiceImpl sessionService = spy(new SessionServiceImpl());

    @Test
    @DisplayName("Returns user ID when session is fully populated")
    void getSignedInUserId_returnsUserId() {
        Session session = mock(Session.class);
        SignInInfo signInInfo = mock(SignInInfo.class);
        UserProfile userProfile = mock(UserProfile.class);

        doReturn(session).when(sessionService).getSessionFromContext();
        when(session.getSignInInfo()).thenReturn(signInInfo);
        when(signInInfo.getUserProfile()).thenReturn(userProfile);
        when(userProfile.getId()).thenReturn("user-abc-123");

        assertEquals("user-abc-123", sessionService.getSignedInUserId());
    }

    @Test
    @DisplayName("Returns null when session is null")
    void getSignedInUserId_nullSession() {
        doReturn(null).when(sessionService).getSessionFromContext();

        assertNull(sessionService.getSignedInUserId());
    }

    @Test
    @DisplayName("Returns null when SignInInfo is null")
    void getSignedInUserId_nullSignInInfo() {
        Session session = mock(Session.class);
        doReturn(session).when(sessionService).getSessionFromContext();
        when(session.getSignInInfo()).thenReturn(null);

        assertNull(sessionService.getSignedInUserId());
    }

    @Test
    @DisplayName("Returns null when UserProfile is null")
    void getSignedInUserId_nullUserProfile() {
        Session session = mock(Session.class);
        SignInInfo signInInfo = mock(SignInInfo.class);

        doReturn(session).when(sessionService).getSessionFromContext();
        when(session.getSignInInfo()).thenReturn(signInInfo);
        when(signInInfo.getUserProfile()).thenReturn(null);

        assertNull(sessionService.getSignedInUserId());
    }
}
