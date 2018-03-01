package com.hpe.nonstop.util;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap; // Ravi: Support for DEFINEs.
import java.util.concurrent.TimeUnit;

/**
 * <b>(C) Copyright [2018] Hewlett Packard Enterprise Development LP.</b>
 * @author NED, Hewlett Packard Enterprise
 *
 */
public class TSMPBuilderFactory {
	public static class Pathmon {
		public int MAXASSIGNS = 10;
		public int MAXDEFINES = 8191;
		public int MAXEXTERNALTCPS = 0;
		public int MAXLINKMONS = 16;
		public int MAXPARAMS = 100;
		public int MAXPATHCOMS = 5;
		public int MAXSERVERCLASSES = 10;
		public int MAXSERVERPROCESSES = 160;
		public int MAXSPI = 10;
		public int MAXSTARTUPS = 0;
		public int MAXTERMS = 10;
		public int MAXTCPS = 10;

		public int PRIMARY_CPU = 0;
		public int BACKUP_CPU = 1;

		String PNAME = "PM";

		// name : pathmon name without '$'
		public Pathmon(String name) {
			PNAME = name;
		}

		// Indicates if the pathmon is started and configured
		public Boolean isPathmonConfigured() throws Exception {
			return TACLUtilities.isProcessPair(PNAME);
		}

		// Indicates if the pathmon is started by not configured
		public Boolean isPathmonStarted() throws Exception {
			return TACLUtilities.isProcessRunning(PNAME);
		}

		public void startAndConfigurePathmon() throws Exception {
			if (isPathmonConfigured())
				return;

			if (isPathmonStarted() == false) {
				startPathmon();
			}
			configurePathmon();
		}

		public static String executePathcomCommands(List<String> commands) throws Exception {
			ProcessBuilder pb = new ProcessBuilder("gtacl", "-p", "pathcom");
			pb.redirectErrorStream(true);

			Process proc = pb.start();

			OutputStreamWriter osw = new OutputStreamWriter(proc.getOutputStream());

			InputStream pis = proc.getInputStream();
			byte[] b = new byte[1024];
			StringBuilder fullOutput = new StringBuilder();
			StringBuilder partOutput = new StringBuilder(); // Error message
															// may span
															// multiple
															// lines
			int cmdcount = 0;
			try {
				while (true && (cmdcount < commands.size())) {
					int read = pis.read(b);
					if (read == -1) {
						// EOF
						break;
					}
					String tmp = new String(b, 0, read);
					partOutput.append(tmp);
					fullOutput.append(tmp);
					System.out.print(tmp);
					if (b[read - 1] == '=') {
						// Check for errors after executing the first
						// command
						if (cmdcount > 0) {
							if (partOutput.toString().contains("ERROR")) {
								osw.write("EXIT\n");
								osw.flush();
								throw new RuntimeException(partOutput.toString());
							}
						}
						partOutput.delete(0, partOutput.length());
						String command = commands.get(cmdcount) + "\n";
						System.out.print(command);
						osw.write(command);
						osw.flush();
						cmdcount++;
					}
				}
			} finally {
				proc.waitFor(1, TimeUnit.SECONDS);
				if (proc.isAlive())
					proc.destroy();
			}
			return fullOutput.toString();
		}

		// Private Functions

		private void startPathmon() throws Exception {
			Object[] ret = TACLUtilities.executeCommand("gtacl", "-nowait", "-name", "/G/" + PNAME, "-cpu",
					String.valueOf(PRIMARY_CPU), "-term", "/G/zhome", "-p", "/G/system/system/pathmon",
					String.valueOf(BACKUP_CPU));
			if (((Integer) ret[0]) != 0) {
				throw new RuntimeException(ret[1].toString());
			}

		}

		private void configurePathmon() throws Exception {
			List<String> commands = new ArrayList<String>();
			{
				commands.add("OPEN $" + PNAME);
				commands.add("SET PATHWAY MAXASSIGNS " + MAXASSIGNS);
				commands.add("SET PATHWAY MAXDEFINES " + MAXDEFINES);
				commands.add("SET PATHWAY MAXEXTERNALTCPS " + MAXEXTERNALTCPS);
				commands.add("SET PATHWAY MAXLINKMONS " + MAXLINKMONS);
				commands.add("SET PATHWAY MAXPARAMS " + MAXPARAMS);
				commands.add("SET PATHWAY MAXPATHCOMS " + MAXPATHCOMS);
				commands.add("SET PATHWAY MAXSERVERCLASSES " + MAXSERVERCLASSES);
				commands.add("SET PATHWAY MAXSERVERPROCESSES " + MAXSERVERPROCESSES);
				commands.add("SET PATHWAY MAXSPI " + MAXSPI);
				commands.add("SET PATHWAY MAXSTARTUPS " + MAXSTARTUPS);
				commands.add("SET PATHWAY MAXTERMS " + MAXTERMS);
				commands.add("SET PATHWAY MAXTCPS " + MAXTCPS);

				commands.add("SET PATHMON BACKUPCPU " + BACKUP_CPU);
				commands.add("START PATHWAY COLD !");
				commands.add("EXIT");
			}
			Pathmon.executePathcomCommands(commands);
		}
	}

