package com.example.agentflow.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.agentflow.model.GatewayChatRequest;
import com.example.agentflow.service.ResilientChatService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/gateway")
@RequiredArgsConstructor
public class GatewayController {

    private final ResilientChatService resilientChatService;

    @PostMapping("/chat")
    public String chat(@RequestBody GatewayChatRequest request) {
        return resilientChatService.chat(
                request.getMessage(),
                request.getPreferredModel());
    }
}
