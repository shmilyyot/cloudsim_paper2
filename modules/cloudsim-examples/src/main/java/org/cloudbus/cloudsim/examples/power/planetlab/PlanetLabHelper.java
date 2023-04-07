package org.cloudbus.cloudsim.examples.power.planetlab;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.github.signaflo.timeseries.model.arima.Arima;
import com.github.signaflo.timeseries.model.arima.ArimaOrder;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelNull;
import org.cloudbus.cloudsim.UtilizationModelPlanetLabInMemory;
import org.cloudbus.cloudsim.examples.power.Constants;
import org.cloudbus.cloudsim.util.MathUtil;

/**
 * A helper class for the running examples for the PlanetLab workload.
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
public class PlanetLabHelper {

	/**
	 * Creates the cloudlet list planet lab.
	 * 
	 * @param brokerId the broker id
	 * @param inputFolderName the input folder name
	 * @return the list
	 * @throws FileNotFoundException the file not found exception
	 */
	public static List<Cloudlet> createCloudletListPlanetLab(int brokerId, String inputFolderName)
			throws FileNotFoundException {
		List<Cloudlet> list = new ArrayList<Cloudlet>();

		long fileSize = 300;
		long outputSize = 300;
		UtilizationModel utilizationModelNull = new UtilizationModelNull();

		File inputFolder = new File(inputFolderName);
		File[] files = inputFolder.listFiles();
		List<String> path = new ArrayList<>();
		for(File file : files){
			String s = file.getAbsolutePath();
			if(!s.startsWith("predict", s.length()-7)){
				path.add(file.getAbsolutePath());
			}
		}
		for (int i = 0; i < path.size(); i++) {
			Cloudlet cloudlet = null;
			try {
				cloudlet = new Cloudlet(
						i,
						Constants.CLOUDLET_LENGTH,
						Constants.CLOUDLET_PES,
						fileSize,
						outputSize,
						new UtilizationModelPlanetLabInMemory(
								path.get(i),
								Constants.SCHEDULING_INTERVAL), utilizationModelNull, utilizationModelNull);
//				double []data = ((UtilizationModelPlanetLabInMemory)cloudlet.getUtilizationModelCpu()).getData();
//				ArimaOrder arimaOrder = MathUtil.findBestOrder(data);
//				int len = data.length;
//				double []predictData = new double[len + 1];
//				Arrays.fill(predictData, 0);
//				System.out.println("begin predict file" + i);
//				for(int j = 30; j <= len; ++j){
//					double[] tempData = new double[30];
//					for(int k = j - 30; k < j; ++k)
//					{
//						System.arraycopy(data, j - 30, tempData, 0, 30);
//					}
//					predictData[j] = MathUtil.arimaPredict(tempData, arimaOrder);
//				}
//				System.out.println(Arrays.toString(predictData));
//				File file = new File(inputFolderName + "/predict/vm_" + i +".obj");
//				try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(file.toPath())))
//				{
//					out.writeObject(predictData);
//					System.out.println("write file" + i);
//				}
//				catch (IOException e)
//				{
//					e.printStackTrace();
//				}
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(0);
			}
			cloudlet.setUserId(brokerId);
			cloudlet.setVmId(i);
			list.add(cloudlet);
		}

		return list;
	}

}
