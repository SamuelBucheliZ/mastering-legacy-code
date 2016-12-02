package org.apache.roller.weblogger.ui.struts2.core;

import com.opensymphony.xwork2.Action;
import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.UserManager;
import org.apache.roller.weblogger.business.Weblogger;
import org.apache.roller.weblogger.config.AuthMethod;
import org.apache.roller.weblogger.config.WebloggerRuntimeConfigInstance;
import org.apache.roller.weblogger.pojos.User;
import org.apache.roller.weblogger.ui.core.RollerSession;
import org.apache.roller.weblogger.util.MailUtilInstance;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import javax.servlet.http.HttpSession;

import java.util.Date;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;


public class RegisterTest {

    // mocked dependencies
    private WebloggerRuntimeConfigInstance mockWebloggerRuntimeConfig;
    private Weblogger mockWeblogger;
    private UserManager mockUserManager;
    private HttpSession mockSession;
    private MailUtilInstance mockMailUtil;

    // test data
    private AuthMethod testAuthMethod;
    private ProfileBean testBean;

    // spy for class under test
    private Register registerSpy;


    @Before
    public void setUp() {
        mockWebloggerRuntimeConfig = mock(WebloggerRuntimeConfigInstance.class);
        mockWeblogger = mock(Weblogger.class);
        mockUserManager = mock(UserManager.class);
        mockSession = mock(HttpSession.class);
        mockMailUtil = mock(MailUtilInstance.class);

        testAuthMethod = AuthMethod.ROLLERDB;

        testBean = new ProfileBean();
        testBean.setUserName("testUser");
        testBean.setPasswordText("testPasswordText");
        testBean.setPasswordConfirm("testPasswordText");

        registerSpy = spy(new Register());
        doReturn(mockWebloggerRuntimeConfig).when(registerSpy).getWebloggerRuntimeConfig();
        doReturn(mockWeblogger).when(registerSpy).getWeblogger();
        doReturn(mockUserManager).when(registerSpy).getUserManager();
        doReturn(mockSession).when(registerSpy).getSession();
        doReturn(mockMailUtil).when(registerSpy).getMailUtil();
        doReturn(testAuthMethod).when(registerSpy).getAuthMethod();
    }

    @Test
    public void test_save_shortestPathToSuccess() throws Exception {
        registerSpy.setBean(testBean);

        String returnValue = registerSpy.save();

        assertEquals(Action.SUCCESS, returnValue);
        assertNull(registerSpy.getActivationStatus());
        assertEquals("ProfileBean{id='null', userName='testUser', password='null', screenName='null', fullName='null', emailAddress='null', locale='null', timeZone='null', openIdUrl='null', passwordText='testPasswordText', passwordConfirm='testPasswordText'}", registerSpy.getBean().getStateString());

        verify(mockWebloggerRuntimeConfig).getBooleanProperty("users.registration.enabled");
        verify(mockUserManager).getUserCount();
        verify(mockWebloggerRuntimeConfig).getBooleanProperty("user.account.email.activation");

        ArgumentCaptor<User> userArgumentCaptor = ArgumentCaptor.forClass(User.class);
        verify(mockUserManager).addUser(userArgumentCaptor.capture());
        User addUserArgument = userArgumentCaptor.getValue();
        assertEquals("User{userName='testUser', password='testPasswordText', openIdUrl='OpenIdUrl{openIdUrl='null'}', screenName='null', fullName='null', emailAddress='null', locale='null', timeZone='null', enabled=true}", addUserArgument.getStateString());
        assertNotNull(addUserArgument.getDateCreated());
        assertNull(addUserArgument.getActivationCode());

        verify(mockWeblogger).flush();

        verify(mockSession).removeAttribute(RollerSession.ROLLER_SESSION);
        verify(mockSession).invalidate();
    }

