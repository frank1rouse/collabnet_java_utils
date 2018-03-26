package com.vce.loadbuild.jenkins.utilities;
import java.io.FileInputStream;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;

import com.collabnet.ce.soap50.types.SoapFieldValues;
import com.collabnet.ce.soap50.webservices.ClientSoapStubFactory;
import com.collabnet.ce.soap50.webservices.cemain.ICollabNetSoap;
import com.collabnet.ce.soap50.webservices.cemain.TrackerFieldSoapDO;
import com.collabnet.ce.soap50.webservices.tracker.ArtifactSoapDO;
import com.collabnet.ce.soap50.webservices.tracker.ITrackerAppSoap;
import com.collabnet.ce.soap50.webservices.tracker.TrackerFieldValueSoapDO;

public class BuildFailureDefectCreation {

    static final String trackerID = "tracker1199"; // Used to delineate which tracker (defects, tasks, tests, stories..)
    static final String propertiesFileName = "defect_creation.properties";

    static Properties props;
    static boolean debug = false;
    static String teamForgeSessionID;
    static String teamforgePW = null;
    static String teamforgeURL = null;
    static String teamforgeUserID = null;
    static int    defectPriority = 1;
    static String defectImpact = "Medium";
    static String reportedInVersion = "TBD2";
    static String defectCategory = "General";
    static String defectAssigned = "pebuildrelease";

    // Low change values in properties file.
    // teamforge.url
    // teamforge.userid
    // teamforge.password
    // default.impact
    // default.assign
    // default.category
    // default.priority

    // High change items are passed through the command line.
    // jobName
    // buildID
    // build status

    public static void main(String[] args) {

        int minimumParameters = 3;
        if (args.length == 0 || args[0].equalsIgnoreCase("--help") || args[0].equalsIgnoreCase("-help") || args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("-h")) {
            printHelp();
            System.exit(1);
        } else if (args.length < minimumParameters) {
            System.err.println("Invalid number of parameters given.");
            System.err.println("");
            printHelp();
            System.exit(1);
        }

        String jobName     = args[0];
        // The buildID is supplied from Jenkins using the ${BUILD_DISPLAY_NAME} environment
        // variable. This variable has a hash tag as the first char which must be stripped.
        // Leave the implementation in such a way that if this changes in the future we wont
        // have to come back and change the code.
        String buildID     = args[1].startsWith("#") ? args[1].substring(1) : args[1];
        String buildStatus = args[2];

        // load valued from the props file so that the command line values will override if given.
        loadProps();

        for (int i=minimumParameters; i < args.length; i++) {
            if (args[i].startsWith("assigned")) {
                String[] splitAssigned = args[i].split("=");
                // Make sure we are not pulling in blanks
                if (splitAssigned.length < 2 || splitAssigned[1].contentEquals("")) {
                    System.err.println("Blank assigned parameter passed");
                    System.err.println("Using default \"" + defectAssigned + "\" user id instead.");
                } else {
                    System.out.println("Using parameter passed assigned value \"" + splitAssigned[1] + "\".");
                    defectAssigned = splitAssigned[1];
                }
            } else if (args[i].startsWith("priority")) {
                String[] splitPriority = args[i].split("=");
                // Make sure we are not pulling in blanks
                if (splitPriority.length < 2 || splitPriority[1].contentEquals("")) {
                    System.err.println("Blank priority parameter passed");
                    System.err.println("Using default priority \"" + defectPriority + "\" instead.");
                } else {
                    System.out.println("Using parameter passed priority value \"" + splitPriority[1] + "\".");
                    validatePriority(splitPriority[1]);
                }
            } else if (args[i].startsWith("reportedinversion")) {
                String[] splitReported = args[i].split("=");
                // Make sure we are not pulling in blanks
                if (splitReported.length < 2 || splitReported[1].contentEquals("")) {
                    System.err.println("Blank reportedinversion parameter passed");
                    System.err.println("Using default reportedinversion \"" + reportedInVersion + "\" instead.");
                } else {
                    System.out.println("Using parameter passed reportedinversion value \"" + splitReported[1] + "\".");
                    // There is no validation of this value as it will keep changing with each release.
                    // Should only be used from a Jenkins job so this should be safe
                    reportedInVersion=splitReported[1];
                }
            } else {
                System.err.println("Unrecognized option \"" + args[i] + "\" given.");
                System.err.println("Unrecognized option ignored.");
            }
        }

        ICollabNetSoap teamforgeSoapInterface = initializeTeamforgeConnection(teamforgeUserID, teamforgePW);

        try {
            ITrackerAppSoap mTrackerApp = (ITrackerAppSoap) ClientSoapStubFactory.getSoapStub(ITrackerAppSoap.class, teamforgeURL);

            // Any error encountered during the creation of the defect will result in an Exception so the
            // catch statement should catch errors.
            createTeamforgeDefect(teamForgeSessionID, mTrackerApp, jobName, buildID, buildStatus);
            System.out.println("The creation of the defect was successful.");
        } catch (Exception e) {
            System.err.println("Error while attempting to create teamforge defect.");
            System.err.println("URL      : " + teamforgeURL);
            System.err.println("userid   : " + teamforgeUserID);
            if (debug)
                e.printStackTrace();
            System.exit(1);
        }

        try {
            // If the teamForgeSessionID is null we never successfully logged
            // into teamforge so no need to log out.
            if (teamForgeSessionID != null) {
                teamforgeSoapInterface.logoff(teamforgeUserID, teamForgeSessionID);
                System.out.println("Successfully logged out of TeamForge.");
            } else {
                System.err.println("Weird, the teamForgeSessionID is null before I tried to logoff.");
            }
        } catch (Exception e) {
            System.err.println("Error logging out of the Teamforge session.");
            if (debug)
                e.printStackTrace();
        }
        System.exit(0);
    }

