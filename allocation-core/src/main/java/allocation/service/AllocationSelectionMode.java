package allocation.service;

/**
 * Describes how ResourceAllocator selected the algorithm for one execution.
 */
public enum AllocationSelectionMode {

    /**
     * The caller explicitly requested a concrete algorithm.
     */
    EXPLICIT,

    /**
     * ResourceAllocator selected a concrete algorithm automatically.
     */
    AUTO
}
