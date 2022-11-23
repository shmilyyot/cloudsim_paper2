package org.cloudbus.cloudsim.power;

import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;

import java.util.List;

public class PowerVmSelectionPolicyPEACR extends PowerVmSelectionPolicy {
    @Override
    public Vm getVmToMigrate(PowerHost host) {
        List<PowerVm> migratableVms = getMigratableVms(host);
        if(migratableVms.isEmpty()){
            return null;
        }
        double cpuOver = (host.getUtilizationOfCpu() - host.getPpr()) * ((double)host.getTotalMips() / 1000);
        double minMetric = Double.MAX_VALUE;
        Vm vmToMigrate = null;
        for (Vm vm : migratableVms) {
            if (vm.isInMigration()) {
                continue;
            }
            double metric = Math.abs(cpuOver - vm.getMips() * vm.getTotalUtilizationOfCpuMips(CloudSim.clock()) / 1000) * host.getRam() / host.getBw() ;
            if (metric < minMetric) {
                minMetric = metric;
                vmToMigrate = vm;
            }
        }
        return vmToMigrate;
    }
}
