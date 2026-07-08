package com.team02.mopl.domain.auth.service;

import com.team02.mopl.domain.auth.dto.ResetPasswordRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MailService {

  public void sendResetPasswordEmail(ResetPasswordRequest request) {}
}
