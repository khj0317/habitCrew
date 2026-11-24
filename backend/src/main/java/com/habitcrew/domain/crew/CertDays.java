package com.habitcrew.domain.crew;

import java.time.DayOfWeek;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 인증 요일을 비트마스크 하나로 저장한다 (월=1, 화=2, 수=4, ... 일=64).
 * 테이블을 따로 두지 않아도 되고, 정산 때 "오늘이 인증일인가"를 비트 연산 한 번으로 판단할 수 있다.
 */
public final class CertDays {

    public static final int ALL = 0b111_1111;

    private CertDays() {
    }

    public static int toMask(Collection<DayOfWeek> days) {
        int mask = 0;
        for (DayOfWeek day : days) {
            mask |= bit(day);
        }
        return mask;
    }

    public static List<DayOfWeek> fromMask(int mask) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            if ((mask & bit(day)) != 0) {
                days.add(day);
            }
        }
        return List.copyOf(days);
    }

    public static boolean contains(int mask, DayOfWeek day) {
        return (mask & bit(day)) != 0;
    }

    private static int bit(DayOfWeek day) {
        return 1 << (day.getValue() - 1);
    }
}
