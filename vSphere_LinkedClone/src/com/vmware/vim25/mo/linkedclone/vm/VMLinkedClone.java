package com.vmware.vim25.mo.linkedclone.vm;

        import java.net.URL;
        import java.util.ArrayList;

        import com.vmware.vim25.ManagedObjectReference;
        import com.vmware.vim25.TaskInfoState;
        import com.vmware.vim25.VirtualDevice;
        import com.vmware.vim25.VirtualDeviceBackingInfo;
        import com.vmware.vim25.VirtualDisk;
        import com.vmware.vim25.VirtualDiskFlatVer1BackingInfo;
        import com.vmware.vim25.VirtualDiskFlatVer2BackingInfo;
        import com.vmware.vim25.VirtualDiskRawDiskMappingVer1BackingInfo;
        import com.vmware.vim25.VirtualDiskSparseVer1BackingInfo;
        import com.vmware.vim25.VirtualDiskSparseVer2BackingInfo;
        import com.vmware.vim25.VirtualMachineCloneSpec;
        import com.vmware.vim25.VirtualMachineRelocateDiskMoveOptions;
        import com.vmware.vim25.VirtualMachineRelocateSpec;
        import com.vmware.vim25.VirtualMachineRelocateSpecDiskLocator;
        import com.vmware.vim25.VirtualMachineSnapshotInfo;
        import com.vmware.vim25.VirtualMachineSnapshotTree;
        import com.vmware.vim25.mo.Datastore;
        import com.vmware.vim25.mo.Folder;
        import com.vmware.vim25.mo.InventoryNavigator;
        import com.vmware.vim25.mo.ServiceInstance;
        import com.vmware.vim25.mo.Task;
        import com.vmware.vim25.mo.VirtualMachine;



public class VMLinkedClone
{
    static int  CLONEMAX = 2001;
    static int  CLONEMIN = 2000;
    static int NUM = CLONEMAX - CLONEMIN;
	
    public static void main(String[] args) throws Exception
    {
        if (args.length != 3)
        {
            System.out.println("Enter the values as arguments : <SdkUrl> <username> <password>");
            System.out.println("format: https://8.8.8.8/sdk root password ");
            return;
        }

        String vmName = "Base-CentOS-Nginx";
        String snapShotName = "snap1";
        String cloneVmName = "CentOS-Nginx-Linkedclone-";

        ServiceInstance si = new ServiceInstance(new URL(args[0]), args[1], args[2], true);

        VirtualMachine vm = (VirtualMachine) new InventoryNavigator(si.getRootFolder()).searchManagedEntity("VirtualMachine", vmName);
        System.out.println("Vm name ="+vm);

        for (int i=0 ; i <= NUM ;i++) {
            createLinkedClone(vm, snapShotName, cloneVmName + "" + CLONEMIN);
            ++CLONEMIN;
            // doClone(args[0],args[1],args[2],args[3],"Linked_Clone_MongoDB_doClone_1");
        }
        si.getServerConnection().logout();
    }

    public static void createLinkedClone(VirtualMachine vm, String snapshotName, String cloneVmName) throws Exception
    {
        if(vm==null || snapshotName==null)
            return;

        ArrayList<Integer> diskKeys = getIndependenetVirtualDiskKeys(vm);

        VirtualMachineRelocateSpec rSpec = new VirtualMachineRelocateSpec();
        if(diskKeys.size() > 0)
        {
            Datastore[] dss = vm.getDatastores();

            VirtualMachineRelocateSpecDiskLocator [] diskLocator = new VirtualMachineRelocateSpecDiskLocator[diskKeys.size()];

            int count = 0;
            for(Integer key : diskKeys)
            {
                diskLocator[count] = new VirtualMachineRelocateSpecDiskLocator();
                diskLocator[count].setDatastore(dss[0].getMOR());
                diskLocator[count].setDiskMoveType(VirtualMachineRelocateDiskMoveOptions.moveAllDiskBackingsAndDisallowSharing.toString());
                diskLocator[count].setDiskId(key);
                count = count + 1;
            }
            rSpec.setDiskMoveType(VirtualMachineRelocateDiskMoveOptions.createNewChildDiskBacking.toString());
            rSpec.setDisk(diskLocator);
        }
        else
        {
            rSpec.setDiskMoveType(VirtualMachineRelocateDiskMoveOptions.createNewChildDiskBacking.toString());
        }

        VirtualMachineCloneSpec cloneSpec = new VirtualMachineCloneSpec();
        cloneSpec.setPowerOn(true);
        cloneSpec.setTemplate(false);
        cloneSpec.setLocation(rSpec);
        cloneSpec.setSnapshot(getSnapshotReference(vm, snapshotName));

        try
        {
            Folder parent = (Folder) vm.getParent();
            Task task = vm.cloneVM_Task(parent, cloneVmName, cloneSpec);

            task.waitForTask();
            if(task.getTaskInfo().getState() == TaskInfoState.error)
            {
                System.out.println("Failure: Virtual Machine cannot be cloned"+""+CLONEMIN);
            }
            if(task.getTaskInfo().getState() == TaskInfoState.success)
            {
                System.out.println("Virtual Machine Cloned  successfully." +""+CLONEMIN);
            }
        }
        catch(Exception e)
        {
            System.out.println("Exception while cloning: " + e);
        }
    }

