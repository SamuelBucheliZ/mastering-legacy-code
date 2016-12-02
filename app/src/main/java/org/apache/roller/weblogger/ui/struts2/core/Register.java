/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  The ASF licenses this file to You
 * under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.  For additional information regarding
 * copyright in this work, please see the NOTICE file in the top level
 * directory of this distribution.
 */

package org.apache.roller.weblogger.ui.struts2.core;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang3.CharSetUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.Weblogger;
import org.apache.roller.weblogger.business.WebloggerFactory;
import org.apache.roller.weblogger.business.UserManager;
import org.apache.roller.weblogger.config.AuthMethod;
import org.apache.roller.weblogger.config.WebloggerConfig;
import org.apache.roller.weblogger.config.WebloggerRuntimeConfigInstance;
import org.apache.roller.weblogger.pojos.OpenIdUrl;
import org.apache.roller.weblogger.pojos.User;
import org.apache.roller.weblogger.ui.core.RollerSession;
import org.apache.roller.weblogger.ui.core.security.CustomUserRegistry;
import org.apache.roller.weblogger.ui.struts2.util.UIAction;
import org.apache.roller.weblogger.util.MailUtilInstance;
import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.validation.SkipValidation;


/**
 * Actions for registering a new user.  This page is activated in Roller in two ways,
 * by explicitly selecting the "Register" button on the Main Menu, or if
 * upon a non-Roller DB login (say, LDAP) if the user does not exist in the
 * Roller DB.  In the latter case, this page is activated from login-redirect.jsp file.
 *
 * @see org.apache.roller.weblogger.ui.struts2.core.Login
 */
public class Register extends UIAction implements ServletRequestAware {
    
    private static Log log = LogFactory.getLog(Register.class);
    public static final String DISABLED_RETURN_CODE = "disabled";
    public static final String DEFAULT_ALLOWED_CHARS = "A-Za-z0-9";

    // this is a no-no, we should not need this
    private HttpServletRequest servletRequest = null;

    private final AuthMethod authMethod = getAuthMethod();

    AuthMethod getAuthMethod() {
        return WebloggerConfig.getAuthMethod();
    }

    private String activationStatus = null;
    
    private String activationCode = null;
    private ProfileBean bean = new ProfileBean();

    public Register() {
        this.pageTitle = "newUser.addNewUser";
    }
    
    // override default security, we do not require an authenticated user
    public boolean isUserRequired() {
        return false;
    }
    
    // override default security, we do not require an action weblog
    public boolean isWeblogRequired() {
        return false;
    }

