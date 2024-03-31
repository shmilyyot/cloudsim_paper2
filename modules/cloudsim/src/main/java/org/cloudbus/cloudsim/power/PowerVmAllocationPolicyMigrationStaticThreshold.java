/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.power;

import java.util.*;
import java.util.stream.Collectors;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;

/**
 * A VM allocation policy that uses a Static CPU utilization Threshold (THR) to detect host over
 * utilization.
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
 * @since CloudSim Toolkit 3.0
 */
public class PowerVmAllocationPolicyMigrationStaticThreshold extends PowerVmAllocationPolicyMigrationAbstract {

	private Random random = new Random();

	/** The static host CPU utilization threshold to detect over utilization.
         * It is a percentage value from 0 to 1
         * that can be changed when creating an instance of the class. */
	private double utilizationThreshold = 0.9;

	/**
	 * Instantiates a new PowerVmAllocationPolicyMigrationStaticThreshold.
	 * 
	 * @param hostList the host list
	 * @param vmSelectionPolicy the vm selection policy
	 * @param utilizationThreshold the utilization threshold
	 */
	public PowerVmAllocationPolicyMigrationStaticThreshold(
			List<? extends Host> hostList,
			PowerVmSelectionPolicy vmSelectionPolicy,
			double utilizationThreshold) {
		super(hostList, vmSelectionPolicy);
		setUtilizationThreshold(utilizationThreshold);
	}

	/**
	 * Checks if a host is over utilized, based on CPU usage.
	 * 
	 * @param host the host
	 * @return true, if the host is over utilized; false otherwise
	 */
	@Override
	protected boolean isHostOverUtilized(PowerHost host) {
		addHistoryEntry(host, getUtilizationThreshold());
		double totalRequestedMips = 0;
		for (Vm vm : host.getVmList()) {
			totalRequestedMips += vm.getCurrentRequestedTotalMips();
		}
		double utilization = totalRequestedMips / host.getTotalMips();
		boolean currentOverload = utilization > getUtilizationThreshold();

		checkConsumption(host, currentOverload);

		return currentOverload;
	}

	/**
	 * Sets the utilization threshold.
	 * 
	 * @param utilizationThreshold the new utilization threshold
	 */
	protected void setUtilizationThreshold(double utilizationThreshold) {
		this.utilizationThreshold = utilizationThreshold;
	}

	/**
	 * Gets the utilization threshold.
	 * 
	 * @return the utilization threshold
	 */
	protected double getUtilizationThreshold() {
		return utilizationThreshold;
	}

	/*以下皆为新增测试*/
	protected boolean isHostCurrentOverLoad(PowerHost host) {
		addHistoryEntry(host, getUtilizationThreshold());
		double totalRequestedMips = 0;
		for (Vm vm : host.getVmList()) {
			totalRequestedMips += vm.getCurrentRequestedTotalMips();
		}
		double utilization = totalRequestedMips / host.getTotalMips();
		return utilization > getUtilizationThreshold();
	}

	protected boolean isHostFutureOverUtilized(PowerHost host) {
		PowerHostUtilizationHistory _host = (PowerHostUtilizationHistory) host;
		double upperThreshold = getUtilizationThreshold();
		return _host.getPredictUtilization() > upperThreshold;
	}

	@Override
	protected boolean isHostOverUtilizedAfterAllocation(PowerHost host, Vm vm) {
		boolean isHostOverUtilizedAfterAllocation = true;
		if (host.vmCreate(vm)) {
//			isHostOverUtilizedAfterAllocation = (isHostCurrentOverLoad(host) || isHostFutureOverUtilized(host));
			isHostOverUtilizedAfterAllocation = (isHostCurrentOverLoad(host));
			host.vmDestroy(vm);
		}
		return isHostOverUtilizedAfterAllocation;
	}

	public static int TIME_INTERVAL = 5;

	protected void checkConsumption(PowerHost host, boolean currentOverload){
		PowerHostUtilizationHistory host1 = (PowerHostUtilizationHistory) host;
		// 当前过载
		if(currentOverload){
			// 过载次数+1
			host1.setOverloadTimes(host1.getOverloadTimes() + 1);
			// 找到上一次放置的时间
			if(host1.getLastPlaceTime() != -1 && host1.getLastPlaceTime() < CloudSim.clock()){
				//上一次放置到当前过载的时间间隔
				int placeTimeStep = (int) ((CloudSim.clock() - host1.getLastPlaceTime())/ 300 + 1);
				//如果这个间隔小，说明放置完很快就过载了
				if(placeTimeStep <= TIME_INTERVAL){
					host1.setAfterPlaceOverloadTime(host1.getAfterPlaceOverloadTime()+1);
				}
//				if(host1.getLastOverloadInterval() != -1){
//					host1.setOverloadBeforePlaceAfterPlaceOverloadTime(host1.getOverloadBeforePlaceAfterPlaceOverloadTime() + 1);
//					if(host1.getUtilizationHistory().length >= 30){
//						System.out.println("放置的时间间隔: " + placeTimeStep + "   |放置之前的时间间隔： "+ host1.getLastOverloadInterval() + "   | "+Arrays.toString(host1.getUtilizationHistory()));
//					}
//				}
				//找到上一次放置之前主机过载的间隔
				if(host1.getLastPlaceTime() >= host1.getLastOverloadTime()){
					int overloadTimeStep = (int) ((host1.getLastPlaceTime() - host1.getLastOverloadTime())/ 300 + 1);
					//如果这个间隔小，说明过去发生了过载
					if(overloadTimeStep <= TIME_INTERVAL){
						host1.setOverloadBeforePlaceAfterPlaceOverloadTime(host1.getOverloadBeforePlaceAfterPlaceOverloadTime() + 1);
					}
				}
			}
			host1.setLastOverloadTime(CloudSim.clock());
		}
	}

