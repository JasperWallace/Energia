/* -*- mode: jde; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Uploader - abstract uploading baseclass (common to both uisp and avrdude)
  Part of the Arduino project - http://www.arduino.cc/

  Copyright (c) 2004-05
  Hernando Barragan

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
  
  $Id$
*/

package processing.app.debug;

import processing.app.Base;
import processing.app.Preferences;
import processing.app.Serial;
import processing.app.SerialException;
import processing.app.SerialNotFoundException;
import processing.app.I18n;
import static processing.app.I18n._;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import javax.swing.*;
//#ifndef RXTX
//import javax.comm.*;
//#else
// rxtx uses package gnu.io, but all the class names
// are the same as those used by javax.comm
import gnu.io.*;
//#endif


public abstract class Uploader implements MessageConsumer  {
  static final String BUGS_URL =
    _("https://developer.berlios.de/bugs/?group_id=3590");
  static final String SUPER_BADNESS =
    I18n.format(_("Compiler error, please submit this code to {0}"), BUGS_URL);

  RunnerException exception;
  //PdePreferences preferences;

  //Serial serialPort;
  static InputStream serialInput;
  static OutputStream serialOutput;
  //int serial; // last byte of data received
  
  boolean verbose;

  public Uploader() {
  }

public abstract boolean uploadUsingPreferences(String buildPath, String className, boolean usingProgrammer)
    throws RunnerException, SerialException;
  
  public abstract boolean burnBootloader() throws RunnerException;
  
  protected void flushSerialBuffer() throws RunnerException, SerialException {
    // Cleanup the serial buffer
    try {
      Serial serialPort = new Serial();
      byte[] readBuffer;
      while(serialPort.available() > 0) {
        readBuffer = serialPort.readBytes();
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {}
      }

      serialPort.setDTR(false);
      serialPort.setRTS(false);

      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {}

      serialPort.setDTR(true);
      serialPort.setRTS(true);
      
      serialPort.dispose();
    } catch (SerialNotFoundException e) {
      throw e;
    } catch(Exception e) {
      e.printStackTrace();
      throw new RunnerException(e.getMessage());
    }
  }

  protected boolean executeUploadCommand(Collection commandDownloader) 
    throws RunnerException
  {
	exception = null;
    firstErrorFound = false;  // haven't found any errors yet
    secondErrorFound = false;
    notFoundError = false;
    int result=0; // pre-initialized to quiet a bogus warning from jikes
    
    String userdir = System.getProperty("user.dir") + File.separator;

    try {
      String[] commandArray = new String[commandDownloader.size()];
      commandDownloader.toArray(commandArray);
      
      if (verbose || Preferences.getBoolean("upload.verbose")) {
        for(int i = 0; i < commandArray.length; i++) {
          System.out.print(commandArray[i] + " ");
        }
        System.out.println();
      }

      Process process;

      if (Base.isMacOS()) {
        ProcessBuilder builder = new ProcessBuilder(commandArray);
        Map<String, String> env = builder.environment();
        env.put("DYLD_LIBRARY_PATH", Base.getHardwarePath() + "/tools/msp430/mspdebug");
        builder.directory(new File(Base.getLM4FBasePath()));
        process = builder.start();

        //process = Runtime.getRuntime().exec(commandArray, envp);
      } else if(Base.isLinux()) {
        ProcessBuilder builder = new ProcessBuilder(commandArray);
        Map<String, String> env = builder.environment();
        env.put("LD_LIBRARY_PATH", Base.getHardwarePath() + "/tools/msp430/bin");
        builder.directory(new File(Base.getLM4FBasePath()));
        process = builder.start();

      } else {
        ProcessBuilder builder = new ProcessBuilder(commandArray);
        builder.directory(new File(Base.getLM4FBasePath()));
        process = builder.start();
      }

      new MessageSiphon(process.getInputStream(), this);
      new MessageSiphon(process.getErrorStream(), this);

      // wait for the process to finish.  if interrupted
      // before waitFor returns, continue waiting
      //
      boolean compiling = true;
      while (compiling) {
        try {
          result = process.waitFor();
          compiling = false;
        } catch (InterruptedException intExc) {
        }
      } 
      if(exception!=null) {
        exception.hideStackTrace();
        throw exception;   
      }
      if(result!=0)
        return false;
    } catch (Exception e) {
      String msg = e.getMessage();
      if ((msg != null) && ((msg.indexOf("update needed") != -1) || (msg.indexOf("update failed") != -1))) {
        return false;
      } else {
        e.printStackTrace();
        result = -1;
      }
    }
    //System.out.println("result2 is "+result);
    // if the result isn't a known, expected value it means that something
    // is fairly wrong, one possibility is that jikes has crashed.
    //
    if (exception != null) throw exception;

    if ((result != 0) && (result != 1 )) {
      exception = new RunnerException(SUPER_BADNESS);
      //editor.error(exception);
      //PdeBase.openURL(BUGS_URL);
      //throw new PdeException(SUPER_BADNESS);
    }

    return (result == 0); // ? true : false;      

  }
  
