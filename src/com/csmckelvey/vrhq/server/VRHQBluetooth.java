package com.csmckelvey.vrhq.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.DataElement;
import javax.bluetooth.DeviceClass;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.DiscoveryListener;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.UUID;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;

import com.csmckelvey.vrhq.core.Constants;
import com.csmckelvey.vrhq.core.VRHQLogger;
import com.csmckelvey.vrhq.core.VRHQRequest;
import com.google.gson.Gson;

public class VRHQBluetooth {
	protected RemoteDevice clientDevice;
	protected boolean clientFound;
	protected String clientToSearchFor = "";
	
	private static VRHQLogger logger;
	
	protected List<RemoteDevice> currentScannedDevices = new ArrayList<>();
	protected Map<String, String> bluetoothServicesFound = new HashMap<>();
	protected List<RemoteDevice> allBluetoothClientsFound = new ArrayList<>();
	
	protected final Object deviceScanCompletedEvent = new Object();
	protected final Object serviceScanCompletedEvent = new Object();
	protected final Object deviceSearchCompletedEvent = new Object();
	
	static {
		logger = VRHQLogger.getLogger();
	}
	
	public void scanAllBluetoothDevices() {
		synchronized(deviceScanCompletedEvent) {
	        try {
	        	currentScannedDevices.clear();
	            LocalDevice local = LocalDevice.getLocalDevice();
	            local.setDiscoverable(DiscoveryAgent.GIAC);
	            DiscoveryAgent discoveryAgent = local.getDiscoveryAgent();
	            boolean startedInquiry = discoveryAgent.startInquiry(DiscoveryAgent.GIAC, new ScanDiscoveryListener());
	            while (startedInquiry) {
	            	deviceScanCompletedEvent.wait();
	            }
	        } catch (BluetoothStateException e) {
				logger.log("BluetoothStateException exception: " + e);
				
			} catch (InterruptedException e) {
				logger.log("InterruptedException exception: " + e);
				 Thread.currentThread().interrupt();
	        }
	    }
		
		logger.log("Found " + currentScannedDevices.size() + " device(s) during this scan");
		logger.log(allBluetoothClientsFound.size() + " total device(s) discovered");
		
		String tmpName;
		for (RemoteDevice device : allBluetoothClientsFound) {
			try { 
				tmpName = device.getFriendlyName(true); 
			} catch (IOException e) { 
				tmpName = "Unknown Name";
				logger.logException(e);
			}
			
			logger.log(tmpName + " @ " + device.getBluetoothAddress(), 1, 0);
		}
	}
	
	public boolean findBluetoothDevice(String deviceAddress) {
		clientFound = false;
		clientToSearchFor = deviceAddress;
		
		for (RemoteDevice device : allBluetoothClientsFound) {
			if (device.getBluetoothAddress().equals(clientToSearchFor)) {
				clientFound = true;
				clientToSearchFor = "";
				clientDevice = device;
				
				return clientFound;
			}
		}
		
		synchronized(deviceSearchCompletedEvent) {
	        try {
	            LocalDevice local = LocalDevice.getLocalDevice();
	            local.setDiscoverable(DiscoveryAgent.GIAC);
	            DiscoveryAgent discoveryAgent = local.getDiscoveryAgent();
	            boolean startedInquiry = discoveryAgent.startInquiry(DiscoveryAgent.GIAC, new SearchDiscoveryListener());
	            while (startedInquiry) {
	                deviceSearchCompletedEvent.wait();
	            }
	        } catch (BluetoothStateException e) {
			    logger.log("BluetoothStateException exception: " + e);
			} catch (InterruptedException e) {
			    logger.log("InterruptedException exception: " + e);
			    Thread.currentThread().interrupt();
	        }
	        
	        clientToSearchFor = "";
			return clientFound;
	    }
	}

	public void searchClientForBluetoothService(String uuid) {
		synchronized(serviceScanCompletedEvent){
			try {
				logger.log("Searching for UUID " + uuid);
				UUID[] serviceUUID = new UUID[] {new UUID(uuid.replaceAll("-", ""), false)};
				LocalDevice localDevice = LocalDevice.getLocalDevice();
				DiscoveryAgent agent = localDevice.getDiscoveryAgent();
				
				agent.searchServices(null, serviceUUID, clientDevice, new ServiceDiscoveryListener());
			} catch (BluetoothStateException e) {
				logger.logException(e);
			}
		}
		
		logger.log(bluetoothServicesFound.size() + " total service(s) discovered");
		for (Entry<String, String> entry : bluetoothServicesFound.entrySet()) {
			logger.log(entry.getKey() + " @ " + entry.getValue(), 1, 0);
		}
	}
	
	public VRHQRequest startListeningBluetooth(String uuidString) {
		VRHQRequest request = null;
		
        
            logger.log("Waiting for clients to connect @ " + uuidString + "...");
            StreamConnection connection = getBluetoothConnection(uuidString);
            RemoteDevice dev;
            BufferedReader bReader;
            
			try {
				dev = RemoteDevice.getRemoteDevice(connection);
				logger.log("Got a connection from ["+dev.getFriendlyName(true)+"]");
				InputStream inStream = connection.openInputStream();
	            bReader = new BufferedReader(new InputStreamReader(inStream));
	            String jsonReceived = bReader.readLine();
	            logger.log("Received: " + jsonReceived);
	            Gson gson = new Gson();
	            request = gson.fromJson(jsonReceived, VRHQRequest.class);
	            bReader.close();
	            connection.close();
			} 
			catch (IOException e) {
				logger.logException(e);
			}
            
        
        return request;
	}
	
