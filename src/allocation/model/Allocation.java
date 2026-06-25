package allocation.model;

import java.util.List;

public class Allocation {

    private AllocationRequest request;
    private List<Resource> assignedResources;

    public Allocation(AllocationRequest request, List<Resource> assignedResources) {
        this.request = request;
        this.assignedResources = assignedResources;
    }

    public AllocationRequest getRequest() {
        return request;
    }

    public List<Resource> getAssignedResources() {
        return assignedResources;
    }
}