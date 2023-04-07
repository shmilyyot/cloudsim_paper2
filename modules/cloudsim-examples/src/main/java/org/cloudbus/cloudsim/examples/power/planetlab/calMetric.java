package org.cloudbus.cloudsim.examples.power.planetlab;

import org.cloudbus.cloudsim.util.MathUtil;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class calMetric {
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        for (int id = 0; id < 800; ++id){
            ObjectInputStream input = new ObjectInputStream(Files.newInputStream(Paths.get("D:\\java_workspace\\cloudsim-cloudsim-4.0\\modules\\cloudsim-examples\\target\\classes\\workload\\planetlab\\20110409\\predict\\vm_"+id+".obj")));
            double[] data = (double[]) input.readObject();
            List<double[]> dataList = Arrays.asList(data);
            System.out.println(Arrays.toString(data));
        }
    }
}
