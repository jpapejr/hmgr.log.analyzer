package com.john.websphere;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class Main {
	
	private StringBuffer analysisBuffer;
	private StringBuffer exportBuffer;
	private Document cgbs;
	private Document[] cgs;

	
	
	private static final String CORE_GROUP_BRIDGE_FILE = "coregroupbridge.xml";
	private static final String CORE_GROUP_FILE = "coregroup.xml";
	private final String HEADER_LINE = "----------------------------------------------------------------------------------------------------------------------------------------------------------------------";
	private final String REPORT_INDENT_ONE = "     ";
	private final String REPORT_INDENT_TWO = REPORT_INDENT_ONE + "     ";
	private final String REPORT_INDENT_THREE = REPORT_INDENT_TWO + "     ";
	private final String COMPUTE_FULL_VIEW = "-C-";
	private final String COMPUTE_PARTIAL_VIEW = "-I-";
	private final String COMPUTE_ASYMMETRIC_VIEW = "-A-";
	private final static String VERSION_ID = "1.0.0";

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Main Main = new Main();
		if (args.length != 2){
			showHelp();
		}
		
		
		if (args[0].equalsIgnoreCase("log_raw")){
			Main.provideLogParsed(new File(args[1]));
		} else if (args[0].equalsIgnoreCase("log_analysis")){
			Main.provideLogAnalysis(new File(args[1]));
		} else if (args[0].equalsIgnoreCase("config")){
			Main.provideConfigAnalysis(new File(args[1]));
		} else if (args[0].equalsIgnoreCase("msg")){
			Main.provideMessageDetail(args[1]);
		} else {
			showHelp();
		}

	}
	
	private void provideLogAnalysis(File file) {
		try {
			doDeepAnalysis(fetchAndParseLogs(file.getAbsolutePath()),file);
			File output = new File(new File(".").getCanonicalPath() + File.separator + file.getName() + "_" + System.currentTimeMillis() + "_log_analysis.txt");
			FileWriter writer = new FileWriter(output);
			writer.write(analysisBuffer.toString());
			writer.write("\n report version: v"  +VERSION_ID + "\n");
			writer.flush();
			writer.close();
			System.out.println("Output written to " + output);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		
	}
	
	
	private void provideConfigAnalysis(File file){
		try {
			fetchExport(file.getAbsolutePath());
			File output = new File(new File(".").getCanonicalPath()  + File.separator + file.getName() + "_" + System.currentTimeMillis() + "_config_analysis.txt");
			FileWriter writer = new FileWriter(output);
			writer.write(exportBuffer.toString());
			writer.write("\n report version: v"  +VERSION_ID + "\n");
			writer.flush();
			writer.close();
			System.out.println("Output written to " + output);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void showHelp(){
		StringBuffer buffer = new StringBuffer();
		buffer.append("HAManager log analysis tool v"+ VERSION_ID +" by John Pape, Jr.\n");
		buffer.append("This tool is not distributed by or supported by IBM. \n");
		buffer.append("------------------------------------------------------- \n");
		buffer.append("Usage 'java -jar hmgrloganalyzer.jar <analysis type> <file path> or <message id> \n");
		buffer.append("------------------------------------------------------- \n");
		buffer.append("Where 'analysis type' is one of the following: \n");
		buffer.append("- log_raw - output the log only showing pertinent HA stack-related messages\n");
		buffer.append("- log_analysis  - output a summary analysis \n");
		buffer.append("- config - output a summary of the core group, bridge, and access point configuration data\n");
		buffer.append("- msg  - output a detailed description of the provided log message (if available)\n\n");
		buffer.append("Using the msg option, the second argument should be the WebSphere message ID\n");
		buffer.append("to be analyzed. Otherwise, the second argument is always a file path to the \n");
		buffer.append("resource to be processed by this tool.\n");
		buffer.append("For log operations, the file input should be a WebSphere SystemOut/SystemErr/trace log file.\n");
		buffer.append("For config operations, the file input should be the path to the cell name directory located under\n");
		buffer.append("the config director (e.g. /WASHOME/config/cells/CELL_NAME_DIRECTORY ");
		buffer.append("\n\n");
		System.out.println(buffer.toString());
		System.exit(0);
		
	}
	
	private void provideLogParsed(File file){
		try {
			String[] results = fetchAndParseLogs(file.getAbsolutePath());
			File output = new File(new File(".").getCanonicalPath() + File.separator + file.getName() + "_" + System.currentTimeMillis() + "_log_parsed.txt");
			FileWriter writer = new FileWriter(output);
			writer.write("Log Parse Result\n");
			writer.write("Time window: " + results[0].substring(0,24) + " -- " + results[results.length - 1].subSequence(0, 24) + "\n");
			try {
				writer.write("Source File : " + file.getCanonicalFile() + "\n");
			} catch (IOException e) {
				//swallow this exception
			}
			writer.write(HEADER_LINE + "\n");
			
			for (String line : results){
				writer.write(line + "\n");
				
			}
			writer.write("\n report version: v"  +VERSION_ID + "\n");
			writer.flush();
			writer.close();
			System.out.println("Output written to " + output);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	private String[] fetchAndParseLogs(String path) throws Exception{
		System.out.println("Parsing logs at " + path + ", please wait a moment...");
		String[] sArray = null;
		ArrayList<String> messages = new ArrayList<String>();
		try {

			InputStream is = new FileInputStream(path);

			//System.out.println("working the stream");
			BufferedReader br = null;
			try {
				br = new BufferedReader(new InputStreamReader(is));
				String line = null;
				while ((line = br.readLine()) != null ){ // this loop is to cycle through the entire log
					//System.out.println("working through the file - line :" + line);
					if (line.indexOf("HMGR") >= 0){
						messages.add(handlePassThruHit(line));
					}else if (line.indexOf("DCSV") >= 0){
						messages.add(handlePassThruHit(line));
					}else if (line.indexOf("CWRCB") >= 0){
						messages.add(handlePassThruHit(line));
					}else if (line.indexOf("WSVR0001I") >= 0){
						messages.add(handlePassThruHit(line));
					}else if (line.indexOf("WSVR0024I") >= 0){
						messages.add(handlePassThruHit(line));
					}else if (line.indexOf("TRAS0017I") >= 0){
						messages.add(handleServerStartupHit(line));
					} else if (line.indexOf("WSVR0605W") >= 0){
						messages.add(handlePassThruHit(line));
					} else if (line.indexOf("WSVR0606W") >= 0){
						messages.add(handlePassThruHit(line));
					} 
				}
				//System.out.println("---" + messages.toString() + "---");
				Object[] oArray = messages.toArray();
				sArray = new String[messages.size()];
				System.arraycopy(oArray, 0, sArray , 0, messages.size());

			} catch (FileNotFoundException e) {
				e.printStackTrace();
				throw e;

			} catch (IOException e) {
				e.printStackTrace();
				throw e;

			} finally {
				try { br.close(); } catch (Exception e) {}; //swallow this, nobody cares
				try { is.close(); } catch (Exception e) {}; //swallow this, nobody cares
			}


		} catch (IOException e) {
			e.printStackTrace();
			throw e;
		} 

		//System.out.println("returning the result");
		if (sArray.length == 0){
			sArray = new String[] {"The analyzed data contained no HAManager log messages"};
		}
		return sArray;

		
		
		
	}
	
	/*
	 * Convenience method for converting a server start message into a nice, plain-text message
	 */
	private String handleServerStartupHit(String input){
		String date = extractDate(input);
		String doctored = input.substring(date.length() + 28, input.length());
		return date + "  The server is starting. " + doctored; 
	}
	
	/*
	 * Convenience method for just parsing the date for the entry and returning it 
	 */
	private String handlePassThruHit(String input){
		String date = extractDate(input);
		String doctored = input.substring(date.length() + 28, input.length());
		return date + " " + doctored; 
	}
	
	/*
	 * 
	 */
	private String extractDate(String x){
		int endDate = x.indexOf("]");
		String dateString = x.substring(1, endDate);
		return dateString;
	}
	
	/**
	 * This method is the master method for the detailed analysis operations performed on the exportDoc instance variable. This
	 * method does no real work, instead, it delegates all the work to other methods.
	 * 
	 * @param entries - a String array of log entries
	 */
	private void doDeepAnalysis(String[] entries, File file){
		analysisBuffer = new StringBuffer();
		analysisBuffer.append("Log Analysis Report\n");
		analysisBuffer.append("Time window: " + entries[0].substring(0,25) + " -- " + entries[entries.length - 1].subSequence(0, 25) + "\n");
		try {
			analysisBuffer.append("Source File : " + file.getCanonicalFile() + "\n");
		} catch (IOException e) {
			//swallow this exception
		}
		analysisBuffer.append(HEADER_LINE + "\n");
		
		summarizeCPUAndThreadIssues(entries);
		summarizeViewChanges(entries);
		summarizeCoreGroupBridgeStability(entries);
		summarizeViewJoinProblems(entries);
		summarizeServerCycles(entries);
		summarizeHungThreads(entries);
		summarizeDCSCongestion(entries);
		
	}
	
	private void summarizeDCSCongestion(String[] entries) {
		analysisBuffer.append(createReportHeader("DCS Congestion"));
		int totalStarvedTime = 0;
		for (String entry : entries){
			if (entry.indexOf("DCSV1051W") >= 0 || entry.indexOf("DCSV1052W") >= 0 || entry.indexOf("DCSV1053I") >= 0){
				//handle HMGR msgs
				String dateString = entry.substring(0, 25);
				
				if (entry.indexOf("DCSV1051W") >= 0){
					String stack = entry.substring(entry.indexOf(" DCS Stack ") + 11, entry.indexOf(" at Member "));
					String member = entry.substring(entry.indexOf("at Member ") + 10, entry.indexOf(": Raised"));
					analysisBuffer.append(REPORT_INDENT_ONE + dateString.trim() + " DCS Stack " + stack + " reported a HIGH SEVERITY congestion alert on member : " + member + "\n");
					
				}
				if (entry.indexOf("DCSV1052W") >= 0){
					String stack = entry.substring(entry.indexOf(" DCS Stack ") + 11, entry.indexOf(" at Member "));
					String member = entry.substring(entry.indexOf("at Member ") + 10, entry.indexOf(": Raised"));
					analysisBuffer.append(REPORT_INDENT_ONE + dateString.trim() + " DCS Stack " + stack + " reported a MEDIUM SEVERITY congestion alert on member : " + member + "\n");
					
				}
				if (entry.indexOf("DCSV1052W") >= 0){
					String stack = entry.substring(entry.indexOf(" DCS Stack ") + 11, entry.indexOf(" at Member "));
					String member = entry.substring(entry.indexOf("at Member ") + 10, entry.indexOf(": Raised"));
					analysisBuffer.append(REPORT_INDENT_ONE + dateString.trim() + " DCS Stack " + stack + " reported that the congestion reported on member: " + member + " has cleared up\n");
					
				}
				//handle DCSV msgs
			} else {
				//skip it, don't care
			}
			
		}
		//provide a helpful link to a support technote discussing the cause for these messages
		analysisBuffer.append(createReportFooter());
		
	}

	/**
	 * This method summarizes any thread scheduling or CPU starvation occurrences seen in the log entries
	 * @param entries - a String array of log entries
	 */
	private void summarizeCPUAndThreadIssues(String[] entries) {
		analysisBuffer.append(createReportHeader("CPU and Threading Problems"));
		int totalStarvedTime = 0;
		for (String entry : entries){
			if (entry.indexOf("HMGR0152W") >= 0 || entry.indexOf("DCSV0004W") >= 0){
				//handle HMGR msgs
				String dateString = entry.substring(0, 25);
				
				if (entry.indexOf("HMGR0152W") >= 0){
					String delay = entry.substring(entry.indexOf(" is ")+4, entry.length()-9);
					analysisBuffer.append(REPORT_INDENT_ONE + "CPU starvation at " + dateString.trim() + " in which the HAManager threads were delayed " + delay + " seconds.\n");
					totalStarvedTime += new Integer(delay).intValue();
				}
				if (entry.indexOf("DCSV0004W") >= 0){
					String stack = entry.substring(entry.indexOf(" DCS Stack ") + 11, entry.indexOf(" at Member "));
					String lastTime = entry.substring(entry.indexOf("Last known CPU"), entry.length());
					analysisBuffer.append(REPORT_INDENT_ONE + "Insufficient CPU timeslice for stack " + stack + " at " + dateString.trim() + ". " + lastTime + "\n");
					
				}
				//handle DCSV msgs
			} else {
				//skip it, don't care
			}
			//monitor.worked(1);
		}
		//provide a helpful link to a support technote discussing the cause for these messages
		analysisBuffer.append("\nSummary -- Total time starved: " + totalStarvedTime + " seconds (" + totalStarvedTime/60 + " minutes) \n");
		analysisBuffer.append("\nPlease review this link for resolution steps for CPU starvation messages shown above: http://www-01.ibm.com/support/docview.wss?uid=swg21236327   \n");
		analysisBuffer.append(createReportFooter());
	}
	
	/**
	 * This method reviews the log entries it is given and highlights any server lifecycle events (start/stop)
	 * @param entries - a String array of log entries
	 */
	private void summarizeServerCycles(String[] entries){
		analysisBuffer.append(createReportHeader("Server Starts/Stops"));
		for (String entry : entries){
			if (entry.indexOf("WSVR0001I") >= 0 || entry.indexOf("WSVR0024I") >= 0){
				String dateString = entry.substring(0, 25);
				if (entry.indexOf("WSVR0001I") >= 0){
					analysisBuffer.append(REPORT_INDENT_ONE + "The server was started at " + dateString + "\n");
				}
				if (entry.indexOf("WSVR0024I") >= 0){
					analysisBuffer.append(REPORT_INDENT_ONE + "The server was stopped at " + dateString + "\n");
				}
			}
		}
		analysisBuffer.append(createReportFooter());
	}
	
	/**
	 * This method summarizes instances of hung thread alerts produced by WebSphere in the logs
	 * @param entries - a String array of log entries
	 */
	private void summarizeHungThreads(String[] entries){
		analysisBuffer.append(createReportHeader("Hung Thread Warnings"));
		for (String entry : entries){
			if (entry.indexOf("WSVR0605W") >= 0 || entry.indexOf("WSVR0606W") >= 0){
				
				if (entry.indexOf("WSVR0605W") >= 0){
					analysisBuffer.append(REPORT_INDENT_ONE + entry + "\n");
				}
				if (entry.indexOf("WSVR0606W") >= 0){
					analysisBuffer.append(REPORT_INDENT_ONE + entry + "\n");
				}
			}
		}
		analysisBuffer.append(createReportFooter());
	}
	
	/**
	 * This method tracks all of the core group stacks seen in the logs and keeps a running tab on how many view changes
	 * it finds. Additionally, the method contains logic that can detect when a full core group restart is done.
	 * @param entries - a String array of log entries
	 */
	private void summarizeViewChanges(String[] entries) {
		LinkedList<StackViewChangeCollection> list = new LinkedList<StackViewChangeCollection>();
		String dateString, stack, viewData, viewStats = null;
		
		Map<String, Long>trackingMap = new HashMap<String, Long>();
		
		for (String s : entries){
			if (s.indexOf("DCSV8050I") >= 0){
				dateString = s.substring(0, 25);
				stack = s.substring(s.indexOf(" DCS Stack ") + 11, s.indexOf(" at Member "));
				viewData = s.substring(s.indexOf("New view installed,"), s.length());
				viewStats = s.substring(s.lastIndexOf("(")+1, s.lastIndexOf(")"));
				
				//sort the list
				Collections.sort(list);	
				//setup the key object
				StackViewChangeCollection stackViewChangeCollection = new StackViewChangeCollection();
				stackViewChangeCollection.setStackName(stack);
				
				if (Collections.binarySearch(list, stackViewChangeCollection) >= 0){
					//System.out.println("Found stack : " + stack + " in the linkedlist..");
					//we know about this stack aleady, put the entry in the map
					int index = Collections.binarySearch(list, new StackViewChangeCollection(stack, new ArrayList<String>()));
					//System.out.println("pulling out StackViewChangeCollection record at index " + index);
					list.get(index).getViewChanges().add(dateString + "  " + viewData + " [ " + computeFullViews(viewStats) + " ]\n");
					if (trackingMap.get(stack).longValue() > new Long(extractViewId(viewData))){
						list.get(index).getViewChanges().add("\n" + REPORT_INDENT_THREE + "****** Detected a full core group restart for stack : " + stack + " at " + dateString + " ******\n\n");
						
					}
					trackingMap.put(stack, new Long(extractViewId(viewData)));
										
				} else {
					//first time we've seen this stack
					//System.out.println("First time we've come across stack " + stack);
					StackViewChangeCollection svcc = new StackViewChangeCollection(stack, new ArrayList<String>());
					svcc.getViewChanges().add(dateString + "  " + viewData + " [ " + computeFullViews(viewStats) + " ]\n");
					list.add(svcc);
					trackingMap.put(stack, new Long(extractViewId(viewData)));
					//System.out.println("Added new record for stack " + stack);
				}
				
				
				
			} else {
				//not the right type of message for this analysis method
			}
			
		}
		
		
		analysisBuffer.append(createReportHeader("View Change Summary"));
		analysisBuffer.append("\nView state legend : C=complete view, I=incomplete view, A=asymmetrical view (not all connected)\n");
		for (StackViewChangeCollection vc : list){
			analysisBuffer.append(".\n.\n.\n" + REPORT_INDENT_ONE + "Stack : "+ vc.getStackName() + " (stack type=" + detectStackType(vc.getStackName()) + ")  ( " + vc.getViewChanges().size() + " changes found ) \n");
			for (String change : vc.getViewChanges()){
				analysisBuffer.append(REPORT_INDENT_TWO + change);
			}
			
			
		}

		analysisBuffer.append(createReportFooter());
		
	}
	
	
	
	/**
	 * A convenience method for creating a report section header
	 * @param title - the section title
	 * @return - a formatted String
	 */
	private String createReportHeader(String title){
		StringBuffer buffer = new StringBuffer();
		
		buffer.append("\n" + HEADER_LINE + "\n");
		buffer.append("-=[ " + title + " ]=-" + "\n");
		buffer.append(HEADER_LINE + "\n");
		return buffer.toString();
		
	}
	
	/**
	 * A convenience method for creating a report section footer
	 * @return - a formatted String
	 */
	private String createReportFooter(){
		return HEADER_LINE + "\n.\n.\n.\n";
	}
	
	/**
	 * This internal method is used by the summarizeViewChanges() method to compute the status of the view at each view change
	 * The method will provide a status of Complete, Incomplete, or Asymmetrical or a combination of one or more status as the 
	 * case requires. 
	 * @param viewStats - the formatted String from WebSphere in the form (AV=x,CD=x,CN=x,DF=x) 
	 * @return - a String encoding the view state
	 */
	private String computeFullViews(String viewStats){
		String available, denied, connected, defined = null;
		StringBuffer buffer = new StringBuffer();
		available = viewStats.substring(viewStats.indexOf("AV=")+3, viewStats.indexOf(", CD="));
		denied = viewStats.substring(viewStats.indexOf("CD=")+3, viewStats.indexOf(", CN="));
		connected = viewStats.substring(viewStats.indexOf("CN=")+3, viewStats.indexOf(", DF="));
		defined = viewStats.substring(viewStats.indexOf("DF=")+3, viewStats.length());
		if (!available.equals(connected) || !denied.equals(connected)){
			buffer.append(COMPUTE_ASYMMETRIC_VIEW);
			
		}
		if (!available.equals(defined) || !connected.equals(defined)){
			buffer.append(COMPUTE_PARTIAL_VIEW);
			
		}else {
			buffer.append(COMPUTE_FULL_VIEW);
			
		}
		
		return buffer.toString();
	}
	
	/**
	 * A simple method that determines the type of core group stack that is referenced in a log entries (core or data)
	 * @param stackName - the name of the core group stack
	 * @return - coreStack or dataStack
	 */
	private String detectStackType(String stackName){
		if (stackName.endsWith(".P")){
			return "dataStack";
		} else {
			return "coreStack";
		}
	}
	
	/**
	 * An internal method used to extract the view serial number from a log entry
	 * @param s - the input String value
	 * @return - the view serial number (view ID)
	 */
	private String extractViewId(String s) {
		String viewId;
		int index1 = s.indexOf("(");
		int index2 = s.indexOf(")")+1;
		viewId = s.substring(index1, index2);
		viewId = viewId.substring(1, viewId.indexOf(":"));
		return viewId;
	}
	
	private void summarizeCoreGroupBridgeStability(String[] entries) {
		analysisBuffer.append(createReportHeader("Core Group Bridge Stability"));
		boolean isABridge = false;
		double stable = 0;
		double unstable = 0;
		double total = 0;
		for (String entry : entries){
			String dateString = entry.substring(0, 25);
			if (entry.indexOf("CWRCB0107I") >= 0 || entry.indexOf("CWRCB0108I") >=0){ 
				if (entry.indexOf("CWRCB0107I") >= 0){
					//means the bridge is stable
					
					analysisBuffer.append(REPORT_INDENT_ONE + "At " + dateString + " the core group bridge service reports stability in the APG\n");
					isABridge = true;
					stable++;
					total++;
				}
				if (entry.indexOf("CWRCB0108I") >= 0){
					//means the bridge in unstable
					
					analysisBuffer.append(REPORT_INDENT_ONE + "At "  + dateString + " the core group bridge server reports ***instability*** in the APG\n");
					isABridge = true;
					unstable++;
					total++;
				}
				
			} else {
				
			}
		}
		if (!isABridge){
			analysisBuffer.append("This process does not appear to be a core group bridge.");
		}
		
		analysisBuffer.append("\n");
		analysisBuffer.append("Summary-- Stable %: " + (stable/total *100) + " Unstable %: " + (unstable/total *100) + "\n");
		analysisBuffer.append(createReportFooter());
		
	}



	private void summarizeViewJoinProblems(String[] entries) {
		analysisBuffer.append(createReportHeader("View Join Problems"));
		HashMap<String, Integer> stats = new HashMap<String, Integer>();
		for (String entry : entries){
			if (entry.indexOf("DCSV8030I") >= 0){
				//handle HMGR msgs
				String dateString = entry.substring(0, 25);
				String joining_Server = entry.substring(entry.indexOf("at Member ")+10, entry.indexOf(": F"));
				String view_server = entry.substring(entry.indexOf("[")+1, entry.indexOf("]"));
				String reason = entry.substring(entry.indexOf("The reason is ")+14, entry.length());
				if (stats.containsKey(joining_Server)){
					Integer i = new Integer(stats.get(joining_Server).intValue() + 1);
					stats.put(joining_Server, i);
				} else {
					stats.put(joining_Server, new Integer(1));
				}
				analysisBuffer.append(REPORT_INDENT_ONE + "At " + dateString.trim() + " server " + joining_Server + " tried to join with server " + view_server + " but failed because : " + reason + "\n");
			} else {
				//skip it, don't care
			}
		
		}
		//provide a helpful link to a support technote discussing the cause for these messages
		analysisBuffer.append("\nSummary of join problems: \n");
		Set<String> set = stats.keySet();
		for (String n : set){
			analysisBuffer.append(REPORT_INDENT_ONE + "Server: " + n + " - failed joins: " + stats.get(n).intValue() + "\n");
		}
		
		analysisBuffer.append("\nPlease review these links for possible resolution steps:\n");
		analysisBuffer.append(REPORT_INDENT_ONE + "Technote -- http://www-01.ibm.com/support/docview.wss?uid=swg21245012\n");
		analysisBuffer.append(REPORT_INDENT_ONE  + "APAR-PK27434 -- http://www-01.ibm.com/support/docview.wss?uid=swg1PK27434\n");
		analysisBuffer.append(REPORT_INDENT_ONE + "APAR-PK81240 -- http://www-01.ibm.com/support/docview.wss?uid=swg1PK81240 \n");
		analysisBuffer.append(createReportFooter());
		
	}
	
	
	/*
	 * (non-Javadoc)
	 * @see com.ibm.serviceability.client.LogAnalysisService#fetchExport(java.lang.String)
	 * A complex method that combines several pieces of logic from the original CGVAT desktop client. This method fetches the coregroupbridge.xml
	 * file from the dir path given to it, reads the contents of the file, noting the core group names seen there, and then fetches the coregroup.xml
	 * files for each of the core groups it saw. From this point, it collates the xml documents together into a single export file (formerly cgvat.xml)
	 * and sends the XML content back to the UI as a String. 
	 */
	public void fetchExport(String path) throws Exception {
		System.out.println("Parsing config data at " + path + ", please wait a moment...");
		XPathFactory xPathFactory = XPathFactory.newInstance();
		XPath xp = xPathFactory.newXPath();

		List<String> cgNames = new ArrayList<String>();

		/**
		 * We need to get the coregroupbridge.xml file, then figure out how many diff core groups
		 * there are to get and then go get the coregroup.xml files from those places and lastly, provide the files
		 */

		//get the cgbs file
		cgbs = convertStreamToXMLDocument(new FileInputStream(path + "/" + CORE_GROUP_BRIDGE_FILE));
		

		//get the coregroup names from the coreGroupAccessPoints tags coreGroup attribute
		NodeList coregroup =  createXPathQuery(xp, "//coreGroupAccessPoints", cgbs);
		for (int i=0;i< coregroup.getLength();i++){
			String cgName = coregroup.item(i).getAttributes().getNamedItem("coreGroup").getTextContent();
			if (!cgNames.contains(cgName)){
				cgNames.add(cgName);
				//System.out.println("added cgname " + cgName);
			}else {
				//already there
			}
		}

		//now go get the coregroup file(s)

		cgs = new Document[cgNames.size()];
		int count = 0;
		for (String name : cgNames){
			cgs[count] = convertStreamToXMLDocument(new FileInputStream(path + "/coregroups/" + name + "/" + CORE_GROUP_FILE));					
			/*
			 * BUG FIX: 130645
			 */
			count++;
			/*
			 * END FIX
			 */
		}

		//feed all the xml Docs to the good ole CGVAT preprocessor code and let it generate a single Doc
		//which will then be converted to a String and sent back to the client side.
		processAllDocs(cgbs, cgNames ,cgs);
		
		
	}
	
	/**
	 * An internal method used by fetchExport() to mash all the collected XML data into the cgvat export document
	 *  
	 * @param cgbs - the XMl Document object for the coregroupbridge.xml file
	 * @param cgNames - a list of core group names seen in the coregroupbridge.xml file
	 * @param cgs - a Document array representing the coregroup.xml files for each core group seen
	 * 
	 */
	private void processAllDocs(Document cgbs, List<String> cgNames, Document[] cgs) {
		if (exportBuffer == null){
			exportBuffer = new StringBuffer();
		}
		//all the files are in - time to begin linking them up and dump out a single XML file for export
		exportBuffer.append("WHAAT Configuration Report\n");
		exportBuffer.append(HEADER_LINE + "\n");
		
		
		//prepare for the impending XPath queries
		XPathFactory xPathFactory = XPathFactory.newInstance();
		XPath xp = xPathFactory.newXPath();
		
		
		NodeList apgResults = createXPathQuery(xp, "//accessPointGroups", cgbs);
		
		//put together the APG > CGAP > BI cluster
		for (int i=0;i<apgResults.getLength();i++){
			String name = apgResults.item(i).getAttributes().getNamedItem("name").getTextContent();
			String CGAPRefs = apgResults.item(i).getAttributes().getNamedItem("coreGroupAccessPointRefs").getTextContent();
			exportBuffer.append(createReportHeader("Access Point Group: " + name));
			
			//split up the CGAP references and go find the info for each one in cgbs, creating a new CGAP element each time (under the APG)
			String[] cgaps = CGAPRefs.split(" ");
			for (int j=0;j<cgaps.length;j++){
				NodeList cgapList = createXPathQuery(xp, "//coreGroupAccessPoints[@id='" + cgaps[j] + "']", cgbs);
				NodeList biList = createXPathQuery(xp, "//coreGroupAccessPoints[@id='" + cgaps[j] + "']/bridgeInterfaces", cgbs);
				for (int k=0;k<cgapList.getLength();k++){
					
					exportBuffer.append("Core Group Access Point : " + cgapList.item(k).getAttributes().getNamedItem("name").getTextContent() + "\n");
					exportBuffer.append(REPORT_INDENT_ONE + "Core Group: " + cgapList.item(k).getAttributes().getNamedItem("coreGroup").getTextContent() + "\n");
					
					for (int l=0;l<biList.getLength();l++){
						exportBuffer.append(REPORT_INDENT_TWO + "Bridge Interface(s): "+ biList.item(l).getAttributes().getNamedItem("server").getTextContent() + " on node : " + biList.item(l).getAttributes().getNamedItem("node").getTextContent() + "\n");
						
					}
				}
				
			}
			
		}
		//end APG > CGAP > BI cluster
			
		exportBuffer.append(createReportFooter());
		exportBuffer.append(createReportHeader("Core Groups"));
		
		//begin CG > Member > State cluster
			
			for (int i=0;i<cgs.length;i++){
				NodeList memberList = createXPathQuery(xp, "//coreGroupServers", cgs[i]);
				NodeList customPropertyList = createXPathQuery(xp, "//customProperties", cgs[i]);
				exportBuffer.append("\nCore Group: " + cgNames.get(i) + "\n");
				
				//output any and all custom properties defined on the core group
				for (int a=0;a<customPropertyList.getLength();a++){
					exportBuffer.append(REPORT_INDENT_ONE + "Custom Property: \n");
					exportBuffer.append(REPORT_INDENT_TWO + "Name: " + customPropertyList.item(a).getAttributes().getNamedItem("name").getTextContent() + "\n"); 
					exportBuffer.append(REPORT_INDENT_TWO + "Value<: " + customPropertyList.item(a).getAttributes().getNamedItem("value").getTextContent() + "\n");
					
				}
				
				
				
				//output all of the members for the core group
				for (int a=0;a<memberList.getLength();a++){
					exportBuffer.append(REPORT_INDENT_ONE + "Member\n");
					exportBuffer.append(REPORT_INDENT_TWO + "Name: " + memberList.item(a).getAttributes().getNamedItem("serverName").getTextContent() + "\n");
					exportBuffer.append(REPORT_INDENT_TWO + "Node: " + memberList.item(a).getAttributes().getNamedItem("nodeName").getTextContent() + "\n");
					
				}
				
				
			}
			
			//end CG > Member > State cluster
			exportBuffer.append(createReportFooter());
			
		
	}
	
	private static NodeList createXPathQuery(XPath xp, String string, Object document) {
		try {
			XPathExpression expression = xp.compile(string);
			NodeList result =  (NodeList)expression.evaluate(document, XPathConstants.NODESET);
			return result;
			
		} catch (XPathExpressionException e) {
			e.printStackTrace();
		}
		return (NodeList) new Object();
	}
	
	
	/**
	 * A factory-type method that is designed to encapsulate all the logic required to take a GetMethod object, invoke it
	 * and parse the response body into a Document object suitable for manipulation by other work methods
	 * @param get - the created GetMethod object (already created and told where and what to request)
	 * @return - an XML Document object
	 */
	private Document convertStreamToXMLDocument(InputStream is) {
		DocumentBuilder db = null;
		Document doc = null;
		try {
			//System.out.println("got response as stream");
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			db = dbf.newDocumentBuilder();
			//System.out.println("setup the XML parsers");
			doc= db.parse(is);
			
		} catch (ParserConfigurationException e) {

			e.printStackTrace();
		} catch (SAXException e) {

			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try { is.close(); } catch (Exception e) {}; //swallow this, nobody cares
		}
		//System.out.println("returning the parsed doc");
		return doc;
		
	}
	
	/*
	 * (non-Javadoc)
	 * @see com.ibm.serviceability.client.LogAnalysisService#provideMessageDetail(java.lang.String)
	 * A work method that is called by the UI on a single log entry in the listing, returns a layman's-terms 
	 * description of the content in the message it is given. 
	 * Note: all of the translations for the messages are contained in a file called messages_en_US.properties (they can be translated to other
	 * languages, too).
	 */
	public void provideMessageDetail(String messageID) {
		StringBuffer buffer = null;
		ResourceBundle bundle = ResourceBundle.getBundle("com.john.websphere.messages", Locale.getDefault());
		//System.out.println("Log Analyzer Engine is going to provide analysis on this string [" + input + "]");
		buffer = new StringBuffer();
		buffer.append("Providing details for the message ID : " + messageID + "\n");
		buffer.append("------------------------------------------------------ \n");
		Enumeration<String> e = bundle.getKeys();
		String element = null;
		String analysis = null;
		int numberOfHits = 0;
		while (e.hasMoreElements()){
			element = e.nextElement();
			if (messageID.indexOf(element) > -1){
				//System.out.println("hit for " + element);
				numberOfHits++;
				analysis = bundle.getString(element);
				buffer.append("[" + element + "]" + "::" + analysis + "\n\n");
			} else {
				
			}
		}
		
		if (numberOfHits == 0){
			buffer.append("No analysis could be done.");
		}
		
		System.out.println( buffer.toString());
	}
	
	

}
