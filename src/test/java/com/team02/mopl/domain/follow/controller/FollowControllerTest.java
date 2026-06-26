package com.team02.mopl.domain.follow.controller;

import com.team02.mopl.global.exception.GlobalExceptionHandler;
import com.team02.mopl.support.TestSecurityConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;

@WebMvcTest(FollowController.class)
@Import({TestSecurityConfiguration.class, GlobalExceptionHandler.class})
public class FollowControllerTest {}
