package org.cloudbus.cloudsim.power;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.util.ExecutionTimeMeasurer;
import org.cloudbus.cloudsim.util.MathUtil;

import java.util.*;

public class PowerVmAllocationPolicyPredictTrendTHR extends PowerVmAllocationPolicyMigrationAbstract{
    /**
     * Instantiates a new PowerVmAllocationPolicyMigrationAbstract.
     *
     * @param hostList          the host list
     * @param vmSelectionPolicy the vm selection policy
     */

    private double utilizationThreshold = 0.8;

    protected void setUtilizationThreshold(double utilizationThreshold) {
        this.utilizationThreshold = utilizationThreshold;
    }

    protected double getUtilizationThreshold() {
        return utilizationThreshold;
    }

    static String INCREASE = "increase";
    static String DECREASE = "decrease";
    static String STABLE = "stable";
    static String SHUTDOWN = "shutdown";

    public PowerVmAllocationPolicyPredictTrendTHR(
            List<? extends Host> hostList,
            PowerVmSelectionPolicy vmSelectionPolicy,
            double utilizationThreshold) {
        super(hostList, vmSelectionPolicy);
        setUtilizationThreshold(utilizationThreshold);
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
        List<PowerVm> vmsToMigrate = getVmsToMigrateFromHosts(overUtilizedHosts);
        getExecutionTimeHistoryVmSelection().add(ExecutionTimeMeasurer.end("optimizeAllocationVmSelection"));

        Log.printLine("Reallocation of VMs from the over-utilized hosts:");
        ExecutionTimeMeasurer.start("optimizeAllocationVmReallocation");
        List<Map<String, Object>> migrationMap = getNewVmPlacement(vmsToMigrate, new HashSet<Host>(
                overUtilizedHosts));
        getExecutionTimeHistoryVmReallocation().add(
                ExecutionTimeMeasurer.end("optimizeAllocationVmReallocation"));
        Log.printLine();

        migrationMap.addAll(getMigrationMapFromUnderUtilizedHosts(overUtilizedHosts));

        restoreAllocation();

        getExecutionTimeHistoryTotal().add(ExecutionTimeMeasurer.end("optimizeAllocationTotal"));

        return migrationMap;
    }

    @Override
    protected boolean isHostOverUtilized(PowerHost host) {
        double upperThreshold = getUtilizationThreshold();
        addHistoryEntry(host, upperThreshold);
//        return isHostCurrentOverUtilized(host);
        return isHostCurrentOverUtilized(host) && isHostFutureOverUtilized(host) && isHostBeforeOverUtilized(host);
    }

    protected boolean isHostCurrentOverUtilized(PowerHost host) {
        PowerHostUtilizationHistory _host = (PowerHostUtilizationHistory) host;
        double upperThreshold = getUtilizationThreshold();
        double totalRequestedMips = 0;
        for (Vm vm : host.getVmList()) {
            totalRequestedMips += vm.getCurrentRequestedTotalMips();
        }
        double utilization = totalRequestedMips / host.getTotalMips();
        return utilization > upperThreshold;
    }

    protected boolean isHostFutureOverUtilized(PowerHost host) {
        PowerHostUtilizationHistory _host = (PowerHostUtilizationHistory) host;
        double upperThreshold = getUtilizationThreshold();
        return _host.getPredictUtilization() > upperThreshold;
    }

    //周期很重要，决定各项指标取舍的最关键因素
    protected boolean isHostBeforeOverUtilized(PowerHost host) {
        PowerHostUtilizationHistory _host = (PowerHostUtilizationHistory) host;
        double upperThreshold = getUtilizationThreshold();
        double[] utilizationHistory = _host.getUtilizationHistory();
//        isOverloadLastFiveTimes(utilizationHistory, upperThreshold);
        int maxIndex = 0;
        double maxValue = utilizationHistory[0];
        double totalValue = utilizationHistory[0];
        for(int i = 1; i < utilizationHistory.length; ++i){
            totalValue += utilizationHistory[i];
            if(totalValue / (i + 1) > maxValue){
                maxValue = totalValue / (i + 1);
                maxIndex = i;
            }
        }
        for(int i = 0; i <= maxIndex; ++i){
//        for(int i = 0; i < Math.min(utilizationHistory.length, 8); ++i){
//        for(int i = 0; i < utilizationHistory.length; ++i){
            if(utilizationHistory[i] > upperThreshold){
                return true;
            }
        }
        return false;
    }

