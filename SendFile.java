
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.security.*;
import java.util.*;
import Snow.*;
import com.snowbound.snapserv.snowutil.*;
import java.awt.Rectangle;
import java.net.*;
import javax.net.ssl.*;

public class SendFile extends HttpServlet {

   private final static int PADDING = 5000;
   private ImageData idata = null;
   private byte[] bytes = null;
   private Properties props = null;
   private String directoryListing = null;
   private String bestGuessBytes = null;
   private int BYTESIZE = 50000;
   private int bytesize = 0;
   private String buildNumber = "14.032003";
   private String action;
   private String fileName;
   private Integer num;
   private int pageNumber;
   private String thumbnail;
   private String thumbrange;
   private Integer thumbwidth;
   private String fullPath;
   private byte[] buff;
   private Snowbnd snow2;
   private String searchString;
   private int debug;
   private byte[] inputArray;
   private String docid;
   private String userid;


   public SendFile()
   {
   } //end constructor

  //Initialize global variables
  public void init(ServletConfig config) throws ServletException
  {
    super.init(config);
  }

  public String getDocid()
  {
   return docid;
  }

  public String getUserid()
  {
   return userid;
  }

  //Process the HTTP Post request
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
  {
    //init variables..
   fullPath =null;
   directoryListing=null;
   fileName = null;
   idata = new ImageData();
   snow2 = new Snowbnd();

   System.err.println("Post: Build Number:  " + buildNumber);

   //get url parameters...
   readParameters(request);

   //Load the Properties File..
   loadProperties();

   //System.out.println("CHECK FOR S: " + fullPath.substring(4,5));


    //Construct Image Data Object
   if (fullPath.substring(0,4).equals("http"))
   {
     URL url = null;
     //Use the correct handler for SSL connections...
     if (fullPath.substring(4,5).equals("s"))
     {
       try
       {
         //props.put("java.protocol.handler.pkgs","com.ibm.net.ssl.internal.www.protocol");
         ///System.setProperties(props);
         //System.out.println(System.getProperties());
         Security.addProvider(new com.ibm.jsse.IBMJSSEProvider());
         System.setProperty("java.protocol.handler.pkgs","com.ibm.net.ssl.internal.www.protocol");;
         url = new URL(fullPath);
       }
       catch(Exception cfe)
       {
         System.out.println("Unable to load the JSSE SSL stream handler." + "Check ClassPath." + cfe.toString());
         cfe.printStackTrace();
       }


     }//end if
     else
     {
       try
       {
         url = new URL(fullPath);
       }
       catch(Exception e)
       {
         e.printStackTrace();
       }
     }

     //If we're reading from a URL we'll download the data into a byte array only once
     //here, and will only later wrap it with a datainputstream for speed...
    //System.err.println("About to open connection");
     HttpURLConnection urlConnection = (HttpURLConnection)url.openConnection();
      // System.err.println("About to open connection2");
     //urlConnection.setAllowUserInteraction(true);
     urlConnection.setAllowUserInteraction(false);
     urlConnection.setDoInput(true);
     urlConnection.setDoOutput(true);
     urlConnection.setUseCaches(false);
    //  urlConnection.connect();
    //System.err.println("About to open connection3");
    InputStream inputstream = urlConnection.getInputStream();
    //System.err.println("About to open connection4");
    ByteArrayOutputStream bytearrayoutputstream = new ByteArrayOutputStream();
    //System.err.println("About to open connection5");
     int avail = 0;
     byte[] arry;

     while (avail != -1)
     {
       arry = new byte[200000];
       avail = inputstream.read(arry);
       System.err.println("# of bytes available on stream: :" + avail);

       if (avail > 0)
       {
         byte[] c = new byte[avail];

         System.arraycopy(arry,0,c,0,avail);
         bytearrayoutputstream.write(c);
       }
     }
     bytearrayoutputstream.flush();
     inputstream.close();
     bytearrayoutputstream.close();



  //   int l = 0;
//     int size = 1000;



    // do
  //   {
//
    //   l = inputstream.read();
  //     bytearrayoutputstream.write(l);
//
//
  //   }
//     while (l != -1);

     inputArray = bytearrayoutputstream.toByteArray();

     inputstream.close();
     bytearrayoutputstream.close();

     DataInputStream dis = getDataInputStream();

     idata.setTotalPages(snow2.IMGLOW_get_pages(dis));

     dis.close();
   }
   else
   {
     idata.setTotalPages(snow2.IMGLOW_get_pages(fullPath));
   }

   idata.setFileName(fileName);

   int actionInt = translateAction(action);

   switch(actionInt)
   {
     case 0: //thumbnail case....
       System.err.println("Request Thumb");
       createThumbs();
       break;
     case 1: //full image request case....
       System.err.println("Request Image");
       createFullImage();
       break;
     case 2: // request for annotation...
       System.err.println("Request Annotation");
       createAnnotation();
       break;
     case 3: //request highlight rects...
       System.err.println("Request Highlights");
       searchText();
       break;
     case 4: //request line data...
       System.err.println("Request Line Data");
       convertToCsv();
       break;
     case 5: //get page count...
       System.err.println("Check Page Count");
       break;


     default:
       break;
   }

    //Write the data Object to the servlet response stream, to be processed by the applet.
    ObjectOutputStream out = new ObjectOutputStream(response.getOutputStream());
    out.writeObject(idata);
    response.getOutputStream().close();
    buff=null;
    out.close();
  }

