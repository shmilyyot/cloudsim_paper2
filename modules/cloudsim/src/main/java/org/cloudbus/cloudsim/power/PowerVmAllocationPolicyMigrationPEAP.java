package org.cloudbus.cloudsim.power;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.util.ExecutionTimeMeasurer;

import java.util.*;

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
        boolean overloaded = utilization > getUtilizationThreshold();
        checkConsumption(host, overloaded);
        return overloaded;
    }

    protected boolean isHostCurrentUtilized(PowerHost host) {
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
                    // 设置放置前过载的间隔
                    if(host != null){
                        PowerHostUtilizationHistory host1 = (PowerHostUtilizationHistory) host;
                        if (host1.vmCreate(vm)) {
                            host1.setLastOverloadInterval(checkOverloadInThePast(host1));
                            host1.vmDestroy(vm);
                        }
                        host1.setLastPlaceTime(CloudSim.clock());
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
                // 设置放置前过载的间隔
                if(host != null){
                    PowerHostUtilizationHistory host1 = (PowerHostUtilizationHistory) host;
                    if (host1.vmCreate(vm)) {
                        host1.setLastOverloadInterval(checkOverloadInThePast(host1));
                        host1.vmDestroy(vm);
                    }
                    host1.setLastPlaceTime(CloudSim.clock());
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
                // 设置放置前过载的间隔
                if(host != null){
                    PowerHostUtilizationHistory host1 = (PowerHostUtilizationHistory) host;
                    if (host1.vmCreate(vm)) {
                        host1.setLastOverloadInterval(checkOverloadInThePast(host1));
                        host1.vmDestroy(vm);
                    }
                    host1.setLastPlaceTime(CloudSim.clock());
                }
                return host;
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

    List<PowerHost>  buildSH(){
        List<PowerHost> hostList = getHostList();
        List<PowerHost> overBestHost = new ArrayList<>();
        List<PowerHost> tempHost = new ArrayList<>();
        for(PowerHost host: hostList){
            if(isHostCurrentUtilized(host) || host.getUtilizationOfCpu() == 0){
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
            if(isHostCurrentUtilized(host) || host.getUtilizationOfCpu() == 0){
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
            double utilization = loadMovingAvg((PowerHostUtilizationHistory) host, 3);
//            double utilization = host.getUtilizationOfCpu();
            if(host.getUtilizationOfCpu() > 0 && utilization < aliq &&  !areAllVmsMigratingOutOrAnyVmMigratingIn(host)){
//                minValue = utilization;
//                underUtilizedHost = host;
                return host;
            }
        }
        return underUtilizedHost;
    }

    @Override
    public List<Map<String, Object>> optimizeAllocation(List<? extends Vm> vmList) {
        ExecutionTimeMeasurer.start("optimizeAllocationTotal");

        ExecutionTimeMeasurer.start("optimizeAllocationHostSelection");
        List<PowerHostUtilizationHistory> overUtilizedHosts = getOverUtilizedHosts();
        getExecutionTimeHistoryHostSelection().add(
                ExecutionTimeMeasurer.end("optimizeAllocationHostSelection"));

        printOverUtilizedHosts(overUtilizedHosts);

        saveAllocation();

        ExecutionTimeMeasurer.start("optimizeAllocationVmSelection");
        List<? extends Vm> vmsToMigrate = getVmsToMigrateFromHosts(overUtilizedHosts);
        getExecutionTimeHistoryVmSelection().add(ExecutionTimeMeasurer.end("optimizeAllocationVmSelection"));

        Log.printLine("Reallocation of VMs from the over-utilized hosts:");
        ExecutionTimeMeasurer.start("optimizeAllocationVmReallocation");
        List<Map<String, Object>> migrationMap = new LinkedList<>();

        getExecutionTimeHistoryVmReallocation().add(
                ExecutionTimeMeasurer.end("optimizeAllocationVmReallocation"));
        Log.printLine();

        migrationMap.addAll(getMigrationMapFromUnderUtilizedHosts(overUtilizedHosts, vmsToMigrate));

        restoreAllocation();

        getExecutionTimeHistoryTotal().add(ExecutionTimeMeasurer.end("optimizeAllocationTotal"));
        return migrationMap;
    }

    protected List<Map<String, Object>> getMigrationMapFromUnderUtilizedHosts(
            List<PowerHostUtilizationHistory> overUtilizedHosts, List<? extends Vm> vmsOverloadToMigrate) {
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
        List<Vm> vmsToMigrateFromUnderUtilizedHost = new LinkedList<>();

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

            List<? extends Vm> vms = getVmsToMigrateFromUnderUtilizedHost(underUtilizedHost);

            if (vms.isEmpty()) {
                continue;
            }

            for(Vm vm: vms){
                vmsToMigrateFromUnderUtilizedHost.add(vm);
            }

        }
        List<Vm> vms = new LinkedList<>();
        vms.addAll(vmsOverloadToMigrate);
        for(Vm vm: vmsToMigrateFromUnderUtilizedHost){
            vms.add(vm);
        }
        migrationMap = getNewVmPlacement(vms, new HashSet<>(overUtilizedHosts));
//        underloadVMsPlacement(migrationMap, excludedHostsForFindingUnderUtilizedHost, excludedHostsForFindingNewVmPlacement, vmsToMigrateFromUnderUtilizedHost);
        return migrationMap;
    }

    private void underloadVMsPlacement(List<Map<String, Object>> migrationMap, Set<PowerHost> excludedHostsForFindingUnderUtilizedHost, Set<PowerHost> excludedHostsForFindingNewVmPlacement, List<? extends Vm> vmsToMigrateFromUnderUtilizedHost) {
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

    @Override
    protected List<? extends Vm> getVmsToMigrateFromUnderUtilizedHost(PowerHost host) {
        List<Vm> vmsToMigrate = new LinkedList<Vm>();
        for (Vm vm : host.getVmList()) {
            if (!vm.isInMigration()) {
                vmsToMigrate.add(vm);
            }
        }
        for (Vm vm : vmsToMigrate) {
            host.vmDestroy(vm);
        }
        return vmsToMigrate;
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
                if (!isHostCurrentUtilized(host)) {
                    break;
                }
            }
        }
        return vmsToMigrate;
    }


}
