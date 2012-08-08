package org.looplang.ribbon.bootstrap;

import loop.Loop;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 */
public class Bootstrap {
  private static final String HOME = System.getProperty("user.home");
  private static volatile URLClassLoader classLoader;
  private static volatile Method method;

  public static void main(String[] args) throws Exception {
    // Load yml and grep the deps.
    File yaml = new File("ribbon.yml");
    if (!yaml.exists()) {
      yaml = new File(args[0] + ".yml");
      if (!yaml.exists()) {
        System.out.println("Not a valid ribbon project. Please run: 'ribbon init <project>'");
        System.exit(1);
      }
    }

    List<String> deps = new ArrayList<String>();
    BufferedReader reader = new BufferedReader(new FileReader(yaml));
    boolean inDeps = false;
    while (reader.ready()) {
      String line = reader.readLine();
      if (line.equals("deps:")) {
        inDeps = true;
        continue;
      }

      if (inDeps) {
        if (line.isEmpty() || !line.startsWith("  - "))
          break;
        deps.add(line.substring("  - ".length()).trim());
      }
    }

    for (String dep : deps) {
      addDepToClasspath(dep);
    }

    // Run the command line arg as a loop file.
    Loop.run(args[0] + ".loop", args);
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

    System.out.print("Resolving " + dep + "...");
    Process process = Runtime.getRuntime().exec("mvn dependency:get -Dartifact=" + dep +
        " -DrepoUrl=" + repo +
        " --batch-mode");

    if (process.waitFor() == 0)
      System.out.println("OK");
    else {
      BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
      while (br.ready())
        System.out.println(br.readLine());
    }
  }
}