  private void loadProperties()
  {

      props = System.getProperties();

      try
      {
		  Class c = getClass();
		  ClassLoader cl = c.getClassLoader();
		  java.io.InputStream is = cl.getResourceAsStream("directory.props");
		  props.load(is);
		  is.close();
		  //This gets the directory where the image files are stored from the property file.
		  directoryListing = props.getProperty("dir");
		  bestGuessBytes = props.getProperty("bytesize");



		 if (directoryListing == null)
		   {
		      System.err.println("*** ERROR READING DIRECTORY FROM DIRECTORY.PROPS FILE ***");
		      directoryListing = "";
		   } //end if

     /* 1/13/05 Updated this as the get file.separator was causing a problem, it should
        never cause the problem it did, it should always be a defined system property but
        for some reason it was not.
     */
		  if (!directoryListing.endsWith("/"))
		  {
		    directoryListing += "/";
		  } //end if
		 } //end try
		 catch(IOException e)
		 {
		   System.err.println("*** ERROR READING FROM PROPERTIES FILE ***");
		 } //end catch


      if ((directoryListing != null) && (directoryListing.length() > 2))
		  fullPath = directoryListing + fileName;
      else
		  fullPath = fileName;
  } //end loadProperties...

  public Object[] buildThumbs(int thumbwidth, String range, String file)
  {
    int startRange;
    int endRange;
    int truesize;
    byte[] tempBytes = new byte[BYTESIZE];
    byte[] finalBytes;
    int format;

    System.out.println(docid);
    System.out.println(userid);

    Object[] thumbs = new Object[1];
    Snowbnd simage = new Snowbnd();
    simage.IMGLOW_set_pdf_input(200,1);
    System.err.println("Step 1");
    //parse the range..
    if (!range.equals("all"))
    {
      int seperator = range.indexOf("-");
      startRange = new Integer(range.substring(0,seperator)).intValue();
      endRange = new Integer (range.substring(seperator+1,range.length())).intValue();
      System.err.println("Step 2");
      System.out.println(fullPath);
      System.out.println(fullPath.substring(0,5));

      if (fullPath.substring(0,4).equals("http"))
      {
       try
       {
		     DataInputStream dis = getDataInputStream();

		     if (endRange > simage.IMGLOW_get_pages(dis)-1)
		      {
		         dis.close();
		         System.out.println(fullPath);
		 		 dis = getDataInputStream();
		 		 endRange = simage.IMGLOW_get_pages(dis)-1;
		         dis.close();
		      }
		 }
		 catch(Exception e)
		 {
		  e.printStackTrace();
		 }

       }
       else
       {
		 if (endRange > simage.IMGLOW_get_pages(fullPath)-1)
		 {
		     endRange = simage.IMGLOW_get_pages(fullPath)-1;
		 }
      }
      System.err.println("Step 3");
      if (debug ==1)
        System.err.println("START RANGE : " + startRange + "  END RANGE: " + endRange);
    }
    else
    {
      startRange = 0;
      endRange = 0;

      System.err.println("MIKE:" +fullPath);
      if (fullPath.substring(0,4).equals("http"))
      {
       try
       {
		   DataInputStream dis = getDataInputStream();
          endRange = simage.IMGLOW_get_pages(dis)-1;
		   dis.close();
		 }
		 catch(Exception e)
		 {
		  e.printStackTrace();
		 }
      }
      else
      {
        endRange = simage.IMGLOW_get_pages(fullPath)-1;
      }

    }
    //end parse the range..

    idata.setThumbEndRange(endRange);
    idata.setThumbStartRange(startRange);
    System.out.println("ENDRANGE: " + endRange);
    System.out.println("STARTRANGE: " + startRange);
    thumbs = new Object[endRange-startRange+1];

    //Create Thumbnail Vector...
    int vert;
    int stat;
    int thumbCount =0;
    for (int i=startRange; i<=endRange; i++)
    {
      int result = 0;
      if (fullPath.substring(0,4).equals("http"))
      {
        try
		 {
          DataInputStream dis = getDataInputStream();
		   result = simage.IMG_decompress_bitmap(dis,i);
		   dis.close();
		 }
		 catch(Exception e)
		 {
		  e.printStackTrace();
		 }
      }
      else
      {
       result = simage.IMG_decompress_bitmap(fullPath,i);
      }

		 if (result == -2)
		 {
		   System.err.println("Thumb: -2 FILE NOT FOUND");
		 }
		 else if (result < 0)
		 {
		   System.err.println("Thumb result: " + result + " while RasterMaster was decompressing");
		 }


     vert = (int)(((double)thumbwidth / (double)simage.getWidth()) * (double)simage.getHeight());
     stat = simage.IMG_create_thumbnail(thumbwidth,vert);
      //stat = simage.IMG_resize_bitmap(thumbwidth,vert);

       if (result >=0)
       {
           int degrees = simage.IMGLOW_get_image_orientation();
                if (degrees > 0)
		 		 {
                        simage.IMG_rotate_bitmap((360-degrees)*100);
		 		 		 if (debug == 1)
		 		 		     System.err.println("thumb: rotated bitmap, correcting...");
		 		 }

       }

    if (debug ==1)
     System.err.println("THUMB CREATED : " + stat);

     if ((simage.getBitsPerPixel() == 1))// || (simage.IMGLOW_get_filetype(fullPath) == 59))
      {
       //Send the output as a Tiff Group 4
       format = 10;
      }
      else
      {
      //Send the output as a JPEG.
      format = 40;
      }

     tempBytes = new byte[BYTESIZE];
     truesize = simage.IMG_save_bitmap(tempBytes,format);

    if (debug ==1)
       System.err.println("TRUE SIZE : " + truesize + "   " + BYTESIZE);

     finalBytes = new byte[truesize];
     System.arraycopy(tempBytes,0,finalBytes,0,truesize);
     thumbs[thumbCount++] = finalBytes;
    }

    //Return Vector of thumbnails.....
    return thumbs;
  }