    protected void isOverloadLastFiveTimes(double[] utilizationHistory, double upperThreshold){
        for(int i = 0; i < utilizationHistory.length; ++i){
            if(utilizationHistory[i] > 0.75){
                if(i <= 5){
                    for(int j = 0; j <= i; ++j){
                        if(utilizationHistory[i] > upperThreshold){
                            return;
                        }
                    }
                    for(int j = i+1; j < utilizationHistory.length; ++j){
                        if(utilizationHistory[j] > 0.75){
                            return;
                        }
                    }
                    if(utilizationHistory.length == 30){
                        System.out.println(Arrays.toString(utilizationHistory));
                    }
                }
                return;
            }
        }
    }

    protected boolean isHostOverUtilizedAfterAllocation(PowerHost host, Vm vm) {
        boolean isHostOverUtilizedAfterAllocation = true;
        if (host.vmCreate(vm)) {
            isHostOverUtilizedAfterAllocation = (isHostCurrentOverUtilized(host) || isHostFutureOverUtilized(host) || isHostBeforeOverUtilized(host));
            host.vmDestroy(vm);
        }
        return isHostOverUtilizedAfterAllocation;
    }

    protected List<PowerHostUtilizationHistory> getDecreaseTrendHosts() {
        List<PowerHostUtilizationHistory> decreaseTrendHosts = new LinkedList<PowerHostUtilizationHistory>();
        for (PowerHostUtilizationHistory host : this.<PowerHostUtilizationHistory> getHostList()) {
            if (isHostDecreaseTrend(host)) {
                decreaseTrendHosts.add(host);
            }
        }
        return decreaseTrendHosts;
    }

    protected List<PowerHostUtilizationHistory> getIncreaseTrendHosts() {
        List<PowerHostUtilizationHistory> increaseTrendHosts = new LinkedList<PowerHostUtilizationHistory>();
        for (PowerHostUtilizationHistory host : this.<PowerHostUtilizationHistory> getHostList()) {
            if (isHostIncreaseTrend(host)) {
                increaseTrendHosts.add(host);
            }
        }
        return increaseTrendHosts;
    }

    // 趋势是小于等于0，都可以视为下降的主机，然后可以吸纳虚拟机
    protected boolean isHostDecreaseTrend(PowerHostUtilizationHistory host) {
        double trend = host.getTrend();
        return trend < 0;
    }

    protected boolean isHostIncreaseTrend(PowerHostUtilizationHistory host) {
        double trend = host.getTrend();
        return trend > 0;
    }

    protected Map<String, List<PowerHostUtilizationHistory>> getDifferentHosts(){
        Map<String, List<PowerHostUtilizationHistory>> hostsMap = new HashMap<>();
        hostsMap.put(SHUTDOWN, new ArrayList<>());
        hostsMap.put(INCREASE, new ArrayList<>());
        hostsMap.put(DECREASE, new ArrayList<>());
        hostsMap.put(STABLE, new ArrayList<>());
        for (PowerHostUtilizationHistory host : this.<PowerHostUtilizationHistory> getHostList()) {
            if(getUtilizationOfCpuMips(host) == 0)
            {
                host.setHostTrendType(SHUTDOWN);
                List<PowerHostUtilizationHistory> hostList = hostsMap.get(SHUTDOWN);
                hostList.add(host);
                hostsMap.put(SHUTDOWN, hostList);
                continue;
            }
            double trend = host.getTrend() + host.getLastTrend();
            if(trend > 0){
                host.setHostTrendType(INCREASE);
                List<PowerHostUtilizationHistory> hostList = hostsMap.get(INCREASE);
                hostList.add(host);
                hostsMap.put(INCREASE, hostList);
            }else if(trend < 0){
                host.setHostTrendType(DECREASE);
                List<PowerHostUtilizationHistory> hostList = hostsMap.get(DECREASE);
                hostList.add(host);
                hostsMap.put(DECREASE, hostList);
            }else{
                host.setHostTrendType(STABLE);
                List<PowerHostUtilizationHistory> hostList = hostsMap.get(STABLE);
                hostList.add(host);
                hostsMap.put(STABLE, hostList);
            }
        }
        return hostsMap;
    }

