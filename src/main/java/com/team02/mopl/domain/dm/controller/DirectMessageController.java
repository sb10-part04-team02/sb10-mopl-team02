package com.team02.mopl.domain.dm.controller;

import com.team02.mopl.domain.dm.service.DirectMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/conversations")
public class DirectMessageController {

  private final DirectMessageService directMessageService;

}
