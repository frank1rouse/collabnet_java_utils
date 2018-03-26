package com.vce.loadbuild.jenkins.utilities;
import java.io.FileInputStream;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.Properties;

import com.collabnet.ce.soap50.webservices.ClientSoapStubFactory;
import com.collabnet.ce.soap50.webservices.cemain.ICollabNetSoap;
import com.collabnet.ce.soap50.webservices.cemain.UserSoapDO;

public class GetCollabnetEmailFromSVNUserid {

    static final String trackerID = "tracker1199"; // Used to delineate which tracker (defects, tasks, tests, stories..)
    static final String propertiesFileName = "getcollabnetemailfromsvnuserid.properties";

    static Properties props;
    static boolean debug = false;
    static String teamForgeSessionID;
    static String teamforgePW = null;
    static String teamforgeURL = null;
    static String teamforgeUserID = null;

    public static void main(String[] args) {

        int minimumParameters = 1;
        if (args.length == 0 || args[0].equalsIgnoreCase("--help") || args[0].equalsIgnoreCase("-help") || args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("-h")) {
            printHelp();
            System.exit(1);
        } else if (args.length < minimumParameters) {
            System.err.println("Invalid number of parameters given.");
            System.err.println("");
            printHelp();
            System.exit(1);
        }

        String userID  = args[0];

        // load valued from the props file so that the command line values will override if given.
        loadProps();

        ICollabNetSoap teamforgeSoapInterface = initializeTeamforgeConnection(teamforgeUserID, teamforgePW);

        try {
            ICollabNetSoap mTrackerApp = (ICollabNetSoap) ClientSoapStubFactory.getSoapStub(ICollabNetSoap.class, teamforgeURL);
            UserSoapDO userInformation = mTrackerApp.getUserData(teamForgeSessionID, userID);
            if (userInformation.getStatus().equalsIgnoreCase("active"))
                System.out.println(userInformation.getEmail());
        } catch (Exception e) {
            if (debug) {
                System.err.println("Error while attempting to create teamforge defect.");
                System.err.println("URL      : " + teamforgeURL);
                System.err.println("userid   : " + teamforgeUserID);
                e.printStackTrace();
            }
            System.exit(1);
        }

        try {
            // If the teamForgeSessionID is null we never successfully logged
            // into teamforge so no need to log out.
            if (teamForgeSessionID != null) {
                teamforgeSoapInterface.logoff(teamforgeUserID, teamForgeSessionID);
                if (debug)
                    System.out.println("Successfully logged out of TeamForge.");
            } else {
                if (debug)
                    System.err.println("Weird, the teamForgeSessionID is null before I tried to logoff.");
            }
        } catch (Exception e) {
            if (debug) {
                System.err.println("Error logging out of the Teamforge session.");
                e.printStackTrace();
            }
        }
        System.exit(0);
    }

    public static void printHelp() {
        System.out.println("");
        System.out.println("Usage: <svn_commit_name>");
        System.out.println("");
        System.out.println("If userid is found in CollabNet the email address will be output");
        System.out.println("For any other error no message will be displayed unless the debug flag is passed");
        System.out.println("There are also options set in the " + propertiesFileName + " properties file.");
    }

    // Log into teamforge a single time
    public static ICollabNetSoap initializeTeamforgeConnection(String teamForgeteamforgeUserID, String teamForgePassword) {

        ICollabNetSoap teamforgeSoapInterface = (ICollabNetSoap) ClientSoapStubFactory.getSoapStub(ICollabNetSoap.class, teamforgeURL);
        try {
            teamForgeSessionID = teamforgeSoapInterface.login(teamForgeteamforgeUserID, teamForgePassword);
        } catch (RemoteException e) {
            if (debug) {
                System.err.println("Error while attempting to connect to TeamForge.");
                System.err.println("URL      : " + teamforgeURL);
                System.err.println("UserID   : " + teamforgeUserID);
                System.err.println("Password : " + teamforgePW);
                System.err.println("");
            }

            // Set to null to indicate to downstream processes that we have not logged into teamforge
            teamForgeSessionID = null;
            if (debug)
                e.printStackTrace();
        }
        return teamforgeSoapInterface;
    }

    public static void loadProps() {
        props = new Properties();

        // Load from the current directory
        try {
            props.load(new FileInputStream(propertiesFileName));
        } catch (IOException e) {
            System.err.println("Problem reading properties file \"" + propertiesFileName + "\" in current directory.");
            System.exit(1);
        }

        // Set debug flag from the props file
        String debugTest = props.getProperty("debug");
        if (debugTest != null && debugTest.equalsIgnoreCase("true")) {
            debug = true;
        }

        teamforgeURL = props.getProperty("teamforge.url");
        teamforgePW = props.getProperty("teamforge.password");
        teamforgeUserID = props.getProperty("teamforge.userid");

        if (teamforgeURL == null || teamforgePW == null || teamforgeUserID == null) {
            System.err.println("The following values must be supplied by the " + propertiesFileName + " file.");
            System.err.println("");
            System.err.println("teamforge.url");
            System.err.println("teamforge.userid");
            System.err.println("teamforge.password");
            System.exit(1);
        }
    }
}