    public static void doClone(String url, String username, String password, String vmname, String clonename) throws Exception
    {
        ServiceInstance si = new ServiceInstance(new URL(url), username, password, true);

        Folder rootFolder = si.getRootFolder();
        VirtualMachine vm = (VirtualMachine) new InventoryNavigator(rootFolder).searchManagedEntity("VirtualMachine", vmname);

        if(vm==null)
        {
            System.out.println("VM " + vmname + " not found");
            si.getServerConnection().logout();
            throw new Exception("Source Virtual Machine Not Found");
        }

        VirtualMachineCloneSpec cloneSpec = new VirtualMachineCloneSpec();
        VirtualMachineRelocateSpec relSpec = new VirtualMachineRelocateSpec();
        relSpec.diskMoveType = "createNewChildDiskBacking";
        cloneSpec.setLocation(relSpec);
        cloneSpec.setPowerOn(false);
        cloneSpec.setTemplate(false);
        cloneSpec.snapshot = vm.getCurrentSnapShot().getMOR();

        System.out.println("Cloning " + vmname + " into " + clonename);
        Task task = vm.cloneVM_Task((Folder) vm.getParent(), clonename, cloneSpec);

        String status = task.waitForMe();
        if(status==Task.SUCCESS)
        {
            System.out.println("VM cloned successfully.");
        }
        else
        {
            throw new Exception("Error while cloning VM");
        }
    }

    private static ArrayList<Integer> getIndependenetVirtualDiskKeys(VirtualMachine vm) throws Exception
    {
        ArrayList<Integer> diskKeys= new ArrayList<Integer>();

        VirtualDevice[] devices = (VirtualDevice[]) vm.getPropertyByPath("config.hardware.device");

        for(int i=0; i<devices.length; i++)
        {
            if(devices[i] instanceof VirtualDisk)
            {
                VirtualDisk vDisk = (VirtualDisk) devices[i];
                String diskMode = "";
                VirtualDeviceBackingInfo vdbi = vDisk.getBacking();

                if(vdbi instanceof VirtualDiskFlatVer1BackingInfo)
                {
                    diskMode = ((VirtualDiskFlatVer1BackingInfo) vdbi).getDiskMode();
                }
                else if(vdbi instanceof VirtualDiskFlatVer2BackingInfo)
                {
                    diskMode = ((VirtualDiskFlatVer2BackingInfo)vdbi).getDiskMode();
                }
                else if(vdbi instanceof VirtualDiskRawDiskMappingVer1BackingInfo)
                {
                    diskMode = ((VirtualDiskRawDiskMappingVer1BackingInfo)vdbi).getDiskMode();
                }
                else if(vdbi instanceof VirtualDiskSparseVer1BackingInfo)
                {
                    diskMode = ((VirtualDiskSparseVer1BackingInfo)vdbi).getDiskMode();
                }
                else if(vdbi instanceof VirtualDiskSparseVer2BackingInfo)
                {
                    diskMode = ((VirtualDiskSparseVer2BackingInfo)vdbi).getDiskMode();
                }

                if(diskMode.indexOf("independent") != -1)
                {
                    diskKeys.add(vDisk.getKey());
                }
            }
        }
        return diskKeys;
    }

    private static ManagedObjectReference getSnapshotReference(VirtualMachine vm, String snapshotName) throws Exception
    {
        VirtualMachineSnapshotInfo snapInfo = vm.getSnapshot();

        ManagedObjectReference snapmor = null;

        if(snapInfo != null)
        {
            VirtualMachineSnapshotTree[] snapTree = snapInfo.getRootSnapshotList();
            snapmor = traverseSnapshotInTree(snapTree, snapshotName);
        }
        return snapmor;
    }

    private static ManagedObjectReference traverseSnapshotInTree(VirtualMachineSnapshotTree[] snapTree, String snapshotName)
    {
        if(snapTree == null || snapshotName == null)
            return null;

        ManagedObjectReference snapmor = null;

        for (int i=0; i < snapTree.length && snapmor == null; i++)
        {
            VirtualMachineSnapshotTree node = snapTree[i];
            if (node.getName().equals(snapshotName))
            {
                snapmor = node.getSnapshot();
            }
            else
            {
                VirtualMachineSnapshotTree[] childTree = snapTree[i].getChildSnapshotList();
                snapmor = traverseSnapshotInTree(childTree, snapshotName);
            }
        }
        return snapmor;
    }
}