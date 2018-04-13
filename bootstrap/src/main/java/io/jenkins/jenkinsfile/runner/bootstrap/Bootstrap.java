package io.jenkins.jenkinsfile.runner.bootstrap;

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Kohsuke Kawaguchi
 */
public class Bootstrap {

    /**
     * This system property is set by the bootstrap script created by appassembler Maven plugin
     * to point to a local Maven repository.
     */
    public final File appRepo = new File(System.getProperty("app.repo"));

    /**
     * Exploded jenkins.war
     */
    public final File warDir;

    /**
     * Where to load plugins from?
     */
    public final File pluginsDir;

    /**
     * Checked out copy of the working space.
     */
    public final File wsDir;

    /**
     *  Workflow pipeline script
     */
    public final File workflowScript;

    /**
     *  Parameters to pass to workflow run
     */
    public final Map<String,String> workflowParameters;


    public Bootstrap(File warDir, File pluginsDir, File wsDir) {
        this.warDir = warDir;
        this.pluginsDir = pluginsDir;
        this.wsDir = wsDir;
        this.workflowScript = new File(wsDir.getPath() + "/Jenkinsfile");
        this.workflowParameters = new HashMap<>();
    }

    public Bootstrap(File warDir, File pluginsDir, File wsDir, File workflowScript) {
        this.warDir = warDir;
        this.pluginsDir = pluginsDir;
        this.wsDir = wsDir;
        this.workflowScript = workflowScript;
        this.workflowParameters = new HashMap<>();
    }

    public static void main(String[] args) throws Throwable {
        // break for attaching profiler
        if (Boolean.getBoolean("start.pause")) {
            System.console().readLine();
        }

        // TODO: support exploding war. See WebInfConfiguration.unpack()
        if (args.length<3 || args.length>5) {
            System.err.println("Usage: jenkinsfilerunner <jenkins.war> <pluginsDir> <ws> [<jenkins-file>] [--params=<key=value>]");
            System.exit(1);
        }
        Bootstrap bootstrap = null;
        String parameters = null;

        if(args.length == 3){
            bootstrap = new Bootstrap(new File(args[0]), new File(args[1]), new File(args[2]));
        }
        else if(args.length>3){
            if(!args[3].startsWith("--params=")) {
                bootstrap = new Bootstrap(new File(args[0]), new File(args[1]), new File(args[2]), new File(args[3]));
            } else {
                bootstrap = new Bootstrap(new File(args[0]), new File(args[1]), new File(args[2]));
                parameters = args[4].replace("--params=","");
            }
        }
        if(args.length>4){
            parameters = args[4];
        }


        if(parameters!=null){
            bootstrap.workflowParameters.putAll(
                    Arrays.stream(args[4].replace("--params=", "").split(",")).collect(
                            Collectors.toMap(
                                    s->s.split("=")[0],
                                    s->s.split("=")[1]
                            )
                    )
            );
        }

        //location of Jenkinsfile passed in

        System.exit(bootstrap.run());
    }

    public int run() throws Throwable {
        ClassLoader jenkins = createJenkinsWarClassLoader();
        ClassLoader setup = createSetupClassLoader(jenkins);

        Thread.currentThread().setContextClassLoader(setup);    // or should this be 'jenkins'?

        Class<?> c = setup.loadClass("io.jenkins.jenkinsfile.runner.App");
        return ((IApp)c.newInstance()).run(this);
    }

    public ClassLoader createJenkinsWarClassLoader() throws IOException {
        return new ClassLoaderBuilder(new SideClassLoader(null))
                .collectJars(new File(warDir,"WEB-INF/lib"))
                // servlet API needs to be visible to jenkins.war
                .collectJars(new File(appRepo,"javax/servlet"))
                .make();
    }

    public ClassLoader createSetupClassLoader(ClassLoader jenkins) throws IOException {
        return new ClassLoaderBuilder(jenkins)
                .collectJars(appRepo)
                .make();
    }
}