  private int determineByteSize(int fileType)
  {
    //switch on filetype...enchanced handling for Tiff files.
    // Addition: July 25, 2001: Added code to calculated byte size for a page.
    //1/13/04
    // It dies in this method for chase, this didn't happen earlier and this code
    //hasn't changed they get an awfully low predetermined byte size for a JPEG
    //it doesn't really make sense but maybe its something with the new JVM,
    //the best guess bytes should suffice anyhow as this calculation has
    //potential flaws on badly formed TIFFs.
		 /*       int value[] = {0};
		       //get image length
		       int imageLength=0;
		       int rowsPerStrip=0;
		       int stripByteCount=0;

		       if (fullPath.substring(0,4).equals("http"))
		       {
		        try
		        {
		 		   DataInputStream dis = getDataInputStream();
		 		   snow2.IMGLOW_get_tiff_tag(257,0,value, dis, null,pageNumber);
		 		   imageLength = value[0];
		 		   dis.close();

		 		   //get rows per strip
		 		   dis = getDataInputStream();
		 		   snow2.IMGLOW_get_tiff_tag(278,0,value,dis,null, pageNumber);
		 		   rowsPerStrip = value[0];
		 		   dis.close();

		 		   //get strip byte count
		 		   dis = getDataInputStream();
		 		   snow2.IMGLOW_get_tiff_tag(279,0,value,dis,null,pageNumber);
		 		   stripByteCount = value[0];
  		 		   dis.close();
		 		 }
		 		 catch(Exception e)
		 		 {
		 		  e.printStackTrace();
		 		 }

		 		 }
		       else
		       {
		 		   snow2.IMGLOW_get_tiff_tag(257,0,value, fullPath, null,pageNumber);
		 		   imageLength = value[0];

		 		   //get rows per strip
		 		   snow2.IMGLOW_get_tiff_tag(278,0,value,fullPath,null, pageNumber);
		 		   rowsPerStrip = value[0];

		 		   //get strip byte count
		 		   snow2.IMGLOW_get_tiff_tag(279,0,value,fullPath,null,pageNumber);
		 		   stripByteCount = value[0];
		       }

		       if (rowsPerStrip == imageLength)
		       {
		 		  bytesize = stripByteCount + PADDING;
		       }
		       else
		       {
		 		  bytesize = ((imageLength/rowsPerStrip) * stripByteCount) +PADDING;
		       }

        if (debug ==1)
		       System.err.println("BYTESIZE: " + bytesize);
		 break;

		 //This would be the case where its not a Tiff...*/
		 //default:
		       bytesize = new Integer(bestGuessBytes).intValue();
		 //break;
		 //}

		 return bytesize;
  }
  private void readParameters(HttpServletRequest request)
  {

   fileName = request.getParameter("fileName");
   docid = request.getParameter("docid");
   userid = request.getParameter("userid");

   if (idata != null)
   {
    idata.setUserId(userid);
    idata.setDocId(docid);
   }

    if (request.getParameter("pageNumber") != null)
        num  = new Integer(request.getParameter("pageNumber"));
    else
       num = new Integer(0);

    action = request.getParameter("action");
    thumbrange = request.getParameter("thumbrange");

    if (request.getParameter("thumbwidth") != null)
        thumbwidth  = new Integer(request.getParameter("thumbwidth"));
    else
        thumbwidth = new Integer(120);

   //Check to make sure the page number is specified if not set it to 0.
   if (num != null)
    {
     pageNumber = num.intValue();
    }
   else
    {
     System.err.println("*** ERROR READING PAGE NUMBER ***");
     pageNumber = 0;
    }

    searchString = request.getParameter("searchString");

    //** CHECK FOR NEW DEBUG VARIABLE **/
     if (request.getParameter("debug") != null)
        debug  = new Integer(request.getParameter("debug")).intValue();
    else
        debug = new Integer(0).intValue();



  }
  //Process the HTTP Get request
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
  {
    System.err.println("Get: Build Number:  " + buildNumber);
    //Process as a post..
    doPost(request,response);
   } //end doGet

