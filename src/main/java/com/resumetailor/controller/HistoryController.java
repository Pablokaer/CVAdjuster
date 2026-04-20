package com.resumetailor.controller;

import com.resumetailor.model.ResumeHistory;
import com.resumetailor.repository.ResumeHistoryRepository;
import com.resumetailor.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class HistoryController {

    private final ResumeHistoryRepository historyRepository;
    private final UserRepository userRepository;

    @GetMapping("/history")
    public String history(Model model, Principal principal) {
        List<ResumeHistory> entries = userRepository.findByEmail(principal.getName())
            .map(historyRepository::findByUserOrderByCreatedAtDesc)
            .orElse(List.of());

        model.addAttribute("history", entries);
        return "history";
    }

    @GetMapping("/history/{id}/text")
    @ResponseBody
    public ResponseEntity<String> getHistoryText(@PathVariable Long id, Principal principal) {
        return historyRepository.findById(id)
            .filter(e -> e.getUser().getEmail().equals(principal.getName()))
            .map(e -> ResponseEntity.ok(e.getTailoredText()))
            .orElse(ResponseEntity.notFound().build());
    }
}
