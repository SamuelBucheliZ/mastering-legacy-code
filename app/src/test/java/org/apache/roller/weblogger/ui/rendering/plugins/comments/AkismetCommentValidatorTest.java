package org.apache.roller.weblogger.ui.rendering.plugins.comments;


import org.apache.roller.util.RollerConstants;
import org.apache.roller.weblogger.business.Weblogger;
import org.apache.roller.weblogger.pojos.Weblog;
import org.apache.roller.weblogger.pojos.WeblogEntryComment;
import org.apache.roller.weblogger.util.RollerMessages;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class AkismetCommentValidatorTest {

    private Weblog weblog;
    private Weblogger weblogger;
    private WeblogEntryComment comment;
    private RollerMessages messages;


    @Before
    public void setUp() {
        weblog = mock(Weblog.class);

        weblogger = mock(Weblogger.class, RETURNS_DEEP_STUBS);
        when(weblogger.getVersion()).thenReturn("1.2.3");
        when(weblogger.getUrlStrategy().getWeblogURL(weblog, null, true)).thenReturn("test.url");

        comment = mock(WeblogEntryComment.class, RETURNS_DEEP_STUBS);
        when(comment.getWeblogEntry().getWebsite()).thenReturn(weblog);

        messages = mock(RollerMessages.class);
    }

    @Test
    public void test_validate_nonTrueResponse() {
        AkismetCommentValidator akismetCommentValidator = spy(new AkismetCommentValidator(null, null));
        doReturn(weblogger).when(akismetCommentValidator).getWeblogger();

        int returnValue = akismetCommentValidator.validate(comment, null);

        assertEquals(RollerConstants.PERCENT_100, returnValue);
        verify(messages, never()).addError(anyString());
    }

    @Test
    public void test_validate_trueResponse() throws IOException {
        BufferedReader inputReader = mock(BufferedReader.class);
        when(inputReader.readLine()).thenReturn("true");

        AkismetCommentValidator akismetCommentValidator = spy(new AkismetCommentValidator(null, null));
        doReturn(weblogger).when(akismetCommentValidator).getWeblogger();
        doReturn("true").when(akismetCommentValidator).callAksimetService(any(StringBuilder.class));

        int returnValue = akismetCommentValidator.validate(comment, messages);

        assertEquals(0, returnValue);
        verify(messages).addError("comment.validator.akismetMessage");
    }

    @Test
    public void test_validate_exception() throws IOException {
        AkismetCommentValidator akismetCommentValidator = spy(new AkismetCommentValidator(null, null));
        doReturn(weblogger).when(akismetCommentValidator).getWeblogger();
        doThrow(new IOException("service unavailable")).when(akismetCommentValidator).callAksimetService(any(StringBuilder.class));

        int returnValue = akismetCommentValidator.validate(comment, messages);

        assertEquals(0, returnValue);
        verify(messages, never()).addError(anyString());
    }
}