    @SkipValidation
    public String execute() {
        
        // if registration is disabled, then don't allow registration
        try {
            if (!getWebloggerRuntimeConfig().getBooleanProperty("users.registration.enabled")
                // unless there are 0 users (need to allow creation of first user)
                && getUserManager().getUserCount() != 0) {
                addError("Register.disabled");
                return DISABLED_RETURN_CODE;
            }
        } catch (Exception e) {
            log.error("Error checking user count", e);
            addError("generic.error.check.logs");
            return DISABLED_RETURN_CODE;
        }
                
        // For new user default to locale set in browser
        bean.setLocale(servletRequest.getLocale().toString());
        
        // For new user default to timezone of server
        bean.setTimeZone(TimeZone.getDefault().getID());
        
        /* TODO: when Spring Security 2.1 is release comment out this stuff, 
         * which pre-populates the user bean with info from OpenID provider.
         *
        Collection attrsCollect = (Collection)WebloggerFactory.getWeblogger()
                .getUserManager().userAttributes.get(UserAttribute.Attributes.openidUrl.toString());
        
        if (attrsCollect != null) {
            ArrayList attrs = new ArrayList(attrsCollect);
            for (OpenIDUserAttribute attr : attrs) {
                if (attr.getName().equals(OpenIDUserAttribute.Attributes.country.toString())) {
                    getBean().setLocale(UIUtils.getLocale(attr.getValue()));
                }                
               if (attr.getName().equals(OpenIDUserAttribute.Attributes.email.toString())) {
                    getBean().setEmailAddress(attr.getValue());
                }
                if (attr.getName().equals(OpenIDUserAttribute.Attributes.fullname.toString())) {
                    getBean().setFullName(attr.getValue());
                }
                if (attr.getName().equals(OpenIDUserAttribute.Attributes.nickname.toString())) {
                    getBean().setUserName(attr.getValue());
                }
                if (attr.getName().equals(OpenIDUserAttribute.Attributes.timezone.toString())) {
                    getBean().setTimeZone(UIUtils.getTimeZone(attr.getValue()));
                }
                if (attr.getName().equals(OpenIDUserAttribute.Attributes.openidname.toString())) {
                    getBean().setOpenidUrl(attr.getValue());
                }
                
            }
        }*/
            
        try {

            if (getAuthMethod() == AuthMethod.LDAP) {
                // See if user is already logged in via Spring Security
                User fromSSOUser = CustomUserRegistry.getUserDetailsFromAuthentication(servletRequest);
                if (fromSSOUser != null) {
                    // Copy user details from Spring Security, including LDAP attributes
                    bean.copyFrom(fromSSOUser);
                }
            } else if (getAuthMethod() == AuthMethod.CMA) {
                // See if user is already logged in via CMA
                if (servletRequest.getUserPrincipal() != null) {
                    // Only detail we get is username, sadly no LDAP attributes
                    bean.setUserName(servletRequest.getUserPrincipal().getName());
                    bean.setScreenName(servletRequest.getUserPrincipal().getName());
                }
            }
            
        } catch (Exception ex) {
            log.error("Error reading SSO user data", ex);
            addError("error.editing.user", ex.toString());
        }
        
        return INPUT;
    }
    
    
    public String save() {
        
        // if registration is disabled, then don't allow registration
        if (isRegistrationDisabled()) return DISABLED_RETURN_CODE;

        List<String> errors = myValidate();

        if (!errors.isEmpty()) {
            for (String errorKey : errors ) {
                addError(errorKey);
            }
            return INPUT;
        }

        try {

            UserManager userManager = getUserManager();
            User user = User.createUserFrom(bean);

            // If user set both password and passwordConfirm then reset password
            if (!StringUtils.isEmpty(bean.getPasswordText()) &&
                    !StringUtils.isEmpty(bean.getPasswordConfirm())) {
                user.resetPassword(bean.getPasswordText());
            }

            // are we using email activation?
            boolean activationEnabled = getWebloggerRuntimeConfig().getBooleanProperty("user.account.email.activation");
            if (activationEnabled) {
                // User account will be enabled after the activation process
                user.setEnabled(Boolean.FALSE);

                String inActivationCode = userManager.createActivationCode();

                user.setActivationCode(inActivationCode);
            }

            String openidurl = bean.getOpenIdUrl();
            if (openidurl != null) {
                user.setOpenIdUrl(new OpenIdUrl(openidurl));
            }

            // save new user
            userManager.addUser(user);

            getWeblogger().flush();

            // now send activation email if necessary
            if (activationEnabled)
                if (user.getActivationCode() != null) {
                    getMailUtil().trySendUserActivationEmail(user);
                    this.activationStatus = "pending";
                }

            // Invalidate session, otherwise new user who was originally
            // authenticated via LDAP/SSO will remain logged in but
            // without a valid Roller role.
            getSession().removeAttribute(RollerSession.ROLLER_SESSION);
            getSession().invalidate();

            // set a special page title
            setPageTitle("welcome.title");

            return SUCCESS;

        } catch (WebloggerException ex) {
            log.error("Error adding new user", ex);
            addError("generic.error.check.logs");
        }

        return INPUT;
    }

    public boolean isRegistrationDisabled() {
        try {
            if (!getWebloggerRuntimeConfig().getBooleanProperty("users.registration.enabled")
                    // unless there are 0 users (need to allow creation of first user)
                    && (getUserManager().getUserCount() != 0)) {
                return true;
            }
        } catch (Exception e) {
            log.error("Error checking user count", e);
            return true;
        }
        return false;
    }

    MailUtilInstance getMailUtil() {
        return MailUtilInstance.INSTANCE;
    }

    HttpSession getSession() {
        return servletRequest.getSession();
    }

    WebloggerRuntimeConfigInstance getWebloggerRuntimeConfig() {
        return WebloggerRuntimeConfigInstance.INSTANCE;
    }

    UserManager getUserManager() {
        return getWeblogger().getUserManager();
    }

    Weblogger getWeblogger() {
        return WebloggerFactory.getWeblogger();
    }


