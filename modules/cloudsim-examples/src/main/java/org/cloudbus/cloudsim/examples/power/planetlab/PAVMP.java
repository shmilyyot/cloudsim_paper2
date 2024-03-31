package org.cloudbus.cloudsim.examples.power.planetlab;

import org.cloudbus.cloudsim.examples.power.Constants;

import java.io.IOException;

public class PAVMP {
    public static void main(String[] args) throws IOException {
        boolean enableOutput = true;
        boolean outputToFile = true;
        String inputFolder = NonPowerAware.class.getClassLoader().getResource("workload/google").getPath();
        String outputFolder = "output";
        String workload = "20110518"; // PlanetLab workload
        String vmAllocationPolicy = "pavmp"; // Static Threshold (THR) VM allocation policy
        String vmSelectionPolicy = "mc"; // Minimum Migration Time (MMT) VM selection policy
        String parameter = "0.8"; // the static utilization threshold
        String placement = "PABFD";

//        for (String nworkload : Constants.workloads) {
//            Constants.currentWorkload = nworkload;
//            new PlanetLabRunner(
//                    enableOutput,
//                    outputToFile,
//                    inputFolder,
//                    outputFolder,
//                    nworkload,
//                    vmAllocationPolicy,
//                    vmSelectionPolicy,
//                    parameter,
//                    Constants.placement);
//        }

        new PlanetLabRunner(
                enableOutput,
                outputToFile,
                inputFolder,
                outputFolder,
                workload,
                vmAllocationPolicy,
                vmSelectionPolicy,
                parameter,
                Constants.placement);
    }
}
