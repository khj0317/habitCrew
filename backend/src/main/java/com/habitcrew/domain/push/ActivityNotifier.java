package com.habitcrew.domain.push;

import com.habitcrew.domain.certification.ActivityEvent;
import com.habitcrew.domain.user.User;
import com.habitcrew.domain.user.UserRepository;
import com.habitcrew.global.time.ServiceTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.sql.Date;

/**
 * 내 인증에 좋아요·댓글이 달리면 작성자에게 푸시 알림을 보낸다.
 * - 커밋된 뒤에, 요청 스레드가 아닌 별도 스레드에서 보낸다 (푸시 서버 응답을 기다리느라 좋아요·댓글이 느려지지 않게)
 * - 내가 내 인증에 한 반응, 알림을 끈 사람, 탈퇴한 사람에게는 보내지 않는다
 * - 좋아요는 인증 하나당 하루 한 번만 알린다 (여럿이 눌러도 알림이 쏟아지지 않게). 댓글은 매번 알리되,
 *   같은 인증의 알림은 휴대폰 알림창에서 하나로 겹쳐 보이게 tag를 같게 준다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityNotifier {

    private final UserRepository userRepository;
    private final PushService pushService;
    private final JdbcTemplate jdbc;
    private final ServiceTime serviceTime;

    @Async
    @TransactionalEventListener
    public void onActivity(ActivityEvent event) {
        try {
            notify(event);
        } catch (RuntimeException e) {
            log.warn("활동 알림을 보내지 못했습니다: {}", event, e);
        }
    }

    void notify(ActivityEvent event) {
        if (event.actorId().equals(event.authorId())) {
            return;
        }
        User author = userRepository.findById(event.authorId()).orElse(null);
        if (author == null || author.isDeleted() || !author.isNotifyActivity()) {
            return;
        }
        String actor = userRepository.findById(event.actorId()).map(User::getNickname).orElse("크루원");
        String url = "/crews/" + event.crewId() + "/certifications/" + event.certificationId();

        if (event.type() == ActivityEvent.Type.LIKE) {
            int claimed = jdbc.update("""
                            insert into notification_log (user_id, kind, ref_date) values (?, ?, ?)
                            on conflict do nothing
                            """,
                    author.getId(), "LIKE:" + event.certificationId(), Date.valueOf(serviceTime.today()));
            if (claimed == 0) {
                return;
            }
            pushService.sendToUser(author.getId(), new PushService.Message(
                    "❤️ 좋아요를 받았어요", actor + "님이 내 인증을 좋아해요", url, "like-" + event.certificationId()));
        } else {
            pushService.sendToUser(author.getId(), new PushService.Message(
                    "💬 새 댓글", actor + ": " + event.commentPreview(), url, "comment-" + event.certificationId()));
        }
    }
}
