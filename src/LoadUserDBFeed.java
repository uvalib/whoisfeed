import java.io.*;
import java.sql.*;
import java.util.*;
import org.apache.log4j.*;
import beans.*;

public class LoadUserDBFeed {


    private Properties config;
    private Connection patronConn;


    static final Logger logger  = LogManager.getLogger(LoadUserDBFeed.class.getName());

    public LoadUserDBFeed() {
	initConfiguration("config/patron-db.conf");
	patronConn = initDbConnection("patron");
    }


    private void loadUserDBData(String filePath) {
	String nextLine;
	java.sql.Timestamp updateTS = new java.sql.Timestamp(System.currentTimeMillis());
    	int nbrPatronInserts = 0;
        int nbrPatronUpdates = 0;
	ArrayList<String> blockedIDs = loadIDBlocks();

	try {
	    BufferedReader br = new BufferedReader(new FileReader(filePath));
	    while ((nextLine = br.readLine()) != null) {
		Patron nextPatron = new Patron(nextLine,"userDB");
		String nextPatronComputingID = nextPatron.getComputingID();
		if (nextPatronComputingID.equals("")) {
			logger.debug("skipping empty patron rec");
			continue;
		}
		if (blockedIDs.contains(nextPatronComputingID)) {
			logger.info("skipping blocked patron " + nextPatronComputingID);
			continue;
		}
		try { 
		    insertNewPatron(nextPatron,updateTS);
		    nbrPatronInserts++;
		} catch (Exception e) {
		    try {
		        updateExistingPatron(nextPatron,updateTS);
		        nbrPatronUpdates++;
		    } catch (Exception updateE ) {
			errorExit("error handling patron rec for " + nextPatron.getComputingID(),updateE);
		    }
		/* we are updating work info, old addresses may no longer be valid */
		    deleteWorkAddresses(nextPatron);
		}
		ArrayList<MailAddress> mailAddresses = nextPatron.getMailAddresses();
		for (int i = 0; i < mailAddresses.size(); i++ ) {
			try { 
				
		    		insertNewPatronAddress(nextPatron,updateTS,i);
			} catch (Exception e) {
		    	  try {
		        	  updateExistingPatronAddress(nextPatron,updateTS,i);
		    	  } catch (Exception updateE ) {
				  logger.error("error handling patron address for " + nextPatron.getComputingID());
		    	  }
			}
		}
		
	    }
	    logger.info( "file end: " + nbrPatronInserts + " inserts, " + nbrPatronUpdates + " upates");
	} catch (Exception e) {
	    errorExit("Error reading whois file input", e);
	}
    }

    private ArrayList<String> loadIDBlocks() {
	PreparedStatement ps = null;
	ResultSet rs = null;
	ArrayList<String> blockedIDs = new ArrayList<String>();
	try {
	    ps = patronConn.prepareStatement("SELECT * FROM blocked_computing_ids");
	    rs = ps.executeQuery();
	    while (rs.next()) {
		String nextComputingID = rs.getString("computing_id");
		if (!(nextComputingID == null)) {
			blockedIDs.add(nextComputingID);
		}
		logger.info("loaded " + blockedIDs.size() + " ids to block");
	    }
        } catch(Exception e) {
	    logger.error("Blocked ID load caught exception " + e.getMessage());
	} finally {
	    if (ps != null) {
		try {
		    ps.close();
		    rs.close();
		}
		catch(Exception e) {}
	    }
	    return blockedIDs;
	}
    }
	
	    
		    
	
	    

    private void deleteWorkAddresses(Patron nextPatron) {
	PreparedStatement ps = null;
	try {
	    ps = patronConn.prepareStatement("DELETE FROM patron_address where computing_id = ? AND ( address_type_cd = 'D' OR address_type_cd = 'M')");
	    ps.clearParameters();
	    ps.setString(1,nextPatron.getComputingID());
	    ps.executeUpdate();
        } catch(Exception e) {
	    logger.debug("Address delete caught exception " + e.getMessage());
	} finally {
	    if (ps != null) {
		try {
		    ps.close();
		}
		catch(Exception e) {}
	    }
	}
    }
	

    private void insertNewPatron(Patron nextPatron, java.sql.Timestamp updateTS) throws SQLException {
        PreparedStatement ps = null;
	try {
            ps = patronConn.prepareStatement("INSERT INTO patron VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? )");
	    ps.clearParameters();
	    ps.setString(1,	nextPatron.getComputingID());
	    ps.setString(2,	nextPatron.getUniversityID());
	    ps.setString(3,	nextPatron.getLastName());
	    ps.setString(4,	nextPatron.getFirstName());
	    ps.setString(5,	nextPatron.getMiddleName());
	    ps.setString(6,	nextPatron.getPrimaryAffiliation());
	    ps.setString(7,	nextPatron.getSchool());
	    ps.setString(8,	nextPatron.getDepartment());
	    ps.setString(9, nextPatron.getAgencyCD());
	    ps.setString(10, nextPatron.getEmailAddress());
	    ps.setTimestamp(11,updateTS);
	    ps.setTimestamp(12,updateTS);
	    ps.executeUpdate();
        } catch(Exception e) {
            throw new SQLException();
	} finally {
	    if (ps != null) {
		try {
		    ps.close();
		}
		catch(Exception e) {}
	    }
	}
    }



