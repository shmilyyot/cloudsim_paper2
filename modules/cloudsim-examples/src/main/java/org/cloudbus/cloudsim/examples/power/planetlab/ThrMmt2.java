package org.cloudbus.cloudsim.examples.power.planetlab;

import org.cloudbus.cloudsim.examples.power.Constants;

import java.io.IOException;

/**
 * A simulation of a heterogeneous power aware data center that applies the Static Threshold (THR)
 * VM allocation policy and Minimum Migration Time (MMT) VM selection policy.
 * 
 * This example uses a real PlanetLab workload: 20110303.
 * 
 * The remaining configuration parameters are in the Constants and PlanetLabConstants classes.
 * 
 * If you are using any algorithms, policies or workload included in the power package please cite
 * the following paper:
 * 
 * Anton Beloglazov, and Rajkumar Buyya, "Optimal Online Deterministic Algorithms and Adaptive
 * Heuristics for Energy and Performance Efficient Dynamic Consolidation of Virtual Machines in
 * Cloud Data Centers", Concurrency and Computation: Practice and Experience (CCPE), Volume 24,
 * Issue 13, Pages: 1397-1420, John Wiley & Sons, Ltd, New York, USA, 2012
 * 
 * @author Anton Beloglazov
 * @since Jan 5, 2012
 */
public class ThrMmt2 {

	/**
	 * The main method.
	 * 
	 * @param args the arguments
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static void main(String[] args) throws IOException {
		boolean enableOutput = true;
		boolean outputToFile = true;
		String inputFolder = NonPowerAware.class.getClassLoader().getResource("workload/google").getPath();
		String outputFolder = "output";
		String workload = "20110518"; // PlanetLab workload
		String vmAllocationPolicy = "thr"; // Static Threshold (THR) VM allocation policy
		String vmSelectionPolicy = "mmt"; // Minimum Migration Time (MMT) VM selection policy
		String parameter = "0.8"; // the static utilization threshold
		String placement = Constants.placement;

		new PlanetLabRunner(
				enableOutput,
				outputToFile,
				inputFolder,
				outputFolder,
				workload,
				"thr",
				"mmt",
				parameter,
				"PABFD");

		new PlanetLabRunner(
				enableOutput,
				outputToFile,
				inputFolder,
				outputFolder,
				workload,
				"thr",
				"mmt",
				parameter,
				"FFD");

		new PlanetLabRunner(
				enableOutput,
				outputToFile,
				inputFolder,
				outputFolder,
				workload,
				"upvmc",
				"mmt",
				parameter,
				"PABFD");

		new PlanetLabRunner(
				enableOutput,
				outputToFile,
				inputFolder,
				outputFolder,
				workload,
				"peap",
				"peacr",
				parameter,
				"PABFD");
	}

}