/* Copyright 2005 I Serv Consultoria Empresarial Ltda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.helianto.security.internal;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.helianto.security.domain.IdentitySecret;
import org.helianto.user.domain.User;
import org.helianto.user.repository.UserReadAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Models core user information retrieved by UserDetailsService as an adapter class
 * to {@link org.helianto.user.domain.User}.
 * 
 * <p>
 * A new <code>UserDetailsAdapter</code> may be created from a single
 * {@link org.helianto.user.domain.User} and the correspondent credential to be expected 
 * during authentication. A new <code>UserDetailsAdapter</code> may also be created from
 * a group with no credential specified, where the authentication is then considered 
 * to be anonymous.
 * </p>
 * 
 * @author Mauricio Fernandes de Castro
 */
public class UserDetailsAdapter
	implements
      Serializable
    , UserDetails
    , UserAuthentication {

    static final Logger logger = LoggerFactory.getLogger(UserDetailsAdapter.class);
    
	private static final long serialVersionUID = 1L;
	
	private UserReadAdapter userReadAdapter;

	private User user;

	private IdentitySecret identitySecret;
    
    private List<GrantedAuthority> authorities = new ArrayList<>();

    /**
     * Constructor
     */
    public UserDetailsAdapter() {
    	super();
    }
    
    /**
     * Constructor.
     * 
     * @param userReadAdapter
     */
    public UserDetailsAdapter(UserReadAdapter userReadAdapter) {
        this();
        this.userReadAdapter = userReadAdapter;
    }
    
    /**
     * Constructor.
     * 
     * @param user
     */
    public UserDetailsAdapter(User user) {
        this();
        this.user = user;
    }
    
    /**
     * Constructor.
     * 
     * @param userReadAdapter
     * @param identitySecurity
     */
    public UserDetailsAdapter(UserReadAdapter userReadAdapter, IdentitySecret identitySecurity) {
        this(userReadAdapter);
        this.identitySecret = identitySecurity;
    }
    
    /**
     * Context id.
     */
    public int getContextId() {
    	if (user!=null) {
    		return user.getContextId();
    	}
    	if(userReadAdapter!=null ){
    		return userReadAdapter.getContextId();
    	}
		return 0;
	}
    
    /**
     * Entity id.
     */
    public int getEntityId() {
    	if (user!=null) {
    		return user.getEntityId();
    	}
		return userReadAdapter.getEntityId();
	}
    
    /**
     * Identity id.
     */
    public int getIdentityId() {
    	if (user!=null && user.getIdentityId()!=null) {
    		return user.getIdentityId();
    	}
    	if(userReadAdapter!=null ){
    		return userReadAdapter.getIdentityId();
    	}
    	return 0 ;
	}
    
    /**
     * User id.
     */
    public int getUserId() {
    	if (user!=null) {
    		return user.getId();
    	}
    	if (userReadAdapter!=null) {
    		return	userReadAdapter.getUserId();
		}
		return 0;
	}
    
    public boolean isAccountNonExpired() {
    	if (user!=null) {
    		return user.isAccountNonExpired();
    	}
    	if(userReadAdapter!=null){
    		return userReadAdapter.isAccountNonExpired();
    	}
    	return false;
    }

    public boolean isAccountNonLocked() {
    	// TODO review this
    	if (user!=null) {
    		return true;
    	}
    	if(userReadAdapter!=null){
    		return userReadAdapter.isAccountNonLocked();
    	}
    	return false;
    }
    
    public boolean isCredentialsNonExpired() {
    	// delegate to the application
        return true;
    }

    public boolean isEnabled() {
    	return isAccountNonLocked();
    }

    public String getPassword() {
    	if (identitySecret!=null && identitySecret.getIdentitySecret()!=null) {
    		return identitySecret.getIdentitySecret();
    	}
        return "";
     }

    public String getUsername() {
    	// !!! it is userKey for Helianto semantics...
    	if (user!=null) {
    		return user.getUserKey();
    	}
    	if(userReadAdapter!=null && userReadAdapter.getUserName()!=null){
    		return userReadAdapter.getUserName();
    	}
        return "";
    }
    
    public List<GrantedAuthority> getAuthorities() {
        return this.authorities;
    }
    public void setAuthorities(List<GrantedAuthority> authorities) {
		this.authorities = authorities;
	}

    
    @Override
    public Set<String> getAuthoritySet() {
        if (authorities!=null) {
            return AuthorityUtils.authorityListToSet(authorities);
        }
        return new HashSet<>();
    }
    
    @Override
    public Locale getUserLocale() {
    	// TODO get the actual user locale
    	return Locale.getDefault();
    }
    
    /**
     * Convenience to retrieve user details from context.
     */
    public static UserDetailsAdapter getUserDetailsFromContext() {
    	Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    	if (authentication!=null) {
    		return (UserDetailsAdapter) authentication.getPrincipal();
    	}
    	return null;
    }
    
}
