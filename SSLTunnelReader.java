import java.net.*;
import java.io.*;
import javax.net.ssl.*;
import com.sun.net.ssl.*;
import javax.net.*;
import javax.security.cert.*;
import java.security.Security;

/*
 * This example illustrates using a URL to access resources
 * on a secure site from behind the firewall using the SSLTunnelSocketFactory.
 *
 */

public class SSLTunnelReader {

   private final static String proxyHost = "192.168.138.15";
   private final static String proxyPort = "80";

   public static void main(String[] args) throws Exception {
      System.setProperty("java.protocol.handler.pkgs",
                         "com.sun.net.ssl.internal.www.protocol");
      Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());
      System.setProperty("https.proxyHost",proxyHost);
      System.setProperty("https.proxyPort",proxyPort);

      URL myimgarc = new URL("https://www.firstunion.com");
      URLConnection urlc = myimgarc.openConnection(); //from secure site
      if(urlc instanceof com.sun.net.ssl.HttpsURLConnection){
         ((com.sun.net.ssl.HttpsURLConnection)urlc).setSSLSocketFactory
                             ((SSLSocketFactory)SSLSocketFactory.getDefault());
      }
      if(urlc instanceof com.sun.net.ssl.HttpsURLConnection){
         ((com.sun.net.ssl.HttpsURLConnection)urlc).setHostnameVerifier(new HostnameVerifier()
            {
                public boolean verify(String urlHostname, String certHostname)
                {
                    return true;
                }
            });
      }


      BufferedReader in = new BufferedReader(new InputStreamReader(
                                             urlc.getInputStream()));

      String inputLine;
      int lines = 0;
      String filename = "ssl.txt";
	  FileOutputStream fos = new FileOutputStream(filename);

      while ((inputLine = in.readLine()) != null){
		lines++;
      	fos.write(inputLine.getBytes());
	  }
      System.out.println("No of lines : "+lines);
	  fos.close();
      in.close();
   }
}

