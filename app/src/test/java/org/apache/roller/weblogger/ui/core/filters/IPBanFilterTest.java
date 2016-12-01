package org.apache.roller.weblogger.ui.core.filters;

import org.apache.roller.weblogger.util.IPBanList;
import org.junit.Test;
import org.mockito.Mockito;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

public class IPBanFilterTest {

    @Test
    public void test_doFilter_notBanned() throws IOException, ServletException {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        new IPBanFilter().doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
        verify(res, never()).sendError(anyInt());
        verify(req).getRemoteAddr();
    }

    @Test
    public void test_doFilter_withBannedIP() throws IOException, ServletException {
        String testAddress = "test.address";

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRemoteAddr()).thenReturn(testAddress);

        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        IPBanList banAllIPList = mock(IPBanList.class);
        when(banAllIPList.isBanned(testAddress)).thenReturn(true);

        IPBanFilter ipBanFilter = spy(new IPBanFilter());
        doReturn(banAllIPList).when(ipBanFilter).getIPBanList();

        ipBanFilter.doFilter(req, res, chain);

        verify(banAllIPList).isBanned(testAddress);
        verify(req, times(2)).getRemoteAddr();
        verify(chain, never()).doFilter(req, res);
        verify(res).sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    public void test_init() throws ServletException {
        FilterConfig filterConfig = mock(FilterConfig.class);

        IPBanFilter ipBanFilter = spy(new IPBanFilter());
        ipBanFilter.init(filterConfig);

        verify(ipBanFilter).init(filterConfig);
        verifyNoMoreInteractions(ipBanFilter);
        verifyZeroInteractions(filterConfig);
    }

    @Test
    public void test_destroy() {
        IPBanFilter ipBanFilter = spy(new IPBanFilter());

        ipBanFilter.destroy();

        verify(ipBanFilter).destroy();
        verifyNoMoreInteractions(ipBanFilter);
    }
}
