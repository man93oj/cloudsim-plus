/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.provisioners;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.cloudbus.cloudsim.resources.Bandwidth;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.Ram;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.resources.ResourceManageable;

/**
 * An abstract class that implements the basic features of a provisioning policy used by a host
 * to allocate a given resource to virtual machines inside it.
 *
 * @see ResourceProvisioner
 * @author Rodrigo N. Calheiros
 * @author Anton Beloglazov
 * @author Manoel Campos da Silva Filho
 * @since 3.0.4
 */
public abstract class AbstractResourceProvisioner implements ResourceProvisioner {
    /**
     * @see #getResource()
     */
    private ResourceManageable resource;

    /** @see #getResourceAllocationMap()  */
    private final Map<Vm, Long> resourceAllocationMap;

    /**
     * @see #getResourceClass()
     */
    private Class<? extends ResourceManageable> resourceClass;

    /**
     * Creates a new ResourceManageable Provisioner for which the {@link #getResource() resource}
     * must be set further.
     *
     * @post $none
     */
    protected AbstractResourceProvisioner() {
        this(ResourceManageable.NULL);
    }

    /**
     * Creates a new ResourceManageable Provisioner.
     *
     * @param resource The resource to be managed by the provisioner
     * @post $none
     */
    public AbstractResourceProvisioner(final ResourceManageable resource) {
        this.setResource(resource);
        this.resourceAllocationMap = new HashMap<>();
    }

    @Override
    public long getAllocatedResourceForVm(Vm vm) {
        return getResourceAllocationMap().getOrDefault(vm, 0L);
    }

    @Override
    public void deallocateResourceForAllVms() {
        for(Vm vm: getResourceAllocationMap().keySet()){
            deallocateResourceForVmSettingAllocationMapEntryToZero(vm);
        }
        getResourceAllocationMap().clear();
    }

    /**
     * Deallocate the resource for the given VM, without removing
     * the VM fro the allocation map. The resource usage of the VM entry on the allocation map
     * is just set to 0.
     * @param vm the VM to deallocate resource
     * @return the amount of allocated VM resource or zero if VM is not found
     */
    protected abstract long deallocateResourceForVmSettingAllocationMapEntryToZero(Vm vm);

    /**
     * Gets the resource being managed for the provisioner, such as {@link Ram}, {@link Pe}, {@link Bandwidth}, etc.
     * @return the resource managed by this provisioner
     */
    protected ResourceManageable getResource() {
        return resource;
    }

    protected final void setResource(ResourceManageable resource) {
        Objects.requireNonNull(resource);
        this.resource = resource;
        this.resourceClass = resource.getClass();
    }

    /**
     * Gets the class of the resource that this provisioner manages.
     * @return the resource class
     */
    protected Class<? extends ResourceManageable> getResourceClass() {
        return resourceClass;
    }

    /**
     * Gets the VM resource allocation map, where each key is a VM and each value
     * is the amount of resource allocated to that VM.
     * @return the resource allocation Map
     */
    protected Map<Vm, Long> getResourceAllocationMap() {
        return resourceAllocationMap;
    }

    @Override
    public long getCapacity() {
        return resource.getCapacity();
    }

    @Override
    public long getTotalAllocatedResource() {
        return resource.getAllocatedResource();
    }

    @Override
    public long getAvailableResource() {
        return resource.getAvailableResource();
    }
}