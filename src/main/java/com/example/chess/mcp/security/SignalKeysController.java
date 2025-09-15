package com.example.chess.mcp.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mcp/keys")
@ConditionalOnProperty(name = "mcp.encryption.impl", havingValue = "signal")
public class SignalKeysController {

    @Autowired
    private SignalDoubleRatchetService signalService;

    @GetMapping("/prekey")
    public ResponseEntity<PreKeyBundleDto> getPreKey(@RequestParam("agentId") String agentId) {
        try {
            PreKeyBundleDto dto = signalService.getPreKeyBundle(agentId);
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
}


