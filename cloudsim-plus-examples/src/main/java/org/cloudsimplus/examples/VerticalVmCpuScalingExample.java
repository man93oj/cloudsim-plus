/*
 * CloudSim Plus: A modern, highly-extensible and easier-to-use Framework for
 * Modeling and Simulation of Cloud Computing Infrastructures and Services.
 * http://cloudsimplus.org
 *
 *     Copyright (C) 2015-2016  Universidade da Beira Interior (UBI, Portugal) and
 *     the Instituto Federal de Educação Ciência e Tecnologia do Tocantins (IFTO, Brazil).
 *
 *     This file is part of CloudSim Plus.
 *
 *     CloudSim Plus is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     CloudSim Plus is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with CloudSim Plus. If not, see <http://www.gnu.org/licenses/>.
 */
package org.cloudsimplus.examples;

import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.Simulation;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.datacenters.DatacenterCharacteristics;
import org.cloudbus.cloudsim.datacenters.DatacenterCharacteristicsSimple;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.ResourceProvisionerSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.resources.Processor;
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.util.Log;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModel;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelFull;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;
import org.cloudsimplus.autoscaling.HorizontalVmScaling;
import org.cloudsimplus.autoscaling.VerticalVmScaling;
import org.cloudsimplus.autoscaling.VerticalVmScalingSimple;
import org.cloudsimplus.autoscaling.resources.ResourceScalingInstantaneous;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.listeners.EventInfo;
import org.cloudsimplus.listeners.EventListener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static java.util.Comparator.comparingDouble;

/**
 * An example that scale VM PEs up, according to the arrival of Cloudlets.
 * A {@link VerticalVmScaling}
 * is set to each {@link #createListOfScalableVms(int) initially created VM},
 * that will check at {@link #SCHEDULING_INTERVAL specific time intervals}
 * if a VM PEs {@link #upperCpuUtilizationThreshold(Vm) are overloaded or not} to then
 * request the PEs to be scaled up.
 *
 * <p>The example uses the CloudSim Plus {@link EventListener} feature
 * to enable monitoring the simulation and dynamically creating objects such as Cloudlets and VMs at runtime.
 * It relies on
 * <a href="http://www.oracle.com/webfolder/technetwork/tutorials/obe/java/Lambda-QuickStart/index.html">Java 8 Lambda Expressions</a>
 * to create a Listener for the {@link Simulation#addOnClockTickListener(EventListener) onClockTick event}
 * to get notifications when the simulation clock advances, then creating and submitting new cloudlets.
 * </p>
 *
 * @author Manoel Campos da Silva Filho
 * @since CloudSim Plus 1.2.0
 */
public class VerticalVmCpuScalingExample {
    /**
     * The interval in which the Datacenter will schedule events.
     * As lower is this interval, sooner the processing of VMs and Cloudlets
     * is updated and you will get more notifications about the simulation execution.
     * However, as higher is this value, it can affect the simulation performance.
     *
     * <p>For this example, a large schedule interval such as 15 will make that just
     * at every 15 seconds the processing of VMs is updated. If a VM is overloaded, just
     * after this time the creation of a new one will be requested
     * by the VM's {@link HorizontalVmScaling Horizontal Scaling} mechanism.</p>
     *
     * <p>If this interval is defined using a small value, you may get
     * more dynamically created VMs than expected. Accordingly, this value
     * has to be trade-off.
     * For more details, see {@link Datacenter#getSchedulingInterval()}.</p>
    */
    private static final int SCHEDULING_INTERVAL = 1;
    private static final int HOSTS = 1;

    private static final int HOST_PES = 32;
    private static final int VMS = 1;
    private static final int VM_PES = 14;
    private static final int VM_RAM = 1200;
    private final CloudSim simulation;
    private DatacenterBroker broker0;
    private List<Host> hostList;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;

    private static final int CLOUDLETS = 10;
    private static final int CLOUDLETS_INITIAL_LENGTH = 20_000;

    private int createdCloudlets;
    private int createsVms;

    public static void main(String[] args) {
        new VerticalVmCpuScalingExample();
    }

    /**
     * Default constructor that builds the simulation scenario and starts the simulation.
     */
    private VerticalVmCpuScalingExample() {
        /*You can remove the seed to get a dynamic one, based on current computer time.
        * With a dynamic seed you will get different results at each simulation run.*/
        final long seed = 1;
        hostList = new ArrayList<>(HOSTS);
        vmList = new ArrayList<>(VMS);
        cloudletList = new ArrayList<>(CLOUDLETS);

        simulation = new CloudSim();
        simulation.addOnClockTickListener(this::onClockTickListener);

        createDatacenter();
        broker0 = new DatacenterBrokerSimple(simulation);

        vmList.addAll(createListOfScalableVms(VMS));

        createCloudletListsWithDifferentDelays();
        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);