    public static void printHelp() {
        System.out.println("");
        System.out.println("Usage: <job_name> <build_id> <build_status> plus optional parameters");
        System.out.println("");
        System.out.println("Optional parameters");
        System.out.println("");
        System.out.println("assigned=<userid>");
        System.out.println("** Must be a valid teamforge userid");
        System.out.println("");
        System.out.println("priority=<num 1 - 5>");
        System.out.println("");
        System.out.println("reportedinversion=<build version>");
        System.out.println("");
        System.out.println("There are also options set in the " + propertiesFileName + " properties file.");
    }


    public static void createTeamforgeDefect(String teamForgeSessionID, ITrackerAppSoap mTrackerApp, String jobName, String buildID, String buildStatus) throws Exception {

        String defectTitle = jobName + ": Build/Sanity Failure " + buildID;
        String howToReproduce = "The full console of the build process is at http://jenkins-qa.vmo.lab:8080/jenkins/job/" + jobName + "/" + buildID + "/console";
        String defectDescription = "The build has failed. Latest build status: " + buildStatus + ". For more info see http://jenkins-qa.vmo.lab:8080/jenkins/job/" + jobName + "/" + buildID;
        System.out.println("Attempting to create a defect with the following attributes.");
        System.out.println("");
        System.out.println("Title       = " + defectTitle);
        System.out.println("Description = " + defectDescription);
        System.out.println("Category    = " + defectCategory);
        System.out.println("Priority    = " + defectPriority);
        System.out.println("Assigned    = " + defectAssigned);
        System.out.println("");

        ArtifactSoapDO defectSoapData = mTrackerApp.createArtifact(
                teamForgeSessionID,                        // java.lang.String sessionId,
                trackerID,                                 // java.lang.String trackerId
                defectTitle,                               // java.lang.String title
                defectDescription,                         // java.lang.String description
                "",                                        // java.lang.String group
                defectCategory,                            // java.lang.String category
                "Open",                                    // java.lang.String status
                "",                                        // java.lang.String customer
                defectPriority,                            // int priority
                0,                                         // int estimatedHours
                defectAssigned,                            // java.lang.String assignedteamforgeUserID
                "",                                        // java.lang.String releaseId
                null,                                      // SoapFieldValues flexFields
                null,                                      // java.lang.String attachmentFileName
                null,                                      // java.lang.String attachmentMimeType
                null);                                     // java.lang.String attachmentFileId

        // Any error in the creation of the defect will result in an exception which will automatically
        // pull us out of this routine. There is a try/catch clause surrounding the call to handle error
        // conditions
        System.out.println("Defect " + defectSoapData.getId() + " has been created. Direct link below.");
        System.out.println("https://teamforge.ctf.pe.vce.com/sf/go/" + defectSoapData.getId());

        HashMap <String, String>flexFieldUpdates = new HashMap<String, String>();
        flexFieldUpdates.put("Impact",defectImpact);
        flexFieldUpdates.put("How to Reproduce", howToReproduce);
        flexFieldUpdates.put("Reported In Version", reportedInVersion);
        Iterator<String> keyIterator = flexFieldUpdates.keySet().iterator();

        System.out.println("");
        System.out.println("Attempting to set the following fields");
        while (keyIterator.hasNext()) {
            String fieldToUpdate = keyIterator.next();
            String updateValue = flexFieldUpdates.get(fieldToUpdate);
            System.out.println("Field \"" + fieldToUpdate + "\" to value \"" + updateValue +"\"");
        }

        updateAddDefectFlexFields(defectSoapData, mTrackerApp, flexFieldUpdates);
        System.out.println("");
        System.out.println("Update Successful");

    }


