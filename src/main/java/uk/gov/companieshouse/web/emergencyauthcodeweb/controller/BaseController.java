package uk.gov.companieshouse.web.emergencyauthcodeweb.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import uk.gov.companieshouse.logging.Logger;
import uk.gov.companieshouse.logging.LoggerFactory;
import uk.gov.companieshouse.web.emergencyauthcodeweb.EmergencyAuthCodeWebApplication;
import uk.gov.companieshouse.web.emergencyauthcodeweb.model.emergencyauthcode.request.EACRequest;
import uk.gov.companieshouse.web.emergencyauthcodeweb.service.navigation.NavigatorService;
import uk.gov.companieshouse.web.emergencyauthcodeweb.session.SessionService;

import jakarta.servlet.http.HttpServletRequest;

public abstract class BaseController {

    @Autowired
    protected NavigatorService navigatorService;

    @Autowired
    protected SessionService sessionService;

    protected static final Logger LOGGER = LoggerFactory
        .getLogger(EmergencyAuthCodeWebApplication.APPLICATION_NAME_SPACE);

    protected static final String ERROR_VIEW = "error";

    protected BaseController() {
    }

    @ModelAttribute("templateName")
    protected abstract String getTemplateName();

    protected void addBackPageAttributeToModel(Model model, String... pathVars) {

        model.addAttribute("backButton", navigatorService.getPreviousControllerPath(this.getClass(), pathVars));
    }

    @ModelAttribute
    public void addCommonAttributes(Model model) {
        model.addAttribute("headerText", "Request an authentication code to be sent to a home address");
        model.addAttribute("headerURL", "/auth-code-requests/start");
        model.addAttribute("phaseBanner", "ALPHA");
        model.addAttribute("phaseBannerLink", "https://www.smartsurvey.co.uk/s/request-auth-code-feedback");
    }

    protected boolean isRequestOwnedBySignedInUser(EACRequest eacRequest, HttpServletRequest request) {
        String signedInUserId = sessionService.getSignedInUserId();
        String requestUserId = eacRequest.getUserId();

        if (signedInUserId == null || !signedInUserId.equals(requestUserId)) {
            LOGGER.errorRequest(request,
                    "Signed-in user does not own this auth code request");
            return false;
        }
        return true;
    }
}