	@Override
	protected List<? extends Vm>
	getVmsToMigrateFromHosts(List<PowerHostUtilizationHistory> overUtilizedHosts) {
		List<Vm> vmsToMigrate = new LinkedList<Vm>();
		for (PowerHostUtilizationHistory host : overUtilizedHosts) {
			while (true) {
				Vm vm = getVmSelectionPolicy().getVmToMigrate(host);
				if (vm == null) {
					break;
				}
				vmsToMigrate.add(vm);
				host.vmDestroy(vm);
				if (!isHostCurrentOverLoad(host)) {
					break;
				}
			}
		}
		return vmsToMigrate;
	}

	public PowerHost findHostForVm(Vm vm, Set<? extends Host> excludedHosts) {
		PowerHost allocatedHost = null;
		double minPower = Double.MAX_VALUE;
		if(getPlacement().equals("PABFD")){
			for (PowerHost host : this.<PowerHost> getHostList()) {
				if (excludedHosts.contains(host)) {
					continue;
				}
				if (host.isSuitableForVm(vm)) {
					if (getUtilizationOfCpuMips(host) != 0 && isHostOverUtilizedAfterAllocation(host, vm)) {
						continue;
					}

					try {
						double powerAfterAllocation = getPowerAfterAllocation(host, vm);
						if (powerAfterAllocation != -1) {
							double oripower = host.getPower();
//						if(host.getUtilizationOfCpu() == 0)
//						{
//							oripower = 0;
//						}
							double powerDiff = powerAfterAllocation - oripower;
							if (powerDiff < minPower) {
								minPower = powerDiff;
								allocatedHost = host;
							}
						}
					} catch (Exception e) {
					}
				}
			}
		}else if(getPlacement().equals("BFD")){
			List<PowerHost> list = getHostList();
			double minLeft = Double.MAX_VALUE;
			PowerHost targetHost = null;
			for (PowerHost host : list) {
				if (excludedHosts.contains(host)) {
					continue;
				}
				if (host.isSuitableForVm(vm)) {
					if (getUtilizationOfCpuMips(host) != 0 && isHostOverUtilizedAfterAllocation(host, vm)) {
						continue;
					}
					double leftMips = host.getTotalMips() - host.getUtilizationOfCpuMips() - vm.getTotalUtilizationOfCpuMips(CloudSim.clock());
					if(leftMips < minLeft){
						minLeft = leftMips;
						targetHost = host;
					}
					return targetHost;
				}
			}
		}else if(getPlacement().equals("FFD")){
			List<PowerHost> list = getHostList();
			for (PowerHost host : list) {
				if (excludedHosts.contains(host)) {
					continue;
				}
				if (host.isSuitableForVm(vm)) {
					if (getUtilizationOfCpuMips(host) != 0 && isHostOverUtilizedAfterAllocation(host, vm)) {
						continue;
					}
					allocatedHost = host;
					break;
				}
			}
		} else if(getPlacement().equals("RANDOM")){
			List<Host> list = getHostList().stream().filter(host -> !excludedHosts.contains(host) && host.isSuitableForVm(vm)).collect(Collectors.toList());
			int n = list.size();
			if(n == 0){
				return null;
			}else{
				Set<PowerHost> set = new HashSet<>();
				int index = random.nextInt(n);
				PowerHost tempHost = (PowerHost) list.get(index);
				set.add(tempHost);
				while (set.size() != n && getUtilizationOfCpuMips(tempHost) != 0 && isHostOverUtilizedAfterAllocation(tempHost, vm)) {
					set.add(tempHost);
					index = new Random().nextInt(n);
					tempHost = (PowerHost) list.get(index);
				}
				if(set.size() == n){
					return null;
				}
				return tempHost;
			}
		}
		// 设置放置前过载的间隔
		if(allocatedHost != null){
			PowerHostUtilizationHistory host1 = (PowerHostUtilizationHistory) allocatedHost;
			if (host1.vmCreate(vm)) {
				host1.setLastOverloadInterval(checkOverloadInThePast(host1));
				host1.vmDestroy(vm);
			}
			host1.setLastPlaceTime(CloudSim.clock());
		}
		return allocatedHost;
	}

	private int checkOverloadInThePast(PowerHostUtilizationHistory allocatedHost) {
		double[] history = allocatedHost.getUtilizationHistory();
		for(int i = 0; i < Math.min(TIME_INTERVAL, history.length); ++i){
			if(history[i] > getUtilizationThreshold()){
				return i+1;
			}
		}
		return -1;
	}


}
