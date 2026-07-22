package uk.gov.companieshouse.web.emergencyauthcodeweb.session.impl;

import org.springframework.stereotype.Component;
import uk.gov.companieshouse.session.Session;
import uk.gov.companieshouse.session.handler.SessionHandler;
import uk.gov.companieshouse.session.model.SignInInfo;
import uk.gov.companieshouse.session.model.UserProfile;
import uk.gov.companieshouse.web.emergencyauthcodeweb.session.SessionService;

import java.util.Map;

@Component
public class SessionServiceImpl implements SessionService {

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Object> getSessionDataFromContext() {

        return SessionHandler.getSessionDataFromContext();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSignedInUserId() {

        Session session = getSessionFromContext();
        if (session != null) {
            SignInInfo signInInfo = session.getSignInInfo();
            if (signInInfo != null) {
                UserProfile userProfile = signInInfo.getUserProfile();
                if (userProfile != null) {
                    return userProfile.getId();
                }
            }
        }
        return null;
    }

    Session getSessionFromContext() {
        return SessionHandler.getSessionFromContext();
    }
}
