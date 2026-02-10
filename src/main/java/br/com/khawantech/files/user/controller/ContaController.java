package br.com.khawantech.files.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.khawantech.files.user.entity.User;
import br.com.khawantech.files.user.service.ContaService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/conta")
@RequiredArgsConstructor
public class ContaController {

    private final ContaService contaService;

    @DeleteMapping
    public ResponseEntity<Void> excluirConta(@AuthenticationPrincipal User user) {
        contaService.excluirConta(user.getId());
        return ResponseEntity.noContent().build();
    }
}