   private DataInputStream getDataInputStream()
   {
      try
      {
		 ByteArrayInputStream bytearrayinputstream = new ByteArrayInputStream(inputArray);
		 DataInputStream dis = new DataInputStream(bytearrayinputstream);
		 bytearrayinputstream.close();

		 return dis;
      }
      catch(Exception e)
      {
		  e.printStackTrace();
      }

      return null;

   }

   private int translateAction(String act)
   {
      if (act.equals("thumbnail"))
        return 0;
      else if (act.equals("image"))
        return 1;
      else if (act.equals("annotation"))
        return 2;
      else if (act.equals("highlights"))
        return 3;
      else if (act.equals("linedata"))
        return 4;
      else if (act.equals("pagecount"))
        return 5;
      else if (act.equals("stream"))
        return 6;
      else
        return -1;
   }

  private void createAnnotation()
  {
    byte buff[] = new byte[1];
    int length;
    RandomAccessFile ras;


		     if (fullPath.substring(0,4).equals("http"))
		     {
		           URL url = null;
		 		   //Use the correct handler for SSL connections...
		 		   if (fullPath.substring(4,5).equals("s"))
		 		   {


		 		    try
		 		    {
		 		  //  props.put("java.protocol.handler.pkgs","com.ibm.net.ssl.internal.www.protocol");
		 		  //  System.setProperties(props);
		 		   // System.out.println(System.getProperties());
		 		   // Security.addProvider(new com.ibm.jsse.JSSEProvider());
           Security.addProvider(new com.ibm.jsse.IBMJSSEProvider());
           System.setProperty("java.protocol.handler.pkgs","com.ibm.net.ssl.internal.www.protocol");;
		 		    url = new URL(fullPath);
		 		    }
		 		    catch(Exception cfe)
		 		    {
		 		       System.out.println("Unable to load the JSSE SSL stream handler." + "Check ClassPath." + cfe.toString());
		 		       cfe.printStackTrace();
		 		    }


		 		   }//end if
		 		   else
		 		   {
		 		     try
		 		     {
		 		      url = new URL(fullPath);
		 		     }
		 		     catch(Exception e)
		 		     {
		 		      e.printStackTrace();
		 		     }
		 		   }

		 		   //If we're reading from a URL we'll download the data into a byte array only once
    		 		   //here, and will only later wrap it with a datainputstream for speed...
		 		 try
		 		 {
		 		 HttpURLConnection urlConnection = (java.net.HttpURLConnection)url.openConnection();
		 		 System.out.println("Check Connection 1");
		 		 //urlConnection.setAllowUserInteraction(true);
            urlConnection.setAllowUserInteraction(false);
		 		 urlConnection.setDoInput(true);
		 		 urlConnection.setDoOutput(true);
		 		 urlConnection.setUseCaches(false);

		 		 InputStream inputstream = urlConnection.getInputStream();
		 		 System.out.println("Check Connection 2");
		 		 ByteArrayOutputStream bytearrayoutputstream = new ByteArrayOutputStream();
		 		 System.out.println("Check Connection 3");
		         int l = 0;
		 		 int size = 1000;

		 		   do
		 		   {

                    l = inputstream.read();
		 		     bytearrayoutputstream.write(l);


		            }
		 		    while (l != -1);
		 		    System.out.println("Check Connection 4");

		 		    buff = bytearrayoutputstream.toByteArray();

		 		    inputstream.close();
		 		    bytearrayoutputstream.close();
		 		 }
		 		 catch(Exception e)
		 		 {
		 		  e.printStackTrace();
		 		 }
   }
   else
   {
    try
     {
       		 ras = new RandomAccessFile (fileName,"r");
       		 length = (int)ras.length();
        buff = new byte[length];
       		 ras.readFully(buff,0,length);
		 ras.close();
        ras = null;
      }
      catch (IOException ioe)
      {
       		 ioe.printStackTrace();
      }
    }//end else
      idata.setAnnotationData(buff);
      idata.setAnnotationName(fileName);

      if (debug ==1)
		 System.err.println("method: createAnnotation -> Annotation Created");
  }
  private void convertToCsv()
  {
		 RandomAccessFile ras;
        int length=0;
		 int filesize=0;
        byte buff[] = new byte[length];
		 byte outbuff[];

         try
         {
           		 ras = new RandomAccessFile (fileName,"r");
            		 length = (int)ras.length();
                buff = new byte[length];
            		 ras.readFully(buff,0,length);
		         ras.close();
                ras = null;
		   }
		   catch (IOException ioe)
		   {
          		 ioe.printStackTrace();
		   }



		   outbuff = new byte[length];
		   int newln = LinetoCSV.LinetoCSV(buff,outbuff,length);

		   idata.setLineDataLength(newln);
		   idata.setLineData(outbuff);
		   idata.setFileName(fileName);

      if (debug ==1)
		 System.err.println("method: lineToCSV -> lineToCSV");


   } //end convert_to_csv