    protected Map<String, List<PowerVm>> getDifferentVms(List<? extends Vm> vmsToMigrate){
        Map<String, List<PowerVm>> vmsMap = new HashMap<>();
        vmsMap.put(SHUTDOWN, new ArrayList<>());
        vmsMap.put(INCREASE, new ArrayList<>());
        vmsMap.put(DECREASE, new ArrayList<>());
        vmsMap.put(STABLE, new ArrayList<>());
        for (Vm vm : vmsToMigrate) {
            PowerVm powerVm = (PowerVm) vm;
            if(vm.getTotalUtilizationOfCpuMips(CloudSim.clock()) == 0)
            {
                powerVm.setVmTrendType(SHUTDOWN);
                List<PowerVm> vmsList = vmsMap.get(SHUTDOWN);
                vmsList.add(powerVm);
                vmsMap.put(SHUTDOWN, vmsList);
                continue;
            }
            double trend = powerVm.getTrend();
            if(trend > 0){
                powerVm.setVmTrendType(INCREASE);
                List<PowerVm> vmsList = vmsMap.get(INCREASE);
                vmsList.add(powerVm);
                vmsMap.put(INCREASE, vmsList);
            }else if(trend < 0){
                powerVm.setVmTrendType(DECREASE);
                List<PowerVm> vmsList = vmsMap.get(DECREASE);
                vmsList.add(powerVm);
                vmsMap.put(DECREASE, vmsList);
            }else{
                powerVm.setVmTrendType(STABLE);
                List<PowerVm> vmsList = vmsMap.get(STABLE);
                vmsList.add(powerVm);
                vmsMap.put(STABLE, vmsList);
            }
        }
        return vmsMap;
    }

    protected void updateVmType(List<? extends Vm> vmsToMigrate){
        for (Vm vm : vmsToMigrate) {
            PowerVm powerVm = (PowerVm) vm;
            if(vm.getTotalUtilizationOfCpuMips(CloudSim.clock()) == 0)
            {
                powerVm.setVmTrendType(SHUTDOWN);
                continue;
            }
            double trend = powerVm.getTrend() + powerVm.getLastTrend();
            if(trend > 0){
                powerVm.setVmTrendType(INCREASE);
            }else if(trend < 0){
                powerVm.setVmTrendType(DECREASE);
            }else{
                powerVm.setVmTrendType(STABLE);
            }
        }
    }

    @Override
    protected List<Map<String, Object>> getNewVmPlacement(
            List<? extends Vm> vmsToMigrate,
            Set<? extends Host> excludedHosts) {
        List<Map<String, Object>> migrationMap = new LinkedList<Map<String, Object>>();
        Map<String, List<PowerHostUtilizationHistory>> hostsMap = getDifferentHosts();
        updateVmType(vmsToMigrate);
        if(vmsToMigrate.size() > 1){
            vmsToMigrate.sort((a, b)->{
                Double aTrend = ((PowerVm)a).getTrend() * ((PowerVm) a).getMips();
                Double bTrend = ((PowerVm)b).getTrend() * ((PowerVm) b).getMips();
                return bTrend.compareTo(aTrend);
            });
        }
        for (Vm vm : vmsToMigrate) {
            PowerVm powerVm = (PowerVm) vm;
//            findHostAndUpdateMigration(powerVm, excludedHosts, hostsMap, migrationMap, DECREASE, STABLE, INCREASE, SHUTDOWN);
            if(powerVm.getVmTrendType().equals(DECREASE) || powerVm.getVmTrendType().equals(STABLE)){
                findHostAndUpdateMigration(powerVm, excludedHosts, hostsMap, migrationMap, INCREASE, STABLE, DECREASE, SHUTDOWN);
            }else{
                findHostAndUpdateMigration(powerVm, excludedHosts, hostsMap, migrationMap, DECREASE, STABLE, INCREASE, SHUTDOWN);
            }
        }
        return migrationMap;
    }

