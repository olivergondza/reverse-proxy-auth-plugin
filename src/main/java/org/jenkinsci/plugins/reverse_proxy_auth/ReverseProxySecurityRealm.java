/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.reverse_proxy_auth;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.security.UserMayOrMayNotExistException;
import hudson.security.SecurityRealm;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import jenkins.model.Jenkins;

import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationManager;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.context.SecurityContextHolder;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UserDetailsService;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.springframework.dao.DataAccessException;

/**
 * @author Kohsuke Kawaguchi
 */
public class ReverseProxySecurityRealm extends SecurityRealm {
    
    private static final String REMOTE_USER_HEADER = "REMOTE_USER";
    
    private final String header;
    private final String rootUrl; // dig root URL from request can be tricky, make sure that we can change it in case of need
    //private final String loginUrl;
    private final boolean loginViaHttps; 
    private final boolean remoteUserHeader;

    @DataBoundConstructor
    public ReverseProxySecurityRealm(String header, String rootUrl, boolean loginViaHttps) {
        this.header = header;
        this.rootUrl = rootUrl;
        this.loginViaHttps = loginViaHttps;
        if(REMOTE_USER_HEADER.equalsIgnoreCase(header))
            remoteUserHeader = true;
        else
            remoteUserHeader = false;
    }

    /**
     * Name of the HTTP header to look at.
     */
    public String getHeader() {
        return header;
    }
    
    public String getRootUrl(){
        return rootUrl;
    }
    
    public boolean getLoginViaHttps(){
    	return loginViaHttps;
    }
    
    /*
    public String getLoginUrl(){
    	return loginUrl;
    }

    public boolean showLoginLink(){
    	return ((loginUrl != null) && (loginUrl.trim() != ""));
    }
    */
    
    public String getSecureRootUrl(){
        String jenkinsRootUrl = (rootUrl != null) ? rootUrl : Jenkins.getInstance().getRootUrl();
    	if(jenkinsRootUrl == null)
    		return null;
    	if(jenkinsRootUrl.startsWith("https")){
    		return jenkinsRootUrl; 
    	}
    	return jenkinsRootUrl.replaceFirst("http", "https");
    }
    
    @Override
    public boolean canLogOut() {
        return false;
    }

    @Override
    public Filter createFilter(FilterConfig filterConfig) {
        return new Filter() {
            public void init(FilterConfig filterConfig) throws ServletException {
            }

            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
                HttpServletRequest r = (HttpServletRequest) request;
                
                String v;
                if(remoteUserHeader)
                    v = r.getRemoteUser();
                else
                    v = r.getHeader(header);
                
                Authentication a;
                if (v==null) {
                    a = Hudson.ANONYMOUS;
                } else {
                    a = new UsernamePasswordAuthenticationToken(v,"",new GrantedAuthority[]{SecurityRealm.AUTHENTICATED_AUTHORITY});
                }

                SecurityContextHolder.getContext().setAuthentication(a);

                chain.doFilter(request,response);
            }

            public void destroy() {
            }
        };
    }

    @Override
    public SecurityComponents createSecurityComponents() {
        return new SecurityComponents(new AuthenticationManager() {
            public Authentication authenticate(Authentication authentication) {
                return authentication;
            }
        }, new UserDetailsService() {
            public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException, DataAccessException {
                throw new UserMayOrMayNotExistException(username);
            }
        });
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<SecurityRealm> {
        @Override
        public String getDisplayName() {
            return Messages.ReverseProxySecurityRealm_DisplayName();
        }
    }
}