   private void searchText()
   {
      int[] count = new int[1];
      int[] error = new int[1];;
      System.out.println(fileName);
      System.out.println(searchString);
      Rectangle[] rc = snow2.IMG_search_text(fileName,searchString,pageNumber,count,error);// = snow2.IMG_search_textIMG_search_text(fileName,searchString,pageNumber,count,error);

      idata.setPDFRectangles(rc);
      idata.setSearchString(searchString);
      idata.setFileName(fileName);

      if (debug ==1)
		 System.err.println("method: searchTxt -> Search Text Done.");


   }

   private void createThumbs()
   {


          if (thumbrange != null)
		     idata.setThumbs(buildThumbs((thumbwidth.intValue()),thumbrange,fileName));
		   else
		     idata.setThumbs(buildThumbs((thumbwidth.intValue()),"all",fileName));

      if (debug ==1)
		 System.err.println("method: createThumbs -> create Thumbs Done");


   }

   private void createFullImage() throws IOException
   {
      int fileType;

    System.out.println(docid);
    System.out.println(userid);

      if (fullPath.substring(0,4).equals("http"))
      {
      		 		   if (fullPath.substring(4,5).equals("s"))
		 		   {
		 		    //Properties props = new Properties();
		 		    //props.put("java.protocol.handler.pkgs","com.ibm.net.ssl.internal.www.protocol");
		 		    //System.setProperties(props);
		 		    try
		 		    {
		 		    // Class clsFactory = Class.forName("com.sun.net.ssl.internal.ssl.Provider");
		 		    // System.out.println("Provider: " + Security.getProvider("SunJSSE"));
		 		    // if ((null != clsFactory))
		 		    // {
		 		      // Security.addProvider(new com.ibm.jsse.JSSEProvider());
		 		    //   url = new URL(fullPath);
		 //		     }
             Security.addProvider(new com.ibm.jsse.IBMJSSEProvider());
             System.setProperty("java.protocol.handler.pkgs","com.ibm.net.ssl.internal.www.protocol");;
		 		    }
		 		    catch(Exception cfe)
		 		    {
		 		       System.out.println("Unable to load the JSSE SSL stream handler." + "Check ClassPath." + cfe.toString());
		 		    }


		 		   }

		 		    DataInputStream dis = getDataInputStream();
		            fileType = snow2.IMGLOW_get_filetype(dis);
		 		    System.out.println("filetype:" + fileType);
      }
      else
      {
       fileType = snow2.IMGLOW_get_filetype(fullPath);
      }

       bytesize = determineByteSize(fileType);

       System.out.println("bytesize:" + bytesize);
        if (debug == 1)
          System.err.println("Predetermined Est. : " + bytesize);

		 bytes = new byte[bytesize];
		 snow2 = null;

		 Snowbnd snow = new Snowbnd();
        snow.IMGLOW_set_pdf_input(200,1);

		 int result;
        if (fullPath.substring(0,4).equals("http"))
		 {


		  DataInputStream dis = getDataInputStream();
		  result = snow.IMG_decompress_bitmap(dis,pageNumber);
		 }
		 else
		 {
		  result = snow.IMG_decompress_bitmap(fullPath,pageNumber);
		 }
		 if (debug ==1)
		   System.err.println("Full Image Decompressed: " + result);
		 //Check to make sure we can actually decompress the file, although if the file was not found
		 //our path would have already gone awry.
		 if (result == -2)
		 {
		   System.err.println("-2 FILE NOT FOUND");
		   throw new java.io.FileNotFoundException("File : " + fullPath + " not found.");
		 }
		 else if (result < 0)
		 {
		   System.err.println(result + " while RasterMaster was decompressing");
		   throw new java.lang.UnknownError("Got Error # " + result + " while RasterMaster was decompressing.");
		 }

       int format;

       /** INSERTION HERE GOING TO TEST TO SEE IF IMAGE IS TIFF, THEN CHECK ORIENTATION TAGS **/
       /** 09/05/2002 **/
       if (result >=0)
       {
           int degrees = snow.IMGLOW_get_image_orientation();
                if (degrees > 0)
		 		 {
                        snow.IMG_rotate_bitmap((360-degrees)*100);
		 		 		 if (debug == 1)
		 		 		     System.err.println("rotated bitmap, correcting...");
		 		 }

       }
       /** END: INSERTION HERE GOING TO TEST TO SEE IF IMAGE IS TIFF, THEN CHECK ORIENTATION TAGS **/

    //   System.out.println("INPUT FORMAT = " + snow.IMGLOW_get_filetype(fullPath));
       System.out.println("PIXEL DEPTH = " + snow.getBitsPerPixel());
       if ((snow.getBitsPerPixel() == 1))// || (snow.IMGLOW_get_filetype(fullPath) == 59))
		   {
		    //Send the output as a Tiff Group 4
		    format = 10;
		   }
		 else
		  {
		   //Send the output as a JPEG.
		   format = 40;
		  }


		 //We skim off buffer overrun by doing the array copy..keeps the network traffic an minimal as possible
		 //to send down each page, saves users having to deal with sending down entire size of bytesize property
		 //for each page rendered.

		 if (debug ==1)
		   System.err.println("FORMAT = " + format);
		 int amount = snow.IMG_save_bitmap(bytes,format);

		   if (amount < 0)
		   {
		       System.err.println(amount + " RASTERMASTER ERROR WHILE SAVING");
		       throw new java.lang.UnknownError("Got Error # " + amount + " while RasterMaster was saving.");
		   }

		 buff= new byte[amount];

		 if (debug == 1)
		   System.err.println("Full Image Actual Size: " + amount);

		 System.arraycopy(bytes,0,buff,0,amount);
		 idata.setData(buff);

   }
}
//end class SunSendFile