package app;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

import net.codejava.excel.SimpleExcelWriterExample;

public class Analyzer {
	private static final String HEADER = "Name\tLock Range\tTag Range\tTrue Range\tAI Range\tODF\tDPS_N\tDPS_L\tDPS_H\tDPS_S\tDPS_D\tDPS_A\tDPA_N\tDPA_L\tDPA_H\tDPA_S\tDPA_D\tDPA_A";

	DecimalFormat df = new DecimalFormat("###.#");

	List<List<Object>> bookData = new LinkedList<>();

	ArrayList<String> dirs = new ArrayList<String>();
	List<String> mortarTypes = Arrays.asList(new String[] { "grenade", "bouncebomb", "splintbm", "spraybomb" });
	List<String> popperTypes = Arrays.asList(new String[] { "radarpopper" });
	String topDirectory = "";

	public Analyzer() {
		Properties p = new Properties();
		try {
			p.load(new FileReader("app.properties"));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		topDirectory = p.getProperty("topDirectory");

		scanArmo(p.getProperty("armoFilepath"));
		
		try {
			SimpleExcelWriterExample.write(bookData, "weps.xlsx");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void scanArmo(String armoFilepath) {
		bookData.add(new ArrayList<>(Arrays.asList(HEADER.split("\t"))));

		String line = null;
		try (BufferedReader br = new BufferedReader(new FileReader(armoFilepath))) {
			while ((line = br.readLine()) != null) {
				if (line.matches("\\[ArmoryGroup\\d+\\].*")) {
//					System.out.println("Found weapon group " + line.substring(12, line.indexOf(']')));
				}
				if (line.startsWith("buildLabel")) {
					int start = line.indexOf('\"') + 1;
                    String groupName = line.substring(start, line.indexOf('\"', start));
					bookData.add(new ArrayList<>(0));
					bookData.add(new ArrayList<>(Arrays.asList(new String[] { groupName })));
				}
				if (line.startsWith("buildItem")) {
					int start = line.indexOf('\"') + 1;
					String apOdf = line.substring(start, line.indexOf('\"', start));
//					System.out.println("pickup odf: " + apOdf);
					scanApOdf(apOdf);
				}
			}
		} catch (IOException e) {
			System.out.println(armoFilepath + " failed at line\n" + line);
			throw new RuntimeException(e);
		}

	}

	private void scanApOdf(String apOdfName) {
		String apOdfPath;
		try {
			apOdfPath = findOdf(apOdfName);
		} catch (IOException e) {
			throw new RuntimeException("Failed to find " + apOdfName, e);
		}

		try (BufferedReader br = new BufferedReader(new FileReader(apOdfPath))) {
			String line;
			while ((line = br.readLine()) != null) {
				if (line.startsWith("weaponName")) {
					int start = line.indexOf('\"') + 1;
					String combatOdfName = line.substring(start, line.indexOf('\"', start));

					HashMap<String, String> cProps = scanGunOdf(combatOdfName);
					HashMap<String, String> aProps = null;
					String assaultOdfName = cProps.get("altName");
					if (null != assaultOdfName) {
						aProps = scanGunOdf(assaultOdfName);
					}

					printStats(cProps);
					if (null != aProps) {
						printStats(aProps);
					}
				}
			}

		} catch (IOException e) {
			throw new RuntimeException("Failed while scanning ap " + apOdfName, e);
		}
	}

	private HashMap<String, String> scanGunOdf(String odfName) throws IOException {

		HashMap<String, String> props = new HashMap<>();

		Properties p = new Properties();
		try {
			p.load(new FileReader(findOdf(odfName)));
			copyProps(p, props, "leaderName", "wpnName", "isAssault", "salvoCount", "salvoDelay", "altName", "aiRange",
					"objectClass", "shotDelay", "classLabel", "ordName", "lockRange");
			props.put("odfName", odfName);
			props.put("gunType", trimValue(p.getProperty("classLabel")));

			if (props.containsKey("ordName")) {
				scanOrdOdf(props, props.get("ordName"));
			}

			// if it's a tag gun
			if (props.containsKey("leaderName")) {
				HashMap<String, String> leaderProps = new HashMap<>();
				scanOrdOdf(leaderProps, props.get("leaderName"));
				props.put("leaderRange", leaderProps.get("range"));
			}

			// if it's a dispenser (might be a torpedo)
			if ("dispenser".equals(props.get("gunType"))) {
				props.put("dispenserOrd", trimValue(p.getProperty("objectClass")));
				scanOrdOdf(props, props.get("dispenserOrd"));
			}

			calcDps(props);

			return props;
		} catch (Throwable e) {
			throw new IOException("Failed while scanning Gun odf " + odfName, e);
		}
	}

	private void scanOrdOdf(HashMap<String, String> props, String ordOdfName) throws IOException {

		props.put("ordOdf", ordOdfName);
		Properties p = new Properties();
		try {
			p.load(new FileReader(findOdf(ordOdfName)));
			props.put("ordType", trimValue(p.getProperty("classLabel")));
			copyProps(p, props, "ammoCost", "xplBlast", "velocForward", "launchOrd", "lifeSpan", "shotSpeed",
					"lifeSpan", "shotSpeed", "lockRange", "damageValue(N)", "damageValue(L)", "damageValue(H)",
					"damageValue(S)", "damageValue(D)", "damageValue(A)");

			calcRange(props);

			// if torpedo xpl
			if (props.containsKey("xplBlast")) {
				HashMap<String, String> xmlProps = new HashMap<>();
				System.out.println("scanning blast " + props.get("xplBlast") + " for " + ordOdfName);
				scanOrdOdf(xmlProps, props.get("xplBlast"));
				String key;
				for (char armor : "NLHSDA".toCharArray()) {
					key = "damageValue(" + armor + ")";
					props.put(key, xmlProps.get(key));
				}
			}

		} catch (Throwable e) {
			throw new IOException("Failed while scanning ord " + ordOdfName, e);
		}
	}

	private void printStats(HashMap<String, String> props) {
		ArrayList<Object> row = new ArrayList<>();
		row.add(props.get("wpnName"));
		row.add(Objects.toString(props.get("lockRange"), ""));
		row.add(Objects.toString(props.get("leaderRange"), ""));
		row.add(props.get("range"));
		row.add(props.get("aiRange"));
		row.add(props.get("odfName"));

		if (props.containsKey("dpsN")) {
			for (char armor : "NLHSDA".toCharArray()) {
				row.add(Objects.toString(props.get("dps" + armor), ""));
			}
			for (char armor : "NLHSDA".toCharArray()) {
				row.add(Objects.toString(props.get("dpa" + armor), ""));
			}
		}

		bookData.add(row);
	}

	private void calcRange(HashMap<String, String> props) throws IOException {
		double shotSpeed = 0, lifeSpan = 0, range = 0;

		if ("torpedo".equals(props.get("ordType"))) {
			shotSpeed = Double.parseDouble(props.get("velocForward"));
		} else if (props.containsKey("shotSpeed")) {
			shotSpeed = Double.parseDouble(props.get("shotSpeed"));
		}

		if (props.containsKey("lifeSpan")) {
			lifeSpan = Double.parseDouble(props.get("lifeSpan"));
		}

		if (mortarTypes.contains(props.get("ordType"))) {
			range = (shotSpeed * shotSpeed) / 9.8;
		} else if (popperTypes.contains(props.get("ordType"))) {
			HashMap<String, String> launchProps = new HashMap<>();
			scanOrdOdf(launchProps, props.get("launchOrd"));
			range = Double.parseDouble(launchProps.get("range"));
		} else if (shotSpeed > 0) {
			range = shotSpeed * lifeSpan;
		}

		if (range > 0) {
			props.put("range", df.format(range));
		}
	}

	private void copyProps(Properties p, HashMap<String, String> props, String... propNames) {
		for (String propName : propNames) {
			if (p.containsKey(propName)) {

				String val = trimValue(p.getProperty(propName));

				if (!"NULL".equals(val.toUpperCase())) {
					props.put(propName, val);
				}
			}
		}
	}

	private String trimValue(String orig) {
		String val = orig;

		if (val.contains("\"")) {
			int begin = orig.indexOf("\"") + 1;
			val = val.substring(begin, orig.indexOf("\"", begin));
		}

		if (val.indexOf("//") > 0) {
			val = val.substring(0, val.indexOf("//"));
		}

		return val.trim();
	}

	private void calcDps(HashMap<String, String> props) {

		if (props.containsKey("shotDelay")) {

			Double ammoCost = props.containsKey("ammoCost") ? Double.parseDouble(props.get("ammoCost")) : null;
			Double shotDelay = Double.parseDouble(props.get("shotDelay"));
			Double salvoDelay = props.containsKey("salvoDelay") ? Double.parseDouble(props.get("salvoDelay")) : null;
			Double salvoCount = props.containsKey("salvoCount") ? Double.parseDouble(props.get("salvoCount")) : null;
			Double damage, dps, dpa;

			for (char armor : "NLHSDA".toCharArray()) {
				if (props.containsKey("damageValue(" + armor + ")")) {

					damage = Double.parseDouble(props.get("damageValue(" + armor + ")"));
					if (null != salvoCount) {
						// if salvo, (damage * salvoCount) / (salvoDelay*salvoCount + shotDelay)
						dps = (damage * salvoCount) / (salvoDelay * salvoCount + shotDelay);
					} else {
						// otherwise damage / shotDelay
						dps = damage / shotDelay;
					}
					props.put("dps" + armor, df.format(dps));

					if (null != ammoCost) {
						dpa = damage / ammoCost;
						props.put("dpa" + armor, df.format(dpa));
					}
				}
			}
		}
	}

	String searchDirsForOdf(String dir, String odfName) {
		File odfFile = new File(dir, odfName + ".odf");
		if (odfFile.exists()) {
			return dir;
		} else {
			String[] directories = new File(dir).list(new FilenameFilter() {
				@Override
				public boolean accept(File current, String name) {
					return new File(current, name).isDirectory();
				}
			});
			if (null != directories) {
				for (String sub : directories) {
					String subOdf = searchDirsForOdf(new File(dir, sub).getAbsolutePath(), odfName);
					if (null != subOdf) {
						return subOdf;
					}
				}
			}
		}
		return null;
	}

	private String findOdf(String odfName) throws IOException {
		File odfFile;
		for (String dir : dirs) {
			odfFile = new File(dir, odfName + ".odf");
			if (odfFile.exists()) {
				return odfFile.getAbsolutePath();
			}
		}
		String newDir = searchDirsForOdf(topDirectory, odfName);
		if (null != newDir) {
//			System.out.println(odfName + " is in a new folder " + newDir);
			dirs.add(newDir);
			return new File(newDir, odfName + ".odf").getAbsolutePath();
		}
		throw new IOException("found no odf file for " + odfName);
	}

	public static void main(String[] args) {
//		Properties p = new Properties();
//		p.setProperty("armoFilepath", "C:\\Users\\Mike\\Downloads\\bzccmm\\steamcmd\\steamapps\\workshop\\content\\624970\\2343997862\\BZTC_Races_1\\BZTC_ODF_RACES\\B_odf_black_dogs_squadron\\bbarmo.odf");
//		try {
//			p.store(new FileWriter("app.properties"), "notes");
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		new Analyzer();
	}
}
