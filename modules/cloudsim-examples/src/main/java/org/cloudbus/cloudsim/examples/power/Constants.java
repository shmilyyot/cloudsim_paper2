package org.cloudbus.cloudsim.examples.power;

import org.cloudbus.cloudsim.power.models.*;

/**
 * If you are using any algorithms, policies or workload included in the power package, please cite
 * the following paper:
 *
 * Anton Beloglazov, and Rajkumar Buyya, "Optimal Online Deterministic Algorithms and Adaptive
 * Heuristics for Energy and Performance Efficient Dynamic Consolidation of Virtual Machines in
 * Cloud Data Centers", Concurrency and Computation: Practice and Experience (CCPE), Volume 24,
 * Issue 13, Pages: 1397-1420, John Wiley & Sons, Ltd, New York, USA, 2012
 *
 * @author Anton Beloglazov
 * @since Jan 6, 2012
 */
public class Constants {

	public final static String placement = "FFD";
	public final static boolean ENABLE_OUTPUT = true;
	public final static boolean OUTPUT_CSV    = false;

	public final static double SCHEDULING_INTERVAL = 300;
	public final static double SIMULATION_LIMIT = 24 * 60 * 60 * 9.9;

	public final static int CLOUDLET_LENGTH	= 2500 * (int) SIMULATION_LIMIT;
	public final static int CLOUDLET_PES	= 1;

	public static String currentWorkload = "";

	/*
	 * VM instance types:
	 *   High-Memory Extra Large Instance: 3.25 EC2 Compute Units, 8.55 GB // too much MIPS
	 *   High-CPU Medium Instance: 2.5 EC2 Compute Units, 0.85 GB
	 *   Extra Large Instance: 2 EC2 Compute Units, 3.75 GB
	 *   Small Instance: 1 EC2 Compute Unit, 1.7 GB
	 *   Micro Instance: 0.5 EC2 Compute Unit, 0.633 GB
	 * 	 g1.s Instance: 1 EC2 Compute Unit, 1024 MB, 2100
	 * 	 g1.m Instance: 2 EC2 Compute Unit, 2048 MB, 2100
	 * 	 g2.m Instance: 1 EC2 Compute Unit, 1024 MB, 2300
	 * 	 g2.l Instance: 2 EC2 Compute Unit, 1920 MB, 2300
	 *   g2.xl Instance: 4 EC2 Compute Unit, 4096 MB, 2300
	 *   g2.2xl Instance: 8 EC2 Compute Unit, 8192 MB, 2300
	 *   We decrease the memory size two times to enable oversubscription
	 *
	 */
	public final static int VM_TYPES = 8;

	//	g1.m, g2.m, g2.xl, g2.2xl
	//	public final static int[] VM_MIPS	= { 2500, 2000, 1000, 500, 2300, 2300, 2300, 2300 };
	//	g1.m, g2.m, g2.l, g2.xl
	//	public final static int[] VM_MIPS	= { 2500, 2000, 1000, 500, 2100, 2300, 2300, 2300 };
	//	g1.s, g1.m, g2.m, g2.l
	public final static int[] VM_MIPS	= { 2500, 2000, 1000, 500, 2100, 2100, 2300, 2300};

	//	g1.m, g2.m, g2.xl, g2.2xl
	//	public final static int[] VM_PES	= { 1, 1, 1, 1, 1, 2, 4, 8 };
	//	g1.m, g2.m, g2.l, g2.xl
	//	public final static int[] VM_PES	= { 1, 1, 1, 1, 2, 1, 2, 4 };
	//	g1.s, g1.m, g2.m, g2.l
	public final static int[] VM_PES	= { 1, 1, 1, 1, 1, 1, 1, 1};

	//	g1.m, g2.m, g2.xl, g2.2xl
	//	public final static int[] VM_RAM	= { 870, 1740, 1740, 613, 1024, 1920, 4096, 8192 };
	//	g1.m, g2.m, g2.l, g2.xl
	//	public final static int[] VM_RAM	= { 870, 1740, 1740, 613, 2048, 1024, 1920, 4096 };
	//	g1.s, g1.m, g2.m, g2.l
	public final static int[] VM_RAM	= { 870, 1740, 1740, 613, 1024, 2048, 1024, 1920};
	public final static int VM_BW		= 100000; // 100 Mbit/s
	public final static int VM_SIZE		= 2500; // 2.5 GB

	/*
	 * Host types:
	 *   HP ProLiant ML110 G4 (1 x [Xeon 3040 1860 MHz, 2 cores], 4GB)
	 *   HP ProLiant ML110 G5 (1 x [Xeon 3075 2660 MHz, 2 cores], 4GB)
	 *   Express5800/GT110f-S (1 x [Xeon E3-1265LV3 2500 MHz, 4 cores], 8GB)
	 *   IBM Corporation IBM System x3450 (1 x [Intel Xeon E5462 2800 MHz, 8 cores], 16GB)
	 *   We increase the memory size to enable over-subscription (x4)
	 */
	public final static int HOST_TYPES	 = 4;
	public final static int[] HOST_MIPS	 = { 1860, 2660, 2500, 2800 };
	public final static int[] HOST_PES	 = { 2, 2, 4, 8 };
	public final static int[] HOST_RAM	 = { 4096, 4096, 8192, 16 * 1024 };
	public final static int HOST_BW		 = 1000000; // 1 Gbit/s
	public final static int HOST_STORAGE = 1000000; // 1 GB

	public final static int[] HOST_PEAK_PEFF = {467, 741, 8809, 1283};

	public final static String[] workloads = {
		"20110303",
		"20110306",
		"20110309",
		"20110322",
		"20110325",
		"20110403",
		"20110409",
		"20110411",
		"20110412",
		"20110420"
	};

	public final static PowerModel[] HOST_POWER = {
		new PowerModelSpecPowerHpProLiantMl110G4Xeon3040(),
		new PowerModelSpecPowerHpProLiantMl110G5Xeon3075(),
		new PowerModelSpecPowerExpress5800GT110fS(),
		new PowerModelSpecPowerIBMSystemX3450(),
	};

}
