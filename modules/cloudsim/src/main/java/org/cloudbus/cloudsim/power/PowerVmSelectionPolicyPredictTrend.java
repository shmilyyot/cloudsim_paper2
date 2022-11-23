package org.cloudbus.cloudsim.power;

import org.cloudbus.cloudsim.Vm;

import java.util.List;

// 根据可能上升的mips请求排序
public class PowerVmSelectionPolicyPredictTrend extends PowerVmSelectionPolicy {
    @Override
    public Vm getVmToMigrate(PowerHost host) {
        List<PowerVm> migratableVms = getMigratableVms(host);
        if(migratableVms.isEmpty()){
            return null;
        }
        migratableVms.sort((a, b)-> Double.compare((b.getTrend() + b.getLastTrend()) * b.getMips(), (a.getTrend() + a.getLastTrend()) * a.getMips()));
        return migratableVms.get(0);
    }
}
