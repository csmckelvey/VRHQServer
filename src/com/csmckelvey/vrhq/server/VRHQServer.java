package com.csmckelvey.vrhq.server;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;

import com.csmckelvey.vrhq.core.Constants;
import com.csmckelvey.vrhq.core.VRHQLogger;
import com.csmckelvey.vrhq.core.VRHQRequest;
import com.google.gson.Gson;

public class VRHQServer {
	
	private int port = -1;
	private final boolean deployed = false;	

	private static VRHQLogger logger = null;

	private Properties props = null;
	private String blutoothUUID = null;
	private String[] commandArray = null;	
	private String clientBTAddress = null;
	private String serverBTAddress = null;
	private VRHQBluetooth bluetooth = new VRHQBluetooth();

	static {
		logger = VRHQLogger.getLogger();
	}
	
	public static void main(String[] args) {
		VRHQServer server = new VRHQServer();
		server.init();
		
		//server.scanAllBluetoothDevices();
		
		//Here we should start 2 threads - 1 to listen bluetooth and the other tcp
		//Those threads should receive a message, execute a command, process the result, respond, then listen again
		//If there are any exceptions the listening should continue
		
		//These threads should only contain connection specific code
		//These should use 'helper' objects which can be used by both threads for other code/functionality
		logger.log("Bluetooth Server Started!");
		while (true) {
			server.startListeningBluetooth();
			//server.startListening(port);
		}
		
	}
	
	public void init() {
		logger.log("Initializing VRHQServer...", 0, 0, true);
		
		loadProperties();
		buildCommandMap();
		
		logger.log("Initializing VRHQServer Complete!", 0, 1, true);
	}
	
	private void loadProperties() {
		logger.log("Loading Properties...", 0, 0, true);
		
		props = new Properties();
		InputStream input = null;

		try {
			
			//	If the server is deployed as a jar we want to use the properties file that is right next to the jar
			//	If the server is running in eclipse we want to use the properties file in the workspace
			if (deployed) { 
				input = new FileInputStream("./" + Constants.PROPERTY_FILENAME); 
			}
			else { 
				input = VRHQServer.class.getResourceAsStream("/config/" + Constants.PROPERTY_FILENAME); 
			}
			
			props.load(input);
			
			blutoothUUID = props.getProperty("UUID");
			port = Integer.parseInt(props.getProperty("port"));
			clientBTAddress = props.getProperty("clientBluetoothAddress");
			serverBTAddress = props.getProperty("serverBluetoothAddress");
			
			if ("true".equals(props.getProperty("debug"))) { 
				VRHQLogger.setDebug(true); 
			}
			if ("true".equals(props.getProperty("outputToFile"))) { 
				VRHQLogger.setOutputToFile(true); 
			}
			VRHQLogger.setOutputFileName(props.getProperty("outputFileName"));
			VRHQLogger.setExceptionOutputFileName(props.getProperty("exceptionOutputFileName"));
			
			logger.log("IP Address          | " + props.getProperty("ipaddress"), 1, 0);
			logger.log("Debug Output        | " + props.getProperty("debug"), 1, 0);
			logger.log("Platform Code       | " + props.getProperty("platform"), 1, 0);
			logger.log("Platform Name       | " + props.getProperty("platformName"), 1, 0);
			logger.log("Bluetooth UUID      | " + blutoothUUID, 1, 0);
			logger.log("Output To File      | " + props.getProperty("outputToFile"), 1, 0);
			logger.log("TCP Port Number     | " + port, 1, 0);
			logger.log("Output File Name    | " + props.getProperty("outputFileName"), 1, 0);
			logger.log("Exception File Name | " + props.getProperty("exceptionOutputFileName"), 1, 0);
			logger.log("Client BT Address   | " + clientBTAddress, 1, 0);
			logger.log("Server BT Address   | " + serverBTAddress, 1, 0);
		} catch (IOException e) {
			logger.logException(e);
		} finally {
			if (input != null) {
				try { 
					input.close(); 
				} 
				catch (IOException e) { 
					logger.logException(e); 
				}
			}
		}
		
		logger.log("Loading Properties Complete!", 0, 0, true);

	}

	private void buildCommandMap() {
		logger.log("Building Command Map...");
		boolean windows = props.getProperty("platform").equals("0");
		
		commandArray = new String[10];
		
		commandArray[Constants.SC_STATUS] = "";
		commandArray[Constants.SC_SCAN_NETWORK] = "";
		commandArray[Constants.SC_LIST_NETWORKS] = "";
		commandArray[Constants.SC_CONNECT_NETWORK] = "";
		
		commandArray[Constants.SC_PING] = "ping";
		if (windows) {
			commandArray[Constants.SC_TRACE] = "tracert";
			commandArray[Constants.SC_NETWORK_INFO] = "ipconfig";
		}
		else {
			commandArray[Constants.SC_TRACE] = "traceroute";
			commandArray[Constants.SC_NETWORK_INFO] = "ifconfig";
		}
		
		logger.log("PING               | " + commandArray[Constants.SC_PING], 1, 0);
		logger.log("TRACE              | " + commandArray[Constants.SC_TRACE], 1, 0);
		logger.log("STATUS             | " + commandArray[Constants.SC_STATUS], 1, 0);
		logger.log("NETWORK_INFO       | " + commandArray[Constants.SC_NETWORK_INFO], 1, 0);
		logger.log("SCAN_NETWORK       | " + commandArray[Constants.SC_SCAN_NETWORK], 1, 0);
		logger.log("LIST_NETWORKS      | " + commandArray[Constants.SC_LIST_NETWORKS], 1, 0);
		logger.log("CONNECT_NETWORK    | " + commandArray[Constants.SC_CONNECT_NETWORK], 1, 0);
		
		logger.log("Building Command Map Complete!");
	}
	