    @Test
    public void test_save_successReturnCode_withActivationEnabled_withoutRetry() throws WebloggerException {
        registerSpy.setBean(testBean);

        when(mockWebloggerRuntimeConfig.getBooleanProperty("user.account.email.activation")).thenReturn(true);
        when(mockUserManager.createActivationCode()).thenReturn("testActivationCode");

        String returnValue = registerSpy.save();

        assertEquals(Action.SUCCESS, returnValue);
        assertEquals("pending", registerSpy.getActivationStatus());
        assertEquals("ProfileBean{id='null', userName='testUser', password='null', screenName='null', fullName='null', emailAddress='null', locale='null', timeZone='null', openIdUrl='null', passwordText='testPasswordText', passwordConfirm='testPasswordText'}", registerSpy.getBean().getStateString());

        verify(mockWebloggerRuntimeConfig).getBooleanProperty("users.registration.enabled");
        verify(mockUserManager).getUserCount();
        verify(mockWebloggerRuntimeConfig).getBooleanProperty("user.account.email.activation");

        verify(mockUserManager).createActivationCode();

        ArgumentCaptor<User> userArgumentCaptor = ArgumentCaptor.forClass(User.class);
        verify(mockUserManager).addUser(userArgumentCaptor.capture());
        User addUserArgument = userArgumentCaptor.getValue();
        assertEquals("User{userName='testUser', password='testPasswordText', openIdUrl='OpenIdUrl{openIdUrl='null'}', screenName='null', fullName='null', emailAddress='null', locale='null', timeZone='null', enabled=false}", addUserArgument.getStateString());
        assertNotNull(addUserArgument.getDateCreated());
        assertEquals("testActivationCode", addUserArgument.getActivationCode());

        verify(mockWeblogger).flush();

        verify(mockMailUtil).trySendUserActivationEmail(userArgumentCaptor.capture());
        User sendMailUserArgument = userArgumentCaptor.getValue();
        assertEquals(addUserArgument, sendMailUserArgument);

        verify(mockSession).removeAttribute(RollerSession.ROLLER_SESSION);
        verify(mockSession).invalidate();
    }

    @Test
    public void test_save_withOpenId() throws WebloggerException {
        testBean.setOpenIdUrl("http://open.id.url/");

        registerSpy.setBean(testBean);

        String returnValue = registerSpy.save();

        assertEquals(Action.SUCCESS, returnValue);
        assertNull(registerSpy.getActivationStatus());
        assertEquals("ProfileBean{id='null', userName='testUser', password='null', screenName='null', fullName='null', emailAddress='null', locale='null', timeZone='null', openIdUrl='http://open.id.url/', passwordText='testPasswordText', passwordConfirm='testPasswordText'}", registerSpy.getBean().getStateString());


        verify(mockWebloggerRuntimeConfig).getBooleanProperty("users.registration.enabled");
        verify(mockUserManager).getUserCount();
        verify(mockWebloggerRuntimeConfig).getBooleanProperty("user.account.email.activation");

        ArgumentCaptor<User> userArgumentCaptor = ArgumentCaptor.forClass(User.class);
        verify(mockUserManager).addUser(userArgumentCaptor.capture());
        User addUserArgument = userArgumentCaptor.getValue();
        assertEquals("User{userName='testUser', password='testPasswordText', openIdUrl='OpenIdUrl{openIdUrl='http://open.id.url'}', screenName='null', fullName='null', emailAddress='null', locale='null', timeZone='null', enabled=true}", addUserArgument.getStateString());
        assertEquals("testUser", addUserArgument.getUserName());
        assertEquals("testPasswordText", addUserArgument.getPassword());
        assertNotNull(addUserArgument.getDateCreated());
        assertNull(addUserArgument.getActivationCode());

        verify(mockWeblogger).flush();

        verify(mockSession).removeAttribute(RollerSession.ROLLER_SESSION);
        verify(mockSession).invalidate();
    }

    @Test
    public void test_save_disabledReturnCode_withoutException() throws WebloggerException {
        when(mockWebloggerRuntimeConfig.getBooleanProperty(anyString())).thenReturn(false);
        when(mockUserManager.getUserCount()).thenReturn(1L);

        String returnValue = registerSpy.save();

        assertEquals(Register.DISABLED_RETURN_CODE, returnValue);
        assertNull(registerSpy.getActivationStatus());
        assertEquals("ProfileBean{id='null', userName='null', password='null', screenName='null', fullName='null', emailAddress='null', locale='null', timeZone='null', openIdUrl='null', passwordText='null', passwordConfirm='null'}", registerSpy.getBean().getStateString());

        verify(mockWebloggerRuntimeConfig).getBooleanProperty("users.registration.enabled");
        verify(mockUserManager).getUserCount();
        verifyZeroInteractions(mockWeblogger);
        verifyZeroInteractions(mockSession);
    }

    @Test
    public void test_save_disabledReturnCode_withException() throws WebloggerException {
        when(mockWebloggerRuntimeConfig.getBooleanProperty(anyString())).thenThrow(new IllegalArgumentException("no such property"));

        String returnValue = registerSpy.save();

        assertEquals(Register.DISABLED_RETURN_CODE, returnValue);
        assertNull(registerSpy.getActivationStatus());
        assertEquals("ProfileBean{id='null', userName='null', password='null', screenName='null', fullName='null', emailAddress='null', locale='null', timeZone='null', openIdUrl='null', passwordText='null', passwordConfirm='null'}", registerSpy.getBean().getStateString());

        verify(mockWebloggerRuntimeConfig).getBooleanProperty("users.registration.enabled");
        verifyZeroInteractions(mockUserManager);
        verifyZeroInteractions(mockWeblogger);
        verifyZeroInteractions(mockSession);
    }

}