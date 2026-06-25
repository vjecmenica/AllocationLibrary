package allocation.constraint;

public class ConstraintResult {

    private boolean satisfied;
    private String message;

    private ConstraintResult(boolean satisfied, String message) {
        this.satisfied = satisfied;
        this.message = message;
    }

    public static ConstraintResult satisfied() {
        return new ConstraintResult(true, null);
    }

    public static ConstraintResult violated(String message) {
        return new ConstraintResult(false, message);
    }

    public boolean isSatisfied() {
        return satisfied;
    }

    public String getMessage() {
        return message;
    }
}