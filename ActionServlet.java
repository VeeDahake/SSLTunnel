package com.jpmchase.pws.webcommon.action;

import java.net.URL;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.Hashtable;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.jpmchase.pws.common.StateLogger;
import com.jpmchase.pws.common.db.DBManager;
import com.jpmchase.pws.common.exception.PWSException;
import com.jpmchase.pws.common.log.Level;
import com.jpmchase.pws.common.log.LogConstants;
import com.jpmchase.pws.common.log.Logger;
import com.jpmchase.pws.common.util.PropManager;
import com.jpmchase.pws.common.util.UserProfile;
import com.jpmchase.pws.webcommon.PWSObjectHelper;
import com.jpmchase.pws.webcommon.SessionConstants;

public class ActionServlet extends org.apache.struts.action.ActionServlet
{
	public static String cvsVersionInfo   = "/*$Id: ActionServlet.java,v 1.9 2007/01/09 20:52:50 j918163 Exp $*/";
	private static final org.apache.log4j.Logger log = Logger.getLogger(ActionServlet.class);


	private static java.util.Hashtable sessionHash;
	private static String sessionLogoutURLs = PropManager.getProperty("sessionLogoutURLs");
	java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat ("MM/dd/yyyy hh:mm:ss");

	public void init(ServletConfig config) throws ServletException
	{
		super.init(config);
	}

	private static void setSessionStatusInDB(HttpSession session, String userId)
	{
		Connection conn = null;
		CallableStatement stmt = null;
		PreparedStatement ps = null;
		if ( (session == null) || (userId == null) )
		{
			log.log(Level.WARN,"[" + userId + "]" + LogConstants.ADMIN + "] " + "Invalid user is or session passed. User id: " + userId + ", session: " + session);
			return;
		}
		String sessionID = session.getId();
		if (sessionID == null || sessionID.trim().equals("") )
		{
			log.log(Level.WARN,"[" + userId + "]" + LogConstants.ADMIN + "] " +"Invalid session id: " + sessionID);
			return;
		}
		log.log(Level.INFO,"[" + userId + "]" + LogConstants.ADMIN + "] " +"User : " + userId + " bound to session by sessionID : " + sessionID);

		try
		{
			//set http session flag to active.

			String strSQL = "BEGIN INSERT INTO USER_SESSIONS (SESSION_ID_PK, USER_ID_FK, PWS_SESSION_ID) VALUES (USER_SESSIONS_PK_SEQ.NEXTVAL, ?, ?) RETURNING SESSION_ID_PK INTO ?; END;";
			
			conn = DBManager.getPooledConnection();
			stmt = conn.prepareCall(strSQL);
			stmt.setString(1, userId);
			stmt.setString(2, sessionID);
			stmt.registerOutParameter(3, Types.NUMERIC );
			log.debug(strSQL);
			stmt.execute();
			long userSessionIdPk = stmt.getLong(3);
			session.setAttribute(SessionConstants.USER_SESSION_ID_PK, "" + userSessionIdPk);

			
			String appServer = PropManager.getProperty("app_server_url");
			strSQL = "UPDATE user_info set b_active_http_session='Y', PWS_SESSION_ID=?, LAST_LOGGED_INTO=? WHERE user_id_pk =?";
			ps = conn.prepareStatement(strSQL);
			ps.setString(1, sessionID);
			ps.setString(2, appServer);
			ps.setString(3, userId);
			
			log.debug(strSQL);
			int cnt = ps.executeUpdate();
			if (cnt != 1)
			{
				log.warn("Error while setting b_active_http_session flag for user id : " + userId);
			}
			//insert the state log record for logging in
			StateLogger.log(30001, userSessionIdPk, userId);

		}
		catch(Exception ex)
		{
			log.fatal( "Exception occurred while logging session for user id : " + userId + " - " + ex.getMessage());
			
		}
		finally
		{
			try
			{
				if(stmt != null)
					stmt.close();
			}catch(Exception ignore){}
			try
			{
				if(ps != null)
					ps.close();
			}catch(Exception ignore){}
			try
			{
				if(conn != null)
					conn.close();
			}
			catch(Exception ignore){
				log.warn("Ignoring exception while closing connection " + ignore.getMessage());
			}
		}
		log.info("Inside validateSession()...User is new. Binding the session to sessionHash with key: " + userId + " and session: " + sessionID);
		sessionHash.put(userId, session);
	}

