package com.planmate.service;

import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.planmate.dto.User;
import com.planmate.exception.BusinessLogicException;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

	@Autowired
	private UserService userService;

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		User user = userService.getUserByEmail(email);
		if (user == null) {
			throw new BusinessLogicException("User not found: " + email);
		}
		return new org.springframework.security.core.userdetails.User(user.getEmail(), user.getPassword(), new ArrayList<>());
	}

}