    public void findHostAndUpdateMigration(PowerVm vm, Set<? extends Host> excludedHosts,
                                           Map<String, List<PowerHostUtilizationHistory>> hostsMap,
                                           List<Map<String, Object>> migrationMap,
                                           String type1, String type2, String type3, String type4){
        PowerHostUtilizationHistory allocatedHost = null;
        if(allocatedHost == null){
            allocatedHost = findHostForVm(vm, excludedHosts, type1, hostsMap);
        }
        if(allocatedHost == null){
            allocatedHost = findHostForVm(vm, excludedHosts, type2, hostsMap);
        }
        if(allocatedHost == null){
            allocatedHost = findHostForVm(vm, excludedHosts, type3, hostsMap);
        }
        if(allocatedHost == null){
            allocatedHost = findHostForVm(vm, excludedHosts, type4, hostsMap);
        }
        if(allocatedHost != null){
            migrateHostAndUpdate(allocatedHost, vm, hostsMap, migrationMap);
        }
    }

    protected void migrateHostAndUpdate(PowerHostUtilizationHistory allocatedHost, Vm vm, Map<String, List<PowerHostUtilizationHistory>> hostsMap, List<Map<String, Object>> migrationMap){
        allocatedHost.vmCreate(vm);
        double trend = allocatedHost.getTrend();
        if(trend > 0){
            String hostOriginTrendType = allocatedHost.getHostTrendType();
            List<PowerHostUtilizationHistory> hostList = hostsMap.get(hostOriginTrendType);
            hostList.remove(allocatedHost);
            hostsMap.put(hostOriginTrendType, hostList);
            hostList = hostsMap.get(INCREASE);
            hostList.add(allocatedHost);
            hostsMap.put(INCREASE, hostList);
            allocatedHost.setHostTrendType(INCREASE);
        }else if(trend < 0){
            String hostOriginTrendType = allocatedHost.getHostTrendType();
            List<PowerHostUtilizationHistory> hostList = hostsMap.get(hostOriginTrendType);
            hostList.remove(allocatedHost);
            hostsMap.put(hostOriginTrendType, hostList);
            hostList = hostsMap.get(DECREASE);
            hostList.add(allocatedHost);
            hostsMap.put(DECREASE, hostList);
            allocatedHost.setHostTrendType(DECREASE);
        }else {
            String hostOriginTrendType = allocatedHost.getHostTrendType();
            List<PowerHostUtilizationHistory> hostList = hostsMap.get(hostOriginTrendType);
            hostList.remove(allocatedHost);
            hostsMap.put(hostOriginTrendType, hostList);
            hostList = hostsMap.get(STABLE);
            hostList.add(allocatedHost);
            hostsMap.put(STABLE, hostList);
            allocatedHost.setHostTrendType(STABLE);
        }
        Log.printConcatLine("VM #", vm.getId(), " allocated to host #", allocatedHost.getId());
        Map<String, Object> migrate = new HashMap<String, Object>();
        migrate.put("vm", vm);
        migrate.put("host", allocatedHost);
        migrationMap.add(migrate);
    }