	/**
	 * Insert the method's description here.
	 * Creation date: (6/7/02 3:01:13 PM)
	 * @param session javax.servlet.http.HttpSession
	 */
	public static synchronized void removeSession(HttpSession session)
	{
		log.debug("Inside removeSession() session: " + session.getId());
		if (session != null)
		{
			Enumeration enum = sessionHash.keys();
			while(enum.hasMoreElements())
			{
				String key = (String)enum.nextElement();
				javax.servlet.http.HttpSession ses = (javax.servlet.http.HttpSession)sessionHash.get(key);
				if (session.equals(ses))
				{
					log.debug("Removing session for user: " + key);
					sessionHash.remove(key);
					break;
				}
			}
		}
	}

	public static synchronized void removeSession(String userId)
	{
		log.debug("Inside removeSession() user: " + userId);
		if(userId != null)
		{
			javax.servlet.http.HttpSession ses = (javax.servlet.http.HttpSession)sessionHash.get(userId);
			if (ses != null){
				log.debug("Removing session for user: " + userId + " ;session="+ses.getId());
				ses.invalidate();
			}
		}
	}

	public synchronized static boolean isActiveUser(String sessionId, String userId) throws ServletException
	{
		log.debug("Inside isActiveUser()..."+userId);
		if(userId == null || userId.trim().equals("")){
			log.warn("Unable to check active status of user.....USER ID:" + userId);
			return false;
		}
		Connection conn = null;
		CallableStatement stmt = null;
		try
		{
			conn = DBManager.getConnection();
			stmt = conn.prepareCall("BEGIN SELECT b_active_http_session INTO ? FROM user_info WHERE user_id_pk=?; END;");
			stmt.registerOutParameter(1, Types.VARCHAR);
//			stmt.setString(2, sessionId);
			stmt.setString(2, userId);
			stmt.execute();
			return "Y".equalsIgnoreCase(stmt.getString(1));
		}
		catch(Exception ignore)
		{
			log.info("Current session does not match User session in DB for User:" + userId + " and session:" + sessionId);
		}
		finally
		{
			try
			{
				if(stmt != null)
					stmt.close();
				if(conn != null)
					conn.close();
			}
			catch(Exception ignore){}
		}
		return false;
	}