    public static void updateAddDefectFlexFields(ArtifactSoapDO defectSoapData, ITrackerAppSoap mTrackerApp, HashMap <String, String> updateInfo) throws RemoteException {

        // Get the available defect fields
        TrackerFieldSoapDO[] trackerFieldSoapDO = mTrackerApp.getFields(teamForgeSessionID, defectSoapData.getFolderId());

        // Flexfields object to add/update
        SoapFieldValues newFlexFields = new SoapFieldValues();

        newFlexFields.setNames(defectSoapData.getFlexFields().getNames());
        newFlexFields.setTypes(defectSoapData.getFlexFields().getTypes());
        int flexFieldsArraySize = newFlexFields.getNames().length;
//        As of the Teamforge update on 9/13/2014, the getValues method originally used
//        below will no longer return data with newly created defects.
//        newFlexFields.setValues(defectSoapData.getFlexFields().getValues());

//        Given that this is a new defect and the call to getValues will give us a null array
//        we need to create an array of empty strings to mimic the previous behavior
        String[] flexFieldsValues= new String[flexFieldsArraySize];
        Arrays.fill(flexFieldsValues, "");
        newFlexFields.setValues(flexFieldsValues);

        Iterator<String> keyIterator = updateInfo.keySet().iterator();
        while (keyIterator.hasNext()) {
            String fieldToUpdate = keyIterator.next();
            String updateValue = updateInfo.get(fieldToUpdate);

            // Ensure that the updateValue is allowed in the field specified.
            if (validUpdateValue(fieldToUpdate, updateValue, trackerFieldSoapDO)) {

                int fieldNameLocation = 0;
                String[] newFlexFieldsNames = newFlexFields.getNames();

                for ( ; fieldNameLocation < newFlexFieldsNames.length; fieldNameLocation++) {
                    if (newFlexFieldsNames[fieldNameLocation].equalsIgnoreCase(fieldToUpdate))
                        break;
                }

                // Means that the field to update already exists in the flexField data
                if (fieldNameLocation < newFlexFieldsNames.length) {
                    // Create and update the FlexValues array
                    Object[] updatedFlexValues = newFlexFields.getValues();
                    updatedFlexValues[fieldNameLocation] = updateValue;
                    // Update the newFlexFields type
                    newFlexFields.setValues(updatedFlexValues);
                } else {
                    // This means that the field is not already in the flexField structure so we must add it.

                    // Extend the newFlexFields arrays and add the new information
                    flexFieldsArraySize = newFlexFields.getNames().length;
                    String[] newFieldNames = new String[flexFieldsArraySize + 1];
                    System.arraycopy(newFlexFields.getNames(), 0, newFieldNames, 0, flexFieldsArraySize);
                    newFieldNames[flexFieldsArraySize] = fieldToUpdate;

                    String[] newFieldTypes = new String[flexFieldsArraySize + 1];
                    System.arraycopy(newFlexFields.getTypes(), 0, newFieldTypes, 0, flexFieldsArraySize);
                    newFieldTypes[flexFieldsArraySize] = "String";

                    Object[] newFieldValues = new Object[flexFieldsArraySize + 1];
                    System.arraycopy(newFlexFields.getValues(), 0, newFieldValues, 0, flexFieldsArraySize);
                    newFieldValues[flexFieldsArraySize] = updateValue;

                    // Update the FlexFields object
                    newFlexFields.setNames(newFieldNames);
                    newFlexFields.setTypes(newFieldTypes);
                    newFlexFields.setValues(newFieldValues);
                }
            }
        }

        // Add the updated FlexFields to the defectSoapData
        defectSoapData.setFlexFields(newFlexFields);

        // submit the updates to the defectSoapData into teamforge
        mTrackerApp.setArtifactData(teamForgeSessionID, defectSoapData, "Update from automated process", null, null, null);
    }


    // Determine if the value given is valid for the intended field.
    public static boolean validUpdateValue(String fieldName, String testFieldValue, TrackerFieldSoapDO[] trackerFieldSoapDO) {
        int fieldNameLocation = 0;
        boolean foundValue = false;

        // Find the field name we are testing.
        for ( ; fieldNameLocation < trackerFieldSoapDO.length; fieldNameLocation++) {
            if (trackerFieldSoapDO[fieldNameLocation].getName().equalsIgnoreCase(fieldName))
                break;
        }

        // This means a match was found in the trackerFieldSoapDO
        if (fieldNameLocation < trackerFieldSoapDO.length) {
            // Get an instance of that field object to make further tests more readable.
            TrackerFieldSoapDO testTrackerField = trackerFieldSoapDO[fieldNameLocation];

            if (testTrackerField.getFieldType().equalsIgnoreCase("single-select")) {
                // Get a list of the valid values in this field.
                TrackerFieldValueSoapDO[] testFieldValues = trackerFieldSoapDO[fieldNameLocation].getFieldValues();

                // Loop through and see if the value supplied matches one of the preset values.
                for (int i = 0; i < testFieldValues.length && !foundValue; i++ ) {
                    if (testFieldValues[i].getValue().equalsIgnoreCase(testFieldValue))
                        foundValue = true;
                }
            } else if (testTrackerField.getFieldType().equalsIgnoreCase("multi-select-user")) {
                // Make sure this is a valid userid
                // TODO We need to improve this in the future to validate users
                foundValue = true;
            } else if (testTrackerField.getFieldType().equalsIgnoreCase("text")) {
                // If the field type is text really anything goes
                foundValue = true;
            }
        } else {
            System.err.println("Field \"" + fieldName + "\" is not a valid defect field.");
        }
        return foundValue;
    }


