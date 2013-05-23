package common.simulation.scenarios;

import se.sics.kompics.p2p.experiment.dsl.SimulationScenario;

@SuppressWarnings("serial")
public class Scenario1 extends Scenario {

    private static SimulationScenario scenario = new SimulationScenario() {
        {

            StochasticProcess process0 = new StochasticProcess() {
                {
                    eventInterArrivalTime(constant(300));
                    raise(3, Operations.peerJoin(), uniform(0, Integer.MAX_VALUE));
                }
            };

            StochasticProcess process1 = new StochasticProcess() {
                {
                    eventInterArrivalTime(constant(50));
                    raise(50, Operations.peerJoin(), uniform(0, Integer.MAX_VALUE));
                }
            };

            StochasticProcess process2 = new StochasticProcess() {
                {
                    eventInterArrivalTime(constant(50));
                    raise(100, Operations.addIndexEntry(), uniform(0, Integer.MAX_VALUE));
                }
            };

//            StochasticProcess churn = new StochasticProcess() {
//                {
//                    System.err.println("###############");
//                    eventInterArrivalTime(exponential(500));                               //  ̃500ms
//                    raise(50, Operations.peerJoin(), uniform(0, Integer.MAX_VALUE));       // 50 joins
//                    raise(50, Operations.peerFail, uniform(0, Integer.MAX_VALUE));         // 50 failures
//                    raise(50, Operations.addIndexEntry(), uniform(0, Integer.MAX_VALUE));  // 50 addEntry
//                }
//            };

            process0.start();
            process1.startAfterTerminationOf(2000, process0);
            process2.startAfterTerminationOf(8000, process1);
            //churn.startAfterTerminationOf(10000, process2);


        }
    };

    // -------------------------------------------------------------------
    public Scenario1() {
        super(scenario);
    }
}
