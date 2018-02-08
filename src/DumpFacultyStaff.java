import java.io.*;
import java.sql.*;
import java.util.*;
import org.apache.log4j.*;
import beans.*;

/*
 * Class to dump all current faculty and staff from patron db into
 * file to feed to SIRSI
 */
public class DumpFacultyStaff {


    private Properties config;
    private Connection patronConn;


    static final Logger logger  = LogManager.getLogger(DumpFacultyStaff.class.getName());

    public DumpFacultyStaff() {
	initConfiguration("config/patron-db.conf");
	patronConn = initDbConnection("patron");
    }


    private void dumpPatronData(String outputFilePath) {
	String nextLine;
	int nbrOutput = 0;
	PrintWriter sirsiFeedWriter = null;
        PreparedStatement ps = null;
	ResultSet rs = null;

	try {
	    sirsiFeedWriter = new PrintWriter(outputFilePath);
	} catch (Exception fileE) {
	    errorExit("Error opening output file " + outputFilePath,fileE);
	}
	
	try {

	    ps = patronConn.prepareStatement("SELECT * FROM patron left outer join patron_address on patron.computing_id = patron_address.computing_id left outer join sirsi_profile on patron.primary_affiliation = sirsi_profile.primary_affiliation WHERE DATE_ADD(patron.update_dt, INTERVAL 3 DAY) > CURDATE() ORDER BY sirsi_profile.sirsi_profile, patron_address.computing_id, patron_address.address_type_cd desc");
	    rs = ps.executeQuery();
	    String fileLine = null;
	    String holder = "";
	    String lastComputingID = "";
	    while (rs.next()) {
		String thisComputingID = rs.getString("computing_id");
/*   skip alternate addresses */
		if (lastComputingID.equals(thisComputingID))  {
			logger.debug("skipping alt address for " + thisComputingID);
			continue;
		}
		String sirsiProfile = rs.getString("sirsi_profile.sirsi_profile");
		if ((sirsiProfile == null) || 
			!sirsiProfile.equals("FACULTY") && 
			!sirsiProfile.equals("EMPLOYEE")) {
			continue;
		}
		lastComputingID = thisComputingID;
		fileLine = 
		    rs.getString("patron.university_id") + "^" +
		    thisComputingID + "^" +
		    (((holder = 
			rs.getString("patron.last_name")) == null)? "":holder)  + "^" +
		    (((holder = 
			rs.getString("patron.first_name")) == null)? "":holder)  + "^" +
		    (((holder = 
			rs.getString("patron.middle_name")) == null)? "":holder)  + "^" +
		    (((holder = 
			rs.getString("patron_address.address_line_1")) == null)? "":holder)  + "^" +
		    (((holder = 
			rs.getString("patron_address.address_line_2")) == null)? "":holder)  + "^" +
		    (((holder = 
			rs.getString("patron_address.address_city")) == null)? "":holder)  + "^" +
		    (((holder = 
			rs.getString("patron_address.address_state")) == null)? "":holder)  + "^" +
		    (((holder = 
			rs.getString("patron_address.address_postal_code")) == null)? "":holder)  + "^" +
		    (((holder = 
			rs.getString("patron.email_address")) == null)? "":holder)  + "^" +
		    (((holder = 
			rs.getString("sirsi_profile.sirsi_profile")) == null)? "":holder)  + "^" +
		    (((holder = 
			rs.getString("patron.department")) == null)? "":holder) ;
		    sirsiFeedWriter.println(fileLine);
		    nbrOutput++;

		}
		logger.info(nbrOutput + " file lines written");
		sirsiFeedWriter.flush();
		sirsiFeedWriter.close();

	} catch (Exception e) {
	    errorExit("Error reading patron db ", e);
	}
    }




    private void initConfiguration( String fileName ) {
	this.config = new Properties();
	try {
	    InputStream is = this.getClass().getResourceAsStream( fileName );
	    this.config.load( is );
	    is.close();
	}
	catch( Exception e ){
	    errorExit( "Unable to initialize the configuration."  , e );
	}
    }

    private Connection initDbConnection( String prefix ) {
	Connection ret = null;
	try {
		Class.forName( config.getProperty( prefix + ".driver" ) );
		ret = DriverManager.getConnection(
						  config.getProperty(prefix +  ".url" ) ,
						  config.getProperty(prefix + ".user" ) ,
						  config.getProperty(prefix + ".password" ) );
		Statement stmt = ret.createStatement();
		stmt.executeUpdate( "use " + config.getProperty(prefix +  ".database" ));
	} catch( Exception e ) {
	    errorExit( "Unable to initialize a database connection."  , e );
	}
	return ret;
    }

    private void errorExit( String message , Exception e ) {
	logger.error( message );
	logger.error( e.toString() );
	e.printStackTrace();
	System.exit(1);
    }

    public static void main(String[] args) throws IOException {
        String outputFilePath = "/home/idm/udb_feed/sirsi-feed-faculty-staff";
	PropertyConfigurator.configure("config/patronload-log4j.properties");
	if (args.length > 0) {
		outputFilePath = args[0];
		logger.info("overriding default file with " + outputFilePath);
	}
	logger.info("patron dump start");
	DumpFacultyStaff myFSDump = new DumpFacultyStaff();

	myFSDump.dumpPatronData(outputFilePath);

	logger.info( "patron dump end");
    }
}