	private void scanAllBluetoothDevices() {
		logger.log("Beginning Bluetooth Device Scan...");
		bluetooth.scanAllBluetoothDevices();
		logger.log("Bluetooth Device Scan Completed!", 0, 1);
	}
	
	private void findBluetoothDevice() {
		logger.log("Beginning Bluetooth Device Search...");
		logger.log("Searching For Bluetooth Device @ " + clientBTAddress);
		
		while (!bluetooth.findBluetoothDevice(clientBTAddress.replaceAll(":", ""))) {
			logger.log("Searching Again For Bluetooth Device @ " + clientBTAddress);
		}
		
		logger.log("Device Discovered @ " + clientBTAddress);
		logger.log("Bluetooth Device Search Completed!", 0, 1);
	}
		
	private void searchClientForBluetoothService() {
		bluetooth.searchClientForBluetoothService(blutoothUUID);
	}
	
	private void searchClientForBluetoothService(String uuid) {
		if (uuid.contains("-")) { uuid = uuid.replaceAll("-", ""); }
		
		logger.log("Bluetooth Client Service Search Starting...");
		bluetooth.searchClientForBluetoothService(uuid);
		logger.log("Bluetooth Client Service Search Completed!");
	}
	
	private void startListening(int port) {
		Socket socket = null;
		PrintWriter out = null;
		BufferedReader in = null;
		ServerSocket serverSocket = null;
		
		try {
			Gson gson = new Gson();
			VRHQRequest request = null;
			
			serverSocket = new ServerSocket(port);
			
			while (true) {
				logger.log("Listening...", 0, 0, true);
				socket = serverSocket.accept(); 
				logger.log("Got A Connection From " + socket.getInetAddress(), 0, 0, true);
				
			    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			    
			    if (in != null) {
			    	String line = null;
			    	logger.log("Receiving...");
					if ((line = in.readLine()) != null) {
						request = gson.fromJson(line, VRHQRequest.class);
					}
			    }
			    
			    logger.log("Request Received");
			    logger.log(request.toString(), 1, 0);
			    logger.log("\n-----------------------------------------------", 0, 1);
			    
			    //TODO
			    //Here I need to do the command lookup and execute that, not the request
			    String result = executeCommand(request.getMessage());
			    logger.log("Sending Response", 0, 0, true);
			    logger.log(result);
			    
			    out = new PrintWriter(socket.getOutputStream(), true);
			    
			    //TODO
			    //Here is where I will invoke the parser to turn the result into a JSON object
			    out.write(result + "\n");
			    out.flush();
			    
			    in.close();
			    out.close();
			    socket.close();
			}
		} catch (IOException e) {
			logger.logException(e);
		} finally {
			if (out != null) {
				out.close();
			}
			if (serverSocket != null) { 
				try { 
					serverSocket.close();
				} 
				catch (IOException e) { 
					logger.logException(e);
				} 
			}
			if (socket != null) { 
				try { 
					socket.close(); 
				} 
				catch (IOException e) { 
					logger.logException(e); 
				} 
			}
			if (in != null) {
				try { 
					in.close(); 
				} 
				catch (IOException e) { 
					logger.logException(e);
				}
			}
		}
	}
	
	private void startListeningBluetooth() {
		logger.log("Starting Bluetooth Listener...");
		
		VRHQRequest request = bluetooth.startListeningBluetooth(blutoothUUID);
		Process commandProcess;
        String commandOutputRead;
        StringBuilder commandOutput = new StringBuilder();
        
        try {
        	switch(request.getCommand()) {
        		case -1:
        			commandOutput.append("SUCCESS");
        			logger.log(commandOutput.toString());
        			break;
        		case Constants.SC_NETWORK_INFO: 
        			logger.log("Executing " + commandArray[Constants.SC_NETWORK_INFO]);
        			commandProcess = Runtime.getRuntime().exec(commandArray[Constants.SC_NETWORK_INFO]);
                    BufferedReader commandOutputReader = new BufferedReader(new InputStreamReader(commandProcess.getInputStream()));
                    while ((commandOutputRead = commandOutputReader.readLine()) != null) {
                        commandOutput.append(commandOutputRead);
                    }
                    commandProcess.waitFor();
                    commandProcess.destroy();
                    break;
        		default:
        			commandOutput.append("Unknown Command Received");
        			logger.log(commandOutput.toString());
        			break;
        	}
        } catch (Exception e) {
        	logger.logException(e);
        }
        
        bluetooth.sendResponse(commandOutput.toString(), blutoothUUID);
        
		logger.log("Blutooth Listener Completed!");
	}
	
	private String executeCommand(String command) {
		Process process;
		StringBuffer output = new StringBuffer();

		try {
			//TODO
			//This should stream output not just wait
			logger.log("Executing ["+command+"] ...");
			process = Runtime.getRuntime().exec(command);
			process.waitFor();
			BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));

			String line = null;			
			while ((line = in.readLine()) != null) {
				System.out.println(line);
				output.append(line + "\n");
			}
		} catch (Exception e) {
			logger.logException(e);
			output.append("Server Error");
		}

		return output.toString();
	}

	private void showBanner() {
		logger.log("");
		logger.log("");
		logger.log("");
		logger.log("");
		logger.log("");
		logger.log("");
	}
}
