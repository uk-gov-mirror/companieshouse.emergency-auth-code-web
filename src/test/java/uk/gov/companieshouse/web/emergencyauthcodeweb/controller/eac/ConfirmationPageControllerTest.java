package uk.gov.companieshouse.web.emergencyauthcodeweb.controller.eac;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uk.gov.companieshouse.web.emergencyauthcodeweb.exception.ServiceException;
import uk.gov.companieshouse.web.emergencyauthcodeweb.model.emergencyauthcode.request.EACRequest;
import uk.gov.companieshouse.web.emergencyauthcodeweb.service.emergencyauthcode.EmergencyAuthCodeService;
import uk.gov.companieshouse.web.emergencyauthcodeweb.session.SessionService;

import java.time.LocalDateTime;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
public class ConfirmationPageControllerTest {
    private static final String REQUEST_ID = "abc123";
    private static final String EAC_CONFIRMATION_PATH = "/auth-code-requests/requests/" + REQUEST_ID + "/confirmation";
    private static final String EAC_CONFIRMATION_VIEW = "eac/confirmationPage";
    private static final String ERROR = "error";
    private static final String SIGNED_IN_USER_ID = "user123";

    private static final LocalDateTime SUBMITTED_AT = LocalDateTime.of(2020, 1, 1, 1, 1);

    private EACRequest eacRequest = new EACRequest();

    @Mock
    private EmergencyAuthCodeService emergencyAuthCodeService;

    @Mock
    private SessionService sessionService;

    private MockMvc mockMvc;

    @InjectMocks
    private ConfirmationPageController controller;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        this.eacRequest.setUserId(SIGNED_IN_USER_ID);
        lenient().when(sessionService.getSignedInUserId()).thenReturn(SIGNED_IN_USER_ID);
    }

    @Test
    @DisplayName("Get confirmation view - successful")
    void getRequestSuccessful() throws Exception {
        eacRequest.setSubmittedAt(SUBMITTED_AT);
        when(emergencyAuthCodeService.getEACRequest(REQUEST_ID)).thenReturn(eacRequest);

        this.mockMvc.perform(get(EAC_CONFIRMATION_PATH))
                .andExpect(status().isOk())
                .andExpect(view().name(EAC_CONFIRMATION_VIEW))
                .andExpect(model().attributeExists("submittedAt"))
                .andExpect(model().attributeExists("submittedMonth"))
                .andExpect(model().attributeExists("submittedTime"));
    }

    @Test
    @DisplayName("Get confirmation view - unsuccessful - ServiceException thrown by EmergencyAuthCodeService")
    void getRequestUnsuccessful_ServiceException() throws Exception {
        when(emergencyAuthCodeService.getEACRequest(REQUEST_ID)).thenThrow(ServiceException.class);

        this.mockMvc.perform(get(EAC_CONFIRMATION_PATH))
                .andExpect(status().isOk())
                .andExpect(view().name(ERROR));
    }

    @Test
    @DisplayName("Get confirmation view - returns error when user does not own request")
    void getRequestUnsuccessful_UserDoesNotOwnRequest() throws Exception {
        eacRequest.setSubmittedAt(SUBMITTED_AT);
        eacRequest.setUserId("differentUser");
        when(emergencyAuthCodeService.getEACRequest(REQUEST_ID)).thenReturn(eacRequest);

        this.mockMvc.perform(get(EAC_CONFIRMATION_PATH))
                .andExpect(status().isOk())
                .andExpect(view().name(ERROR));
    }

    @Test
    @DisplayName("Get confirmation view - returns error when session user ID is null")
    void getRequestUnsuccessful_NullSessionUserId() throws Exception {
        eacRequest.setSubmittedAt(SUBMITTED_AT);
        when(emergencyAuthCodeService.getEACRequest(REQUEST_ID)).thenReturn(eacRequest);
        when(sessionService.getSignedInUserId()).thenReturn(null);

        this.mockMvc.perform(get(EAC_CONFIRMATION_PATH))
                .andExpect(status().isOk())
                .andExpect(view().name(ERROR));
    }
}
