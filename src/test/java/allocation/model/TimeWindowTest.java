package allocation.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeWindowTest {

    @Test
    void overlapsReturnsTrueForOverlappingWindows() {
        TimeWindow first = window(10, 12);
        TimeWindow second = window(11, 13);

        assertTrue(first.overlaps(second));
        assertTrue(second.overlaps(first));
    }

    @Test
    void overlapsReturnsFalseForTouchingWindows() {
        TimeWindow first = window(10, 12);
        TimeWindow second = window(12, 14);

        assertFalse(first.overlaps(second));
        assertFalse(second.overlaps(first));
    }

    @Test
    void containsReturnsTrueForFullyContainedWindow() {
        TimeWindow outer = window(10, 14);
        TimeWindow inner = window(11, 13);

        assertTrue(outer.contains(inner));
    }

    @Test
    void containsReturnsFalseForWindowOutsideBounds() {
        TimeWindow outer = window(10, 14);
        TimeWindow overlappingButNotContained = window(13, 15);

        assertFalse(outer.contains(overlappingButNotContained));
    }

    private TimeWindow window(int startHour, int endHour) {
        return new TimeWindow(
                LocalDateTime.of(2026, 7, 1, startHour, 0),
                LocalDateTime.of(2026, 7, 1, endHour, 0)
        );
    }
}
