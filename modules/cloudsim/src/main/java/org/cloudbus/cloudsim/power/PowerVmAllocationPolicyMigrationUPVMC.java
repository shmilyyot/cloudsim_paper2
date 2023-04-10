package org.cloudbus.cloudsim.power;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.util.MathUtil;

import java.util.*;

public class PowerVmAllocationPolicyMigrationUPVMC extends
        PowerVmAllocationPolicyMigrationAbstract{
    private double utilizationThreshold = 0.8;
    private double schedulingInterval = 300;


    public PowerVmAllocationPolicyMigrationUPVMC(
            List<? extends Host> hostList,
            PowerVmSelectionPolicy vmSelectionPolicy,
            double utilizationThreshold) {
        super(hostList, vmSelectionPolicy);
        setUtilizationThreshold(utilizationThreshold);
    }


    @Override
    protected boolean isHostOverUtilized(PowerHost host) {
        addHistoryEntry(host, getUtilizationThreshold());
        boolean futureOverload = isHostFutureOverUtilized(host);
        boolean currentOverload = isHostCurrentOverUtilized(host);
        return currentOverload;
    }

    protected boolean isHostCurrentOverUtilized(PowerHost host) {
        addHistoryEntry(host, getUtilizationThreshold());
        double totalRequestedMips = 0;
        for (Vm vm : host.getVmList()) {
            totalRequestedMips += vm.getCurrentRequestedTotalMips();
        }
        double utilization = totalRequestedMips / host.getTotalMips();
        boolean futureOverload = isHostFutureOverUtilized(host);
        return utilization > getUtilizationThreshold();
    }

    protected boolean isHostFutureOverUtilized(PowerHost host) {
        PowerHostUtilizationHistory _host = (PowerHostUtilizationHistory) host;
        double[] utilizationHistory = _host.getUtilizationHistory();
        int length = 10; // we use 10 to make the regression responsive enough to latest values
        if (utilizationHistory.length < length) {
            return false;
        }
        double[] utilizationHistoryReversed = new double[length];
        for (int i = 0; i < length; i++) {
            utilizationHistoryReversed[i] = utilizationHistory[length - i - 1];
        }
        double[] estimates = null;
        try {
            estimates = getParameterEstimates(utilizationHistoryReversed);
        } catch (IllegalArgumentException e) {
            return false;
        }
        double migrationIntervals = Math.ceil(getMaximumVmMigrationTime(_host) / getSchedulingInterval());
        double predictedUtilization = estimates[0] + estimates[1] * (length + migrationIntervals);
//        predictedUtilization *= 1.2;

//        addHistoryEntry(host, predictedUtilization);
        return predictedUtilization > getUtilizationThreshold();
    }

    protected double[] getParameterEstimates(double[] utilizationHistoryReversed) {
        return MathUtil.getLoessParameterEstimates(utilizationHistoryReversed);
    }

    protected double getMaximumVmMigrationTime(PowerHost host) {
        int maxRam = Integer.MIN_VALUE;
        for (Vm vm : host.getVmList()) {
            int ram = vm.getRam();
            if (ram > maxRam) {
                maxRam = ram;
            }
        }
        return maxRam / ((double) host.getBw() / (2 * 8000));
    }

    /**
     * Sets the scheduling interval.
     *
     * @param schedulingInterval the new scheduling interval
     */
    protected void setSchedulingInterval(double schedulingInterval) {
        this.schedulingInterval = schedulingInterval;
    }

    /**
     * Gets the scheduling interval.
     *
     * @return the scheduling interval
     */
    protected double getSchedulingInterval() {
        return schedulingInterval;
    }

    protected void setUtilizationThreshold(double utilizationThreshold) {
        this.utilizationThreshold = utilizationThreshold;
    }
    protected double getUtilizationThreshold() {
        return utilizationThreshold;
    }

    protected boolean isHostFutureOverUtilizedAfterAllocation(PowerHost host, Vm vm) {
        boolean isHostOverUtilizedAfterAllocation = true;
        PowerVm powerVm = (PowerVm) vm;
        List<Double> vmUsages = powerVm.getUtilizationHistory();
        double[] vmHistory = MathUtil.doubleListToArray(vmUsages);
        int length = 10; // we use 10 to make the regression responsive enough to latest values
        if (vmHistory.length < length) {
            return false;
        }
        double[] utilizationHistoryReversed = new double[length];
        for (int i = 0; i < length; i++) {
            utilizationHistoryReversed[i] = vmHistory[length - i - 1];
        }
        double[] estimates = null;
        try {
            estimates = getParameterEstimates(utilizationHistoryReversed);
        } catch (IllegalArgumentException e) {
            return false;
        }
        double vmPredictedUtilization = estimates[0] + estimates[1] * length;

        PowerHostUtilizationHistory _host = (PowerHostUtilizationHistory) host;
        double[] utilizationHistory = _host.getUtilizationHistory();
        if (utilizationHistory.length < length) {
            return false;
        }
        double[] hostUtilizationHistoryReversed = new double[length];
        for (int i = 0; i < length; i++) {
            utilizationHistoryReversed[i] = utilizationHistory[length - i - 1];
        }
        double[] hostEstimates = null;
        try {
            hostEstimates = getParameterEstimates(utilizationHistoryReversed);
        } catch (IllegalArgumentException e) {
            return false;
        }
        double migrationIntervals = Math.ceil(getMaximumVmMigrationTime(_host) / getSchedulingInterval());
        double predictedUtilization = hostEstimates[0] + hostEstimates[1] * (length + migrationIntervals);



//        if (host.vmCreate(vm)) {
//            isHostOverUtilizedAfterAllocation = isHostFutureOverUtilized(host);
//            host.vmDestroy(vm);
//        }

        return (vmPredictedUtilization * powerVm.getMips() + predictedUtilization * _host.getTotalMips()) > getUtilizationThreshold() * _host.getTotalMips();
    }

    protected boolean isHostOverUtilizedAfterAllocation(PowerHost host, Vm vm) {
        boolean isHostOverUtilizedAfterAllocation = true;
        if (host.vmCreate(vm)) {
            isHostOverUtilizedAfterAllocation = isHostCurrentOverUtilized(host);
            host.vmDestroy(vm);
        }
        return isHostOverUtilizedAfterAllocation;
    }

    public PowerHost findHostForVmUnderLoad(Vm vm, Set<? extends Host> excludedHosts, List<PowerHost> activeHost) {
        PowerHost allocatedHost = null;

        for(PowerHost host: activeHost){
            if(vm.getHost() == host){
                break;
            }
            if (excludedHosts.contains(host)) {
                continue;
            }
            if (host.isSuitableForVm(vm)) {
                if (isHostOverUtilizedAfterAllocation(host, vm) || isHostFutureOverUtilizedAfterAllocation(host, vm)) {
                    continue;
                }
                return host;
            }
        }
        return allocatedHost;
    }

    public PowerHost findHostForVm(Vm vm, Set<? extends Host> excludedHosts) {
        PowerHost allocatedHost = null;
        List<PowerHost> hostList = getHostList();
        List<PowerHost> activeHost = new ArrayList<>();
        List<PowerHost> idleHosts = new ArrayList<>();
//        for(PowerHost host:hostList){
//            if(host.getUtilizationOfCpu() != 0.0){
//                activeHost.add(host);
//            }else{
//                idleHosts.add(host);
//            }
//        }

        for(PowerHost host: hostList){
            if (excludedHosts.contains(host)) {
                continue;
            }
            if (host.isSuitableForVm(vm)) {
                if (isHostOverUtilizedAfterAllocation(host, vm) || isHostFutureOverUtilizedAfterAllocation(host, vm)) {
                    continue;
                }
                return host;
            }
        }

//        for(PowerHost host: idleHosts){
//            if (excludedHosts.contains(host)) {
//                continue;
//            }
//            if (host.isSuitableForVm(vm)) {
//                if (getUtilizationOfCpuMips(host) != 0 && (isHostOverUtilizedAfterAllocation(host, vm) || isHostFutureOverUtilizedAfterAllocation(host, vm))) {
//                    continue;
//                }
//                allocatedHost = host;
//                break;
//            }
//        }
        return allocatedHost;
    }

    protected List<Map<String, Object>> getMigrationMapFromUnderUtilizedHosts(
            List<PowerHostUtilizationHistory> overUtilizedHosts) {
        List<Map<String, Object>> migrationMap = new LinkedList<Map<String, Object>>();
        List<PowerHost> switchedOffHosts = getSwitchedOffHosts();

        // over-utilized hosts + hosts that are selected to migrate VMs to from over-utilized hosts
        Set<PowerHost> excludedHostsForFindingUnderUtilizedHost = new HashSet<PowerHost>();
        excludedHostsForFindingUnderUtilizedHost.addAll(overUtilizedHosts);
        excludedHostsForFindingUnderUtilizedHost.addAll(switchedOffHosts);
        excludedHostsForFindingUnderUtilizedHost.addAll(extractHostListFromMigrationMap(migrationMap));

        // over-utilized + under-utilized hosts
        Set<PowerHost> excludedHostsForFindingNewVmPlacement = new HashSet<PowerHost>();
        excludedHostsForFindingNewVmPlacement.addAll(overUtilizedHosts);
        excludedHostsForFindingNewVmPlacement.addAll(switchedOffHosts);

        int numberOfHosts = getHostList().size();

        while (true) {
            if (numberOfHosts == excludedHostsForFindingUnderUtilizedHost.size()) {
                break;
            }
            PowerHost underUtilizedHost = getUnderUtilizedHost(excludedHostsForFindingUnderUtilizedHost);
            if (underUtilizedHost == null) {
                break;
            }

            Log.printConcatLine("Under-utilized host: host #", underUtilizedHost.getId(), "\n");

            excludedHostsForFindingUnderUtilizedHost.add(underUtilizedHost);
            excludedHostsForFindingNewVmPlacement.add(underUtilizedHost);

            List<? extends Vm> vmsToMigrateFromUnderUtilizedHost = getVmsToMigrateFromUnderUtilizedHost(underUtilizedHost);
            if (vmsToMigrateFromUnderUtilizedHost.isEmpty()) {
                continue;
            }

            Log.print("Reallocation of VMs from the under-utilized host: ");
            if (!Log.isDisabled()) {
                for (Vm vm : vmsToMigrateFromUnderUtilizedHost) {
                    Log.print(vm.getId() + " ");
                }
            }
            Log.printLine();
            List<Map<String, Object>> newVmPlacement = getNewVmPlacementFromUnderUtilizedHost(
                    vmsToMigrateFromUnderUtilizedHost,
                    excludedHostsForFindingNewVmPlacement);

            excludedHostsForFindingUnderUtilizedHost.addAll(extractHostListFromMigrationMap(newVmPlacement));

            migrationMap.addAll(newVmPlacement);
            Log.printLine();
        }

        return migrationMap;
    }

    protected List<Map<String, Object>> getNewVmPlacementFromUnderUtilizedHost(
            List<? extends Vm> vmsToMigrate,
            Set<? extends Host> excludedHosts) {
        List<Map<String, Object>> migrationMap = new LinkedList<Map<String, Object>>();
        List<PowerHost> hostList = getHostList();
        List<PowerHost> activeHost = new ArrayList<>();
        for(PowerHost host:hostList){
            if(host.getUtilizationOfCpu() != 0.0){
                activeHost.add(host);
            }
        }
        activeHost.sort(((a,b)->{
            double u1 = a.getUtilizationOfCpu();
            double u2 = b.getUtilizationOfCpu();
            if(u1 > u2){
                return -1;
            }else if(u1 < u2){
                return 1;
            }else{
                return 0;
            }
        }));

        vmsToMigrate.sort((a, b)->{
            double u1 = a.getTotalUtilizationOfCpu(CloudSim.clock());
            double u2 = b.getTotalUtilizationOfCpu(CloudSim.clock());
            if(u1 < u2){
                return 1;
            }else if(u1 > u2){
                return -1;
            }else{
                return 0;
            }
        });

        for (Vm vm : vmsToMigrate) {
            PowerHost allocatedHost = findHostForVmUnderLoad(vm, excludedHosts, activeHost);
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
