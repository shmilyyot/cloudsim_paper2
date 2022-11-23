package org.cloudbus.cloudsim.examples.power.planetlab;

import org.cloudbus.cloudsim.examples.power.Constants;

import java.io.IOException;

public class CAPMOP {
    /**
     * The main method.
     *
     * @param args the arguments
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public static void main(String[] args) throws IOException {
        boolean enableOutput = true;
        boolean outputToFile = false;
        String inputFolder = NonPowerAware.class.getClassLoader().getResource("workload/planetlab").getPath();
        String outputFolder = "output";
        String workload = "20110303"; // PlanetLab workload
        String vmAllocationPolicy = "capmop"; // Static Threshold (THR) VM allocation policy
        String vmSelectionPolicy = "mmt"; // Minimum Migration Time (MMT) VM selection policy
        String parameter = "0.8"; // the static utilization threshold
        String placement = "PABFD";

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