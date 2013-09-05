package org.kde.kdeconnect.ComputerLinks;

import org.kde.kdeconnect.LinkProviders.BaseLinkProvider;
import org.kde.kdeconnect.NetworkPackage;

import java.util.ArrayList;


public abstract class BaseComputerLink {

    private BaseLinkProvider linkProvider;
    private String deviceId;
    private ArrayList<PackageReceiver> receivers = new ArrayList<PackageReceiver>();

    protected BaseComputerLink(String deviceId, BaseLinkProvider linkProvider) {
        this.linkProvider = linkProvider;
        this.deviceId = deviceId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public BaseLinkProvider getLinkProvider() {
        return linkProvider;
    }


    public interface PackageReceiver {
        public void onPackageReceived(NetworkPackage np);
    }

    public void addPackageReceiver(PackageReceiver pr) {
        receivers.add(pr);
    }
    public void removePackageReceiver(PackageReceiver pr) {
        receivers.remove(pr);
    }

    //Should be called from a background thread listening to packages
    protected void packageReceived(NetworkPackage np) {
        for(PackageReceiver pr : receivers) {
            pr.onPackageReceived(np);
        }
    }

    //TO OVERRIDE
    public abstract boolean sendPackage(NetworkPackage np);

}
