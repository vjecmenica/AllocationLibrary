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
         * Ograničenje:
         * x + y <= 1
         *
         * Znači ne mogu oba istovremeno biti 1.
         */
        model.addLessOrEqual(
                LinearExpr.sum(new IntVar[]{x, y}),
                1
        );

        /*
         * Cilj:
         * Maksimizuj 10*x + 5*y
         *
         * Pošto x vredi 10, a y vredi 5,
         * optimalno rešenje treba da bude:
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