    // Log into teamforge a single time
    public static ICollabNetSoap initializeTeamforgeConnection(String teamForgeteamforgeUserID, String teamForgePassword) {

        ICollabNetSoap teamforgeSoapInterface = (ICollabNetSoap) ClientSoapStubFactory.getSoapStub(ICollabNetSoap.class, teamforgeURL);
        try {
            teamForgeSessionID = teamforgeSoapInterface.login(teamForgeteamforgeUserID, teamForgePassword);
        } catch (RemoteException e) {
            System.err.println("Error while attempting to connect to TeamForge.");
            System.err.println("URL      : " + teamforgeURL);
            System.err.println("UserID   : " + teamforgeUserID);
            System.err.println("Password : " + teamforgePW);
            System.err.println("");
            System.err.println("No Defect will be created.");
            System.err.println("");

            // Set to null to indicate to downstream processes that we have not logged into teamforge
            teamForgeSessionID = null;
            if (debug)
                e.printStackTrace();
        }
        return teamforgeSoapInterface;
    }
    public static void validatePriority(String testPriority) {
        testPriority = testPriority.trim();
        try {
            int testDefectPriority = new Integer(testPriority).intValue();
            if (testDefectPriority < 1 || testDefectPriority > 5) {
                System.err.println("Priority \"" + testDefectPriority + "\" is not between 1 - 5.");
                System.err.println("Using default priority \"" + defectPriority + "\" instead.");
            }
            // If we reach here then the priority is valid.
            defectPriority = testDefectPriority;
        } catch (NumberFormatException e) {
            System.err.println("Can't convert priority \"" + testPriority + "\" to an integer");
            System.err.println("Using default priority \"" + defectPriority + "\" instead.");
            if (debug)
                e.printStackTrace();
        }
    }

    public static void validateImpact(String testImpact) {
        testImpact = testImpact.trim();
        String[] validImpactValues = {"High", "Medium", "Low", "Corner Case", "TBD"};
        if (Arrays.asList(validImpactValues).contains(testImpact)) {
            defectImpact = testImpact;
        } else {
            System.err.println("Invalid Impact value \"" + testImpact + "\".");
            System.err.println("Using default Impact \"" + defectImpact + "\" instead.");
        }
    }

    public static void validateCategory(String testCategory) {
        testCategory = testCategory.trim();
        String[] validCategoryValues = {"Automation",
                                        "Component: Compliance backend",
                                        "Component: SDK/Simulator",
                                        "Component: sLib Driver",
                                        "Component: sLib Enterprise",
                                        "Component: vCenter Plugin",
                                        "Component: vCops Adapter",
                                        "Feature: Permissions",
                                        "Feature: Platform Components",
                                        "Feature: RCM Content",
                                        "Feature: RCM Update",
                                        "Feature: Security Hardening",
                                        "General: Documentation",
                                        "General: Process",
                                        "General: Security",
                                        "TBD"};

        if (Arrays.asList(validCategoryValues).contains(testCategory)) {
            defectCategory = testCategory;
        } else {
            System.err.println("Invalid Category value \"" + testCategory + "\".");
            System.err.println("Using default Category \"" + defectCategory + "\" instead.");
        }
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

        String priorityTest = props.getProperty("default.priority");
        if (priorityTest != null) {
            System.out.println("Loading priority value \"" + priorityTest + "\" from " + propertiesFileName + " props file.");
            validatePriority(priorityTest);
        }

        String impactTest = props.getProperty("default.impact");
        if (impactTest != null) {
            System.out.println("Loading impact   value \"" + impactTest + "\" from " + propertiesFileName + " props file.");
            validateImpact(impactTest);
        }

        String categoryTest = props.getProperty("default.category");
        if (categoryTest != null) {
            System.out.println("Loading category value \"" + categoryTest + "\" from " + propertiesFileName + " props file.");
            validateCategory(categoryTest);
        }

        String assignedTest = props.getProperty("default.assigned");
        if (assignedTest != null) {
            System.out.println("Loading assigned value \"" + assignedTest + "\" from " + propertiesFileName + " props file.");
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