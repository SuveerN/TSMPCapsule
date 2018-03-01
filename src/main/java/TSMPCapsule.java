import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarFile;

import com.hpe.nonstop.util.TSMPBuilderFactory;

/**
 * <p>
 * The user of this caplet should be aware of the application packaging and
 * deploying solution offered by Capsule (http://www.capsule.io). The following
 * is the description of a Caplet from the website.<br/>
 * <blockquote>Caplets are classes that hook into the capsule and modify its
 * behavior. A capsule contains metadata about your application and the means to
 * execute it as a plain JVM program. Caplets can use the metadata in the
 * capsule to launch the application in some more sophisticated
 * ways</blockquote>
 * </p>
 * <p>
 * This caplet overrides the default <code>launch</code> functionality of
 * Capsule, uses the Capsule's meta-data together with TS/MP specific
 * configuration to launch the application in the HPE NonStop Server's TS/MP
 * environment.<br/>
 * Unlike some of the other caplets, the main-class in the jar's manifest should
 * be the full path of this class i.e. <code>TSMPCapsule</code>. This caplet
 * will assume its TS/MP role only if the mode is set to the value
 * <code>TSMP</code>. This makes it easy for the app developers to test the
 * application package in a development environment such as Linux / Windows.
 * <p>
 * One of the ways to configure a generic Capsule is to have the configuration
 * parameters as part of the manifest file in different sections. TSMPCapsule
 * does not employ this method as there is chance of a single capsule jar being
 * used to configure multiple TSMP runtime environments. Instead the capsule
 * loads the configuration from a file. The file itself is in the format
 * specified by <code>java.util.Properties</code>. The following are the keys
 * that this Caplet recognizes.<br/>
 * Properties related to configuring the PATHMON process
 * <ul>
 * <li>PATHMON_NAME : This is the name of the pathmon process without the '$'.
 * Example: CRD
 * <li>PRIMARY_CPU : The CPU on which the primary of the pathmon process should
 * run. Example: 0
 * <li>BACKUP_CPU : The CPU on which the backup of the pathmon process should
 * run. Example: 1
 * </ul>
 * Properties related to configuring the SERVERCLASS process. The details of
 * each attribute can be found in the 'HPE NonStop TS/MP System Management
 * Manual'
 * <ul>
 * <li>AUTORESTART : Value should be a number. Default 10
 * <li>NUMSTATIC : Value should be a number. Default 1
 * <li>MAXSERVERS : Value should be a number. Default 1
 * <li>PROCESS_NAMES : This is a comma separated list of names for the static
 * instances. Example: PRC1,PRC2,PRC3. The names do not contain '$' symbol
 * <li>CPUS : This is a list of CPUs on which the processes should be started.
 * Default {{0,1}}. Following are some examples: Example 1: {{0},{2,3},{4}}.
 * Example 2: {{0,1},{1,2},{2,3},{3,0}}
 * <li>STDOUT : Default "/dev/null"
 * <li>STDERR : Default "/dev/null"
 * <li>DEFINE : This is the list of defines that have to be added to the
 * serverclass configuration. Each define is enclosed in paranthesis and
 * multiple defines have to be separated by comma. No Default. Example:
 * (=_SQLMX_SMD_LOCATION, class defaults, volume
 * $MYVOL.ZSD0),(=_MX_CMP_PROG_FILE_NAME, class map, file $MYVOL.MYSUBVOL.MXCMP)
 * </ul>
 * </p>
 * <p>
 * A single Capsule jar (with TSMP Caplet) will result in configuration of a
 * pathway environment comprising a single <code>PATHMON</code> and a single
 * <code>SERVERCLASS</code>. If multiple <code>SERVERCLASS</code>es are to be
 * configured they have to be done with a Jar per <code>SERVERCLASS</code>.<br/>
 * If the <code>PATHMON</code> in the configuration is already running, the
 * Caplet will try to add <code>SERVERCLASS</code> to the existing
 * <code>PATHMON</code>.
 * </p>
 * <p>
 * <strong>How does the TS/MP behaviour get triggered?</strong> <br/>
 * The TS/MP mode gets triggered when the system property
 * <code>capsule.mode</code> is set to <code>TSMP</code>
 * </p>
 * 
 * <p>
 * <strong>What are the ways to pass configuration file?</strong><br/>
 * The configuration file can be passed using system property
 * <code>tsmp.config.prop</code> or the file can be placed in the META-INF
 * folder of the jar that is being run
 * </p>
 * 
 * <p>
 * <strong>Can it be run on any platform?</strong><br/>
 * No. It has to be run on a NonStop Server ONLY
 * </p>
 * 
 * <p>
 * <strong>Can this caplet configure processes in the Guardian
 * Environment?</strong><br/>
 * No. It can only configure Java processes and hence only in the OSS
 * environment
 * </p>
 * 
 * <p>
 * <strong>Can the caplet be enhanced to spawn/configure any kind of process on
 * NonStop?</strong><br/>
 * Yes. It can be
 * </p>
 * 
 * <b>(C) Copyright [2018] Hewlett Packard Enterprise Development LP.</b>
 * 
 * @author NED, Hewlett Packard Enterprise
 *
 */
