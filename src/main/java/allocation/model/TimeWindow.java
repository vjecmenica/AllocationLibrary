package allocation.model;

import java.time.LocalDateTime;

public class TimeWindow {

    private LocalDateTime start;
    private LocalDateTime end;

    public TimeWindow(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Start i end ne smeju biti null.");
        }

        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("Start mora biti pre end vremena.");
        }

        this.start = start;
        this.end = end;
    }

    public boolean overlaps(TimeWindow other) {
        return this.start.isBefore(other.end) && other.start.isBefore(this.end);
    }

    public boolean contains(TimeWindow other) {
        return !this.start.isAfter(other.start) && !this.end.isBefore(other.end);
    }

    public LocalDateTime getStart() {
        return start;
    }

    public LocalDateTime getEnd() {
        return end;
    }
}