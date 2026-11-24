package com.habitcrew.domain.certification;

import com.habitcrew.domain.certification.dto.CertificationDtos;
import com.habitcrew.global.error.BusinessException;
import com.habitcrew.global.error.ErrorCode;
import com.habitcrew.global.security.LoginUserId;
import com.habitcrew.global.time.RequestTimeFilter;
import com.habitcrew.global.time.ServiceTime;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class CertificationController {

    private final CertificationService certificationService;
    private final ServiceTime serviceTime;

    @PostMapping(value = "/api/crews/{crewId}/certifications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CertificationDtos.Item create(@LoginUserId Long userId,
                                        @PathVariable Long crewId,
                                        @RequestPart("photo") MultipartFile photo,
                                        @RequestParam(value = "message", required = false) String message,
                                        HttpServletRequest request) throws IOException {
        if (photo.isEmpty()) {
            throw new BusinessException(ErrorCode.PHOTO_REQUIRED);
        }
        return certificationService.create(userId, crewId, photo.getBytes(), message,
                RequestTimeFilter.receivedAt(request, serviceTime));
    }

    @GetMapping("/api/crews/{crewId}/certifications")
    public CertificationDtos.Feed feed(@LoginUserId Long userId, @PathVariable Long crewId,
                                       @RequestParam(required = false) Long cursor) {
        return certificationService.feed(userId, crewId, cursor);
    }

    @GetMapping("/api/crews/{crewId}/certifications/today")
    public CertificationDtos.Today today(@LoginUserId Long userId, @PathVariable Long crewId) {
        return certificationService.today(userId, crewId);
    }

    @GetMapping("/api/certifications/{certificationId}")
    public CertificationDtos.Item get(@LoginUserId Long userId, @PathVariable Long certificationId) {
        return certificationService.get(userId, certificationId);
    }

    @DeleteMapping("/api/certifications/{certificationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@LoginUserId Long userId, @PathVariable Long certificationId) {
        certificationService.delete(userId, certificationId);
    }

    @PutMapping("/api/certifications/{certificationId}/like")
    public CertificationDtos.LikeResult like(@LoginUserId Long userId, @PathVariable Long certificationId) {
        return certificationService.like(userId, certificationId);
    }

    @DeleteMapping("/api/certifications/{certificationId}/like")
    public CertificationDtos.LikeResult unlike(@LoginUserId Long userId, @PathVariable Long certificationId) {
        return certificationService.unlike(userId, certificationId);
    }

    @GetMapping("/api/certifications/{certificationId}/comments")
    public List<CertificationDtos.Comment> comments(@LoginUserId Long userId, @PathVariable Long certificationId) {
        return certificationService.comments(userId, certificationId);
    }

    @PostMapping("/api/certifications/{certificationId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CertificationDtos.Comment addComment(@LoginUserId Long userId, @PathVariable Long certificationId,
                                                @Valid @RequestBody CertificationDtos.CommentRequest request) {
        return certificationService.addComment(userId, certificationId, request.content());
    }

    @DeleteMapping("/api/certifications/{certificationId}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@LoginUserId Long userId, @PathVariable Long certificationId,
                              @PathVariable Long commentId) {
        certificationService.deleteComment(userId, certificationId, commentId);
    }
}