    private void updateExistingPatron(Patron nextPatron, java.sql.Timestamp updateTS) {
        PreparedStatement ps = null;
	try {
            ps = patronConn.prepareStatement("UPDATE patron set university_id =?, last_name = ?, first_name = ?, middle_name = ?, primary_affiliation = ?, school = ?, department = ?, agency_cd = ?, email_address = ?, update_dt = ? where computing_id = ?");
	    ps.clearParameters();
	    ps.setString(1,	nextPatron.getUniversityID());
	    ps.setString(2,	nextPatron.getLastName());
	    ps.setString(3,	nextPatron.getFirstName());
	    ps.setString(4,	nextPatron.getMiddleName());
	    ps.setString(5,	nextPatron.getPrimaryAffiliation());
	    ps.setString(6,	nextPatron.getSchool());
	    ps.setString(7,	nextPatron.getDepartment());
	    ps.setString(8, nextPatron.getAgencyCD());
	    ps.setString(9, nextPatron.getEmailAddress());
	    ps.setTimestamp(10,updateTS);
	    ps.setString(11,	nextPatron.getComputingID());
	    ps.executeUpdate();
        } catch(Exception e) {
	    errorExit("Update failed on existing patron " + nextPatron.getComputingID(), e);
	} finally {
	    if (ps != null) {
		try {
		    ps.close();
		}
		catch(Exception e) {}
	    }
	}
    }

    private void insertNewPatronAddress(Patron nextPatron, java.sql.Timestamp updateTS,int addressIndex) throws SQLException {
        PreparedStatement ps = null;
	try {
	    MailAddress mailAddress = nextPatron.getMailAddresses().get(addressIndex);
            ps = patronConn.prepareStatement("INSERT INTO patron_address VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
	    ps.clearParameters();
	    ps.setString(1,	nextPatron.getComputingID());
	    ps.setString(2,	nextPatron.getUniversityID());
	    ps.setString(3,	mailAddress.getAddressTypeCode());
	    ps.setString(4,	mailAddress.getAddressLine1());
	    ps.setString(5,	mailAddress.getAddressLine2());
	    ps.setString(6,	mailAddress.getCity());
	    ps.setString(7,	mailAddress.getState());
	    ps.setString(8,	mailAddress.getCountry());
	    ps.setString(9,	mailAddress.getPostalCode());
	    ps.setTimestamp(10,updateTS);
	    ps.executeUpdate();
        } catch(Exception e) {
            throw new SQLException();
	} finally {
	    if (ps != null) {
		try {
		    ps.close();
		}
		catch(Exception e) {}
	    }
	}
    }

    private void updateExistingPatronAddress(Patron nextPatron, java.sql.Timestamp updateTS,int addressIndex) {
        PreparedStatement ps = null;
	MailAddress mailAddress = nextPatron.getMailAddresses().get(addressIndex);
	try {
            ps = patronConn.prepareStatement("UPDATE patron_address set university_id =?, address_line_1 = ?, address_line_2 = ?, address_city = ?, address_state = ?, address_country = ?, address_postal_code = ?, update_dt = ? where computing_id = ? and address_type_cd = ?");
	    ps.clearParameters();
	    ps.setString(1,	nextPatron.getUniversityID());
	    ps.setString(2,	mailAddress.getAddressLine1());
	    ps.setString(3,	mailAddress.getAddressLine2());
	    ps.setString(4,	mailAddress.getCity());
	    ps.setString(5,	mailAddress.getState());
	    ps.setString(6,	mailAddress.getCountry());
	    ps.setString(7,     mailAddress.getPostalCode());
	    ps.setTimestamp(8,  updateTS);
	    ps.setString(9,     nextPatron.getComputingID());
	    ps.setString(10,    mailAddress.getAddressTypeCode());
	    ps.executeUpdate();
        } catch(Exception e) {
	    logger.error("Update failed on existing patron address " + mailAddress.getAddressTypeCode() +  " for " + nextPatron.getComputingID());
	} finally {
	    if (ps != null) {
		try {
		    ps.close();
		}
		catch(Exception e) {}
	    }
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
						  config.getProperty( prefix + ".url" ) ,
						  config.getProperty( prefix +  ".user" ) ,
						  config.getProperty( prefix + ".password" ) );
		Statement stmt = ret.createStatement();
		stmt.executeUpdate( "use " + config.getProperty( prefix + ".database" ));
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
        String inputFilepath = "/home/idm/udb_feed/whois_data.out";
	PropertyConfigurator.configure("config/patronload-log4j.properties");
	if (args.length > 0) {
		inputFilepath = args[0];
		logger.info("overriding default file with " + inputFilepath);
	}
	logger.info("patron load start");
	LoadUserDBFeed myLoad = new LoadUserDBFeed();

	myLoad.loadUserDBData(inputFilepath);

	logger.info( "patron load end");
    }
}
