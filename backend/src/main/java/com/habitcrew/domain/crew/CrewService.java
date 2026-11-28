package com.habitcrew.domain.crew;

import com.habitcrew.domain.certification.CertificationPhotos;
import com.habitcrew.domain.crew.CrewMember.Role;
import com.habitcrew.domain.crew.dto.CrewRequests;
import com.habitcrew.domain.crew.dto.CrewResponses;
import com.habitcrew.domain.notification.NotificationService;
import com.habitcrew.domain.user.User;
import com.habitcrew.domain.user.UserRepository;
import com.habitcrew.global.error.BusinessException;
import com.habitcrew.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CrewService {

    static final int MAX_CREWS_PER_USER = 10;
    private static final int INVITE_CODE_ATTEMPTS = 5;

    private final CrewRepository crewRepository;
    private final CrewMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final CertificationPhotos certificationPhotos;
    private final NotificationService notificationService;
    private final Clock clock;

    @Transactional
    public CrewResponses.Detail create(Long userId, CrewRequests.Create request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        checkCrewLimit(userId);

        Crew crew = crewRepository.save(new Crew(request.name(), request.description(), request.certDays(),
                request.penaltyPoints(), request.maxMembers(), newInviteCode(), userId, clock.instant()));
        CrewMember owner = memberRepository.save(new CrewMember(crew, user, Role.OWNER, clock.instant()));
        return CrewResponses.Detail.of(crew, Role.OWNER, List.of(owner));
    }

    @Transactional(readOnly = true)
    public List<CrewResponses.Summary> myCrews(Long userId) {
        List<CrewMember> memberships = memberRepository.findMyMemberships(userId);
        if (memberships.isEmpty()) {
            return List.of();
        }
        List<Long> crewIds = memberships.stream().map(m -> m.getCrew().getId()).toList();
        Map<Long, Long> counts = memberRepository.countByCrewIds(crewIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        return memberships.stream()
                .map(m -> CrewResponses.Summary.of(m.getCrew(), counts.getOrDefault(m.getCrew().getId(), 0L), m.getRole()))
                .toList();
    }

    @Transactional(readOnly = true)
    public CrewResponses.Detail detail(Long userId, Long crewId) {
        CrewMember me = membership(crewId, userId);
        return CrewResponses.Detail.of(me.getCrew(), me.getRole(), memberRepository.findMembers(crewId));
    }

    @Transactional
    public CrewResponses.Detail update(Long userId, Long crewId, CrewRequests.Update request) {
        membership(crewId, userId);
        // 정원을 줄이는 도중에 누가 참여하면 정원보다 많아질 수 있어서, 참여와 같은 잠금을 건다
        Crew crew = crewRepository.findByIdForUpdate(crewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREW_NOT_FOUND));
        requireOwner(crew, userId);
        if (request.maxMembers() < memberRepository.countByCrewId(crewId)) {
            throw new BusinessException(ErrorCode.MAX_MEMBERS_BELOW_CURRENT);
        }
        crew.update(request.name(), request.description(), request.maxMembers());
        return detail(userId, crewId);
    }

    @Transactional(readOnly = true)
    public CrewResponses.InvitePreview preview(Long userId, String rawCode) {
        Crew crew = crewRepository.findByInviteCode(CrewRequests.normalizeCode(rawCode))
                .filter(c -> !isGuestCrew(c))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INVITE_CODE));
        String ownerNickname = userRepository.findById(crew.getOwnerId()).map(User::getNickname).orElse("");
        return new CrewResponses.InvitePreview(crew.getId(), crew.getName(), crew.getDescription(),
                crew.getCertDays(), crew.getPenaltyPoints(), memberRepository.countByCrewId(crew.getId()),
                crew.getMaxMembers(), ownerNickname, memberRepository.existsByCrewIdAndUserId(crew.getId(), userId));
    }

    /**
     * 초대 코드로 참여한다.
     * 크루 행을 잠근 상태에서 인원을 세므로, 마지막 한 자리에 여러 명이 동시에 참여해도 한 명만 성공한다.
     * 같은 사람이 버튼을 두 번 눌러 동시에 요청해도 (crew_id, user_id) 유니크 제약이 한 번 더 막는다.
     */
    @Transactional
    public CrewResponses.Detail join(Long userId, CrewRequests.Join request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        // 체험 계정은 실제 크루에 섞이지 않는다 (24시간 뒤 지워질 때 실제 크루의 기록이 함께 사라지지 않게)
        if (user.isGuest()) {
            throw new BusinessException(ErrorCode.GUEST_RESTRICTED);
        }
        Crew crew = crewRepository.findByInviteCodeForUpdate(request.inviteCode())
                .filter(c -> !isGuestCrew(c))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INVITE_CODE));

        if (memberRepository.existsByCrewIdAndUserId(crew.getId(), userId)) {
            throw new BusinessException(ErrorCode.ALREADY_JOINED);
        }
        if (memberRepository.countByCrewId(crew.getId()) >= crew.getMaxMembers()) {
            throw new BusinessException(ErrorCode.CREW_FULL);
        }
        checkCrewLimit(userId);

        memberRepository.saveAndFlush(new CrewMember(crew, user, Role.MEMBER, clock.instant()));
        notificationService.create(crew.getOwnerId(), NotificationService.Type.MEMBER_JOINED, "새 크루원",
                user.getNickname() + "님이 " + crew.getName() + "에 참여했어요", "/crews/" + crew.getId());
        return detail(userId, crew.getId());
    }

    /** 초대 코드가 원치 않는 곳에 퍼졌을 때 새 코드로 바꾼다 (예전 코드로는 더 이상 참여할 수 없다) */
    @Transactional
    public CrewResponses.Detail regenerateInviteCode(Long userId, Long crewId) {
        Crew crew = membership(crewId, userId).getCrew();
        requireOwner(crew, userId);
        crew.changeInviteCode(newInviteCode());
        return detail(userId, crewId);
    }

    /** 크루를 나간다. 크루장은 혼자 남았을 때만 나갈 수 있고, 그러면 크루가 사라진다 */
    @Transactional
    public void leave(Long userId, Long crewId) {
        CrewMember me = membership(crewId, userId);
        if (me.getRole() == Role.OWNER) {
            if (memberRepository.countByCrewId(crewId) > 1) {
                throw new BusinessException(ErrorCode.OWNER_CANNOT_LEAVE);
            }
            // DB 기록(크루원·인증·정산 등)은 ON DELETE CASCADE로 함께 지워지지만, 저장소의 사진 파일은 따로 지워야 한다
            certificationPhotos.deleteAfterCommit(certificationPhotos.keysOfCrew(crewId));
            crewRepository.delete(me.getCrew());
            return;
        }
        memberRepository.delete(me);
    }

    @Transactional
    public CrewResponses.Detail kick(Long userId, Long crewId, Long targetUserId) {
        Crew crew = membership(crewId, userId).getCrew();
        requireOwner(crew, userId);
        if (userId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.CANNOT_TARGET_SELF);
        }
        memberRepository.delete(targetMember(crewId, targetUserId));
        memberRepository.flush();
        return detail(userId, crewId);
    }

    @Transactional
    public CrewResponses.Detail transferOwner(Long userId, Long crewId, CrewRequests.TransferOwner request) {
        CrewMember me = membership(crewId, userId);
        Crew crew = me.getCrew();
        requireOwner(crew, userId);
        if (userId.equals(request.userId())) {
            throw new BusinessException(ErrorCode.CANNOT_TARGET_SELF);
        }
        CrewMember target = targetMember(crewId, request.userId());

        me.changeRole(Role.MEMBER);
        target.changeRole(Role.OWNER);
        crew.changeOwner(target.getUser().getId());
        memberRepository.flush();
        return detail(userId, crewId);
    }

    /** 체험 계정이 만든(또는 넘겨받은) 체험용 크루. 초대 코드로 다른 사람이 들어올 수 없다 */
    private boolean isGuestCrew(Crew crew) {
        return userRepository.findById(crew.getOwnerId()).map(User::isGuest).orElse(false);
    }

    /** 크루원이 아니면 크루가 있는지조차 알려 주지 않는다 (404) */
    private CrewMember membership(Long crewId, Long userId) {
        return memberRepository.findByCrewIdAndUserId(crewId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREW_NOT_FOUND));
    }

    private CrewMember targetMember(Long crewId, Long targetUserId) {
        return memberRepository.findByCrewIdAndUserId(crewId, targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private static void requireOwner(Crew crew, Long userId) {
        if (!crew.isOwner(userId)) {
            throw new BusinessException(ErrorCode.NOT_CREW_OWNER);
        }
    }

    private void checkCrewLimit(Long userId) {
        if (memberRepository.countByUserId(userId) >= MAX_CREWS_PER_USER) {
            throw new BusinessException(ErrorCode.TOO_MANY_CREWS);
        }
    }

    private String newInviteCode() {
        for (int i = 0; i < INVITE_CODE_ATTEMPTS; i++) {
            String code = inviteCodeGenerator.generate();
            if (!crewRepository.existsByInviteCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("초대 코드를 만들지 못했습니다.");
    }
}
