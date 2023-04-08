package org.cloudbus.cloudsim.power;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.util.MathUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

public class PowerVmAllocationPolicyMigrationPEAP extends
		PowerVmAllocationPolicyMigrationAbstract {
    /** The static host CPU utilization threshold to detect over utilization.
     * It is a percentage value from 0 to 1
     * that can be changed when creating an instance of the class. */
    private double utilizationThreshold = 0.8;

    /**
     * Instantiates a new PowerVmAllocationPolicyMigrationStaticThreshold.
     *
     * @param hostList the host list
     * @param vmSelectionPolicy the vm selection policy
     * @param utilizationThreshold the utilization threshold
     */
    public PowerVmAllocationPolicyMigrationPEAP(
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
//        return loadMovingAvg((PowerHostUtilizationHistory) host, 3) > getUtilizationThreshold();
        return utilization > getUtilizationThreshold();
    }

    protected boolean isHostCurrentUtilized(PowerHost host) {
        addHistoryEntry(host, getUtilizationThreshold());
        double totalRequestedMips = 0;
        for (Vm vm : host.getVmList()) {
            totalRequestedMips += vm.getCurrentRequestedTotalMips();
        }
        double utilization = totalRequestedMips / host.getTotalMips();
        return utilization > getUtilizationThreshold();
    }

    protected boolean isHostOverUtilizedAfterAllocation(PowerHost host, Vm vm) {
        boolean isHostOverUtilizedAfterAllocation = true;
        if (host.vmCreate(vm)) {
            isHostOverUtilizedAfterAllocation = isHostCurrentUtilized(host);
            host.vmDestroy(vm);
        }
        return isHostOverUtilizedAfterAllocation;
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

    public PowerHost findHostForVm(Vm vm, Set<? extends Host> excludedHosts) {
        PowerHost allocatedHost = null;

        // 计算虚拟机的cpu offer
        Long vmOffer = Math.round(vm.getMips() * vm.getTotalUtilizationOfCpuMips(CloudSim.clock()) / 1000);

        //创建三个列表
        List<PowerHost> idleList = buildIdleHost();
        List<PowerHost> SH = buildSH();
        TreeMap<Long, List<PowerHost>> LrList = buildLr(SH);

        // 遍历最佳cpuoffer
        if(LrList.containsKey(vmOffer)){
            for(PowerHost host: LrList.get(vmOffer)){
                if (excludedHosts.contains(host)) {
                    continue;
                }
                if (host.isSuitableForVm(vm)) {
                    if (getUtilizationOfCpuMips(host) != 0 && isHostOverUtilizedAfterAllocation(host, vm)) {
                        continue;
                    }
                    return host;
                }
            }
        }

        // 遍历SH
        for (PowerHost host : SH) {
            if (excludedHosts.contains(host)) {
                continue;
            }
            if (host.isSuitableForVm(vm)) {
                if (getUtilizationOfCpuMips(host) != 0 && isHostOverUtilizedAfterAllocation(host, vm)) {
                    continue;
                }
                return host;
            }
        }

        // 遍历闲置的
        for (PowerHost host : idleList) {
            if (excludedHosts.contains(host)) {
                continue;
            }
            if (host.isSuitableForVm(vm)) {
                if (getUtilizationOfCpuMips(host) != 0 && isHostOverUtilizedAfterAllocation(host, vm)) {
                    continue;
                }
                return host;
            }
        }

        return allocatedHost;
    }

    List<PowerHost>  buildSH(){
        List<PowerHost> hostList = getHostList();
        List<PowerHost> overBestHost = new ArrayList<>();
        List<PowerHost> tempHost = new ArrayList<>();
        for(PowerHost host: hostList){
            if(isHostOverUtilized(host) || host.getUtilizationOfCpu() == 0){
                continue;
            }
            if(host.getUtilizationOfCpu() > host.getPpr()){
                overBestHost.add(host);
            }else{
                tempHost.add(host);
            }
        }
        tempHost.sort(Comparator.comparing(PowerHost::getPeakPeff).reversed());
        tempHost.addAll(overBestHost);
        return tempHost;
    }

    TreeMap<Long, List<PowerHost>> buildLr(List<PowerHost> hostList){
        TreeMap<Long, List<PowerHost>> Lr = new TreeMap<>();
        for(PowerHost host: hostList){
            if(isHostOverUtilized(host) || host.getUtilizationOfCpu() == 0){
                continue;
            }
            Long cpuOffer = host.getCpuOffer();
            Lr.getOrDefault(cpuOffer, new ArrayList<>()).add(host);
        }
        return Lr;
    }

    List<PowerHost> buildIdleHost(){
        List<PowerHost> hostList = getHostList();
        List<PowerHost> tempHost = new ArrayList<>();
        for(PowerHost host: hostList){
            if(host.getUtilizationOfCpu() == 0){
                tempHost.add(host);
            }
        }
        return tempHost;
    }

    double loadMovingAvg(PowerHostUtilizationHistory host, int W_SIZE){
        double[] utilizationWindows = new double[W_SIZE];
        double[] host_utilization = host.getUtilizationHistory();
        if(host_utilization.length < W_SIZE){
            return host.getUtilizationOfCpu();
        }
        System.arraycopy(host_utilization, 0, utilizationWindows, 0, W_SIZE);
        double ans = 0.0;
        for(int t = 0; t < W_SIZE; ++t){
            double w = Math.pow(Math.E, -t) * (1 - 1 / Math.E) / (1 - Math.pow(Math.E, -W_SIZE));
            ans += w * utilizationWindows[t];
        }
        return ans;
    }

    double thresholdALIQ(){
        List<PowerHost> hostList = getHostList();
        List<Double> utilizations = new ArrayList<>();
        for(PowerHost host: hostList){
            double utilization = host.getUtilizationOfCpu();
            if(utilization == 0){
                continue;
            }
            utilizations.add(utilization);
        }
        utilizations.sort(Comparator.comparingDouble(a -> a));
        int p25 = (utilizations.size()) / 4 , p75 = 3 * (utilizations.size()) / 4;
        double total = 0.0;
        for(int i = p25; i <= p75; ++i){
            total += utilizations.get(i);
        }
        return getUtilizationThreshold() - total / (p75 - p25 + 1);
    }

    protected PowerHost getUnderUtilizedHost(Set<? extends Host> excludedHosts) {
        PowerHost underUtilizedHost = null;
        double aliq = thresholdALIQ();
        double minValue = Double.MAX_VALUE;
        for (PowerHost host : this.<PowerHost> getHostList()) {
            if (excludedHosts.contains(host)) {
                continue;
            }
//            double utilization = loadMovingAvg((PowerHostUtilizationHistory) host, 3);
            if(host.getUtilizationOfCpu() > 0 && host.getUtilizationOfCpu() < aliq &&  !areAllVmsMigratingOutOrAnyVmMigratingIn(host)){
//                minValue = utilization;
//                underUtilizedHost = host;
                return host;
            }
        }
        return underUtilizedHost;
    }

}