        simulation.start();

        printSimulationResults();
    }

    private void onClockTickListener(EventInfo eventInfo) {
        vmList.forEach(vm -> {
            Log.printFormatted("\t\tTime %6.1f: Vm %d CPU Usage: %6.2f%% (%2d vCPUs. Running Cloudlets: #%d)\n",
                eventInfo.getTime(), vm.getId(), vm.getCurrentCpuPercentUse()*100.0,
                vm.getNumberOfPes(), vm.getCloudletScheduler().getCloudletExecList().size());
        });
    }

    private void printSimulationResults() {
        List<Cloudlet> finishedCloudlets = broker0.getCloudletsFinishedList();
        Comparator<Cloudlet> sortByVmId = comparingDouble(c -> c.getVm().getId());
        Comparator<Cloudlet> sortByStartTime = comparingDouble(c -> c.getExecStartTime());
        finishedCloudlets.sort(sortByVmId.thenComparing(sortByStartTime));

        new CloudletsTableBuilder(finishedCloudlets).build();
    }

    /**
     * Creates a Datacenter and its Hosts.
     */
    private void createDatacenter() {
        for (int i = 0; i < HOSTS; i++) {
            hostList.add(createHost());
        }

        DatacenterCharacteristics characteristics = new DatacenterCharacteristicsSimple(hostList);
        Datacenter dc0 = new DatacenterSimple(simulation, characteristics, new VmAllocationPolicySimple());
        dc0.setSchedulingInterval(SCHEDULING_INTERVAL);
    }

    private Host createHost() {
        List<Pe> peList = new ArrayList<>(HOST_PES);
        for (int i = 0; i < HOST_PES; i++) {
            peList.add(new PeSimple(1000, new PeProvisionerSimple()));
        }

        final long ram = 20000; //in Megabytes
        final long bw = 100000; //in Megabytes
        final long storage = 10000000; //in Megabites/s
        final int id = hostList.size();
        return new HostSimple(ram, bw, storage, peList)
            .setRamProvisioner(new ResourceProvisionerSimple())
            .setBwProvisioner(new ResourceProvisionerSimple())
            .setVmScheduler(new VmSchedulerTimeShared());
    }

    /**
     * Creates a list of initial VMs in which each VM is able to scale horizontally
     * when it is overloaded.
     *
     * @param numberOfVms number of VMs to create
     * @return the list of scalable VMs
     * @see #createVerticalPeScalingForVm(Vm)
     */
    private List<Vm> createListOfScalableVms(final int numberOfVms) {
        List<Vm> newList = new ArrayList<>(numberOfVms);
        for (int i = 0; i < numberOfVms; i++) {
            Vm vm = createVm();
            createVerticalPeScalingForVm(vm);
            newList.add(vm);
        }

        return newList;
    }

    /**
     * Creates a Vm object.
     *
     * @return the created Vm
     */
    private Vm createVm() {
        final int id = createsVms++;

        return new VmSimple(id, 1000, VM_PES)
            .setRam(VM_RAM).setBw(1000).setSize(10000).setBroker(broker0)
            .setCloudletScheduler(new CloudletSchedulerTimeShared());
    }

    /**
     * Creates a {@link VerticalVmScaling} for the CPU of a given VM.
     *
     * @param vm the VM in which the VerticalVmScaling will be created
     * @see #createListOfScalableVms(int)
     */
    private void createVerticalPeScalingForVm(Vm vm) {
        //The percentage in which the number of PEs has to be scaled
        final double scalingFactor = 0.1;
        VerticalVmScaling verticalCpuScaling = new VerticalVmScalingSimple(Processor.class, scalingFactor);
        /* By uncommenting the line below, you will see that instead of gradually
         * increasing or decreasing the number of PEs, when the scaling object detect
         * the CPU usage is above or below the defined thresholds,
         * it will automatically calculate the number of PEs to add/remove to
         * move the VM from the over or underload condition.
        */
        //verticalCpuScaling.setResourceScaling(new ResourceScalingInstantaneous());
        /*Implementations of a ResourceScaling can also be defined using a Lambda Expression as below.
        * It is just an example the scale the resource twice the amount defined by the scaling factor.*/
        verticalCpuScaling.setResourceScaling(s -> (long)(s.getScalingFactor()*2*s.getVmResourceToScale().getAllocatedResource()));

        verticalCpuScaling.setLowerThresholdFunction(this::lowerCpuUtilizationThreshold);
        verticalCpuScaling.setUpperThresholdFunction(this::upperCpuUtilizationThreshold);
        vm.setPeVerticalScaling(verticalCpuScaling);
    }

    /**
     * Defines the minimum CPU utilization percentage that defines a Vm as underloaded.
     * This function is using a statically defined threshold, but it would be defined
     * a dynamic threshold based on any condition you want.
     * A reference to this method is assigned to each Vertical VM Scaling created.
     *
     * @param vm the VM to check if its CPU is underloaded.
     *        The parameter is not being used internally, that means the same
     *        threshold is used for any Vm.
     * @return the lower CPU utilization threshold
     * @see #createVerticalPeScalingForVm(Vm)
     */
    private double lowerCpuUtilizationThreshold(Vm vm) {
        return 0.4;
    }

    /**
     * Defines the maximum CPU utilization percentage that defines a Vm as overloaded.
     * This function is using a statically defined threshold, but it would be defined
     * a dynamic threshold based on any condition you want.
     * A reference to this method is assigned to each Vertical VM Scaling created.
     *
     * @param vm the VM to check if its CPU is overloaded.
     *        The parameter is not being used internally, that means the same
     *        threshold is used for any Vm.
     * @return the upper CPU utilization threshold
     * @see #createVerticalPeScalingForVm(Vm)
     */
    private double upperCpuUtilizationThreshold(Vm vm) {
        return 0.8;
    }

    /**
     * Creates lists of Cloudlets to be submitted to the broker with different delays,
     * simulating their arrivals at different times.
     * Adds all created Cloudlets to the {@link #cloudletList}.
     */
    private void createCloudletListsWithDifferentDelays() {
        final int initialCloudletsNumber = (int)(CLOUDLETS/2.5);
        final int remainingCloudletsNumber = CLOUDLETS-initialCloudletsNumber;
        //Creates a List of Cloudlets that will start running immediately when the simulation starts
        for (int i = 0; i < initialCloudletsNumber; i++) {
            cloudletList.add(createCloudlet(CLOUDLETS_INITIAL_LENGTH+(i*1000), 2));
        }

        /* Create several Cloudlets, increasing the arrival delay and decreasing
        * the length of each one.
        * The increasing delay enables CPU usage to increase gradually along the arrival of
        * new Cloudlets (triggering CPU up scaling at some point in time).
        * The decreasing length enables to finish in different times,
        * to gradually reduce CPU usage (triggering CPU down scaling at some point in time).
        * Check the logs to understand how the scaling is working.*/
        for (int i = 1; i <= remainingCloudletsNumber; i++) {
            cloudletList.add(createCloudlet(CLOUDLETS_INITIAL_LENGTH*2/i, 1,i*2));
        }
    }

    /**
     * Creates a single Cloudlet.
     *
     * @param length the length of the cloudlet to create.
     * @param numberOfPes the number of PEs the Cloudlets requires.
     * @param delay the delay that defines the arrival time of the Cloudlet at the Cloud infrastructure.
     * @return the created Cloudlet
     */
    private Cloudlet createCloudlet(long length, int numberOfPes, double delay) {
        final int id = createdCloudlets++;
        //randomly selects a length for the cloudlet
        UtilizationModel utilizationFull = new UtilizationModelFull();
        Cloudlet cl = new CloudletSimple(id, length, numberOfPes);
        cl.setFileSize(1024)
          .setOutputSize(1024)
          .setUtilizationModel(utilizationFull)
          .setBroker(broker0)
          .setSubmissionDelay(delay);
        return cl;
    }

    /**
     * Creates a single Cloudlet with no delay, which means the Cloudlet arrival time will
     * be zero (exactly when the simulation starts).
     *
     * @param length the Cloudlet length
     * @param numberOfPes the number of PEs the Cloudlets requires
     * @return the created Cloudlet
     */
    private Cloudlet createCloudlet(long length, int numberOfPes) {
        return createCloudlet(length, numberOfPes, 0);
    }

    /**
     * Increments the RAM resource utilization, that is defined in absolute values,
     * in 10MB every second.
     *
     * @param um the Utilization Model that called this function
     * @return the new resource utilization after the increment
     */
    private double utilizationIncrement(UtilizationModelDynamic um) {
        return um.getUtilization() + um.getTimeSpan()*10;
    }
}