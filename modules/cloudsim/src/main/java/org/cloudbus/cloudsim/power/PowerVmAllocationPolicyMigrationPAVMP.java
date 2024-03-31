package org.cloudbus.cloudsim.power;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;

import java.util.*;
import java.util.stream.Collectors;

public class PowerVmAllocationPolicyMigrationPAVMP extends PowerVmAllocationPolicyMigrationAbstract{
    /** The static host CPU utilization threshold to detect over utilization.
     * It is a percentage value from 0 to 1
     * that can be changed when creating an instance of the class. */
    private double utilizationThreshold = 0.9;

    private int T = 12;

    /**
     * Instantiates a new PowerVmAllocationPolicyMigrationStaticThreshold.
     *
     * @param hostList the host list
     * @param vmSelectionPolicy the vm selection policy
     * @param utilizationThreshold the utilization threshold
     */
    public PowerVmAllocationPolicyMigrationPAVMP(
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
        boolean overloaded = utilization > getUtilizationThreshold();
        checkConsumption(host, overloaded);
        return overloaded;
    }

    protected boolean isHostCurrentOverUtilized(PowerHost host) {
        addHistoryEntry(host, getUtilizationThreshold());
        double totalRequestedMips = 0;
        for (Vm vm : host.getVmList()) {
            totalRequestedMips += vm.getCurrentRequestedTotalMips();
        }
        double utilization = totalRequestedMips / host.getTotalMips();
        boolean overloaded = utilization > getUtilizationThreshold();
//        checkConsumption(host, overloaded);
        return overloaded;
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

    protected double AffinityComputing(PowerVm vmi, PowerVm vmj){
        double sum = 0;
        double time = CloudSim.clock();
//        if(time < (9000.0 - T * 300)){
//            return Math.sqrt(Math.pow(vmi.getTotalUtilizationOfCpu(time) + vmj.getTotalUtilizationOfCpu(time) - (vmi.getTotalUtilizationOfCpu(time) + vmj.getTotalUtilizationOfCpu(time)) / 2, 2));
//        }
        for(int i = 0; i < T; ++i){
            sum += vmi.getPredictUtilization((T+1) * time) + vmj.getPredictUtilization((T+1) * time);
        }
        double uij = sum / T;
        double diff = 0;
        for(int i = 0; i < T; ++i){
            diff += Math.pow(vmi.getPredictUtilization((T+1) * time) + vmj.getPredictUtilization((T+1) * time) - uij, 2);
        }
        return Math.sqrt(diff / T);
    }

    protected List<Map<String, Object>> getNewVmPlacement(
            List<? extends Vm> vmsToMigrate,
            Set<? extends Host> excludedHosts) {
        List<Map<String, Object>> migrationMap = new LinkedList<Map<String, Object>>();
//		PowerVmList.sortByCpuUtilization(vmsToMigrate);
        double time = CloudSim.clock();
        for (Vm vm : vmsToMigrate) {
            PowerHost allocatedHost = (time >= (9000.0 - T * 300)) ? findHostForVmPAVMP(vm, excludedHosts) : findHostForVm(vm, excludedHosts);
//            PowerHost allocatedHost = findHostForVmPAVMP(vm, excludedHosts);
            if (allocatedHost != null) {
                allocatedHost.vmCreate(vm);
                Log.printConcatLine("VM #", vm.getId(), " allocated to host #", allocatedHost.getId());

                Map<String, Object> migrate = new HashMap<String, Object>();
                migrate.put("vm", vm);
                migrate.put("host", allocatedHost);
                migrationMap.add(migrate);
            }
        }
        return migrationMap;
    }

    public PowerHost findHostForVmPAVMP(Vm vm, Set<? extends Host> excludedHosts) {
        PowerHost allocatedHost = null;
        PowerVm powerVm = (PowerVm) vm;
        List<PowerHost> hostList = getHostList();
        double Aij_min = Double.MAX_VALUE;
        for(PowerHost host: hostList){
            if (excludedHosts.contains(host)) {
                continue;
            }
            if (host.isSuitableForVm(vm)) {
                if (getUtilizationOfCpuMips(host) != 0 && isHostOverUtilizedAfterAllocation(host, vm)) {
                    continue;
                }
                double Aij = 0.0;
                for(Vm tempVm: host.getVmList()){
                    PowerVm tvm = (PowerVm) tempVm;
                    Aij += AffinityComputing(powerVm, tvm);
                }
                if(Aij < Aij_min){
                    Aij_min = Aij;
                    allocatedHost = host;
                }
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

    @Override
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

    private int checkOverloadInThePast(PowerHostUtilizationHistory allocatedHost) {
        double[] history = allocatedHost.getUtilizationHistory();
        for(int i = 0; i < Math.min(TIME_INTERVAL, history.length); ++i){
            if(history[i] > getUtilizationThreshold()){
                return i+1;
            }
        }
        return -1;
    }

    protected boolean isHostOverUtilizedAfterAllocation(PowerHost host, Vm vm) {
        boolean isHostOverUtilizedAfterAllocation = true;
        if (host.vmCreate(vm)) {
            isHostOverUtilizedAfterAllocation = isHostCurrentOverUtilized(host);
            host.vmDestroy(vm);
        }
        return isHostOverUtilizedAfterAllocation;
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
                if (!isHostCurrentOverUtilized(host)) {
                    break;
                }
            }
        }
        return vmsToMigrate;
    }

}