  protected boolean executeUploadCommand(String[] commands) 
		    throws RunnerException
		  {
			exception = null;
		    firstErrorFound = false;  // haven't found any errors yet
		    secondErrorFound = false;
		    notFoundError = false;
		    int result=0; // pre-initialized to quiet a bogus warning from jikes
		    
		    String userdir = System.getProperty("user.dir") + File.separator;

		    try {

		      
		      if (verbose || Preferences.getBoolean("upload.verbose")) {
		        for(int i = 0; i < commands.length; i++) {
		          System.out.print(commands[i] + " ");
		        }
		        System.out.println();
		      }

		      Process process;

		      if (Base.isMacOS()) {
		        ProcessBuilder builder = new ProcessBuilder(commands);
		        Map<String, String> env = builder.environment();
		        env.put("DYLD_LIBRARY_PATH", Base.getHardwarePath() + "/tools/msp430/mspdebug");
		        builder.directory(new File(Base.getLM4FBasePath()));
		        process = builder.start();
		        
		        //process = Runtime.getRuntime().exec(commands, envp);
		      } else if(Base.isLinux()) {
		        String envp[] = { "LD_LIBRARY_PATH=" + Base.getHardwarePath() + "/tools/msp430/bin" };
		        process = Runtime.getRuntime().exec(commands, envp);

		      } else {
		        process = Runtime.getRuntime().exec(commands);
		      }

		      new MessageSiphon(process.getInputStream(), this);
		      new MessageSiphon(process.getErrorStream(), this);

		      // wait for the process to finish.  if interrupted
		      // before waitFor returns, continue waiting
		      //
		      boolean compiling = true;
		      while (compiling) {
		        try {
		          result = process.waitFor();
		          compiling = false;
		        } catch (InterruptedException intExc) {
		        }
		      } 
		      if(exception!=null) {
		        exception.hideStackTrace();
		        throw exception;   
		      }
		      if(result!=0)
		        return false;
		    } catch (Exception e) {
		      String msg = e.getMessage();
		      if ((msg != null) && ((msg.indexOf("update needed") != -1) || (msg.indexOf("update failed") != -1))) {
		        return false;
		      } else {
		        e.printStackTrace();
		        result = -1;
		      }
		    }
		    //System.out.println("result2 is "+result);
		    // if the result isn't a known, expected value it means that something
		    // is fairly wrong, one possibility is that jikes has crashed.
		    //
		    if (exception != null) throw exception;

		    if ((result != 0) && (result != 1 )) {
		      exception = new RunnerException(SUPER_BADNESS);
		      //editor.error(exception);
		      //PdeBase.openURL(BUGS_URL);
		      //throw new PdeException(SUPER_BADNESS);
		    }

		    return (result == 0); // ? true : false;      

		  }

  boolean firstErrorFound;
  boolean secondErrorFound;

  // part of the PdeMessageConsumer interface
  //
  boolean notFoundError;

  public void message(String s) {
    //System.err.println("MSG: " + s);
    System.err.print(s);

    // ignore cautions
    if (s.indexOf("Error") != -1) {
      //exception = new RunnerException(s+" Check the serial port selected or your Board is connected");
      //System.out.println(s);
      //notFoundError = true;
      return;
    }
    if(notFoundError) {
      //System.out.println("throwing something");
      exception = new RunnerException(I18n.format(_("the selected serial port {0} does not exist or your board is not connected"), s));
      return;
    }
    if (s.indexOf("Device is not responding") != -1 ) {
      exception =  new RunnerException(_("Device is not responding, check the right serial port is selected or RESET the board right before exporting"));
      return;
    }
    if (s.indexOf("Programmer is not responding") != -1 ||
        s.indexOf("programmer is not responding") != -1 ||
        s.indexOf("protocol error") != -1) {
      exception = new RunnerException(_("Problem uploading to board.  See http://www.arduino.cc/en/Guide/Troubleshooting#upload for suggestions."));
      return;
    }
    if (s.indexOf("Expected signature") != -1) {
      exception = new RunnerException(_("Wrong microcontroller found.  Did you select the right board from the Tools > Board menu?"));
      return;
    }
    if (s.indexOf("--allow-fw-update") != -1) {
    	exception = new RunnerException(_("update needed"));
    }

    if (s.indexOf("Update failed") != -1) {
    	exception = new RunnerException(_("update failed"));
    }

  }


}
