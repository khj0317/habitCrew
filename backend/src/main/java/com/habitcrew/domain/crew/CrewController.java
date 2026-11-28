package com.habitcrew.domain.crew;

import com.habitcrew.domain.crew.dto.CrewRequests;
import com.habitcrew.domain.crew.dto.CrewResponses;
import com.habitcrew.global.security.LoginUserId;
import com.habitcrew.global.web.RateLimited;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/crews")
@RequiredArgsConstructor
public class CrewController {

    private final CrewService crewService;

    @RateLimited(name = "crew-create", limit = 5)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CrewResponses.Detail create(@LoginUserId Long userId, @Valid @RequestBody CrewRequests.Create request) {
        return crewService.create(userId, request);
    }

    @GetMapping
    public List<CrewResponses.Summary> myCrews(@LoginUserId Long userId) {
        return crewService.myCrews(userId);
    }

    @GetMapping("/{crewId}")
    public CrewResponses.Detail detail(@LoginUserId Long userId, @PathVariable Long crewId) {
        return crewService.detail(userId, crewId);
    }

    @PatchMapping("/{crewId}")
    public CrewResponses.Detail update(@LoginUserId Long userId, @PathVariable Long crewId,
                                       @Valid @RequestBody CrewRequests.Update request) {
        return crewService.update(userId, crewId, request);
    }

    @GetMapping("/invites/{inviteCode}")
    public CrewResponses.InvitePreview preview(@LoginUserId Long userId, @PathVariable String inviteCode) {
        return crewService.preview(userId, inviteCode);
    }

    @RateLimited(name = "crew-join", limit = 10)
    @PostMapping("/join")
    public CrewResponses.Detail join(@LoginUserId Long userId, @Valid @RequestBody CrewRequests.Join request) {
        return crewService.join(userId, request);
    }

    @PostMapping("/{crewId}/invite-code")
    public CrewResponses.Detail regenerateInviteCode(@LoginUserId Long userId, @PathVariable Long crewId) {
        return crewService.regenerateInviteCode(userId, crewId);
    }

    @DeleteMapping("/{crewId}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@LoginUserId Long userId, @PathVariable Long crewId) {
        crewService.leave(userId, crewId);
    }

    @DeleteMapping("/{crewId}/members/{targetUserId}")
    public CrewResponses.Detail kick(@LoginUserId Long userId, @PathVariable Long crewId,
                                     @PathVariable Long targetUserId) {
        return crewService.kick(userId, crewId, targetUserId);
    }

    @PostMapping("/{crewId}/owner")
    public CrewResponses.Detail transferOwner(@LoginUserId Long userId, @PathVariable Long crewId,
                                              @Valid @RequestBody CrewRequests.TransferOwner request) {
        return crewService.transferOwner(userId, crewId, request);
    }
}