    @SkipValidation
    public String activate() {
        
        try {
            UserManager mgr = getUserManager();

            if (activationCode == null) {
                addError("error.activate.user.missingActivationCode");
            } else {
                User user = mgr.getUserByActivationCode(activationCode);
                
                if (user != null) {
                    // enable user account
                    user.setEnabled(Boolean.TRUE);
                    user.setActivationCode(null);
                    mgr.saveUser(user);
                    getWeblogger().flush();

                    this.activationStatus = "active";

                } else {
                    addError("error.activate.user.invalidActivationCode");
                }
            }
            
        } catch (WebloggerException e) {
            addError(e.getMessage());
            log.error("ERROR in activateUser", e);
        }
        
        if (hasActionErrors()) {
            this.activationStatus = "error";
        }
        
        // set a special page title
        setPageTitle("welcome.title");
            
        return SUCCESS;
    }
    
    
    private List<String> myValidate() {
        List<String> errors = new ArrayList<>();
        // if using external auth, we don't want to error on empty password/username from HTML form.
        boolean usingSSO = (authMethod == AuthMethod.LDAP) || (authMethod == AuthMethod.CMA);
        if (usingSSO) {
            // store an unused marker in the Roller DB for the passphrase in
            // the LDAP or CMA cases, as actual passwords are stored externally
            String unusedPassword = WebloggerConfig.getProperty("users.passwords.externalAuthValue", "<externalAuth>");
            
            // Preserve username and password, Spring Security case
            User fromSSOUser = CustomUserRegistry.getUserDetailsFromAuthentication(servletRequest);
            if (fromSSOUser != null) {
                bean.setPasswordText(unusedPassword);
                bean.setPasswordConfirm(unusedPassword);
                bean.setUserName(fromSSOUser.getUserName());
            }

            // Preserve username and password, CMA case             
            else if (servletRequest.getUserPrincipal() != null) {
                bean.setUserName(servletRequest.getUserPrincipal().getName());
                bean.setPasswordText(unusedPassword);
                bean.setPasswordConfirm(unusedPassword);
            }
        }
        
        String allowed = WebloggerConfig.getProperty("username.allowedChars");
        if ((allowed == null) || (allowed.trim().length() == 0)) {
            allowed = DEFAULT_ALLOWED_CHARS;
        }
        
        // check that username only contains safe characters
        String safe = CharSetUtils.keep(bean.getUserName(), allowed);
        if (!safe.equals(bean.getUserName()) ) {
            errors.add("error.add.user.badUserName");
        }
        
        // check password, it is required if OpenID and SSO are disabled
        if (AuthMethod.ROLLERDB.name().equals(authMethod.name())
                && StringUtils.isEmpty(bean.getPasswordText())) {
                errors.add("error.add.user.passwordEmpty");
                return errors;
        }
        
        // User.password does not allow null, so generate one
        if (authMethod.name().equals(AuthMethod.OPENID.name()) ||
                (authMethod.name().equals(AuthMethod.DB_OPENID.name()) && !StringUtils.isEmpty(bean.getOpenIdUrl()))) {
            String randomString = RandomStringUtils.randomAlphanumeric(255);
            bean.setPasswordText(randomString);
            bean.setPasswordConfirm(randomString);
        }
        
        // check that passwords match 
        if (!bean.getPasswordText().equals(bean.getPasswordConfirm())) {
            errors.add("userRegister.error.mismatchedPasswords");
        }
        
        // check that username is not taken
        if (!StringUtils.isEmpty(bean.getUserName())) {
            try {
                UserManager mgr = getUserManager();
                if (mgr.getUserByUserName(bean.getUserName(), null) != null) {
                    errors.add("error.add.user.userNameInUse");
                    // reset user name
                    bean.setUserName(null);
                }
            } catch (WebloggerException ex) {
                errors.add("error checking for user");
                log.error("error checking for user", ex);
            }
        }

        // check that OpenID, if provided, is not taken
        if (!StringUtils.isEmpty(bean.getOpenIdUrl())) {
            try {
                UserManager mgr = getUserManager();
                if (mgr.getUserByOpenIdUrl(bean.getOpenIdUrl()) != null) {
                    errors.add("error.add.user.openIdInUse");
                    // reset OpenID URL
                    bean.setOpenIdUrl(null);
                }
            } catch (WebloggerException ex) {
                errors.add("generic.error.check.logs");
                log.error("error checking OpenID URL", ex);
            }
        }

        return errors;
    }
    
    
    public HttpServletRequest getServletRequest() {
        return servletRequest;
    }

    public void setServletRequest(HttpServletRequest servletRequest) {
        this.servletRequest = servletRequest;
    }
    
    public ProfileBean getBean() {
        return bean;
    }

    public void setBean(ProfileBean bean) {
        this.bean = bean;
    }

    public String getActivationStatus() {
        return activationStatus;
    }

    public void setActivationStatus(String activationStatus) {
        this.activationStatus = activationStatus;
    }

    public String getActivationCode() {
        return activationCode;
    }

    public void setActivationCode(String activationCode) {
        this.activationCode = activationCode;
    }
    
}
