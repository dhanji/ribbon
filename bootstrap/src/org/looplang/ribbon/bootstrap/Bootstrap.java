package org.looplang.ribbon.bootstrap;

import loop.Loop;
import loop.LoopCompileException;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 */
public class Bootstrap {
  private static final String HOME = System.getProperty("user.home");
  private static volatile URLClassLoader classLoader;
  private static volatile Method method;
  private static final Set<String> MAVEN_REPOS = new LinkedHashSet<String>();

  private static final Set<String> COMMANDS = new HashSet<String>();

  static {
    COMMANDS.add("config");
    COMMANDS.add("init");
    COMMANDS.add("add");
    COMMANDS.add("remove");
    COMMANDS.add("redep");
  }

  public static void main(String[] args) throws Exception {
    // Everything needs yaml so just include it by default.
    addDepToClasspath("org.yaml:snakeyaml:1.10");

    Map<String, Object> config = readRibbonYml();

    // Allow user to configure custom maven repos
    Object repositories = config.get("repositories");
    if (null != repositories) {
      if (repositories instanceof Collection)
        MAVEN_REPOS.addAll((Collection<? extends String>) repositories);
      else
        MAVEN_REPOS.add(repositories.toString());
    }

    if (args.length > 0 && COMMANDS.contains(args[0])) {
      runCommand(args[0], args);

      // Add basic deps if this is an init command.
      if ("init".equals(args[0])) {
        runCommand("add", "add", "org.looplang.ribbon:ribbon-web:1.0");
      }

      // Rebuild classpath if necessary.
      if (!"config".equals(args[0]))
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
        addJarPathToClasspath(new File(dep));
    }

    Object main = config.get("entry_point");
    if (main != null)
      try {
        Loop.run(main.toString() + ".loop", args);
      } catch (LoopCompileException e) {
        // Suppress as it is already printed
        System.err.println("ribbon: abnormal exit");
      }
    else
      // Run the ribbon web app!
      Class.forName("org.looplang.ribbon.Ribbon")
          .getDeclaredMethod("start")
          .invoke(null);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> readRibbonYml() throws IOException {
    // Read configuration from ribbon.yml
    Yaml y = new Yaml();
    FileReader io = new FileReader("ribbon.yml");
    try {
      return (Map<String, Object>) y.load(io);

    } finally {
      io.close();
    }
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

    addJarPathToClasspath(jarFile);
  }

  private static void addJarPathToClasspath(File jarFile)
      throws NoSuchMethodException, IllegalAccessException, InvocationTargetException,
      MalformedURLException {
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
    String repo = toRepoString();

    System.out.print("   resolving...");
    String command = "mvn dependency:get -Dartifact=" + dep +
        " -DrepoUrl=" + repo +
        " --batch-mode";
    Process process = Runtime.getRuntime().exec(command);

    if (process.waitFor() == 0)
      System.out.println("ok");
    else {
      System.out.println("failed");
      System.out.println(command);
      System.out.println();
      BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
      while (br.ready())
        System.out.println(br.readLine());
      System.exit(1);
    }
  }

  private static String toRepoString() {
    return MAVEN_REPOS.toString().replaceAll("[\\[\\] ]", "");
  }

  public static void buildClasspath(String pomFile, String to) throws Exception {
    System.out.print("   analyzing...");
    String command = "mvn dependency:build-classpath --file=" + pomFile
        + " -Dmdep.outputFile="
        + to
        + " -DrepoUrl=" + toRepoString()
        + " --batch-mode";
    Process process = Runtime.getRuntime().exec(command);

    if (process.waitFor() == 0)
      System.out.println("ok");
    else {
      System.out.println("failed");
      System.out.println(command);
      System.out.println();
      BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
      while (br.ready())
        System.out.println(br.readLine());
      System.exit(1);
    }
  }
}