	public static class Serverclass {
		public String PROCESSTYPE = "OSS";
		public String ARGLIST = null;
		public int AUTORESTART = 10;
		public int[][] CPUS = { { 0, 1 } };
		public File CWD = null;
		public String[][] ENV = { { "NAME", "VALUE" } };
		public String HOMETERM = "$ZHOME";
		public int LINKDEPTH = 4;
		public int MAXLINKS = 16;
		public int MAXSERVERS = 1;
		public int NUMSTATIC = 1;
		public String[] PROCESS = { "$CRD1" };
		public File PROGRAM = null;
		public File STDOUT = null;
		public File STDERR = null;

		
		// The list of DEFINEs to be set on the server.
		// This value can be specified in two ways:
		// 1) In the config file using the DEFINE property.
		// 2) On the command line using the tsmp.server.define property.
		//
		// In both cases, multiple defines can be specified by enclosing each
		// define in parenthesis and separating the entries using commas:
		// DEFINE=(define-spec1),(define-spec2),...
		// define-spec: define-name, define-attribute-spec
		// define-name: A valid name to be assigned to the DEFINE.
		// The first character must be an equal sign (=).
		// define-attribute-spec: Comma-separated list of attributes.
		//
		// The parenthesis are optional, if only one define is specified.
		//
		// If a define-name is specified in both the config file as well as the
		// command line, the command line attributes will be used.
		//
		// Example:
		// DEFINE=(=_SQLMX_SMD_LOCATION, class defaults, volume $MYVOL.ZSD0),
		// (=_MX_CMP_PROG_FILE_NAME, class map, file $MYVOL.MYSUBVOL.MXCMP)
		private HashMap<String, String> DEFINES = new HashMap<String, String>();

		private String SVCNAME = null;

		public Serverclass(String svcName) {
			SVCNAME = svcName;
		}

		public void configureAndStartInPathmon(Pathmon pmon) throws Exception {
			List<String> config = new ArrayList<String>();
			config.add("OPEN $" + pmon.PNAME);
			config.addAll(getConfiguration());
			config.add("ADD SERVER " + SVCNAME);
			config.add("EXIT");
			try {
				Pathmon.executePathcomCommands(config);
			} catch (Exception ex) {
				if (!ex.getMessage().contains("ENTRY ALREADY EXISTS"))
					throw ex;
			}

			config.clear();
			config.add("OPEN $" + pmon.PNAME);
			config.add("START SERVER " + SVCNAME);
			config.add("EXIT");
			Pathmon.executePathcomCommands(config);
		}

		public void addDefines(String defines) {
			if (defines != null) {
				// Multiple defines might be specified as a comma-separated
				// list, with each entry enclosed in parenthesis. Strip the
				// parenthesis, commas and also any surrounding whitespace.
				final String delimiters = "\\s*((\\()|(\\)(\\s*,)?))\\s*";

				String[] definesList = defines.split(delimiters);

				if (definesList.length > 1) {
					// The specification is enclosed in parenthesis.
					// In this case, every alternate entry in definesList will
					// be an empty string, starting with the very first entry:
					// definesList[0] = <empty>
					// definesList[1] = <define-spec1>
					// definesList[2] = <empty>
					// definesList[3] = <define-spec2>
					// ...
					//
					// This is because the delimiter pattern includes the
					// left parenthesis.
					for (int i = 1; i < definesList.length; i += 2)
						// Leading and trailing whitespace is already trimmed.
						addDefine(definesList[i]);
				} else
					addDefine(definesList[0].trim());
			}

			return;
		}

		public void addDefine(String define) {
			String[] defineNameAndAttrs = define.split(",", 2);

			// Sanity check. Ignore invalid specifications.
			if ((defineNameAndAttrs.length == 2) && (defineNameAndAttrs[0].charAt(0) == '='))
				DEFINES.put(defineNameAndAttrs[0], define);

			return;
		}

