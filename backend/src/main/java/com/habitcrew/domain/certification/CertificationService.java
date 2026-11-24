package com.habitcrew.domain.certification;

import com.habitcrew.domain.certification.dto.CertificationDtos;
import com.habitcrew.domain.crew.CertDays;
import com.habitcrew.domain.crew.Crew;
import com.habitcrew.domain.crew.CrewMember;
import com.habitcrew.domain.crew.CrewMemberRepository;
import com.habitcrew.domain.crew.CrewRepository;
import com.habitcrew.domain.user.User;
import com.habitcrew.domain.user.UserRepository;
import com.habitcrew.global.error.BusinessException;
import com.habitcrew.global.error.ErrorCode;
import com.habitcrew.global.storage.PhotoStorage;
import com.habitcrew.global.time.ServiceTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CertificationService {

    static final int FEED_PAGE_SIZE = 10;
    static final int MESSAGE_MAX = 200;
    private static final int COMMENT_LIMIT = 200;

    private final CertificationRepository certificationRepository;
    private final CertificationCommentRepository commentRepository;
    private final CrewRepository crewRepository;
    private final CrewMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final ImageProcessor imageProcessor;
    private final PhotoStorage photoStorage;
    private final ServiceTime serviceTime;
    private final TransactionTemplate transaction;
    private final TransactionTemplate readOnly;

    public CertificationService(CertificationRepository certificationRepository,
                                CertificationCommentRepository commentRepository,
                                CrewRepository crewRepository,
                                CrewMemberRepository memberRepository,
                                UserRepository userRepository,
                                ImageProcessor imageProcessor,
                                PhotoStorage photoStorage,
                                ServiceTime serviceTime,
                                PlatformTransactionManager transactionManager) {
        this.certificationRepository = certificationRepository;
        this.commentRepository = commentRepository;
        this.crewRepository = crewRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.imageProcessor = imageProcessor;
        this.photoStorage = photoStorage;
        this.serviceTime = serviceTime;
        this.transaction = new TransactionTemplate(transactionManager);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    /**
     * 사진 인증을 올린다.
     * 사진 변환·업로드는 시간이 걸리므로 DB 트랜잭션 밖에서 한다 (업로드 동안 DB 연결을 붙잡고 있지 않게).
     * 그래서 순서를 "가벼운 검사 → 사진 처리·업로드 → 짧은 트랜잭션으로 저장"으로 나눴고,
     * 저장에 실패하면 올려 둔 사진을 지워 저장소에 주인 없는 파일이 남지 않게 한다.
     *
     * @param receivedAt 요청이 도착한 시각. 업로드가 자정을 넘겨 끝나도 이 시각의 날짜로 인증한다
     */
    public CertificationDtos.Item create(Long userId, Long crewId, byte[] photo, String rawMessage, Instant receivedAt) {
        LocalDate certDate = serviceTime.dateOf(receivedAt);
        String message = normalizeMessage(rawMessage);

        readOnly.executeWithoutResult(status -> {
            Crew crew = requireMember(crewId, userId).getCrew();
            if (!CertDays.contains(crew.getCertDaysMask(), certDate.getDayOfWeek())) {
                throw new BusinessException(ErrorCode.NOT_CERT_DAY);
            }
            // 무거운 사진 처리 전에 먼저 걸러낸다. 동시에 두 번 올리는 경우는 아래 유니크 제약이 막는다
            if (certificationRepository.existsByCrewIdAndUserIdAndCertDate(crewId, userId, certDate)) {
                throw new BusinessException(ErrorCode.ALREADY_CERTIFIED);
            }
        });

        ImageProcessor.Result image = imageProcessor.process(photo);
        String base = "certifications/" + crewId + "/" + UUID.randomUUID();
        String imageKey = base + ".jpg";
        String thumbnailKey = base + "_thumb.jpg";
        photoStorage.put(imageKey, image.display(), "image/jpeg");
        try {
            photoStorage.put(thumbnailKey, image.thumbnail(), "image/jpeg");
            Certification saved = transaction.execute(status -> {
                User user = userRepository.getReferenceById(userId);
                return certificationRepository.saveAndFlush(new Certification(crewId, user, certDate, message,
                        imageKey, thumbnailKey, image.width(), image.height(), serviceTime.now()));
            });
            return readOnly.execute(status -> toItem(certificationRepository.findWithUser(saved.getId()).orElseThrow(),
                    userId, false));
        } catch (DataIntegrityViolationException e) {
            deletePhotos(imageKey, thumbnailKey);
            throw new BusinessException(ErrorCode.ALREADY_CERTIFIED);
        } catch (RuntimeException e) {
            deletePhotos(imageKey, thumbnailKey);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public CertificationDtos.Feed feed(Long userId, Long crewId, Long cursor) {
        requireMember(crewId, userId);
        List<Certification> page = certificationRepository.findFeed(crewId, cursor, Limit.of(FEED_PAGE_SIZE + 1));
        boolean hasNext = page.size() > FEED_PAGE_SIZE;
        List<Certification> items = hasNext ? page.subList(0, FEED_PAGE_SIZE) : page;

        Set<Long> liked = items.isEmpty() ? Set.of() : new HashSet<>(certificationRepository.findLikedIds(userId,
                items.stream().map(Certification::getId).toList()));
        return new CertificationDtos.Feed(
                items.stream().map(c -> toItem(c, userId, liked.contains(c.getId()))).toList(),
                hasNext ? items.getLast().getId() : null);
    }

    @Transactional(readOnly = true)
    public CertificationDtos.Item get(Long userId, Long certificationId) {
        Certification certification = requireAccess(certificationId, userId);
        return toItem(certification, userId, certificationRepository.isLiked(certificationId, userId));
    }

    /** 오늘의 인증 현황: 누가 인증했고 누가 아직인지 */
    @Transactional(readOnly = true)
    public CertificationDtos.Today today(Long userId, Long crewId) {
        Crew crew = requireMember(crewId, userId).getCrew();
        LocalDate today = serviceTime.today();
        Map<Long, Long> certifiedBy = certificationRepository.findByCrewIdAndCertDate(crewId, today).stream()
                .collect(Collectors.toMap(c -> c.getUser().getId(), Certification::getId));

        List<CertificationDtos.MemberStatus> members = memberRepository.findMembers(crewId).stream()
                .map(CrewMember::getUser)
                .map(u -> new CertificationDtos.MemberStatus(u.getId(), u.getNickname(),
                        certifiedBy.containsKey(u.getId()), certifiedBy.get(u.getId())))
                .toList();
        int certifiedCount = (int) members.stream().filter(CertificationDtos.MemberStatus::certified).count();

        return new CertificationDtos.Today(today, CertDays.contains(crew.getCertDaysMask(), today.getDayOfWeek()),
                serviceTime.deadlineOf(today), certifiedBy.get(userId), certifiedCount, members.size(), members);
    }

    /** 내 인증은 그날 안에만 지울 수 있다 (다시 찍어 올리기용). 지난 인증은 정산 근거라서 지울 수 없다 */
    public void delete(Long userId, Long certificationId) {
        Certification removed = transaction.execute(status -> {
            Certification certification = requireAccess(certificationId, userId);
            if (!certification.isAuthor(userId)) {
                throw new BusinessException(ErrorCode.NOT_CERTIFICATION_AUTHOR);
            }
            if (!certification.getCertDate().equals(serviceTime.today())) {
                throw new BusinessException(ErrorCode.PAST_CERTIFICATION);
            }
            certificationRepository.delete(certification);
            return certification;
        });
        deletePhotos(removed.getImageKey(), removed.getThumbnailKey());
    }

    /** 좋아요 (PUT): 여러 번 보내도 결과가 같다. 실제로 새로 눌린 경우에만 개수를 올린다 */
    @Transactional
    public CertificationDtos.LikeResult like(Long userId, Long certificationId) {
        requireAccess(certificationId, userId);
        if (certificationRepository.insertLike(certificationId, userId) == 1) {
            certificationRepository.addLikeCount(certificationId, 1);
        }
        return new CertificationDtos.LikeResult(true, certificationRepository.likeCount(certificationId));
    }

    @Transactional
    public CertificationDtos.LikeResult unlike(Long userId, Long certificationId) {
        requireAccess(certificationId, userId);
        if (certificationRepository.deleteLike(certificationId, userId) == 1) {
            certificationRepository.addLikeCount(certificationId, -1);
        }
        return new CertificationDtos.LikeResult(false, certificationRepository.likeCount(certificationId));
    }

    @Transactional(readOnly = true)
    public List<CertificationDtos.Comment> comments(Long userId, Long certificationId) {
        requireAccess(certificationId, userId);
        return commentRepository.findByCertification(certificationId, Limit.of(COMMENT_LIMIT)).stream()
                .map(c -> toComment(c, userId))
                .toList();
    }

    @Transactional
    public CertificationDtos.Comment addComment(Long userId, Long certificationId, String content) {
        requireAccess(certificationId, userId);
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        CertificationComment comment = commentRepository.save(
                new CertificationComment(certificationId, user, content, serviceTime.now()));
        certificationRepository.addCommentCount(certificationId, 1);
        return toComment(comment, userId);
    }

    @Transactional
    public void deleteComment(Long userId, Long certificationId, Long commentId) {
        requireAccess(certificationId, userId);
        CertificationComment comment = commentRepository.findById(commentId)
                .filter(c -> c.getCertificationId().equals(certificationId))
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        if (!comment.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_COMMENT_AUTHOR);
        }
        commentRepository.delete(comment);
        certificationRepository.addCommentCount(certificationId, -1);
    }

    private CrewMember requireMember(Long crewId, Long userId) {
        return memberRepository.findByCrewIdAndUserId(crewId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREW_NOT_FOUND));
    }

    /** 크루원이 아니면 인증이 있는지조차 알려 주지 않는다 */
    private Certification requireAccess(Long certificationId, Long userId) {
        return certificationRepository.findWithUser(certificationId)
                .filter(c -> memberRepository.existsByCrewIdAndUserId(c.getCrewId(), userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.CERTIFICATION_NOT_FOUND));
    }

    private CertificationDtos.Item toItem(Certification c, Long userId, boolean likedByMe) {
        return new CertificationDtos.Item(
                c.getId(),
                new CertificationDtos.Author(c.getUser().getId(), c.getUser().getNickname()),
                c.getCertDate(),
                c.getMessage(),
                photoStorage.url(c.getImageKey()),
                photoStorage.url(c.getThumbnailKey()),
                c.getImageWidth(),
                c.getImageHeight(),
                c.getLikeCount(),
                likedByMe,
                c.getCommentCount(),
                c.isAuthor(userId),
                c.getCreatedAt());
    }

    private static CertificationDtos.Comment toComment(CertificationComment c, Long userId) {
        return new CertificationDtos.Comment(c.getId(),
                new CertificationDtos.Author(c.getUser().getId(), c.getUser().getNickname()),
                c.getContent(), c.getUser().getId().equals(userId), c.getCreatedAt());
    }

    private static String normalizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String stripped = message.strip();
        if (stripped.length() > MESSAGE_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "한마디는 " + MESSAGE_MAX + "자까지 쓸 수 있어요.");
        }
        return stripped;
    }

    private void deletePhotos(String... keys) {
        for (String key : keys) {
            photoStorage.delete(key);
        }
    }
}