	public void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, java.io.IOException
	{
		// Check for the session validation of logged in user....

		try
		{
/*
 			try {
				// The following code is purely for logging purpose....
				Enumeration headersEnum = req.getHeaderNames();
				Logger.info("----------Inside ActionServlet.service(). Reading header info from request------------");
				String nextHeaderName = null;
				String nextHeaderValue = null;
				while(headersEnum != null && headersEnum.hasMoreElements()){
					nextHeaderName = headersEnum.nextElement().toString();
					Logger.info("Header name:" + nextHeaderName);
					nextHeaderValue = req.getHeader(nextHeaderName);
					Logger.info("Header value:" + nextHeaderValue);
				}
				Logger.info("----------Inside ActionServlet.service(). End header info from request------------");
				Logger.info("----------Inside ActionServlet.service(). Reading cookies from request------------");
				Cookie[] cookies = req.getCookies();
				Cookie tempCookie = null;
				if(cookies != null && cookies.length > 0){
					for(int i = 0,j=cookies.length;i<j;i++){
						tempCookie = cookies[i];
						Logger.info("Cookie Name:" + tempCookie.getName() + " ----- value: " + tempCookie.getValue());
					}
				}
				else{
					Logger.info("No cookies available in request");
				}
				Logger.info("----------Inside ActionServlet.service(). End cookies info from request------------");
				Logger.info("IP address of client: " + req.getRemoteAddr());
				Logger.info("HOST of client: " + req.getRemoteHost());
				// The above code is purely for logging purpose....
			} catch (Exception e) {
				Logger.fatal("", "", getClass().getName(), "service()", "Inside ActionServlet.service()... Error while getting header info from request object: " + e.getMessage(), e);
				e.printStackTrace();
			}
*/
			if(sessionHash == null){
				log.debug("Inside service()..creating new hash");
				sessionHash = new Hashtable();
			}

			HttpSession session = req.getSession(false);
			String sessionId = null;
			String userId = null;
			if (session == null){
				log.warn("Inside service()..Session is NULL");
			}
			else{
				sessionId = session.getId();
				log.info("Inside service()..Got session id: " + sessionId);
				if(session.getAttribute("USERINFO") != null){
				   log.info("Inside service()..Getting user information from session object");
				   com.jpmchase.pws.common.util.UserProfile userInfo = null;
				   try {
				   	userInfo = (com.jpmchase.pws.common.util.UserProfile)session.getAttribute("USERINFO");
				   	}
				   catch (Exception ignore){
					log.warn("Inside service()..Error while getting user information from session object: " + ignore.getMessage());
					ignore.printStackTrace();
				   }
				   if(userInfo != null){
					   userId = userInfo.getUserId();
						Thread t = Thread.currentThread();
						if(t != null && t.getName() != null && userId != null){
							String presentName = t.getName();
							int index = presentName.indexOf(":USER:");
							if(index >= 0)
							{
								t.setName(presentName.substring(0, index+6) + userId);
							}
							else
							{
								t.setName(presentName + ":USER:" + userId);
							}
						}
				   }
			   }
			   else{
					log.warn("Inside service()..Could not get user information from session object:" + sessionId);
			   }
			}

			
//			String asyncOption = req.getParameter("asyncOption");
//			if (asyncOption != null && asyncOption.equalsIgnoreCase("1")){
//				log.info("Inside service()..Invalidating session in other servers for user:" + userId);
//				invalidateAllServers(userId);
//			}

			String logoutOption = req.getParameter("logoutOption");
			if (logoutOption != null && logoutOption.equalsIgnoreCase("1"))
			{
				invalidateAllServers(userId);
				HttpSession _session = (sessionHash.get(userId) != null) ? (HttpSession)sessionHash.get(userId) : null;
				if(_session != null && !_session.getId().equals(sessionId))
				{
					log.debug("Inside validateSession()...invalidating previous session: " + _session.getId());
					_session.invalidate();
					sessionHash.remove(userId);
				}
				setSessionStatusInDB(session, userId);
				session.removeAttribute(SessionConstants.USER_LOGIN);
			}
			else
			{
				if (false == isActiveUser(sessionId, userId))
				{
					setSessionStatusInDB(session, userId);
					session.removeAttribute(SessionConstants.USER_LOGIN);
				}
				else
				{
					if (session.getAttribute(SessionConstants.USER_LOGIN) != null)
					{
						req.getRequestDispatcher("jsp/invalidSession.jsp?SCRNAME=Invalid Session").forward(req, res);
						return;
					}
				}
			}
		}
		catch(Exception e)
		{
			log.warn("Error while validating session..." , e);
			// e.printStackTrace();
		}

		//milliseconds since January 1, 1970, 00:00:00 GMT
		long respStart = Calendar.getInstance().getTime().getTime();
		try
		{
			super.service(req, res);
		}
		finally
		{
			//milliseconds since January 1, 1970, 00:00:00 GMT
			long respEnd = Calendar.getInstance().getTime().getTime();
			long diff = respEnd - respStart; //milli seconds
			double _secs = (double) diff/1000; //seconds
			Logger.response(1, req.getRequestURI(), _secs);
			logUserResourceAccess(req, respStart, respEnd);
		}
	}

