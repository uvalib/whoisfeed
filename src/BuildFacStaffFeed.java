import java.io.*;
import java.sql.*;
import java.util.*;
import org.apache.log4j.*;
import beans.*;


/*
 * Class to read in file from ITS containing current whois/UDB records
 * and write out file of faculty and staff in format suitable for SIRSI ingest.
 * Requires minimal processing to translate primary affiliation to SIRSI profile
 * and skip students, etc. who come in SIS pull.
 */
public class BuildFacStaffFeed {

    private Properties config;
    private List<String> excludedIDs;
    private String UDBFilePath;
    private String SirsiFeedFilePath;


    static final Logger logger  = LogManager.getLogger(BuildFacStaffFeed.class.getName());


    private void loadUserDBData() {
	String nextInputLine = null;
	String nextOutputLine = null;
	int nbrOutput = 0;
	BufferedReader UDBReader = null;
	PrintWriter SirsiFeedWriter = null;
	

	try {
	    UDBReader = new BufferedReader(new FileReader(UDBFilePath));
	} catch ( Exception inputE ) {
		errorExit("Error opening input file " + UDBFilePath, inputE);
	}

	try {
	    SirsiFeedWriter = new PrintWriter(SirsiFeedFilePath);
	} catch ( Exception outputE ) {
		errorExit("Error opening output file " + SirsiFeedFilePath, outputE );
	}

	try {
	    while ((nextInputLine = UDBReader.readLine()) != null) {
		Patron nextPatron = new Patron(nextInputLine,"userDB");
		String nextPatronComputingID = 
			nextPatron.getComputingID();
/* 
 *              skip any bad records, including the test records from ITS
		if (nextPatronComputingID.equals("") ||
			nextPatronComputingID.equals("bta4n") || 
			nextPatronComputingID.equals("mta8x") ||
			nextPatronComputingID.equals("jta3e") ||
			nextPatronComputingID.equals("mst3k")) {
 */
		if (nextPatronComputingID.equals("") ||
			excludedIDs.contains(nextPatronComputingID)) {
			logger.debug("Skipping record for -" + 
				nextPatronComputingID + "_"); 
			continue;
		}
		String primAff = nextPatron.getPrimaryAffiliation();
/*  this is staff/faculty feed, ignore the rest */
		if (!primAff.equals("Faculty") && 
			!primAff.equals("Staff") &&
			!primAff.equals("Instructor") ) {
			continue;	
		}
		ArrayList<MailAddress> mailAddresses = nextPatron.getMailAddresses();
		MailAddress address = null;
		if (mailAddresses.size() > 0 ) {
			address = mailAddresses.get(0);
		}
		if (address == null) address = new MailAddress();
		nextOutputLine =
			nextPatron.getUniversityID() + "^" +
			nextPatron.getComputingID() + "^" +
			nextPatron.getLastName() + "^" +
			nextPatron.getFirstName() + "^" +
			nextPatron.getMiddleName() + "^" +
			address.getAddressLine1() + "^" +
			address.getAddressLine2() + "^" +
			address.getCity() + "^" +
			address.getState() + "^" +
			address.getPostalCode() + "^" +
			nextPatron.getEmailAddress() + "^";
			
		if (primAff.equals("Faculty") || primAff.equals("Instructor")) {
			nextOutputLine = nextOutputLine.concat("FACULTY^");
		} else if (primAff.equals("Staff")) {
			nextOutputLine = nextOutputLine.concat("EMPLOYEE^");
		} else {
			logger.info("Abandoning bad input line: " + nextInputLine);
			continue;
		}
		nextOutputLine = nextOutputLine.concat(nextPatron.getDepartment());
		SirsiFeedWriter.println(nextOutputLine);
		nbrOutput++;
	    }
	    SirsiFeedWriter.flush();
	    SirsiFeedWriter.close();
	    logger.info(nbrOutput + " file lines writtern");
		
	} catch (Exception e) {
	    errorExit("Error processing file: " + nextOutputLine, e);
	}
    }

    private void initConfig(String inputFilePath, String outputFilePath) {
	this.config = new Properties();
	try {
		InputStream is = this.getClass().getResourceAsStream("config/whoisFeed.conf");
		this.config.load(is);
		is.close();
	 }  catch ( Exception e) {
		errorExit("Unable to load whois config", e);
	}
	String excludeList = config.getProperty("excludeList","mst3k");
	excludedIDs = Arrays.asList(excludeList.split(","));
	if (inputFilePath != null) {
		UDBFilePath = inputFilePath;
	} else {
		UDBFilePath = config.getProperty("UDBFilePath","/home/idm/udb_feed/whois_data.out");
	}
	if (outputFilePath != null) {
		SirsiFeedFilePath =  outputFilePath;
	} else {
		SirsiFeedFilePath = config.getProperty("SirsiFeedFilePath","/home/idm/udb_feed/sirsi-feed-faculty-staff");
	}
	
    }
	


    private void errorExit( String message , Exception e ) {
	logger.error( message );
	logger.error( e.toString() );
	e.printStackTrace();
	System.exit(1);
    }

    public static void main(String[] args) throws IOException {
        String inputFilePath = null;
	String outputFilePath = null;
	PropertyConfigurator.configure("config/whoisFeed-log4j.properties");
	if (args.length > 0) {
		inputFilePath = args[0];
		logger.debug("overriding config input file with " + inputFilePath);
	} 
	if (args.length > 1) {
		outputFilePath = args[1];
		logger.debug("overriding config output file with " + 
			outputFilePath);
	} 
	logger.debug("whois load start");
	BuildFacStaffFeed buildFeed = new BuildFacStaffFeed();
	buildFeed.initConfig(inputFilePath,outputFilePath);

	buildFeed.loadUserDBData();

	logger.debug( "whois load end");
    }
}