    @Override
    protected List<PowerVm>
    getVmsToMigrateFromHosts(List<PowerHostUtilizationHistory> overUtilizedHosts) {
        List<PowerVm> vmsToMigrate = new LinkedList<>();
        for (PowerHostUtilizationHistory host : overUtilizedHosts) {
            while (true) {
                PowerVm vm = (PowerVm) getVmSelectionPolicy().getVmToMigrate(host);
                if (vm == null) {
                    break;
                }
                vmsToMigrate.add(vm);
                host.vmDestroy(vm);
                if (!isHostOverUtilized(host)) {
                    break;
                }
            }
        }
        return vmsToMigrate;
    }

    public PowerHostUtilizationHistory findHostForVm(Vm vm, Set<? extends Host> excludedHosts, String arg, Map<String, List<PowerHostUtilizationHistory>> hostsMap) {
        List<PowerHostUtilizationHistory> hostList = hostsMap.getOrDefault(arg, new ArrayList<>());
        if(arg.equals(INCREASE) && hostList.size() > 1){
            hostList.sort((a, b)->{
                Double aTrend = a.getTrend() * a.getTotalMips();
                Double bTrend = b.getTrend() * b.getTotalMips();
                return aTrend.compareTo(bTrend);
            });
        }else if(arg.equals(DECREASE) && hostList.size() > 1){
            hostList.sort((a, b)->{
                Double aTrend = a.getTrend() * a.getTotalMips();
                Double bTrend = b.getTrend() * b.getTotalMips();
                return bTrend.compareTo(aTrend);
            });
        }
        hostList = getHostList();
        PowerHostUtilizationHistory powerHost = null;
//        double bestValue = Double.MIN_VALUE;
        double bestValue = Double.MAX_VALUE;
        double minPower = Double.MAX_VALUE;
        PowerVm powerVm = (PowerVm) vm;
//        if(CloudSim.clock() < 8699){
//            for (PowerHost host : this.<PowerHost> getHostList()) {
//                if (excludedHosts.contains(host)) {
//                    continue;
//                }
//                if (host.isSuitableForVm(vm)) {
//                    if (getUtilizationOfCpuMips(host) != 0 && isHostOverUtilizedAfterAllocation(host, vm)) {
//                        continue;
//                    }
//
//                    try {
//                        double powerAfterAllocation = getPowerAfterAllocation(host, vm);
//                        if (powerAfterAllocation != -1) {
//                            double oripower = host.getPower();
//						if(host.getUtilizationOfCpu() == 0)
//						{
//							oripower = 0;
//						}
//                            double powerDiff = powerAfterAllocation - oripower;
//                            if (powerDiff < minPower) {
//                                minPower = powerDiff;
//                                powerHost = (PowerHostUtilizationHistory) host;
//                            }
//                        }
//                    } catch (Exception e) {
//                    }
//                }
//            }
//            return powerHost;
//        }
        List<PowerHost> idleHostList = buildIdleHost();
        HashSet<PowerHost> idleHostSet = new HashSet<>(idleHostList);
        for(PowerHostUtilizationHistory host : hostList){
            if (excludedHosts.contains(host) || idleHostSet.contains(host)) {
                continue;
            }
            if (host.isSuitableForVm(powerVm)) {
                if (getUtilizationOfCpuMips(host) != 0 && isHostOverUtilizedAfterAllocation(host, powerVm)) {
                    continue;
                }
//                // 1. 直接从高到低
//                return host;

//                //2.选择过去当前未来三个时刻余弦相似度
//                double[] hostLeftMips = new double[3];
//                double[] vmUsedMips = new double[3];
//                hostLeftMips[0] = host.getUtilizationHistory()[0] * host.getTotalMips();
//                hostLeftMips[1] = host.getUtilizationOfCpuMips();
//                hostLeftMips[2] = host.getPredictUtilization() * host.getTotalMips();
//                vmUsedMips[0] = powerVm.getUtilizationHistory().get(0) * vm.getMips();
//                vmUsedMips[1] = powerVm.getCurrentRequestedTotalMips();
//                vmUsedMips[2] = powerVm.getPredictUtilization(CloudSim.clock()) * vm.getMips();
//
//                double cosSine = -MathUtil.cosineSimilarity(hostLeftMips, vmUsedMips);
//                if(cosSine <= bestValue){
//                    bestValue = cosSine;
//                    powerHost = host;
//                }

//                //3. 历史趋势余弦相似度
//                double[] hostHistoryTrend = MathUtil.arrayToNegative(host.getHistoryTrend());
//                double[] vmHistoryTrend= powerVm.getHistoryTrend();
//                double cosSine = -MathUtil.cosineSimilarity(hostHistoryTrend, vmHistoryTrend);
//                if(cosSine <= bestValue){
//                    bestValue = cosSine;
//                    powerHost = host;
//                }

                //4. 历史趋势方差
//                double[] hostHistoryTrend = host.getHistoryTrend();
//                double[] vmHistoryTrend= powerVm.getHistoryTrend();
//                double weight = MathUtil.stDevTrend(hostHistoryTrend, vmHistoryTrend);
//                if(weight < bestValue){
//                    bestValue = weight;
//                    powerHost = host;
//                }

//                //5 条件判断组合
//                if(arg.equals(DECREASE)){
//                    return host;
//                }else{
//                    double[] hostHistoryTrend = host.getHistoryTrend();
//                    double[] vmHistoryTrend= powerVm.getHistoryTrend();
//                    double weight = MathUtil.stDevTrend(hostHistoryTrend, vmHistoryTrend);
//                    if(weight < bestValue){
//                        bestValue = weight;
//                        powerHost = host;
//                    }
//                }

//                //6 平均每个vm的下降趋势
//                double totalTrend = host.getTrend() * host.getTotalMips() / host.getVmList().size();
//                if (totalTrend + powerVm.getMips() * powerVm.getTrend() < bestValue){
//                    bestValue = totalTrend + powerVm.getMips() * powerVm.getTrend();
//                    powerHost = host;
//                }

//                //7 和阈值相差的剩余资源
//                double totalTrend = host.getTrend() * host.getTotalMips();
//                if (Math.abs(totalTrend + powerVm.getMips() * powerVm.getTrend()) < bestValue){
//                    bestValue = totalTrend + powerVm.getMips() * powerVm.getTrend();
//                    powerHost = host;
//                }

//                //8. 利用过去的趋势，魔改6
//                double totalTrend = (host.getTrend() + host.getLastTrend()) * host.getTotalMips() / host.getVmList().size();
//                double totalVmTrend = powerVm.getMips() * (powerVm.getTrend() + powerVm.getLastTrend());
//                if(totalTrend + totalVmTrend < bestValue){
//                    bestValue = totalTrend + totalVmTrend;
//                    powerHost = host;
//                }

                //9 过去和未来趋势的余弦相似度
                double []v1 = {host.getLastTrend() * host.getTotalMips() / host.getVmList().size(), host.getTrend() * host.getTotalMips() / host.getVmList().size()};
                double []v2 = {powerVm.getMips() * powerVm.getLastTrend(), powerVm.getMips() * powerVm.getTrend()};
                double cosSine = MathUtil.cosineSimilarity(v1, v2);
//                System.out.println(cosSine);
//                if(Double.isNaN(cosSine)){
//                    if(getUtilizationOfCpuMips(host) == 0){
//                        cosSine = -1 * host.getTotalMips();
//                    }else{
//                        cosSine = 0;
//                    }
//                }
                if(cosSine < bestValue){
                    bestValue = cosSine;
                    powerHost = host;
                }
            }
        }
        if(powerHost == null){
            for (PowerHost host: idleHostList){
                if (excludedHosts.contains(host)) {
                    continue;
                }
                if (host.isSuitableForVm(vm)) {
                    if (getUtilizationOfCpuMips(host) != 0 && isHostOverUtilizedAfterAllocation(host, vm)) {
                        continue;
                    }
                    return (PowerHostUtilizationHistory) host;
                }
            }
        }

        return powerHost;
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


}
