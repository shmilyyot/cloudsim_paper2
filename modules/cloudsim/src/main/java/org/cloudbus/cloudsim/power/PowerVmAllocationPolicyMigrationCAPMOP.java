package org.cloudbus.cloudsim.power;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.util.MathUtil;

import java.util.*;

public class PowerVmAllocationPolicyMigrationCAPMOP extends
        PowerVmAllocationPolicyMigrationAbstract{
    private double utilizationThreshold = 0.7;
    private double underloadUtilizationThreshold = 0.3;

    private static final int THhd = 12;

    public PowerVmAllocationPolicyMigrationCAPMOP(
            List<? extends Host> hostList,
            PowerVmSelectionPolicy vmSelectionPolicy,
            double utilizationThreshold) {
        super(hostList, vmSelectionPolicy);
        setUtilizationThreshold(utilizationThreshold);
    }

    protected boolean isHostOverUtilized(PowerHost host) {
        addHistoryEntry(host, getUtilizationThreshold());
        double totalRequestedMips = 0;
        for (Vm vm : host.getVmList()) {
            totalRequestedMips += vm.getCurrentRequestedTotalMips();
        }
        double utilization = totalRequestedMips / host.getTotalMips();
        return utilization > getUtilizationThreshold();
    }

    protected void setUtilizationThreshold(double utilizationThreshold) {
        this.utilizationThreshold = utilizationThreshold;
    }

    protected double getUtilizationThreshold() {
        return utilizationThreshold;
    }

    public double getUnderloadUtilizationThreshold() {
        return underloadUtilizationThreshold;
    }

    public void setUnderloadUtilizationThreshold(double underloadUtilizationThreshold) {
        this.underloadUtilizationThreshold = underloadUtilizationThreshold;
    }

    protected List<Map<String, Object>> getNewVmPlacement(
            List<? extends Vm> vmsToMigrate,
            Set<? extends Host> excludedHosts) {
        List<Map<String, Object>> migrationMap = new LinkedList<Map<String, Object>>();
        if(vmsToMigrate.size() > 1){
            vmsToMigrate.sort((a, b)-> {
                double u1 = a.getTotalUtilizationOfCpu(CloudSim.clock());
                double u2 = b.getTotalUtilizationOfCpu(CloudSim.clock());
                if(u1 > u2){
                    return -1;
                }else if(u1 < u2){
                    return 1;
                }else{
                    return 0;
                }

            });
        }
        for (Vm vm : vmsToMigrate) {
            PowerHost allocatedHost = findHostForVm(vm, excludedHosts);
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

    public PowerHost findHostForVm(Vm vm, Set<? extends Host> excludedHosts) {
        PowerHost allocatedHost = null;
        PowerVm powerVm = (PowerVm) vm;
        if(powerVm.getUtilizationHistory().size() < THhd){
            allocatedHost = MOP(powerVm, excludedHosts);
        }else{
            allocatedHost = CAP(powerVm, excludedHosts);
        }
        return allocatedHost;
    }

    public boolean isUnderloadHost(PowerHost host){
        return host.getUtilizationOfCpu() < getUnderloadUtilizationThreshold();
    }

    public boolean isNormalHost(PowerHost host){
        double utilization = avgLoad(host);
        return utilization < 0.7 &&  utilization > getUnderloadUtilizationThreshold();
    }

    public double avgLoad(PowerHost host){
        PowerHostUtilizationHistory powerHost = (PowerHostUtilizationHistory) host;
        OptionalDouble utilization = Arrays.stream(powerHost.getUtilizationHistory()).average();
        return utilization.isPresent() ? utilization.getAsDouble() : 0;
    }

    public double calOP(PowerHost host){
        PowerHostUtilizationHistory powerHost = (PowerHostUtilizationHistory) host;
        double utilization = avgLoad(powerHost);
        return (utilization - getUnderloadUtilizationThreshold()) / (0.7 - getUnderloadUtilizationThreshold());
    }

    public PowerHost MOP(PowerVm vm, Set<? extends Host> excludedHosts){
        PowerHost allocatedHost = null;
        List<PowerHost> hostList = getHostList();
        double minOverload = Double.MAX_VALUE;
        for(PowerHost host: hostList){
            if (excludedHosts.contains(host)) {
                continue;
            }
            if (host.isSuitableForVm(vm)) {
                if (isHostOverUtilizedAfterAllocation(host, vm) || !isNormalHost(host)) {
                    continue;
                }
                double op = calOP(host);
                if(op < minOverload){
                    allocatedHost = host;
                    minOverload = op;
                }
            }
        }
        if(allocatedHost == null){
            for(PowerHost host: hostList){
                if (excludedHosts.contains(host)) {
                    continue;
                }
                if (host.isSuitableForVm(vm)) {
                    if (isHostOverUtilizedAfterAllocation(host, vm)) {
                        continue;
                    }
                    double op = calOP(host);
                    if(op < minOverload){
                        allocatedHost = host;
                        minOverload = op;
                    }
                }
            }
        }
        return allocatedHost;
    }

    public PowerHost CAP(PowerVm vm, Set<? extends Host> excludedHosts){
        PowerHost allocatedHost = null;
        List<PowerHost> hostList = getHostList();
        double minCorr = Double.MAX_VALUE;
        for(PowerHost host: hostList){
            if (excludedHosts.contains(host)) {
                continue;
            }
            PowerHostUtilizationHistory powerHostUtilizationHistory = (PowerHostUtilizationHistory) host;
            if (host.isSuitableForVm(vm)) {
                if (isHostOverUtilizedAfterAllocation(host, vm)) {
                    continue;
                }
                double[] hostData = powerHostUtilizationHistory.getUtilizationHistory();
                double[] vmData = MathUtil.listToArray(vm.getUtilizationHistory());
                double corr =  MathUtil.corr(hostData, vmData, Math.min(THhd, Math.min(hostData.length, vmData.length)));;
                if(corr < minCorr){
                    allocatedHost = host;
                    minCorr = corr;
                }
            }
        }
//        if(allocatedHost == null){
//            for(PowerHost host: hostList){
//                if (excludedHosts.contains(host)) {
//                    continue;
//                }
//                PowerHostUtilizationHistory powerHostUtilizationHistory = (PowerHostUtilizationHistory) host;
//                if (host.isSuitableForVm(vm)) {
//                    return host;
//                }
//            }
//        }
        return allocatedHost;
    }

    protected List<Map<String, Object>> getNewVmPlacementFromUnderUtilizedHost(
            List<? extends Vm> vmsToMigrate,
            Set<? extends Host> excludedHosts) {
        List<Map<String, Object>> migrationMap = new LinkedList<Map<String, Object>>();
        if(vmsToMigrate.size() > 1){
            vmsToMigrate.sort((a, b)-> {
                double u1 = a.getTotalUtilizationOfCpu(CloudSim.clock());
                double u2 = b.getTotalUtilizationOfCpu(CloudSim.clock());
                if(u1 > u2){
                    return -1;
                }else if(u1 < u2){
                    return 1;
                }else{
                    return 0;
                }

            });
        }
        for (Vm vm : vmsToMigrate) {
            PowerHost allocatedHost = findHostForVm(vm, excludedHosts);
            if (allocatedHost != null) {
                allocatedHost.vmCreate(vm);
                Log.printConcatLine("VM #", vm.getId(), " allocated to host #", allocatedHost.getId());

                Map<String, Object> migrate = new HashMap<String, Object>();
                migrate.put("vm", vm);
                migrate.put("host", allocatedHost);
                migrationMap.add(migrate);
            } else {
                Log.printLine("Not all VMs can be reallocated from the host, reallocation cancelled");
                for (Map<String, Object> map : migrationMap) {
                    ((Host) map.get("host")).vmDestroy((Vm) map.get("vm"));
                }
                migrationMap.clear();
                break;
            }
        }
        return migrationMap;
    }
}
