package allocation.test;

import com.google.ortools.Loader;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.IntVar;
import com.google.ortools.sat.LinearExpr;

public class CpSatSmokeTest {

    public static void main(String[] args) {
        Loader.loadNativeLibraries();

        CpModel model = new CpModel();

        IntVar x = model.newBoolVar("x");
        IntVar y = model.newBoolVar("y");

        /*
         * Constraint:
         * x + y <= 1
         *
         * This means both variables cannot be 1 at the same time.
         */
        model.addLessOrEqual(
                LinearExpr.sum(new IntVar[]{x, y}),
                1
        );

        /*
         * Objective:
         * Maximize 10*x + 5*y
         *
         * Since x is worth 10 and y is worth 5,
         * the optimal solution should be:
         * x = 1
         * y = 0
         */
        model.maximize(
                LinearExpr.weightedSum(
                        new IntVar[]{x, y},
                        new long[]{10, 5}
                )
        );

        CpSolver solver = new CpSolver();
        solver.getParameters().setNumSearchWorkers(1);

        CpSolverStatus status = solver.solve(model);

        System.out.println("Status: " + status);
        System.out.println("x = " + solver.value(x));
        System.out.println("y = " + solver.value(y));
        System.out.println("Objective value = " + solver.objectiveValue());
    }
}
