/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.power;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.VmScheduler;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.models.PowerModel;
import org.cloudbus.cloudsim.provisioners.BwProvisioner;
import org.cloudbus.cloudsim.provisioners.RamProvisioner;
import org.cloudbus.cloudsim.util.MathUtil;

/**
 * A host that stores its CPU utilization percentage history. The history is used by VM allocation
 * and selection policies.
 * 
 * <br/>If you are using any algorithms, policies or workload included in the power package please cite
 * the following paper:<br/>
 * 
 * <ul>
 * <li><a href="http://dx.doi.org/10.1002/cpe.1867">Anton Beloglazov, and Rajkumar Buyya, "Optimal Online Deterministic Algorithms and Adaptive
 * Heuristics for Energy and Performance Efficient Dynamic Consolidation of Virtual Machines in
 * Cloud Data Centers", Concurrency and Computation: Practice and Experience (CCPE), Volume 24,
 * Issue 13, Pages: 1397-1420, John Wiley & Sons, Ltd, New York, USA, 2012</a>
 * </ul>
 * 
 * @author Anton Beloglazov
 * @since CloudSim Toolkit 2.0
 */
public class PowerHostUtilizationHistory extends PowerHost {

	public String getHostTrendType() {
		return hostTrendType;
	}

	public void setHostTrendType(String hostTrendType) {
		this.hostTrendType = hostTrendType;
	}

	String hostTrendType = null;

	public double getLastPlaceTime() {
		return lastPlaceTime;
	}

	public void setLastPlaceTime(double lastPlaceTime) {
		this.lastPlaceTime = lastPlaceTime;
	}

	double lastPlaceTime = -1;

	public double getLastOverloadTime() {
		return lastOverloadTime;
	}

	public void setLastOverloadTime(double lastOverloadTime) {
		this.lastOverloadTime = lastOverloadTime;
	}

	double lastOverloadTime = -1;

	int afterPlaceOverloadTime = 0;

	public int getLastOverloadInterval() {
		return lastOverloadInterval;
	}

	public void setLastOverloadInterval(int lastOverloadInterval) {
		this.lastOverloadInterval = lastOverloadInterval;
	}

	int lastOverloadInterval = -1;

	public int getAfterPlaceOverloadTime() {
		return afterPlaceOverloadTime;
	}

	public void setAfterPlaceOverloadTime(int afterPlaceOverloadTime) {
		this.afterPlaceOverloadTime = afterPlaceOverloadTime;
	}

	public int getOverloadBeforePlaceAfterPlaceOverloadTime() {
		return overloadBeforePlaceAfterPlaceOverloadTime;
	}

	public void setOverloadBeforePlaceAfterPlaceOverloadTime(int overloadBeforePlaceAfterPlaceOverloadTime) {
		this.overloadBeforePlaceAfterPlaceOverloadTime = overloadBeforePlaceAfterPlaceOverloadTime;
	}

	int overloadBeforePlaceAfterPlaceOverloadTime = 0;

	public Integer getOverloadTimes() {
		return overloadTimes;
	}

	public void setOverloadTimes(Integer overloadTimes) {
		this.overloadTimes = overloadTimes;
	}

	Integer overloadTimes = 0;
	/**
	 * Instantiates a new PowerHostUtilizationHistory.
	 * 
	 * @param id the host id
	 * @param ramProvisioner the ram provisioner
	 * @param bwProvisioner the bw provisioner
	 * @param storage the storage capacity
	 * @param peList the host's PEs list
	 * @param vmScheduler the vm scheduler
	 * @param powerModel the power consumption model
	 */
	public PowerHostUtilizationHistory(
			int id,
			RamProvisioner ramProvisioner,
			BwProvisioner bwProvisioner,
			long storage,
			List<? extends Pe> peList,
			VmScheduler vmScheduler,
			PowerModel powerModel) {
		super(id, ramProvisioner, bwProvisioner, storage, peList, vmScheduler, powerModel);
	}

	/**
	 * Gets the host CPU utilization percentage history.
	 * 
	 * @return the host CPU utilization percentage history
	 */
	protected double[] getUtilizationHistory() {
		double[] utilizationHistory = new double[PowerVm.HISTORY_LENGTH];
		double hostMips = getTotalMips();
		for (PowerVm vm : this.<PowerVm> getVmList()) {
			for (int i = 0; i < vm.getUtilizationHistory().size(); i++) {
				utilizationHistory[i] += vm.getUtilizationHistory().get(i) * vm.getMips() / hostMips;
			}
		}
		return MathUtil.trimZeroTail(utilizationHistory);
	}

	protected double getPredictUtilization() {
		double predictUtilization = 0.0;
		double hostMips = getTotalMips();
		for (PowerVm vm : this.<PowerVm> getVmList()) {
			double vmPredictUtilization = vm.getPredictUtilization(CloudSim.clock());
			if(vmPredictUtilization >= 0.001)
			{
				predictUtilization += vmPredictUtilization * vm.getMips() / hostMips;
			}
			else {
				predictUtilization += vm.getTotalUtilizationOfCpu(CloudSim.clock()) * vm.getMips() / hostMips;
			}
		}
		return predictUtilization;
	}

	//Todo 当前时刻利用率不在预测历史利用率里面
	public double getTrend() {
		double curUtilization = this.getUtilizationOfCpu();
		if(CloudSim.clock() < 1799.0)
		{
			return 0;
		}
//		double[] utilizationHistory = this.getUtilizationHistory();
//		if(utilizationHistory.length < PowerVm.HISTORY_LENGTH){
//			return 0;
//		}
//		double[] utilizationHistoryCopy = new double[PowerVm.HISTORY_LENGTH];
//		utilizationHistoryCopy[0] = curUtilization;
//		System.arraycopy(utilizationHistory, 0, utilizationHistoryCopy, 1, PowerVm.HISTORY_LENGTH - 1);
//		double predictUtilization = MathUtil.arimaPredict(MathUtil.reverseArray(utilizationHistoryCopy));
		double predictUtilization = getPredictUtilization();
		if (predictUtilization < 0.001){
			return curUtilization - this.getUtilizationHistory()[0];
		}
		return predictUtilization - curUtilization;
	}

	public double getLastTrend(){
		double curUtilization = this.getUtilizationOfCpu();
		if(CloudSim.clock() < 1799.0)
		{
			return 0;
		}
		return curUtilization - this.getUtilizationHistory()[0];
	}

	public double[] getHistoryTrend(){
		double[] utilizationHistory = this.getUtilizationHistory();
		int len = utilizationHistory.length;
		double[] historyTrend = new double[len];
		for(int i = 0; i < len - 1; ++i){
			historyTrend[i] = (utilizationHistory[i+1] - utilizationHistory[i]) * this.getTotalMips();
		}
		historyTrend[len-1] = (getUtilizationOfCpu() - utilizationHistory[len-1]) * this.getTotalMips();
		return historyTrend;
	}


}
