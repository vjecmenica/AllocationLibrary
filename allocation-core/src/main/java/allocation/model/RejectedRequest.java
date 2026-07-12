package allocation.model;

public class RejectedRequest {

    private AllocationRequest request;
    private String reason;

    public RejectedRequest(AllocationRequest request, String reason) {
        this.request = request;
        this.reason = reason;
    }

    public AllocationRequest getRequest() {
        return request;
    }

    public String getReason() {
        return reason;
    }
}