public class TSMPCapsule extends Capsule {

	private boolean LOG_ENABLED = true;

	public TSMPCapsule(Capsule pred) {
		super(pred);
		log("Constructor called with", pred.getClass());
	}

	public TSMPCapsule(Path jarFile) {
		super(jarFile);
		log("Constructor called with", jarFile.getClass(), jarFile.toAbsolutePath());
	}

	protected ProcessBuilder prelaunch(List<String> jvmArgs, List<String> args) {
		if (LOG_ENABLED) {
			StringBuffer buf = new StringBuffer();
			buf.append("JVM ARGS=").append(Arrays.toString(jvmArgs.toArray()));
			buf.append("\nARGS=").append(Arrays.toString(args.toArray()));
			log("prelaunch called with\n", buf.toString());
		}
		return super.prelaunch(jvmArgs, args);
	}

	protected int launch(ProcessBuilder pb) throws IOException, InterruptedException {
		String mode = System.getProperty("capsule.mode", "NONE");
		super.log(LOG_VERBOSE, "Mode is " + mode);
		if (mode.compareTo("TSMP") != 0) {
			return super.launch(pb);
		}

		super.log(LOG_VERBOSE, "TSMP Capsule is taking over and configure the application under TSMP");
		Properties props = new Properties();
		if (System.getProperty("tsmp.config.prop") != null)
			props.load(new FileReader(new File(System.getProperty("tsmp.config.prop"))));
		else {
			JarFile jar = new JarFile(super.getJarFile().toFile());
			props.load(jar.getInputStream(jar.getEntry("META-INF/tsmp.config.prop")));
			jar.close();
		}

		TSMPBuilderFactory.Pathmon pmon = new TSMPBuilderFactory.Pathmon(
				props.getOrDefault("PATHMON_NAME", "NONE").toString());
		pmon.BACKUP_CPU = Integer.parseInt(props.getOrDefault("BACKUP_CPU", "1").toString());
		pmon.PRIMARY_CPU = Integer.parseInt(props.getOrDefault("PRIMARY_CPU", "0").toString());
		try {
			pmon.startAndConfigurePathmon();
		} catch (Exception ex) {
			throw new IOException(ex);
		}

		TSMPBuilderFactory.Serverclass svc = new TSMPBuilderFactory.Serverclass(
				props.getOrDefault("SERVERCLASS_NAME", "NONE").toString());
		List<String> args = pb.command();
		StringBuffer arglist = new StringBuffer();
		for (int i = 0; i < args.size(); i++) {
			if (i == 0) {
				svc.PROGRAM = new File(args.get(i));
				continue;
			}
			arglist.append(args.get(i));
			arglist.append(",");
		}
		arglist.deleteCharAt(arglist.length() - 1);
		svc.ARGLIST = arglist.toString();

		svc.CWD = super.getJarFile().getParent().toFile();
		svc.AUTORESTART = Integer.parseInt(props.getOrDefault("AUTORESTART", "10").toString());
		svc.NUMSTATIC = Integer.parseInt(props.getOrDefault("NUMSTATIC", "1").toString());
		svc.MAXSERVERS = Integer.parseInt(props.getOrDefault("MAXSERVERS", "1").toString());

		String pnames = props.getOrDefault("PROCESS_NAMES", "a,b,c").toString();
		ArrayList<String> pnamelist = new ArrayList<String>();
		for (String s : pnames.split(",")) {
			pnamelist.add("$" + s);
		}
		svc.PROCESS = pnamelist.toArray(svc.PROCESS);

		{
			String cpus = (String) props.getOrDefault("CPUS", "{{0,1}}");
			String[] cpu_pairs = cpus.substring(1, cpus.length() - 1).split(";");
			int[][] CPUS = new int[cpu_pairs.length][2];
			for (int i = 0; i < cpu_pairs.length; i++) {
				String[] pair = cpu_pairs[i].substring(1, cpu_pairs[i].length() - 1).split(",");
				CPUS[i][0] = Integer.valueOf(pair[0]);
				CPUS[i][1] = Integer.valueOf(pair.length > 1 ? pair[1] : "-1");
			}
			svc.CPUS = CPUS;
		}
		svc.STDOUT = new File(props.getOrDefault("STDOUT", "/dev/null").toString());
		svc.STDERR = new File(props.getOrDefault("STDERR", "/dev/null").toString());

		svc.addDefines(props.getProperty("DEFINE"));
		svc.addDefines(System.getProperty("tsmp.server.define"));

		try {
			svc.configureAndStartInPathmon(pmon);
		} catch (Exception ex) {
			throw new IOException(ex);
		}
		return 0;
	}

	private final void log(Object... args) {
		if (LOG_ENABLED) {
			StringBuffer buf = new StringBuffer();
			for (Object arg : args) {
				buf.append(arg);
				buf.append(" ");
			}
			super.log(LOG_VERBOSE, buf.toString());
			// System.out.println(buf.toString());
		}
	}
}