	/**
	 * @param req
	 * @param respStart
	 * @param respEnd
	 */
	private void logUserResourceAccess(HttpServletRequest req, long respStart, long respEnd) {
		
		HttpSession session = req.getSession(false);
		if(session == null){
			log.warn("Error in logUserResourceAccess. Invalid session");
			return;
		}
		String userId = PWSObjectHelper.getUserName(session);
		String url = req.getRequestURI();
		log.debug("logging resource access for user " + userId + " and url " + url);

		String appServer = PropManager.getProperty("app_server_url");
		String clientIp = "";
		String clientBrowser = "";

		//System.out.println("Appserver user logged into = [" + appServer + "]");

		if(req.getRemoteAddr() != null) {
			clientIp = req.getRemoteAddr();
		}
		//System.out.println("IP Address user logged in from = [" + clientIp + "]");

		if(req.getHeader("User-Agent") != null) {
			clientBrowser = req.getHeader("User-Agent");
		}
		//System.out.println("the user is using [" + clientBrowser + "] Browser");
		if(url != null )
		{
			url = url.substring(url.lastIndexOf("/"));
			int index = url.lastIndexOf(".do");
			if (index > -1)
				url = url.substring(0, index);
		}
		Connection conn = null;
		CallableStatement stmt = null;
		try
		{
			conn = DBManager.getConnection();
			stmt = conn.prepareCall("{call log_user_access_pkg.log_user_access_sp(?,?,?,?,?,?,?,?)}");
			stmt.setString(1, userId);
			stmt.setString(2, session.getId());
			stmt.setString(3, url);
			stmt.setTimestamp(4, new Timestamp(respStart));
			stmt.setTimestamp(5, new Timestamp(respEnd));
			stmt.setString(6, appServer);
			stmt.setString(7, clientIp);
			stmt.setString(8, clientBrowser);
			stmt.execute();
			log.debug("completed logging resource access for user " + userId + " and url " + url);
		}
		catch(Exception ignore)
		{
			log.warn("Error logging User:" + userId + " and session:" + session.getId() + " due to " +
					 ignore.getMessage());
			ignore.printStackTrace();
		}
		finally
		{
			try
			{
				if(stmt != null)
					stmt.close();
				if(conn != null)
					conn.close();
			}
			catch(Exception ignore){
				log.debug("Ignoring exception while closing connection " + ignore.getMessage());
			}
		}
	}

	private static boolean invalidateAllServers(String userId) {
	    
	    String appServer = null;
	    Connection conn = null;
	    Statement stmt = null;
	    ResultSet res  = null;
	    String query = "SELECT LAST_LOGGED_INTO FROM USER_INFO WHERE USER_ID_PK = '" + userId + "' ";
	    try {
	        log.debug("Query = " + query);
	        conn = DBManager.getConnection();
	        stmt = conn.createStatement();
	        res = stmt.executeQuery(query);
	        if(res.next()) {
	            appServer = res.getString(1);
	            log.info("Previous session of user " + userId + " is active on " + appServer);
	        }
	        
	    } catch(Exception sq) {
	        Logger.warn("Exception while retrieving app server where the user session is active" + sq.getMessage());
	    } finally {
	        try {
	            if(res != null) {
	                res.close();
	            }
	            if(stmt != null) {
	                stmt.close();
	            }
	            if(conn != null) {
	                conn.close();
	            }
	        } catch(SQLException e) {
	            log.info("Ignoring exception while closing connection " + e.getMessage());
	        }
	    }
	    
	    if(appServer != null) {
	        appServer = appServer.trim();
	        log.debug("Invalidating previous session of user " + userId + " on " + appServer);
	        try {
				java.net.URL url = new URL(appServer + "logout.jsp?" + SessionConstants.USER_ID + "=" + userId);
				Object obj = url.getContent();
				log.debug("Invalidated with URL [" + appServer + "logout.jsp?" + SessionConstants.USER_ID + "=" + userId +"]" );
			} catch(Exception e) {
				log.warn("Error while invalidating previous session of user" + userId + " on " + appServer,  e);
			}
	    } else {
	        log.fatal("Unable to determine the app server on which previous session of user " + userId + " is active");
	    }
		
		log.debug("out of invalidateAllServers");
		return true;
	}


	public static Hashtable getActiveUsers() throws PWSException
	{
		Hashtable usersHash = null;
		if(sessionHash == null || sessionHash.size() == 0)
		{
			return usersHash;
		}
		try
		{
			usersHash = new Hashtable();
			Enumeration enum = sessionHash.keys();
			String nextKey = null;
			HttpSession nextSession = null;
			UserProfile up = null;
			String userName = null;
			while(enum.hasMoreElements())
			{
				nextKey = enum.nextElement().toString();
				nextSession = (HttpSession)sessionHash.get(nextKey);
				up = (UserProfile)nextSession.getAttribute("USERINFO");
				if (up == null || up.getUserName() == null)
				{
					usersHash.put(nextKey, "UNKNOWN USER");
				}
				else{
					userName = up.getUserName();
					usersHash.put(nextKey, userName);
				}
			}
		}
		catch (Exception e)
		{
			log.fatal("Error while getting active users list: " + e.getMessage());
			throw new PWSException(e.getMessage());
		}
		return usersHash;
	}
}
