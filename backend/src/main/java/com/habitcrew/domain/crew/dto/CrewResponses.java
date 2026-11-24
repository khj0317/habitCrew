package com.habitcrew.domain.crew.dto;

import com.habitcrew.domain.crew.Crew;
import com.habitcrew.domain.crew.CrewMember;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.List;

public final class CrewResponses {

    private CrewResponses() {
    }

    /** 내 크루 목록의 카드 한 장 */
    public record Summary(
            Long id,
            String name,
            String description,
            List<DayOfWeek> certDays,
            int penaltyPoints,
            long memberCount,
            int maxMembers,
            CrewMember.Role myRole
    ) {
        public static Summary of(Crew crew, long memberCount, CrewMember.Role myRole) {
            return new Summary(crew.getId(), crew.getName(), crew.getDescription(), crew.getCertDays(),
                    crew.getPenaltyPoints(), memberCount, crew.getMaxMembers(), myRole);
        }
    }

    public record Member(Long userId, String nickname, CrewMember.Role role, Instant joinedAt) {
        public static Member from(CrewMember member) {
            return new Member(member.getUser().getId(), member.getUser().getNickname(), member.getRole(),
                    member.getJoinedAt());
        }
    }

    /** 크루 상세: 크루원에게만 보여 주므로 초대 코드를 포함한다 */
    public record Detail(
            Long id,
            String name,
            String description,
            List<DayOfWeek> certDays,
            int penaltyPoints,
            int maxMembers,
            String inviteCode,
            CrewMember.Role myRole,
            Instant createdAt,
            List<Member> members
    ) {
        public static Detail of(Crew crew, CrewMember.Role myRole, List<CrewMember> members) {
            return new Detail(crew.getId(), crew.getName(), crew.getDescription(), crew.getCertDays(),
                    crew.getPenaltyPoints(), crew.getMaxMembers(), crew.getInviteCode(), myRole,
                    crew.getCreatedAt(), members.stream().map(Member::from).toList());
        }
    }

    /** 초대 코드로 참여하기 전에 보여 주는 미리보기 (크루원 명단·초대 코드는 빼고 보여 준다) */
    public record InvitePreview(
            Long crewId,
            String name,
            String description,
            List<DayOfWeek> certDays,
            int penaltyPoints,
            long memberCount,
            int maxMembers,
            String ownerNickname,
            boolean alreadyJoined
    ) {
    }
}
