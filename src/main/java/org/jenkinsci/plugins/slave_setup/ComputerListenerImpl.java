package org.jenkinsci.plugins.slave_setup;

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.slaves.ComputerListener;
import hudson.util.LogTaskListener;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension
public class ComputerListenerImpl extends ComputerListener {

    private static final Logger LOGGER = Logger.getLogger(ComputerListenerImpl.class.getName());

    private SetupDeployer deployer = new SetupDeployer();

    @Override
    public void preLaunch(Computer c, TaskListener listener) throws IOException, InterruptedException {
        listener.getLogger().println("just before slave " + c.getName() + " gets launched ...");

        SetupConfig config = SetupConfig.get();

        listener.getLogger().println("executing pre-launch scripts ...");
        deployer.executePreLaunchScripts(c, config, listener);
    }

    /**
     * Prepares the slave before it gets online by copying the given content in root and executing the configured setup
     * script.
     *
     * @param c the computer to set up
     * @param channel not used
     * @param root the root of the slave
     * @param listener log listener
     * @throws IOException
     * @throws InterruptedException
     */
    @Override
    public void preOnline(Computer c, Channel channel, FilePath root, TaskListener listener) throws IOException, InterruptedException {
        listener.getLogger().println("just before slave " + c.getName() + " gets online ...");

        SetupConfig config = SetupConfig.get();

        listener.getLogger().println("executing prepare script ...");
        deployer.executePrepareScripts(c, config, listener);

        listener.getLogger().println("setting up slave " + c.getName() + " ...");
        deployer.deployToComputer(c, root, listener, config);

        listener.getLogger().println("slave setup done.");
    }

    /**
     * Performs actions, required to be done after slave startup. F.e, configure DNS record for corresponding cloud
     * machine.
     *
     * @param c the computer to execute actions for
     * @param listener log listener
     * @throws IOException
     * @throws InterruptedException
     */
    @Override
    public void onOnline(Computer c, TaskListener listener) throws IOException, InterruptedException {
        if (!(c instanceof Jenkins.MasterComputer)) {
            /*
            preLaunch and preOnline events occur only after master node, and hereby, Jenkins itself starts, so they
            don't require this check. But onOnline is being triggered upon master node startup. It seems fair to skip
            these actions for master node. TODO: discuss that, may be it should be optional
             */
            listener.getLogger().println("right after slave " + c.getName() + " got online ...");
            SetupConfig config = SetupConfig.get();
            deployer.executeStateChangeScript(c, config, listener, true);
        }
    }

    /**
     * Performs actions, required to be done after slave disconnection. Use this to roll back actions done by
     * {@link #onOnline(hudson.model.Computer, hudson.model.TaskListener)}.
     *
     * @param c the computer to execute actions for
     */
    @Override
    public void onOffline(Computer c) {
        TaskListener listener = new LogTaskListener(LOGGER, Level.ALL);
        listener.getLogger().println("right after slave " + c.getName() + " got offline ...");
        SetupConfig config = SetupConfig.get();
        try {
            deployer.executeStateChangeScript(c, config, listener, false);
        } catch (AbortException e) {
            listener.getLogger().println("node-offline script have failed to execute");
        }
    }
}
