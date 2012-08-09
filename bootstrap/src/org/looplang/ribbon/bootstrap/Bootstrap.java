package org.looplang.ribbon.bootstrap;

import loop.Loop;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 */
public class Bootstrap {
  private static final String HOME = System.getProperty("user.home");
  private static volatile URLClassLoader classLoader;
  private static volatile Method method;

  private static final Set<String> COMMANDS = new HashSet<String>();
  static {
    COMMANDS.add("init");
    COMMANDS.add("add");
    COMMANDS.add("remove");
    COMMANDS.add("redep");
  }

  public static void main(String[] args) throws Exception {
    // Everything needs yaml so just include it by default.
    addDepToClasspath("org.yaml:snakeyaml:1.10");

    if (args.length > 0 && COMMANDS.contains(args[0])) {
      runCommand(args[0], args);

      // Add basic deps if this is an init command.
      if ("init".equals(args[0])) {
        runCommand("add", "add", "org.looplang.ribbon:ribbon-web:1.0");
      }

      // Rebuild classpath if necessary.
      runCommand("redep", "redep");

      System.out.println();
      return;
    }

    // Load yml and grep the deps.
    File yaml = new File("ribbon.yml");
    if (!yaml.exists()) {
      yaml = new File(args[0] + ".yml");
      if (!yaml.exists()) {
        System.out.println("not a valid ribbon project. Please run: 'ribbon init <project>'\n");
        System.exit(1);
      }
    }

    List<String> deps = new ArrayList<String>();
    BufferedReader reader = new BufferedReader(new FileReader("._classpath"));
    while (reader.ready()) {
      String line = reader.readLine();

      deps.addAll(Arrays.asList(line.split(":")));
    }

    for (String dep : deps) {
      if (!dep.isEmpty())
        addDepToClasspath(dep);
    }

    // Run the ribbon web app!
    Class.forName("org.looplang.ribbon.Ribbon")
        .getDeclaredMethod("main", String[].class)
        .invoke(null, args);
  }

  private static void runCommand(String command, String... args) throws Exception {
    // Read deps first (if any)
    InputStream depStream = Bootstrap.class.getResourceAsStream(command + ".deps");
    if (depStream != null) {
      BufferedReader reader = new BufferedReader(new InputStreamReader(depStream));
      while (reader.ready())
        addDepToClasspath(reader.readLine());
    }

    InputStreamReader reader = stream(command);
    Loop.run(command, reader, args);
  }

  public static InputStreamReader stream(String arg) {
    return new InputStreamReader(Bootstrap.class.getResourceAsStream(arg + ".loop"));
  }

  @SuppressWarnings("deprecation")
  public static void addDepToClasspath(String dep) throws Exception {
    String jarPath = toJarPath(dep);

    // Rebuild local repository URL.
    String file = HOME + "/.m2/repository/" + jarPath;

    File jarFile = new File(file);
    if (!jarFile.exists()) {
      // Use Aether to fetch the jar.
      fetchDependency(dep);
    }

    if (classLoader == null) {
      classLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
      method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
      method.setAccessible(true);
    }
    method.invoke(classLoader, jarFile.toURL());
  }

  private static String toPomPath(String dep) {
    return toPath(dep, "pom");
  }

  private static String toJarPath(String dep) {
    return toPath(dep, "jar");
  }

  private static String toPath(String dep, String ext) {
    String[] split = dep.split(":");
    String jar = split[1] + "-" + split[2] + "." + ext;

    return split[0].replace('.', '/')
        + '/'
        + split[1]
        + '/'
        + split[2]
        + '/' + jar;
  }

  private static void fetchDependency(String dep) throws Exception {
    String repo = "http://repo1.maven.org/maven2";

    System.out.print("   resolving...");
    Process process = Runtime.getRuntime().exec("mvn dependency:get -Dartifact=" + dep +
        " -DrepoUrl=" + repo +
        " --batch-mode");

    if (process.waitFor() == 0)
      System.out.println("ok");
    else {
      System.out.println("failed");
      BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
      while (br.ready())
        System.out.println(br.readLine());
    }
  }

  public static void buildClasspath(String pomFile, String to) throws Exception {
    System.out.print("   analyzing...");
    Process process = Runtime.getRuntime().exec("mvn dependency:build-classpath --file=" + pomFile
        + " -Dmdep.outputFile="
        + to
        + " --batch-mode");

    if (process.waitFor() == 0)
      System.out.println("ok");
    else {
      System.out.println("failed");
      BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
      while (br.ready())
        System.out.println(br.readLine());
    }
  }
}