		// Returns pathcom commands to configure the serverclass
		private List<String> getConfiguration() {
			final int MAX_PER_LINE = 120; // max chars per line of command
			List<String> config = new ArrayList<String>();
			config.add("SET SERVER PROCESSTYPE " + PROCESSTYPE);
			if ("SET SERVER ARGLIST ".length() + ARGLIST.length() > MAX_PER_LINE) {
				config.add("SET SERVER ARGLIST &");
				String command = ARGLIST;
				String partcommand = "";
				do {
					partcommand = command.substring(0, Math.min(command.length(), MAX_PER_LINE));
					config.add("" + partcommand + "&");
					command = command.substring(partcommand.length());
				} while (command.length() > 0);
				config.add("");
			} else {
				config.add("SET SERVER ARGLIST " + ARGLIST);
			}

			config.add("SET SERVER AUTORESTART " + AUTORESTART);

			{
				StringBuffer cpus = new StringBuffer();
				cpus.append("(");
				for (int i = 0; i < CPUS.length; i++) {
					cpus.append(CPUS[i][0]);
					Object dummy = CPUS[i][1] != -1 ? cpus.append(":").append(CPUS[i][1]).append(",") : null;
				}
				cpus.setCharAt(cpus.length() - 1, ')');

				config.add("SET SERVER CPUS " + cpus.toString());
			}
			config.add("SET SERVER CWD " + (CWD == null ? "null" : CWD.getAbsolutePath()));
			{
				for (int i = 0; i < ENV.length; i++) {
					config.add("SET SERVER ENV " + ENV[i][0] + "=" + ENV[i][1]);
				}
			}
			config.add("SET SERVER HOMETERM " + HOMETERM);
			config.add("SET SERVER LINKDEPTH " + LINKDEPTH);
			config.add("SET SERVER MAXLINKS " + MAXLINKS);
			config.add("SET SERVER MAXSERVERS " + MAXSERVERS);
			config.add("SET SERVER NUMSTATIC " + NUMSTATIC);
			{
				System.out.printf("length[%d], NUMSTATIC[%d], Names[%s]\n", PROCESS.length, NUMSTATIC,
						Arrays.toString(PROCESS));
				System.out.println("Math.min = " + Math.min(PROCESS.length, NUMSTATIC));
				for (int i = 0; i < Math.min(PROCESS.length, NUMSTATIC); i++) {
					config.add("SET SERVER PROCESS " + PROCESS[i]);
				}
			}
			config.add("SET SERVER PROGRAM " + (PROGRAM == null ? "null" : PROGRAM.getAbsolutePath()));
			if (STDOUT != null)
				config.add("SET SERVER STDOUT " + STDOUT.getAbsolutePath());
			if (STDERR != null)
				config.add("SET SERVER STDERR " + STDERR.getAbsolutePath());

			for(String key:DEFINES.keySet()) {
				config.add(DEFINES.get(key));
			}
			// DEFINES.forEach((k, v) -> config.add("SET SERVER DEFINE " + v));

			return config;
		}

	}

	static class TACLUtilities {
		public static final Boolean isProcessRunning(String name) throws Exception {
			Object[] ret = executeCommand("gtacl", "-c", "status $" + name);
			String outmsg = (String) ret[1];
			if (outmsg.contains(name.toUpperCase()))
				return true;
			return false;
		}

		public static final Boolean isProcessPair(String name) throws Exception {
			Object[] ret = executeCommand("gtacl", "-c", "status $" + name);
			String outmsg = (String) ret[1];
			int index1 = outmsg.indexOf(name.toUpperCase());
			if (index1 == -1)
				return false;
			if (index1 != outmsg.lastIndexOf(name.toUpperCase()))
				return true;
			return false;
		}

		// return object array of 2 elements. 1>Exit Code 2> Output text
		public static final Object[] executeCommand(String command, String... args) throws Exception {
			List<String> clist = new ArrayList<String>();
			clist.add(command);
			for (String arg : args) {
				clist.add(arg);
			}
			ProcessBuilder pb = new ProcessBuilder(clist);
			pb.redirectErrorStream(true);
			System.out.println(Arrays.toString(clist.toArray()).replace(',', ' '));
			Process proc = pb.start();
			proc.waitFor();
			int exitValue = proc.exitValue();
			byte[] b = new byte[1024]; // assuming size is sufficient
			int read = proc.getInputStream().read(b);
			if (read == -1) // if EOS then just set it to 0 to
							// facilitate
							// string conversion
				read = 0;
			System.out.println(new String(b, 0, read));
			Object[] ret = new Object[2];
			ret[0] = new Integer(exitValue);
			ret[1] = new String(b, 0, read);
			return ret;
		}
	}
}
