package org.cloudbus.cloudsim.power;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;

import java.util.*;

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
        return utilization > getUtilizationThreshold();
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
        return allocatedHost;
    }

}
