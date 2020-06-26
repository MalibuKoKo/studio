/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package studio.driver;

import org.usb4java.*;
import studio.driver.event.DeviceHotplugEventListener;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class LibUsbActivePollingWorker implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(LibUsbActivePollingWorker.class.getName());

    private final Context context;
    private final DeviceHotplugEventListener listener;
    private Device device = null;

    public LibUsbActivePollingWorker(Context context, DeviceHotplugEventListener listener) {
        this.context = context;
        this.listener = listener;
    }

    @Override
    public void run() {
        // List available devices
        DeviceList devices = new DeviceList();
        int result = LibUsb.getDeviceList(this.context, devices);
        if (result < 0) {
            throw new LibUsbException("Unable to get libusb device list", result);
        }
        try {
            // Iterate over all devices and scan for the right one
            Device found = null;
            for (Device d: devices) {
                DeviceDescriptor descriptor = new DeviceDescriptor();
                result = LibUsb.getDeviceDescriptor(d, descriptor);
                if (result != LibUsb.SUCCESS) {
                    throw new LibUsbException("Unable to read libusb device descriptor", result);
                }
                if (descriptor.idVendor() == LibUsbHelper.VENDOR_ID && descriptor.idProduct() == LibUsbHelper.PRODUCT_ID) {
                    found = d;
                }
            }
            // Fire plugged / unplugged events
            if (found != null && this.device == null) {
                LOGGER.info("Active polling found a new device. Firing event.");
                this.device = found;
                CompletableFuture.runAsync(() -> this.listener.onDevicePlugged(this.device));
            } else if (found == null && this.device != null) {
                LOGGER.info("Active polling lost the device. Firing event.");
                CompletableFuture.runAsync(() -> this.listener.onDeviceUnplugged(this.device));
                this.device = null;
            }
        } finally {
            // Ensure the allocated device list is freed
            LibUsb.freeDeviceList(devices, false);  // Do NOT unref devices
        }
    }
}