	public void sendResponse(String responseString, String uuidString) {
		PrintWriter out = null;
		
		try {
			logger.log("Sending Response...");
			out = new PrintWriter(getBluetoothConnection(uuidString).openOutputStream(), true);
			out.write(responseString + "\n");
			out.close();
			logger.log("Response Sent!");
		} catch (IOException e) {
			logger.logException(e);
		}
	}
	
	public StreamConnection getBluetoothConnection(String uuidString) {
		UUID uuid = new UUID(uuidString.replaceAll("-", ""), false);
		final String url  =  "btspp://localhost:" + uuid  + ";name=" + Constants.SERVER_NAME + ";authenticate=false;encrypt=false;";

		RemoteDevice device = null;
		StreamConnection connection = null;
		StreamConnectionNotifier streamConnNotifier = null;
		try {
			LocalDevice.getLocalDevice().setDiscoverable(DiscoveryAgent.GIAC);
			logger.log("Opening URL @ " + url);
			streamConnNotifier = (StreamConnectionNotifier) Connector.open(url);
			logger.log("Now Accepting and Opening Connections");
	        connection = streamConnNotifier.acceptAndOpen();
	        logger.log("Connection Accepted and Opened");
	        device = RemoteDevice.getRemoteDevice(connection);
            logger.log("Connecting to ["+device.getFriendlyName(true)+"]");
		} catch (IOException e) {
			logger.logException(e);
		}
		
		return connection;
	}
	
	class ScanDiscoveryListener implements DiscoveryListener {
		
		@Override
	    public void deviceDiscovered(RemoteDevice device, DeviceClass cod) {
	    	String address = device.getBluetoothAddress();
	    	logger.log("Device found @ " + address);
	    	currentScannedDevices.add(device);
	    	
			//if (!allBluetoothClientsFound.contains(address)) {
			//	allBluetoothClientsFound.add(device);
			//}
	    }
	
		@Override
	    public void inquiryCompleted(int discType) {
	    	logger.log("Device Scan Complete");
	        synchronized(deviceScanCompletedEvent) {
	            deviceScanCompletedEvent.notifyAll();
	        }
	    }
	
		@Override
	    public void serviceSearchCompleted(int transID, int respCode) {}
		
		@Override
	    public void servicesDiscovered(int transID, ServiceRecord[] servRecord) {}    
	}
	
	class SearchDiscoveryListener implements DiscoveryListener {
		
		@Override
	    public void deviceDiscovered(RemoteDevice device, DeviceClass cod) {
	    	String address = device.getBluetoothAddress();
	        
        	if (address.equals(clientToSearchFor)) {
				clientFound = true;
				clientDevice = device;
			}
			
			//if (!allBluetoothClientsFound.contains(address)) {
			//	allBluetoothClientsFound.add(device);
			//}
	    }
	
		@Override
	    public void inquiryCompleted(int discType) {
	    	logger.log("Device Search Complete");
	        synchronized(deviceSearchCompletedEvent) {
	            deviceSearchCompletedEvent.notifyAll();
	        }
	    }
	
		@Override
	    public void serviceSearchCompleted(int transID, int respCode) {}
	
		@Override
	    public void servicesDiscovered(int transID, ServiceRecord[] servRecord) {}    
	}
	
	class ServiceDiscoveryListener implements DiscoveryListener {

		@Override
		public void inquiryCompleted(int arg0) {}
		
		@Override
		public void deviceDiscovered(RemoteDevice arg0, DeviceClass arg1) {}

		@Override
		public void serviceSearchCompleted(int arg0, int arg1) {
			synchronized (serviceScanCompletedEvent) {
				serviceScanCompletedEvent.notifyAll();
			}
		}

		@Override
		public void servicesDiscovered(int arg0, ServiceRecord[] services) {
			for (int i = 0; i < services.length; i++) {
				logger.log(services[i]+"");
				String url = services[i].getConnectionURL(ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false);
				if (url == null) { 
					continue; 
				}
				
				DataElement serviceName = services[i].getAttributeValue(0x0100);
				if (serviceName != null) {
					logger.log("Discovered Service [" + serviceName.getValue() + "] @ " + url);
					bluetoothServicesFound.put(serviceName.getValue().toString(), url);
				} 
				else {
					logger.log("Discovered Unknown Service @ " + url);
				}
			}
		}
		
		public void sendMessageToDevice(String serverURL){
			logger.log("< INSERT CONNECTION AND COMMAND SENDING CODE > @ " + serverURL);
//	        try{
//	            System.out.println("Connecting to " + serverURL);
//	    
//	            ClientSession clientSession = (ClientSession) Connector.open(serverURL);
//	            HeaderSet hsConnectReply = clientSession.connect(null);
//	            if (hsConnectReply.getResponseCode() != ResponseCodes.OBEX_HTTP_OK) {
//	                System.out.println("Failed to connect");
//	                return;
//	            }
//	    
//	            HeaderSet hsOperation = clientSession.createHeaderSet();
//	            hsOperation.setHeader(HeaderSet.NAME, "Hello.txt");
//	            hsOperation.setHeader(HeaderSet.TYPE, "text");
//	    
//	            //Create PUT Operation
//	            Operation putOperation = clientSession.put(hsOperation);
//	    
//	            // Sending the message
//	            byte data[] = "Hello World !!!".getBytes("iso-8859-1");
//	            OutputStream os = putOperation.openOutputStream();
//	            os.write(data);
//	            os.close();
//	    
//	            putOperation.close();
//	            clientSession.disconnect(null);
//	            clientSession.close();
//	        }
//	        catch (Exception e) {
//	            e.printStackTrace();
//	        }
	    }

	}
}
